# Proposal

## Why

MS03 nació acoplado a MS06 y a un modelo de alerta con un solo tipo y un ciclo de vida que incluye `ENDED` y `finalizar`, aunque una alerta es un mensaje instantáneo que no transcurre durante un periodo. Tampoco existe una bandeja para que el destinatario descubra y confirme sus alertas, ni un tipo de mensaje operativo que se distinga de una emergencia. Esto limita la reutilización del servicio y obliga a MS06 a recibir un trato especial en lugar de usar el API como cualquier otro cliente.

## What Changes

- Reemplazar el tipo único por `types` de 1..N valores. `SIMULATION` deja de ser un tipo y se expresa con `origin` (`MANUAL` o `SIMULATION`). Se agrega `OPERATIVA` como tipo exclusivo para mensajes de operatividad, no de emergencia.
- Cambiar el ciclo de vida a `DRAFT -> SCHEDULED -> ACTIVE -> ARCHIVED`. Se eliminan `ENDED`, `endedAt` y `PATCH /{id}/finalizar`; se permite archivar desde `DRAFT`, `SCHEDULED` y `ACTIVE`.
- Mantener la activación inmediata sin `activationAt`, usando `activateImmediately` o `PATCH /{id}/activar`.
- Agregar la bandeja del destinatario `GET /api/v1/alertas/mias`, con permiso `receive`, filtros `ACTIVE`, `ARCHIVED` y `ALL`, paginación y `confirmedAt`.
- Enviar un mensaje base en los correos de emergencia; omitirlo en las alertas `OPERATIVA`. El correo enlaza a la bandeja para que el usuario confirme explícitamente.
- Desacoplar MS06: eliminar las reglas que fuerzan `origin=SIMULATION` por `X-Service-Name: ms06` y el alias `eventId`. `X-Service-Name` queda como metadato opcional de auditoría.
- Migrar los datos existentes: `alerts.type` a `alert_types`, `ENDED` a `ARCHIVED` y `SIMULATION` a `OTHER` conservando `origin=SIMULATION`.
- Actualizar el contrato OpenAPI, el README, la colección Postman y las pruebas.
- **BREAKING**: `type` pasa a `types`; desaparecen `ENDED` y `/finalizar`; cambia la semántica de `X-Service-Name`; los clientes que envíen `type` o `status=ENDED` deberán migrar.

## Capabilities

### New Capabilities

- `alert-catalog`: tipos de alerta, origen, reglas de combinación y tipo operativo.
- `alert-lifecycle`: creación, programación, activación inmediata, archivado y transiciones de estado.
- `alert-inbox`: bandeja del destinatario, visibilidad de sus alertas y confirmación de recepción.
- `alert-notifications`: contenido del correo, mensaje base y exclusión del mensaje base para alertas operativas.
- `alert-access-control`: permisos de gestión y archivo, visibilidad por destinatario y metadatos de servicio para auditoría.
- `alert-api-contract`: contrato HTTP, DTOs, filtros, estados, errores y paridad estático/runtime.
- `alert-audit`: acciones y estados de auditoría, clasificación de actores y conservación de valores legados.

### Modified Capabilities

Ninguna. No existen specs previas en `openspec/specs/`.

## Impact

- Código: `AlertEntity`, `AlertType`, `AlertStatus`, `AlertMapper`, `AlertServiceImpl`, `AlertLifecycleServiceImpl`, `AlertStateMachine`, `AlertStateRules`, `AlertController`, `ConfirmationController`, `EmailTemplateBuilder`, `RequesterContext`, `AuditQueryServiceImpl`, repositorios y especificaciones.
- API: `POST /api/v1/alertas`, `PUT /api/v1/alertas/{id}`, `GET /api/v1/alertas`, `PATCH /{id}/programar`, `PATCH /{id}/activar`, `PATCH /{id}/archivar`, `GET /api/v1/alertas/mias` y `POST /{id}/confirmaciones`; se elimina `PATCH /{id}/finalizar`.
- Base de datos: migración `005` con la tabla `alert_types`, transformación de `ENDED` a `ARCHIVED`, retiro de `ended_at`, ajuste de constraints e índices para la bandeja.
- Contrato: `docs/api/openapi.yaml`, `README.md` y `docs/postman/ms03-alertas.postman_collection.json`.
- Pruebas: unitarias, integración con Testcontainers, contrato estático/runtime y migración de datos.
- Fuera de alcance: frontend (solo se documenta la ruta de la bandeja), acuses de entrega o lectura de Brevo, rediseño completo de la matriz de roles y cualquier cambio del cambio anterior `actualizar-reglas-alertas`, que se ignora.
