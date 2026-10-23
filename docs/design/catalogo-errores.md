# Catalogo de errores - MS03 Alertas y Emergencias

Formato estandar de todas las respuestas de error:

```json
{
  "timestamp": "2026-10-22T14:05:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "The submitted data is invalid",
  "path": "/api/v1/alertas",
  "details": [
    {
      "field": "title",
      "message": "Title is required"
    }
  ]
}
```

- `details` siempre es una lista; queda vacia cuando el error no esta asociado a un campo.
- El `code` es estable para el frontend. El `message` puede incluir IDs o valores concretos.
- Los mensajes del backend se mantienen en ingles en el codigo actual; la columna "Mensaje UI" define el texto que muestra el frontend en espanol.
- Los errores de autenticacion y autorizacion tambien usan este formato, incluidos los producidos por Spring Security.

## Catalogo

| HTTP | code | Excepcion Java | Mensaje backend | Cuando ocurre | Mensaje UI |
|---|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `InvalidAlertRequestException` | `The submitted data is invalid` o el mensaje de validacion del campo | Cuerpo ausente o mal formado, campos obligatorios faltantes, enum invalido, UUID invalido, `simulationId` ausente para origen `SIMULATION`, o `simulationId` presente para origen `MANUAL` | Revisa los campos marcados. |
| 400 | `INVALID_REFERENCE` | `InvalidReferenceException` | `One or more references do not exist` | MS01 o MS02 no reconocen uno o mas usuarios, roles, edificios o zonas enviados | Uno o mas edificios, zonas o destinatarios no existen. |
| 400 | `INVALID_PAGINATION` | `InvalidPaginationException` | `The query parameters are invalid` | `page` negativo, `size` fuera de 1..100, o `startDate` posterior a `endDate` | Los filtros de busqueda no son validos. |
| 400 | `INVALID_ACTIVATION_DATE` | `InvalidActivationDateException` | `The activation date and time must be in the future` | `PATCH /programar` con `activationAt` pasada o igual al instante actual | La fecha de activacion debe ser futura. |
| 401 | `UNAUTHORIZED` | `AuthenticationException` | `Authentication is required or the token is invalid` | Falta el token, esta vencido o la firma no es valida | Tu sesion expiro. Inicia sesion nuevamente. |
| 403 | `FORBIDDEN` | `AccessDeniedException` | `You do not have permission to perform this operation` | El rol del token no permite la operacion | No tienes permisos para realizar esta accion. |
| 403 | `NOT_A_RECIPIENT` | `NotARecipientException` | `No es destinatario de la alerta {alertId}` | Un usuario generico consulta o confirma una alerta de la que no es destinatario | No tienes acceso a esta alerta. |
| 404 | `ALERT_NOT_FOUND` | `AlertNotFoundException` | `Alert not found with ID: {alertId}` | El UUID no corresponde a ninguna alerta | La alerta no existe o fue eliminada. |
| 404 | `RESOURCE_NOT_FOUND` | Manejado por Spring MVC | `The requested resource was not found` | Ruta inexistente o recurso no disponible | El recurso solicitado no existe. |
| 405 | `METHOD_NOT_ALLOWED` | Manejado por Spring MVC | `The HTTP method is not allowed` | Metodo HTTP no soportado para la ruta | Operacion no permitida. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Manejado por Spring MVC | `The content type is not supported` | `Content-Type` distinto de `application/json` en endpoints con cuerpo | Formato de contenido no soportado. |
| 409 | `INVALID_ALERT_STATE` | `InvalidAlertStateException` | `The alert is in status {status} and does not allow this operation` | Se intenta editar una alerta `ACTIVE`, `ENDED` o `ARCHIVED` | Esta alerta ya no se puede editar. |
| 409 | `INVALID_TRANSITION` | `InvalidTransitionException` | `Cannot transition the alert from {from} to {to}` | Transicion no permitida por la maquina de estados | No se puede realizar esa accion en el estado actual. |
| 409 | `ALERT_NOT_ACTIVE` | `AlertNotActiveException` | `La alerta {alertId} no esta activa` | Se confirma una alerta que no esta `ACTIVE` | Esta alerta ya no esta vigente. |
| 409 | `SIMULATION_MISMATCH` | `SimulationMismatchException` | `La alerta {alertId} no corresponde al simulacro {simulationId}` | `SERVICE_MS06` finaliza una alerta con un `simulationId` distinto | La alerta no corresponde a este simulacro. |
| 409 | `DUPLICATE_SIMULATION_ALERT` | `DuplicateSimulationAlertException`, `DataIntegrityViolationException` | `Ya existe una alerta viva para el simulacro {simulationId}: {existingAlertId}` | Se crea una alerta para un `simulationId` que ya tiene una alerta `SCHEDULED` o `ACTIVE` con datos de creacion distintos, o la restriccion unica detecta una carrera | Ya existe una alerta vigente para este simulacro con datos diferentes. |
| 409 | `CONCURRENT_MODIFICATION` | `OptimisticLockingFailureException` | `The alert was modified by another user` | Dos actualizaciones compiten sobre la misma version de la alerta | La alerta fue modificada por otra persona. Recarga los datos. |
| 503 | `EXTERNAL_SERVICE_UNAVAILABLE` | `ExternalServiceUnavailableException` | `Service {service} is unavailable. Please try again later.` | MS01, MS02 o MS06 no responden o devuelven un error de disponibilidad | El servicio no esta disponible. Intenta de nuevo. |
| 500 | `INTERNAL_ERROR` | `Exception` | `An unexpected error occurred` | Error no controlado; la traza solo se registra en el log | Ocurrio un error inesperado. |

## Idempotencia y conflictos de simulacro

`POST /api/v1/alertas` con origen `SIMULATION` aplica esta precedencia:

1. Si no existe una alerta `SCHEDULED` o `ACTIVE` para el `simulationId`, crea la alerta y responde `201`.
2. Si existe y los datos de creacion coinciden, responde `200` con la alerta existente.
3. Si existe y los datos de creacion difieren, responde `409 DUPLICATE_SIMULATION_ALERT`.

La comparacion de datos usa `type`, `title`, `instructions` (sin espacios al inicio o al final), `scope` (tipo e IDs como conjuntos), `recipients` (como conjunto) y `activationAt`. No compara `activateImmediately`.

La restriccion unica condicional de base de datos sigue siendo la ultima defensa ante concurrencia: si dos solicitudes intentan crear la misma alerta viva al mismo tiempo, una gana y la otra se traduce a `409 DUPLICATE_SIMULATION_ALERT`.

## Ejemplos

### Validacion

```json
{
  "timestamp": "2026-10-22T14:05:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "The submitted data is invalid",
  "path": "/api/v1/alertas",
  "details": [
    {
      "field": "origin.simulationId",
      "message": "simulationId is required when the origin is SIMULATION"
    }
  ]
}
```

### Referencias invalidas

```json
{
  "timestamp": "2026-10-22T14:05:00Z",
  "status": 400,
  "code": "INVALID_REFERENCE",
  "message": "One or more references do not exist",
  "path": "/api/v1/alertas",
  "details": [
    {
      "field": "scope.ids",
      "message": "The referenced ID does not exist: bld-999"
    }
  ]
}
```

### Transicion invalida

```json
{
  "timestamp": "2026-10-22T14:05:00Z",
  "status": 409,
  "code": "INVALID_TRANSITION",
  "message": "Cannot transition the alert from ENDED to ACTIVE",
  "path": "/api/v1/alertas/7f3c1a2e-9b4d-4c8a-9f1e-2a6d5c8b7e10/activar",
  "details": []
}
```

### Simulacro duplicado con datos distintos

```json
{
  "timestamp": "2026-10-22T14:05:00Z",
  "status": 409,
  "code": "DUPLICATE_SIMULATION_ALERT",
  "message": "Ya existe una alerta viva para el simulacro 3c1f8e2a-5d7b-4a90-8c3e-1f2a4b6c8d90: 7f3c1a2e-9b4d-4c8a-9f1e-2a6d5c8b7e10",
  "path": "/api/v1/alertas",
  "details": []
}
```

### Alerta no vigente al confirmar

```json
{
  "timestamp": "2026-10-22T14:05:00Z",
  "status": 409,
  "code": "ALERT_NOT_ACTIVE",
  "message": "La alerta 7f3c1a2e-9b4d-4c8a-9f1e-2a6d5c8b7e10 no esta activa",
  "path": "/api/v1/alertas/7f3c1a2e-9b4d-4c8a-9f1e-2a6d5c8b7e10/confirmaciones",
  "details": []
}
```

### Simulacro distinto al finalizar

```json
{
  "timestamp": "2026-10-22T14:05:00Z",
  "status": 409,
  "code": "SIMULATION_MISMATCH",
  "message": "La alerta 7f3c1a2e-9b4d-4c8a-9f1e-2a6d5c8b7e10 no corresponde al simulacro 3c1f8e2a-5d7b-4a90-8c3e-1f2a4b6c8d90",
  "path": "/api/v1/alertas/7f3c1a2e-9b4d-4c8a-9f1e-2a6d5c8b7e10/finalizar",
  "details": []
}
```

### Servicio externo no disponible

```json
{
  "timestamp": "2026-10-22T14:05:00Z",
  "status": 503,
  "code": "EXTERNAL_SERVICE_UNAVAILABLE",
  "message": "Service MS02 is unavailable. Please try again later.",
  "path": "/api/v1/alertas",
  "details": []
}
```
