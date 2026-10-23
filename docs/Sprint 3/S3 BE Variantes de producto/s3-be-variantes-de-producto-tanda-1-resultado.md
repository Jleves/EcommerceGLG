# S3 BE — Variantes de producto: resultado de Tanda 1

**Fecha:** 22/10/2026. **Estado:** COMPLETA. **Plan:** [Tanda 1](s3-be-variantes-de-producto-plan-implementacion.md#tanda-1--persistencia-y-variante-inicial).

## Revisión previa y alcance

El código inicial no tenía tablas ni clases de variantes. `ProductServiceImpl` escribía precio y disponibilidad en `products`; V9 era la última migración. D01, D04 y D06 seguían siendo aplicables. No había cambios locales previos (`git status --short` vacío).

Se implementó la persistencia de la variante inicial, el backfill y la sincronización de alta/edición, con `varianteInicialId` agregado a las respuestas administrativas. Quedaron fuera de alcance la gestión de múltiples variantes, la selección pública, el retiro de columnas antiguas, cambios del frontend y endpoints nuevos. No se necesitó contexto adicional de producto; sí se revisaron fixtures de pruebas relacionadas al aparecer nuevas FK y Docker al preparar la verificación MySQL.

## Cambios realizados

- V10 crea `product_variants` con precio `DECIMAL(15,2)`, disponibilidad, estado, SKU opcional y unicidad global de `sku_normalizado` no nulo. Crea `variant_characteristics` con `variant_id` como clave primaria, de modo que una variante admite a lo sumo una asociación. Añade FK e índices. El `INSERT ... SELECT` copia precio, disponibilidad y auditoría de todos los productos, con variante activa y SKU nulo.
- Se agregaron `ProductVariant`, `VariantCharacteristic` y sus repositorios JPA. El alta guarda producto y variante dentro de una transacción. La edición bloquea el producto y actualiza sus dos valores heredados y los de la variante antes de completar la transacción. Un producto sin variante inicial en una edición es una anomalía y la operación falla; no se genera una variante silenciosamente.
- `ProductResponse` y `ProductSummaryResponse` agregan `varianteInicialId` y conservan los campos existentes. El listado obtiene IDs de variante en una consulta por página. La lectura pública continúa usando `Product`.
- Se ajustaron los fixtures que borran productos entre pruebas para eliminar primero sus variantes; los fixtures de edición HTTP crean una variante asociada. Esta adaptación fue necesaria por la FK nueva y no modifica flujos productivos.

**Diferencias respecto del plan:** ninguna funcional. No se inició la Tanda 2.

## Tests ejecutados

| Categoría | Comprobación | Resultado |
| --- | --- | --- |
| Migración H2 | Actualización V9→V10 con cuatro combinaciones de estado/disponibilidad, copia decimal y una variante por producto | PASS, 1 prueba |
| Persistencia H2/JPA | Alta con variante inicial y asociación opcional con característica, recarga de mapeos | PASS, 1 prueba |
| Servicio y HTTP | Alta, edición, respuesta administrativa con ID, sincronización de precio/disponibilidad y controles heredados | PASS; `ProductServiceTest` 10, `ProductServiceIntegrationTest` 4, `ProductControllerTest` 10, `ProductListingTest` 3 |
| Atomicidad | Fallo al guardar una característica revierte tanto producto como variante | PASS, 1 prueba |
| Regresión | Suite backend completa, incluida la regresión H2 de producto, características, imágenes y lectura pública | `mvnw.cmd test -q`: 573 detectadas; 406 ejecutadas, 167 omitidas, 0 fallas, 0 errores |
| Empaquetado | Compilación y generación del artefacto con Java 21 | `mvnw.cmd package -q -DskipTests`: PASS |
| MySQL/Testcontainers | Backfill V9→V10, decimal exacto, precio positivo, SKU normalizado único, múltiples SKU nulos y FK | PASS, 3 pruebas nuevas en MySQL 8.4 |
| Regresión MySQL relacionada | Productos, características, subcategorías, imágenes, concurrencia y migraciones | PASS, 37 pruebas en 19 clases; 0 fallas, 0 errores y 0 omisiones |

Java 21 se encontró en `C:\Users\jlinf\.jdks\ms-21.0.7` y se configuró solo para los comandos de Maven. La ejecución dirigida inicial en sandbox no terminó por el agente de Mockito; la repetición fuera del sandbox pasó 28 pruebas. La primera suite completa se ejecutó sin Docker y omitió 167 casos; después se ejecutaron específicamente las pruebas de V10 y la regresión MySQL relacionada con Docker disponible. La suite completa de 573 pruebas no se repitió con Docker.

## Incidentes

### INC-001 — Agente Mockito bloqueado en sandbox

**Contexto:** primera ejecución dirigida de servicio/HTTP dentro del sandbox.

**Test/comportamiento afectado:** ejecución de pruebas que usan Mockito.

**Esperado:** iniciar los tests y obtener resultado de aserciones.

**Obtenido:** el proceso quedó sin avanzar tras `could not open (5) pipe ... to send result of operation load` al adjuntar Byte Buddy; se interrumpió.

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** bloqueo del mecanismo de adjunto del agente Java en el sandbox; la repetición fuera del sandbox pudo adjuntarlo.

**Resolución:** repetir fuera del sandbox sin modificar producto ni tests.

**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS, 28 pruebas dirigidas.

**Riesgo o aprendizaje derivado:** las suites con Mockito deben ejecutarse en un entorno que permita el adjunto del agente.

### INC-002 — MySQL/Testcontainers inicialmente no disponible

**Contexto:** suite backend completa y pruebas nuevas de MySQL.

**Test/comportamiento afectado:** migración y restricciones reales de MySQL; regresión que depende de Testcontainers.

**Esperado:** iniciar MySQL 8.4 en Docker y ejecutar las pruebas.

**Obtenido:** Testcontainers informó `Could not find a valid Docker environment`; Docker CLI no pudo conectarse al daemon. Se intentó iniciar Docker Desktop en segundo plano y el daemon siguió sin responder. En una comprobación posterior, `com.docker.service` seguía detenido y no pudo iniciarse con los permisos disponibles. Hay un servicio `MySQL84` activo, pero la conexión local sin credenciales fue rechazada; no se modificó esa base.

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** daemon Docker no disponible al ejecutar la primera suite.

**Resolución:** al reintentar, Docker respondió fuera del sandbox. Se ejecutaron las pruebas nuevas de V10 y la regresión MySQL relacionada con contenedores aislados MySQL 8.4.

**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS, 37 pruebas MySQL relacionadas sin omisiones.

**Riesgo o aprendizaje derivado:** H2 por sí solo no confirma restricciones ni comportamiento de V10 en MySQL; esa evidencia quedó cubierta con Testcontainers.

### INC-003 — Excepción esperada incorrecta en test MySQL

**Contexto:** primera ejecución de `ProductVariantPersistenceMySqlTest` con Docker disponible.

**Test/comportamiento afectado:** `mysqlRejectsNonPositivePriceAndMissingProduct`, rechazo de precio cero.

**Esperado:** MySQL rechaza `precio_referencia = 0`; el test esperaba `DataIntegrityViolationException`.

**Obtenido:** MySQL rechazó el dato con código 3819 por `ck_product_variants_precio_positive`, pero `JdbcTemplate` lo envolvió en `UncategorizedSQLException`.

**Clasificación:** defecto del test.

**Causa:** la expectativa fijaba una clase de excepción de Spring que no corresponde a este código de error de MySQL.

**Resolución:** comprobar la causa `SQLException` y el código MySQL 3819, conservando la expectativa funcional de rechazo. La repetición pasó.

**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS.

**Riesgo o aprendizaje derivado:** para `CHECK` de MySQL, verificar el código del motor evita depender de la traducción de excepciones de Spring.

## Tests faltantes y riesgos

No faltan pruebas de las definidas para la Tanda 1. Se compararon las pruebas planificadas con las ejecutadas: backfill H2/MySQL, restricciones MySQL, alta/edición HTTP, atomicidad y regresión relacionada quedaron cubiertos. La suite backend completa no se repitió con Docker; se ejecutó antes sin Docker (406 pruebas activas) y luego se corrió la regresión MySQL pertinente (37 pruebas activas).

Durante la transición hay dos copias persistentes de precio y disponibilidad. `ProductServiceImpl` las sincroniza, pero escrituras directas al repositorio o SQL externo pueden divergir. El contrato público sigue leyendo `products` hasta la Tanda 3. Una prueba local tampoco valida por sí sola un entorno de destino. No hay decisiones de arquitectura pendientes.

## Cierre de la tanda

- [x] V10 migra productos existentes en H2 y MySQL; las altas nuevas crean variante activa.
- [x] Precio y disponibilidad coinciden en backfill y flujos de alta/edición probados.
- [x] Tests H2/MySQL y regresión relacionada pasan; incidentes y riesgos están registrados.

**Archivos modificados:** V10; entidades y repositorios en `catalog/product/variant`; `ProductServiceImpl`; `ProductResponse` y `ProductSummaryResponse`; pruebas de migración, persistencia, servicio, HTTP, atomicidad y regresión; plan e informe de esta tanda.

**Revisión humana antes de aprobar Tanda 2:** revisar el diff de V10, la compatibilidad temporal de columnas duplicadas y el resultado de pruebas en contenedores aislados. No se comenzó la Tanda 2.
