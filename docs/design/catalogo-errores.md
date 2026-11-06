# Catalogo de errores - MS03 Alertas y Emergencias

Todas las respuestas de error tienen exactamente estos cinco campos:

```json
{
  "status": 400,
  "codigo": "VALIDACION",
  "mensaje": "Los datos enviados no son válidos",
  "errores": [
    {
      "campo": "title",
      "mensaje": "Title is required"
    }
  ],
  "correlation_id": "11111111-1111-4111-8111-111111111111"
}
```

- `errores` siempre es una lista; queda vacia cuando el error no esta asociado a un campo.
- `codigo` es estable para el frontend. `mensaje` puede incluir IDs o valores concretos.
- `correlation_id` es el UUID v4 recibido en `X-Correlation-ID` o uno generado por MS03. El mismo valor se devuelve en la cabecera `X-Correlation-ID`.
- Los errores de autenticacion y autorizacion tambien usan este formato, incluidos los producidos por Spring Security.
- No se agregan campos como `timestamp`, `path`, `code`, `message` ni `details`: el Gateway rechaza respuestas con un contrato distinto.

## Cabeceras de identidad

MS03 no valida JWT. Espera que el API Gateway inyecte:

| Cabecera | Obligatoria | Contenido |
|---|---|---|
| `X-User-Id` | Si | UUID del usuario |
| `X-User-Role` | Si | Uno o mas roles oficiales separados por coma |
| `X-Service-Name` | No | `ms03` o `ms06` |
| `X-Correlation-ID` | No | UUID v4 de correlacion |

Si faltan `X-User-Id` o `X-User-Role`, o no son validas, responde `401 NO_AUTENTICADO`.

## Catalogo

| HTTP | codigo | Excepcion Java | Mensaje por defecto | Cuando ocurre |
|---|---|---|---|---|
| 400 | `VALIDACION` | `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `InvalidAlertRequestException` | `Los datos enviados no son válidos` o el mensaje de validacion del campo | Cuerpo ausente o mal formado, campos obligatorios faltantes, enum invalido, UUID invalido, `simulationId` ausente para origen `SIMULATION` o presente para origen `MANUAL` |
| 400 | `REFERENCIA_INVALIDA` | `InvalidReferenceException` | `Una o más referencias no existen` | MS01 o el catalogo local no reconocen uno o mas usuarios, roles, edificios o zonas |
| 400 | `PAGINACION_INVALIDA` | `InvalidPaginationException` | `Los parámetros de consulta no son válidos` | `page` negativo, `size` fuera de 1..100, o `startDate` posterior a `endDate` |
| 400 | `FECHA_ACTIVACION_INVALIDA` | `InvalidActivationDateException` | `La fecha de activación debe ser futura` | `PATCH /programar` con `activationAt` pasada o igual al instante actual |
| 401 | `NO_AUTENTICADO` | `AuthenticationException`, `InvalidIdentityHeadersException` | `Se requiere autenticación o la identidad no es válida` | Faltan cabeceras de identidad o son invalidas |
| 403 | `PROHIBIDO` | `AccessDeniedException`, `ForbiddenOperationException` | `No tienes permiso para realizar esta operación` | El permiso no permite la operacion o un ORGANIZADOR intenta crear una alerta que no es de simulacro |
| 403 | `NO_ES_DESTINATARIO` | `NotARecipientException` | `No eres destinatario de esta alerta` | Un usuario generico consulta o confirma una alerta de la que no es destinatario |
| 404 | `ALERTA_NO_ENCONTRADA` | `AlertNotFoundException` | `La alerta no existe` | El UUID no corresponde a ninguna alerta |
| 404 | `NO_ENCONTRADO` | Manejado por Spring MVC | `El recurso solicitado no existe` | Ruta inexistente o recurso no disponible |
| 405 | `METODO_NO_PERMITIDO` | Manejado por Spring MVC | `El método HTTP no está permitido` | Metodo HTTP no soportado para la ruta |
| 415 | `TIPO_NO_SOPORTADO` | Manejado por Spring MVC | `El tipo de contenido no está soportado` | `Content-Type` distinto de `application/json` en endpoints con cuerpo |
| 409 | `ESTADO_ALERTA_INVALIDO` | `InvalidAlertStateException` | `El estado de la alerta no permite esta operación` | Se intenta editar una alerta `ACTIVE`, `ENDED` o `ARCHIVED` |
| 409 | `TRANSICION_INVALIDA` | `InvalidTransitionException` | `La transición de estado solicitada no es válida` | Transicion no permitida por la maquina de estados |
| 409 | `ALERTA_NO_ACTIVA` | `AlertNotActiveException` | `La alerta no está activa` | Se confirma una alerta que no esta `ACTIVE` |
| 409 | `CONFLICTO_SIMULACION` | `SimulationMismatchException` | `El simulacro no corresponde a la alerta` | MS06 u ORGANIZADOR finaliza una alerta con un `simulationId` distinto |
| 409 | `SIMULACION_DUPLICADA` | `DuplicateSimulationAlertException`, `DataIntegrityViolationException` | `Ya existe una alerta activa o programada para este simulacro` | Se crea una alerta para un `simulationId` con datos distintos, o la restriccion unica detecta una carrera |
| 409 | `MODIFICACION_CONCURRENTE` | `OptimisticLockingFailureException` | `La alerta fue modificada por otro usuario` | Dos actualizaciones compiten sobre la misma version de la alerta |
| 503 | `SERVICIO_EXTERNO_NO_DISPONIBLE` | `ExternalServiceUnavailableException` | `Un servicio externo no está disponible. Intenta de nuevo más tarde` | MS01 o MS02 no responden o devuelven un error de disponibilidad |
| 500 | `ERROR_INTERNO` | `Exception` | `Ocurrió un error inesperado` | Error no controlado; la traza solo se registra en el log |

## Idempotencia y conflictos de simulacro

`POST /api/v1/alertas` con origen `SIMULATION` aplica esta precedencia:

1. Si no existe una alerta `SCHEDULED` o `ACTIVE` para el `simulationId`, crea la alerta y responde `201`.
2. Si existe y los datos de creacion coinciden, responde `200` con la alerta existente.
3. Si existe y los datos de creacion difieren, responde `409 SIMULACION_DUPLICADA`.

La comparacion de datos usa `type`, `title`, `instructions` (sin espacios al inicio o al final), `scope` (tipo e IDs como conjuntos), `recipients` (como conjunto) y `activationAt`. No compara `activateImmediately`.

La restriccion unica condicional de base de datos sigue siendo la ultima defensa ante concurrencia: si dos solicitudes intentan crear la misma alerta viva al mismo tiempo, una gana y la otra se traduce a `409 SIMULACION_DUPLICADA`.

## Ejemplos

### Validacion

```json
{
  "status": 400,
  "codigo": "VALIDACION",
  "mensaje": "Los datos enviados no son válidos",
  "errores": [
    {
      "campo": "origin.simulationId",
      "mensaje": "simulationId es obligatorio cuando el origen es SIMULATION"
    }
  ],
  "correlation_id": "11111111-1111-4111-8111-111111111111"
}
```

### Referencias invalidas

```json
{
  "status": 400,
  "codigo": "REFERENCIA_INVALIDA",
  "mensaje": "Una o más referencias no existen",
  "errores": [
    {
      "campo": "alcance.ids",
      "mensaje": "La referencia no existe: BLD-999"
    }
  ],
  "correlation_id": "11111111-1111-4111-8111-111111111111"
}
```

### Transicion invalida

```json
{
  "status": 409,
  "codigo": "TRANSICION_INVALIDA",
  "mensaje": "No se puede cambiar la alerta de ENDED a ACTIVE",
  "errores": [],
  "correlation_id": "11111111-1111-4111-8111-111111111111"
}
```

### Simulacro duplicado con datos distintos

```json
{
  "status": 409,
  "codigo": "SIMULACION_DUPLICADA",
  "mensaje": "Ya existe una alerta viva para el simulacro 3c1f8e2a-5d7b-4a90-8c3e-1f2a4b6c8d90: 7f3c1a2e-9b4d-4c8a-9f1e-2a6d5c8b7e10",
  "errores": [],
  "correlation_id": "11111111-1111-4111-8111-111111111111"
}
```

### Alerta no vigente al confirmar

```json
{
  "status": 409,
  "codigo": "ALERTA_NO_ACTIVA",
  "mensaje": "La alerta no está activa",
  "errores": [],
  "correlation_id": "11111111-1111-4111-8111-111111111111"
}
```

### Simulacro distinto al finalizar

```json
{
  "status": 409,
  "codigo": "CONFLICTO_SIMULACION",
  "mensaje": "La alerta 7f3c1a2e-9b4d-4c8a-9f1e-2a6d5c8b7e10 no corresponde al simulacro 3c1f8e2a-5d7b-4a90-8c3e-1f2a4b6c8d90",
  "errores": [],
  "correlation_id": "11111111-1111-4111-8111-111111111111"
}
```

### Servicio externo no disponible

```json
{
  "status": 503,
  "codigo": "SERVICIO_EXTERNO_NO_DISPONIBLE",
  "mensaje": "El servicio MS01 no está disponible. Intenta de nuevo más tarde",
  "errores": [],
  "correlation_id": "11111111-1111-4111-8111-111111111111"
}
```
