# Spec Delta

## Purpose

Define el contenido de las notificaciones por correo, el mensaje base de las alertas de emergencia, la variante operativa y el enlace de acceso a la bandeja.

## ADDED Requirements

### Requirement: Mensaje base de emergencia
Los correos de alertas de emergencia SHALL incluir el mensaje base: “Has recibido una alerta oficial de CampusSeguro. Confirma la recepción y sigue los pasos indicados: {instrucciones}. Ver y confirmar alerta: {link}”.

#### Scenario: Correo de incendio
- **WHEN** se activa una alerta con `types: [FIRE]`
- **THEN** el correo incluye el mensaje base con las instrucciones y el enlace

#### Scenario: Correo con varios tipos
- **WHEN** se activa una alerta con `types: [FIRE, EARTHQUAKE]`
- **THEN** el correo incluye el mensaje base una sola vez y menciona ambos tipos

### Requirement: Alerta operativa sin mensaje base
Si `types` contiene `OPERATIVA`, el correo SHALL omitir el mensaje base y SHALL incluir el título, las instrucciones y el enlace de la alerta.

#### Scenario: Correo operativo
- **WHEN** se activa una alerta con `types: [OPERATIVA]`
- **THEN** el correo no incluye el mensaje base y sí incluye el título y las instrucciones

### Requirement: Asunto del correo
El asunto de una alerta de emergencia SHALL ser `Alerta CampusSeguro: {tipos}`, con las etiquetas legibles de los tipos en orden canónico. El asunto de una alerta operativa SHALL ser `[Operativa] {título}`.

#### Scenario: Asunto de emergencia
- **WHEN** se envía una alerta con `types: [FIRE, EARTHQUAKE]`
- **THEN** el asunto es `Alerta CampusSeguro: Terremoto, Incendio`

#### Scenario: Asunto operativo
- **WHEN** se envía una alerta con `types: [OPERATIVA]` y título `Cierre de biblioteca`
- **THEN** el asunto es `[Operativa] Cierre de biblioteca`

### Requirement: Enlace a la bandeja
El correo SHALL enlazar a `{APP_BASE_URL}/bandeja?alerta={id}`. Si `APP_BASE_URL` no está configurada, el correo SHALL omitir el enlace sin impedir el envío.

#### Scenario: Enlace con URL configurada
- **WHEN** `APP_BASE_URL` tiene un valor y se envía una alerta
- **THEN** el correo incluye el enlace a la bandeja con el identificador de la alerta

#### Scenario: URL no configurada
- **WHEN** `APP_BASE_URL` no tiene valor y se envía una alerta
- **THEN** el correo se envía sin el enlace

### Requirement: Envío al activar
Las notificaciones SHALL enviarse cuando la alerta pasa a `ACTIVE`, incluida la activación inmediata. Las alertas `DRAFT`, `SCHEDULED` y `ARCHIVED` SHALL NOT generar correos.

#### Scenario: Activación inmediata
- **WHEN** se crea una alerta con `activateImmediately=true`
- **THEN** se envían las notificaciones a sus destinatarios

#### Scenario: Alerta programada
- **WHEN** una alerta permanece en `SCHEDULED`
- **THEN** no se envían notificaciones hasta su activación

### Requirement: Registro de entregas
Por cada destinatario y alerta, MS03 SHALL registrar el resultado del envío como `SENT` o `FAILED`. El alcance SHALL limitarse al envío y SHALL NOT incluir acuses de entrega o lectura.

#### Scenario: Envío exitoso
- **WHEN** el proveedor de correo acepta el mensaje de un destinatario
- **THEN** MS03 registra una entrega `SENT` para ese destinatario y alerta

#### Scenario: Envío fallido
- **WHEN** el proveedor de correo rechaza o falla el mensaje de un destinatario
- **THEN** MS03 registra una entrega `FAILED` y continúa con los demás destinatarios

### Requirement: Resolución de destinatarios
MS03 SHALL resolver destinatarios `USER` y `ROLE`, SHALL eliminar duplicados por usuario y SHALL usar solo el canal `EMAIL`. Si no hay destinatarios configurados, SHALL usar los usuarios activos como respaldo.

#### Scenario: Destinatarios por usuario y rol
- **WHEN** una alerta tiene destinatarios `USER` y `ROLE`
- **THEN** cada usuario recibe como máximo un correo

#### Scenario: Sin destinatarios configurados
- **WHEN** una alerta no tiene destinatarios configurados
- **THEN** MS03 envía el correo a los usuarios activos
