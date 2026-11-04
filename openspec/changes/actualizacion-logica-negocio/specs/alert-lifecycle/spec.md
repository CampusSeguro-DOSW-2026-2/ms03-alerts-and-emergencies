# Spec Delta

## Purpose

Define la creación, programación, activación, edición y archivado de alertas, junto con las transiciones de estado permitidas y la idempotencia de los simulacros.

## ADDED Requirements

### Requirement: Estados válidos
Los estados de una alerta SHALL ser `DRAFT`, `SCHEDULED`, `ACTIVE` y `ARCHIVED`. El estado `ENDED` SHALL NOT ser válido ni exponerse en el contrato.

#### Scenario: Estado ENDED rechazado
- **WHEN** se envía o se filtra por `status=ENDED`
- **THEN** MS03 responde `400` con código `VALIDACION`

#### Scenario: Estados expuestos
- **WHEN** se consulta una alerta
- **THEN** su `status` es uno de `DRAFT`, `SCHEDULED`, `ACTIVE` o `ARCHIVED`

### Requirement: Transiciones permitidas
Las transiciones SHALL ser `DRAFT -> SCHEDULED`, `DRAFT -> ACTIVE`, `DRAFT -> ARCHIVED`, `SCHEDULED -> ACTIVE`, `SCHEDULED -> ARCHIVED` y `ACTIVE -> ARCHIVED`. Cualquier otra transición SHALL responder `409 TRANSICION_INVALIDA`.

#### Scenario: Programar un borrador
- **WHEN** se programa una alerta en `DRAFT` con una fecha futura
- **THEN** la alerta pasa a `SCHEDULED`

#### Scenario: Activar una alerta programada
- **WHEN** se activa manualmente una alerta en `SCHEDULED`
- **THEN** la alerta pasa a `ACTIVE`

#### Scenario: Reactivar una alerta archivada
- **WHEN** se intenta activar una alerta en `ARCHIVED`
- **THEN** MS03 responde `409` con código `TRANSICION_INVALIDA`

### Requirement: Activación inmediata
Al crear, `activateImmediately=true` SHALL producir una alerta `ACTIVE` con `activatedAt` y sin `activationAt`. Si se envían `activateImmediately=true` y `activationAt` juntos, MS03 SHALL responder `400 VALIDACION`.

#### Scenario: Crear y activar de inmediato
- **WHEN** se crea una alerta con `activateImmediately=true`
- **THEN** MS03 responde `201`, la alerta queda `ACTIVE` y `activatedAt` tiene la fecha de creación

#### Scenario: Activación inmediata con fecha programada
- **WHEN** se crea una alerta con `activateImmediately=true` y `activationAt`
- **THEN** MS03 responde `400` con código `VALIDACION`

#### Scenario: Activación manual sin fecha
- **WHEN** se activa una alerta `DRAFT` o `SCHEDULED` con `PATCH /{id}/activar` sin `activationAt`
- **THEN** la alerta pasa a `ACTIVE` con `activatedAt` en el momento de la activación

### Requirement: Programación
`activationAt` SHALL ser una fecha futura. Una fecha pasada o igual al momento de la solicitud SHALL responder `400 FECHA_ACTIVACION_INVALIDA`. La activación programada SHALL ejecutarse cuando llegue o supere `activationAt`.

#### Scenario: Programar con fecha pasada
- **WHEN** se crea o programa una alerta con `activationAt` anterior o igual al momento actual
- **THEN** MS03 responde `400` con código `FECHA_ACTIVACION_INVALIDA`

#### Scenario: Activación automática
- **WHEN** llega `activationAt` de una alerta `SCHEDULED`
- **THEN** la alerta pasa a `ACTIVE` y registra `activatedAt`

#### Scenario: Activación manual anticipada
- **WHEN** se activa manualmente una alerta `SCHEDULED` antes de su `activationAt`
- **THEN** la alerta pasa a `ACTIVE` sin esperar la fecha programada

### Requirement: Archivado manual
Una alerta `DRAFT`, `SCHEDULED` o `ACTIVE` SHALL poder archivarse y pasar a `ARCHIVED` con `archivedAt`. Archivar una alerta ya `ARCHIVED` SHALL ser idempotente y responder `200` sin modificar `archivedAt`.

#### Scenario: Archivar una alerta activa
- **WHEN** se archiva una alerta `ACTIVE`
- **THEN** la alerta pasa a `ARCHIVED` y registra `archivedAt`

#### Scenario: Archivar una alerta programada
- **WHEN** se archiva una alerta `SCHEDULED`
- **THEN** la alerta pasa a `ARCHIVED` y deja de activarse en su fecha

#### Scenario: Archivado repetido
- **WHEN** se archiva una alerta que ya está `ARCHIVED`
- **THEN** MS03 responde `200` y conserva el `archivedAt` existente

### Requirement: Edición restringida
La edición SHALL permitirse solo en alertas `DRAFT` o `SCHEDULED`. Una alerta `ACTIVE` o `ARCHIVED` SHALL responder `409 ESTADO_ALERTA_INVALIDO` ante una edición.

#### Scenario: Editar un borrador
- **WHEN** se actualiza una alerta `DRAFT` con un nuevo conjunto de `types`
- **THEN** MS03 responde `200` y conserva los nuevos tipos

#### Scenario: Editar una alerta activa
- **WHEN** se intenta actualizar una alerta `ACTIVE`
- **THEN** MS03 responde `409` con código `ESTADO_ALERTA_INVALIDO`

### Requirement: Idempotencia de simulacros
Para `origin=SIMULATION` con `simulationId`, la creación SHALL ser idempotente mientras exista una alerta `SCHEDULED` o `ACTIVE` con ese identificador. Un payload equivalente SHALL responder `200` con la alerta existente; un payload distinto SHALL responder `409 SIMULACION_DUPLICADA`.

#### Scenario: Repetir el mismo simulacro
- **WHEN** se crea un simulacro con un `simulationId` que ya tiene una alerta viva y el payload es equivalente
- **THEN** MS03 responde `200` con la alerta existente

#### Scenario: Simulacro con payload distinto
- **WHEN** se crea un simulacro con un `simulationId` que ya tiene una alerta viva y el payload es distinto
- **THEN** MS03 responde `409` con código `SIMULACION_DUPLICADA`

#### Scenario: Simulacro archivado
- **WHEN** se crea un simulacro con el `simulationId` de una alerta `ARCHIVED`
- **THEN** MS03 crea una nueva alerta

### Requirement: Sin finalización
La operación de finalización, el estado `ENDED` y el campo `endedAt` SHALL eliminarse del contrato. Una solicitud a `PATCH /{id}/finalizar` SHALL responder `404`.

#### Scenario: Finalizar una alerta
- **WHEN** un cliente llama a `PATCH /api/v1/alertas/{id}/finalizar`
- **THEN** MS03 responde `404`

#### Scenario: Respuesta sin endedAt
- **WHEN** se consulta una alerta
- **THEN** la respuesta no incluye `endedAt`
