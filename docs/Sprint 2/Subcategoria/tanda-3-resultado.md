# Tanda 3 — Resultado de ejecución

**Plan:** [subcategorias-plan-implementacion.md](subcategorias-plan-implementacion.md)  
**Estado:** COMPLETA

## Alcance ejecutado

- `POST /api/catalog/products` acepta `subcategoriaId` opcional. Ausente o `null` crea un producto sin subcategoría; un ID existente se bloquea y se valida contra la categoría activa seleccionada.
- `PUT /api/catalog/products/{id}` distingue tres casos: campo omitido conserva la asignación, `null` la elimina y un ID la reemplaza. Si cambia `categoriaId`, se exige `subcategoriaId: null` o un ID del nuevo padre cuando el producto tenía una asignación incompatible. Una nueva asignación bloquea categoría, subcategoría y producto en ese orden, para coordinar también la desactivación del padre.
- Una asignación inexistente responde 404; un padre distinto responde 400; una categoría padre inactiva responde 409. La edición ordinaria sin nueva asignación mantiene el comportamiento previo, incluido el uso de una categoría inactiva.
- `ProductResponse` y `ProductSummaryResponse` exponen `subcategoriaId` nullable además de `categoriaId`. `GET /api/catalog/products` combina `nombre`, `categoriaId` y `subcategoriaId`, con el orden y la paginación existentes; una combinación de IDs con padres distintos responde 400.

No se modificó React, el catálogo público, el esquema ni el borrado de subcategorías. `findPageByName(...)` se conservó para las consultas y pruebas existentes; `findPageByFilters(...)` implementa el listado ampliado.

## Verificación

Comando desde `Backend/spring-starter`, con `JAVA_HOME=C:\Users\jlinf\.jdks\ms-21.0.7`:

```powershell
.\mvnw.cmd -q '-Dtest=ProductSubcategoryHttpTest,ProductSubcategoryConcurrencyMySqlTest,ProductServiceTest,ProductListingTest,ProductListingMySqlTest,ProductControllerTest,ProductCharacteristicUpdateHttpTest,SubcategoryDeleteMySqlTest' test
```

| Grupo | Pruebas | Qué demuestra | Resultado |
| --- | --- | --- | --- |
| HTTP e integración H2 | `ProductSubcategoryHttpTest` (3) | Alta con/sin subcategoría, lectura, semántica omitido/`null`/ID, cambio de categoría, errores 400/404/409, filtros combinados, IDs de respuesta y paginación. | 3/3 PASS |
| Concurrencia MySQL 8.4 | `ProductSubcategoryConcurrencyMySqlTest` (4) | Asignación antes de borrado termina desvinculada; borrado antes de alta rechaza el ID; edición y borrado no forman un bloqueo circular; desactivación previa del padre impide una asignación en edición. | 4/4 PASS |
| Regresión de productos y características | `ProductServiceTest`, `ProductListingTest`, `ProductListingMySqlTest`, `ProductControllerTest`, `ProductCharacteristicUpdateHttpTest` (26) | Contrato previo de alta, edición, estados, seguridad, búsqueda, paginación y características sin enviar `subcategoriaId`. | 26/26 PASS |
| Regresión de borrado MySQL | `SubcategoryDeleteMySqlTest` (3) | Desvinculación, conservación de categoría, actualización de fecha y rollback de la tanda 2. | 3/3 PASS |

**Corrida conjunta final:** 36/36 PASS, 0 fallos, 0 errores, 0 omitidos. Incluye la comprobación de la segunda página filtrada y la carrera con desactivación del padre. La compilación de código productivo también pasó.

## INC-004 — Mockito no pudo adjuntarse en el sandbox

**Tanda:** 3  
**Contexto:** primera corrida de la suite Maven.  
**Test/comportamiento afectado:** inicio de `ProductCharacteristicUpdateHttpTest` y, por consecuencia, la suite seleccionada.  
**Esperado:** iniciar Mockito y terminar los tests.  
**Obtenido:** la JVM informó que no podía abrir el pipe de attach; la corrida no progresó y se interrumpió.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** confirmada por el mensaje de attach y por la ejecución posterior; el entorno restringido impedía la comunicación entre procesos que necesita Mockito.  
**Resolución:** ejecutar la misma suite fuera del sandbox autorizado, sin cambiar expectativas ni implementación.  
**Código productivo modificado:** no por este incidente.  
**Tests modificados:** no por este incidente.  
**Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS, 36/36 en la corrida final.  
**Riesgo o aprendizaje derivado:** las suites con Mockito pueden requerir ejecución fuera del sandbox en este equipo.

## Tests faltantes y riesgos residuales

No faltan pruebas planificadas para la tanda 3. La prueba MySQL cubre ambos órdenes de alta frente a borrado y edición antes de borrado; no explora todas las combinaciones posibles de carga, fallas de infraestructura o transacciones externas. La regla de correspondencia entre categoría y subcategoría se garantiza en el servicio; una escritura directa a `products` podría guardar un par incompatible porque el esquema tiene FKs independientes. La interfaz administrativa y sus pruebas pertenecen a la tanda 4; el catálogo público queda para su tarjeta futura.

**Decisiones pendientes descubiertas:** ninguna.

## Archivos modificados

- `Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/product/controller/ProductController.java`
- `Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/product/service/ProductService.java` y `ProductServiceImpl.java`
- `Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/product/repository/ProductRepository.java`
- `Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/product/dto/CreateProductRequest.java`, `UpdateProductRequest.java`, `ProductResponse.java` y `ProductSummaryResponse.java`
- `Backend/spring-starter/src/test/java/com/ashenox/starter/catalog/product/ProductServiceTest.java`, `ProductSubcategoryHttpTest.java` y `ProductSubcategoryConcurrencyMySqlTest.java`
- `docs/Sprint 2/Subcategoria/subcategorias-plan-implementacion.md` y este documento.

## Cierre

- [x] Productos nuevos y existentes funcionan con o sin subcategoría.
- [x] Asignación y borrado concurrentes conservan integridad y no forman un bloqueo circular en los casos probados.
- [x] Contratos, regresión, faltantes, incidente y riesgos documentados.

Antes de aprobar la tanda 4, conviene revisar la semántica de `PUT` (omitido conserva, `null` quita) y el riesgo de escrituras directas a la base de datos que salteen el servicio.
