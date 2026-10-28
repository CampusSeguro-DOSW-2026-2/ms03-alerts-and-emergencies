# Propuesta: confirmación de recepción por destinatarios

**Estado:** propuesta, pendiente de decisión del squad. No hay código implementado.
**Pantalla afectada:** "Simulacro activo" (`#/simulacro`), botón *Confirmar Recepción de Alerta*. Hoy guarda la confirmación solo en el navegador.

## Qué ya funciona (comprobado el 2026-10-25 contra `develop`)

Con un JWT local firmado a mano (`sub: usr-002`, `roles: ["STUDENT"]`) y dos alertas activas, una dirigida al rol `STUDENT` y otra a `TEACHER`:

| Llamada | Respuesta |
|---|---|
| `GET /api/v1/alertas/{id}` (alerta a estudiantes) | 200 |
| `POST /api/v1/alertas/{id}/confirmaciones` (primera vez) | 201 |
| `POST /api/v1/alertas/{id}/confirmaciones` (reintento) | 200, idempotente |
| `GET` / `POST` sobre la alerta a profesores | 403 `NOT_A_RECIPIENT` |
| `GET /api/v1/alertas/{id}/auditoria` | 1 evento `CONFIRM` |
| `GET /api/v1/alertas?status=ACTIVE` | **403**, solo COORDINATOR/AUDITOR |

`ConfirmationService`, `AlertAccessPolicy` y la auditoría ya cubren la regla de negocio. No hace falta un rol `RECIPIENT`: basta con el rol de comunidad (`STUDENT`, `TEACHER`…) que emitirá MS01.

## Qué falta

1. **Tokens de destinatario en local.** `GET /local/tokens` solo emite COORDINATOR, AUDITOR, SERVICE_MS06 y un USER sin roles. Ninguno de ellos es destinatario de una alerta dirigida a un rol.
2. **Descubrir la alerta activa propia.** Un destinatario no puede listar alertas (403), así que el frontend no sabe qué alerta mostrar ni confirmar.
3. **Saber si ya confirmó.** Hoy solo se deduce reintentando el `POST`, que devuelve 200 si ya existía.

## Cambios propuestos

### A. Perfil `local`: tokens de destinatario (solo desarrollo)
En `LocalTokenController`, añadir un token por cada rol de comunidad del `catalog-stub.json`, usando como `sub` el primer usuario con ese rol:

```json
{ "RECIPIENT_STUDENT": "<jwt sub=usr-001 roles=[STUDENT]>", "RECIPIENT_TEACHER": "…", "RECIPIENT_STAFF": "…", "RECIPIENT_SECURITY": "…" }
```
- Sigue protegido por `@Profile("local")`; no existe en otros entornos.
- Prueba: `LocalTokenControllerTest`, que comprueba claves, `sub` y `roles`.

### B. Nuevo endpoint: alertas activas del usuario
`GET /api/v1/alertas/activas` · `isAuthenticated()`

- Devuelve las alertas `ACTIVE` en las que el actor es destinatario: por rol, por usuario o por entrega registrada, con la misma regla que `AlertAccessPolicy`.
- Respuesta: lista de `{ id, title, instructions, scope, activatedAt, confirmedAt }`, donde `confirmedAt` es `null` si aún no confirmó.
- Capas: controller → `AlertService.findActiveForActor(actor)` → repositorio con *specification* sobre `alert_recipients` y `notification_deliveries`.
- Pruebas:
  - unitarias del servicio: rol que coincide, rol que no coincide, `USER` directo y `confirmedAt`;
  - de integración: 200 con alerta propia, lista vacía sin alertas y 401 sin token.
- Resuelve también el pendiente de "lectura de la alerta activa para usuarios sin rol privilegiado". Además, `title` puede servir como nombre del simulacro en el encabezado.

### C. Frontend (`campus-seguro`), tras A y B
- `services/http.ts`: admitir un segundo token (destinatario) además del de coordinador, solo en desarrollo.
- `ms03Api.getActiveSimulation` usa `GET /activas`; `isAlertConfirmed` lee `confirmedAt`; `confirmAlert` llama a `POST /{id}/confirmaciones`.
- La pantalla no cambia visualmente.

## Fuera de alcance
- El JWT real lo emite MS01. Los nombres de rol (`STUDENT`, `TEACHER`…) deben acordarse con MS01 antes de pasar a producción.
- No se modifica `ConfirmationService` ni el modelo de datos.

## Decisión que se necesita
- ¿Se aprueban A y B?
- ¿El nombre `/activas` les sirve, o prefieren `/mias?status=ACTIVE`?
