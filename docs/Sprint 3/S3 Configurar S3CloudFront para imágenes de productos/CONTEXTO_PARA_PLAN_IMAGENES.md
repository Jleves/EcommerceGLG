# Contexto para planificar imágenes de productos (Sprint 3)

**Fecha del traspaso:** 13/10/2026.  
**Propósito:** continuar en otro chat con un plan de implementación por tandas para estas dos tarjetas de Trello:

1. [S3 Configurar S3/CloudFront para imágenes de productos](https://trello.com/c/PjBAprJa/4-s3-configurar-s3-cloudfront-para-im%C3%A1genes-de-productos).
2. [S3 Múltiples fotos y selección de foto principal](https://trello.com/c/T2C2WVqd/15-s3-m%C3%BAltiples-fotos-y-selecci%C3%B3n-de-foto-principal).

El plan se debe escribir según [`PROMPT_GENERAR_PLAN_IMPLEMENTACION.md`](../../../PROMPT_GENERAR_PLAN_IMPLEMENTACION.md), después de revisar el código relacionado y cerrar las decisiones arquitectónicas pendientes. Ese prompt pide un **plan**, sin implementar código, tests ni migraciones durante su elaboración. Las dos tarjetas deben planificarse juntas por su dependencia funcional. Este archivo es un traspaso de contexto; **no es el plan de implementación** ni prueba que los criterios de aceptación ya estén completos.

## 1. Alcance que exigen las tarjetas

La tarjeta de infraestructura requiere S3 privado y CloudFront como vía de entrega pública; subida autorizada sin credenciales AWS en el navegador; original si se conserva, miniatura y versión de detalle; claves de objeto en la base de datos en lugar de URL públicas fijas; caché, reemplazo mediante claves nuevas, borrado de todas las variantes y seguimiento de costos. Logo, banners y carrusel institucional pueden permanecer como archivos estáticos del frontend.

La tarjeta de múltiples fotos requiere varias fotos por **Producto**, orden estable y como máximo una principal. La principal se usa en la tarjeta pública. Cada foto necesita al menos miniatura (referencia: ~400 px de ancho) y detalle (~1200 px), en un formato web eficiente como WebP. Crear, reemplazar y borrar deben mantener coherentes la base de datos y S3. La galería pertenece al producto completo: seleccionar una variante de producto **no** cambia las fotos.

## 2. Recursos AWS creados y comprobados por el usuario

### Seguridad y entornos

- La cuenta root y el usuario IAM administrador personal ya tienen MFA.
- Se eligió **una cuenta AWS** con recursos separados para **staging** y **producción**. Región S3: `us-east-1`.
- Se descartó usar un único usuario IAM técnico o un único bucket para ambos entornos. La aplicación ejecutará el backend en una VPS externa a AWS cuando el cliente la contrate; **todavía no existe esa VPS**.
- Los buckets tienen las cuatro opciones de **Block Public Access** activadas. La propiedad de objetos es **Bucket owner enforced** (ACL deshabilitadas). Cifrado predeterminado **SSE-S3**. No se activó el versionado nativo de S3 ni Object Lock.
- Staging y producción tienen distribuciones CloudFront separadas, plan **Free flat-rate**, origen S3 privado y acceso mediante OAC. Se eligió la configuración de origen y caché recomendada al crear las distribuciones. No se configuró `Origin path`.

### Staging

- Bucket: `glgcorralon-product-images-staging-121026`.
- Distribución CloudFront: `glgcorralon-product-images-staging`.
- ID: `E2ARNBAE5EGW9V`.
- Dominio: `d296ipo7jtz4ug.cloudfront.net`.
- La política de bucket generada para OAC permite `s3:GetObject` **solo** al principal de servicio CloudFront condicionado al ARN de esa distribución. No concede permisos de escritura a CloudFront.
- El usuario comprobó que la URL directa de un objeto S3 responde `AccessDenied` y que la URL de ese mismo objeto por CloudFront muestra la imagen. La URL raíz de la distribución puede devolver `AccessDenied` porque no se configuró un objeto raíz; la prueba correcta es con la clave concreta del objeto.

### Producción

- Bucket: `glgcorralon-product-images-prod-131026`.
- El usuario creó la distribución CloudFront de producción y comprobó entrega de una imagen por CloudFront y `AccessDenied` por URL directa de S3.
- **No se registraron en este chat el ID ni el dominio exactos de la distribución de producción**; pedirlos o leerlos de AWS al preparar su configuración.

### Usuarios IAM técnicos

- Usuarios sin acceso a consola: `glgcorralon-images-staging` y `glgcorralon-images-prod`.
- Cada uno tiene una política **inline** propia, limitada a los objetos (`bucket/*`) de su bucket. Acciones: `s3:PutObject`, `s3:GetObject` y `s3:DeleteObject`. No se les dio `s3:ListBucket` ni acceso al bucket del otro entorno. La política de staging se vio en captura; el usuario confirmó haber aplicado también la de producción.
- No se agruparon ambos usuarios en un grupo de permisos común, porque los recursos autorizados son distintos.
- **No se confirmó la creación de access keys**. La clave de staging será necesaria para la prueba local desde IntelliJ. La clave de producción puede esperar al despliegue. Nunca colocar claves en Git, en el frontend ni en este documento.

### Etiquetas, presupuestos y monitoreo

- Se añadieron etiquetas `Project = GLGCorralon` y `Environment = staging` / `prod` a los recursos durante la configuración; staging CloudFront inicialmente tenía solo `Name` y luego el usuario agregó las otras etiquetas.
- El usuario creó presupuestos separados para S3 y para CloudFront por entorno. Para los presupuestos de CloudFront se seleccionaron las categorías de costo `CloudFront` y `CloudFront Flat-Rate Plans`.
- **Pendiente de verificar:** que las etiquetas de asignación de costos aparezcan y estén activas en Billing, que los presupuestos efectivamente separen los costos por entorno y que sus alertas lleguen. AWS puede tardar en exponer etiquetas nuevas para asignación de costos. También falta definir/verificar las métricas concretas para almacenamiento, operaciones y tráfico.
- Se eligió el plan Free flat-rate para empezar; revisar límites y costos vigentes antes de producción según tráfico real.

## 3. Estado del código comprobado

- Backend: Spring Boot 4.1, Java 21, Maven, MySQL/Flyway. Frontend: React/Vite.
- Existe [`Product.java`](../../../Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/product/model/Product.java) con datos básicos, pero **no** entidad/repository/servicio de imágenes de producto.
- La migración más reciente encontrada es `V8__create_subcategories.sql`; habrá que crear la persistencia para galería y claves de variantes.
- [`application.properties`](../../../Backend/spring-starter/src/main/resources/application.properties) ya toma secretos existentes mediante variables de entorno, pero no tiene configuración S3/CloudFront. [`pom.xml`](../../../Backend/spring-starter/pom.xml) todavía no declara el SDK de AWS ni una biblioteca de procesamiento de imágenes.
- El modelo inicial documenta `ImagenProducto` con `producto_id`, clave S3, orden y `es_principal`; debe evolucionarse para registrar las claves de original, miniatura y detalle. Ver [`docs/modelo-inicial.md`](../../modelo-inicial.md).
- En esta conversación **no se modificó código, configuración, tests ni migraciones** para imágenes.
- Al crear este archivo, `git status` ya mostraba cambios ajenos en `docs/Diagrama ECOMMERCE.drawio` y su `.bkp`; no fueron hechos en este trabajo. Preservarlos.

## 4. Decisiones y propuesta técnica conversada

### Entrega y credenciales

- El navegador debe pedir imágenes a CloudFront y cargar fotos mediante una API autenticada del backend. No recibe credenciales AWS ni sube directamente a S3 en esta versión.
- Para pruebas locales, **no hace falta AWS CLI ni VPS**. El usuario intentó `aws --version` y el comando no estaba instalado. Se acordó comprobar el flujo ejecutando Spring Boot desde IntelliJ con credenciales de **staging** en variables de entorno de la configuración de ejecución, no en un `application.properties` versionado.
- Variables sugeridas para el SDK local: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION=us-east-1`. Los nombres de las variables propias de bucket y dominio CDN se fijarán en el plan. Un archivo de propiedades por sí solo no prueba nada: hay que añadir SDK y código/prueba de subida, lectura y borrado.

### Generación de variantes

- **Propuesta preferida, todavía por formalizar en el plan:** el backend valida la foto y genera dos versiones optimizadas (miniatura ~400 px y detalle ~1200 px), luego sube cada archivo a S3 con `Content-Type` y `Cache-Control` adecuados. S3 por sí mismo almacena los objetos, **no genera las miniaturas**. Un procesamiento en AWS requeriría componentes adicionales, por ejemplo Lambda; no se eligió esa arquitectura.
- Formato web propuesto: WebP. Hay que decidir formatos admitidos de entrada, límites de tamaño/dimensiones, biblioteca de procesamiento, calidad, orientación EXIF y manejo de archivos inválidos.
- **Decisión pendiente:** conservar o no el original en S3. Conservarlo facilita regenerar tamaños; aumenta almacenamiento.

### Claves, caché, reemplazo y borrado

- **Propuesta preferida:** claves nuevas e inmutables para cada revisión, por ejemplo `products/{productId}/images/{imageId}/{revision}/thumb.webp` y `.../detail.webp`; si se conserva, `.../original.<ext>`. La base de datos guarda las claves activas, no URLs de CloudFront fijas. El backend compone la URL de cada tamaño.
- El versionado del **nombre de la clave** es distinto del versionado nativo de S3; este último permanece desactivado. Al reemplazar se suben las variantes nuevas y después se actualizan las claves activas de la foto. Así el cliente pide una URL nueva sin invalidación masiva de CloudFront.
- La configuración «caché recomendada» usada al crear la distribución **no cierra aún la decisión de TTL**. Revisar la política realmente asociada y definir `Cache-Control` para los objetos generados y la duración de retención de claves antiguas. La política administrada `CachingOptimized`, si es la asociada, usa TTL predeterminado de 24 horas cuando el objeto no envía encabezados, con máximo de 365 días.
- No crear una regla de ciclo de vida que borre indiscriminadamente todo objeto de producto tras X días: borraría también fotos activas. Se necesita limpieza dirigida de revisiones sustituidas y cargas fallidas, con un plazo de seguridad acordado.
- Al borrar una foto, quitarla de la galería y gestionar **todas** sus claves (original, miniatura, detalle y revisiones obsoletas). Si era la principal, elegir explícitamente la nueva principal o dejar el producto sin principal según la regla que se acuerde. Las operaciones de MySQL y S3 no comparten una transacción: prever compensación/reintentos ante fallos parciales.
- Borrar el objeto en S3 **no** garantiza que desaparezca de inmediato de una caché CloudFront ya poblada. Si se requiere retirada inmediata, invalidar únicamente esas rutas. Está pendiente definir en qué casos ese requisito aplica.

Fuentes AWS útiles: [políticas de caché](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-cache-policies.html), [duración de caché y encabezados](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Expiration.html), [nombres versionados frente a versionado S3](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/UpdatingExistingObjects.html), [invalidaciones](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html).

## 5. Próximos pasos para el nuevo chat

1. Leer las dos tarjetas de Trello y el prompt de generación del plan; inspeccionar controller, service, DTO, seguridad, migraciones y frontend de Producto. No asumir que el modelo inicial ya está implementado.
2. Cerrar decisiones que cambian contrato o persistencia: conservación del original, formatos y límites, modelo de galería y unicidad de principal, TTL y plazo de limpieza, política de borrado inmediato, comportamiento ante fallos parciales y autorización de administración.
3. Redactar un **plan conjunto por tandas** en `docs/Sprint 3/S3 Configurar S3CloudFront para imágenes de productos/`, conforme al prompt. El usuario decidió abrir otro chat para esta tarea.
4. Después de aprobar/usar ese plan, implementar y verificar localmente con **staging**: carga autenticada, transformación, almacenamiento, lectura por CloudFront, principal/orden, reemplazo y borrado. Comprobar que el frontend nunca recibe credenciales ni usa la URL directa de S3.
5. Verificar monitoreo y alertas de costos, revisar configuración de producción y cerrar cada tarjeta solo cuando cumpla su aceptación. La VPS se abordará cuando el cliente la contrate.

**Nota de evidencia:** están comprobadas manualmente por el usuario la lectura CloudFront y la denegación de lectura pública directa en ambos entornos. **No están probadas todavía** la carga con el usuario IAM técnico, la generación de variantes, la integración backend/frontend, los reemplazos/borrados ni la separación efectiva de costos por entorno.
