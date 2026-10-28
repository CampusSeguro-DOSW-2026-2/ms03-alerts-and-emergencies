# Estado del backend MS03 - Fase 1

- **Repositorio:** `ms03-alerts-and-emergencies`
- **Rama auditada:** `develop`
- **Commit:** `e056f7b` (`Merge pull request #3 from CampusSeguro-DOSW-2026-2/feature/docs`)
- **Fecha:** 2026-10-19
- **Alcance:** auditoría del backend y la base de datos contra `docs/Alertas_MS03_Backlog_Final_ES.md`. No se modificó código de producción.
- **Archivos preexistentes sin seguimiento:** `AGENTS.md`, `docs/Alertas_MS03_Backlog_Final_ES.md`.

## 1. Resumen ejecutivo

El backend compila y la suite actual pasa: `mvn clean verify` termina con `BUILD SUCCESS`, 127 pruebas y 0 fallos/errores.

Ninguna de las HUs backend auditadas alcanza el DoD completo: **11 quedan en estado Parcial y 2 en Falta**. El núcleo de creación, edición y parte del ciclo de vida existe, pero hay brechas críticas de contrato, activación, control de acceso, pruebas de integración y documentación.

Hallazgos críticos:

1. **HU 2.1.2 (activar) no cumple el flujo principal:** `activate()` solo acepta `SCHEDULED`; no permite `DRAFT → ACTIVE`, no es idempotente para una alerta ya `ACTIVE` y no usa el UPDATE condicional exigido por la tarea 2.1.2.1.
2. **No existe `AlertAccessPolicy`:** el usuario genérico autenticado no puede consultar una alerta aunque sea destinatario; los endpoints de lectura están limitados por rol fijo y `findById` no recibe el principal.
3. **El contrato HTTP actual no coincide con el backlog:** las acciones de ciclo de vida usan `PUT` y nombres en español (`/programacion`, `/activacion`, `/finalizacion`, `/archivado`), mientras el backlog define `PATCH /programar`, `/activar`, `/finalizar`, `/archivar`. Además `StateChangeResponse` existe pero no se usa.
4. **Faltan los artefactos de contrato:** no existen `docs/api/openapi.yaml` ni `docs/design/catalogo-errores.md`; por eso los puntos f) y h) de la auditoría no son verificables.
5. **La suite no cumple el DoD de pruebas:** no hay Testcontainers, pruebas de repositorio, prueba de contexto, pruebas reales de seguridad ni integración con PostgreSQL. La cobertura total de línea es 77,11 %, por debajo del 80 %.
6. **Notificaciones y consulta de auditoría no existen:** las HUs 3.1.1 y 5.2.2 no tienen implementación backend.

## 2. Build, pruebas y cobertura

Comando ejecutado:

```text
mvn clean verify
```

Resultado:

| Métrica | Valor |
|---|---|
| Resultado Maven | `BUILD SUCCESS` |
| Pruebas ejecutadas | 127 |
| Fallos / errores | 0 / 0 |
| Clases de prueba | 8 |
| Cobertura de línea | 77,11 % (586/760) |
| Cobertura de instrucción | 78,72 % (2911/3698) |
| Cobertura de rama | 57,81 % (111/192) |
| Cobertura de método | 76,15 % (198/260) |

Evidencia:

- `target/surefire-reports/`
- `target/site/jacoco/index.html`
- `target/site/jacoco/jacoco.csv`

La suite actual es unitaria:

- `AlertServiceImplTest` y `AlertLifecycleServiceImplTest` con Mockito.
- `AlertControllerTest` con MockMvc standalone.
- `GlobalExceptionHandlerTest`, `AlertMapperTest`, `ActorResolverTest`, `StubCatalogClientTest`.
- `AlertsApplicationTest` no es una prueba JUnit: es una clase con `main` duplicado.

Brechas de prueba:

- No hay `@SpringBootTest` ni prueba de contexto.
- No hay Testcontainers ni pruebas de repositorio (`AlertRepositoryIT`, `ConfirmationRepositoryIT`, etc.).
- No hay WireMock ni `RestCatalogClientTest`.
- `AlertControllerTest` usa `MockMvcBuilders.standaloneSetup(...)`; por diseño no evalúa `@PreAuthorize` ni Spring Security.
- No hay prueba de concurrencia ni de bloqueo optimista real contra PostgreSQL.
- No hay prueba de scheduler ni de aislamiento de errores por alerta.
- No hay ArchUnit ni prueba de rollback de auditoría.
- No hay umbral de cobertura en `pom.xml`; JaCoCo solo genera reporte.

## 3. Estado por HU

Criterio de estado:

- **Completa:** todas las tareas backend/BD/pruebas/documentación aplicables están implementadas y verificadas.
- **Parcial:** existe implementación funcional, pero faltan tareas o el DoD backend no se cumple.
- **Falta:** no existe implementación backend.

Las tareas `[FE]` no se evalúan porque el repositorio no contiene `frontend/`. Se listan solo cuando el contrato o la HU dependen de ellas.

| HU | Estado backend | Implementado | Tareas faltantes o bloqueos principales |
|---|---|---|---|
| 0.2.1 Base PostgreSQL | Parcial | Docker Compose con `postgres:17-alpine`, volumen, healthcheck, init scripts; `001_create_schema.sql` con seis tablas y restricciones; `002_indexes.sql` con únicos e índices | 0.2.1.1 estructura raíz (`backend/`, `frontend/`, `docs/design/`); 0.2.1.2 `.env.example`; 0.2.1.5 `003_sample_data.sql`; 0.2.1.6 documentar reejecución con volumen nuevo; 0.2.1.7 evidencia `psql`; 0.2.1.8 comandos de BD en README |
| 0.2.2 Esqueleto backend | Parcial | Java/Spring Boot, capas, JPA, Security OAuth2/JWT, `ddl-auto=validate`, CORS, `Clock`, `ErrorResponse`, handler global, 401/403 | 0.2.2.1 Actuator, springdoc, Testcontainers, WireMock; 0.2.2.3 perfil `test` y variables `RESEND_*`/`MS01_URL`/`MS02_URL`/`APP_BASE_URL`; 0.2.2.4 `NotificationDeliveryEntity` y `ConfirmationEntity`; 0.2.2.5 `AlertNotActiveException`, `NotARecipientException`, `SimulationMismatchException`, `EmailSendException`; 0.2.2.6 handlers faltantes; 0.2.2.8 `OpenApiConfig`; 0.2.2.9 Actuator/MDC; 0.2.2.10 prueba de contexto; 0.2.2.11 pruebas de seguridad |
| 1.1.1 Crear alerta | Parcial | Request/response, validaciones, repositorio, mapper, service transaccional, estado `DRAFT`, origen `MANUAL`, auditoría, controller POST 201 + `Location`, handlers 400/503 | 1.1.1.3 `RestCatalogClient` (solo existe stub local/test); 1.1.1.7 `@Operation`/`@ApiResponse`; 1.1.1.13 caso 503; 1.1.1.14 pruebas de seguridad/negativas; 1.1.1.15 WireMock; 1.1.1.16 `AlertRepositoryIT`; tareas FE 1.1.1.8-1.1.1.12 y 1.1.1.17 |
| 1.2.1 Editar alerta | Parcial | `AlertUpdateRequest` con `@AtLeastOneField`, reglas de editabilidad, `update` transaccional, revalidación de referencias cambiadas, reemplazo de alcance/destinatarios, PUT 200, 409 por versión | 1.2.1.5 documentación OpenAPI; 1.2.1.10 cobertura parcial de escenarios; 1.2.1.11 pruebas de controller 404/409/403; 1.2.1.12 `AlertRepositoryIT`; tareas FE 1.2.1.6-1.2.1.9 y 1.2.1.13 |
| 2.1.1 Programar alerta | Parcial | `AlertStateMachine`, `AlertScheduleRequest`, `schedule` transaccional, validación de fecha futura, `activation_at`, estado `SCHEDULED`, auditoría | 2.1.1.2 `StateChangeResponse` con campos incorrectos y sin uso; 2.1.1.4 ruta/método (`PUT /programacion` vs `PATCH /programar`); 2.1.1.5 OpenAPI; 2.1.1.10 `AlertStateMachineTest`; 2.1.1.12 pruebas de controller para 400/409/404/403; tareas FE 2.1.1.6-2.1.1.9 y 2.1.1.13 |
| 2.1.2 Activar alerta | Parcial (crítico) | Activación desde `SCHEDULED`, `activated_at`, auditoría con estado de origen | 2.1.2.1 UPDATE condicional `status IN ('DRAFT','SCHEDULED')`; 2.1.2.2 activación desde `DRAFT`, idempotencia, relectura y `AlertActivatedEvent`; 2.1.2.3 actor `SYSTEM_SCHEDULER` y aislamiento de errores por alerta; 2.1.2.4 `PATCH /activar` y permiso `SERVICE_MS06`; 2.1.2.5 OpenAPI; 2.1.2.7 pruebas de service; 2.1.2.8 prueba de scheduler; 2.1.2.9 prueba de concurrencia; 2.1.2.10 pruebas de controller; tarea FE 2.1.2.6 |
| 2.1.3 Finalizar alerta | Parcial | `end` transaccional, idempotente si ya está `ENDED`, `ended_at`, auditoría | 2.1.3.2 `PATCH /finalizar` y permiso `SERVICE_MS06`; 2.1.3.3 OpenAPI; 2.1.3.5 cobertura de auditoría; 2.1.3.6 pruebas de controller 409/404/MS06; tareas FE 2.1.3.4 y 2.1.3.7 |
| 2.1.4 Archivar alerta | Parcial | `archive` transaccional, `archived_at`, idempotente si ya está `ARCHIVED`, auditoría | 2.1.4.2 `assertNotArchived` existe pero no se usa en update/schedule/activate/end; 2.1.4.3 `PATCH /archivar`; 2.1.4.4 OpenAPI; 2.1.4.6 pruebas parametrizadas de operaciones sobre archivada; 2.1.4.7 pruebas de controller; tarea FE 2.1.4.5 |
| 5.1.1 Consultar alerta | Parcial | `AlertResponse` completo, mapper, `findById`, GET por id, 404 | 5.1.1.2 `findById(id, principal)` + `AlertAccessPolicy`; 5.1.1.3 `@EntityGraph`/`JOIN FETCH` y prueba de consultas; 5.1.1.4 acceso para usuario genérico autenticado; 5.1.1.5 OpenAPI; 5.1.1.8 prueba `NOT_A_RECIPIENT`; 5.1.1.9 pruebas 403/401; tareas FE 5.1.1.6-5.1.1.7 y 5.1.1.10 |
| 5.1.2 Listar alertas | Parcial | `AlertFilter`, `PageResponse`, `AlertSummaryResponse`, Specifications, validación de paginación/fechas, orden descendente, paginación | 5.1.2.1 `AlertFilter` incluye `tipo` no previsto y `PageResponse` usa `page`/`size` en lugar de `página`/`tamaño`; 5.1.2.4 permiso extra `SERVICE_MS06`; 5.1.2.5 OpenAPI; 5.1.2.10 pruebas de filtros; 5.1.2.11 pruebas de controller faltantes; 5.1.2.12 `AlertRepositoryIT` + `EXPLAIN`; tareas FE 5.1.2.6-5.1.2.9 y 5.1.2.13 |
| 5.2.1 Registrar auditoría | Parcial | `AuditEventEntity`, repositorio de solo inserción, `AuditService.record`, `ActorResolver` para usuario/servicio, auditoría en create/update/lifecycle | 5.2.1.1 falta acción de resumen de notificaciones enviadas y consultas en el repositorio; 5.2.1.2 `SYSTEM_SCHEDULER` no resuelto por `ActorResolver`; 5.2.1.3 usa `MANDATORY` en vez de `REQUIRED` y falta auditoría de notificaciones; 5.2.1.4 `NotificationService` ausente; 5.2.1.5 falta prueba de propagación de fallo; 5.2.1.6 prueba de rollback; 5.2.1.7 ArchUnit |
| 5.2.2 Consultar auditoría | Falta | Solo existe la entidad y el repositorio de inserción | 5.2.2.1-5.2.2.3 `AuditEventResponse`, `AuditQueryService`, endpoint y permisos; 5.2.2.6-5.2.2.7 pruebas; tareas FE 5.2.2.4-5.2.2.5 y 5.2.2.8 |
| 3.1.1 Notificaciones por correo | Falta | Solo tablas `notification_deliveries`/`alert_confirmations` y acciones de auditoría relacionadas | 3.1.1.1 configuración del proveedor; 3.1.1.2-3.1.1.7 `EmailSender`, plantilla, resolución de destinatarios, `NotificationService`, manejo de fallos y documentación; 3.1.1.8-3.1.1.12 pruebas; 3.1.1.13 evidencia de envío real |

## 4. Desviaciones de arquitectura y contrato

### 4.1 Estructura y stack

- No existe `backend/` ni `frontend/`; el backend está directamente en `src/` y la base en `database/`. La tarea 0.2.1.1 no se cumple.
- `pom.xml` usa **Java 21** y **Spring Boot 4.1.1**; el backlog y `AGENTS.md` indican Java 17.
- No hay Maven Wrapper (`mvnw`), pero el README documenta `./mvnw`. Los comandos reales son `mvn ...`.
- Spring Boot 4 usa Jackson 3 (`tools.jackson.*`); el stub ya lo utiliza.
- No existen `docs/api/openapi.yaml` ni `docs/design/catalogo-errores.md`. `docs/design/` tampoco existe; hay `docs/images/` con diagramas y `docs/evidence/` con documentos.

### 4.2 Contrato HTTP

| Acción | Backlog | Implementación actual | Estado |
|---|---|---|---|
| Crear | `POST /api/v1/alertas` | `POST /api/v1/alertas` | Coincide |
| Editar | `PUT /api/v1/alertas/{id}` | `PUT /api/v1/alertas/{id}` | Coincide |
| Consultar | `GET /api/v1/alertas/{id}` | `GET /api/v1/alertas/{id}` | Coincide en ruta |
| Listar | `GET /api/v1/alertas` | `GET /api/v1/alertas` | Coincide en ruta |
| Programar | `PATCH /api/v1/alertas/{id}/programar` | `PUT /api/v1/alertas/{id}/programacion` | No coincide |
| Activar | `PATCH /api/v1/alertas/{id}/activar` | `PUT /api/v1/alertas/{id}/activacion` | No coincide; solo coordinador |
| Finalizar | `PATCH /api/v1/alertas/{id}/finalizar` | `PUT /api/v1/alertas/{id}/finalizacion` | No coincide; solo coordinador |
| Archivar | `PATCH /api/v1/alertas/{id}/archivar` | `PUT /api/v1/alertas/{id}/archivado` | No coincide |
| Confirmar | `POST /api/v1/alertas/{id}/confirmaciones` | No existe | Falta |
| Auditoría | `GET /api/v1/alertas/{id}/auditoria` | No existe | Falta |

Otros desajustes:

- `StateChangeResponse` existe, pero sus campos son `id`, `estado`, `fechaActivacionProgramada`; el backlog pide `idAlerta`, `estadoAnterior`, `estadoNuevo`, `fechaCambio`. No se usa: los endpoints de ciclo de vida devuelven `AlertResponse`.
- `PageResponse` usa `page` y `size`; el backlog pide `página` y `tamaño` junto con `contenido`, `totalElementos` y `totalPaginas`.
- `AlertFilter` incluye `tipo`, no previsto en la tarea 5.1.2.1. Sin `openapi.yaml` no se puede confirmar si es un campo adicional permitido.
- `AlertType` solo define `EARTHQUAKE`; la tarea FE 1.1.1.10 describe Sismo, Simulacro, Incendio y Otro. Falta confirmar el contrato antes de ampliar el enum.
- No hay anotaciones `@Operation`/`@ApiResponse` ni Swagger/springdoc.

### 4.3 Seguridad y acceso

- `Role` contiene solo `COORDINATOR`, `AUDITOR` y `SERVICE_MS06`; no existe `RECIPIENT`, lo cual coincide con el backlog.
- No existe `AlertAccessPolicy`. Por tanto, el acceso de un usuario genérico autenticado a una alerta no se resuelve por destinatario.
- `AlertController` protege la lectura con `hasAnyRole('COORDINATOR','AUDITOR','SERVICE_MS06')`; un usuario genérico recibe 403 antes de llegar al service, aunque sea destinatario.
- `AlertServiceImpl.findById(UUID)` no recibe principal ni aplica política de acceso.
- `SERVICE_MS06` puede listar alertas, permiso no previsto en la tarea 5.1.2.4 (que indica coordinador y auditor).
- `SERVICE_MS06` no puede activar ni finalizar: el controller usa `COORDINATOR` para todas las acciones de ciclo de vida.
- `ActorResolver` resuelve usuario y servicio, pero no devuelve `SYSTEM_SCHEDULER`; el scheduler pasa el literal `"system"`.
- El repositorio de auditoría (`AuditEventRepository`) solo expone `save`; no hay consultas para la HU 5.2.2.

### 4.4 Persistencia y dominio

- `AlertEntity` tiene `@Version`, y `findForUpdate` usa `LockModeType.OPTIMISTIC_FORCE_INCREMENT`.
- `database/002_indexes.sql` sí define el índice único parcial `uq_alerts_simulation_live` para `SCHEDULED`/`ACTIVE`, y los únicos de confirmaciones y entregas.
- `schedule`, `activate`, `end` y `archive` bloquean con `findForUpdate`, pero no usan el UPDATE condicional exigido para activación.
- `AlertStateRules.assertNotArchived` existe, pero no se invoca en los flujos de edición/ciclo de vida.
- `findById` carga alcance y destinatarios con consultas separadas; no hay `@EntityGraph` ni `JOIN FETCH`, por lo que el riesgo N+1 de la tarea 5.1.1.3 sigue abierto.
- `AlertSpecifications` implementa estado, tipo, rango de fechas y `simulationId`; el rango se interpreta en la zona del `Clock`.
- `AlertServiceImpl.update` usa `findForUpdate` y `assertEditable`; el 409 por versión está cubierto por el handler, pero no hay prueba de concurrencia.
- `BaseEntity` usa `Instant.now()` como respaldo en `@PrePersist`; los services normalmente asignan fechas con `Clock`. Es un riesgo menor de determinismo en pruebas.

### 4.5 Auditoría

- `AuditService.record` usa `Propagation.MANDATORY`; la tarea 5.2.1.3 pide `REQUIRED`. Para las llamadas actuales el efecto es equivalente (debe existir transacción), pero es una desviación literal.
- La auditoría de creación, edición y ciclo de vida se registra con `fromStatus`, `toStatus`, actor y detalle.
- El scheduler registra actor `"system"` en `AlertLifecycleServiceImpl.activateScheduledAlerts`, no `SYSTEM_SCHEDULER`.
- No hay evento `AlertActivatedEvent` ni auditoría de notificaciones porque no existe `NotificationService`.
- `AuditAction` no tiene una acción de resumen de notificaciones enviadas; `database/001_create_schema.sql` tampoco la incluye en el `CHECK`.
- `AuditServiceImplTest` verifica la anotación `MANDATORY`, pero no prueba que un fallo de auditoría revierta la operación ni que se propague la excepción.

### 4.6 Notificaciones y correo

- No existe `EmailSender`, `SmtpEmailSender`/`ResendEmailSender`, `EmailTemplateBuilder`, `RecipientResolverService`, `NotificationService`, `NotificationDeliveryRepository` ni `ConfirmationService`.
- No hay dependencia de correo en `pom.xml`.
- `CatalogClient` ya expone `getUsersByRole`, pero solo hay `StubCatalogClient` para `local`/`test`; no hay cliente real hacia MS01/MS02.
- Las tablas `notification_deliveries` y `alert_confirmations` existen, pero no tienen entidades JPA.
- El backlog pide Resend; la decisión posterior de Fase 3 es SMTP genérico con Brevo inicial. Esa decisión debe reflejarse en backlog/contrato antes de implementar.

### 4.7 Documentación y operación

- No hay `docs/api/openapi.yaml`, `docs/design/catalogo-errores.md` ni `docs/design/`.
- El README no documenta los comandos de PostgreSQL exigidos por 0.2.1.8; además referencia Maven Wrapper inexistente.
- No hay `.env.example`; existe `.env` local ignorado por Git.
- No hay evidencia `psql` de restricciones ni carga de datos de ejemplo.
- No se ejecutó `docker compose up` durante esta auditoría; la validación de la base fue estática (scripts y configuración).

## 5. Verificación de puntos críticos a-h

| Punto | Veredicto | Evidencia |
|---|---|---|
| a) `DRAFT → ACTIVE` y `SCHEDULED → ACTIVE`; UPDATE condicional | **No cumple** | `AlertStateMachine` permite ambas transiciones, pero `AlertLifecycleServiceImpl.activate()` rechaza todo lo que no sea `SCHEDULED` (`AlertLifecycleServiceImpl.java:98-100`). `AlertRepository` no tiene UPDATE condicional; solo `findForUpdate` y la consulta del scheduler (`AlertRepository.java:18-22`). |
| b) Scheduler solo procesa `SCHEDULED`; no activa `DRAFT`; tolera fallos por alerta | **Parcial** | Consulta `findByStatusAndActivationAtLessThanEqual(SCHEDULED, now)` (`AlertLifecycleServiceImpl.java:131-132`), por lo que no toca `DRAFT`. Pero el loop no captura errores por alerta y no usa `SYSTEM_SCHEDULER`. No hay prueba de scheduler. |
| c) Sin rol `RECIPIENT`; acceso genérico por `AlertAccessPolicy` | **Parcial** | `Role` no tiene `RECIPIENT` (`Role.java:6-9`), correcto. No existe `AlertAccessPolicy`; `findById` no recibe principal y el controller exige roles fijos (`AlertController.java:36-37,74-77`). |
| d) Auditoría en la misma transacción; origen de activación; actor del scheduler | **Parcial** | `AuditService.record` es `MANDATORY` (`AuditServiceImpl.java:29-43`) y las operaciones son transaccionales; se guarda `previousStatus`. El scheduler registra `"system"` (`AlertLifecycleServiceImpl.java:145-151`), no `SYSTEM_SCHEDULER`; no hay prueba de rollback. |
| e) Bloqueo optimista y 409 `CONCURRENT_MODIFICATION` | **Cumple en código; falta prueba real** | `@Version` en `AlertEntity.java:59-61`; `findForUpdate` con `OPTIMISTIC_FORCE_INCREMENT` (`AlertRepository.java:18-20`); handler de `OptimisticLockingFailureException` a 409 (`GlobalExceptionHandler.java:74-77`). No hay prueba de concurrencia contra PostgreSQL. |
| f) Catálogo de errores y handlers | **No verificable; hay brechas** | Falta `docs/design/catalogo-errores.md`. `ErrorCode` contiene `ALERT_NOT_ACTIVE`, `NOT_A_RECIPIENT`, `SIMULATION_MISMATCH`, `DUPLICATE_SIMULATION_ALERT`, pero no existen `AlertNotActiveException`, `NotARecipientException`, `SimulationMismatchException` ni `EmailSendException`; tampoco sus handlers dedicados. |
| g) Único condicional por `simulation_id` para `SCHEDULED`/`ACTIVE` | **Cumple** | `database/002_indexes.sql:19-27` define `uq_alerts_simulation_live` con `WHERE simulation_id IS NOT NULL AND status IN ('SCHEDULED','ACTIVE')`. |
| h) Contrato OpenAPI y rutas del backlog | **No verificable / no cumple** | Falta `docs/api/openapi.yaml`. Las rutas actuales de ciclo de vida usan `PUT` y nombres en español; el backlog pide `PATCH` y nombres en infinitivo. `StateChangeResponse` no se usa. |

## 6. Prioridades recomendadas

### P0 - Antes de cerrar el ciclo de vida del Sprint 1

1. Corregir HU 2.1.2:
   - UPDATE condicional con `status IN ('DRAFT','SCHEDULED')`.
   - Activación desde `DRAFT` y `SCHEDULED`.
   - Idempotencia para alerta ya `ACTIVE`.
   - Actor `SYSTEM_SCHEDULER`.
   - Aislamiento de errores por alerta en el scheduler.
   - Evento `AlertActivatedEvent`.
   - Pruebas de service, scheduler, controller y concurrencia con Testcontainers.
2. Alinear el contrato HTTP:
   - `PATCH` para programar/activar/finalizar/archivar.
   - `StateChangeResponse` con los campos del backlog y uso real.
   - `PageResponse` y `AlertFilter` según contrato.
   - `AlertType` y zona horaria de programación.
   - Crear `docs/api/openapi.yaml` y `docs/design/catalogo-errores.md`.
3. Implementar `AlertAccessPolicy` y `findById(id, principal)`:
   - Usuario genérico destinatario con acceso.
   - 403 `NOT_A_RECIPIENT` para no destinatario.
   - Eliminar la restricción de rol fijo en lectura.
   - Resolver N+1 con `@EntityGraph`/`JOIN FETCH` y prueba de consultas.
4. Completar infraestructura de pruebas:
   - Testcontainers PostgreSQL.
   - Pruebas de repositorio y contexto.
   - Pruebas reales de seguridad 401/403.
   - WireMock para el catálogo.
   - Cobertura nueva ≥ 80 % y umbral JaCoCo.

### P1 - Completar HUs 0, 1, 2 y 5

1. 0.2.1: `.env.example`, `003_sample_data.sql`, comandos de README, evidencia `psql` y estructura raíz.
2. 0.2.2: Actuator, springdoc/OpenAPI, perfil `test`, entidades de notificación/confirmación, excepciones y handlers faltantes, MDC y pruebas de contexto/seguridad.
3. 1.1.1: `RestCatalogClient` con timeouts, documentación OpenAPI y pruebas WireMock/repositorio.
4. 1.2.1: pruebas de controller/repositorio y documentación.
5. 2.1.3 y 2.1.4: `PATCH`, permiso `SERVICE_MS06` donde aplica, uso de `assertNotArchived` y pruebas.
6. 5.1.2: corregir nombres de paginación, quitar o documentar `tipo`, pruebas de filtros y `AlertRepositoryIT`.
7. 5.2.1: `SYSTEM_SCHEDULER`, propagación `REQUIRED`, acción de resumen de notificaciones, auditoría de notificaciones y pruebas de rollback/ArchUnit.
8. 5.2.2: `AuditEventResponse`, `AuditQueryService`, endpoint y pruebas.

### P2 - Fase 3 / Sprint 2

1. Implementar 3.1.1 con SMTP genérico y Brevo inicial (decisión de Fase 3), no Resend.
2. `EmailSender`, `SmtpEmailSender`, `LoggingEmailSender` para local/test.
3. `EmailTemplateBuilder` con escape de HTML y versión texto plano.
4. `RecipientResolverService`, `NotificationService` asíncrono, `NotificationDeliveryRepository`.
5. Reintentos limitados, límite de 300 correos/día, idempotencia por destinatario y auditoría de resumen.
6. Pruebas unitarias, WireMock/SMTP simulado, integración con Testcontainers y evidencia de envío real.

## 7. Decisiones pendientes antes de Fase 2

1. **Reglas de negocio 2, 4 y 8:** el backlog las marca como PENDIENTES. No se deben implementar ni suponer.
2. **Duplicado de simulación:** el índice único está definido, pero falta decidir si el reintento de MS06 responde 409 `DUPLICATE_SIMULATION_ALERT` o 200 con la alerta existente. El backlog deja ambas posibilidades abiertas.
3. **Contrato:** entregar o autorizar la creación de `docs/api/openapi.yaml` y `docs/design/catalogo-errores.md`; sin ellos no se puede cerrar f) ni h).
4. **Proveedor de correo:** el backlog dice Resend; la decisión de Fase 3 es SMTP/Brevo. Actualizar el backlog/contrato antes de implementar.
5. **Tipos de alerta:** confirmar si el backend debe soportar solo `EARTHQUAKE` o los cuatro tipos de la UI.
6. **Alcance de la auditoría:** confirmar si las tareas `[FE]` y `[INT]` deben considerarse para declarar una HU “Completa” o si esta fase se limita al backend/BD.

## 8. Comandos y evidencia utilizados

```text
mvn clean verify
git branch --show-current
git log -1 --oneline
git status --short
```

Resultados principales:

- Rama: `develop`.
- Commit: `e056f7b`.
- `mvn clean verify`: 127 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`.
- Working tree antes de este informe: solo `AGENTS.md` y `docs/Alertas_MS03_Backlog_Final_ES.md` sin seguimiento.
- No se modificó código de producción, `pom.xml`, SQL ni configuración.
