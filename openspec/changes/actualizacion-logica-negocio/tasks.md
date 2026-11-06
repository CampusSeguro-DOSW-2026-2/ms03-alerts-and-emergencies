# Tasks

## 1. Esquema y migración

- [x] 1.1 Crear `database/005_business_logic_update.sql` con `alert_types`, backfill desde `alerts.type`, conversión `ENDED -> ARCHIVED`, retiro de `alerts.type`/`ended_at`, constraints nuevas, checks de auditoría legados e índices de bandeja; verificar que el script se aplica sobre PostgreSQL 17 limpio sin errores.
- [x] 1.2 Agregar una prueba de migración que aplique `001` a `005` sobre datos con `ENDED`, `SIMULATION` y `MANUAL`, y verificar que todas las alertas tienen al menos un tipo, no quedan `ENDED`, `archived_at` está completo y las constraints son válidas.
- [x] 1.3 Documentar en `README.md` el orden de aplicación de `005`, la ventana de mantenimiento y el respaldo previo; verificar que las instrucciones escritas se ejecutan tal cual en local.

## 2. Dominio y persistencia

- [x] 2.1 Actualizar `AlertType` a `EARTHQUAKE, FIRE, OTHER, OPERATIVA`, retirar `SIMULATION`, y actualizar `AlertStatus` a cuatro estados sin `ENDED`; verificar con pruebas unitarias de enums y compilación.
- [x] 2.2 Agregar `Set<AlertType> types` a `AlertEntity` con `@ElementCollection`, tabla `alert_types`, `@BatchSize` y orden canónico, eliminando `type` y `endedAt`; verificar la persistencia de varios tipos con una prueba de repositorio.
- [x] 2.3 Crear los enums de auditoría `AuditAction` y `AuditStatus` con los valores legados `END`/`ENDED`, y actualizar `AuditEventEntity` para mapearlos; verificar que una fila histórica se lee sin excepción.
- [x] 2.4 Actualizar `AlertStateMachine` con las transiciones `DRAFT -> SCHEDULED/ACTIVE/ARCHIVED`, `SCHEDULED -> ACTIVE/ARCHIVED` y `ACTIVE -> ARCHIVED`; verificar con pruebas unitarias que `ARCHIVED` no tiene salidas y que `ENDED` no existe.
- [x] 2.5 Actualizar `AlertMapper` para ordenar `types` y construir respuestas sin `endedAt`, y `AlertSpecifications` para filtrar por `type` repetido con `distinct`; verificar con pruebas de mapper y especificación que no hay duplicados.

## 3. DTOs y validación

- [x] 3.1 Reemplazar `type` por `List<AlertType> types` en `AlertCreateRequest`, `AlertUpdateRequest`, `AlertResponse`, `AlertSummaryResponse` y `AlertFilter`, eliminar `AlertEndRequest`, `endedAt` y los alias `eventId`; verificar compilación y pruebas de serialización/deserialización.
- [x] 3.2 Implementar en `AlertServiceImpl` la validación de al menos un tipo, sin duplicados, catálogo válido, `OPERATIVA` exclusiva y reglas de `origin`/`simulationId`; verificar cada regla con pruebas unitarias y respuestas `400 VALIDACION`.
- [x] 3.3 Actualizar la creación para persistir `types`, comparar la idempotencia de simulacros por conjunto de tipos y mantener el orden canónico en la respuesta; verificar con pruebas de creación e idempotencia.
- [x] 3.4 Actualizar `AlertServiceImpl.update` para reemplazar el conjunto de `types` cuando venga presente y validar las mismas reglas; verificar con pruebas de edición y de rechazo en `ACTIVE`/`ARCHIVED`.
- [x] 3.5 Agregar pruebas de controlador para crear y editar con varios tipos, `type` singular ausente y errores de catálogo; verificar con `mvn test`.

## 4. Ciclo de vida, permisos y MS06

- [x] 4.1 Actualizar `AlertLifecycleService` y `AlertLifecycleServiceImpl`: eliminar `end`, hacer `archive` idempotente, recibir `RequesterContext` y aplicar la regla `manage_drill` solo a `SIMULATION`; verificar con pruebas unitarias de cada transición y del archivado repetido.
- [x] 4.2 Actualizar `AlertController`: eliminar `PATCH /{id}/finalizar`, usar `CAN_MANAGE_ALERTS` en editar/programar/activar/archivar y mantener `CAN_READ_HISTORY`/`CAN_VIEW`; verificar con pruebas MockMvc de rutas, permisos y códigos.
- [x] 4.3 Eliminar `RequesterContext.isServiceMs06()`, hacer `auditActor()` genérico para `{servicio}:{actor}` y quitar el trato especial de MS06 en servicios y auditoría; verificar con pruebas de seguridad y de `actorType`.
- [x] 4.4 Eliminar `SimulationMismatchException` y cualquier referencia a `eventId` en el código de producción; verificar con `rg` que no quedan referencias y con la suite de pruebas.

## 5. Bandeja y confirmación

- [x] 5.1 Crear `InboxStatus`, `InboxAlertResponse` e `InboxService`, y agregar la consulta de bandeja en `AlertRepository` con subconsultas `exists` sobre `alert_recipients` y `notification_deliveries`; verificar con pruebas unitarias de filtros y orden.
- [x] 5.2 Cargar los tipos con `@BatchSize` y las confirmaciones de la página con una sola consulta `findByUserIdAndAlertIdIn`; verificar que el mapeo de `confirmedAt` es correcto y que no hay N+1 en la prueba de integración.
- [x] 5.3 Agregar `GET /api/v1/alertas/mias` con permiso `receive`, `status` por defecto `ACTIVE`, `ARCHIVED`/`ALL` y paginación; verificar con MockMvc los estados, el orden, la página y el `403` sin permiso.
- [x] 5.4 Ajustar la confirmación para alertas `ACTIVE` y verificar `201`/`200`/`409 ALERTA_NO_ACTIVA`/`403 NO_ES_DESTINATARIO`/`404 ALERTA_NO_ENCONTRADA`, incluida la concurrencia con un único evento `CONFIRM`.
- [x] 5.5 Verificar que una alerta `DRAFT`/`SCHEDULED` nunca aparece en la bandeja y que una alerta archivada desde `DRAFT` aparece con `activatedAt` nulo al final; cubrir con pruebas de integración.

## 6. Correo

- [x] 6.1 Actualizar `EmailTemplateBuilder` para renderizar varios tipos en orden canónico, aplicar el mensaje base solo a emergencias, omitirlo en `OPERATIVA` y usar el asunto `Alerta CampusSeguro: {tipos}` o `[Operativa] {título}`; verificar con pruebas unitarias.
- [x] 6.2 Cambiar el enlace a `{APP_BASE_URL}/bandeja?alerta={id}` y tolerar una URL vacía sin fallar; verificar con pruebas de HTML/texto con y sin `APP_BASE_URL`.
- [x] 6.3 Verificar que el correo se envía solo al pasar a `ACTIVE`, que no se envía en `DRAFT`/`SCHEDULED`/`ARCHIVED` y que se registran `SENT`/`FAILED` por destinatario; cubrir con pruebas del listener y de `NotificationService`.

## 7. Auditoría y legado

- [x] 7.1 Actualizar `AuditService` para traducir `AlertStatus` a `AuditStatus`, emitir solo `CREATE/UPDATE/SCHEDULE/ACTIVATE/ARCHIVE/CONFIRM` y no generar `END`; verificar con pruebas unitarias de eventos.
- [x] 7.2 Actualizar `AuditQueryServiceImpl` para clasificar `SYSTEM` por actor del planificador/notificador, `SERVICE` por actor con `:` y `USER` en el resto; verificar con pruebas de los tres casos.
- [x] 7.3 Verificar que el archivado idempotente no duplica el evento `ARCHIVE` y que las filas legadas `END`/`ENDED` se leen y se devuelven sin modificarse; cubrir con pruebas de servicio e integración.
- [x] 7.4 Revisar que la consulta de auditoría exige `read_history` y que `X-Correlation-ID` se conserva o se genera; verificar con pruebas MockMvc.

## 8. Contrato, documentación y Postman

- [x] 8.1 Actualizar `docs/api/openapi.yaml` con `types`, los cuatro estados, ausencia de `/finalizar`, `GET /mias`, errores de cinco campos y paginación en español; verificar que el documento es OpenAPI 3.1 válido y que las rutas coinciden con el controlador.
- [x] 8.2 Actualizar `OpenApiConfig` para describir `X-Service-Name` como metadato de auditoría sin mención especial a `ms06`; verificar con `OpenApiConfigTest`.
- [x] 8.3 Actualizar `README.md` con endpoints, permisos, roles, estados, bandeja, correo y migración; verificar que cada ejemplo y ruta documentada existe en el código.
- [x] 8.4 Actualizar `docs/postman/ms03-alertas.postman_collection.json` para crear con `types`, filtrar por `type` repetido, usar la bandeja y eliminar `/finalizar`; verificar que la colección es JSON válido y que se ejecuta contra el entorno local.
- [x] 8.5 Agregar una prueba de contrato estático/runtime que compare OpenAPI con rutas, parámetros, estados y campos; verificar con `mvn test`.

## 9. Integración y verificación final

- [x] 9.1 Ejecutar `mvn clean verify` y verificar que pasan unitarias, integración, contrato y el umbral JaCoCo de 80%.
- [x] 9.2 Ejecutar la migración `005` sobre una base limpia y sobre el fixture legado, y verificar conteos de `alerts` vs. `alert_types`, ausencia de `ENDED`/`ended_at` y validez de constraints e índices.
- [x] 9.3 Ejecutar el flujo HTTP completo con la colección Postman: crear alerta multi-tipo, filtrar, programar, activar, consultar bandeja, confirmar, archivar y comprobar el correo; verificar los códigos y el contenido esperado.
- [x] 9.4 Buscar remanentes prohibidos con `rg` (`AlertStatus.ENDED`, `endedAt`, `finalizar`, `isServiceMs06`, `eventId`, `alerts.type`) y verificar que solo aparecen en migración, auditoría legada o specs, nunca en el código nuevo.

## Workflow follow-up

- Archivar el cambio después de que la revisión del proyecto y `mvn clean verify` estén aprobados.
- Verificar el resultado archivado y que las specs principales reflejen las capacidades nuevas.
