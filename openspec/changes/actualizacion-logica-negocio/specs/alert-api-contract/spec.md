# Spec Delta

## Purpose

Define el contrato HTTP observable de MS03: representación de tipos y estados, endpoints, filtros, errores, paginación, cabeceras y paridad entre la documentación estática y el comportamiento en ejecución.

## ADDED Requirements

### Requirement: Representación de tipos
Las solicitudes de creación y edición y las respuestas SHALL usar `types` como arreglo de uno o más tipos. El campo `type` singular SHALL NOT formar parte del contrato.

#### Scenario: Crear con types
- **WHEN** se envía `POST /api/v1/alertas` con `types: [FIRE, EARTHQUAKE]`
- **THEN** la respuesta `201` devuelve `types` con ambos valores

#### Scenario: Enviar type singular
- **WHEN** se envía `POST /api/v1/alertas` con `type: FIRE`
- **THEN** MS03 responde `400` con código `VALIDACION`

#### Scenario: Respuesta sin type singular
- **WHEN** se consulta una alerta
- **THEN** la respuesta incluye `types` y no incluye `type`

### Requirement: Contrato de estados
El contrato SHALL exponer únicamente `DRAFT`, `SCHEDULED`, `ACTIVE` y `ARCHIVED`. El estado `ENDED` SHALL NOT aparecer en solicitudes, respuestas, filtros ni documentación.

#### Scenario: Filtro por estado válido
- **WHEN** se consulta `GET /api/v1/alertas?status=ACTIVE`
- **THEN** MS03 devuelve solo alertas `ACTIVE`

#### Scenario: Filtro por ENDED
- **WHEN** se consulta `GET /api/v1/alertas?status=ENDED`
- **THEN** MS03 responde `400` con código `VALIDACION`

### Requirement: Endpoints de ciclo de vida
El contrato SHALL incluir `POST /api/v1/alertas`, `PUT /api/v1/alertas/{id}`, `GET /api/v1/alertas`, `GET /api/v1/alertas/{id}`, `PATCH /api/v1/alertas/{id}/programar`, `PATCH /api/v1/alertas/{id}/activar`, `PATCH /api/v1/alertas/{id}/archivar`, `GET /api/v1/alertas/mias` y `POST /api/v1/alertas/{id}/confirmaciones`.

#### Scenario: Archivado disponible
- **WHEN** se llama a `PATCH /api/v1/alertas/{id}/archivar` sobre una alerta no archivada
- **THEN** MS03 responde `200` con la alerta `ARCHIVED`

#### Scenario: Finalización no disponible
- **WHEN** se llama a `PATCH /api/v1/alertas/{id}/finalizar`
- **THEN** MS03 responde `404`

### Requirement: Activación en el contrato
La creación SHALL aceptar `activationAt` para programar y `activateImmediately` para activar de inmediato. La operación de activación manual SHALL NOT aceptar `activationAt`.

#### Scenario: Programar desde creación
- **WHEN** se envía `POST /api/v1/alertas` con una `activationAt` futura
- **THEN** la respuesta `201` devuelve la alerta `SCHEDULED`

#### Scenario: Activar sin fecha
- **WHEN** se envía `PATCH /api/v1/alertas/{id}/activar` sin `activationAt`
- **THEN** MS03 responde `200` con la alerta `ACTIVE`

### Requirement: Filtros del listado
El listado SHALL aceptar `type` repetido con semántica OR, `origin`, `scope`, `status`, `activeAt` y paginación. El filtro `type` SHALL coincidir si la alerta contiene al menos uno de los valores enviados.

#### Scenario: Filtro por dos tipos
- **WHEN** se consulta `GET /api/v1/alertas?type=FIRE&type=EARTHQUAKE`
- **THEN** MS03 devuelve las alertas que contengan al menos uno de los dos tipos

#### Scenario: Filtro por origen
- **WHEN** se consulta `GET /api/v1/alertas?origin=SIMULATION`
- **THEN** MS03 devuelve solo alertas con origen `SIMULATION`

### Requirement: Formato de errores
Los errores SHALL usar el sobre de cinco campos: `status`, `codigo`, `mensaje`, `errores` y `correlation_id`. Los códigos de negocio SHALL viajar en `codigo`.

#### Scenario: Error de validación
- **WHEN** una solicitud incumple una regla de negocio
- **THEN** la respuesta incluye los cinco campos y el código de negocio en `codigo`

#### Scenario: Error de recurso inexistente
- **WHEN** se consulta un identificador inexistente
- **THEN** la respuesta `404` incluye `codigo: ALERTA_NO_ENCONTRADA`

### Requirement: Paginación
Las respuestas paginadas SHALL usar `contenido`, `page`, `size`, `total_elementos` y `total_paginas`. `page` SHALL iniciar en cero, `size` SHALL valer 20 por defecto y SHALL tener un máximo de 100.

#### Scenario: Página por defecto
- **WHEN** se consulta un listado sin `page` ni `size`
- **THEN** la respuesta devuelve la página cero con veinte elementos como máximo

#### Scenario: Página fuera de rango
- **WHEN** se solicita `size=101`
- **THEN** MS03 responde `400` con código `PAGINACION_INVALIDA`

### Requirement: Cabeceras de contexto
Las solicitudes SHALL requerir `X-User-Id` y `X-User-Role`. `X-Service-Name` y `X-Correlation-ID` SHALL ser opcionales. El contrato SHALL NOT usar autenticación Bearer.

#### Scenario: Solicitud sin identidad
- **WHEN** una solicitud no incluye `X-User-Id` o `X-User-Role`
- **THEN** MS03 responde `401` con código `NO_AUTENTICADO`

#### Scenario: Solicitud con metadatos opcionales
- **WHEN** una solicitud incluye `X-Service-Name` y `X-Correlation-ID`
- **THEN** MS03 procesa la solicitud y conserva la correlación en la respuesta y la auditoría

### Requirement: Paridad del contrato
El contrato estático de `docs/api/openapi.yaml` SHALL coincidir con el comportamiento en ejecución para rutas, parámetros, estados, códigos de error y campos obligatorios.

#### Scenario: Verificación de contrato
- **WHEN** se ejecuta la prueba de contrato estático y runtime
- **THEN** no se reportan diferencias entre OpenAPI y el API
