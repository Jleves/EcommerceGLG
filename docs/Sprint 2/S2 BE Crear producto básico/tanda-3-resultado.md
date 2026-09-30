# S2 BE — Crear producto básico: resultado de la tanda 3

**Fecha:** 2026-09-30  
**Estado:** COMPLETA

## Alcance y verificación previa

La tanda 2 dejó implementados `CreateProductRequest`, `ProductService.create`, el bloqueo de categoría y `ProductCategoryInactiveException`; el árbol de trabajo estaba limpio. La ruta de producto aún no existía y `SecurityConfig` carecía de matcher para ella. `CategoryController` y `GlobalExceptionHandler` dieron la convención directa para el controlador, autorización y errores. No se requirió ampliar el análisis fuera de estas dependencias y sus tests relacionados.

Se implementó el alta HTTP privada, su validación, la traducción de categoría inactiva a 409 y las pruebas HTTP. Quedan fuera de alcance edición, listado o ficha pública, características, variantes, imágenes y promociones de tarjetas posteriores.

**Archivos previstos:** `ProductController.java` para la ruta; `SecurityConfig.java` para el matcher; `GlobalExceptionHandler.java` para el 409; `ProductControllerTest.java` para contrato, seguridad y conservación del producto; este plan y el presente resultado para registrar la ejecución. No se necesitó refactor adicional.

## Cambios realizados

- `POST /api/catalog/products` valida `CreateProductRequest` y responde 201 con `ProductResponse`.
- La ruta y sus subrutas requieren uno de los tres roles admitidos; se conserva CSRF global y `@PreAuthorize` al nivel del controlador.
- `ProductCategoryInactiveException` devuelve `409 RESOURCE_CONFLICT` mediante `ApiError`.
- Las pruebas ejercitan la ruta con sesiones reales, MockMvc, H2, Flyway y repositorios, incluyendo desactivación y reactivación de categoría por HTTP.

**Diferencias respecto del plan:** ninguna funcional. Se usó el contenedor `maven:3.9-eclipse-temurin-21` porque no hay JDK local.

## Tests ejecutados

Comando: `mvn -B -q '-Dtest=ProductControllerTest,CategoryControllerTest' test` dentro del contenedor Maven. Los reportes Surefire finales indican **12 pruebas, 0 fallos, 0 errores y 0 omisiones**.

| Categoría | Tests | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| HTTP, seguridad y contrato | `ProductControllerTest` (5) | Los tres roles crean con sesión y CSRF; 201 incluye ID, categoría, datos y estados fijados por servidor. Sin sesión, CSRF o rol admitido se obtiene 401/403 sin inserción. | PASS |
| Validación y negocio | Incluidos en `ProductControllerTest` | Nombre vacío/largo, categoría nula/cero, precio nulo/cero/negativo/con exceso de decimales o enteros, disponibilidad nula, descripción larga y JSON malformado dan 400; categoría inexistente da 404 e inactiva 409. Se verificaron `status`, `code`, `requestId` y ausencia de filas. | PASS |
| Integración y regresión | `ProductControllerTest` y `CategoryControllerTest` (7) | Producto existente conserva categoría, precio y estado al desactivar; se rechaza otra alta y vuelve a admitirse tras reactivar. Las rutas de categoría mantienen alta, listado, actualización, roles y transiciones. | PASS |

**Tests planificados vs. implementados/ejecutados:** todos los grupos planificados para la tanda 3 tienen cobertura. **Tests faltantes:** ninguno. La concurrencia MySQL y el esquema MySQL se verificaron en las tandas 1 y 2; esta tanda no los repitió porque no modifica persistencia ni bloqueo.

## Incidentes

Ningún incidente técnico relevante. No hubo tests fallidos ni cambios correctivos durante la ejecución.

## Riesgos residuales y decisiones

El recorrido HTTP se probó sobre H2; su comportamiento de bloqueo concurrente en MySQL depende de la evidencia ya registrada en la tanda 2. No hay decisiones pendientes. Edición, lectura pública y variantes permanecen en tarjetas posteriores.

## Criterio de cierre

- [x] Alta HTTP y respuestas 400/401/403/404/409 verificadas con ausencia de escritura en rechazos.
- [x] Conservación del producto al desactivar categoría repetida por el recorrido HTTP integrado.
- [x] Regresión HTTP de categorías, tests faltantes e incidentes registrados.

Antes de aprobar la tarjeta completa, el responsable humano debería revisar el contrato público del request y response, los permisos de los tres roles y el alcance que queda para las tarjetas posteriores. La implementación se detiene en esta tanda.
