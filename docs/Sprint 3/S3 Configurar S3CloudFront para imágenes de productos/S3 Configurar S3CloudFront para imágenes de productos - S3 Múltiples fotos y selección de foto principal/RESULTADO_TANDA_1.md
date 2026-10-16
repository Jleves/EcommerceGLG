# Resultado de ejecución — Tanda 1

**Fecha:** 14/10/2026  
**Estado:** COMPLETA (cierre de la prueba Linux el 16/10/2026)

## Alcance y cambios realizados

- Se añadió `V9__create_product_images.sql` con `product_images`, `image_storage_operations`, índices y una columna generada con índice único que impide dos fotos principales publicadas por producto. Las claves S3, la revisión, el estado pendiente/publicado y la intención de almacenamiento quedan en MySQL.
- Se implementaron `POST /api/catalog/products/{id}/images` y `GET /api/catalog/products/{id}/images` para los roles `SUPER_ADMIN`, `ADMIN` y `USER`, sujetos a sesión y CSRF. El POST usa campo multipart `file`, responde 201 con `id`, `position`, `primary`, `thumbnailUrl` y `detailUrl`; el GET devuelve solo fotos publicadas. Archivo inválido responde 400, cupo agotado 409 y fallo S3 503.
- La carga valida contenido JPEG, PNG o WebP fijo, tamaño de hasta 10 MiB y hasta 20 millones de píxeles antes de decodificar; aplica orientación EXIF, conserva proporciones y transparencia, no amplía imágenes pequeñas y genera dos WebP sin metadatos de origen. Se incorporó `scrimage-webp` 4.6.7 con calidad 80. Un fallo del codificador se distingue de un archivo inválido y responde 503.
- El servicio bloquea el producto y reserva un cupo en una transacción antes de subir. S3 recibe claves de revisión nuevas `products/{productId}/images/{imageId}/{revision}/thumb.webp` y `detail.webp`, `Content-Type: image/webp` y `Cache-Control: public, max-age=86400`. Otra transacción publica la foto solo tras las dos subidas; la primera publicada queda principal. Si S3 falla, la foto no aparece y la operación conserva las claves para limpieza en la tanda 2.
- No se añadieron principal/orden/reemplazo/borrado, trabajador de limpieza, frontend ni lectura pública. La consulta administrativa se incluyó aquí porque permite verificar la publicación y es dependencia directa del modal futuro.

**Diferencias respecto del plan:** se implementó el GET administrativo, definido en el contrato transversal pero no asignado explícitamente en el mapa de la tanda. La prueba Linux se completó el 16/10/2026 con Java 21 en WSL.

## Verificación de staging

- MySQL 8.4 local aplicó `V9` desde la versión 8 y Hibernate validó el nuevo modelo.
- Se creó el producto de prueba **ID 5**, identificado como «Tanda 1 prueba imagenes …». Quedaron **dos fotos de prueba**, IDs **1 y 2**, tras repetir la verificación con sesión real. Permanecerán allí hasta que exista borrado administrativo en la tanda 2.
- El segundo POST usó una cookie de sesión real del `SUPER_ADMIN` existente, creada para la prueba y revocada al finalizar. Respondió **201**; el GET administrativo devolvió la galería. Las dos variantes de la foto se descargaron por CloudFront con `image/webp`, mientras la URL directa del objeto S3 devolvió **403**.
- La prueba de concurrencia sobre MySQL 8.4, con S3 simulado y datos temporales eliminados al terminar, confirmó que dos cargas simultáneas por el último cupo publican solo una foto y dejan una sola principal.
- No se guardaron ni imprimieron access keys. El frontend no se modificó y no recibe credenciales AWS.

## Tests ejecutados

| Categoría | Pruebas | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| Unitarios de procesamiento | `ImageProcessorTest` (4) | JPEG, PNG y WebP; 400/1200 px, no ampliación, EXIF, transparencia, salida sin EXIF; corrupción, animación, tamaño y píxeles rechazados | 4 PASS |
| Unidad de S3 | `S3ImageStorageTest` (1) | Bucket configurado, `image/webp` y caché de 86.400 s en `PutObject` | 1 PASS |
| Integración local | `ProductImageFlowTest` (4) | Reserva, dos claves, primera principal, fallo de la segunda subida sin publicación, límite de cinco y concurrencia | 4 PASS |
| HTTP y seguridad | `ProductImageControllerTest` (2) | 401 sin sesión, 403 sin rol/CSRF, roles admitidos, GET, 400/409/503 | 2 PASS |
| MySQL real | `ProductImageMySqlIT` (1) | Dos cargas compiten por el quinto lugar; una sola se publica | 1 PASS |
| Staging real | `ProductImageStagingIT` (1) | Sesión real, carga S3, lectura CloudFront de ambas variantes y 403 S3 directo | 1 PASS |
| Configuración y regresión | `ProductImageConfigurationTest` (3), `ProductControllerTest` (10), `ProductListingTest` (3), `ProductServiceTest` (10), `ProductPersistenceIntegrationTest` (1), `SpringStarterApplicationTests` (1) | Arranque y contratos de producto existentes con V9 aplicada | 28 PASS |

**Total de los reportes Surefire:** 41 tests exitosos, 0 fallidos, 0 errores y 0 omitidos. `mvnw -DskipTests package` y `git diff --check` terminaron sin errores. Los tests que activan Mockito se ejecutaron fuera del sandbox por su restricción de conexión a Java.

**Linux:** el JAR de `scrimage-webp` contiene binarios `cwebp` y `dwebp` para Linux x86_64 y aarch64. La verificación inicial extrajo y ejecutó `cwebp` x86_64 en WSL (`1.4.0`) para convertir un PPM. El 16/10/2026 se ejecutó el JAR Spring completo con Temurin 21.0.12.1 Alpine x64 en la distribución Linux de WSL, perfil `test` y puerto aleatorio: Spring arrancó en 17,649 s y Flyway aplicó V1–V9. Un smoke test temporal invocó `ImageProcessor` mediante `PropertiesLauncher` del mismo JAR, con las dependencias WebP anidadas; Scrimage extrajo `dwebp` y `cwebp` a `/tmp` y generó WebP válidos de 400 × 200 (288 bytes) y 1200 × 600 (1464 bytes). Ambas ejecuciones terminaron sin error. No se requirió Docker Desktop ni se agregaron archivos productivos o tests permanentes.

## Incidentes

### INC-101 — Sintaxis de columna generada en H2

**Contexto:** primer arranque de integración tras añadir V9.  
**Test/comportamiento afectado:** migración de `product_images`.  
**Esperado:** Flyway aplica V9 en H2 y MySQL.  
**Obtenido:** H2 rechazó `STORED` con SQLState 42001.  
**Clasificación:** configuración/compatibilidad de migración.  
**Causa:** H2 2.4 no admite esa forma de la cláusula; MySQL admite omitirla.  
**Resolución:** se quitó `STORED`, conservando la columna generada y su índice único; V9 pasó en H2 y MySQL 8.4.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** sí.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** validar DDL de columnas generadas en ambos motores.

### INC-102 — Mockito no pudo conectarse a Java dentro del sandbox

**Contexto:** primera repetición de los tests Spring.  
**Test/comportamiento afectado:** integración con contexto Spring.  
**Esperado:** finalizar Surefire.  
**Obtenido:** `could not open (5) pipe ... javatool`; el proceso quedó sin resultado.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** restricción de conexión de Mockito en el sandbox, ya observada en la tanda 0.  
**Resolución:** se repitieron las mismas pruebas fuera del sandbox.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** los tests Spring con Mockito requieren ese modo de ejecución en este equipo.

### INC-103 — Fixture de producto contaminado por fotos de otro test

**Contexto:** regresión conjunta de imágenes y `ProductControllerTest`.  
**Test/comportamiento afectado:** limpieza de productos antes de los tests de controlador.  
**Esperado:** borrar productos del fixture.  
**Obtenido:** la nueva FK `product_images.product_id` impidió borrar una foto creada en el mismo H2 compartido.  
**Clasificación:** defecto de aislamiento del test nuevo.  
**Causa:** los tests nuevos compartían la base H2 de regresión.  
**Resolución:** se asignaron bases H2 aisladas a `ProductImageFlowTest` y `ProductImageControllerTest`; no se cambió el test ni la expectativa existente.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS, regresión conjunta completa.  
**Riesgo o aprendizaje derivado:** los tests que crean dependencias nuevas deben aislar sus datos de fixtures globales antiguos.

### INC-104 — Prueba JVM Linux inicialmente no disponible

**Contexto:** verificación del codificador empaquetado.  
**Test/comportamiento afectado:** conversión dentro del JAR Spring ejecutado en Linux.  
**Esperado:** arranque y conversión WebP con Java 21 Linux.  
**Obtenido:** Docker Desktop no inició; WSL disponible no contiene Java.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** falta de un runtime Java Linux utilizable en este equipo.  
**Resolución:** inicialmente se verificó que el artefacto incluye el binario Linux y se ejecutó una conversión real con él en WSL. Un PNG de muestra inicial estaba corrupto y un PPM de texto no era aceptado; se corrigió el dato de prueba a PPM binario. El 16/10/2026 se incorporó temporalmente un JDK 21 Alpine a `target`, se arrancó el JAR Spring completo en WSL y se ejecutó un smoke test de las dos variantes WebP mediante el `PropertiesLauncher` del propio JAR.
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS para arranque del JAR completo y conversión WebP con el cargador Spring Boot y Java 21 Linux.
**Riesgo o aprendizaje derivado:** el JDK Linux estándar no arrancó en la distribución mínima de Docker Desktop porque carecía de glibc; Temurin 21 Alpine resolvió la incompatibilidad del entorno de prueba. El despliegue en la futura VPS deberá validar su propio entorno.

## Riesgos residuales

- **Caída abrupta entre S3 y MySQL:** la intención persiste, pero aún no existe el trabajador que recupere una fila `PENDING` ni borre claves `CLEANUP_REQUIRED`. Riesgo: objetos huérfanos y cupos reservados tras una caída; el flujo de errores S3 controlados sí se probó. El trabajador y sus reintentos corresponden a la tanda 2; no adelantar esa implementación.
- **Staging conserva datos:** producto 5 y fotos 1–2 siguen visibles en la galería administrativa y en CloudFront. Eliminar sus objetos y registros mediante el ciclo de vida de la tanda 2 cuando se apruebe esa tanda.
- **Entorno de despliegue:** el smoke test se ejecutó en Linux x86_64 Alpine dentro de WSL. Repetir un smoke test al preparar la futura VPS para detectar diferencias propias de su distribución y permisos.

## Decisiones pendientes descubiertas

Ninguna decisión arquitectónica nueva. La prueba de empaquetado con JVM Linux quedó completada.

## Archivos modificados

- `Backend/spring-starter/pom.xml`, `application.properties` y `V9__create_product_images.sql`.
- Nuevos paquetes `catalog/product/image/{controller,dto,model,repository,service,storage}` y ampliación de `GlobalExceptionHandler` / `ApiErrorCode`.
- Tests nuevos en `src/test/java/com/ashenox/starter/catalog/product/image`; no se modificaron tests anteriores.
- Este documento y la sección «Resultado de ejecución» de la tanda 1 en el plan vivo.

## Cierre de la tanda

- [x] Carga autenticada de staging con ambas variantes entregadas por CloudFront.
- [x] Lectura S3 directa denegada y sin credenciales en frontend.
- [x] Tests, fallos investigados e incompatibilidades registrados.
- [x] Codificador nativo empaquetado probado en Linux x86_64.
- [x] JAR Spring completo ejecutado con Java 21 Linux y conversión WebP desde sus dependencias anidadas.

**Cierre:** los criterios de la tanda 1 quedaron verificados. Los riesgos residuales de limpieza recuperable pertenecen a la tanda 2; la prueba en la futura VPS corresponde a la preparación del despliegue.
