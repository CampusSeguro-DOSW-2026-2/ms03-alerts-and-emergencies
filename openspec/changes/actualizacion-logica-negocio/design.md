# Design

## Context

MS03 es un servicio Spring Boot 4.1.1 sobre Java 21, construido con Maven y PostgreSQL 17. La persistencia usa JPA con `spring.jpa.hibernate.ddl-auto=validate`; no hay Flyway ni Liquibase, y los scripts `database/001` a `database/004` se aplican manualmente antes de desplegar. La autenticación no valida JWT: el API Gateway inyecta `X-User-Id`, `X-User-Role`, `X-Service-Name` y `X-Correlation-ID`.

El modelo actual tiene un único `alerts.type` con el enum `EARTHQUAKE, SIMULATION, FIRE, OTHER`, un ciclo `DRAFT -> SCHEDULED -> ACTIVE -> ENDED -> ARCHIVED`, un endpoint `PATCH /{id}/finalizar` y un trato especial para MS06 basado en `X-Service-Name: ms06`. Ya existen la confirmación de recepción, la auditoría, el envío de correo y la consulta de destinatarios; la bandeja personal no existe.

Ver `proposal.md` para la motivación. Las specs describen el comportamiento esperado; este documento explica cómo implementarlo.

## Goals / Non-Goals

**Goals:**

- Un modelo de tipos múltiples con reglas de combinación y un tipo operativo.
- Un ciclo de vida de cuatro estados, sin finalización, con archivado idempotente desde cualquier estado no archivado.
- Una bandeja personal con confirmación explícita y correo enlazado a esa bandeja.
- Desacoplar MS06 del comportamiento de negocio y dejar `X-Service-Name` solo como metadato de auditoría.
- Migrar los datos y los contratos sin perder el historial de auditoría.

**Non-Goals:**

- Implementar el frontend de la bandeja; solo se documenta la ruta.
- Añadir acuses de entrega o lectura del proveedor de correo.
- Rediseñar por completo la matriz de roles; se mantiene la asignación actual salvo las reglas de `manage_drill` descritas.
- Cambiar MS01, el proveedor Brevo, el esquema de alcance o el modelo de destinatarios.
- Mantener compatibilidad hacia atrás con `type`, `ENDED` o `eventId`; el cambio es breaking y se documenta como tal.

## Decisions

### 1. Tipos múltiples en una tabla de colección

`AlertEntity` expone `Set<AlertType> types` con `@ElementCollection`, `@CollectionTable(name = "alert_types", joinColumns = @JoinColumn(name = "alert_id"))`, `@Enumerated(EnumType.STRING)` y `@BatchSize(size = 50)`. La tabla `alert_types` tiene columnas `alert_id` y `type`, clave primaria compuesta `(alert_id, type)` y FK `ON DELETE CASCADE`. El enum queda en `EARTHQUAKE, FIRE, OTHER, OPERATIVA`.

La columna `alerts.type` se conserva solo durante la migración y luego se elimina. Dejarla con `NOT NULL` rompería las inserciones nuevas, y hacerla nullable mantendría una fuente de verdad duplicada. El backfill traduce `SIMULATION -> OTHER`; `EARTHQUAKE`, `FIRE` y `OTHER` se copian tal cual.

Se usa una colección de enum y no un `@ManyToMany` a una tabla de catálogo porque los tipos son un conjunto fijo sin atributos propios. Se descartó JSONB o un arreglo de PostgreSQL porque complican la validación, el filtrado y el mapeo JPA; también se descartó una tabla de catálogo porque no aporta datos adicionales.

El orden de presentación es el orden canónico del enum: `EARTHQUAKE`, `FIRE`, `OTHER`, `OPERATIVA`. `AlertMapper` ordena los tipos antes de construir respuestas y correos; el conjunto persistido no depende del orden de entrada.

La validación de duplicados se hace en el servicio, porque Bean Validation no cubre bien la unicidad de una colección. `AlertSpecifications` filtra con un join a `types` y `distinct`; la prueba de integración verifica que el contenido no se duplique y que el conteo no se infle. Se añade el índice `alert_types(type, alert_id)` para el filtro por tipo.

### 2. Ciclo de vida de cuatro estados

`AlertStatus` queda en `DRAFT, SCHEDULED, ACTIVE, ARCHIVED`. `AlertStateMachine` codifica:

- `DRAFT -> SCHEDULED, ACTIVE, ARCHIVED`
- `SCHEDULED -> ACTIVE, ARCHIVED`
- `ACTIVE -> ARCHIVED`
- `ARCHIVED` sin transiciones

`archive` comprueba primero si la alerta ya está `ARCHIVED` y devuelve la respuesta actual sin escribir ni auditar. Si no, valida la transición, fija `archivedAt` y audita. Así el archivado repetido es idempotente y no genera un segundo evento `ARCHIVE`.

Se eliminan `end`, `AlertEndRequest`, `SimulationMismatchException`, `endedAt` y la ruta `PATCH /{id}/finalizar`. El estado `ENDED` no existe en el dominio ni en el contrato.

La programación mantiene `activationAt` como fecha futura. `activateImmediately=true` y `activationAt` son mutuamente excluyentes. La activación inmediata crea la alerta `ACTIVE` con `activatedAt` y sin `activationAt`; la activación manual de una alerta `SCHEDULED` no necesita fecha y puede adelantarse. El planificador sigue consultando `SCHEDULED` con `activationAt <= now`, pero ahora publica el evento y envía correo solo cuando la transición a `ACTIVE` ocurre.

`AlertStateRules` mantiene la edición restringida a `DRAFT` y `SCHEDULED`. El archivado se permite desde `DRAFT`, `SCHEDULED` y `ACTIVE`; una alerta archivada no se edita ni se reactiva.

La concurrencia se apoya en el bloqueo optimista existente y en `activateIfPending`. Para el archivado se usa `findForUpdate` y la comprobación de estado dentro de la misma transacción. Las pruebas cubren dos archivados concurrentes y dos activaciones concurrentes.

### 3. Auditoría compatible con el legado

El dominio `AlertStatus` no puede incluir `ENDED`, pero la tabla `audit_events` ya contiene filas con `END` y `ENDED`. Si el enum de auditoría se reutiliza, Hibernate falla al leer esos valores. Por eso se separan los enums de persistencia:

- `AuditAction` conserva `CREATE`, `UPDATE`, `SCHEDULE`, `ACTIVATE`, `ARCHIVE`, `CONFIRM`, `NOTIFICATION_SENT`, `NOTIFICATION_FAILED`, `RECIPIENT_RESOLUTION_FAILED` y el valor legado `END`.
- `AuditStatus` contiene `DRAFT`, `SCHEDULED`, `ACTIVE`, `ARCHIVED` y el valor legado `ENDED`.

`AuditEventEntity` mapea `action` y los estados con estos enums de auditoría. `AuditService` recibe `AlertStatus` y lo traduce a `AuditStatus`; el código nuevo nunca emite `END`. `AuditEventResponse` puede devolver `END`/`ENDED` únicamente para filas históricas, lo que cumple la regla de lectura legada.

La migración mantiene `END` y `ENDED` en los checks de `audit_events`, pero no en los checks de `alerts`. Así los datos históricos siguen siendo legibles y las nuevas operaciones no pueden escribirlos.

`AuditQueryServiceImpl` deja de mirar el prefijo `ms06:`. La clasificación es: actor `SYSTEM_SCHEDULER` o `SYSTEM_NOTIFIER` produce `SYSTEM`; un actor con `:` produce `SERVICE`; el resto produce `USER`. Los IDs de usuario son UUID, por lo que el `:` es un separador inequívoco para el actor de servicio.

### 4. Contrato HTTP y DTOs

Los DTOs de creación y edición usan `List<AlertType> types`; las respuestas usan `List<AlertType> types`. Se elimina `type` singular y `endedAt`. `AlertFilter` recibe `List<AlertType> types` y el controlador lo enlaza desde el parámetro repetido `type`, preservando el nombre de query parameter acordado. El listado también acepta `origin`, `scope` (tipo de alcance) y `activeAt` (instante en que la alerta estaba activa: `activatedAt <= activeAt` y `archivedAt` nulo o posterior).

La creación valida, en este orden: al menos un tipo, sin duplicados, catálogo válido, reglas de `OPERATIVA`, reglas de `origin`/`simulationId` y `manage_drill`. La edición reemplaza el conjunto completo de tipos cuando `types` viene presente y vuelve a aplicar las mismas reglas. La comparación de idempotencia por `simulationId` compara conjuntos de tipos, no listas ordenadas.

El controlador cambia `PUT /{id}`, `PATCH /programar` y `PATCH /archivar` a `CAN_MANAGE_ALERTS` (`manage` o `manage_drill`). El servicio recibe `RequesterContext` en las operaciones de ciclo de vida y aplica la regla de que `manage_drill` solo opera alertas `SIMULATION`. El planificador construye un contexto de sistema sin roles, por lo que no queda restringido a simulacros.

Se elimina la ruta de finalización y se añade `GET /api/v1/alertas/mias`. El resto de rutas y el sobre de errores (`status`, `codigo`, `mensaje`, `errores`, `correlation_id`) y la paginación (`contenido`, `page`, `size`, `total_elementos`, `total_paginas`) se mantienen.

`docs/api/openapi.yaml`, `OpenApiConfig`, `README.md` y la colección Postman se actualizan en el mismo cambio. La prueba de contrato compara el OpenAPI estático con las rutas y esquemas en ejecución.

### 5. Bandeja y confirmación

Se añade `InboxService` y un DTO `InboxAlertResponse` con `id`, `types`, `title`, `status`, `origin`, `scope`, `instructions`, `activatedAt`, `archivedAt` y `confirmedAt`. El endpoint `GET /api/v1/alertas/mias` exige `receive`.

El filtro de estado usa un enum propio `InboxStatus { ACTIVE, ARCHIVED, ALL }`, no `AlertStatus`, para que `DRAFT` y `SCHEDULED` no sean valores aceptables. El valor por defecto es `ACTIVE`; `ALL` expande a `ACTIVE + ARCHIVED`.

La consulta de bandeja se implementa en `AlertRepository` con subconsultas `exists` sobre `alert_recipients` y `notification_deliveries`, sin unir ambas tablas para evitar duplicados:

- destinatario `USER` con `recipient_ref_id = userId`;
- destinatario `ROLE` con `recipient_ref_id` en los roles del actor;
- entrega registrada para el usuario.

El orden es `activatedAt DESC NULLS LAST, createdAt DESC`; una alerta archivada desde `DRAFT` o `SCHEDULED` no tiene `activatedAt` y queda al final. La paginación reutiliza `PageResponse` con page 0, size 20 y máximo 100.

Para evitar N+1, los tipos se cargan con `@BatchSize(50)` y las confirmaciones de la página se cargan en una sola consulta `findByUserIdAndAlertIdIn`. El `confirmedAt` se toma del registro de confirmación; si no existe, es `null`.

La confirmación no cambia de contrato: `POST /{id}/confirmaciones` exige `receive`, responde `201` en la primera inserción, `200` en una repetición, `409 ALERTA_NO_ACTIVA` si la alerta no está `ACTIVE`, `403 NO_ES_DESTINATARIO` si el actor no es destinatario y `404 ALERTA_NO_ENCONTRADA` si no existe. Se conserva el `INSERT ... ON CONFLICT DO NOTHING` para la concurrencia y solo se audita cuando la inserción fue efectiva.

`AlertAccessPolicy` ya contempla destinatarios y entregas para el detalle; se mantiene y se reutiliza la misma semántica en la bandeja. Un actor con rol `ADMIN` no tiene `receive`, por lo que no accede a la bandeja ni confirma aunque sea destinatario nominal.

### 6. Correo

`EmailTemplateBuilder` construye el asunto y el cuerpo a partir de `types`, el título y las instrucciones. Las etiquetas legibles son `Terremoto`, `Incendio`, `Otra emergencia` y `Operativa`; se unen en orden canónico con `, `. El asunto de emergencia es `Alerta CampusSeguro: {etiquetas}` y el operativo es `[Operativa] {título}`.

El mensaje base de emergencia es exactamente: “Has recibido una alerta oficial de CampusSeguro. Confirma la recepción y sigue los pasos indicados: {instrucciones}. Ver y confirmar alerta: {link}”. Si `types` contiene `OPERATIVA`, el cuerpo omite ese mensaje base y presenta el título, las instrucciones y el enlace.

El enlace es `{appBaseUrl}/bandeja?alerta={id}`. `appBaseUrl` se normaliza sin barra final. Si la propiedad está vacía, el correo se envía sin la línea del enlace. Se generan versiones HTML y texto, con escape HTML de los valores.

El envío sigue siendo posterior al commit mediante `AlertActivatedEvent` y `@TransactionalEventListener(AFTER_COMMIT)`. No se envía correo al crear en `DRAFT`, al programar en `SCHEDULED` ni al archivar. Se conservan el límite diario, la resolución de destinatarios `USER`/`ROLE`, la deduplicación por usuario, el respaldo de usuarios activos y el registro `SENT`/`FAILED` por destinatario.

### 7. Desacople de MS06 y permisos

Se elimina `RequesterContext.isServiceMs06()`. `auditActor()` devuelve `{serviceName}:{actor}` cuando hay `X-Service-Name` no vacío y `actor` en caso contrario. `X-Service-Name` no altera el origen, no otorga permisos y no tiene ninguna rama especial para `ms06`.

Se eliminan los `@JsonAlias("eventId")` de los DTOs; el contrato solo acepta `simulationId` dentro de `origin`. `simulationId` sigue siendo un campo de dominio y la idempotencia de simulacros se conserva.

La matriz de roles no se rediseña:

- `COORDINADOR`: `receive`, `manage`, `read_history`.
- `ORGANIZADOR`: `receive`, `manage_drill`, `read_history`.
- `ADMINISTRATIVO`, `ESTUDIANTE`, `PROFESOR`, `BRIGADISTA`: `receive`.
- `AUDITOR`: `read_history`.
- `ADMIN`: sin permisos MS03.

`manage_drill` se aplica a crear, editar, programar, activar y archivar. Para las operaciones sobre una alerta existente, el servicio carga la alerta y compara su `origin`; si el contexto es de solo simulacros y el origen es `MANUAL`, responde `403 PROHIBIDO`.

### 8. Migración y despliegue

La migración se aplica manualmente, igual que las anteriores, y debe ejecutarse antes de arrancar el código nuevo porque `ddl-auto=validate` exige el esquema nuevo. El despliegue requiere una ventana de mantenimiento: el código viejo no puede convivir con el esquema nuevo.

Pasos de `database/005_business_logic_update.sql`:

1. Crear `alert_types` con PK `(alert_id, type)`, FK a `alerts` con `ON DELETE CASCADE` e índice `alert_types(type, alert_id)`.
2. Copiar `alerts.type` a `alert_types`, traduciendo `SIMULATION` a `OTHER`.
3. Convertir `ENDED` a `ARCHIVED` y fijar `archived_at = COALESCE(ended_at, now())`.
4. Eliminar las restricciones antiguas `ck_alerts_type`, `ck_alerts_status`, `ck_alerts_ended_has_date`, `ck_alerts_active_has_date` y `ck_alerts_archived_has_date`.
5. Eliminar `alerts.type` y `alerts.ended_at`.
6. Recrear `ck_alerts_status` con cuatro valores y `ck_alerts_active_has_date` como `status <> 'ACTIVE' OR activated_at IS NOT NULL`, para permitir archivar desde `DRAFT`/`SCHEDULED` sin `activatedAt`. Mantener `ck_alerts_scheduled_has_date` y `ck_alerts_archived_has_date`.
7. Ajustar `ck_audit_action`, `ck_audit_from_status` y `ck_audit_to_status` para aceptar los valores nuevos y los legados `END`/`ENDED`, sin permitir que el código nuevo los escriba.
8. Crear los índices de bandeja: `alert_recipients(recipient_type, recipient_ref_id, alert_id)`, `notification_deliveries(user_id, alert_id)`, `alert_confirmations(user_id, alert_id)` y `alerts(status, activated_at)`.
9. Verificar que todas las alertas tienen al menos un tipo, que no quedan `ENDED`, que no queda `ended_at` y que las constraints son válidas.

El script se ejecuta dentro de una transacción. Antes se toma un respaldo de la base. La verificación posterior incluye conteos de `alerts` vs. `alert_types`, búsqueda de `status = 'ENDED'`, comprobación de `archived_at` no nulo en `ARCHIVED` y `VALIDATE CONSTRAINT` de las nuevas restricciones.

Rollback preferido: restaurar el respaldo y volver a desplegar la versión anterior. Un rollback solo por SQL es posible pero destructivo para la información multi-tipo: habría que recrear `alerts.type`, elegir un tipo por alerta (`SIMULATION` si el origen es simulacro, `OTHER` para `OPERATIVA`, o el primero del conjunto), recrear `ended_at` desde `archived_at`, restaurar los checks antiguos y eliminar `alert_types`. Se documenta como último recurso.

### 9. Pruebas y verificación

- Unitarias: reglas de catálogo, origen, `OPERATIVA`, transiciones, archivado idempotente, edición restringida, comparación de idempotencia, construcción de correo, `actorType` y permisos `manage_drill`.
- Integración con Testcontainers: crear con varios tipos, filtrar por `type` repetido sin duplicados, programar, activar, archivar desde cada estado, bandeja `ACTIVE`/`ARCHIVED`/`ALL`, confirmación y migración.
- Contrato: OpenAPI estático contra rutas y esquemas en ejecución; actualizar la colección Postman.
- Migración: aplicar `001` a `005` en una base limpia y sobre datos con `ENDED`/`SIMULATION`; verificar el legado de auditoría.
- Cobertura: mantener el umbral JaCoCo de 80%.

## Risks / Trade-offs

- **Cambio breaking de contrato** → se documenta en README/OpenAPI y se publica como versión mayor; no se deja alias de `type` ni de `ENDED`.
- **Pérdida de información al eliminar `alerts.type`** → backfill completo a `alert_types`, respaldo previo y rollback documentado; la migración se prueba con datos reales.
- **Duplicados o conteos inflados en el filtro multi-tipo** → `distinct` en la consulta y pruebas de integración con varias alertas del mismo tipo.
- **N+1 al mapear tipos y confirmaciones** → `@BatchSize`, consulta única de confirmaciones por página e índices.
- **Enumeraciones de auditoría incompatibles con filas legadas** → enums de auditoría separados y prueba de lectura de `END`/`ENDED`.
- **Alertas archivadas sin `activatedAt`** → `NULLS LAST` y `confirmedAt` nulo; la bandeja no las trata como activas.
- **`manage_drill` aplicado de forma inconsistente** → el controlador usa `CAN_MANAGE_ALERTS` y el servicio valida el origen; pruebas para cada operación.
- **Enlace de correo depende del frontend** → la ruta `APP_BASE_URL/bandeja?alerta={id}` se documenta y el correo tolera una URL vacía.
- **Destinatarios con rol `ADMIN`** → no tienen `receive`; se documenta que deben usarse roles con permiso de recepción.
- **Correo y activación asíncronos** → se conserva el envío después del commit y el registro de entregas; no se promete entrega ni lectura.

## Migration Plan

1. Tomar respaldo de PostgreSQL y ejecutar `database/005_business_logic_update.sql` en staging.
2. Validar conteos, constraints e índices; ejecutar la suite de integración contra el esquema migrado.
3. Detener MS03, aplicar la migración en producción y arrancar la versión nueva. No hay despliegue mixto.
4. Humo: crear alerta con dos tipos, filtrar, programar, activar, consultar bandeja, confirmar, archivar y verificar el correo.
5. Rollback: restaurar el respaldo y desplegar la versión anterior; el rollback por SQL solo se usa si no hay respaldo y acepta la pérdida de tipos múltiples.

## Open Questions

Ninguna. Las decisiones pendientes de producto (ruta exacta del frontend, textos definitivos de la bandeja y política de acuses) quedan fuera del alcance y no cambian las specs ni las tareas.
