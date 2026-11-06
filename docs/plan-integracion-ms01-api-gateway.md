# Plan de integracion de MS03 con MS01 y API Gateway

> Rutas relativas a la raiz del workspace. Fuente de reglas: `documentation.wiki` y documentacion de MS01/API Gateway.

## Estado actual

MS03 ya no valida JWT ni configura CORS. Autentica con `X-User-Id`, `X-User-Role` y `X-Correlation-ID`, usa los 8 roles oficiales y permisos `ms03:*`, devuelve errores de 5 campos, pagina con nombres en espanol y consulta MS01 con el contrato real. El stub de catalogo queda para `local`/`test`; el perfil `integration` usa MS01.

La integracion fue verificada de extremo a extremo el 2026-10-27 con la imagen `ghcr.io/campusseguro-dosw-2026-2/api-gateway:integracion` y MS01 `develop` (`40d476e8`). La imagen del Gateway implementa RF-GW-08, elimina las cabeceras de identidad del cliente y enruta MS03; el `develop` local del Gateway todavia no contiene esos cambios.

Queda un bloqueo externo: MS01 no expone un endpoint interno o por lote para resolver destinatarios sin una cuenta con `ms01:user:read`. Como workaround de integracion, MS03 usa una cuenta tecnica `AUDITOR` creada en la base local de MS01.

## Cambios implementados en MS03

### 1. Seguridad y contrato de entrada

- Se elimino la validacion JWT (`oauth2ResourceServer`), `LocalSecurityConfig`, `LocalTokenController`, `LocalTokenService`, `JwtRoleConverter` y la dependencia OAuth2.
- `IdentityHeaderParser` valida `X-User-Id` (UUID), `X-User-Role` (lista de roles oficiales) y `X-Service-Name`.
- `IdentityHeadersAuthenticationFilter` crea un `RequesterIdentity`; si faltan o son invalidas las cabeceras en un endpoint protegido responde `401 NO_AUTENTICADO`.
- `Permission` y `UserRole` implementan la matriz oficial:
  - COORDINADOR: `receive`, `manage`, `read_history`.
  - ORGANIZADOR: `receive`, `manage_drill`, `read_history`.
  - AUDITOR: `read_history`.
  - ESTUDIANTE, PROFESOR, ADMINISTRATIVO y BRIGADISTA: `receive`.
  - ADMIN: sin permisos MS03.
- Los controladores usan `hasAuthority`:
  - crear/activar/finalizar: `manage` o `manage_drill`;
  - editar/programar/archivar: `manage`;
  - consultar alerta: `receive` o `read_history`;
  - confirmar: `receive`;
  - auditoria: `read_history`.
- `AlertAccessPolicy` usa `COORDINADOR`, `ORGANIZADOR` y `AUDITOR` como roles privilegiados y compara destinatarios por UUID.
- `ORGANIZADOR` solo puede crear alertas con `origin.type = SIMULATION`; si no, `403 PROHIBIDO`.
- Se elimino CORS propio; Swagger queda detras de `SPRINGDOC_ENABLED`.

### 2. Correlacion, errores y paginacion

- `CorrelationIdFilter` acepta `X-Correlation-ID` solo si es UUID v4; si no, genera uno. Lo guarda en request, MDC (`correlation_id`) y respuesta, y limpia MDC al finalizar.
- `AlertActivatedEvent` lleva `correlationId`; el listener asincrono restaura el MDC y el scheduler genera uno cuando no hay peticion.
- `ErrorResponse` tiene exactamente `status`, `codigo`, `mensaje`, `errores` y `correlation_id`. `errores` siempre es `[]`, nunca `null`.
- `GlobalExceptionHandler`, `ErrorResponseWriter`, `RestAuthenticationEntryPoint` y `RestAccessDeniedHandler` generan ese formato en 400, 401, 403, 404, 405, 409, 415, 500 y 503.
- Se conservan codigos de dominio traducidos (`TRANSICION_INVALIDA`, `SIMULACION_DUPLICADA`, etc.) y estan declarados en OpenAPI.
- `PageResponse` usa `contenido`, `page`, `size`, `total_elementos` y `total_paginas`.
- El `Location` del 201 es una ruta relativa (`/api/v1/alertas/{id}`).

### 3. Cliente MS01

- `RestCatalogClient` usa el contrato real de MS01:
  - `GET /api/v1/users/{id}` para usuarios;
  - `GET /api/v1/users?rol=&estado=ACTIVO&page=&size=100` paginado para roles.
- Envia `X-User-Id` tecnico, `X-User-Role`, `X-Service-Name: ms03`, `X-Service-Key` si esta configurada y `X-Correlation-ID`.
- Timeouts de conexion y lectura de 2000 ms.
- Valida usuarios activos, roles oficiales y UUIDs antes de llamar.
- `app.catalog.technical-user-id` es obligatorio en el perfil `integration`; se usa una cuenta `AUDITOR` como workaround mientras MS01 no exponga el endpoint interno.
- `StubCatalogClient` y `catalog-stub.json` quedan para `local`/`test`, con UUIDs y roles en espanol.

### 4. Roles, MS06 y datos

- `SERVICE_MS06` desaparece como rol. MS06 llama con identidad de usuario `ORGANIZADOR`, `X-Service-Name: ms06` y el permiso `manage_drill`.
- La finalizacion por MS06 u ORGANIZADOR exige `simulationId` y lo compara con la alerta; si falta responde 400 y si no coincide 409 `CONFLICTO_SIMULACION`.
- Los destinatarios por rol usan los 8 roles oficiales.
- `NotificationDeliveryEntity` sigue persistiendo el correo; queda pendiente evaluar si se elimina para cumplir la minimizacion de datos de la wiki.
- La auditoria local sigue siendo la fuente de `/alertas/{id}/auditoria`; la integracion con MS08 no esta implementada.

### 5. Configuracion y despliegue

- MS03 escucha en `8083`; el `servers` de OpenAPI apunta al Gateway (`http://localhost:8080`).
- Actuator expone solo `health` e `info`; Swagger se controla con `SPRINGDOC_ENABLED`.
- `application-integration.yml` define MS01 real y desactiva Swagger.
- `docker-compose.gw.yml` levanta Gateway, MS01, MS03 y sus dos PostgreSQL. Solo el Gateway publica `8080`; MS01 y MS03 quedan en la red interna.
- MS03 usa `MS01_URL=http://api-gateway:8080`, `MS_SERVICE_KEY`, `MS01_TECHNICAL_USER_ID` y `MS01_TECHNICAL_USER_ROLE=AUDITOR`. Las llamadas al catalogo pasan por el Gateway con `X-Service-Name: ms03`.
- La cuenta tecnica local insertada es `auditor.integracion@escuelaing.edu.co` con id `b4051a1f-8f5c-4b79-b57e-dcbf68166abf`, estado `ACTIVO` y rol `AUDITOR`. No sirve para iniciar sesion; solo para el catalogo.
- En un volumen nuevo de `ms01-db`, `integration/ms01-seed.sql` crea esa cuenta de forma automatica. Si el volumen ya existia, el seed no se ejecuta: aplique el SQL manualmente o recree el volumen.
- El compose fija `GATEWAY_DOWNSTREAM_TIMEOUT_MS=15000`. El valor por defecto de 3 s provocaba `504 TIEMPO_AGOTADO` en el primer login y en la creacion de alertas con varias llamadas a MS01.
- Las claves y contrasenas locales viven en `.env.gw`, ignorado por Git. El par RSA se genero fuera del repositorio.

## Evidencia de verificacion

- `mvn clean verify`: 254 pruebas unitarias y 12 de integracion, 0 fallos, 0 errores.
- Cobertura JaCoCo: linea 92.10 %, instruccion 92.79 %, rama 76.07 %.
- `docker compose --env-file .env.gw -f docker-compose.gw.yml config --quiet`: salida 0.
- Login, registro y asignacion de roles a traves del Gateway: correctos.
- MS03 llamo a MS01 por el Gateway; los logs muestran `upstream=ms01`, `origen=ms03` y el mismo `correlation_id` en la peticion externa y en las llamadas internas.
- Ciclo de vida verificado por el Gateway:
  - creacion activa con `activateImmediately=true`: 201 y estado `ACTIVE`;
  - creacion programada: 201 y estado `SCHEDULED`;
  - activacion manual de una programada: 200 y estado `ACTIVE`;
  - confirmacion del estudiante: 201 la primera vez y 200 en el reintento;
  - alerta programada sin activar: 409 `ALERTA_NO_ACTIVA` al confirmar;
  - finalizacion y archivado: 200 en cada transicion.
- Reglas verificadas:
  - ORGANIZADOR con origen `MANUAL`: 403 `PROHIBIDO`;
  - ORGANIZADOR con origen `SIMULATION`: 201;
  - mismo `simulationId` con los mismos datos: 200 idempotente;
  - mismo `simulationId` con datos distintos: 409 `SIMULACION_DUPLICADA`;
  - destinatario inexistente: 400 `REFERENCIA_INVALIDA` con el campo `destinatarios`;
  - fecha de activacion pasada: 400 `FECHA_ACTIVACION_INVALIDA`;
  - paginacion invalida: 400 `PAGINACION_INVALIDA`;
  - sin JWT pero con `X-User-Id`/`X-User-Role`: 401 `NO_AUTENTICADO` (el Gateway elimina esas cabeceras);
  - destinatario por rol `ESTUDIANTE`: la alerta se crea y el estudiante puede confirmar;
  - auditor: puede listar y consultar auditoria, no puede escribir;
  - `X-Correlation-ID` valido se conserva; uno invalido se reemplaza por un UUID v4.
- Coleccion Postman ejecutada contra el Gateway: 30 peticiones, 82 aserciones, 0 fallos. Incluye login por rol, ciclo de vida, consultas y errores 400/401/403/404/409.
- La coleccion migrada temporalmente a v3 para lint: 30 elementos escaneados, 0 errores, 0 advertencias.

## Criterios de aceptacion

- [x] Sin cabeceras de identidad, MS03 responde `401 NO_AUTENTICADO`; con permiso insuficiente, `403 PROHIBIDO`.
- [x] Todos los errores de MS03 tienen exactamente 5 campos y el mismo `correlation_id`.
- [x] `ORGANIZADOR` solo crea alertas de simulacro; no existe `SERVICE_MS06`.
- [x] El OpenAPI no declara `bearerAuth`, declara las cabeceras, los permisos y la paginacion estandar.
- [x] No quedan referencias a JWT local ni CORS propio en MS03.
- [x] La imagen `:integracion` del Gateway implementa RF-GW-08 y enruta MS03.
- [ ] MS01 expone el endpoint interno de consulta de usuarios.
- [x] MS03 llama a MS01 unicamente por el Gateway; el compose usa `MS01_URL=http://api-gateway:8080`.
- [x] `mvn clean verify` pasa con Docker/Testcontainers disponible.
- [x] El flujo E2E MS03 -> Gateway -> MS01 esta verificado.
- [x] La coleccion Postman puede ejecutarse contra el Gateway con JWT.

## Prerrequisitos y bloqueos externos

- **MS01:** aprobar la decision #2 y exponer el endpoint interno de consulta de usuarios; registrar la llamada en la wiki §5. Sin esto, la resolucion de destinatarios en produccion depende de una cuenta tecnica.
- **Gateway:** el `develop` local no tiene RF-GW-08; usar la imagen `:integracion` o integrar esa rama. Revisar el timeout de downstream para llamadas que consultan varios usuarios.
- **MS02:** acordar el contrato de validacion de alcance; la ruta usada actualmente no esta registrada en la wiki.
- **MS08:** confirmar `POST /api/v1/audit/events` y su ruta interna.
- **Frontend:** adaptar el parser de errores y paginacion al contrato de 5 campos.
- **Correo:** el compose local desactiva Brevo y apunta a un SMTP inexistente; los fallos de notificacion son asincronos y no bloquean la API.

## Decisiones confirmadas

1. La wiki prevalece sobre el formato de errores y roles del `AGENTS.md`/backlog local.
2. Esta iteracion cubre solo MS01; MS02, MS06 y MS08 quedan en fases separadas.
3. Se conservan codigos de error de dominio traducidos, declarados en OpenAPI.
4. La cuenta tecnica `AUDITOR` es un workaround local, no la solucion definitiva de MS01.

## Decisiones pendientes

1. **MS01:** acepta exponer el endpoint interno de consulta por lote? Si no, la resolucion de destinatarios queda limitada a la cuenta tecnica.
2. **MS06:** confirmar que enviara `X-Service-Name: ms06` ademas de la identidad `ORGANIZADOR`.
3. **MS08:** confirmar si `/alertas/{id}/auditoria` se conserva o si MS08 es la fuente unica.
4. **Datos:** decidir si `NotificationDeliveryEntity` deja de persistir el correo.
5. **Timeout:** acordar el valor de downstream del Gateway para llamadas internas que resuelven varios usuarios; el compose local usa 15 s.
