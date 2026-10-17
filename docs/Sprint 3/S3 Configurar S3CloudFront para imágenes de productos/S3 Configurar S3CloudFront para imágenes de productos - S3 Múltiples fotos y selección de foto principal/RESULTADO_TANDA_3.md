# Resultado de ejecución — Tanda 3

**Fecha:** 17/10/2026  
**Estado:** COMPLETA

## Alcance implementado

- `GET /api/public/products` lista solo productos activos, ordenados por nombre e ID, con `page` y `size` (0 y 20 por defecto; tamaño máximo 100). Incluye `primaryThumbnailUrl` o `null`.
- `GET /api/public/products/{id}` devuelve solo productos activos, sus características y la galería de fotos publicadas en orden. Inexistentes e inactivos responden 404; `disponible=false` sigue visible.
- Las respuestas públicas mantienen los campos actuales de resumen y detalle del producto. Las URLs de imagen se forman desde las claves guardadas y la base CloudFront configurada. El servicio de imágenes administrativo usa el mismo constructor de URLs sin cambiar sus respuestas.
- El listado obtiene las principales de la página en una consulta por lote. `SecurityConfig` permite sin sesión únicamente los dos GET públicos y deniega las demás rutas `/api/public/**`.

**Fuera de alcance:** modal y frontend de la tanda 4; cambios en S3, migraciones y recorrido real de CloudFront en staging de la tanda 5.

**Contexto adicional requerido:** se revisaron `SecurityConfig` y los tests de productos e imágenes porque la configuración existente permitía `POST /api/public/**` y la tanda exige lectura pública únicamente. Las tandas 1 y 2 figuran completas en sus resultados y commits. El árbol de Git estaba limpio antes de empezar.

## Cambios realizados

- Controlador, servicio y tres DTO públicos separados de la API administrativa.
- Consultas de productos activos y fotos principales publicadas por lote; lectura ordenada de la galería para el detalle.
- `ProductImageUrlService` compartido entre las respuestas públicas y administrativas.
- Regla de seguridad pública limitada a GET y prueba HTTP nueva.

**Diferencias respecto del plan:** el listado expone solo paginación y los DTO públicos conservan los campos actuales de producto, según las decisiones fijadas antes de implementar. Sin migración ni refactor adicional.

## Tests ejecutados

Comando final: `./mvnw.cmd -q '-Dtest=ProductListingTest,ProductControllerTest,ProductImageControllerTest,ProductImageFlowTest,PublicProductHttpTest' '-Dlogging.level.root=ERROR' test`, con JDK 21 y ejecución fuera del sandbox para permitir el agente de Mockito.

| Grupo | Pruebas | Qué demuestran | Resultado |
| --- | --- | --- | --- |
| Integración y seguridad pública | `PublicProductHttpTest` (3) | Listado paginado, principal o `null`, inactivos fuera, `disponible=false` visible, detalle ordenado y características, fotos pendientes fuera, GET anónimo y mutaciones públicas denegadas | PASS |
| Consulta por lote y URLs | Dentro de `PublicProductHttpTest` | Una llamada al repositorio para principales de una página de dos productos, ninguna consulta de galería por producto, URLs con el dominio CloudFront de test y claves de ambas variantes | PASS |
| Regresión de productos | `ProductListingTest` (3), `ProductControllerTest` (10) | Listado, contrato, validación, roles y mutaciones administrativas | PASS |
| Regresión de imágenes | `ProductImageControllerTest` (4), `ProductImageFlowTest` (10) | Contrato administrativo, carga, principal, orden, reemplazo, borrado y limpieza con almacenamiento simulado | PASS |

**Tests exitosos:** 30. **Tests fallidos al cierre:** 0. **Errores al cierre:** 0. **Omitidos:** 0. `git diff --check` no informó errores de espacios; mostró únicamente avisos de conversión LF/CRLF de Git.

## Incidentes

### INC-301 — Base H2 compartida entre clases de prueba

**Contexto:** la prueba pública pasó al ejecutarse sola, pero falló en la primera ejecución junto a la regresión.  
**Test/comportamiento afectado:** dos casos de `PublicProductHttpTest` al insertar fotos de prueba.  
**Esperado:** inserciones válidas y respuestas públicas comprobables.  
**Obtenido:** `DataIntegrityViolationException` sobre `ck_product_images_status`; la causa anidada indicaba que la base H2 había sido cerrada. En esa ejecución hubo 30 tests, 2 errores.  
**Clasificación:** defecto del test, por aislamiento del entorno de pruebas.  
**Causa:** la clase nueva usaba el nombre H2 compartido `starterdbtest`; otra clase del conjunto cerró ese contexto al finalizar.  
**Resolución:** asignar a `PublicProductHttpTest` una base H2 propia mediante `@TestPropertySource` y repetir el mismo conjunto.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS, 30/30.  
**Riesgo o aprendizaje derivado:** las pruebas Spring con `@DirtiesContext` que comparten base H2 en memoria deben aislar el nombre de la base al ejecutarse juntas.

El primer intento local de Maven no arrancó porque `mvn` y `JAVA_HOME` no estaban configurados en esta consola; se usó el wrapper del proyecto con el JDK 21 instalado. La primera ejecución dentro del sandbox quedó detenida al adjuntar Mockito; se repitió fuera del sandbox, conforme al comportamiento observado en tandas anteriores. Ninguno de esos intentos cambió el contrato ni los tests.

## Tests faltantes y riesgos residuales

- **MySQL 8.4 para la consulta pública nueva:** no se ejecutó esta consulta contra MySQL real o Testcontainers en esta tanda. Motivo: la verificación enfocada cubrió JPQL y respuestas con H2 y la tanda evita una nueva infraestructura de pruebas. Riesgo residual: una diferencia específica de dialecto podría aparecer en MySQL. Impacto potencial: fallo del listado o detalle público. Recomendación: incluir ambos GET en el recorrido integral de staging de la tanda 5.
- **Entrega real de esas URLs por CloudFront:** no se hizo GET de objetos reales mediante los endpoints públicos nuevos. Motivo: la tanda 3 valida el contrato y la composición de URL; el recorrido integral está previsto en la tanda 5. Riesgo residual: una configuración de entorno o clave incorrecta puede producir una imagen no disponible. Impacto potencial: miniatura o detalle roto. Recomendación: validar respuestas públicas y objetos CDN en staging en la tanda 5.
- **Apariencia de tarjeta y detalle públicos:** no existen pantallas públicas en el alcance de estas tarjetas. Riesgo: futuros requisitos visuales pueden pedir otro dato o tamaño. Impacto: ajuste del contrato o interfaz futura. Recomendación: revisarlo cuando se planifiquen esas pantallas.

**Decisiones pendientes descubiertas:** ninguna. No se observaron fallos productivos en la regresión final.

## Archivos modificados

- Nuevos: `PublicProductController`, `PublicProductService`, `PublicProductSummaryResponse`, `PublicProductDetailResponse`, `PublicProductPageResponse`, `ProductImageUrlService` y `PublicProductHttpTest`.
- Modificados: `ProductRepository`, `ProductImageRepository`, `ProductImageService`, `SecurityConfig`, el resultado de la tanda 3 en `PLAN_IMPLEMENTACION_IMAGENES.md` y este documento.

## Criterio de cierre

- [x] Lectura pública validada para activos, inactivos y productos sin fotos.
- [x] URLs CDN y consulta por lote del listado paginado comprobadas.
- [x] GET sin sesión y mutaciones públicas denegadas; contratos administrativos en regresión.
- [x] Tests, incidente y riesgos residuales registrados.

Antes de aprobar la tanda 4, revisar el contrato JSON público y tener presente que el recorrido con MySQL/S3/CloudFront reales queda para la tanda 5. La implementación se detiene aquí.
