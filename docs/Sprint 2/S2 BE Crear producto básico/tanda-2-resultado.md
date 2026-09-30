# S2 BE — Crear producto básico: resultado de la tanda 2

**Fecha:** 2026-09-30  
**Estado:** COMPLETA

## Alcance y verificación previa

La tanda 1 dejó presentes la migración V6, `Product` y `ProductRepository`, con pruebas de persistencia H2 y MySQL. `CategoryServiceImpl.setActive` aún leía con `findById`, por lo que el alta y la transición de estado necesitaban coordinarse mediante `CategoryRepository.lockById`. No fue necesario revisar componentes ajenos al catálogo. Se consultó una prueba MySQL existente de concurrencia para seguir la convención de observar esperas reales en `performance_schema`.

Se implementó el contrato de entrada y salida del servicio, la elegibilidad de categoría activa y el bloqueo compartido por el alta y las transiciones de estado. Quedan fuera de alcance el controlador, autorización, CSRF, traducción HTTP de excepciones y tests de respuesta HTTP de la tanda 3, además de variantes y otras tarjetas posteriores.

**Archivos previstos y finalidad:** DTO y servicio nuevos en `catalog/product`; `CategoryRepository` y `CategoryServiceImpl` para el bloqueo; `CategoryServiceTest` y tests nuevos del producto para verificar reglas, conservación y concurrencia. No hubo refactors adicionales.

## Cambios realizados

- `CreateProductRequest` declara las restricciones de nombre, categoría, descripción, precio y disponibilidad acordadas; `ProductResponse.from` devuelve los datos persistidos y estados iniciales.
- `ProductServiceImpl.create` bloquea la fila de categoría en una transacción, distingue ausencia de inactividad, recorta el nombre y guarda con `activo=true` y `destacado=false`. Los nombres duplicados siguen permitidos.
- `CategoryRepository.lockById` aplica `PESSIMISTIC_WRITE` a una consulta por ID. `CategoryServiceImpl.setActive` usa el mismo bloqueo tanto al desactivar como al reactivar.
- Se añadieron pruebas unitarias, de integración H2 y de concurrencia MySQL. La regresión de categoría se adaptó a la llamada al repositorio con bloqueo.

**Diferencias respecto del plan:** ninguna funcional. La ejecución usó Maven con Java 21 en Docker porque no había JDK local.

## Tests ejecutados

Se ejecutaron dos selecciones Maven con `mvn -B -q -Dtest=... test` en `maven:3.9-eclipse-temurin-21`, montando `/var/run/docker.sock` y usando MySQL 8.4 de Testcontainers. Los reportes Surefire de ambas ejecuciones finales registran **31 pruebas, 0 fallos, 0 errores y 0 omisiones**.

| Categoría | Tests | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| Unitarios | `ProductServiceTest` (4) | Recorte de nombre, conservación de datos, estados fijados por servidor, duplicados admitidos y ausencia de guardado con categoría inexistente o inactiva. | PASS |
| Integración H2 | `ProductServiceIntegrationTest` (1) | Producto existente conserva `category_id`, datos y `activo` tras desactivar; una nueva alta se rechaza; tras reactivar vuelve a permitirse. | PASS |
| Concurrencia MySQL | `ProductCategoryConcurrencyMySqlTest` (2) | En ambos órdenes se observó espera de bloqueo sobre `categories`: alta primero conserva el producto; desactivación primero hace rechazar el alta sin insertar. | PASS |
| Regresión categoría | `CategoryServiceTest` (8), `CategoryPersistenceIntegrationTest` (3), `CategoryControllerTest` (7), `CategoryServiceMySqlTest` (2) | Alta, actualización, listado y transiciones de categoría conservan su comportamiento de servicio, persistencia y HTTP. | PASS |
| Regresión persistencia | `ProductPersistenceIntegrationTest` (1), `ProductPersistenceMySqlTest` (3) | Esquema, mapeo, precio, FK y auditoría del producto siguen funcionando. | PASS |

**Tests planificados vs. implementados/ejecutados:** se cubrieron todos los grupos planificados para la tanda 2: unitarios, integración H2, ambos órdenes de concurrencia MySQL y regresión del servicio de categoría. **Tests faltantes:** ninguno. Las pruebas HTTP de alta de producto, roles, CSRF, validación y códigos de error pertenecen a la tanda 3.

## Incidentes

### INC-T2-001

**Contexto:** primera ejecución de la suite seleccionada dentro de un contenedor Maven.  
**Test/comportamiento afectado:** `ProductCategoryConcurrencyMySqlTest`, ambos órdenes concurrentes.  
**Esperado:** Testcontainers conecta al daemon Docker y ejecuta las dos pruebas sobre MySQL 8.4.  
**Obtenido:** Maven terminó con salida 0, pero Testcontainers omitió las pruebas MySQL al no encontrar `/var/run/docker.sock`; los tests H2 y unitarios sí se ejecutaron.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** se había montado la pipe de Windows en el contenedor Linux, no el socket Unix que Testcontainers necesita.  
**Resolución:** se montó `/var/run/docker.sock:/var/run/docker.sock` y se repitió la suite. Los reportes finales registran 2 pruebas de concurrencia, 0 omisiones y 0 fallos.  
**Código productivo modificado:** no.  
**Tests modificados:** no.  
**Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** el código de salida de Maven por sí solo no confirma que Testcontainers haya ejecutado las pruebas; se deben revisar los contadores de omisiones.

## Riesgos residuales y decisiones

No hay decisiones pendientes. `CreateProductRequest` tiene restricciones declaradas, pero todavía no existe ruta HTTP que las aplique; tampoco se han verificado autorización, CSRF ni el mapeo de la excepción de categoría inactiva a 409. Esas verificaciones son parte de la tanda 3. No se detectaron faltantes dentro de la tanda 2.

## Criterio de cierre

- [x] Categoría activa y estados iniciales comprobados; categoría inexistente e inactiva no guardan producto.
- [x] Ambos órdenes de alta y desactivación comprobados con espera real en MySQL.
- [x] Producto existente sobrevive a la desactivación y reactivación de su categoría.
- [x] Resultados, faltantes e incidente de entorno registrados en este documento y en el plan vivo.

Antes de aprobar la tanda 3, revisar la consulta con bloqueo y la coordinación transaccional, además de la cobertura MySQL de ambos órdenes. No se inició la tanda 3.
