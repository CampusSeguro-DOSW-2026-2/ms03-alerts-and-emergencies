# Spec Delta

## Purpose

Define la bandeja personal del destinatario, la visibilidad de las alertas que le corresponden y la confirmación explícita de recepción.

## ADDED Requirements

### Requirement: Bandeja del destinatario
`GET /api/v1/alertas/mias` SHALL requerir el permiso `receive` y SHALL devolver solo las alertas dirigidas al actor por destinatario `USER`, por rol o mediante una entrega de notificación registrada.

#### Scenario: Destinatario directo
- **WHEN** el actor consulta su bandeja y es destinatario `USER` de una alerta
- **THEN** la alerta aparece en su bandeja

#### Scenario: Destinatario por rol
- **WHEN** el actor consulta su bandeja y su rol es destinatario `ROLE` de una alerta
- **THEN** la alerta aparece en su bandeja

#### Scenario: Actor sin permiso
- **WHEN** un actor sin permiso `receive` consulta la bandeja
- **THEN** MS03 responde `403` con código `PROHIBIDO`

### Requirement: Visibilidad por estado
La bandeja SHALL mostrar por defecto solo alertas `ACTIVE`. El parámetro `status` SHALL aceptar `ACTIVE`, `ARCHIVED` o `ALL`; las alertas `DRAFT` y `SCHEDULED` SHALL NOT aparecer en ningún caso.

#### Scenario: Bandeja por defecto
- **WHEN** el actor consulta la bandeja sin `status`
- **THEN** MS03 devuelve solo sus alertas `ACTIVE`

#### Scenario: Alertas archivadas
- **WHEN** el actor consulta la bandeja con `status=ARCHIVED`
- **THEN** MS03 devuelve solo sus alertas `ARCHIVED`

#### Scenario: Todas las alertas del actor
- **WHEN** el actor consulta la bandeja con `status=ALL`
- **THEN** MS03 devuelve sus alertas `ACTIVE` y `ARCHIVED`

#### Scenario: Alerta en borrador
- **WHEN** existe una alerta `DRAFT` o `SCHEDULED` dirigida al actor
- **THEN** la alerta no aparece en su bandeja

### Requirement: Orden y paginación
La bandeja SHALL ordenar por `activatedAt` descendente y SHALL paginar con `page` desde cero y `size` con valor por defecto 20 y máximo 100.

#### Scenario: Orden por activación
- **WHEN** el actor tiene varias alertas activas
- **THEN** la más recientemente activada aparece primero

#### Scenario: Tamaño fuera de rango
- **WHEN** se solicita `size` mayor que 100
- **THEN** MS03 responde `400` con código `PAGINACION_INVALIDA`

### Requirement: Contenido de cada alerta
Cada elemento de la bandeja SHALL incluir `id`, `types`, `title`, `status`, `origin`, `scope`, `instructions`, `activatedAt`, `archivedAt` y `confirmedAt`.

#### Scenario: Alerta activa sin confirmar
- **WHEN** una alerta activa dirigida al actor no ha sido confirmada
- **THEN** el elemento incluye `confirmedAt` en `null`

#### Scenario: Alerta activa confirmada
- **WHEN** una alerta activa dirigida al actor ya fue confirmada
- **THEN** el elemento incluye la fecha de confirmación en `confirmedAt`

### Requirement: Confirmación de recepción
`POST /api/v1/alertas/{id}/confirmaciones` SHALL requerir el permiso `receive`. La primera confirmación SHALL responder `201`; una confirmación repetida del mismo actor SHALL responder `200` con el registro existente.

#### Scenario: Primera confirmación
- **WHEN** un destinatario confirma por primera vez una alerta `ACTIVE`
- **THEN** MS03 responde `201` y registra `confirmedAt`

#### Scenario: Confirmación repetida
- **WHEN** el mismo destinatario confirma de nuevo la misma alerta
- **THEN** MS03 responde `200` y conserva el `confirmedAt` original

### Requirement: Errores de confirmación
La confirmación SHALL responder `409 ALERTA_NO_ACTIVA` si la alerta no está `ACTIVE`, `403 NO_ES_DESTINATARIO` si el actor no es destinatario y `404 ALERTA_NO_ENCONTRADA` si la alerta no existe.

#### Scenario: Confirmar una alerta archivada
- **WHEN** un destinatario confirma una alerta `ARCHIVED`
- **THEN** MS03 responde `409` con código `ALERTA_NO_ACTIVA`

#### Scenario: Confirmar sin ser destinatario
- **WHEN** un actor que no es destinatario confirma una alerta existente
- **THEN** MS03 responde `403` con código `NO_ES_DESTINATARIO`

#### Scenario: Confirmar una alerta inexistente
- **WHEN** se confirma un identificador que no existe
- **THEN** MS03 responde `404` con código `ALERTA_NO_ENCONTRADA`

### Requirement: Confirmación concurrente
Dos confirmaciones simultáneas del mismo destinatario SHALL producir un único registro de confirmación y un único evento de auditoría de tipo `CONFIRM`.

#### Scenario: Confirmaciones simultáneas
- **WHEN** el mismo destinatario envía dos confirmaciones al mismo tiempo
- **THEN** solo se persiste una confirmación y se registra un único evento `CONFIRM`

### Requirement: Confirmación explícita
Abrir el enlace del correo SHALL NOT confirmar la alerta. La confirmación SHALL requerir una acción explícita del destinatario mediante el endpoint de confirmaciones.

#### Scenario: Abrir el enlace del correo
- **WHEN** el destinatario abre el enlace de la alerta desde el correo
- **THEN** la alerta sigue sin confirmar hasta que envíe la confirmación
