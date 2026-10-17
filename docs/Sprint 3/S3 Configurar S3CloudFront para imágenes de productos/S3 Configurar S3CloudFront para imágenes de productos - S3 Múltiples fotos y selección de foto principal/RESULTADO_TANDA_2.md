# Resultado de ejecución — Tanda 2

**Fecha:** 17/10/2026  
**Estado:** COMPLETA

## Alcance y cambios realizados

- Se añadieron `PUT /api/catalog/products/{productId}/images/{imageId}/primary`, `PUT /order` con `{ "imageIds": [...] }`, `PUT /{imageId}` multipart con campo `file` y `DELETE /{imageId}`. Conservan autenticación, roles `SUPER_ADMIN`/`ADMIN`/`USER`, CSRF y formato de errores. Orden exige exactamente todos los IDs publicados; borrado devuelve 204.
- El servicio bloquea el producto para serializar cambios. Principal y orden solo afectan MySQL. Al borrar la principal promueve la primera restante según el orden actual. Un reemplazo pendiente sobre la foto provoca 409 en un segundo cambio.
- Reemplazo reserva una intención persistente, sube dos WebP con claves de revisión nuevas y después publica las dos claves en una transacción. La foto anterior sigue publicada ante fallo de subida. Las claves sustituidas quedan programadas para borrar 25 horas después.
- El borrado retira la foto de la API y registra la eliminación de ambas claves en la misma transacción. `ImageStorageCleanupJob` procesa operaciones vencidas cada cinco minutos, recupera cargas/reemplazos interrumpidos después de una hora, reintenta sin límite de intentos con demora creciente hasta una hora y registra pendientes/fallos en logs. Las llamadas individuales a S3 tienen timeout total de dos minutos. La espera de recuperación configurable exige al menos diez minutos.
- No se modificaron pantallas ni lectura pública. No se requirió una migración: V9 ya contiene estados, intentos y fecha de ejecución.

**Diferencias respecto del plan:** ninguna funcional. La verificación real se completó con MySQL 8.4 en Testcontainers y con el MySQL/S3/CloudFront de staging, usando la configuración de IntelliJ sin registrar secretos.

## Tests ejecutados

| Categoría | Pruebas | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| Integración local | `ProductImageFlowTest` (10) | Orden completo, principal, promoción, reemplazo, retención y borrado posterior de claves antiguas, fallos, limpieza de carga abandonada, reintento e idempotencia | 10 PASS |
| Concurrencia | Incluidas en `ProductImageFlowTest` | Dos cambios de principal simultáneos mantienen una principal; un reemplazo pendiente rechaza otro cambio | PASS |
| HTTP y seguridad | `ProductImageControllerTest` (4) | Contratos, roles, sesión, CSRF, orden inválido y borrado 204 | 4 PASS |
| Procesamiento, S3 y configuración | `ImageProcessorTest` (4), `S3ImageStorageTest` (1), `ProductImageConfigurationTest` (3) | Variantes WebP, encabezados S3 y configuración | 8 PASS |
| Regresión de productos | `ProductControllerTest` (10), `ProductListingTest` (3), `ProductServiceTest` (10), `ProductPersistenceIntegrationTest` (1) | Contratos previos de productos | 24 PASS |
| MySQL 8.4 | `ProductImageMySqlIT` (2), MySQL en Testcontainers y S3 simulado | Reserva concurrente del último cupo; principal, orden, reemplazo, concurrencia, borrado y limpieza en MySQL | 2 PASS |
| Staging real | `ProductImageLifecycleStagingIT` (1), MySQL, S3 y CloudFront reales | API autenticada/CSRF; orden, principal, reemplazo, variantes WebP servidas por CDN, borrado visible y limpieza S3 | 1 PASS |
| Regresión general sin Docker | `mvnw test` | Suite completa disponible en ese estado del entorno | Sin fallos ni errores tras aislar fixtures H2; tests Docker omitidos |
| Regresión general con Docker | `mvnw test` | Suite completa con Testcontainers | Repetición final: 602 tests, 0 fallos, 0 errores y 0 omitidos; primera corrida e INC-206 documentados |
| Subcategorías y usuarios aislados | `SubcategoryControllerTest`, `AdminUserProvisioningIntegrationTest`, tres clases de email/desactivación/reactivación | Aislamiento de fixtures H2 compartidos | PASS |
| Construcción | `mvnw -DskipTests package`, `git diff --check` | Compilación y revisión de espacios | PASS |

Las pruebas enfocadas de imágenes y productos finalizaron sin fallos. Los tests Spring con Mockito se ejecutaron fuera del sandbox, como en la tanda 1. La prueba de staging se repitió y confirmó la galería vacía y las operaciones de eliminación completadas.

## Incidentes

### INC-201 — Mapper inexistente en el fixture HTTP

**Contexto:** primera ejecución de `ProductImageControllerTest` tras añadir pruebas HTTP.  
**Test/comportamiento afectado:** inyección del fixture de lectura de IDs de respuesta.  
**Esperado:** crear el test y ejecutar las solicitudes.  
**Obtenido:** Spring no encontró un bean `com.fasterxml.jackson.databind.ObjectMapper`.  
**Clasificación:** defecto del test.  
**Causa:** el fixture asumía que Spring Boot 4 exponía ese tipo como bean.  
**Resolución:** el test crea su mapper local; no cambió el contrato HTTP.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** no depender de beans de serialización que la aplicación no registra.

### INC-202 — Entidad desactualizada sobrescribía el contador de reintentos

**Contexto:** primera ejecución del test de fallo temporal de borrado.  
**Test/comportamiento afectado:** `failedReplacementKeepsPublishedUrlsAndCleanupRetries`.  
**Esperado:** conservar `attempts=1` tras el reintento exitoso.  
**Obtenido:** el test observó `attempts=0`.  
**Clasificación:** defecto del test.  
**Causa:** el fixture guardó una entidad obtenida antes de que el trabajador actualizara el contador y sobrescribió su valor.  
**Resolución:** recargar la operación antes de adelantar `nextExecutionAt`.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** los fixtures de reintento deben usar el estado persistido más reciente.

### INC-203 — Regresión general con base H2 compartida

**Contexto:** ejecución de la suite completa y repetición enfocada que incluía subcategorías.  
**Test/comportamiento afectado:** `SubcategoryControllerTest.clean`, antes de cuatro tests.  
**Esperado:** borrar fixtures de subcategorías/categorías.  
**Obtenido:** FK de `products` impidió el borrado porque otros tests dejaron productos en `starterdbtest`.  
**Clasificación:** defecto de aislamiento de tests preexistentes.  
**Causa:** `SubcategoryControllerTest` comparte H2 con tests de productos y su limpieza no elimina productos dependientes; los tests nuevos de imágenes usan bases H2 propias. El test de subcategorías aislado pasó.  
**Resolución:** se asignó una base H2 propia a `SubcategoryControllerTest`, sin alterar sus expectativas. La suite sin Docker pasó después.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS para subcategorías y para la suite sin Docker.  
**Riesgo o aprendizaje derivado:** los fixtures compartidos pueden depender del orden de ejecución.

### INC-204 — Fixture de provisión de usuarios en H2 compartida

**Contexto:** regresión general tras resolver INC-203.  
**Test/comportamiento afectado:** diez casos de `AdminUserProvisioningIntegrationTest`, que verifican provisión y CSRF.  
**Esperado:** respuestas de seguridad y provisión según el contrato existente.  
**Obtenido:** las pruebas fallaban en la suite compartida y pasaban por separado.  
**Clasificación:** defecto de aislamiento del test.  
**Causa:** reutilización del mismo esquema/estado H2 por contextos de integración diferentes.  
**Resolución:** asignar un nombre de base H2 propio a esa clase; la suite sin Docker pasó después.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.

### INC-205 — Aserción incorrecta en la primera verificación de staging

**Contexto:** primera ejecución del ciclo real sobre las fotos 1 y 2 del producto 5.  
**Test/comportamiento afectado:** comprobación final de operaciones `COMPLETE` luego de borrar por API y ejecutar la limpieza.  
**Esperado:** dos operaciones de eliminación completadas.  
**Obtenido:** el conteo devolvió cuatro porque incluía también las operaciones de carga originales, ya `COMPLETE`.  
**Clasificación:** defecto del test.  
**Causa:** el filtro identificaba claves y estado, pero no las operaciones de eliminación recién reclamadas.  
**Resolución:** capturar los ID de las dos operaciones `CLEANUP_REQUIRED` vencidas antes del trabajador y verificar su estado final; se agregó una rama de auditoría para repetir la prueba con la galería ya vacía.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS. La API dejó la galería vacía y el trabajador completó ambas eliminaciones.

### INC-206 — Base H2 cerrada en la regresión general con Docker

**Contexto:** suite completa ejecutada con Docker disponible el 17/10/2026.  
**Test/comportamiento afectado:** 12 casos de `AdminUserDeactivationTest`, `AdminUserEmailIntegrationTest` y `AdminUserReactivationTest`, que verifican cambios administrativos con desafíos MFA.  
**Esperado:** insertar desafíos válidos y completar las pruebas de usuarios.  
**Obtenido:** `ck_auth_challenges_origin` falló; la causa anidada de H2 indica `The database has been closed`. Los otros casos de esas clases pasaron.  
**Clasificación:** defecto de aislamiento/configuración de tests.  
**Causa:** reutilización de `starterdbtest` por contextos Spring de la suite; al aislar esas bases desapareció el error.  
**Resolución:** se asignó una base H2 propia a cada clase; las tres pasaron juntas y la suite completa terminó con 0 errores.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.

## Tests faltantes y riesgos residuales

- **Claves sustituidas en staging:** el reemplazo de la foto 2 programó las claves anteriores para borrar después de 25 horas. El test verificó la programación; su ejecución futura todavía no ha ocurrido. La eliminación vencida y repetible sí se probó con S3 real para las fotos borradas y con S3 simulado para las claves antiguas.
- **Caché CloudFront:** una URL conocida puede seguir sirviendo contenido hasta expirar, conforme al plan. No se prueba retiro instantáneo ni se hacen invalidaciones.
- **Regresión general:** la repetición final con Docker pasó. Las pruebas explícitas `*IT` de MySQL de imágenes y staging se ejecutaron además por separado; no forman parte del patrón predeterminado de Surefire.

**Decisiones pendientes descubiertas:** ninguna decisión funcional nueva.

## Archivos modificados

- Controlador, servicio, repositorio de operaciones, nuevo DTO de orden y nuevo `ImageStorageCleanupJob` en `catalog/product/image`.
- Configuración de S3, `AppProperties` y `application.properties` para timeout, frecuencia y reintentos.
- Tests `ProductImageFlowTest` y `ProductImageControllerTest`.
- Tests `ProductImageMySqlIT` y `ProductImageLifecycleStagingIT`; aislamiento H2 de `SubcategoryControllerTest` y cuatro clases de usuarios.
- Este resultado y la sección de ejecución de la tanda 2 del plan vivo.

## Cierre de la tanda

- [x] Principal, orden, reemplazo y borrado verificados en integración local y HTTP.
- [x] Recuperación, reintentos y observabilidad verificados con S3 simulado.
- [x] Tests, incidentes y riesgos residuales registrados.
- [x] Ciclo y concurrencia verificados en MySQL 8.4; reemplazo, borrado y limpieza verificados en S3/CloudFront de staging.
- [x] Fotos de prueba del producto 5 eliminadas y limpieza S3 de sus claves vigentes confirmada.
- [x] Regresión general con Docker sin errores de fixture: 602 tests PASS.

**Cierre:** COMPLETA. Se cumplen los tres criterios de cierre del plan. La limpieza programada para 25 horas de las claves sustituidas sigue pendiente de su vencimiento normal; se verificó su programación y el mecanismo de ejecución. No se inicia la tanda 3.
