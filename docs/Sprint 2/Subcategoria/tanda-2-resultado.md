# Tanda 2 — resultado de ejecución

**Fecha:** 12 de octubre de 2026  
**Estado:** COMPLETA  
**Plan:** [subcategorias-plan-implementacion.md](subcategorias-plan-implementacion.md)

## Alcance realizado

- `SubcategoryController` expone `GET/POST /api/catalog/subcategories` y `GET/PUT/DELETE /api/catalog/subcategories/{id}` con los mismos roles, sesión y CSRF que categorías. Alta: 201; lecturas y edición: 200; borrado: 204.
- La creación exige padre existente y activo. El nombre se recorta y normaliza con `Locale.ROOT`, con unicidad por categoría respaldada por el índice de V8. La edición conserva el padre. La lista admite `categoriaId` opcional y ordena por padre, nombre e ID. Solo el detalle calcula `productosAsociados`.
- El borrado bloquea la fila de subcategoría, ejecuta `UPDATE products SET subcategory_id = NULL, updated_at = CURRENT_TIMESTAMP(6) WHERE subcategory_id = :id` y elimina la subcategoría en una transacción. Los productos y sus categorías permanecen.
- `GlobalExceptionHandler` traduce el conflicto de nombre o padre inactivo a 409. La categoría o subcategoría ausente responde 404 y los requests inválidos responden 400.

## Diferencias respecto del plan

`JpaRepository` de la versión actual no declara `deleteAndFlush(...)`; el servicio ejecuta `delete(...)` y `flush()` explícito. El efecto y el límite transaccional son los previstos.

## Pruebas ejecutadas

| Grupo | Ejecución | Qué demuestra | Resultado |
| --- | --- | --- | --- |
| HTTP, servicio y seguridad H2 | `SubcategoryControllerTest` (4) | Alta, consulta, filtro, detalle, edición, borrado, validaciones y códigos 201/200/204/400/404/409; acceso de SUPER_ADMIN, ADMIN y USER; rechazo de anónimo, rol ajeno y mutaciones sin CSRF. | 4/4 PASS |
| Integración MySQL | `SubcategoryDeleteMySqlTest` (3) | Borrado vacío y liberación del nombre; borrado con dos productos, categoría conservada y `updated_at` actualizado solo en los afectados; una FK de prueba que falla en el DELETE revierte la desvinculación. | 3/3 PASS |
| Concurrencia MySQL | `SubcategoryCreateConcurrencyMySqlTest` (1) | Dos altas que superan simultáneamente el prechequeo producen una sola fila; el índice único rechaza la otra. | 1/1 PASS |
| Regresión de categorías H2 | `CategoryControllerTest` (7) | Alta, edición, activación, desactivación, validaciones y seguridad previas. | 7/7 PASS |
| Regresión de productos H2 | `ProductControllerTest` (10) y `ProductServiceIntegrationTest` (4) | API y servicio de productos existentes continúan funcionando con el repositorio modificado. | 14/14 PASS |

**Total final:** 29/29 PASS, 0 fallidos y 0 omitidos. Maven y JDK 21 se ejecutaron en `maven:3.9-eclipse-temurin-21`; MySQL 8.4 se ejecutó mediante Testcontainers. La terminal Windows no tiene Java configurado.

## INC-002 — método de repositorio inexistente

**Tanda:** 2.  
**Contexto:** primera compilación de la implementación.  
**Test/comportamiento afectado:** inicio de la suite HTTP; no alcanzó a ejecutar tests.  
**Esperado:** compilar el servicio de borrado.  
**Obtenido:** el compilador no encontró `SubcategoryRepository.deleteAndFlush(...)`.  
**Clasificación:** defecto de implementación.  
**Causa:** confirmada; la interfaz `JpaRepository` de esta versión ofrece `delete(...)` y `flush()` por separado.  
**Resolución:** reemplazar la llamada por `delete(subcategory)` seguido de `flush()` dentro de la transacción.  
**Código productivo modificado:** sí. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** verificar la API real de la versión de Spring Data al traducir métodos propuestos del plan.

## INC-003 — expectativas incorrectas en dos pruebas HTTP

**Tanda:** 2.  
**Contexto:** primera ejecución de `SubcategoryControllerTest` tras compilar.  
**Test/comportamiento afectado:** listado por rol y conflicto al editar un nombre ocupado.  
**Esperado:** el listado contenía `role.ordinal() + 1` filas; la edición obtenía 409.  
**Obtenido:** el listado contenía una fila; la edición respondió 200.  
**Clasificación:** defecto del test.  
**Causa:** confirmada; la prueba vaciaba las subcategorías al terminar cada rol y escogía para la edición la misma fila cuyo nombre enviaba. Las respuestas del servicio eran correctas.  
**Resolución:** esperar una fila por rol y escoger expresamente la fila `Chapas` antes de intentar renombrarla a `Caños`, ya ocupado.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** las expectativas de conteo deben reflejar el aislamiento de datos dentro del propio test.

## Faltantes y riesgos

No faltan pruebas planificadas para la tanda 2. La asignación de subcategorías desde la API de productos y la carrera entre asignación y borrado permanecen sin probar porque pertenecen a la tanda 3. Hasta entonces, `products.subcategory_id` solo puede poblarse mediante acceso directo a datos. La cifra `productosAsociados` se consulta antes del borrado y puede variar antes del DELETE; la desvinculación efectiva se hace dentro de la transacción. Las pruebas cubren rollback por una falla de FK entre el UPDATE y el DELETE, no todas las posibles fallas de infraestructura.

**Decisiones pendientes descubiertas:** ninguna.

## Archivos de la tanda

- `Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/subcategory/controller/SubcategoryController.java`
- `Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/subcategory/dto/` (4 DTOs)
- `Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/subcategory/service/` (interfaz, implementación y excepción)
- `Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/product/repository/ProductRepository.java`
- `Backend/spring-starter/src/main/java/com/ashenox/starter/shared/error/GlobalExceptionHandler.java`
- `Backend/spring-starter/src/test/java/com/ashenox/starter/catalog/subcategory/SubcategoryControllerTest.java`
- `Backend/spring-starter/src/test/java/com/ashenox/starter/catalog/subcategory/SubcategoryDeleteMySqlTest.java`
- `Backend/spring-starter/src/test/java/com/ashenox/starter/catalog/subcategory/SubcategoryCreateConcurrencyMySqlTest.java`
- `docs/Sprint 2/Subcategoria/subcategorias-plan-implementacion.md` y este resultado.

## Criterio de cierre

- [x] API con validación, permisos y unicidad por padre.
- [x] Borrado atómico con categoría conservada y `updated_at` actualizado.
- [x] Regresión de categorías y productos ejecutada.
- [x] Incidentes, alcance no probado y riesgos residuales registrados.

**Revisión humana previa a la tanda 3:** confirmar el contrato HTTP y la semántica de borrado físico; revisar que el riesgo de asignación/borrado concurrentes se cubra al implementar la asignación en productos.
