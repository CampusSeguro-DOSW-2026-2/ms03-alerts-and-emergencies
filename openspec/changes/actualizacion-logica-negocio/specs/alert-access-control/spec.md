# Spec Delta

## Purpose

Define los permisos de gestión y consulta, la visibilidad de las alertas para sus destinatarios y el tratamiento de los metadatos de servicio en la autorización y la auditoría.

## ADDED Requirements

### Requirement: Permiso de gestión
El permiso `manage` SHALL permitir crear, editar, programar, activar y archivar cualquier alerta. El permiso `manage_drill` SHALL permitir las mismas operaciones solo sobre alertas con `origin=SIMULATION`.

#### Scenario: Gestión de una alerta manual
- **WHEN** un actor con `manage` opera una alerta `MANUAL`
- **THEN** la operación se permite

#### Scenario: Gestión de simulacros
- **WHEN** un actor con `manage_drill` opera una alerta `SIMULATION`
- **THEN** la operación se permite

#### Scenario: Simulacro con permiso insuficiente
- **WHEN** un actor con `manage_drill` intenta operar una alerta `MANUAL`
- **THEN** MS03 responde `403` con código `PROHIBIDO`

### Requirement: Permisos de consulta
El permiso `read_history` SHALL permitir consultar el listado y el detalle de alertas. El permiso `receive` SHALL permitir consultar la bandeja y confirmar alertas.

#### Scenario: Listado sin permiso
- **WHEN** un actor sin `read_history` consulta el listado de alertas
- **THEN** MS03 responde `403` con código `PROHIBIDO`

#### Scenario: Bandeja con permiso de recepción
- **WHEN** un actor con `receive` consulta su bandeja
- **THEN** MS03 responde `200` con sus alertas

### Requirement: Visibilidad del detalle
El detalle de una alerta SHALL ser visible para los roles con acceso global (`COORDINADOR`, `ORGANIZADOR`, `AUDITOR`) y para los destinatarios `USER`, por rol o con entrega registrada. Cualquier otro actor SHALL recibir `403 PROHIBIDO`.

#### Scenario: Destinatario consulta el detalle
- **WHEN** un destinatario de la alerta consulta su detalle
- **THEN** MS03 responde `200`

#### Scenario: Actor ajeno consulta el detalle
- **WHEN** un actor que no es destinatario ni tiene acceso global consulta el detalle
- **THEN** MS03 responde `403` con código `NO_ES_DESTINATARIO`

### Requirement: Estado del actor de servicio
La cabecera `X-Service-Name` SHALL ser un metadato opcional de auditoría. Su presencia SHALL NOT modificar el origen de la alerta ni otorgar permisos, y el valor `ms06` SHALL NOT tener un comportamiento especial.

#### Scenario: Servicio ms06 crea una alerta manual
- **WHEN** una solicitud con `X-Service-Name: ms06` crea una alerta sin `origin=SIMULATION`
- **THEN** la alerta conserva `origin=MANUAL` y no se fuerza un simulacro

#### Scenario: Servicio sin permisos
- **WHEN** una solicitud con `X-Service-Name` no tiene un rol autorizado para la operación
- **THEN** MS03 responde `403` con código `PROHIBIDO`

### Requirement: Identidad de auditoría del servicio
Cuando `X-Service-Name` está presente, la auditoría SHALL registrar al actor como `{servicio}:{userId}` con `actorType=SERVICE`. Sin la cabecera, la auditoría SHALL registrar al usuario con `actorType=USER`.

#### Scenario: Auditoría de solicitud de servicio
- **WHEN** una solicitud con `X-Service-Name: ms06` y `X-User-Id: 123` realiza una operación auditada
- **THEN** el evento registra el actor `ms06:123` y `actorType=SERVICE`

#### Scenario: Auditoría de solicitud de usuario
- **WHEN** una solicitud sin `X-Service-Name` realiza una operación auditada
- **THEN** el evento registra al usuario y `actorType=USER`

### Requirement: Rol ADMIN sin permisos MS03
El rol `ADMIN` SHALL NOT tener permisos de MS03. La gestión de alertas SHALL realizarse con un rol que tenga los permisos correspondientes, como `COORDINADOR`; `ADMINISTRATIVO` conserva únicamente el permiso de recepción.

#### Scenario: ADMIN intenta crear una alerta
- **WHEN** un actor con rol `ADMIN` crea una alerta
- **THEN** MS03 responde `403` con código `PROHIBIDO`

#### Scenario: COORDINADOR crea una alerta
- **WHEN** un actor con rol `COORDINADOR` y permiso `manage` crea una alerta
- **THEN** MS03 responde `201`

#### Scenario: ADMINISTRATIVO intenta crear una alerta
- **WHEN** un actor con rol `ADMINISTRATIVO` crea una alerta
- **THEN** MS03 responde `403` con código `PROHIBIDO`
