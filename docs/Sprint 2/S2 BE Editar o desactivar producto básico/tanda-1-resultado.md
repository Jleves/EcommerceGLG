# S2 BE — Editar o desactivar producto básico: resultado de la tanda 1

**Fecha:** 02/10/2026  
**Estado:** COMPLETA

## Alcance y verificación previa

El producto básico ya tenía entidad, tabla V6, repositorio, servicio de alta y pruebas de persistencia. `ProductServiceImpl.create(...)` bloquea la categoría y exige que esté activa; la edición acordada permite una categoría inactiva. `ProductController` solo publica el alta. El árbol de trabajo contenía el plan de esta tarjeta sin registrar y no tenía cambios productivos previos.

**Alcance implementado:** contrato de entrada `UpdateProductRequest`, `ProductService.update(...)`, bloqueo de la fila de producto en `ProductRepository.lockById(...)`, persistencia de los cinco campos básicos y pruebas de validación, servicio e integración. **Fuera de alcance:** desactivar/reactivar (tanda 2), rutas HTTP y seguridad de la edición (tanda 3), características, variantes y frontend. No fue necesario ampliar el análisis más allá de las clases de producto, la referencia de categoría, los tests relacionados y el entorno Maven utilizado en la tarjeta anterior.

**Archivos previstos:** DTO nuevo, `ProductService`, `ProductServiceImpl`, `ProductRepository`, `ProductServiceTest`, `ProductServiceIntegrationTest` y el plan vivo. Se añadió `UpdateProductRequestValidationTest` para comprobar las anotaciones antes de conectar el DTO al controller. No hubo migración ni refactor productivo adicional.

## Cambios realizados

- `UpdateProductRequest` declara los cinco campos básicos y las mismas restricciones que `CreateProductRequest`; `activo` y `destacado` no son campos editables.
- `ProductServiceImpl.update(...)` busca el producto con bloqueo pesimista, comprueba que la categoría exista sin exigir que esté activa, recorta el nombre, sustituye datos básicos y devuelve `ProductResponse` dentro de una transacción. Si falta el producto o la categoría, lanza `ResourceNotFoundException` antes de guardar.
- `ProductRepository.lockById(...)` consulta `Product` por ID con `PESSIMISTIC_WRITE`. El alta conserva su bloqueo de categoría y su rechazo de categorías inactivas.
- Las pruebas cubren edición con categoría activa e inactiva, producto inactivo, descripción `null`, nombre repetido, precio exacto, conservación de estados y ausencia de cambios ante una categoría inexistente.

**Diferencias respecto del plan:** se agregó una prueba de validación directa del DTO; el test de regresión `productSurvivesDeactivationAndReactivationControlsNewCreates` necesitó independizar su conteo de las filas creadas por otros tests del mismo contexto H2. No cambió el contrato ni el código productivo por ese incidente.

## Tests ejecutados

Se ejecutó en `maven:3.9-eclipse-temurin-21`, con el repositorio montado en `/workspace`:

```text
mvn -B -q '-Dtest=ProductServiceTest,UpdateProductRequestValidationTest,ProductServiceIntegrationTest,ProductPersistenceIntegrationTest,ProductControllerTest' test
```

Los reportes Surefire de la ejecución final registran **18 pruebas, 0 fallos, 0 errores y 0 omisiones**.

| Grupo | Clases y cantidad | Qué demuestra | Resultado |
| --- | --- | --- | --- |
| Unitarios de servicio | `ProductServiceTest` (8) | Alta anterior, edición de los cinco datos, categoría inactiva, producto inactivo, estados conservados, referencias inexistentes y ausencia de unicidad de nombre. | PASS |
| Validación del DTO | `UpdateProductRequestValidationTest` (1) | Campos obligatorios y límites de nombre, categoría, descripción, precio y disponibilidad equivalentes al alta. | PASS |
| Integración H2/Flyway | `ProductServiceIntegrationTest` (3) | Edición persistida y recargada con categoría activa e inactiva, precio exacto, auditoría y estados conservados; categoría inexistente no altera la fila; regla original del alta sigue vigente. | PASS |
| Regresión de persistencia | `ProductPersistenceIntegrationTest` (1) | La migración V6 y el mapeo JPA siguen guardando y recargando producto, FK y precio. | PASS |
| Regresión HTTP del alta | `ProductControllerTest` (5) | El alta mantiene roles, CSRF, validaciones, respuestas y rechazo de categoría inactiva. No prueba aún la ruta de edición. | PASS |

**Tests planificados frente a ejecutados:** se ejecutaron los grupos de servicio, integración H2 y regresión previstos para la tanda. La prueba directa del DTO es adicional. No se omitió una prueba requerida de esta tanda.

## Incidentes

### INC-T1-001 — Conteo compartido en la prueba de integración

**Contexto:** primera ejecución conjunta de los tests nuevos y de regresión con el perfil H2.  
**Test/comportamiento afectado:** `ProductServiceIntegrationTest.productSurvivesDeactivationAndReactivationControlsNewCreates`; verifica que la desactivación de una categoría conserva el producto y controla nuevas altas.  
**Esperado:** después de la primera alta del escenario, el conteo total debía aumentar en uno; después de reactivar la categoría y crear otro, en dos.  
**Obtenido:** la prueba esperaba un conteo absoluto de `1`, pero encontró `3` porque las nuevas pruebas de integración habían persistido otras dos filas en el contexto compartido. Primera ejecución: 18 pruebas, 1 fallo, 0 errores, 0 omisiones.  
**Clasificación:** defecto del test (aislamiento de datos).  
**Causa:** confirmada; la expectativa asumía base vacía y no medía el punto de partida de su propio escenario.  
**Resolución:** registrar `initialProductCount` al comenzar el test y comparar contra `+1` y `+2`; conservar la expectativa de negocio y el código productivo.  
**Código productivo modificado:** no por el incidente.  
**Tests modificados:** sí, `ProductServiceIntegrationTest`.  
**Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS en la selección completa; 18/18.  
**Riesgo o aprendizaje derivado:** los tests que comparten contexto H2 no deben asumir conteos absolutos sin aislamiento o línea de base propia.

## Tests faltantes y riesgos residuales

**Tests faltantes de la tanda 1:** ninguno. **No probado todavía:** contrato HTTP de `PUT`, aplicación de `@Valid`, sesión, CSRF y roles de la edición; pertenece a la tanda 3. La serialización de mutaciones concurrentes del mismo producto en MySQL se verificará en la tanda 2, al reutilizar `lockById` para cambios de estado.

**Riesgo residual:** el DTO está validado directamente, pero aún no existe una ruta que lo aplique. El bloqueo serializa una transacción por producto; no detecta formularios desactualizados, por lo que una edición posterior puede sobrescribir datos de otra edición válida. Este comportamiento ya figuraba como riesgo en el plan.

**Decisiones pendientes descubiertas:** ninguna. No se introdujo una regla nueva para categorías inactivas ni unicidad de nombres.

## Cierre de la tanda

- [x] Edición básica persistida en producto inactivo y con categoría inactiva.
- [x] `activo` y `destacado` conservados; errores de referencia sin escritura parcial.
- [x] Tests de servicio, DTO, integración H2 y regresión del alta ejecutados.
- [x] Incidente de test clasificado, corregido y verificado en la selección completa.
- [x] Tests faltantes y riesgos residuales declarados; plan vivo actualizado.

**Archivos de la tanda:** `UpdateProductRequest.java`, `ProductService.java`, `ProductServiceImpl.java`, `ProductRepository.java`, `ProductServiceTest.java`, `ProductServiceIntegrationTest.java`, `UpdateProductRequestValidationTest.java`, el plan vivo y este resultado. No se creó commit.

**Revisión humana antes de la tanda 2:** comprobar el contrato del DTO y que la categoría inactiva sea admisible en edición mientras el alta la sigue rechazando; revisar el alcance del bloqueo pesimista y el riesgo de formularios obsoletos. La implementación se detiene aquí.
