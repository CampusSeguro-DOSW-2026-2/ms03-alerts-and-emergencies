# Spec Delta

## Purpose

Define el catálogo de tipos de alerta, el origen de la alerta y las reglas de combinación, incluyendo el tipo operativo para mensajes que no son de emergencia.

## ADDED Requirements

### Requirement: Múltiples tipos por alerta
Una alerta SHALL tener entre uno y varios tipos del catálogo. Los tipos SHALL ser únicos dentro de la alerta y SHALL conservarse al crearla, editarla y consultarla.

#### Scenario: Alerta con dos tipos
- **WHEN** se crea una alerta con `types: [FIRE, EARTHQUAKE]`
- **THEN** MS03 responde `201` y devuelve ambos tipos en `types`

#### Scenario: Alerta sin tipos
- **WHEN** se crea una alerta con `types` vacío o ausente
- **THEN** MS03 responde `400` con código `VALIDACION`

#### Scenario: Tipos duplicados
- **WHEN** se crea una alerta con `types: [FIRE, FIRE]`
- **THEN** MS03 responde `400` con código `VALIDACION`

### Requirement: Catálogo de tipos válidos
Los únicos tipos válidos SHALL ser `EARTHQUAKE`, `FIRE`, `OTHER` y `OPERATIVA`. Un valor distinto SHALL rechazarse con `400 VALIDACION`.

#### Scenario: Tipo desconocido
- **WHEN** se envía un tipo que no pertenece al catálogo
- **THEN** MS03 responde `400` con código `VALIDACION`

#### Scenario: Tipo SIMULATION como valor de types
- **WHEN** se envía `SIMULATION` dentro de `types`
- **THEN** MS03 responde `400` con código `VALIDACION`

### Requirement: Origen separado del tipo
El origen SHALL ser `MANUAL` o `SIMULATION` y SHALL describir si la alerta es un simulacro, no el fenómeno. `origin=SIMULATION` SHALL exigir `simulationId`; `origin=MANUAL` SHALL rechazar `simulationId`.

#### Scenario: Simulacro con identificador
- **WHEN** se crea una alerta con `origin.type=SIMULATION` y `simulationId`
- **THEN** MS03 crea la alerta y conserva el identificador

#### Scenario: Simulacro sin identificador
- **WHEN** se crea una alerta con `origin.type=SIMULATION` sin `simulationId`
- **THEN** MS03 responde `400` con código `VALIDACION`

#### Scenario: Alerta manual con identificador de simulacro
- **WHEN** se crea una alerta con `origin.type=MANUAL` y `simulationId`
- **THEN** MS03 responde `400` con código `VALIDACION`

### Requirement: Tipo OPERATIVA exclusivo
`OPERATIVA` SHALL representar mensajes de operatividad, no de emergencia. Una alerta con `OPERATIVA` SHALL tener exactamente ese tipo y SHALL NOT tener `origin=SIMULATION`; una violación SHALL responder `400 VALIDACION`.

#### Scenario: Alerta operativa
- **WHEN** se crea una alerta con `types: [OPERATIVA]` y `origin.type=MANUAL`
- **THEN** MS03 crea la alerta y la marca como operativa

#### Scenario: Operativa combinada con emergencia
- **WHEN** se crea una alerta con `types: [OPERATIVA, FIRE]`
- **THEN** MS03 responde `400` con código `VALIDACION`

#### Scenario: Operativa como simulacro
- **WHEN** se crea una alerta con `types: [OPERATIVA]` y `origin.type=SIMULATION`
- **THEN** MS03 responde `400` con código `VALIDACION`

### Requirement: Filtro por tipos
El listado SHALL aceptar el parámetro repetido `type` y SHALL devolver las alertas que contengan al menos uno de los tipos solicitados.

#### Scenario: Filtro por dos tipos
- **WHEN** se consulta `GET /api/v1/alertas?type=FIRE&type=EARTHQUAKE`
- **THEN** MS03 devuelve las alertas que tengan `FIRE`, `EARTHQUAKE` o ambos

#### Scenario: Filtro sin coincidencias
- **WHEN** se consulta un tipo que no tiene alertas asociadas
- **THEN** MS03 devuelve una página vacía con `200`
