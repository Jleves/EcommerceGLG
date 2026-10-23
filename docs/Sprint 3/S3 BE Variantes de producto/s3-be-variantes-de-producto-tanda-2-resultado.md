# S3 BE — Variantes de producto: resultado de Tanda 2

**Fecha:** 22/10/2026. **Estado:** COMPLETA. **Plan:** [Tanda 2](s3-be-variantes-de-producto-plan-implementacion.md#tanda-2--gestión-administrativa-de-variantes).

## Revisión previa y alcance

La tanda 1 seguía implementada: V10, entidades, repositorios, variante inicial y sincronización heredada estaban presentes; `git status --short` estaba limpio. Se revisaron sus DTO, servicios, pruebas de producto/características y seguridad. El código asumía una sola variante en `findByProductId`, y el `PUT` exigía precio/disponibilidad; ambos debían cambiar para cumplir D06.

Se implementó la gestión administrativa y las reglas D01–D06. Quedaron fuera de alcance la selección pública, V11, el frontend de variantes, carrito y presupuestos. Fue necesario ampliar el análisis a `ProductServiceTest` para adaptar su fixture al nuevo constructor y a los tests de características para confirmar el contrato de conteos y borrado.

## Cambios realizados

- `ProductVariantController` expone listado, alta, edición y cambios de estado bajo `/api/catalog/products/{productId}/variants`, con los roles de catálogo y CSRF existente.
- `ProductVariantService` bloquea el producto en cada mutación. Valida el único atributo variable, valor y unidad normalizados con `Locale.ROOT`, reserva valores de variantes inactivas, impide desactivar la última activa y valida SKU global opcional. El índice único de MySQL es la defensa final ante carreras.
- El alta de producto acepta el par heredado o `varianteInicial`, pero no ambos. El `PUT` de producto distingue campos heredados ausentes de presentes: con varias variantes rechaza su presencia con 409; con una variante conserva la sincronización. La edición común permanece disponible.
- Las respuestas administrativas de detalle y listado incluyen todas las variantes, activas e inactivas. `precioReferencia`, `disponible` y `varianteInicialId` representan la variante de menor ID. El listado carga variantes y valores por lote. La columna heredada sigue sincronizada al editar esa variante para sostener la transición, sin aplicar todavía la selección pública de la tanda 3.
- `CharacteristicResponse` añade `cantidadVariantes` sin cambiar `cantidadProductos`. La lista calcula ambos conteos con consultas agrupadas. El borrado de una definición usada por variantes devuelve 409; la FK queda como defensa ante carreras.
- Los conflictos nuevos se entregan con el formato `ApiError` existente. No se agregó una migración ni se cambió configuración.

**Diferencias respecto del plan:** se concretó que el listado incluye todas las variantes y que los escalares administrativos corresponden al menor ID, según las decisiones tomadas antes de implementar. No se adelantó la tanda 3.

## Tests ejecutados

| Categoría | Qué demuestra | Resultado |
| --- | --- | --- |
| Servicio/H2 | Alta gradual, IDs estables, atributo único, normalización de valor/unidad, reserva de inactivas, última activa, SKU, atributo compartido, alta anidada y PUT heredado/común | PASS; 6 pruebas en `ProductVariantFlowTest`, sin omisiones |
| HTTP/seguridad H2 | Rutas nuevas, respuesta de listado/detalle, roles permitidos y ajeno, anónimo, CSRF, alta con `varianteInicial`, fuentes mixtas, PUT multivariante 409 y borrado en uso 409 | PASS; incluido en las 6 pruebas anteriores |
| Concurrencia MySQL 8.4 | Dos altas del mismo valor se serializan; dos productos con el mismo SKU normalizado dejan una sola edición persistida | PASS; 2 pruebas en `ProductVariantConcurrencyMySqlTest`, sin omisiones |
| Regresión dirigida | Producto, listado, validación de PUT, características y conteos | PASS; 36 pruebas en la corrida dirigida posterior a INC-002/003 |
| Suite backend | Regresión general con Docker disponible | PASS; `mvnw.cmd test -q` salió 0; reportes Surefire: 622 pruebas, 0 fallas, 0 errores, 0 omisiones en 109 clases |
| Ajuste final | Conteo agrupado en H2/MySQL y casos HTTP añadidos después de la suite completa | PASS; corridas dirigidas de características y variantes, sin fallas |
| Empaquetado | Compilación y generación del artefacto con Java 21 | PASS; `mvnw.cmd package -q -DskipTests` |

La suite completa tenía 622 casos al ejecutarse; el caso HTTP agregado después pasó en una corrida dirigida y los reportes locales actualizados suman 623. Java 21 se configuró para Maven desde `C:\Users\jlinf\.jdks\ms-21.0.7`. Las pruebas que cargan Mockito se ejecutaron fuera del sandbox; Testcontainers usó Docker Desktop. `git diff --check` no informó errores de espacios, solo avisos de conversión LF/CRLF de Git.

## Incidentes

### INC-001 — Mockito detenido en sandbox

**Contexto:** las primeras corridas dirigidas dentro del sandbox llegaron al inicio de Mockito y quedaron sin progreso; se interrumpieron.

**Test/comportamiento afectado:** pruebas Spring Boot con Mockito.

**Esperado:** completar la corrida y producir reportes Surefire.

**Obtenido:** proceso detenido tras cargar el agente dinámico, sin resultado de pruebas.

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** restricción del sandbox al agente de Mockito, consistente con la tanda 1.

**Resolución:** repetir las corridas fuera del sandbox con aprobación de ejecución.

**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS.

**Riesgo o aprendizaje derivado:** ejecutar estas pruebas fuera del sandbox mientras persista esa restricción.

### INC-002 — Expectativa heredada del validador de PUT

**Contexto:** `UpdateProductRequestValidationTest` esperaba que un precio o disponibilidad nulos fueran siempre inválidos por `@NotNull`.

**Test/comportamiento afectado:** validación de `UpdateProductRequest`.

**Esperado:** el test antiguo esperaba una violación de campo `precioReferencia` al construir un request sin precio.

**Obtenido:** el validador no la produjo, porque D06 requiere permitir campos heredados ausentes en la edición común.

**Clasificación:** cambio deliberado de contrato, respaldado por D06 y el plan.

**Causa:** la prueba codificaba la obligatoriedad anterior para todo `PUT`.

**Resolución:** antes de modificar el test se confirmó D06. Se ajustó la expectativa para permitir ausencia, conservando las pruebas de límites de los valores presentes; el servicio rechaza `null` explícito en monovariante y la presencia heredada en multivariante.

**Código productivo modificado:** sí. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS.

**Riesgo o aprendizaje derivado:** la presencia se interpreta en la capa de servicio, donde se conoce cuántas variantes tiene el producto.

### INC-003 — Flush anticipado al comprobar SKU

**Contexto:** la primera corrida de `nestedInitialVariantRejectsMixedSourcesAndSkuIsGlobal` falló al editar una variante con un SKU ya usado.

**Test/comportamiento afectado:** detección de SKU duplicado en una edición.

**Esperado:** `ProductVariantConflictException` antes de persistir.

**Obtenido:** `DataIntegrityViolationException` del índice único de H2. La consulta `existsBySkuNormalizadoAndIdNot` provocó un flush del cambio ya aplicado a la entidad JPA.

**Clasificación:** defecto de implementación.

**Causa:** se asignaba el SKU nuevo antes de consultar la colisión.

**Resolución:** calcular y comprobar el SKU normalizado antes de modificar la entidad; conservar el índice único para carreras MySQL. No se cambió la expectativa válida del test.

**Código productivo modificado:** sí. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS en H2 y en las pruebas de concurrencia MySQL.

**Riesgo o aprendizaje derivado:** las consultas JPA pueden sincronizar cambios pendientes antes de ejecutarse.

### INC-004 — Aviso de cierre de Surefire

**Contexto:** al terminar la suite completa, Surefire informó que iba a terminar su JVM hija tras 30 segundos de `System.exit(0)`; los pools de conexiones a contenedores ya detenidos emitieron avisos.

**Test/comportamiento afectado:** cierre del proceso de pruebas, no una aserción identificada.

**Esperado:** cierre sin aviso.

**Obtenido:** código de salida 0 y reportes con 622 pruebas, 0 fallas, 0 errores y 0 omisiones, junto con el aviso.

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** probablemente quedan recursos de pruebas asociados a contextos MySQL tras detener los contenedores; no se confirmó una causa puntual.

**Resolución:** no se cambió código ajeno a la tanda. Se comprobó el código de salida y los reportes; las corridas dirigidas posteriores cerraron correctamente.

**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS, con aviso residual en la suite completa.

**Riesgo o aprendizaje derivado:** vigilar el cierre de Surefire en futuras suites generales; el aviso no debe confundirse con una falla de test.

## Tests faltantes y riesgos residuales

Los cinco grupos previstos en el plan (servicio/HTTP, validación, concurrencia MySQL, características, seguridad/compatibilidad) tienen pruebas implementadas y ejecutadas. No faltan pruebas previstas para esta tanda. La suite completa precedió al ajuste de la consulta agrupada y a un caso HTTP adicional; ambos se comprobaron después con corridas dirigidas.

La API pública todavía lee `products.precio_referencia` y `products.disponible`, que siguen siendo columnas transitorias. No se probó selección pública por precio, retiro de columnas, interfaz visual de variantes, navegador ni staging; pertenecen a la tanda 3 o a otras tarjetas. Un producto multivariante puede mostrar públicamente el valor transitorio hasta completar la tanda 3. Las pruebas locales no validan un entorno de destino.

## Cierre

- [x] Las variantes se administran sin duplicar productos ni admitir dos atributos variables.
- [x] Se preservan IDs, SKU único global y al menos una variante activa.
- [x] Borrado y conteos de características, concurrencia MySQL, HTTP, seguridad y compatibilidad comprobados.
- [x] Incidentes, pruebas no realizadas fuera de alcance y riesgos residuales registrados.

**Decisiones pendientes descubiertas:** ninguna. **Siguiente revisión humana:** comprobar el contrato administrativo de variantes y el riesgo de precio público transitorio antes de aprobar la tanda 3.
