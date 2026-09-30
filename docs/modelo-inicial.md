# Análisis del modelo inicial de base de datos

## Propósito y estado

Este documento registra las decisiones funcionales de datos acordadas para GLG Corralón. Es la fuente de diseño para preparar, usando `PROMPT_GENERAR_PLAN_IMPLEMENTACION.md`, el plan técnico del Sprint 2 y las tarjetas de migración de sprints posteriores.

En Sprint 1 se define y revisa el modelo; no se crean estas tablas todavía. La revisión y aceptación final por parte del negocio sigue pendiente. El backend usa MySQL y Flyway. Las cuentas, sesiones, recuperación de contraseña y MFA ya existen en las migraciones V1–V4 y se reutilizan; no se duplican en este modelo.

## Decisiones de alcance

- El carrito V1 vive en el navegador y no requiere cuenta ni tabla propia.
- El precio del catálogo es de referencia para solicitar presupuesto; no representa una oferta vinculante.
- “Pedir presupuesto” abre WhatsApp. La aplicación puede contar el clic y los productos incluidos, pero no confirma que el mensaje se envió ni que la venta se concretó.
- No se modelan pedidos, detalles de pedido ni stock integrado en V1. Una futura solicitud de presupuesto persistida o un flujo de compra requerirá decisiones y tarjetas propias.
- Las métricas permanentes se guardan agregadas por día, no como una fila por cada interacción.

## Modelo de catálogo

| Entidad | Datos y relaciones | Reglas acordadas |
| --- | --- | --- |
| `Categoria` | `id`, nombre, descripción, ícono, activo | Una categoría contiene muchos productos. Se desactiva, no se borra físicamente si tiene productos. |
| `Producto` | `id`, `categoria_id`, nombre, descripción, activo, destacado | Agrupa las variantes bajo una ficha pública. Pertenece a una categoría; solo los activos se publican. No tiene `marca_id` ni `caracteristica_id` directo. |
| `VarianteProducto` | `id`, `producto_id`, SKU opcional, precio de referencia, disponibilidad, activo | Representa una opción concreta del producto, con precio y disponibilidad propios. Un producto puede tener varias variantes. |
| `Caracteristica` | `id`, nombre | Define claves reutilizables, por ejemplo `Marca`, `Color` o `Medida`. El nombre es único. |
| `Producto_Caracteristica` | `producto_id`, `caracteristica_id`, valor, unidad opcional | Atributos compartidos por todas las variantes. La combinación producto/característica es única. `Marca` puede guardarse como característica; por ejemplo, clave `Marca`, valor `Acme`. |
| `Variante_Caracteristica` | `variante_id`, `caracteristica_id`, valor, unidad opcional | Valores que distinguen una variante, como color, capacidad, diámetro o largo. La combinación variante/característica es única. |
| `ImagenProducto` | `id`, `producto_id`, clave S3, orden, es principal | Un producto tiene varias imágenes y como máximo una principal. Se almacena la clave del objeto, no una URL pública fija. |
| `Promocion` | `id`, `producto_id`, precio promocional, inicio, fin, activa | Un producto puede no tener promoción y conservar promociones históricas. Como máximo una puede estar vigente para el producto. |

`Producto.destacado` es independiente de `Promocion`: puede haber un producto destacado sin descuento, una promoción no destacada o ambas cosas a la vez. En este último caso, el producto aparece destacado con su precio promocional mientras la promoción esté activa y vigente.

La marca no requiere una tabla propia. El filtro por marca se obtiene buscando la característica cuyo nombre es `Marca` y comparando su valor. La cardinalidad y unicidad de atributos deben respetarse al editar el producto.

### Variantes y precio inicial del catálogo

Una ficha pública representa un `Producto` con sus variantes. Por ejemplo, una pintura de 20 L puede tener variantes roja, blanca y celeste; un tornillo puede variar simultáneamente en diámetro y largo. Cada combinación de valores dentro de un producto identifica una sola variante: no se permiten combinaciones duplicadas.

La tarjeta del catálogo público muestra, sin prefijo «desde», el precio de referencia de la variante activa y disponible más barata. Al abrir el detalle, esa misma variante queda seleccionada inicialmente. Si ninguna variante activa está disponible, se muestra la variante activa más barata con el aviso «No disponible»; un producto sin variantes activas no se publica. Ante precios iguales, se desempata por ID de variante para mantener una selección estable. El carrito y la consulta de presupuesto deben conservar la variante seleccionada.

El alta básica de Sprint 2 conserva temporalmente precio de referencia y disponibilidad en `Producto`, sin exigir características ni variantes. La tarjeta de variantes de Sprint 3 migrará esos valores a una variante inicial por cada producto existente y ajustará el contrato de lectura y edición. El diagrama representa el modelo objetivo posterior a esa migración.

## Suscripciones y consentimiento

Una fila de `Suscripcion` representa un contacto y puede contener ambos canales:

| Campo | Regla |
| --- | --- |
| `id` | Clave primaria. |
| `nombre` | Opcional. |
| `email` | Opcional; se normaliza y valida cuando se informa. |
| `whatsapp` | Opcional; se normaliza y valida cuando se informa. |
| `consentimiento_email_en` | Fecha/hora de aceptación para email; vacía si no autorizó ese canal. |
| `consentimiento_whatsapp_en` | Fecha/hora de aceptación para WhatsApp; vacía si no autorizó ese canal. |
| `baja_email_en` | Fecha/hora de baja para email; vacía mientras no haya solicitado la baja. |
| `baja_whatsapp_en` | Fecha/hora de baja para WhatsApp; vacía mientras no haya solicitado la baja. |

Para crear la suscripción debe haber al menos un canal informado y autorizado. El consentimiento y la baja son independientes por canal. La baja se conserva como dato y no elimina la fila; una futura exportación excluye los canales dados de baja. Se descartó el campo `origen` de la suscripción. V1 captura y exporta contactos; la automatización de bajas desde campañas o WhatsApp queda para cuando exista envío integrado.

## Métricas agregadas

Cada clave única representa una combinación de día y dimensiones; el backend incrementa el contador con una operación atómica de MySQL. La fecha corresponde al día en la zona horaria de negocio configurada.

| Entidad | Dimensiones y contadores | Uso |
| --- | --- | --- |
| `InteraccionProductoDiaria` | `fecha`, `producto_id`, `tipo`, `contador`; tipos iniciales `VISTA` y `AGREGADO_CARRITO` | Productos más vistos y más agregados al carrito. Única por producto, día y tipo. El contador de agregado mide acciones, no unidades ni el estado actual del carrito. |
| `VistaCategoriaDiaria` | `fecha`, `categoria_id`, `contador` | Categorías más consultadas. Única por categoría y día. |
| `BusquedaDiaria` | `fecha`, término normalizado, veces buscado, búsquedas sin resultados | Detectar términos frecuentes y búsquedas que no encontraron productos, sin conservar una fila permanente por búsqueda. |
| `ConsultaPresupuestoDiaria` | `fecha`, `origen` (`DETALLE` o `CARRITO`), cantidad de consultas, suma de productos distintos y suma de unidades | Contar clics de “Pedir presupuesto” y calcular promedios por consulta. Única por día y origen. |
| `ProductoConsultaDiaria` | `fecha`, `origen`, `producto_id`, consultas que lo incluyeron y unidades consultadas | Saber qué productos se consultaron por WhatsApp y en qué cantidades. Única por día, origen y producto. |

El evento de presupuesto se registra una vez al pulsar el botón, desde el detalle o el carrito. Ese clic incrementa la consulta global una vez y actualiza los contadores de cada producto incluido. Un clic significa **consulta iniciada hacia WhatsApp**, no mensaje enviado ni venta. Los productos distintos y las unidades son medidas separadas: un carrito con 5 bolsas suma una consulta, una inclusión del producto y 5 unidades. El promedio de productos distintos por consulta es `total_productos_distintos / cantidad_consultas`; el promedio de unidades se calcula con `total_unidades / cantidad_consultas`.

Los productos más vistos se obtienen sumando `contador` de `InteraccionProductoDiaria` para `tipo = VISTA` dentro del período, agrupando por producto y ordenando de mayor a menor. Es un conteo de vistas, no de visitantes únicos.

## Acceso previsto

El backend aplica la autorización; ocultar controles en React no reemplaza las reglas de API.

| Acción | `SUPER_ADMIN` | `ADMIN` | `USER` |
| --- | --- | --- | --- |
| Administrar cuentas | Sí | No | No |
| Gestionar catálogo e imágenes | Sí | Sí | Sí |
| Gestionar promociones y contenido comercial | Sí | Sí | Según tarjeta de implementación |
| Consultar y exportar suscripciones | Sí | Sí | No |
| Consultar métricas | Sí | Sí | No |

## Separación de implementación

- **Sprint 2 — catálogo básico:** categorías y productos con sus datos esenciales, relaciones, estado y precio de referencia. Las migraciones y tarjetas deben reflejar que no existe entidad `Marca`.
- **Sprint 3 — variantes, atributos e imágenes:** `VarianteProducto`, `Caracteristica`, `Producto_Caracteristica`, `Variante_Caracteristica` e imágenes múltiples con selección de principal, según tarjetas separadas de catálogo.
- **Sprints posteriores:** promociones; captura, consulta y exportación de suscripciones; métricas y panel. Cada grupo requiere tarjetas y migraciones separadas. Las fechas de baja se incluyen en la tabla de suscripción desde su primera migración, aunque la automatización de bajas se agregue después.
- **Fuera de V1:** pedidos persistidos, detalle de pedido, pagos online y stock integrado.

## Criterio de salida de S1-01

- El negocio revisa y acepta campos, relaciones, reglas de promoción, consentimiento por canal y significado de las métricas.
- Las migraciones de Sprint 2 y las posteriores quedan en tarjetas separadas, con dependencias visibles.
- El plan técnico siguiente se genera tomando este documento como propuesta de diseño principal; cualquier decisión nueva detectada al inspeccionar el código se registra como pendiente en ese plan.
