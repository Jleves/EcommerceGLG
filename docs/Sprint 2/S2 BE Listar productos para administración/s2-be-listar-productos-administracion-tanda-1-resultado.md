# Tanda 1 — resultado de ejecución

**Estado:** COMPLETA

## Alcance ejecutado

Se agregó `GET /api/catalog/products` con `page` desde 0, `size` predeterminado 20 y válido de 1 a 100, y `nombre` opcional. Devuelve resúmenes sin características, total y cantidad de páginas. Incluye productos activos e inactivos, ordenados por `nombre` e `id` ascendentes. El nombre se recorta y se busca sin distinguir mayúsculas; `%`, `_` y `!` se interpretan literalmente. `nombre` vacío o compuesto por espacios devuelve 400.

Se modificaron el controller, contrato e implementación del servicio y repositorio de productos; se agregaron `ProductSummaryResponse` y `ProductPageResponse`. No hubo cambio de esquema, frontend ni mutaciones. No fue necesario ampliar el contexto más allá de los componentes, tests y manejo de errores directamente relacionados.

## Diferencias respecto del plan

Ninguna funcional. Se sumó una prueba de compatibilidad SQL en MySQL 8.4.

## Tests ejecutados

Comando: `mvnw.cmd -q '-Dtest=ProductListingTest,ProductListingMySqlTest,ProductControllerTest,ProductServiceIntegrationTest' test` con Java 21. Corrida final: **18 tests, 18 exitosos, 0 fallidos, 0 omitidos**.

| Grupo | Pruebas | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| Integración de lectura | `ProductListingTest` | Vacío, página llena y fuera del total; metadatos; empates por ID; activos e inactivos; resumen sin características | PASS |
| Búsqueda | `ProductListingTest`, `ProductListingMySqlTest` | Recorte, búsqueda sin distinguir mayúsculas, literales `%`, `_`, `!`, conteo y paginación en H2/MySQL | PASS |
| Seguridad y validación HTTP | `ProductListingTest` | 200 para SUPER_ADMIN, ADMIN y USER; 401 sin sesión; 403 otro rol; 400 para `nombre` vacío, página negativa y tamaño fuera de 1–100 | PASS |
| Costo de consulta | `ProductListingTest` | Un spy verifica que el listado no invoca `findByProductIdWithCharacteristic` por fila | PASS |
| Regresión | `ProductControllerTest`, `ProductServiceIntegrationTest` | Alta, detalle, edición, estados, permisos y persistencia de las operaciones existentes | PASS |

## INC-001 — patrón incompleto en prueba MySQL

**Contexto:** primera ejecución de `ProductListingMySqlTest.mysqlPaginatesAndEscapesLiteralNameSearch`.

**Test/comportamiento afectado:** búsqueda literal de `%` con paginación en MySQL.

**Esperado:** dos productos que contienen `%`.

**Obtenido:** cero productos.

**Clasificación:** defecto del test.

**Causa:** el patrón de prueba `%!%` escapaba el porcentaje, pero omitía el comodín final que utiliza el servicio para buscar subcadenas. El patrón correcto es `%!%%`.

**Resolución:** se corrigió únicamente el dato del test y se ejecutó de nuevo la suite completa de la tanda.

**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS.

**Riesgo o aprendizaje derivado:** al probar directamente el repositorio, el patrón debe reproducir el contrato de normalización del servicio.

## Tests faltantes y riesgos residuales

No faltan los grupos de prueba previstos para esta tanda. El costo se comprobó respecto de la carga de características; no se midió latencia ni número total de consultas con volumen de producción. La búsqueda por subcadena puede degradarse con un catálogo grande; se recomienda medirla con datos reales antes de decidir optimizaciones. El uso desde React corresponde a la tanda 2.

## Decisiones pendientes

Ninguna.

## Criterio de cierre

- [x] Contrato, filtros, orden, permisos y errores comprobados.
- [x] Tests de la tanda ejecutados; costo de consulta revisado.
- [x] Incidentes y riesgos residuales registrados.

La siguiente tanda requiere aprobación explícita según el plan.
