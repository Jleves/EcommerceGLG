# Modelo inicial para planificar el catálogo

Este documento define el alcance de datos del ecommerce. Las tablas de catálogo se implementarán en el Sprint 2 y las imágenes, promociones, contactos y eventos en sus sprints respectivos. Las tablas de `users`, sesiones, recuperación y MFA ya están en migraciones Flyway V1–V4 heredadas del starter.

## Entidades previstas

| Entidad | Datos mínimos | Regla de negocio |
| --- | --- | --- |
| Categoría | ID, nombre, ícono, descripción, estado | Un nombre visible por categoría; no borrar físicamente una categoría con productos. |
| Marca | ID, nombre, estado | Marca opcional para el producto; se usa en catálogo y filtros. |
| Producto | ID, nombre, descripción, precio de referencia, categoría, marca opcional, disponibilidad, estado | Solo los productos activos son públicos; el precio mostrado es orientativo para solicitar presupuesto. |
| Atributo de producto | Producto, nombre, valor, unidad opcional | Permite características particulares sin agregar una columna por rubro. |
| Imagen de producto | Producto, clave del objeto, orden, principal | Una imagen principal como máximo; guardar clave S3, no URL pública fija. |
| Promoción | Producto, precio promocional opcional, inicio, fin, destacado | La vigencia determina el precio visible y la aparición en ofertas. |
| Suscriptor | Nombre opcional, email opcional, WhatsApp opcional, consentimiento, fecha y origen | Exigir al menos un canal y registrar aceptación antes de exportar. |
| Evento comercial | Tipo, fecha, referencia opcional a producto/categoría, búsqueda y origen cuando aplique | Registrar búsquedas, vistas, carrito y salida a WhatsApp sin exigir cuenta de comprador. |

El carrito de V1 vive en el navegador; no necesita tabla propia ni cuenta de comprador. El presupuesto se prepara como mensaje de WhatsApp y no crea una orden de compra. Una futura solicitud persistida se planificará junto con las métricas si el negocio necesita seguimiento posterior.

## Acceso previsto

| Acción | SUPER_ADMIN | ADMIN (dueño) | USER (colaborador) |
| --- | --- | --- | --- |
| Crear, cambiar email, activar o desactivar cuentas | Sí | No | No |
| Gestionar catálogo e imágenes | Sí | Sí | Sí |
| Gestionar promociones y contenidos comerciales | Sí | Sí | Según alcance de la tarjeta de promociones |
| Consultar suscriptores, exportar contactos y ver métricas | Sí | Sí | No |
| Cambiar contraseña y activar/desactivar MFA propios | Sí | Sí | Sí |

El backend debe aplicar los permisos; ocultar controles en React no reemplaza la autorización de API. No se cambiará el rol de una cuenta en la V1: se crea otra cuenta con el rol correcto y se desactiva la anterior.

