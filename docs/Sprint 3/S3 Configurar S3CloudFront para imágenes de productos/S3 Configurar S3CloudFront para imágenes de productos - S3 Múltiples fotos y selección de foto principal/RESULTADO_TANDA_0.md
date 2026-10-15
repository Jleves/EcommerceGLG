# Resultado de ejecución — Tanda 0

**Fecha:** 13/10/2026; cierre verificado el 14/10/2026  
**Estado:** COMPLETA

## Alcance y cambios realizados

- El backend toma `app.images.bucket` de `IMAGE_S3_BUCKET`, `app.images.cloud-front-base-url` de `IMAGE_CLOUDFRONT_BASE_URL` y la región de `AWS_REGION` (por defecto `us-east-1`). Bucket y URL son obligatorios. La URL debe ser HTTPS y contener solo el origen; la región válida para estos buckets es `us-east-1`.
- Quedaron configurados y validados los límites iniciales de 10 MiB, 20 millones de píxeles, ancho de miniatura 400 px, detalle 1200 px y TTL de 24 horas. Aún no se aplican a una carga: eso pertenece a la tanda 1.
- Se añadió AWS SDK Java v2 `2.55.14` mediante BOM y un bean `S3Client` que usa `DefaultCredentialsProvider`. El arranque no requiere una llamada AWS y no se introdujeron claves en código, propiedades, tests o frontend.
- `application-test.properties` usa un bucket y dominio ficticios. El código de test comprueba que una configuración de staging apunte al bucket y CDN de staging.
- No se agregaron endpoints, procesamiento, migraciones ni cambios al frontend o a AWS.

**Diferencias respecto del plan:** ninguna en el código. La infraestructura de costos encontrada contradice el estado previo documentado; se detuvo esa parte sin modificar recursos AWS.

## Verificación AWS de solo lectura

| Componente | Staging | Producción |
| --- | --- | --- |
| Bucket | `glgcorralon-product-images-staging-121026`, `us-east-1` | `glgcorralon-product-images-prod-131026`, `us-east-1` |
| Distribución y dominio | `E2ARNBAE5EGW9V`, `d296ipo7jtz4ug.cloudfront.net` | `E5FOY3R85C9QT`, `d2q2545tsijue5.cloudfront.net` |
| Origen y OAC | S3 privado, OAC `E1Q6XYW3FLNWGV` | S3 privado, OAC `E2WCXAGN1IL1L9` |
| Bucket y acceso | Bloqueo de todo acceso público activo; ACL deshabilitadas; política `s3:GetObject` solo para CloudFront con ARN de esta distribución | Igual, con ARN de producción |
| IAM técnico | Política inline `PutObject`, `GetObject`, `DeleteObject` limitada a `bucket/*`; sin `ListBucket`; una access key activa, todavía sin uso según IAM | Mismas acciones limitadas a su bucket; sin `ListBucket`; sin access key |
| Caché | `Managed-CachingOptimized` | `Managed-CachingOptimized` |
| Etiquetas CloudFront | `Project=GLGCorralon`, `Environment=staging` | `Project=GLGCorralon`, `Environment=prod` |
| Etiquetas de usuario del bucket S3 | `Project=GLGCorralon`, `Environment=staging` (añadidas después por el usuario) | `Project=GLGCorralon`, `Environment=prod` (añadidas después por el usuario) |
| Propiedades S3 | SSE-S3, versionado y Object Lock deshabilitados | SSE-S3, versionado y Object Lock deshabilitados |

La política administrada tiene TTL mínimo **1 segundo**, predeterminado **86.400 segundos** y máximo **31.536.000 segundos**. Los futuros objetos enviarán `Cache-Control: public, max-age=86400` en la tanda 1; la configuración actual de CloudFront permite ese TTL. [Política de caché](https://us-east-1.console.aws.amazon.com/cloudfront/v4/home?region=us-east-1#/policies/cache/658327ea-f89d-4fab-a63d-7e88639e58f6).

Billing muestra únicamente dos presupuestos: `glgcorralon-cloudfront-staging-mensual`, sin filtros incluidos, y `S3-PRESUPUESTO`, filtrado solo por servicio S3. El usuario aclaró que este último fue pensado para producción; al revisar de nuevo «Parámetros adicionales del presupuesto», la consola muestra únicamente `Servicio: S3 (Simple Storage Service)`. Por eso su nombre o intención no excluye los costos del bucket de staging de la misma cuenta. Ambos son de USD 10 mensuales y muestran tres alertas para un destinatario con estado `Active (legacy)`; **no se comprobó la llegada de correos**. No hay presupuestos filtrados efectivamente por producción en la lista. [Presupuestos](https://us-east-1.console.aws.amazon.com/costmanagement/home?region=us-east-1#/budgets/overview), [etiquetas de costos](https://us-east-1.console.aws.amazon.com/costmanagement/home#/tags).

En la inspección inicial, ambos buckets tenían cero etiquetas de usuario y las claves de asignación de costos estaban inactivas. Después el usuario añadió `Project` y `Environment` a los buckets. La nueva lectura de las propiedades confirmó **dos etiquetas en cada uno**, con los valores de la tabla; Billing muestra ambas claves **Activas**. Las distribuciones CloudFront ya tenían esas etiquetas. La activación de etiquetas no modifica automáticamente los filtros de presupuestos ni demuestra todavía costos atribuidos.

**Revisión de presupuestos del 14/10/2026:** tras volver a iniciar sesión en AWS, la lista continúa con **dos** presupuestos. `S3-PRESUPUESTO` conserva como único filtro `Servicio: S3 (Simple Storage Service)` y `glgcorralon-cloudfront-staging-mensual` muestra `Filtros incluidos: Ninguno`. Ambos siguen con monto mensual de USD 10 y tres alertas asociadas a un destinatario `Active (legacy)`; la consola no demuestra recepción efectiva. No se editaron presupuestos en esta revisión.

**Actualización posterior del 14/10/2026:** el usuario guardó `Servicio: CloudFront Flat-Rate Plans` como filtro de costos de `glgcorralon-cloudfront-staging-mensual` y añadió etiquetas al recurso presupuesto (`Project=GLGCorralon`, `Environment=staging`). `S3-PRESUPUESTO` también tiene etiquetas del recurso presupuesto (`Project=GLGCorralon`, `Environment=prod`). Al releer «Filtros incluidos», ninguno tiene aún un filtro de costos por `Environment`: CloudFront muestra solo el servicio Flat-Rate Plans y S3 solo el servicio S3. Las etiquetas del presupuesto no limitan los costos incluidos; falta agregar `Dimensión: Etiqueta` en el ámbito de cada presupuesto y verificar el resultado guardado. El agente no guardó cambios en AWS.

**Nueva verificación del 14/10/2026:** tras los cambios realizados por el usuario, «Filtros incluidos» muestra en `S3-PRESUPUESTO` `Servicio: S3 (Simple Storage Service)`, `Etiqueta: Environment: prod` y `Etiqueta: Project: GLGCorralon`. En `glgcorralon-cloudfront-staging-mensual` muestra `Servicio: CloudFront Flat-Rate Plans`, `Etiqueta: Environment: staging` y `Etiqueta: Project: GLGCorralon`. Los dos presupuestos existentes ya delimitan sus respectivos costos por servicio, proyecto y entorno. Aún no hay evidencia de costos atribuidos ni de recepción efectiva de alertas; tampoco se han verificado presupuestos para S3 staging o CloudFront producción. El agente solo leyó la consola.

**Verificación posterior del 14/10/2026:** la lista muestra ahora cuatro presupuestos mensuales de USD 10, todos «En buen estado» y con importe utilizado de USD 0,00. `glgcorralon-s3-staging-mensual` incluye `Servicio: S3 (Simple Storage Service)`, `Etiqueta: Environment: staging` y `Etiqueta: Project: GLGCorralon`. `glgcorralon-cloudfront-prod-mensual` incluye `Servicio: CloudFront Flat-Rate Plans`, `Etiqueta: Environment: prod` y `Etiqueta: Project: GLGCorralon`. Los dos presupuestos nuevos muestran tres alertas de costo real por encima del 50 %, 80 % y 100 %; el destinatario figura `Active (legacy)`. Con los dos presupuestos previos, quedan representadas las cuatro combinaciones servicio/entorno previstas. Todavía no se observa costo atribuido ni se ha comprobado la recepción efectiva de alertas. El agente no modificó AWS.

La lectura CloudFront y la denegación S3 directa fueron comprobadas antes por el usuario y constan en `CONTEXTO_PARA_PLAN_IMAGENES.md`; no se repitieron en esta ejecución. Tampoco se probaron permisos IAM mediante llamadas firmadas ni carga de imágenes, que pertenecen a la tanda 1.

## Tests ejecutados

| Categoría | Pruebas | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| Configuración | `AppPropertiesValidationTest` (8) | Obligatoriedad, región, URL, límites y TTL de imágenes; conserva validaciones previas | 8 PASS |
| Arranque y configuración | `ProductImageConfigurationTest` (3) | Falla legible sin bucket o dominio; configuración staging crea cliente en `us-east-1` y usa solo su bucket | 3 PASS |
| Regresión | `SpringStarterApplicationTests` (1) | Contexto Spring con perfil test, migraciones existentes y nuevo cliente S3 | 1 PASS fuera del sandbox |
| Seguridad | Inspección de `application.properties`, código backend/frontend y DTO de producto | No hay credenciales AWS en archivos productivos ni en respuestas de producto existentes | PASS estático |
| Arranque local de staging | `spring-boot:run` con variables de la configuración `CorralonGLG` de IntelliJ | Spring inició con Java 21, conectó a MySQL, Flyway validó 8 migraciones y no requirió migraciones nuevas; el proceso se detuvo tras `Started SpringStarterApplication` | PASS fuera del sandbox |

Los reportes Surefire indican **12 tests exitosos, 0 fallidos**. El intento de regresión dentro del sandbox quedó interrumpido por el mecanismo de conexión de Mockito con Java; la repetición fuera del sandbox finalizó con código 0. El arranque local adicional se ejecutó con las variables de IntelliJ, incluidas bucket, URL CloudFront y credenciales AWS presentes, sin imprimir ni copiar sus valores. La configuración `.idea` está ignorada por Git. La creación del `S3Client` no hace una llamada a AWS, así que el arranque no valida la vigencia de la access key ni los permisos IAM. `git diff --check` terminó sin errores.

### Tests faltantes y riesgos residuales

- **Permisos efectivos y entrega:** no se realizó `PutObject/GetObject/DeleteObject` firmado ni una nueva lectura de objeto por CloudFront. Riesgo: una política visible puede diferir del permiso efectivo o de la entrega real. Recomendación: probar el flujo en la tanda 1.
- **Recepción y atribución real de costos:** los cuatro presupuestos están filtrados por servicio, proyecto y entorno, pero aún marcan USD 0,00; no hay evidencia de correo recibido ni de cargos atribuidos a esas etiquetas. Recomendación: revisar Billing cuando aparezca uso facturado y verificar recepción con el responsable de Billing. La falta de gasto no permite probar el disparo de un umbral en esta tanda.
- **Respuestas HTTP de imágenes:** todavía no existen endpoints de imágenes; la inspección de seguridad se limita a configuración y respuestas actuales de producto. Recomendación: cubrir el contrato nuevo en tandas 1 y 3.

## Incidentes

### INC-001 — Import de validación incompatible con Spring Boot 4

**Contexto:** primera compilación de `ProductImageConfigurationTest`.  
**Test/comportamiento afectado:** pruebas de arranque de propiedades.  
**Esperado:** compilar y ejecutar el test.  
**Obtenido:** `org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration` no existe.  
**Clasificación:** defecto del test.  
**Causa:** Spring Boot 4.1 ubica la clase en `org.springframework.boot.validation.autoconfigure`.  
**Resolución:** se corrigió únicamente el import del test después de inspeccionar el JAR instalado.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** verificar paquetes de auto configuración contra la versión real del proyecto.

### INC-002 — Mockito no pudo conectarse a Java dentro del sandbox

**Contexto:** `SpringStarterApplicationTests` inició el contexto pero la ejecución aislada quedó detenida tras el intento de instrumentación de Mockito.  
**Test/comportamiento afectado:** regresión de arranque.  
**Esperado:** cierre de Surefire y reporte PASS.  
**Obtenido:** `could not open (5) pipe ... javatool` y sin reporte nuevo dentro del sandbox.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** conexión de Mockito con el proceso Java restringida por el sandbox.  
**Resolución:** se repitió el mismo test fuera del sandbox, sin alterar test ni producción.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS, 1 test, código 0.  
**Riesgo o aprendizaje derivado:** los tests Spring que activan Mockito pueden requerir ejecución fuera de este sandbox.

### INC-003 — Costos sin separación efectiva por entorno

**Contexto:** inspección de AWS Billing, etiquetas de S3 y presupuestos.  
**Test/comportamiento afectado:** criterio de cierre de etiquetas, presupuestos y alertas por entorno.  
**Esperado:** etiquetas utilizables para costos y presupuestos S3/CloudFront separados para staging y producción.  
**Obtenido:** ambos buckets muestran 0 etiquetas de usuario; `Environment` y `Project` están inactivas en Billing; existen solo dos presupuestos, uno CloudFront sin filtros y uno S3 filtrado por servicio, ninguno por entorno.  
**Clasificación:** configuración/infraestructura/entorno; contradicción con el contexto previo.  
**Causa:** no determinada.  
**Resolución:** durante la ejecución no se modificó AWS. Después el usuario etiquetó ambos buckets, activó las claves en Billing y configuró cuatro presupuestos con filtros por servicio, `Environment` y `Project`.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** RESUELTO para configuración y separación de presupuestos; atribución de cargos y entrega de correos pendientes de evidencia real.  
**Riesgo o aprendizaje derivado:** las etiquetas del recurso presupuesto no sustituyen los filtros de costos del presupuesto.

### INC-004 — Primeros intentos de arranque local detenidos por el entorno

**Contexto:** prueba de arranque con la configuración de IntelliJ.  
**Test/comportamiento afectado:** inicio de Spring con las variables de staging.  
**Esperado:** iniciar el backend hasta `Started SpringStarterApplication`.  
**Obtenido:** el primer intento no encontró `JAVA_HOME`; el segundo alcanzó Flyway, pero el sandbox denegó la conexión TCP con MySQL (`Permission denied: getsockopt`).  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** la terminal no heredó el JDK de IntelliJ y la conexión local a MySQL estaba restringida dentro del sandbox.  
**Resolución:** se usó el JDK 21 instalado y se repitió el mismo arranque fuera del sandbox, sin cambiar código ni tests.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS; MySQL conectó, Flyway confirmó esquema en versión 8 y Spring inició.  
**Riesgo o aprendizaje derivado:** el arranque no prueba las credenciales AWS mediante una operación S3.

## Decisiones pendientes descubiertas

**D01 — Asignación de costos por entorno.** Configuración resuelta: las etiquetas de los buckets y su activación en Billing fueron verificadas; los cuatro presupuestos tienen filtros de costos por servicio, `Environment` y `Project`, cubriendo S3 y CloudFront en staging y producción. Queda el seguimiento de atribución cuando aparezcan datos de facturación y de recepción efectiva de alertas. La tanda 0 aprobada permitía verificar AWS, no modificarlo; los cambios en AWS fueron realizados por el usuario.

## Archivos modificados

- `Backend/spring-starter/pom.xml`
- `Backend/spring-starter/src/main/resources/application.properties`
- `Backend/spring-starter/src/main/resources/application-test.properties`
- `Backend/spring-starter/src/main/java/com/ashenox/starter/shared/config/AppProperties.java`
- `Backend/spring-starter/src/main/java/com/ashenox/starter/catalog/product/image/config/ProductImageS3Config.java` (nuevo)
- `Backend/spring-starter/src/test/java/com/ashenox/starter/shared/config/AppPropertiesValidationTest.java`
- `Backend/spring-starter/src/test/java/com/ashenox/starter/catalog/product/image/config/ProductImageConfigurationTest.java` (nuevo)
- `docs/Sprint 3/S3 Configurar S3CloudFront para imágenes de productos/PLAN_IMPLEMENTACION_IMAGENES.md`
- Este documento (nuevo).

## Cierre de la tanda

- [x] Configuración de staging validada con variables locales, sin secretos versionados. El backend arrancó; la operación efectiva con la access key queda para la tanda 1.
- [x] Bucket, OAC, IAM, política de caché y alertas registrados por entorno. Los cuatro presupuestos tienen filtros correctos y tres alertas configuradas; la recepción efectiva de correo sigue sin evidencia.
- [x] Tests de configuración ejecutados; faltantes, incidentes y riesgos visibles.

**Seguimiento posterior:** comprobar recepción de alertas y atribución de cargos cuando exista gasto real. La prueba efectiva de permisos IAM y entrega de objetos pertenece a la tanda 1.
