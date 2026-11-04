# Spec Delta

## Purpose

Define los eventos de auditoría del ciclo de vida, la clasificación de actores, los estados auditados y la conservación de los valores legados.

## ADDED Requirements

### Requirement: Eventos de ciclo de vida
MS03 SHALL auditar `CREATE`, `UPDATE`, `SCHEDULE`, `ACTIVATE`, `ARCHIVE` y `CONFIRM`. Las nuevas operaciones SHALL NOT generar eventos `END`.

#### Scenario: Creación auditada
- **WHEN** se crea una alerta
- **THEN** se registra un evento `CREATE` con el identificador de la alerta

#### Scenario: Archivado auditado
- **WHEN** se archiva una alerta
- **THEN** se registra un evento `ARCHIVE` con el estado anterior y el nuevo estado

#### Scenario: Finalización no auditada
- **WHEN** se realiza cualquier operación del nuevo ciclo de vida
- **THEN** no se registra un evento `END`

### Requirement: Estados de auditoría
Los eventos nuevos SHALL usar los estados `DRAFT`, `SCHEDULED`, `ACTIVE` y `ARCHIVED`. Los valores legados `END` y `ENDED` SHALL conservarse para lectura y SHALL NOT generarse en nuevas operaciones.

#### Scenario: Evento nuevo con estado archivado
- **WHEN** se archiva una alerta
- **THEN** el evento registra `ARCHIVED` como estado nuevo

#### Scenario: Lectura de eventos legados
- **WHEN** se consulta la auditoría de una alerta histórica
- **THEN** los eventos `END` o `ENDED` existentes se devuelven sin modificarse

### Requirement: Clasificación del actor
La auditoría SHALL clasificar al actor como `USER`, `SERVICE` o `SYSTEM`. `X-Service-Name` SHALL producir `SERVICE`; las tareas automáticas de activación y notificación SHALL producir `SYSTEM`; el resto SHALL producir `USER`.

#### Scenario: Actor de servicio
- **WHEN** una operación llega con `X-Service-Name: ms06`
- **THEN** el evento registra `actorType=SERVICE` y el actor `ms06:{userId}`

#### Scenario: Actor del sistema
- **WHEN** el planificador activa una alerta programada
- **THEN** el evento registra `actorType=SYSTEM`

#### Scenario: Actor de usuario
- **WHEN** un usuario opera una alerta sin `X-Service-Name`
- **THEN** el evento registra `actorType=USER`

### Requirement: Archivado idempotente
Un archivado repetido de una alerta ya `ARCHIVED` SHALL responder `200` y SHALL NOT generar un nuevo evento `ARCHIVE`.

#### Scenario: Segundo archivado
- **WHEN** se archiva por segunda vez una alerta ya archivada
- **THEN** no se duplica el evento `ARCHIVE`

### Requirement: Correlación de auditoría
Cuando la solicitud incluya `X-Correlation-ID`, el evento de auditoría SHALL conservar ese valor. Sin la cabecera, MS03 SHALL generar un identificador de correlación.

#### Scenario: Correlación proporcionada
- **WHEN** una solicitud incluye `X-Correlation-ID: abc-123`
- **THEN** el evento de auditoría registra `abc-123`

#### Scenario: Correlación generada
- **WHEN** una solicitud no incluye `X-Correlation-ID`
- **THEN** el evento de auditoría registra un identificador generado por MS03

### Requirement: Consulta de auditoría
La consulta de auditoría SHALL requerir el permiso `read_history` y SHALL ser de solo lectura, paginada y filtrable por alerta y tipo de evento.

#### Scenario: Consulta autorizada
- **WHEN** un actor con `read_history` consulta la auditoría
- **THEN** MS03 devuelve los eventos paginados

#### Scenario: Consulta sin permiso
- **WHEN** un actor sin `read_history` consulta la auditoría
- **THEN** MS03 responde `403` con código `PROHIBIDO`
