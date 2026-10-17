# Resultado de ejecución — Tanda 5

**Fecha:** 17/10/2026  
**Estado:** PARCIAL

## Alcance y resultado

Se validó el ciclo administrativo y público vigente **sin reemplazo** contra MySQL 8.4, S3 y CloudFront de staging. El test de staging existente dejó de llamar al endpoint retirado y ahora prueba cinco cargas, cupo, principal, orden, borrado, promoción, posiciones compactas y carga opcional. No se cambió código productivo, esquema ni AWS.

La validación real se hizo por API autenticada con MockMvc y recursos de staging; **no se recorrió el modal en un navegador real**. Los tests del componente y de la pantalla cubren su interacción en jsdom. Tampoco existe aún una VPS para verificar su contenedor. Estos faltantes impiden declarar la tanda completa según el criterio acordado.

## Evidencia del recorrido en staging

- Producto 5: se confirmó el nombre de producto de prueba, estado activo y galería inicial vacía. Se generaron seis fotos de prueba (IDs 12–17); cinco simultáneas dieron posiciones 1–5 y la sexta solicitud fue rechazada con HTTP 409.
- Para cada foto cargada, CloudFront respondió HTTP 200 y `image/webp` para miniatura de **400 px** y detalle de **1200 px**. La URL S3 directa de ambas variantes respondió HTTP 403.
- Se eligió la foto 14 como principal; el detalle público devolvió la misma galería que la API administrativa y el listado público mostró su miniatura. Tras ordenar, el orden fue 16, 12, 13, 14, 15 con una sola principal. Al borrar la principal 14, el orden quedó compacto y la primera restante, 16, pasó a principal también en la respuesta pública.
- La galería pudo permanecer en cuatro fotos y aceptar una carga opcional. Al terminar, la API volvió a galería vacía. El trabajador marcó completas las seis operaciones de borrado de esta prueba; la consola S3 ya no mostró prefijos de los IDs 12–17 bajo `products/5/images/`.
- No hay una entidad ni endpoint de variante de producto en este módulo: la galería se consulta por `productId` y no por característica o selección de variante. Esa propiedad se verificó en el contrato/código, no mediante una pantalla de variantes.
- El trabajador informó **una operación pendiente adicional**. La consola S3 muestra únicamente un prefijo de la antigua foto 2 bajo el producto 5; corresponde al ciclo histórico de reemplazo documentado en la Tanda 2. No se eliminó manualmente ni se atribuyó su fecha de ejecución sin consultar la fila de operación.

## Estado operativo por entorno

| Comprobación | Staging | Producción |
| --- | --- | --- |
| S3 | Bucket `glgcorralon-product-images-staging-121026`; etiquetas `Project=GLGCorralon`, `Environment=staging`; SSE-S3 | Bucket `glgcorralon-product-images-prod-131026`; etiquetas `Project=GLGCorralon`, `Environment=prod`; SSE-S3 |
| CloudFront | Distribución `E2ARNBAE5EGW9V`, dominio `d296ipo7jtz4ug.cloudfront.net`, habilitada, plan Free | Distribución `E5FOY3R85C9QT`, dominio `d2q2545tsijue5.cloudfront.net`, habilitada, plan Free |
| IAM técnico | `glgcorralon-images-staging`: una clave activa, con uso reciente en S3; la carga real comprobó `PutObject` y la limpieza `DeleteObject` | `glgcorralon-images-prod`: cero claves de acceso; aceptado hasta preparar el despliegue |
| Presupuesto | S3 y CloudFront: USD 10 mensual cada uno, filtros por servicio, `Project` y `Environment=staging` | S3 y CloudFront: USD 10 mensual cada uno, filtros por servicio, `Project` y `Environment=prod` |

La consola mostró **cuatro presupuestos en buen estado y USD 0,00 utilizados**. Los dos de S3 filtran `S3 (Simple Storage Service)`; los de CloudFront filtran `CloudFront Flat-Rate Plans`. Billing mostró `Project` y `Environment` como etiquetas activas de asignación de costos. Cada presupuesto muestra tres alertas asociadas a un destinatario `Active (legacy)`; la consola no demuestra que haya llegado un correo. Con USD 0,00 tampoco hay cargos atribuidos que permitan comprobar la separación real entre entornos.

Ambas distribuciones figuran en el plan **Free**. Según la [documentación vigente de AWS](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/flat-rate-pricing-plan.html), ese plan tiene una asignación mensual de 1 millón de solicitudes y 100 GB de transferencia; las asignaciones son referencia de uso, no un límite rígido. Revisar el uso y la factura reales antes y después de publicar producción. El presupuesto filtrado por `CloudFront Flat-Rate Plans` corresponde al servicio de cargos de planes de tarifa plana según [AWS PricingPlanManager](https://docs.aws.amazon.com/PricingPlanManager/latest/UserGuide/monitoring-costs.html); su eficacia con cargo USD 0,00 todavía no pudo observarse.

## Operación mínima

- **Configuración:** `URL_DB`, `USERNAME_DB`, `PASSWORD_DB`; `IMAGE_S3_BUCKET`, `IMAGE_CLOUDFRONT_BASE_URL`, `AWS_REGION=us-east-1`; credenciales AWS por la cadena estándar del SDK. Para la ejecución local de staging se usaron las variables ignoradas por Git de IntelliJ, sin copiar ni imprimir secretos. La configuración de producción requiere credenciales técnicas al desplegar.
- **Límites y caché:** por defecto 10 MB y 20 megapíxeles por entrada, miniatura 400 px, detalle 1200 px, máximo cinco fotos. `IMAGE_CACHE_TTL=24h`; CloudFront puede conservar una URL antigua hasta expirar aunque S3 ya haya borrado el objeto.
- **Limpieza:** `IMAGE_CLEANUP_INTERVAL_MS=300000` (5 min), `IMAGE_CLEANUP_BATCH_SIZE=50`, `IMAGE_CLEANUP_RETRY_BASE=1m`, `IMAGE_CLEANUP_RETRY_MAX=1h`, `IMAGE_ABANDONED_UPLOAD_GRACE=1h`. Consultar filas `PENDING_UPLOAD` y `CLEANUP_REQUIRED`, `attempts` y `next_execution_at`; no borrar objetos a mano mientras haya operaciones persistidas.
- **Señales:** seguir `image_cleanup_pending`, `image_cleanup_retry`, `image_cleanup_transaction_failed` y `image_cleanup_complete` en logs. Revisar almacenamiento y operaciones S3, solicitudes y transferencia CloudFront, uso del plan Free, filtros y costos de los cuatro presupuestos. Registrar recepción efectiva de una alerta cuando ocurra.
- **Seguridad:** no se encontraron variables `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY` en fuente o build del frontend. El backend de imágenes usa memoria y el SDK S3, sin escritura local de fotos en el código inspeccionado. La ausencia de archivos en un contenedor desplegado queda sin comprobación hasta disponer de la VPS.

## Tests ejecutados

| Grupo | Qué demuestra | Resultado |
| --- | --- | --- |
| Backend enfocado: `ProductImageFlowTest`, `ProductImageControllerTest`, `PublicProductHttpTest`, `ImageProcessorTest`, `S3ImageStorageTest` | Borrado con reintento ante fallo S3 simulado, cupo, principal, orden, contratos HTTP y seguridad pública, procesamiento y almacenamiento | **21 PASS**, 0 fallos, 0 errores |
| MySQL 8.4 en Testcontainers: `ProductImageMySqlIT` | Principal/posiciones y límite concurrente con MySQL real | **2 PASS**, 0 fallos, 0 errores |
| Staging real: `ProductImageLifecycleStagingIT` | Recorrido y limpieza descritos arriba, CDN WebP y S3 directo denegado | **1 PASS**, 0 fallos, 0 errores |
| Frontend completo en serie | Modal sin reemplazo, borrado y carga opcional, producto correcto, cliente HTTP, CSRF y regresión general | **265 PASS** en 28 archivos |
| Calidad | `npm run lint`, `npm run build`, `git diff --check` | PASS; Git avisó conversión LF/CRLF sin error |

## Incidentes

### INC-502 — MySQL inaccesible desde el sandbox

**Contexto:** primer intento de `ProductImageLifecycleStagingIT`.  
**Test/comportamiento afectado:** arranque Spring para recorrer staging.  
**Esperado:** conectar con MySQL y ejecutar el recorrido.  
**Obtenido:** fallo de comunicación JDBC antes de ejecutar el test.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** restricción de red del sandbox, ya observada en tandas anteriores.  
**Resolución:** repetir la misma prueba fuera del sandbox con la configuración local de IntelliJ.  
**Código productivo modificado:** no. **Tests modificados por el incidente:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS, 1/1.  
**Riesgo o aprendizaje derivado:** la ejecución con recursos locales de staging requiere acceso de red fuera del sandbox.

### INC-503 — Testcontainers sin acceso al pipe de Docker

**Contexto:** primer intento de `ProductImageMySqlIT`.  
**Test/comportamiento afectado:** regresión de persistencia y concurrencia MySQL.  
**Esperado:** iniciar MySQL 8.4 en Docker.  
**Obtenido:** `AccessDeniedException` sobre `docker_engine` antes de los casos.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** pipe de Docker restringido dentro del sandbox.  
**Resolución:** repetir sin cambiar el test, fuera del sandbox.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS, 2/2.  
**Riesgo o aprendizaje derivado:** Testcontainers necesita acceso al motor Docker.

## Pruebas faltantes y riesgos residuales

| Faltante | Motivo, impacto y recomendación |
| --- | --- |
| Modal en navegador real contra staging | No había backend ni frontend locales corriendo ni una sesión de navegador autenticada para el recorrido manual. El test jsdom y la API real cubren las capas por separado, pero no el enlace visual completo. Ejecutar un recorrido corto con una sesión administrativa antes de cerrar la tanda. |
| Operación histórica pendiente | El trabajador informó una operación ajena a las seis nuevas; S3 aún muestra el prefijo de la foto histórica 2. Revisar su fila, fecha `next_execution_at`, intentos y posterior log `image_cleanup_complete`; no asumir fallo antes de su vencimiento. |
| Costos atribuidos y correo de alertas | Los cuatro presupuestos siguen en USD 0,00; no hubo umbral observado. Revisar Cost Explorer cuando haya cargos y comprobar en el buzón la primera alerta real. Un filtro configurado no prueba por sí solo atribución ni entrega. |
| Producción y VPS | No existe despliegue ni clave técnica de producción; no se probaron transferencia/costo reales, compatibilidad WebP de esa VPS ni ausencia de archivos en su contenedor. Validar al preparar el despliegue, sin adelantarlo en esta tanda. |
| Variantes de producto y accesibilidad real | La galería está ligada al producto en código, pero no hay flujo de variantes ni lector de pantalla/navegador real para ensayar. Revisar al incorporar esas pantallas. |

No se probó retiro instantáneo de URLs antiguas ya cacheadas por CloudFront; el diseño vigente acepta su expiración natural.

## Diferencias y cierre

**Diferencias respecto del plan:** el test manual histórico se adaptó al contrato sin reemplazo. La validación por navegador real y la comprobación de costos/alertas efectivos permanecen pendientes por evidencia no disponible. No hubo decisión nueva de arquitectura ni cambio de contrato.

**Archivos modificados:** `ProductImageLifecycleStagingIT.java`, este resultado y el apartado «Resultado de ejecución» de Tanda 5 en `PLAN_IMPLEMENTACION_IMAGENES.md`.

- [x] Ciclo por API autenticada y lectura pública demostrado con MySQL/S3/CloudFront de staging.
- [x] Seis borrados de prueba completados y objetos nuevos ausentes del prefijo S3.
- [x] Configuración visible de ambos entornos y cuatro presupuestos revisada; faltantes operativos registrados.
- [x] Regresión enfocada, MySQL, frontend, lint y build exitosos; incidentes registrados.
- [ ] Modal recorrido en navegador real contra staging.
- [ ] Atribución de cargos y recepción de alertas comprobadas cuando exista evidencia.
- [ ] Operación histórica pendiente confirmada como completa.

**Revisión humana antes de aprobar el cierre:** recorrer el modal con sesión administrativa, verificar la operación histórica cuando venza y comprobar atribución/correo cuando AWS muestre cargos o dispare un umbral. No iniciar otra tanda.
