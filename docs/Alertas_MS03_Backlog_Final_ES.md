# Alertas (MS03) - Backlog de Jira (versión final: v3 + correcciones de v4)

**Grupo:** Zephyros · **Proyecto:** CampusSeguro · **Microservicio:** MS03 - Alertas y Emergencias

---

## Registro de cambios respecto a v3

| # | Problema detectado en v3 | Corrección aplicada |
|---|---|---|
| 1 | **Crítico.** La máquina de estados solo permitía `Programada → Activa`: no había forma de activar una alerta de inmediato (ni el coordinador ni MS06). | Se agrega la transición `Borrador → Activa` (activación manual/inmediata), manteniendo `Programada → Activa` (scheduler o activación anticipada). Ver Convenciones, 0.1.1.7, 0.1.1.8, 0.1.2.1, HU 2.1.2. |
| 2 | Sin validación de duplicados por `simulation_id`: MS06 podía crear alertas repetidas ante un reintento. | Restricción de unicidad condicional en BD (0.1.1.3), comportamiento idempotente en HU 4.1.1 y filtro `simulationId` en el listado (HU 5.1.2). |
| 3 | El rol `RECIPIENT` del JWT no existe en el catálogo de roles de MS01. | Se elimina `RECIPIENT`. Solo `COORDINATOR`, `AUDITOR` y `SERVICE_MS06` tienen privilegio; el resto de usuarios autenticados se trata como "usuario genérico" y `AlertAccessPolicy` decide el acceso por alerta. **PENDIENTE** confirmar con MS01 los nombres reales de rol. |
| 4 | Las reglas de negocio 2, 4 y 8 se citaban pero no están documentadas en ningún artefacto disponible. | Se marcan como **PENDIENTE** (no se inventan). Deben traerse antes del Gate de diseño. |
| 5 | Resend (en vez de FCM) y React sin TypeScript eran desviaciones silenciosas del stack del curso. | Se documentan como decisiones explícitas de equipo (sección "Decisiones de arquitectura a confirmar"). |

---

## Alcance por sprint

El Sprint 1 cubre lo que el squad puede construir y demostrar **sin depender de que otro squad tenga su microservicio real listo** (MS01 y MS02 se consumen con stubs acordados por contrato).

**Sprint 1:** Épica 0 completa (diseño + Gate + base técnica), Épica 1, Épica 2 (ciclo de vida completo con las dos rutas hacia Activa) y Épica 5 (consultar, listar y auditoría; la auditoría se construye desde la primera HU).

**Sprint 2:** Épica 3 (notificación por correo: depende de la cuenta de Resend y la plantilla aprobadas; no bloquea el ciclo de vida, y puede adelantarse si hay capacidad), Épica 4 (integración con MS06: depende de que MS06 exista para la prueba conjunta) y Épica 6 (verificación y cierre).

---

## Convenciones

**Estados** (en pantalla / en código): Borrador `DRAFT`, Programada `SCHEDULED`, Activa `ACTIVE`, Finalizada `ENDED`, Archivada `ARCHIVED`.

**Transiciones válidas:**

```
DRAFT      → SCHEDULED   (programar, requiere fecha futura)
DRAFT      → ACTIVE      (activación manual inmediata, o MS06 con activación inmediata)
SCHEDULED  → ACTIVE      (scheduler al llegar la fecha, o activación manual anticipada)
ACTIVE     → ENDED       (finalizar, manual o por MS06)
ENDED      → ARCHIVED    (archivar)
```

Toda transición no listada responde `409 INVALID_TRANSITION`. Una alerta `ARCHIVED` no admite ninguna modificación.

**Roles:** solo `COORDINATOR`, `AUDITOR` y `SERVICE_MS06` son roles con privilegio. Cualquier otro usuario autenticado es un "usuario genérico": su acceso a una alerta (`GET /alertas/{id}`, `POST .../confirmaciones`) lo decide `AlertAccessPolicy`, no un rol fijo del JWT.

**Stack** (basado en el laboratorio ToDo Full Stack): Java 17 + Spring Boot + Maven, API REST, Spring Data JPA, PostgreSQL 17 en Docker (imagen oficial `postgres:17-alpine`), React + Vite, Resend para correo. Repositorio único con `backend/`, `frontend/`, `database/` y `docs/`.

**Herramientas de diseño:** diagramas en **LucidChart**, mockups en **Figma**. Todo artefacto se exporta a `docs/design/` y se enlaza en el README. **No se usan diagramas de secuencia.**

**Arquitectura del backend:** `controller → service (interfaz + Impl) → repository → entity`, con `dto`, `exception`, `config`, `client` (MS01/MS02/Resend) y `mapper`. El controller nunca accede al repository ni contiene lógica de negocio.

**Etiquetas de tareas:** `[DISEÑO]` diagramas, mockups y contratos · `[BD]` base de datos · `[BE]` backend · `[FE]` frontend · `[PRUEBAS]` pruebas · `[CALIDAD]` análisis estático/dinámico y pipeline · `[DOCS]` documentación · `[INT]` integración con otros squads · `[OPS]` configuración y despliegue local

**Fases del proyecto:**

| Fase | Contenido | Condición para avanzar |
|---|---|---|
| **1. Diseño** | Épica 0, Feature 0.1 (HU 0.1.1 a 0.1.4): todos los diagramas, el contrato OpenAPI, los mockups y el plan de pruebas | Revisión de diseño aprobada (ver "Gate de diseño") |
| **2. Implementación** | Épica 0 Feature 0.2 y Épicas 1 a 5 | Cada HU cumple el DoD |
| **3. Verificación** | Épica 6: análisis estático, análisis dinámico, E2E y evidencias | Quality gate en verde |

---

## Definition of Ready (DoR) general

Una HU de implementación entra a un sprint cuando:

- El requerimiento relacionado (Feature) fue revisado y acordado con el equipo.
- Los artefactos de diseño que le aplican (diagramas en LucidChart, contrato OpenAPI, mockups en Figma) están **aprobados en el Gate de diseño**.
- Los campos, las reglas de validación y los códigos de error de la HU están definidos en el contrato OpenAPI y en el catálogo de errores.
- Las dependencias con MS01, MS02, MS06 o Resend están identificadas, y el contrato o un stub está disponible.
- Los criterios de aceptación son claros y verificables.

## Definition of Done (DoD) general

Una HU está terminada cuando:

- La funcionalidad está entregada de punta a punta (API + pantalla si aplica), cumple los criterios de aceptación y se puede demostrar en el ambiente local con Docker.
- La implementación respeta la arquitectura por capas y coincide con el diseño y el contrato OpenAPI. Si algo cambió, el diseño se actualizó.
- Todos los errores esperados están manejados con excepciones propias y el `GlobalExceptionHandler`, y la respuesta usa el formato de error estándar. No hay `500` para situaciones que corresponden a `400`, `403`, `404` o `409`. En el frontend, cada error se muestra con un mensaje claro.
- Pruebas unitarias del service (Mockito), del controller (MockMvc) y de integración con PostgreSQL (Testcontainers) ejecutadas y aprobadas. Pruebas de componentes del frontend (Vitest + React Testing Library) cuando hay pantalla.
- Cobertura de código nueva ≥ 80 % (JaCoCo y cobertura de Vitest).
- Análisis estático en verde: el quality gate de SonarCloud pasa, sin bugs ni vulnerabilidades críticas, y sin dependencias con vulnerabilidades altas o críticas.
- Código revisado y aprobado mediante Pull Request, con el pipeline en verde.
- Endpoint documentado en Swagger/OpenAPI, coincidiendo con `docs/api/openapi.yaml`.
- Logs implementados sin datos sensibles. Sin secretos en el repositorio (API keys y tokens salen de variables de entorno).

---

## Épica 0: Diseño y fundamentos técnicos

Como equipo de desarrollo, quiero diseñar completamente la solución antes de implementarla y dejar listas las bases técnicas, para que las historias funcionales se construyan sobre un diseño aprobado y se entreguen funcionando.

### Feature 0.1: Diseño de la solución (Fase 1, sin RF)

Todos los artefactos de diseño se elaboran antes de escribir código de negocio.

#### HU 0.1.1: Diseño de datos y arquitectura (LucidChart)

Como equipo de desarrollo, quiero diseñar el modelo de datos y la arquitectura de MS03, para tener claro qué se va a construir y cómo se relacionan las partes.

**DoR**
- Requerimientos RF-ALT-01 a RF-ALT-07 revisados.
- Reglas de negocio 1, 3, 5, 6, 7 y 9 confirmadas. **Las reglas 2, 4 y 8 quedan como PENDIENTE** hasta recibir el documento completo de reglas de negocio: no se diseñan a ciegas.

**MoSCoW**
- Must Have: la base de datos y todas las capas dependen de este diseño.

**Criterios de aceptación**
- Dado que el diseño está terminado, cuando el equipo hace la revisión, entonces cada diagrama es consistente con los requerimientos y entre sí (los nombres de tablas, entidades y clases coinciden).
- Dado cada regla de negocio confirmada (1, 3, 5, 6, 7 y 9), entonces existe al menos un elemento del diseño que la hace cumplir (constraint, validación o estado). La cobertura es parcial (6 de 9) mientras las reglas 2, 4 y 8 sigan pendientes.
- Dado el diagrama de estados, entonces incluye explícitamente las transiciones `DRAFT→ACTIVE` y `SCHEDULED→ACTIVE` como dos caminos válidos distintos hacia Activa, cada uno con su disparador (coordinador, scheduler o MS06).

**DoD**
- Diagramas exportados a `docs/design/` y enlazados en el README.
- Aprobados en el Gate de diseño.

**Tareas**
- Tarea 0.1.1.1 `[DISEÑO]` Diagrama entidad-relación en LucidChart con las tablas `alerts`, `alert_scope_items`, `alert_recipients`, `notification_deliveries`, `alert_confirmations` y `audit_events`: PK (UUID), FK, cardinalidades, columnas de auditoría (`created_at`, `updated_at`), `version` en `alerts` para bloqueo optimista, y `simulation_id` nullable con índice.
- Tarea 0.1.1.2 `[DISEÑO]` Diccionario de datos por tabla: tipo, longitud (p. ej. `instructions VARCHAR(1000)`), nulabilidad, valores por defecto y `CHECK` (estado, tipo de alerta, tipo de alcance, origen, estado del envío). Incluir `version` en `alerts` para bloqueo optimista.
- Tarea 0.1.1.3 `[DISEÑO]` Definición de restricciones e índices que hacen cumplir las reglas: único (`alert_id`, `user_id`) en `alert_confirmations` (regla 6) y en `notification_deliveries` (regla 5), índice (`status`, `activation_at`) para el scheduler, índice por `created_at` para el listado e índice por `simulation_id` **con una restricción de unicidad condicional** (un `simulation_id` no puede tener más de una alerta en estado `SCHEDULED` o `ACTIVE` simultáneamente), para sostener la idempotencia de la integración con MS06.
- Tarea 0.1.1.4 `[DISEÑO]` Diagrama de arquitectura general: aplicación web (React), API Gateway, MS03, MS01, MS02, MS06, Resend y PostgreSQL, indicando protocolo y autenticación de cada conexión.
- Tarea 0.1.1.5 `[DISEÑO]` Diagrama de capas y paquetes del backend (`controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `exception`, `config`, `client`, `scheduler`) con las dependencias permitidas entre ellos.
- Tarea 0.1.1.6 `[DISEÑO]` Diagrama de clases: entidades JPA y enums (`AlertStatus`, `AlertType`, `ScopeType`, `RecipientType`, `AuditAction`), DTOs, interfaces de service y sus implementaciones, repositories, `EmailSender` y `CatalogClient` con sus implementaciones, y la jerarquía de excepciones.
- Tarea 0.1.1.7 `[DISEÑO]` Diagrama de estados del ciclo de vida con los **dos caminos hacia Activa**: `Borrador → Activa` (coordinador en activación manual inmediata, o MS06 al iniciar un simulacro ya mismo) y `Programada → Activa` (scheduler automático, o coordinador adelantando una alerta ya programada); además `Borrador → Programada`, `Activa → Finalizada` y `Finalizada → Archivada`. Indicar para cada transición quién la dispara, la condición y el efecto, y marcar en qué estados se permite editar.
- Tarea 0.1.1.8 `[DISEÑO]` Diagrama de flujo (no de secuencia) de la activación, cubriendo ambos caminos (`Borrador` y `Programada`): scheduler/manual/MS06 → cambio condicional de estado → evento → resolución de destinatarios → envío con Resend → registro del resultado y auditoría.
- Tarea 0.1.1.9 `[DISEÑO]` Diagrama de despliegue local: contenedor PostgreSQL con volumen, backend en `:8080`, frontend en `:5173`, y variables de entorno requeridas por cada uno.

#### HU 0.1.2: Diseño del contrato de la API y del manejo de errores

Como equipo de desarrollo, quiero definir primero el contrato de la API y el catálogo de errores, para que backend, frontend y los demás squads trabajen sobre el mismo acuerdo.

**DoR**
- HU 0.1.1 en revisión (el modelo de datos define los campos).

**MoSCoW**
- Must Have: sin contrato no se pueden construir en paralelo los endpoints y las pantallas.

**Criterios de aceptación**
- Dado el archivo `openapi.yaml`, cuando se valida con un linter de OpenAPI, entonces no tiene errores.
- Dado cada endpoint, entonces el contrato incluye ejemplos de solicitud y respuesta, y todos los códigos de error posibles con su ejemplo.
- Dado un error esperado cualquiera, entonces existe una fila en el catálogo de errores con código HTTP, excepción, código de error y mensaje.
- Dado el endpoint de activación, entonces el contrato distingue explícitamente el caso `Borrador → Activa` del caso `Programada → Activa` y documenta ambos.
- Dado el endpoint de creación por MS06, entonces el contrato documenta el comportamiento idempotente ante un `simulationId` repetido.

**DoD**
- `docs/api/openapi.yaml` y `docs/design/catalogo-errores.md` aprobados en el Gate de diseño.
- Contrato con MS06 revisado y aceptado por el squad de MS06 (aunque la integración en vivo se prueba en Sprint 2).

**Tareas**
- Tarea 0.1.2.1 `[DISEÑO]` Redactar `docs/api/openapi.yaml` (contract-first) con los 10 endpoints: `POST/GET/PUT /api/v1/alertas`, `GET /api/v1/alertas/{id}`, `PATCH .../programar`, `.../activar`, `.../finalizar`, `.../archivar`, `POST .../confirmaciones` y `GET .../auditoria`; incluye esquemas de request/response, paginación (con filtro `simulationId` en el listado) y seguridad bearer. El `PATCH .../activar` documenta ambos caminos (`DRAFT→ACTIVE` y `SCHEDULED→ACTIVE`) como 200 válidos.
- Tarea 0.1.2.2 `[DISEÑO]` Definir el formato de error estándar: `{timestamp, status, code, message, path, details[{field, message}]}`.
- Tarea 0.1.2.3 `[DISEÑO]` Catálogo de errores (tabla en `catalogo-errores.md`): `VALIDATION_ERROR` 400, `INVALID_REFERENCE` 400, `INVALID_PAGINATION` 400, `INVALID_ACTIVATION_DATE` 400, `UNAUTHORIZED` 401, `FORBIDDEN` / `NOT_A_RECIPIENT` 403, `ALERT_NOT_FOUND` 404, `INVALID_ALERT_STATE` 409, `INVALID_TRANSITION` 409, `ALERT_NOT_ACTIVE` 409, `SIMULATION_MISMATCH` 409, `DUPLICATE_SIMULATION_ALERT` 409 **(nuevo; ver punto abierto 1)**, `CONCURRENT_MODIFICATION` 409, `EXTERNAL_SERVICE_UNAVAILABLE` 503, `INTERNAL_ERROR` 500; para cada uno: excepción Java asociada, mensaje en español y qué muestra el frontend.
- Tarea 0.1.2.4 `[DISEÑO]` Matriz de validaciones por campo (tipo, alcance, instrucciones, destinatarios, origen, fechas, paginación): regla, mensaje y dónde se valida (Bean Validation, service o base de datos).
- Tarea 0.1.2.5 `[DISEÑO]` Matriz de roles por endpoint usando únicamente `COORDINATOR`, `AUDITOR` y `SERVICE_MS06` como roles con privilegio; cualquier otro usuario autenticado se trata como "usuario genérico", y su acceso a `GET /alertas/{id}` y a `POST /confirmaciones` depende de `AlertAccessPolicy` (si está listado como destinatario directo, pertenece a un rol destinatario de esa alerta, o tiene un registro de envío), no de un rol fijo del JWT.
- Tarea 0.1.2.6 `[DISEÑO]` Documento de contrato con MS01 (validar usuarios y roles, obtener usuarios con correo por rol; **incluir la pregunta explícita de qué nombres de rol emite el JWT**), MS02 (validar edificios/zonas) y MS06 (creación con activación inmediata y finalización, incluyendo el comportamiento idempotente por `simulationId`), con ejemplos JSON y el mecanismo de token de servicio.
- Tarea 0.1.2.7 `[INT]` Enviar y dejar en revisión los contratos con los squads de MS01, MS02 y MS06 (no bloquea el Sprint 1: se trabaja con stubs mientras se confirma).
- Tarea 0.1.2.8 `[DISEÑO]` Decisiones de diseño que cierran ambigüedades de los requerimientos (se documentan en `docs/design/decisiones.md`): campo de fecha usado por el filtro del listado, comportamiento ante filtros inválidos, que la auditoría se escribe en la misma transacción que la operación, y las dos rutas de activación.

#### HU 0.1.3: Diseño de interfaces en Figma

Como equipo de desarrollo, quiero diseñar en Figma todas las pantallas, estados y el correo, para implementar el frontend sin ambigüedades.

**DoR**
- Contrato OpenAPI en revisión (los campos y errores de cada pantalla son conocidos).

**MoSCoW**
- Must Have: el frontend de todas las HU se implementa a partir de estos mockups.

**Criterios de aceptación**
- Dado cada pantalla, entonces existen sus estados: cargando, vacío, error de red, error de validación y éxito.
- Dado cada error del catálogo que el usuario puede ver, entonces existe el mensaje en pantalla.
- Dado el prototipo navegable, entonces se puede recorrer el flujo completo (crear → programar → activar → finalizar → archivar → ver auditoría) y el flujo del destinatario.

**DoD**
- Archivo de Figma compartido con permisos para el equipo y enlazado en el README. Mockups exportados a `docs/design/ui/`.
- Aprobados en el Gate de diseño.

**Tareas**
- Tarea 0.1.3.1 `[DISEÑO]` Mapa de navegación y flujos de usuario del coordinador, el auditor y el destinatario.
- Tarea 0.1.3.2 `[DISEÑO]` Sistema de diseño en Figma: colores, tipografía, botones, campos, selectores, badges de estado (un color por cada estado de alerta), toasts, banners de error, diálogos de confirmación y esqueletos de carga.
- Tarea 0.1.3.3 `[DISEÑO]` Mockup del dashboard de alertas: tabla (tipo, estado, alcance, origen, fecha de creación, acción), filtros por estado y rango de fechas, paginación, botón "Nueva alerta" y los estados cargando / sin resultados / error con botón reintentar.
- Tarea 0.1.3.4 `[DISEÑO]` Mockup del formulario de creación: select de tipo, selector de alcance (campus completo / edificios específicos con multiselección), textarea de instrucciones con contador de caracteres 0/1000, selector de destinatarios (usuarios y roles) con chips, y errores bajo cada campo.
- Tarea 0.1.3.5 `[DISEÑO]` Mockup del modo edición (mismo formulario precargado) y del estado no editable (alerta Activa, Finalizada o Archivada).
- Tarea 0.1.3.6 `[DISEÑO]` Mockup del detalle de alerta en cada uno de sus cinco estados: datos, alcance, destinatarios, fechas, origen, botones de acción válidos por estado y panel de auditoría.
- Tarea 0.1.3.7 `[DISEÑO]` Mockups de los diálogos: programar (fecha y hora), activar, finalizar y archivar, con el texto de advertencia de cada uno. El diálogo de "Activar" deja claro si la alerta pasará de Borrador o de Programada a Activa.
- Tarea 0.1.3.8 `[DISEÑO]` Mockup de la vista del destinatario (versión móvil): alerta activa sin confirmar, ya confirmada, alerta ya no vigente, sin permiso y no encontrada.
- Tarea 0.1.3.9 `[DISEÑO]` Mockup del panel de línea de tiempo de auditoría (con estado vacío y de error).
- Tarea 0.1.3.10 `[DISEÑO]` Diseño de la plantilla del correo (vista de escritorio y móvil): tipo de alerta, instrucciones, alcance y botón "Ver y confirmar alerta".
- Tarea 0.1.3.11 `[DISEÑO]` Prototipo navegable en Figma de los flujos principales.

#### HU 0.1.4: Plan de pruebas y de calidad

Como equipo de desarrollo, quiero definir antes de implementar cómo vamos a probar y a analizar la calidad del código, para que las pruebas y los análisis formen parte del desarrollo desde el primer endpoint.

**DoR**
- Matriz de validaciones (0.1.2.4) y catálogo de errores (0.1.2.3) disponibles.

**MoSCoW**
- Must Have: define el criterio de calidad de todas las HU.

**Criterios de aceptación**
- Dado cada requerimiento RF-ALT-01 a RF-ALT-07, entonces tiene casos de prueba identificados en la matriz de pruebas, con el nivel en que se prueba (unitaria, integración, E2E).
- Dado cada herramienta de análisis, entonces tiene definido umbral de aceptación y en qué momento se ejecuta.

**DoD**
- `docs/design/plan-pruebas.md` aprobado en el Gate de diseño.

**Tareas**
- Tarea 0.1.4.1 `[DISEÑO]` Estrategia de pruebas por nivel. Unitarias de service (JUnit 5 + Mockito + AssertJ, con repository y clientes simulados), de controller (MockMvc con `@WebMvcTest`, con service simulado), de integración del repository (`@DataJpaTest` + Testcontainers con `postgres:17-alpine`) y del frontend (Vitest + React Testing Library con la API simulada). E2E de los flujos principales (Playwright).
- Tarea 0.1.4.2 `[DISEÑO]` Matriz de casos de prueba por RF y por HU (camino feliz, cada error del catálogo, bordes: fecha pasada, tamaño máximo de página, doble activación, doble confirmación, edición concurrente), agregando explícitamente: activación desde Borrador, activación desde Programada, intento de activar una alerta ya Activa (idempotente) y creación repetida de MS06 con el mismo `simulationId` (debe ser idempotente, no duplicar).
- Tarea 0.1.4.3 `[DISEÑO]` Plan de análisis estático: SonarCloud (quality gate: cobertura nueva ≥ 80 %, 0 bugs y 0 vulnerabilidades en código nuevo, duplicación < 3 %), SpotBugs con find-sec-bugs en el backend, ESLint en el frontend, OWASP Dependency-Check y `npm audit` para dependencias, y gitleaks para secretos.
- Tarea 0.1.4.4 `[DISEÑO]` Plan de análisis dinámico: OWASP ZAP (API scan usando `openapi.yaml`) contra el servicio en ejecución, pruebas de carga con k6 sobre el listado, la creación y la activación con muchos destinatarios (umbral propuesto: p95 < 500 ms y 0 % de errores en el listado), y las pruebas de integración con base de datos real.
- Tarea 0.1.4.5 `[DISEÑO]` Datos de prueba: dataset de ejemplo (`003_sample_data.sql`), usuarios y tokens de cada rol, y un stub de MS01/MS02/MS06 y de Resend para pruebas.
- Tarea 0.1.4.6 `[DISEÑO]` Definir los ambientes (local con Docker, desarrollo compartido) y qué se ejecuta en cada uno.

#### Gate de diseño (hito de revisión)

Antes de iniciar la Fase 2, el equipo realiza una revisión conjunta de las HU 0.1.1 a 0.1.4:

- Revisión cruzada: ER ↔ OpenAPI ↔ Figma ↔ diagrama de clases (mismos campos, mismos nombres, mismos errores).
- ¿Las dos rutas de activación están documentadas de forma idéntica en el ER, el diagrama de estados, el OpenAPI y los mockups?
- Reglas de negocio 2, 4 y 8 recibidas e incorporadas al diseño (hoy PENDIENTE).
- Comentarios resueltos y artefactos con versión "aprobada".
- Cambios de diseño posteriores se hacen por Pull Request sobre `docs/` y se avisan al equipo.

### Feature 0.2: Base técnica (Fase 2)

Implementación de la base de datos, el esqueleto del backend, el esqueleto del frontend y el pipeline, sobre el diseño aprobado.

#### HU 0.2.1: Base de datos PostgreSQL en Docker

Como equipo de desarrollo, quiero la base de datos creada con scripts SQL versionados sobre PostgreSQL en Docker, para que cualquier integrante la levante igual en su equipo.

**DoR**
- ER, diccionario de datos y restricciones aprobados (HU 0.1.1).

**MoSCoW**
- Must Have: todas las historias persisten sus datos aquí.

**Criterios de aceptación**
- Dado un equipo con Docker, cuando se ejecuta `docker compose up -d`, entonces PostgreSQL queda corriendo y el esquema creado sin pasos manuales.
- Dado que se intenta insertar dos confirmaciones del mismo usuario para una alerta, entonces la base de datos rechaza la segunda.
- Dado que se intenta tener dos alertas `SCHEDULED` o `ACTIVE` para el mismo `simulation_id`, entonces la base de datos rechaza la segunda.
- Dado que se detiene y elimina el contenedor, cuando se vuelve a levantar con el mismo volumen, entonces los datos se conservan.

**DoD**
- Scripts ejecutan desde cero. README con los comandos para levantar, verificar y reiniciar la base.

**Tareas**
- Tarea 0.2.1.1 `[OPS]` Crear la estructura del repositorio (`backend/`, `frontend/`, `database/`, `docs/design/`, `docs/evidence/`, `docker-compose.yml`) y el `.gitignore`.
- Tarea 0.2.1.2 `[OPS]` Descargar `postgres:17-alpine` y crear `docker-compose.yml` con el servicio, el volumen persistente, las variables `POSTGRES_DB/USER/PASSWORD` tomadas de un archivo `.env` (con `.env.example` versionado) y un `healthcheck`.
- Tarea 0.2.1.3 `[BD]` Escribir `database/001_create_schema.sql` con las seis tablas, tipos, `DEFAULT`, `NOT NULL`, FK con `ON DELETE CASCADE` donde corresponde y los `CHECK` definidos en el diccionario de datos.
- Tarea 0.2.1.4 `[BD]` Escribir `database/002_indexes.sql` con los índices y restricciones únicas definidos en el diseño, incluida la restricción única condicional por `simulation_id` (alertas `SCHEDULED` o `ACTIVE`).
- Tarea 0.2.1.5 `[BD]` Escribir `database/003_sample_data.sql` con alertas de ejemplo en cada estado (solo para desarrollo).
- Tarea 0.2.1.6 `[OPS]` Montar los scripts en `/docker-entrypoint-initdb.d` y documentar el comando para reejecutarlos con un volumen nuevo.
- Tarea 0.2.1.7 `[PRUEBAS]` Verificar con `psql` dentro del contenedor (`\dt`, `\d alerts`, inserciones inválidas) que los `CHECK`, FK y únicos (incluido el condicional por `simulation_id`) rechazan los datos incorrectos, y guardar la evidencia en `docs/evidence/`.
- Tarea 0.2.1.8 `[DOCS]` Documentar en el README: levantar, detener, reiniciar, eliminar contenedor y eliminar volumen.

#### HU 0.2.2: Esqueleto del backend (Spring Boot)

Como equipo de desarrollo, quiero el proyecto backend creado con la arquitectura por capas, la seguridad, Swagger y el manejo global de errores, para que todos los endpoints se construyan sobre el mismo estándar.

**DoR**
- Diagramas de capas y de clases, catálogo de errores y matriz de roles aprobados.

**MoSCoW**
- Must Have: es la base de toda HU de backend.

**Criterios de aceptación**
- Dado que la base está corriendo, cuando se inicia el backend, entonces arranca y valida que las entidades coinciden con el esquema (`ddl-auto=validate`).
- Dado una solicitud sin token válido, entonces responde 401 con el formato de error estándar; con un token de un rol no permitido, responde 403.
- Dado un cuerpo JSON mal formado, un UUID inválido o un error no controlado, entonces responde `400` o `500` con el formato estándar y sin exponer trazas ni mensajes internos.
- Dado que se abre `/swagger-ui`, entonces se ve la documentación con el esquema de seguridad bearer.

**DoD**
- `mvn clean verify` pasa. Swagger accesible en local.

**Tareas**
- Tarea 0.2.2.1 `[BE]` Generar el proyecto con Spring Initializr (Maven, Java 17, grupo `edu.eci.dosw`, artefacto `alerts-service`) con Web, Data JPA, PostgreSQL Driver, Validation, Security, OAuth2 Resource Server, Actuator y Test; agregar springdoc-openapi, Testcontainers (PostgreSQL), WireMock y JaCoCo al `pom.xml`.
- Tarea 0.2.2.2 `[BE]` Crear los paquetes del diseño y la clase principal.
- Tarea 0.2.2.3 `[BE]` Configurar `application.yml` con perfiles `local`/`test`, tomando de variables de entorno `DB_URL`, `DB_USER`, `DB_PASSWORD`, `RESEND_API_KEY`, `RESEND_FROM`, `APP_BASE_URL`, `MS01_URL`, `MS02_URL` y `JWT_*`; `ddl-auto=validate`.
- Tarea 0.2.2.4 `[BE]` Crear los enums `AlertStatus`, `AlertType`, `ScopeType`, `RecipientType`, `AuditAction` y las entidades `AlertEntity` (con `@Version`), `AlertScopeItemEntity`, `AlertRecipientEntity`, `NotificationDeliveryEntity`, `ConfirmationEntity` y `AuditEventEntity`, con `@Enumerated(EnumType.STRING)`.
- Tarea 0.2.2.5 `[BE]` Crear el `ErrorResponse` y la jerarquía de excepciones del diagrama de clases: `AlertNotFoundException`, `InvalidAlertStateException`, `InvalidTransitionException`, `AlertNotActiveException`, `NotARecipientException`, `InvalidReferenceException`, `InvalidActivationDateException`, `InvalidPaginationException`, `SimulationMismatchException`, `ExternalServiceUnavailableException` y `EmailSendException`.
- Tarea 0.2.2.6 `[BE]` Implementar `GlobalExceptionHandler` (`@RestControllerAdvice`) con un handler por cada fila del catálogo de errores, y para `MethodArgumentNotValidException` (con `details` por campo), `ConstraintViolationException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `ObjectOptimisticLockingFailureException`, `AccessDeniedException` y `Exception` (genérico → 500 con `INTERNAL_ERROR`, registrando la traza solo en el log).
- Tarea 0.2.2.7 `[BE]` Configurar Spring Security como resource server JWT: extracción de roles (`COORDINATOR`, `AUDITOR`, `SERVICE_MS06`; no se define un rol `RECIPIENT`: el resto de usuarios autenticados son usuarios genéricos y su acceso por alerta lo decide `AlertAccessPolicy`), `AuthenticationEntryPoint` (401) y `AccessDeniedHandler` (403) que devuelven el formato estándar, y `@EnableMethodSecurity`.
- Tarea 0.2.2.8 `[BE]` Configurar `OpenApiConfig` (información, esquema bearer), la configuración de CORS (`WebConfig`, origen `http://localhost:5173`) y un bean `Clock` inyectable para poder probar lógica con fechas.
- Tarea 0.2.2.9 `[BE]` Configurar logging con identificador de correlación (MDC) y el endpoint de salud de Actuator.
- Tarea 0.2.2.10 `[PRUEBAS]` Pruebas de contexto: arranca contra Testcontainers y valida el esquema; pruebas de `GlobalExceptionHandler` con un controller de prueba que lanza cada excepción y verifica código HTTP, `code` y formato.
- Tarea 0.2.2.11 `[PRUEBAS]` Pruebas de seguridad con MockMvc: sin token → 401, token vencido → 401, rol no permitido → 403.

#### HU 0.2.3: Esqueleto del frontend (React + Vite)

Como equipo de desarrollo, quiero el frontend creado con su estructura, cliente de API y componentes compartidos, para que cada pantalla se implemente sobre las mismas bases.

**DoR**
- Sistema de diseño y mapa de navegación aprobados (HU 0.1.3).

**MoSCoW**
- Must Have: es la base de toda HU con pantalla.

**Criterios de aceptación**
- Dado que se ejecuta `npm run dev`, entonces la aplicación abre en `http://localhost:5173` con el layout del módulo.
- Dado que la API responde 401, entonces se redirige al login; con 403 se muestra la pantalla "sin permiso"; con 404 la pantalla "no encontrado".
- Dado cualquier error de red o `500`, entonces se muestra un mensaje claro y un botón para reintentar, y la aplicación no queda en blanco.

**DoD**
- `npm run lint`, `npm test` y `npm run build` pasan.

**Tareas**
- Tarea 0.2.3.1 `[FE]` Crear el proyecto con `npm create vite@latest frontend -- --template react` y la estructura `src/api`, `src/features/alerts/{components,hooks,pages}` y `src/shared`.
- Tarea 0.2.3.2 `[FE]` Configurar React Router con las rutas: `/alertas`, `/alertas/nueva`, `/alertas/:id`, `/alertas/:id/editar` y `/alertas/:id/ver` (vista del destinatario), más rutas `/sin-permiso` y `*` (no encontrado).
- Tarea 0.2.3.3 `[FE]` Crear `src/api/apiClient.js`: wrapper de `fetch` que usa `VITE_API_URL`, envía el JWT en `Authorization`, convierte las respuestas de error en una clase `ApiError {status, code, message, details}` y distingue error de red (sin respuesta).
- Tarea 0.2.3.4 `[FE]` Crear `getErrorMessage(error)` que traduce cada `code` del catálogo a su mensaje en español, y un `ErrorBoundary` global para errores de renderizado.
- Tarea 0.2.3.5 `[FE]` Crear el contexto de autenticación (usuario, rol y token) y los componentes `ProtectedRoute` (redirige al login con `returnTo`) y `RoleGuard` (por rol).
- Tarea 0.2.3.6 `[FE]` Crear los componentes compartidos del sistema de diseño: `AlertStateBadge`, `ConfirmDialog`, `Toast`, `ErrorBanner` (con botón reintentar), `Spinner`/esqueleto, `EmptyState` y `Pagination`.
- Tarea 0.2.3.7 `[FE]` Configurar ESLint, Prettier y un archivo `.env.example`.
- Tarea 0.2.3.8 `[PRUEBAS]` Configurar Vitest + React Testing Library + cobertura, y escribir las pruebas de `apiClient` (éxito, 4xx con `ApiError`, error de red, JWT en el header), `getErrorMessage`, `ProtectedRoute`, `RoleGuard`, `ErrorBanner` y `ConfirmDialog`.

#### HU 0.2.4: Pipeline de calidad (análisis estático en cada Pull Request)

Como equipo de desarrollo, quiero un pipeline que compile, pruebe y analice el código en cada Pull Request, para detectar problemas antes de integrar.

**DoR**
- Plan de pruebas y de calidad aprobado (HU 0.1.4).

**MoSCoW**
- Must Have: sostiene el DoD de todas las HU.

**Criterios de aceptación**
- Dado un Pull Request, cuando el pipeline se ejecuta, entonces corre compilación, pruebas con JaCoCo y cobertura de Vitest, análisis de SonarCloud, SpotBugs, ESLint, Dependency-Check, `npm audit` y gitleaks.
- Dado que el quality gate falla o hay un secreto detectado, entonces el Pull Request no se puede integrar.

**DoD**
- Rama principal protegida: requiere pipeline en verde y al menos una aprobación.

**Tareas**
- Tarea 0.2.4.1 `[CALIDAD]` Crear el workflow de CI (GitHub Actions) con el trabajo de backend: `mvn clean verify` con Testcontainers y reporte JaCoCo (umbral 80 %).
- Tarea 0.2.4.2 `[CALIDAD]` Crear el trabajo de frontend: `npm ci`, `npm run lint`, `npm test -- --coverage` y `npm run build`.
- Tarea 0.2.4.3 `[CALIDAD]` Configurar SonarCloud para ambos módulos con el quality gate definido en el plan, y publicar los reportes de cobertura.
- Tarea 0.2.4.4 `[CALIDAD]` Agregar SpotBugs con find-sec-bugs al backend.
- Tarea 0.2.4.5 `[CALIDAD]` Agregar OWASP Dependency-Check (backend) y `npm audit` (frontend), fallando con severidad alta o crítica.
- Tarea 0.2.4.6 `[CALIDAD]` Agregar gitleaks para detectar secretos.
- Tarea 0.2.4.7 `[OPS]` Configurar la protección de la rama principal y la plantilla de Pull Request con la lista de verificación del DoD.

---

## Épica 1: Creación y configuración de alertas

Como coordinador de emergencia, quiero crear y editar alertas con su información básica, para que estén listas antes de que ocurra un evento.

### Feature 1.1: Crear alerta (RF-ALT-01)

Una alerta se registra con tipo, alcance, instrucciones y destinatarios en una sola operación, ya sea por el coordinador desde la interfaz web o por solicitud de MS06.

#### HU 1.1.1: Crear alerta manualmente

Como coordinador, quiero crear una alerta indicando su tipo, alcance, instrucciones y destinatarios desde la interfaz web, para que esté lista antes de un evento.

**DoR**
- HU 0.1.1 a 0.1.3 aprobadas; HU 0.2.1 a 0.2.3 terminadas.
- Contrato de validación de edificios/zonas (MS02) y de usuarios/roles (MS01) disponible o stub acordado.

**MoSCoW**
- Must Have: sin la creación no hay alertas que programar o activar.

**Criterios de aceptación**
- Dado que el coordinador diligencia el formulario con datos válidos, cuando guarda, entonces el sistema crea la alerta en estado Borrador, responde 201 con su identificador y la interfaz lo lleva al detalle con el mensaje "Alerta creada en Borrador".
- Dado que falta un campo obligatorio, que las instrucciones superan 1000 caracteres, que no hay destinatarios, o que el alcance es "edificios específicos" con la lista vacía, entonces la API responde 400 con el detalle por campo y el formulario muestra el error bajo cada campo.
- Dado que un edificio, zona, usuario o rol no existe, entonces la API responde 400 `INVALID_REFERENCE` indicando los IDs inválidos.
- Dado que MS01 o MS02 no responden, entonces la API responde 503 `EXTERNAL_SERVICE_UNAVAILABLE` y la interfaz muestra "Servicio no disponible, intente de nuevo" sin perder lo digitado.
- Dado un usuario que no es coordinador, entonces la API responde 403.

**DoD**
- Cumple el DoD general. Auditoría de creación registrada (se completa con la HU 5.2.1).

**Tareas**
- Tarea 1.1.1.1 `[BE]` Crear `AlertCreateRequest` (tipo, alcance `{tipo, ids[]}`, instrucciones, destinatarios `[{tipo, id}]`) con `@NotNull`, `@NotBlank`, `@Size(max = 1000)`, `@NotEmpty` y la validación personalizada `@ValidScope` (alcance "edificios específicos" exige lista no vacía); crear `AlertResponse` según el contrato.
- Tarea 1.1.1.2 `[BE]` Crear `AlertRepository` (`JpaRepository<AlertEntity, UUID>`) y `AlertMapper` (Entity ↔ DTO, incluyendo alcance y destinatarios).
- Tarea 1.1.1.3 `[BE]` Crear la interfaz `CatalogClient` (`validateBuildingsZones(ids)`, `validateRecipients(recipients)`) y su implementación `RestCatalogClient` hacia MS02/MS01 con timeouts de conexión y lectura; más `StubCatalogClient` para el perfil `local`. Los IDs inexistentes lanzan `InvalidReferenceException` con la lista de IDs; las fallas o timeouts lanzan `ExternalServiceUnavailableException`.
- Tarea 1.1.1.4 `[BE]` Crear la interfaz `AlertService` y `AlertServiceImpl.create(request, actor)` con `@Transactional`: valida referencias con `CatalogClient`, asigna estado `DRAFT` y origen manual, usa el `Clock` para las fechas, guarda alerta, alcance y destinatarios, llama a `AuditService` y retorna `AlertResponse`. Inyección por constructor.
- Tarea 1.1.1.5 `[BE]` Crear `AlertController` (`@RestController`, `@RequestMapping("/api/v1/alertas")`) con `POST` que recibe `@Valid @RequestBody`, está protegido con `@PreAuthorize` para `COORDINATOR`, responde 201 con el header `Location`, y solo llama al service.
- Tarea 1.1.1.6 `[BE]` Registrar en `GlobalExceptionHandler` las respuestas de `InvalidReferenceException` (400, con `details` de IDs) y `ExternalServiceUnavailableException` (503).
- Tarea 1.1.1.7 `[DOCS]` Anotar el endpoint con `@Operation` y `@ApiResponse` (201, 400, 401, 403, 503) y verificar que el Swagger generado coincide con `openapi.yaml`.
- Tarea 1.1.1.8 `[FE]` Crear `createAlert(payload)` en `src/api/alertApi.js` (POST JSON; lanza `ApiError`) y `catalogApi.js` (`getBuildings`, `getZones`, `getUsers`, `getRoles` a través del API Gateway).
- Tarea 1.1.1.9 `[FE]` Crear `ScopeSelector.jsx` (radio campus completo / edificios específicos con multiselección) y `RecipientSelector.jsx` (búsqueda y chips de usuarios y roles). Cada uno carga sus opciones con su propio estado cargando / vacío / error con reintentar.
- Tarea 1.1.1.10 `[FE]` Crear `AlertForm.jsx` según el mockup: select de tipo (Sismo, Simulacro, Incendio, Otro), `ScopeSelector`, textarea de instrucciones con contador 0/1000 y `RecipientSelector`. Validación al enviar y al salir de cada campo (tipo obligatorio, instrucciones obligatorias y ≤ 1000, mínimo un destinatario, edificios obligatorios si el alcance es específico) con el mensaje bajo el campo. El botón queda deshabilitado mientras envía.
- Tarea 1.1.1.11 `[FE]` Crear el hook `useCreateAlert` con `submitting`, `error` y `fieldErrors`: `details` de un 400 se asignan a los campos del formulario; 401 redirige al login; 403 muestra el mensaje de sin permiso; 503 y error de red muestran el banner con reintentar conservando los datos; el éxito navega a `/alertas/:id` con el toast.
- Tarea 1.1.1.12 `[FE]` Crear `CreateAlertPage.jsx` que ensambla el formulario con `RoleGuard` para el coordinador.
- Tarea 1.1.1.13 `[PRUEBAS]` `AlertServiceTest` (Mockito, AssertJ; `AlertRepository`, `CatalogClient`, `AuditService` y `Clock` simulados): `create_shouldSaveAlertInDraft`, `create_shouldSetManualOriginByDefault`, `create_shouldThrowInvalidReference_whenBuildingDoesNotExist`, `create_shouldThrowInvalidReference_whenRecipientDoesNotExist`, `create_shouldThrowExternalServiceUnavailable_whenMs02Fails`, `create_shouldRecordAuditEvent`, `create_shouldNotSave_whenValidationFails`.
- Tarea 1.1.1.14 `[PRUEBAS]` `AlertControllerTest` (MockMvc, service simulado): POST válido → 201 y `Location`; sin tipo → 400 con `details`; instrucciones de 1001 caracteres → 400; destinatarios vacíos → 400; alcance específico sin IDs → 400; JSON mal formado → 400; sin token → 401; rol auditor → 403; service lanza `InvalidReferenceException` → 400; lanza `ExternalServiceUnavailableException` → 503.
- Tarea 1.1.1.15 `[PRUEBAS]` `RestCatalogClientTest` (WireMock): respuesta válida, ID inexistente, 5xx y timeout.
- Tarea 1.1.1.16 `[PRUEBAS]` `AlertRepositoryIT` (Testcontainers PostgreSQL): guarda y recupera una alerta con alcance y destinatarios; la base rechaza un estado inválido y un `instructions` mayor al límite.
- Tarea 1.1.1.17 `[PRUEBAS]` `AlertForm.test.jsx`: renderiza los campos; muestra cada error de validación; bloquea el envío con más de 1000 caracteres; exige edificios si el alcance es específico; envía el payload correcto; deshabilita el botón mientras envía; muestra los errores de campo que vienen del servidor. `CreateAlertPage.test.jsx` (API simulada): éxito redirige al detalle; 503 muestra el banner conservando los datos; 401 redirige al login.

### Feature 1.2: Editar alerta (RF-ALT-03)

Los datos de una alerta se pueden modificar mientras esté en estado Borrador o Programada.

#### HU 1.2.1: Editar alerta en un estado editable

Como coordinador, quiero editar los datos de una alerta que está en Borrador o Programada, para corregir su información antes de que se active.

**DoR**
- HU 1.1.1 terminada (reutiliza validaciones, formulario y selectores); HU 5.1.1 para precargar los datos o un stub.

**MoSCoW**
- Must Have: corregir los datos antes de activar evita enviar información errónea.

**Criterios de aceptación**
- Dado que la alerta está en Borrador o Programada, cuando el coordinador cambia uno o más campos y guarda, entonces la API responde 200 con `fechaActualizacion`, el estado no cambia y la interfaz muestra los datos actualizados.
- Dado que la alerta está Activa, Finalizada o Archivada, entonces la API responde 409 `INVALID_ALERT_STATE`, y la interfaz no ofrece el botón Editar ni permite abrir `/alertas/:id/editar`.
- Dado que el cuerpo no trae ningún campo, o un campo es inválido, entonces la API responde 400 con el detalle.
- Dado que la alerta no existe, entonces la API responde 404 y la interfaz muestra la pantalla de no encontrado.
- Dado que otra persona modificó la alerta mientras se editaba, entonces la API responde 409 `CONCURRENT_MODIFICATION` y la interfaz ofrece recargar los datos.

**DoD**
- Cumple el DoD general. Auditoría de edición registrada (HU 5.2.1).

**Tareas**
- Tarea 1.2.1.1 `[BE]` Crear `AlertUpdateRequest` (todos los campos opcionales, con las mismas validaciones cuando vienen) y la validación personalizada `@AtLeastOneField`.
- Tarea 1.2.1.2 `[BE]` Crear `AlertStateRules` (componente) con `isEditable(status)` (Borrador, Programada) y `assertEditable(alert)` que lanza `InvalidAlertStateException`, indicando el estado actual.
- Tarea 1.2.1.3 `[BE]` Implementar `AlertService.update(id, request, actor)` con `@Transactional`: busca la alerta (`AlertNotFoundException`), valida editable, vuelve a validar con `CatalogClient` solo los alcances o destinatarios que cambian, aplica solo los campos enviados, reemplaza alcance y destinatarios, actualiza `updated_at`, llama a `AuditService` y retorna la respuesta.
- Tarea 1.2.1.4 `[BE]` Agregar `PUT /api/v1/alertas/{id}` al controller (200) con `@PreAuthorize` de coordinador; el handler de `ObjectOptimisticLockingFailureException` responde 409 `CONCURRENT_MODIFICATION`, y el de `MethodArgumentTypeMismatchException` responde 400 para un UUID mal formado.
- Tarea 1.2.1.5 `[DOCS]` Documentar el endpoint (200, 400, 401, 403, 404, 409, 503) y verificarlo contra `openapi.yaml`.
- Tarea 1.2.1.6 `[FE]` Agregar `getAlert(id)` y `updateAlert(id, payload)` a `alertApi.js`, y el hook `useAlert(id)` (alerta, cargando, error, recargar).
- Tarea 1.2.1.7 `[FE]` Convertir `AlertForm` en un formulario de dos modos (`mode="create" | "edit"`, `initialValues`): en edición precarga los datos, calcula las diferencias y envía solo los campos modificados; "Guardar" se deshabilita si no hay cambios.
- Tarea 1.2.1.8 `[FE]` Crear `EditAlertPage.jsx`: carga la alerta con `useAlert`; si el estado no es editable, redirige al detalle con un banner "Esta alerta ya no se puede editar".
- Tarea 1.2.1.9 `[FE]` Manejo de errores de edición: 409 `INVALID_ALERT_STATE` muestra el banner anterior; 409 `CONCURRENT_MODIFICATION` muestra "La alerta fue modificada por otra persona" con el botón Recargar; 404 redirige a la pantalla de no encontrado; 400 asigna errores a los campos.
- Tarea 1.2.1.10 `[PRUEBAS]` `AlertServiceTest`: `update_shouldUpdateOnlySentFields`, `update_shouldAllowDraftAndScheduled` (parametrizado), `update_shouldThrowInvalidState_whenActiveEndedOrArchived` (parametrizado), `update_shouldThrowNotFound`, `update_shouldRevalidateReferences_whenScopeChanges`, `update_shouldNotCallCatalog_whenOnlyInstructionsChange`, `update_shouldRecordAuditEvent`.
- Tarea 1.2.1.11 `[PRUEBAS]` `AlertControllerTest`: PUT → 200; cuerpo vacío → 400; UUID inválido → 400; inexistente → 404; estado no editable → 409; conflicto de versión → 409; usuario sin rol elevado → 403.
- Tarea 1.2.1.12 `[PRUEBAS]` `AlertRepositoryIT`: dos actualizaciones concurrentes sobre la misma versión provocan el conflicto de bloqueo optimista.
- Tarea 1.2.1.13 `[PRUEBAS]` `AlertForm.test.jsx` (modo edición): precarga valores; envía solo campos modificados; deshabilita guardar sin cambios. `EditAlertPage.test.jsx`: redirige cuando el estado no es editable; muestra el banner del 409 de concurrencia con Recargar; muestra no encontrado ante un 404.

---

## Épica 2: Gestión del ciclo de vida de la alerta

Como coordinador de emergencia, quiero que la alerta avance por su ciclo de vida (programación, activación, finalización, archivado), para que su estado siempre refleje la situación real del evento.

### Feature 2.1: Gestionar ciclo de vida de la alerta (RF-ALT-04)

El sistema gestiona los estados y las acciones que producen cada transición.

#### HU 2.1.1: Programar alerta (11.3)

Como coordinador, quiero programar una alerta indicando su fecha y hora de activación, para que se active automáticamente cuando se necesite.

**DoR**
- Diagrama de estados aprobado (0.1.1.7); mockups del diálogo y de las acciones por estado aprobados (0.1.3.6, 0.1.3.7).

**MoSCoW**
- Must Have: sin programación las alertas no se activan sin intervención manual.

**Criterios de aceptación**
- Dado que la alerta está en Borrador, cuando el coordinador indica una fecha y hora futuras, entonces la API responde 200 con estado anterior y nuevo, y la interfaz muestra la alerta como Programada con su fecha.
- Dado que la fecha es pasada o igual a la actual, entonces la API responde 400 `INVALID_ACTIVATION_DATE` y el diálogo lo indica.
- Dado que la alerta no está en Borrador, entonces la API responde 409 `INVALID_TRANSITION` y la interfaz no ofrece la acción.
- Dado que la alerta no existe, entonces responde 404.

**DoD**
- Cumple el DoD general. Auditoría del cambio de estado registrada (HU 5.2.1).

**Tareas**
- Tarea 2.1.1.1 `[BE]` Crear el componente `AlertStateMachine` con el mapa de transiciones del diagrama de estados y `assertTransition(from, to)`, que lanza `InvalidTransitionException` con el estado actual y el solicitado. Todas las acciones del ciclo de vida lo usan.
- Tarea 2.1.1.2 `[BE]` Crear `AlertScheduleRequest` (`activationAt` obligatorio, con zona horaria) y `StateChangeResponse` (`idAlerta`, `estadoAnterior`, `estadoNuevo`, `fechaCambio`).
- Tarea 2.1.1.3 `[BE]` Crear `AlertLifecycleService` y `AlertLifecycleServiceImpl.schedule(id, request, actor)` con `@Transactional`: busca la alerta, valida la transición, valida que `activationAt` sea posterior a `Clock.now()` (si no, `InvalidActivationDateException`), guarda `activation_at`, cambia a `SCHEDULED`, llama a `AuditService`.
- Tarea 2.1.1.4 `[BE]` Crear en el controller `PATCH /api/v1/alertas/{id}/programar` (200, solo coordinador) y los handlers de las excepciones nuevas (409 y 400).
- Tarea 2.1.1.5 `[DOCS]` Documentar el endpoint (200, 400, 401, 403, 404, 409) y verificarlo contra `openapi.yaml`.
- Tarea 2.1.1.6 `[FE]` Agregar `scheduleAlert(id, activationAt)` a `alertApi.js`, y el hook `useAlertActions(alertId)` que expone `schedule`, `activate`, `end`, `archive`, `loading` y `error`, y recarga la alerta tras cada éxito.
- Tarea 2.1.1.7 `[FE]` Crear `AlertActions.jsx`: según el estado muestra solo las acciones válidas (Borrador: Editar, Programar y Activar; Programada: Editar y Activar; Activa: Finalizar; Finalizada: Archivar; Archivada: ninguna), y oculta todas para el rol auditor.
- Tarea 2.1.1.8 `[FE]` Crear `ScheduleDialog.jsx` con selector de fecha y hora, mínimo la hora actual y zona horaria visible; valida que sea futura antes de enviar y deshabilita el botón mientras envía.
- Tarea 2.1.1.9 `[FE]` Manejo de errores de la acción: 400 de fecha muestra el mensaje en el diálogo; 409 muestra "No se puede programar: la alerta está en estado X" y recarga el estado; 404 y error de red muestran el banner.
- Tarea 2.1.1.10 `[PRUEBAS]` `AlertStateMachineTest`: cada transición válida (incluidas `DRAFT→ACTIVE` y `SCHEDULED→ACTIVE`) y todas las inválidas (parametrizado, incluyendo saltos y retrocesos de la regla 1).
- Tarea 2.1.1.11 `[PRUEBAS]` `AlertLifecycleServiceTest` (Mockito): `schedule_shouldChangeToScheduled`, `schedule_shouldThrowInvalidDate_whenPast`, `schedule_shouldThrowInvalidDate_whenEqualsNow`, `schedule_shouldThrowInvalidTransition_whenNotDraft`, `schedule_shouldThrowNotFound`, `schedule_shouldRecordAuditEvent`.
- Tarea 2.1.1.12 `[PRUEBAS]` `AlertControllerTest`: PATCH → 200; fecha pasada → 400; estado no Borrador → 409; inexistente → 404; sin fecha → 400; usuario sin rol elevado → 403.
- Tarea 2.1.1.13 `[PRUEBAS]` `AlertActions.test.jsx` (un caso por estado, incluido el rol auditor) y `ScheduleDialog.test.jsx` (fecha pasada bloquea el envío; muestra el error del servidor; deshabilita mientras envía).

#### HU 2.1.2: Activar alerta (9.7)

Como coordinador, quiero poder activar una alerta de inmediato aunque no esté programada, y que una alerta programada se active automáticamente a su hora o manualmente antes de tiempo, para que el evento comience a comunicarse en el momento adecuado.

**DoR**
- Diagrama de estados corregido aprobado (0.1.1.7), con las dos rutas hacia Activa.

**MoSCoW**
- Must Have: es la acción que da inicio al efecto de la alerta, y bloquea el flujo de MS06 si no funciona desde Borrador.

**Criterios de aceptación**
- Dado que la alerta está en **Borrador**, cuando el coordinador o el servicio MS06 la activan, entonces la API responde 200 con estado Activa, registra `activated_at = ahora` y dispara la notificación.
- Dado que la alerta está **Programada** y llega su fecha y hora, entonces el scheduler la activa dentro de la tolerancia configurada.
- Dado que la alerta está **Programada** y el coordinador la activa manualmente antes de su fecha, entonces la API responde 200 con estado Activa.
- Dado que la alerta está en **Finalizada** o **Archivada**, entonces la API responde 409 `INVALID_TRANSITION`.
- Dado que la alerta ya está **Activa**, entonces no se activa ni se notifica de nuevo; la API responde 200 con el estado actual.
- Dado que dos solicitudes activan la misma alerta al mismo tiempo (desde cualquiera de los dos caminos), entonces solo una realiza la transición y se dispara una sola notificación.
- Dado que el scheduler falla al activar una alerta, entonces las demás alertas pendientes se siguen procesando.

**DoD**
- Cumple el DoD general, incluida la prueba de concurrencia contra PostgreSQL real para ambos caminos.

**Tareas**
- Tarea 2.1.2.1 `[BE]` En `AlertRepository`, `@Modifying @Query` que cambia a `ACTIVE` y guarda `activated_at` **si el estado actual es `DRAFT` o `SCHEDULED`** (`UPDATE ... WHERE id = :id AND status IN ('DRAFT','SCHEDULED')`), retornando las filas afectadas; y `findByStatusAndActivationAtLessThanEqual` para el scheduler (solo aplica a `SCHEDULED`).
- Tarea 2.1.2.2 `[BE]` Implementar `AlertLifecycleService.activate(id, actor)`: usa la actualización condicional sobre ambos estados de origen; si afecta una fila, registra auditoría indicando desde qué estado se activó (`DRAFT` o `SCHEDULED`) y publica `AlertActivatedEvent`; si afecta cero filas, vuelve a leer la alerta (ya Activa → retorna el estado actual sin evento; `Finalizada`/`Archivada` → `InvalidTransitionException`; inexistente → `AlertNotFoundException`).
- Tarea 2.1.2.3 `[BE]` Crear `AlertActivationScheduler` con `@Scheduled`: busca únicamente las alertas **`SCHEDULED`** vencidas (nunca activa una `DRAFT` automáticamente — esa ruta es exclusivamente manual/MS06), invoca `activate` con actor `SYSTEM_SCHEDULER`, captura y registra el error de cada alerta sin detener el ciclo.
- Tarea 2.1.2.4 `[BE]` Agregar `PATCH /api/v1/alertas/{id}/activar` al controller (200), permitido para `COORDINATOR` y `SERVICE_MS06`, sin exigir un estado previo específico en el controller (la validación de transición queda en el service).
- Tarea 2.1.2.5 `[DOCS]` Documentar el endpoint dejando explícitos ambos orígenes válidos (200, 401, 403, 404, 409).
- Tarea 2.1.2.6 `[FE]` En `AlertActions.jsx`, mostrar el botón "Activar" también cuando el estado es `Borrador` (antes solo aparecía en `Programada`), con el mismo diálogo de confirmación advirtiendo el envío de notificaciones.
- Tarea 2.1.2.7 `[PRUEBAS]` `AlertLifecycleServiceTest`: `activate_fromDraft_shouldChangeToActiveAndPublishEvent`, `activate_fromScheduled_shouldChangeToActiveAndPublishEvent`, `activate_shouldBeIdempotent_whenAlreadyActive`, `activate_shouldThrowInvalidTransition_whenEndedOrArchived`, `activate_shouldThrowNotFound`, `activate_shouldRecordAuditEvent_withOriginState`.
- Tarea 2.1.2.8 `[PRUEBAS]` `AlertActivationSchedulerTest`: solo procesa `SCHEDULED` vencidas, nunca toca una `DRAFT`.
- Tarea 2.1.2.9 `[PRUEBAS]` `AlertRepositoryIT`: la actualización condicional funciona desde `DRAFT` y desde `SCHEDULED`; prueba de concurrencia con dos hilos activando la misma alerta desde `DRAFT` contra PostgreSQL real: solo uno obtiene la fila.
- Tarea 2.1.2.10 `[PRUEBAS]` `AlertControllerTest`: PATCH activar desde Borrador → 200; desde Programada → 200; ya activa → 200; Finalizada/Archivada → 409; inexistente → 404; auditor → 403.

#### HU 2.1.3: Finalizar alerta (7)

Como coordinador, quiero finalizar una alerta activa, para cerrar correctamente su ciclo de vida cuando el evento termina.

**DoR**
- Diagrama de estados aprobado. El mismo endpoint lo usa MS06 (HU 4.1.2).

**MoSCoW**
- Must Have: finalizar es necesario para cerrar el evento.

**Criterios de aceptación**
- Dado que la alerta está Activa, cuando el coordinador la finaliza, entonces la API responde 200 con estado Finalizada y se guarda `ended_at`.
- Dado que la alerta ya está Finalizada, entonces la API responde 200 con el estado actual, sin error.
- Dado que está en Borrador, Programada o Archivada, entonces la API responde 409 `INVALID_TRANSITION`.
- Dado que no existe, entonces responde 404.

**DoD**
- Cumple el DoD general.

**Tareas**
- Tarea 2.1.3.1 `[BE]` Implementar `AlertLifecycleService.end(id, actor)`: valida con `AlertStateMachine`, trata el caso "ya finalizada" como respuesta exitosa sin cambios ni auditoría duplicada, guarda `ended_at` y llama a `AuditService`.
- Tarea 2.1.3.2 `[BE]` Agregar `PATCH /api/v1/alertas/{id}/finalizar` al controller (200), permitido para coordinador y servicio MS06.
- Tarea 2.1.3.3 `[DOCS]` Documentar el endpoint (200, 401, 403, 404, 409).
- Tarea 2.1.3.4 `[FE]` Agregar `endAlert(id)` a `alertApi.js` y conectar el botón Finalizar de `AlertActions` con `ConfirmDialog`, estado de carga y manejo de errores (409 muestra el estado actual y recarga).
- Tarea 2.1.3.5 `[PRUEBAS]` `AlertLifecycleServiceTest`: `end_shouldChangeToEnded`, `end_shouldBeIdempotent_whenAlreadyEnded`, `end_shouldThrowInvalidTransition_whenDraftScheduledOrArchived`, `end_shouldThrowNotFound`, `end_shouldRecordAuditEvent`.
- Tarea 2.1.3.6 `[PRUEBAS]` `AlertControllerTest`: finalizar → 200; ya finalizada → 200; estado inválido → 409; inexistente → 404; token MS06 → 200.
- Tarea 2.1.3.7 `[PRUEBAS]` Prueba del botón Finalizar (diálogo, éxito y error 409).

#### HU 2.1.4: Archivar alerta (7)

Como coordinador, quiero archivar una alerta finalizada, para que el historial de alertas se mantenga organizado.

**DoR**
- Regla 3 (Archivada no se modifica) reflejada en el diagrama de estados.

**MoSCoW**
- Should Have: ordena el historial, pero no es esencial para la operación del evento.

**Criterios de aceptación**
- Dado que la alerta está Finalizada, cuando el coordinador la archiva, entonces la API responde 200 con estado Archivada.
- Dado que no está Finalizada, entonces responde 409 `INVALID_TRANSITION`.
- Dado que está Archivada, cuando se intenta editar, programar, activar o finalizar, entonces responde 409 y la interfaz la muestra en solo lectura, sin botones de acción.

**DoD**
- Cumple el DoD general.

**Tareas**
- Tarea 2.1.4.1 `[BE]` Implementar `AlertLifecycleService.archive(id, actor)` con `AlertStateMachine`, `archived_at` y auditoría.
- Tarea 2.1.4.2 `[BE]` Agregar a `AlertStateRules` la guarda `assertNotArchived(alert)` y usarla en `update`, `schedule`, `activate` y `end`, para que ninguna modificación sea posible sobre una alerta archivada.
- Tarea 2.1.4.3 `[BE]` Agregar `PATCH /api/v1/alertas/{id}/archivar` al controller (200, solo coordinador).
- Tarea 2.1.4.4 `[DOCS]` Documentar el endpoint (200, 401, 403, 404, 409).
- Tarea 2.1.4.5 `[FE]` Agregar `archiveAlert(id)` a `alertApi.js`, conectar el botón Archivar con `ConfirmDialog` y manejo de errores, y mostrar el detalle de una alerta archivada en solo lectura con un banner "Alerta archivada".
- Tarea 2.1.4.6 `[PRUEBAS]` `AlertLifecycleServiceTest`: `archive_shouldChangeToArchived`, `archive_shouldThrowInvalidTransition_whenNotEnded`, `archive_shouldThrowNotFound`, y para cada operación sobre una alerta archivada (`update`, `schedule`, `activate`, `end`) que lance la excepción esperada (parametrizado).
- Tarea 2.1.4.7 `[PRUEBAS]` `AlertControllerTest` de archivar (200, 409, 404, 403) y prueba del detalle en solo lectura sin botones.

---

## Épica 3: Notificación y confirmación de alertas (Sprint 2)

Como usuario destinatario, quiero recibir y confirmar alertas, para estar al tanto de las instrucciones durante un evento.

### Feature 3.1: Notificar alerta (RF-ALT-05)

Se envían notificaciones por correo electrónico mediante Resend cuando una alerta pasa a Activa (desde cualquiera de las dos rutas), y cada destinatario puede confirmar su recepción desde la aplicación web. Esto reemplaza la "notificación push a usuarios conectados" por "notificación por correo asíncrona", como decisión de equipo (ver "Decisiones de arquitectura a confirmar").

#### HU 3.1.1: Enviar notificaciones por correo al activarse (15.7)

Como usuario destinatario, quiero recibir un correo cuando se active una alerta, para conocer las instrucciones del evento.

**DoR**
- Plantilla del correo aprobada (0.1.3.10); contrato con MS01 para obtener usuarios y correos aprobado.
- Cuenta de Resend con API key y remitente definidos (o destinatarios de demo definidos si el dominio no está verificado).

**MoSCoW**
- Must Have: sin notificaciones la alerta no cumple su propósito.

**Criterios de aceptación**
- Dado que una alerta pasa a Activa, entonces el sistema envía un correo a cada destinatario único (usuarios directos y usuarios de los roles), con el tipo, las instrucciones y un enlace a `/alertas/{id}/ver`.
- Dado que el envío a un destinatario falla, entonces se registra como fallido con el motivo, sin afectar el estado de la alerta ni el envío a los demás.
- Dado que Resend responde con límite de solicitudes, entonces se reintenta un número limitado de veces con espera creciente antes de registrar el fallo.
- Dado que el evento de activación se procesa de nuevo, entonces no se reenvían correos a quienes ya se les envió (regla 5).
- Dado que MS01 no está disponible al resolver destinatarios, entonces se registra el fallo en el log y en la auditoría, y la alerta sigue Activa.
- Dado que las instrucciones contienen HTML o scripts, entonces el correo los muestra como texto escapado.

**DoD**
- Cumple el DoD general. Prueba de envío real documentada en `docs/evidence/`.

**Tareas**
- Tarea 3.1.1.1 `[INT]` Configurar la cuenta de Resend, la API key en variables de entorno y el remitente; confirmar los límites del plan (cantidad de correos, solicitudes por segundo y restricciones sin dominio verificado) y definir la estrategia de la demo.
- Tarea 3.1.1.2 `[BE]` Crear la interfaz `EmailSender` (`send(EmailMessage)`) y `ResendEmailSender` que llama a la API de Resend con timeout; los errores 4xx, 5xx y de red lanzan `EmailSendException` con el motivo; el 429 se reintenta hasta 3 veces con espera creciente.
- Tarea 3.1.1.3 `[BE]` Crear `EmailTemplateBuilder` que genera el HTML (según el diseño de Figma) y una versión en texto plano; escapa el contenido controlado por el usuario (instrucciones) y construye el enlace con `APP_BASE_URL`.
- Tarea 3.1.1.4 `[BE]` Crear `RecipientResolverService`: expande los destinatarios directos y los roles (consultando MS01 a través de `CatalogClient`) en una lista de usuarios con correo, sin duplicados.
- Tarea 3.1.1.5 `[BE]` Crear `NotificationDeliveryRepository` y `NotificationService.notifyAlertActivated(alertId)`: escucha `AlertActivatedEvent` con `@Async` (executor propio), resuelve destinatarios, omite los que ya tienen un registro de envío, envía uno por uno capturando `EmailSendException` por destinatario, y guarda `SENT` o `FAILED` con el motivo (truncado) en `notification_deliveries`.
- Tarea 3.1.1.6 `[BE]` Manejo de fallos globales de la notificación: si `RecipientResolverService` falla, registrar el error y un evento de auditoría de fallo de notificación sin revertir el estado de la alerta; registrar en auditoría el resumen final (enviados y fallidos).
- Tarea 3.1.1.7 `[DOCS]` Documentar la integración (variables, límites, estrategia de demo y cómo reintentar) en el README.
- Tarea 3.1.1.8 `[PRUEBAS]` `NotificationServiceTest` (Mockito; `EmailSender`, `RecipientResolverService`, repository simulados): `notify_shouldSendOneEmailPerUniqueRecipient`, `notify_shouldRecordSentForEach`, `notify_shouldRecordFailedAndContinue_whenOneSendFails`, `notify_shouldNotResend_whenDeliveryAlreadyExists`, `notify_shouldNotChangeAlertState_onFailure`, `notify_shouldRecordAudit_whenResolverFails`.
- Tarea 3.1.1.9 `[PRUEBAS]` `ResendEmailSenderTest` (WireMock): 200, 4xx, 5xx, timeout, y 429 con reintento exitoso y con reintentos agotados.
- Tarea 3.1.1.10 `[PRUEBAS]` `EmailTemplateBuilderTest`: contiene tipo, instrucciones y enlace correcto; escapa `<script>` y comillas; genera la versión en texto plano.
- Tarea 3.1.1.11 `[PRUEBAS]` `RecipientResolverServiceTest`: usuarios directos, roles, duplicados entre ambos y falla de MS01.
- Tarea 3.1.1.12 `[PRUEBAS]` Prueba de integración (Spring + Testcontainers, `EmailSender` simulado): activar una alerta dispara exactamente un ciclo de envío y registra los resultados; repetir el evento no reenvía.
- Tarea 3.1.1.13 `[PRUEBAS]` Prueba manual de envío real con Resend en un ambiente controlado; guardar captura del correo recibido en `docs/evidence/`.

#### HU 3.1.2: Ver y confirmar la recepción de la alerta (13)

Como usuario destinatario, quiero abrir la alerta desde mi correo y confirmar que la recibí, para saber qué hacer y para que el coordinador sepa que la información me llegó.

**DoR**
- Mockups de la vista del destinatario aprobados (0.1.3.8) y regla de acceso del destinatario definida en la matriz de roles.

**MoSCoW**
- Should Have: la confirmación aporta información valiosa, pero la alerta cumple su propósito sin ella.

**Criterios de aceptación**
- Dado que el destinatario abre el enlace del correo, cuando no está autenticado, entonces se le pide iniciar sesión y regresa a la alerta.
- Dado un destinatario autenticado, cuando abre una alerta Activa que le corresponde, entonces ve el tipo, las instrucciones y el botón Confirmar.
- Dado que confirma, entonces la API responde 201, se guarda con fecha y hora, y la interfaz muestra "Confirmaste la recepción el [fecha]".
- Dado que ya había confirmado, entonces la API responde 200 con la confirmación existente, sin duplicar.
- Dado que la alerta no está Activa, entonces la API responde 409 `ALERT_NOT_ACTIVE` y la interfaz muestra "Esta alerta ya no está vigente".
- Dado que el usuario no es destinatario, entonces la API responde 403 `NOT_A_RECIPIENT` y la interfaz muestra "No tienes acceso a esta alerta".
- Dado que la alerta no existe, entonces responde 404.

**DoD**
- Cumple el DoD general. Ajuste de la regla de acceso de `GET /alertas/{id}` reflejado en los requerimientos.

**Tareas**
- Tarea 3.1.2.1 `[BE]` Crear `AlertAccessPolicy`: coordinador, auditor y servicio pueden ver cualquier alerta; cualquier otro usuario autenticado (sin rol elevado) solo si está listado directamente como destinatario, pertenece a un rol destinatario de esa alerta o tiene un registro de envío para esa alerta; si no, `NotARecipientException`. El acceso no depende de un rol fijo del JWT. Usarla en `AlertService.findById`.
- Tarea 3.1.2.2 `[BE]` Crear `ConfirmationRepository` y `ConfirmationService.confirm(alertId, userId)` con `@Transactional`: valida existencia (404), estado Activa (`AlertNotActiveException`) y que sea destinatario (`NotARecipientException`); si ya existe la confirmación, la retorna (resultado "ya existente"); si no, la crea. Ante una violación de la restricción única por concurrencia (`DataIntegrityViolationException`), consulta y retorna la existente.
- Tarea 3.1.2.3 `[BE]` Agregar `POST /api/v1/alertas/{id}/confirmaciones` al controller: toma el `userId` del JWT, responde 201 si es nueva o 200 si ya existía, y registra los handlers de 409 `ALERT_NOT_ACTIVE` y 403 `NOT_A_RECIPIENT`.
- Tarea 3.1.2.4 `[DOCS]` Documentar el endpoint (200, 201, 401, 403, 404, 409).
- Tarea 3.1.2.5 `[FE]` Agregar `confirmReceipt(alertId)` a `alertApi.js` y el hook `useConfirmReceipt` con los estados `confirming`, `confirmed` y `error`.
- Tarea 3.1.2.6 `[FE]` Crear `AlertViewPage.jsx` (ruta `/alertas/:id/ver`, `ProtectedRoute` con `returnTo`) con diseño pensado para móvil: tipo, instrucciones (renderizadas como texto, nunca como HTML), alcance y fecha de activación, con esqueleto de carga.
- Tarea 3.1.2.7 `[FE]` Crear `ConfirmReceiptButton.jsx` y los estados de la vista: confirmable, ya confirmada (muestra la fecha y deshabilita el botón), alerta no vigente (409), sin acceso (403) y no encontrada (404), según los mockups.
- Tarea 3.1.2.8 `[PRUEBAS]` `ConfirmationServiceTest`: `confirm_shouldCreateConfirmation`, `confirm_shouldReturnExisting_whenAlreadyConfirmed`, `confirm_shouldThrowNotActive`, `confirm_shouldThrowNotARecipient`, `confirm_shouldThrowNotFound`, `confirm_shouldReturnExisting_whenConcurrentInsertFails`. `AlertAccessPolicyTest`: cada rol y las tres formas de ser destinatario.
- Tarea 3.1.2.9 `[PRUEBAS]` `ConfirmationControllerTest`: nueva → 201; ya existente → 200; no activa → 409; no destinatario → 403; inexistente → 404; sin token → 401.
- Tarea 3.1.2.10 `[PRUEBAS]` `ConfirmationRepositoryIT`: la restricción única rechaza la segunda confirmación y una inserción concurrente deja un único registro.
- Tarea 3.1.2.11 `[PRUEBAS]` `AlertViewPage.test.jsx` y `ConfirmReceiptButton.test.jsx`: muestra las instrucciones como texto escapado; confirma y refleja el resultado; estado ya confirmada; alerta no vigente; 403; 404; redirección al login sin sesión.

---

## Épica 4: Integración con simulacros (MS06) (Sprint 2)

Como MS06, quiero solicitar la creación y finalización de alertas asociadas a un simulacro, para que el simulacro y su alerta se mantengan sincronizados.

### Feature 4.1: Integración con simulacros (RF-ALT-06)

MS03 expone las operaciones que MS06 necesita para crear/programar y finalizar una alerta asociada a un simulacro.

#### HU 4.1.1: Crear o programar alerta solicitada por MS06 (11.3)

Como MS06, quiero solicitar la creación o programación de una alerta asociada a un simulacro, para que se active automáticamente cuando inicie el simulacro.

**DoR**
- Contrato con MS06 aceptado por su squad (0.1.2.6, 0.1.2.7), incluido el mecanismo del token de servicio y el comportamiento idempotente por `simulationId`.
- MS06 disponible en el ambiente de desarrollo para la prueba conjunta.

**MoSCoW**
- Must Have: sin esta integración los simulacros no pueden disparar su alerta.

**Criterios de aceptación**
- Dado que MS06 envía los datos de la alerta con un token de servicio válido, cuando la solicitud es válida, entonces se crea asociada al ID del simulacro y se responde 201 con el identificador.
- Dado que la solicitud incluye fecha y hora de activación futura, entonces la alerta queda Programada; si no la incluye ni pide activación inmediata, queda en Borrador.
- Dado que la solicitud incluye `activarInmediatamente: true` (sin `activationAt`), entonces la alerta se crea y se activa de inmediato por la ruta `Borrador → Activa`, responde 201 con estado Activa y dispara la notificación, sin forzar una fecha futura artificial.
- Dado que ya existe una alerta `SCHEDULED` o `ACTIVE` para ese `simulationId` (por ejemplo, ante un reintento de MS06), entonces responde **200 con la alerta existente** (idempotente) y no crea otra.
- Dado que el origen es "Simulacro" y falta el ID del simulacro, o faltan datos obligatorios, entonces se responde 400 con el motivo por campo.
- Dado que el token de servicio es inválido o falta, entonces se responde 401; si un token de usuario sin rol de coordinador intenta crear, 403.
- Dado que una alerta fue creada por un simulacro, entonces la interfaz muestra su origen y el ID del simulacro en el listado y el detalle.

**DoD**
- Cumple el DoD general, más al menos una prueba conjunta con el squad de MS06 en el ambiente de desarrollo.

**Tareas**
- Tarea 4.1.1.1 `[BE]` Implementar la autenticación de servicio acordada (validación del token de servicio y asignación del rol `SERVICE_MS06`) en la configuración de seguridad; token inválido → 401 con el formato estándar.
- Tarea 4.1.1.2 `[BE]` Extender `AlertCreateRequest` con `origin {tipo, simulationId}`, `activationAt` opcional y `activarInmediatamente` opcional; validación personalizada: origen "Simulacro" exige `simulationId` (regla 9).
- Tarea 4.1.1.3 `[BE]` Ajustar `AlertServiceImpl.create`: guarda origen y `simulation_id`; antes de crear, consulta por `simulationId` (mismo mecanismo del filtro de HU 5.1.2) y, si ya hay una alerta `SCHEDULED` o `ACTIVE`, retorna la existente con 200; estado `SCHEDULED` si viene `activationAt` futura (validada con `Clock`, si no `InvalidActivationDateException`); si viene `activarInmediatamente`, crea en `DRAFT` y llama a `AlertLifecycleService.activate` (reutiliza la ruta `Borrador → Activa` de HU 2.1.2, con su auditoría y evento); `DRAFT` en los demás casos. Ante una violación de la restricción única condicional por concurrencia (`DataIntegrityViolationException`), consulta y retorna la existente. El actor de auditoría es el servicio MS06.
- Tarea 4.1.1.4 `[BE]` Permitir `POST /api/v1/alertas` a los roles `COORDINATOR` y `SERVICE_MS06`; el resto de endpoints de gestión siguen restringidos al coordinador.
- Tarea 4.1.1.5 `[DOCS]` Agregar a Swagger los ejemplos de solicitud de MS06 (con y sin fecha) y las respuestas de error.
- Tarea 4.1.1.6 `[FE]` Crear `OriginBadge.jsx` (Manual / Simulacro con el ID) y mostrarlo en `AlertTable` y en el detalle de la alerta.
- Tarea 4.1.1.7 `[PRUEBAS]` `AlertServiceTest`: `create_withSimulationOrigin_shouldAssociateSimulationId`, `create_withFutureActivation_shouldBeScheduled`, `create_withoutActivation_shouldBeDraft`, `create_withPastActivation_shouldThrowInvalidDate`, `create_shouldRecordServiceAsActor`, `create_withSameSimulationId_shouldReturnExistingAlert`, `create_withActivateImmediately_shouldBeActiveAndPublishEvent`, `create_shouldReturnExisting_whenConcurrentInsertFails`.
- Tarea 4.1.1.8 `[PRUEBAS]` `AlertControllerTest` y pruebas de seguridad: token de servicio válido → 201; token inválido → 401; origen simulacro sin ID → 400; datos incompletos → 400 con detalle; token de usuario sin rol → 403; mismo `simulationId` repetido → 200 con la alerta existente; `activarInmediatamente` → 201 con estado Activa.
- Tarea 4.1.1.9 `[PRUEBAS]` Colección de Postman con las solicitudes de MS06 (válidas e inválidas) compartida con el squad, y prueba del `OriginBadge`.
- Tarea 4.1.1.10 `[INT]` Prueba conjunta con el squad de MS06 en el ambiente de desarrollo.

#### HU 4.1.2: Finalizar alerta notificada por MS06 (9.7)

Como MS06, quiero notificar a MS03 que un simulacro terminó, para que su alerta asociada finalice de forma coordinada.

**DoR**
- Contrato de la notificación de fin aceptado por MS06.

**MoSCoW**
- Must Have: sin esta notificación la alerta no se cierra con el simulacro.

**Criterios de aceptación**
- Dado que la alerta está asociada al simulacro y está Activa, cuando MS06 notifica su fin, entonces cambia a Finalizada y se responde 200.
- Dado que la alerta ya está Finalizada, entonces se responde 200 con el estado actual, sin error ni cambios.
- Dado que la alerta no está asociada al simulacro que notifica, entonces se responde 409 `SIMULATION_MISMATCH`.
- Dado que la alerta no existe, 404; dado un token inválido, 401.

**DoD**
- Cumple el DoD general y la prueba conjunta del ciclo completo con el squad de MS06.

**Tareas**
- Tarea 4.1.2.1 `[BE]` Extender `AlertLifecycleService.end` para solicitudes de servicio: recibe el `simulationId` en el cuerpo y valida que coincida con el de la alerta (`SimulationMismatchException`); reutiliza la transición y la idempotencia de HU 2.1.3.
- Tarea 4.1.2.2 `[BE]` Ajustar el controller de `PATCH /finalizar` para aceptar el cuerpo opcional con `simulationId`, obligatorio cuando el actor es el servicio, y registrar el handler de 409 `SIMULATION_MISMATCH`.
- Tarea 4.1.2.3 `[DOCS]` Agregar a Swagger los ejemplos de finalización por MS06 y sus errores.
- Tarea 4.1.2.4 `[PRUEBAS]` `AlertLifecycleServiceTest`: `endBySimulation_shouldChangeToEnded`, `endBySimulation_shouldBeIdempotent`, `endBySimulation_shouldThrowMismatch`, `endBySimulation_shouldThrowNotFound`, `endBySimulation_shouldRecordServiceAsActor`.
- Tarea 4.1.2.5 `[PRUEBAS]` `AlertControllerTest`: 200, 200 idempotente, 409 de simulacro distinto, 404, 401 y falta de `simulationId` con token de servicio → 400.
- Tarea 4.1.2.6 `[INT]` Prueba conjunta del ciclo completo (crear → activar → finalizar) con el squad de MS06.

---

## Épica 5: Monitoreo y trazabilidad de alertas

Como coordinador o auditor, quiero consultar las alertas y su rastro de auditoría, para hacer seguimiento de lo que ocurrió antes, durante y después de un evento.

### Feature 5.1: Consultar alertas (RF-ALT-02)

Se puede consultar una alerta específica y un listado filtrado y paginado, desde la interfaz web y desde otros microservicios.

#### HU 5.1.1: Consultar una alerta específica

Como coordinador, auditor o MS06, quiero consultar la información completa de una alerta, para conocer sus datos y su estado actual.

**DoR**
- Mockup del detalle aprobado (0.1.3.6); matriz de roles aprobada.

**MoSCoW**
- Must Have: otros servicios y roles dependen de conocer los datos actuales de una alerta.

**Criterios de aceptación**
- Dado que la alerta existe y el actor está autorizado, entonces la API responde 200 con tipo, alcance, instrucciones, destinatarios, estado, origen y fechas (creación, actualización, activación programada, activación, finalización, archivado).
- Dado que no existe, entonces responde 404 y la interfaz muestra la pantalla de no encontrado.
- Dado un usuario autenticado sin rol elevado que no corresponde a la alerta (no es destinatario), entonces responde 403.
- Dado que el coordinador o el auditor abre el detalle, entonces la interfaz muestra los datos, el estado y las acciones válidas (solo lectura para el auditor).

**DoD**
- Cumple el DoD general.

**Tareas**
- Tarea 5.1.1.1 `[BE]` Ampliar `AlertResponse` con alcance, destinatarios, origen y todas las fechas, y mapearlos en `AlertMapper`.
- Tarea 5.1.1.2 `[BE]` Implementar `AlertService.findById(id, principal)` con `@Transactional(readOnly = true)`: busca la alerta (`AlertNotFoundException`) y aplica `AlertAccessPolicy`.
- Tarea 5.1.1.3 `[BE]` Evitar el problema N+1 al cargar alcance y destinatarios (`@EntityGraph` o `JOIN FETCH` en el repository) y verificarlo con un test que cuente las consultas.
- Tarea 5.1.1.4 `[BE]` Agregar `GET /api/v1/alertas/{id}` al controller (200), accesible para coordinador, auditor, servicio y cualquier otro usuario autenticado, este último sujeto a `AlertAccessPolicy`.
- Tarea 5.1.1.5 `[DOCS]` Documentar el endpoint (200, 401, 403, 404).
- Tarea 5.1.1.6 `[FE]` Crear `AlertDetailPage.jsx` (`/alertas/:id`): encabezado con tipo, `AlertStateBadge` y `OriginBadge`; secciones de datos, alcance (nombres de edificios con respaldo al ID si el catálogo falla), destinatarios y fechas; `AlertActions` visibles solo para coordinador; botón "Volver al listado".
- Tarea 5.1.1.7 `[FE]` Estados del detalle: esqueleto mientras carga, banner con reintentar ante un error de red o 503, pantalla de no encontrado (404) y de sin permiso (403).
- Tarea 5.1.1.8 `[PRUEBAS]` `AlertServiceTest`: `findById_shouldReturnFullAlert`, `findById_shouldThrowNotFound`, `findById_shouldThrowNotARecipient_whenUserNotRecipient`.
- Tarea 5.1.1.9 `[PRUEBAS]` `AlertControllerTest`: 200, 404, 403, 401, UUID inválido → 400.
- Tarea 5.1.1.10 `[PRUEBAS]` `AlertDetailPage.test.jsx`: muestra datos de cada estado; auditor sin botones de acción; error de red con reintentar; 404 y 403.

#### HU 5.1.2: Listar alertas con filtros y paginación

Como coordinador o auditor, quiero listar las alertas filtradas por estado y rango de fechas, para revisar las alertas relevantes de un período.

**DoR**
- Decisiones del listado cerradas en `decisiones.md` (campo de fecha filtrado, filtros inválidos, tamaño máximo de página); mockup del dashboard aprobado.

**MoSCoW**
- Should Have: filtros y paginación hacen usable el listado, aunque uno básico funcionaría sin ellos.

**Criterios de aceptación**
- Dado que se envían filtros válidos, entonces la API responde 200 solo con las alertas que los cumplen, paginadas y con metadatos (total de registros y página actual).
- Dado que no se envían filtros, entonces retorna el listado completo paginado, ordenado por fecha de creación descendente.
- Dado que `fechaInicio` es posterior a `fechaFin`, un estado que no existe o `size` mayor al máximo, entonces responde 400 con el detalle.
- Dado que ninguna alerta cumple los filtros, entonces la interfaz muestra "No hay alertas que coincidan".
- Dado que se recarga la página, entonces los filtros y la página se conservan (están en la URL).
- Dado que se envía `simulationId`, entonces la API responde solo con las alertas asociadas a ese simulacro (típicamente cero o una, dada la restricción de unicidad de la tarea 0.1.1.3).

**DoD**
- Cumple el DoD general.

**Tareas**
- Tarea 5.1.2.1 `[BE]` Crear `AlertFilter` (estado, fechaInicio, fechaFin, `simulationId`, page, size) y `PageResponse<T>` (contenido, página, tamaño, total de elementos, total de páginas) y `AlertSummaryResponse` (id, tipo, alcance, estado, origen, fecha).
- Tarea 5.1.2.2 `[BE]` Hacer que `AlertRepository` extienda `JpaSpecificationExecutor` y crear `AlertSpecifications` (por estado, por rango de fecha de creación y por `simulationId`, usando el índice del diseño de datos). El filtro por `simulationId` es también el mecanismo que usa HU 4.1.1 para verificar si ya existe una alerta para un simulacro.
- Tarea 5.1.2.3 `[BE]` Implementar `AlertService.search(filter)` con `@Transactional(readOnly = true)`: valida el rango de fechas y el tamaño máximo (`InvalidPaginationException` / `ValidationException`), construye la especificación y el `Pageable` ordenado por fecha de creación descendente.
- Tarea 5.1.2.4 `[BE]` Agregar `GET /api/v1/alertas` al controller (200, coordinador y auditor); un valor de estado fuera del catálogo responde 400 mediante el handler de `MethodArgumentTypeMismatchException`.
- Tarea 5.1.2.5 `[DOCS]` Documentar el endpoint y cada parámetro de consulta (200, 400, 401, 403).
- Tarea 5.1.2.6 `[FE]` Agregar `getAlerts(params)` a `alertApi.js` y el hook `useAlerts(filters)` que sincroniza filtros y página con los parámetros de la URL y expone `data`, `loading`, `error` y `reload`.
- Tarea 5.1.2.7 `[FE]` Crear `AlertFilters.jsx`: select de estado (los cinco estados y "Todos"), dos campos de fecha, botones Aplicar y Limpiar; valida `fechaInicio ≤ fechaFin` antes de consultar y muestra el error en el campo.
- Tarea 5.1.2.8 `[FE]` Crear `AlertTable.jsx` (tipo, `AlertStateBadge`, alcance, `OriginBadge`, fecha de creación, enlace al detalle) y usar el componente `Pagination` con selector de tamaño (10/20/50) y manejo de la última página.
- Tarea 5.1.2.9 `[FE]` Crear `AlertsPage.jsx` (`/alertas`): botón "Nueva alerta" solo para coordinador; esqueleto de carga, estado vacío, y banner de error con reintentar.
- Tarea 5.1.2.10 `[PRUEBAS]` `AlertServiceTest`: `search_shouldFilterByState`, `search_shouldFilterByDateRange`, `search_shouldFilterBySimulationId`, `search_shouldReturnAllWhenNoFilters`, `search_shouldThrowInvalidPagination_whenSizeExceedsMax`, `search_shouldThrowValidation_whenStartAfterEnd`.
- Tarea 5.1.2.11 `[PRUEBAS]` `AlertControllerTest`: 200 con y sin filtros, estado inválido → 400, fechas invertidas → 400, `size` excedido → 400, `page` negativo → 400, filtro `simulationId` → 200 solo con la alerta del simulacro, auditor → 200, usuario sin rol elevado → 403.
- Tarea 5.1.2.12 `[PRUEBAS]` `AlertRepositoryIT`: especificaciones combinadas (estado + fechas) y paginación sobre datos reales; verificar con `EXPLAIN` que usa los índices con un volumen de datos de prueba.
- Tarea 5.1.2.13 `[PRUEBAS]` `AlertFilters.test.jsx` (valida fechas, aplica y limpia), `AlertTable.test.jsx` (renderiza filas, badges y enlaces), `AlertsPage.test.jsx` (carga, vacío, error con reintentar, botón "Nueva alerta" según rol y filtros desde la URL).

### Feature 5.2: Auditoría (RF-ALT-07)

Las acciones relevantes sobre las alertas se registran y se pueden consultar.

#### HU 5.2.1: Registrar eventos de auditoría

Como sistema, quiero registrar cada acción relevante realizada sobre una alerta, para tener trazabilidad de lo ocurrido.

**DoR**
- Tabla `audit_events` creada; catálogo de acciones auditables definido en el diagrama de clases (`AuditAction`).

**MoSCoW**
- Must Have: la trazabilidad es un requisito del proyecto (regla 7).

**Criterios de aceptación**
- Dado que se crea, edita, programa, activa, finaliza o archiva una alerta, entonces queda registrado el actor, la acción y la fecha y hora, **dentro de la misma transacción** de la operación.
- Dado que el registro de auditoría falla, entonces la operación completa se revierte (no puede haber cambios sin auditoría).
- Dado que la acción la realiza MS06, entonces el actor se registra como servicio; si la realiza el scheduler, como `SYSTEM_SCHEDULER`.
- Dado que se activa una alerta, entonces el evento de auditoría registra desde qué estado se activó (Borrador o Programada), para distinguir una activación inmediata de una programada.
- Dado que se envían las notificaciones, entonces queda registrado el resumen de envíos (enviados y fallidos).
- Dado que se intenta modificar o eliminar un evento ya registrado, entonces no existe forma de hacerlo desde el código.

**DoD**
- Cumple el DoD general. Todos los services del módulo invocan la auditoría y esto está verificado con pruebas.

**Tareas**
- Tarea 5.2.1.1 `[BE]` Definir `AuditAction` (creación, edición, programación, activación, finalización, archivado, notificaciones enviadas, notificación fallida) y el `AuditEventRepository` de solo inserción y consulta (sin métodos de actualización ni borrado).
- Tarea 5.2.1.2 `[BE]` Crear `ActorResolver` que obtiene del contexto de seguridad el actor y su tipo (`USER` con el `sub` del JWT, `SERVICE` con el identificador del servicio) y soporta el actor `SYSTEM_SCHEDULER` para el scheduler.
- Tarea 5.2.1.3 `[BE]` Crear `AuditService.record(alertId, action, actor, detail)` con propagación `REQUIRED`, para que participe en la transacción de la operación, y la auditoría de notificaciones en una transacción propia (se ejecuta después del commit).
- Tarea 5.2.1.4 `[BE]` Invocar `AuditService` desde `AlertServiceImpl` (crear y editar), `AlertLifecycleServiceImpl` (programar, activar, finalizar, archivar) y `NotificationService` (resumen), reemplazando los stubs de las HU anteriores.
- Tarea 5.2.1.5 `[PRUEBAS]` `AuditServiceTest` y `ActorResolverTest`: actor de usuario, de servicio y del scheduler; detalle guardado; `record_shouldPropagateException_whenSaveFails`.
- Tarea 5.2.1.6 `[PRUEBAS]` Prueba de integración (Testcontainers): al crear y recorrer todo el ciclo de vida de una alerta se registran los eventos esperados en orden; si se fuerza un fallo en la auditoría, la operación se revierte y el estado de la alerta no cambia.
- Tarea 5.2.1.7 `[PRUEBAS]` Prueba de arquitectura (ArchUnit) que verifica que ninguna clase fuera de `AuditService` use `AuditEventRepository` para modificar eventos, y que los controllers no dependan de los repositories.

#### HU 5.2.2: Consultar el historial de auditoría de una alerta

Como auditor, quiero consultar el historial de auditoría de una alerta específica, para revisar quién hizo qué y cuándo.

**DoR**
- Mockup del panel de auditoría aprobado (0.1.3.9); roles autorizados definidos (Auditor/Observador y Coordinador).

**MoSCoW**
- Must Have: la auditoría solo es útil si se puede consultar.

**Criterios de aceptación**
- Dado que la alerta tiene eventos, cuando el auditor consulta su historial, entonces la API responde 200 con los eventos en orden cronológico (actor, tipo de actor, acción, detalle y fecha), y la interfaz los muestra como línea de tiempo en el detalle.
- Dado que la alerta no tiene eventos, entonces responde 200 con lista vacía y la interfaz muestra "Sin eventos registrados".
- Dado que el actor no tiene un rol autorizado, entonces responde 403 y la interfaz oculta el panel.
- Dado que la alerta no existe, entonces responde 404.

**DoD**
- Cumple el DoD general.

**Tareas**
- Tarea 5.2.2.1 `[BE]` Crear `AuditEventResponse` y `AuditQueryService.findByAlert(alertId)` (`@Transactional(readOnly = true)`): verifica que la alerta exista (`AlertNotFoundException`) y retorna los eventos ordenados por fecha ascendente.
- Tarea 5.2.2.2 `[BE]` Agregar `GET /api/v1/alertas/{id}/auditoria` al controller (200) con `@PreAuthorize` para `AUDITOR` y `COORDINATOR`.
- Tarea 5.2.2.3 `[DOCS]` Documentar el endpoint (200, 401, 403, 404).
- Tarea 5.2.2.4 `[FE]` Agregar `getAlertAudit(id)` a `alertApi.js` y el hook `useAudit(alertId)` con carga, error y reintento.
- Tarea 5.2.2.5 `[FE]` Crear `AuditTimeline.jsx`: lista vertical con ícono y etiqueta en español por acción (la activación indica si fue desde Borrador o desde Programada), actor, tipo de actor y fecha formateada; estados cargando, vacío ("Sin eventos registrados") y error con reintentar; se integra al `AlertDetailPage` solo para los roles autorizados.
- Tarea 5.2.2.6 `[PRUEBAS]` `AuditQueryServiceTest`: `findByAlert_shouldReturnOrderedEvents`, `findByAlert_shouldReturnEmptyList`, `findByAlert_shouldThrowNotFound`.
- Tarea 5.2.2.7 `[PRUEBAS]` `AuditControllerTest`: 200, lista vacía → 200, 404, 403 para usuario sin rol elevado, 401 sin token.
- Tarea 5.2.2.8 `[PRUEBAS]` `AuditTimeline.test.jsx`: renderiza los eventos en orden con su etiqueta; estado vacío; error con reintentar; el panel no se muestra para un rol no autorizado.

---

## Épica 6: Verificación de calidad y cierre (Sprint 2)

Como equipo de desarrollo, quiero verificar con análisis estático, análisis dinámico y pruebas de punta a punta que la solución cumple con la calidad esperada, para entregar con evidencia.

### Feature 6.1: Análisis estático y dinámico (Fase 3)

#### HU 6.1.1: Análisis estático del código y de las dependencias

Como equipo de desarrollo, quiero revisar y corregir los hallazgos del análisis estático, para entregar código sin vulnerabilidades ni defectos críticos.

**DoR**
- Pipeline de calidad en funcionamiento (HU 0.2.4); HU funcionales terminadas.

**MoSCoW**
- Must Have: es parte del criterio de calidad definido en el plan.

**Criterios de aceptación**
- Dado el análisis completo del proyecto, entonces el quality gate de SonarCloud queda en verde, sin bugs ni vulnerabilidades abiertas con severidad bloqueante o crítica.
- Dado el análisis de dependencias, entonces no quedan vulnerabilidades altas o críticas sin tratar; las que se aceptan tienen justificación escrita.
- Dado gitleaks sobre todo el historial del repositorio, entonces no hay secretos expuestos.

**DoD**
- Informe en `docs/evidence/analisis-estatico.md` con capturas de los reportes.

**Tareas**
- Tarea 6.1.1.1 `[CALIDAD]` Ejecutar el análisis completo (SonarCloud, SpotBugs, ESLint) y clasificar los hallazgos por severidad.
- Tarea 6.1.1.2 `[BE]` Corregir los hallazgos bloqueantes y críticos del backend, y revisar los de severidad media (code smells, complejidad, duplicación).
- Tarea 6.1.1.3 `[FE]` Corregir los hallazgos bloqueantes y críticos del frontend y los avisos de accesibilidad y de ESLint.
- Tarea 6.1.1.4 `[CALIDAD]` Revisar el reporte de OWASP Dependency-Check y `npm audit`: actualizar dependencias o documentar la excepción justificada.
- Tarea 6.1.1.5 `[CALIDAD]` Ejecutar gitleaks sobre el historial completo; si aparece un secreto, rotarlo y limpiar el historial.
- Tarea 6.1.1.6 `[DOCS]` Redactar el informe de análisis estático con la comparación antes y después de las correcciones.

#### HU 6.1.2: Análisis dinámico (seguridad y carga)

Como equipo de desarrollo, quiero ejecutar análisis dinámico sobre el servicio en ejecución, para encontrar fallas de seguridad y de rendimiento que el código por sí solo no revela.

**DoR**
- Backend y base de datos corriendo en el ambiente local o de desarrollo con datos de prueba.

**MoSCoW**
- Should Have: complementa las pruebas funcionales con evidencia de seguridad y rendimiento.

**Criterios de aceptación**
- Dado el escaneo de OWASP ZAP contra la API usando `openapi.yaml` y tokens de cada rol, entonces no quedan alertas de riesgo alto; las de riesgo medio están corregidas o justificadas.
- Dado intentos de acceso con el rol equivocado, entonces todos los endpoints responden 401 o 403 según la matriz de roles.
- Dado la prueba de carga del plan, entonces el listado cumple el umbral definido (p95 y tasa de errores) y la activación con muchos destinatarios no bloquea la respuesta de la API.

**DoD**
- Informe en `docs/evidence/analisis-dinamico.md` con los reportes de ZAP y de k6.

**Tareas**
- Tarea 6.1.2.1 `[CALIDAD]` Ejecutar OWASP ZAP (API scan con `openapi.yaml`) autenticado con cada rol, y exportar el reporte.
- Tarea 6.1.2.2 `[PRUEBAS]` Pruebas de autorización dinámicas: recorrer todos los endpoints con cada rol (y sin token) y comparar el resultado con la matriz de roles.
- Tarea 6.1.2.3 `[PRUEBAS]` Pruebas de entradas hostiles: instrucciones con HTML y scripts, IDs mal formados, cuerpos muy grandes, y parámetros de consulta fuera de rango; verificar que no hay `500` ni fugas de información en los errores.
- Tarea 6.1.2.4 `[BE]` Corregir los hallazgos de ZAP (cabeceras de seguridad, mensajes de error, CORS) y volver a ejecutar el escaneo.
- Tarea 6.1.2.5 `[CALIDAD]` Escribir y ejecutar los scripts de k6: listado con filtros, creación de alertas y activación de una alerta con muchos destinatarios, midiendo p95 y tasa de errores.
- Tarea 6.1.2.6 `[BE]` Optimizar según los resultados (índices, consultas, tamaño del executor de notificaciones) y repetir la medición.
- Tarea 6.1.2.7 `[DOCS]` Redactar el informe de análisis dinámico con los resultados y las correcciones.

#### HU 6.1.3: Pruebas de punta a punta y evidencias de cierre

Como equipo de desarrollo, quiero probar los flujos completos desde la interfaz y reunir las evidencias, para demostrar que la solución funciona integrada.

**DoR**
- HU funcionales de las Épicas 1 a 5 terminadas; Resend y los stubs de MS06 disponibles.

**MoSCoW**
- Must Have: demuestra la integración de toda la solución.

**Criterios de aceptación**
- Dado el flujo del coordinador, entonces la prueba E2E crea una alerta, la edita, la programa, la activa, la finaliza y la archiva desde la interfaz, y verifica el estado en cada paso.
- Dado el flujo del destinatario, entonces la prueba E2E abre el enlace, confirma la recepción y la segunda confirmación no duplica.
- Dado el flujo del auditor, entonces se ve el historial completo de la alerta de los flujos anteriores.
- Dado cada flujo, entonces los datos quedan persistidos en PostgreSQL y se comprueba con una consulta.

**DoD**
- Evidencias en `docs/evidence/` y README final con instrucciones para levantar todo en local.

**Tareas**
- Tarea 6.1.3.1 `[PRUEBAS]` Escribir las pruebas E2E con Playwright del flujo del coordinador (crear → editar → programar → activar → finalizar → archivar), con el servicio de correo simulado.
- Tarea 6.1.3.2 `[PRUEBAS]` Escribir las pruebas E2E del flujo del destinatario (enlace del correo → login → ver → confirmar → confirmar de nuevo) y del flujo del auditor (ver historial).
- Tarea 6.1.3.3 `[PRUEBAS]` Escribir las pruebas E2E de los casos de error visibles: formulario inválido, edición de alerta no editable, activación repetida y alerta no vigente.
- Tarea 6.1.3.4 `[INT]` Prueba final conjunta con MS06 (ciclo completo) y con MS01/MS02 reales si ya están disponibles.
- Tarea 6.1.3.5 `[DOCS]` Reunir las evidencias: PostgreSQL en Docker, tabla con datos tras las pruebas, reporte JaCoCo, cobertura de Vitest, reportes de Sonar, ZAP y k6, correo recibido, y capturas del flujo completo.
- Tarea 6.1.3.6 `[DOCS]` Completar el README: arquitectura, cómo levantar base de datos, backend y frontend, variables de entorno, cómo ejecutar cada tipo de prueba y enlaces a los artefactos de diseño.
- Tarea 6.1.3.7 `[DISEÑO]` Actualizar los diagramas de LucidChart, el contrato OpenAPI y los mockups de Figma para que reflejen lo realmente implementado.

---

## Orden de entrega sugerido

**Sprint 1**
1. **Fase 1: Diseño.** HU 0.1.1, 0.1.2, 0.1.3 y 0.1.4 completas, más el Gate de diseño. No se escribe código de negocio antes del gate. Las reglas de negocio 2, 4 y 8 deben llegar antes del gate.
2. **Fase 2a: Base técnica.** HU 0.2.1 a 0.2.4 (base de datos, backend, frontend y pipeline), con stubs de MS01/MS02.
3. **Fase 2b: Ver y registrar.** HU 1.1.1, 5.2.1, 5.1.1 y 5.1.2 (crear alertas y verlas en el dashboard, con auditoría desde el inicio).
4. **Fase 2c: Ciclo de vida y trazabilidad.** HU 1.2.1, Épica 2 (con las dos rutas hacia Activa) y HU 5.2.2.

**Sprint 2**
5. **Fase 2d: Notificación.** Épica 3 (requiere que la activación funcione; puede adelantarse si hay capacidad).
6. **Fase 2e: Integración.** Épica 4. Planear las pruebas conjuntas con MS06 con anticipación.
7. **Fase 3: Verificación.** Épica 6.

En cada HU de implementación, backend y frontend se construyen en el mismo sprint, para entregar la funcionalidad completa.

## Notas de revisión (cambios de v2 a v3, vigentes)

- **El diseño va primero.** Se creó el Feature 0.1 con cuatro HU de diseño (datos y arquitectura, contrato de la API, interfaces en Figma, plan de pruebas) y un Gate de diseño antes de implementar. Las tareas `[DISEÑO]` ya no están dispersas en cada HU.
- **Se eliminaron los diagramas de secuencia.** Se reemplazaron por diagramas ER, de arquitectura, de capas, de clases, de estados, de flujo y de despliegue en LucidChart. Los mockups están en Figma.
- **Tareas de frontend detalladas.** Cada una nombra el componente, el hook o la función de la API, los campos, las reglas de validación, los estados (cargando, vacío, error) y los mensajes. Se usa la estructura del laboratorio (`api/`, `features/`, `components`, `hooks`, `pages`).
- **Tareas de backend por capas.** Cada endpoint tiene tareas de DTO, mapper, repository, interfaz de service con su implementación, controller, excepciones y Swagger.
- **Manejo de excepciones explícito.** Hay un catálogo de errores diseñado desde el inicio, excepciones propias, `GlobalExceptionHandler`, y en el frontend `ApiError`, `getErrorMessage` y las pantallas de error. Los criterios de aceptación incluyen los casos de error.
- **Pruebas por capa.** Cada HU lista los casos de prueba por nombre: service con Mockito, controller con MockMvc, repository con Testcontainers y componentes con Vitest.
- **Análisis estático y dinámico.** El estático (SonarCloud, SpotBugs, ESLint, Dependency-Check, `npm audit`, gitleaks) corre en cada Pull Request desde la HU 0.2.4. El dinámico (OWASP ZAP, k6, pruebas de autorización y de concurrencia) está en la Épica 6, y las pruebas de integración con PostgreSQL real ya están en cada HU.
- **PostgreSQL en Docker con scripts SQL**, como en el laboratorio (`postgres:17-alpine`, `ddl-auto=validate`).
- **Se agregó la Épica 6** (verificación y cierre).
- **Frontend en React + Vite sin TypeScript**, igual que el laboratorio.
- **Decisiones que cambian respecto a los requerimientos** (a registrar en `decisiones.md` y ajustar en el documento de requerimientos): (1) un filtro inválido en el listado responde 400, en vez de ignorarse como dice el flujo alterno A2 de RF-ALT-02; (2) el listado filtra por fecha de creación; (3) la auditoría se escribe en la misma transacción que la operación.

---

## Decisiones de arquitectura a confirmar con el equipo

1. **Resend en vez de Firebase Cloud Messaging.** El documento del curso permite un mecanismo alterno, pero cambia la "notificación push a usuarios conectados" por "correo asíncrono". Confirmar que es aceptable para la sustentación y dejarlo explícito en `decisiones.md`.
2. **React + Vite sin TypeScript.** Desviación consciente del stack propuesto en el documento (que especifica TypeScript). Documentar como decisión de equipo.
3. **Rol `RECIPIENT` eliminado del diseño de seguridad.** Se reemplaza por "usuario autenticado sin rol elevado" + `AlertAccessPolicy`. Pendiente confirmar con el squad de MS01 los nombres exactos de rol que emite su JWT.
4. **Reglas de negocio 2, 4 y 8.** No están documentadas en ningún artefacto disponible para este squad. Deben obtenerse antes del Gate de diseño; mientras tanto, HU 0.1.1 tiene cobertura parcial de reglas (6 de 9).
5. **Restricción de unicidad por `simulation_id`** (una alerta activa/programada por simulacro): confirmar con el squad de MS06 que esta es la semántica esperada (un simulacro = una sola alerta viva a la vez) y no, por ejemplo, que un simulacro pueda reabrir una alerta finalizada con una nueva.

## Puntos abiertos detectados al unificar v3 y v4

1. **`DUPLICATE_SIMULATION_ALERT` (409) vs. respuesta idempotente (200).** El v4 agrega el código 409 al catálogo de errores, pero también dice que HU 4.1.1 responde 200 con la alerta existente ante un `simulationId` repetido. Ambas cosas no pueden ser el comportamiento del mismo caso. Hay que decidir si el 409 se elimina del catálogo o si aplica a otro caso (por ejemplo, un reintento con datos distintos). Mientras tanto, HU 4.1.1 sigue la versión idempotente (200).
2. **Alcance de la unicidad.** La restricción condicional solo cubre alertas `SCHEDULED` o `ACTIVE`. Una alerta `DRAFT` creada para un simulacro no está cubierta, así que activar un `DRAFT` cuando ya hay otra viva para el mismo `simulation_id` chocaría con la restricción. Definir qué error devuelve ese caso.
