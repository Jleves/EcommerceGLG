# Backlog para actualizar Trello

Fuente: exportación `TEkoBnn7 - ecommerce.json` y alcance funcional de la propuesta al cliente. **Aplicado al tablero Ecommerce el 28/09/2026:** se actualizaron las diez tarjetas del Sprint 1, se redujo el Sprint 2 a catálogo básico, se movieron las tarjetas de sprints posteriores a Backlog y se añadió la gestión de marcas. Los IDs de las tarjetas existentes se conservaron. Este documento mantiene los criterios para el refinamiento posterior.

## Flujo del tablero

Listas actuales: **BackLog → Refinado → sprint 1 → En curso → Validación → Terminado → sprint 2**. Las antiguas listas `epicas 3`, `epicas 4` y `epicas 5` se reutilizaron como estados vacíos; los prefijos `E3` y `E5` permanecen en las tarjetas. `sprint 1` y `sprint 2` conservan los compromisos planificados. Las tarjetas validadas localmente pasan a `Terminado` tras la revisión y aceptación del sprint.

## Diez tarjetas actuales de Sprint 1

Las descripciones siguientes se pueden pegar en las tarjetas homónimas. Las tarjetas heredadas de seguridad quedan **validadas localmente en GLG / pendientes de revisión de sprint y entorno de destino**, según `validacion-sprint-1.md`. S3/CloudFront y CI/CD permanecen en backlog.

### 1. Definir modelo de datos — alcance Sprint 1

**Título sugerido:** `S1-01 Definir modelo inicial de catálogo (autenticación reutilizada)`

**Descripción:** Las migraciones de usuarios, sesiones, recuperación y MFA ya existen en el starter. Revisar el modelo propuesto para categoría, marca, producto, características, imágenes, promociones, suscriptores y eventos. En Sprint 1 se acuerdan campos y relaciones; la persistencia de catálogo se implementa desde Sprint 2.

**Aceptación:** el modelo distingue precio de referencia de presupuesto, carrito local sin cuenta, permisos de cada rol y entidades que requieren migraciones nuevas.

### 2. Setup proyecto React

**Título sugerido:** `S1-02 Adaptar y validar React/Vite para GLG`

**Descripción:** Reutilizar routing, AuthProvider, cliente HTTP, protección de rutas y estilos del starter. Ajustar identidad GLG y comprobar que login, perfil, MFA y usuarios funcionan con el backend copiado. La tienda pública, el estado de carrito y el diseño final quedan en sprints posteriores.

**Aceptación:** tests, lint y build pasan; las pantallas privadas respetan roles y el nombre visible es GLG Corralón.

### 3. Configurar bucket S3 + distribución CloudFront

**Destino:** Backlog, objetivo Sprint 3. **Estado:** pendiente, no realizado en starter.

**Descripción:** Diseñar subida autorizada, almacenamiento privado y entrega de imágenes del catálogo; incluir borrado/reemplazo y selección de foto principal cuando exista la entidad producto. No crear recursos AWS hasta contar con cuenta, dominio y configuración de destino.

### 4. Setup proyecto Spring Boot

**Título sugerido:** `S1-03 Adaptar y validar Spring Boot/MySQL para GLG`

**Descripción:** Reutilizar capas de auth, JWT, CSRF, Flyway, seguridad y configuración del starter. Cambiar identidad de aplicación, arrancar con configuración local y validar migraciones V1–V4 sobre MySQL. Revisar variables para dev y producción antes del despliegue; no duplicar el módulo de seguridad.

**Aceptación:** compilación y pruebas locales pasan; el arranque MySQL aplica Flyway y no requiere secretos versionados.

### 5. CI/CD básico y entornos

**Destino:** Backlog de preparación de despliegue. **Estado:** pendiente, no realizado en starter.

**Descripción:** Pipeline de test/build, ambiente de staging y despliegue productivo con secretos externos. Planificarlo cuando exista un incremento de catálogo a publicar; mientras tanto usar comprobaciones locales reproducibles.

### 6. Login con usuario y contraseña

**Título sugerido:** `S1-04 Validar login heredado en GLG`

**Estado:** reutilizado en GLG y validado localmente; pendiente de revisión de sprint y entorno de destino.

**Aceptación:** `SUPER_ADMIN`, `ADMIN` y `USER` acceden al perfil propio; credenciales inválidas no crean sesión; cada rol conserva solo sus permisos.

### 7. Cambio de contraseña propia

**Título sugerido:** `S1-05 Validar cambio de contraseña heredado en GLG`

**Estado:** reutilizado en GLG y validado localmente; pendiente de revisión de sprint y entorno de destino.

**Aceptación:** cada rol puede cambiar su contraseña con la actual; sesiones anteriores quedan revocadas; la contraseña previa ya no permite login.

### 8. Alta/edición/baja de usuarios

**Título sugerido:** `S1-06 Validar gestión de cuentas solo para SUPER_ADMIN`

**Descripción:** En V1, “editar” significa cambiar email y “baja” significa desactivar con posibilidad de reactivar. El rol no se edita: se crea otra cuenta y se desactiva la anterior. El dueño `ADMIN` no administra usuarios.

**Estado:** reutilizado en GLG y validado localmente; pendiente de revisión de sprint y entorno de destino.

**Aceptación:** alta, listado, detalle, cambio de email y activar/desactivar funcionan desde React; `ADMIN` y `USER` reciben 403 al invocar la API.

### 9. Restricción de roles

**Título sugerido:** `S1-07 Validar autorización de cuentas por SUPER_ADMIN`

**Estado:** reutilizado en GLG y validado localmente; pendiente de revisión de sprint y entorno de destino.

**Aceptación:** solo `SUPER_ADMIN` puede crear cuentas de cualquier rol o desactivarlas; las rutas React y los endpoints rechazan al resto. El `ADMIN` tendrá métricas y catálogo, pero no CRUD de cuentas.

### 10. Activar/desactivar MFA por correo

**Título sugerido:** `S1-08 Validar MFA por correo heredado en GLG`

**Estado:** reutilizado en GLG y validado localmente; pendiente de revisión de sprint y entorno de destino.

**Aceptación:** los tres roles pueden activar/desactivar MFA propio; con MFA activo, el login requiere código enviado por SMTP de prueba y respeta vencimiento/reintentos.

## Sprint 2 — catálogo administrable básico

Conservar y refinar las tarjetas existentes `Crear categoría`, `S2 BE Editar, desactivar o reactivar categoría`, `Crear producto` y `Editar/eliminar producto`. La tarjeta de edición de categoría incluye desactivación y reactivación. Añadir una tarjeta **Marca: crear, editar y desactivar** porque el PDF compromete catálogo y filtros por marca. Criterio conjunto: migraciones Flyway y endpoints con permisos para `SUPER_ADMIN`, `ADMIN` y `USER`; formulario React; productos con nombre, categoría, descripción, precio de referencia y disponibilidad. No incluir aún fotos ni atributos particulares. Si la estimación supera la capacidad del sprint, comprometer categorías y marcas primero y mantener productos en `Refinado`.

## Sprint 3 — detalle de datos e imágenes

Mover desde el Sprint 2 actual las tarjetas `Características comunes + particulares del producto` y `Carga de múltiples fotos + selección de foto principal`. Vincularlas a la tarjeta S3/CloudFront diferida del Sprint 1. Aceptación: atributos por producto, varias imágenes, una principal y entrega pública por CloudFront sin exponer credenciales de subida.

## Sprint 4 — catálogo público

Mover desde el Sprint 2 actual `Header`, `Cards de categorías`, `Footer`, `Buscar producto`, `Detalle de producto` y `Productos relacionados`. Añadir tarjetas de **filtros por categoría, marca y atributos**, **búsqueda por marca/palabra clave** y **estado de disponibilidad**, comprometidos en el PDF. Aceptación: navegación móvil, resultados o mensaje vacío, ficha detallada y datos servidos desde el catálogo administrable. `Carril de marcas` puede entrar cuando exista la página pública.

## Sprint 5 — consulta comercial

Mover desde `epicas 5` las cuatro tarjetas E5-1 a E5-4: agregar al carrito local, editar cantidades/eliminar, pedir presupuesto del carrito por WhatsApp y consultar un producto puntual. Aceptación: sin login del comprador, carrito conservado en el navegador y mensaje prearmado con productos/cantidades, sin checkout ni pago.

## Backlog V1 posterior, en orden de dependencia

1. **Promociones y home:** E7-1, E7-2, E3-2, E3-4 y los elementos de servicios/métodos de pago de E3-5. Quitar la promoción de calculadora de E3-5, porque la calculadora pasa a V2. Mostrar solo promociones vigentes.
2. **Captación y contacto:** E6-1, E6-2, E10-1 y E10-2. El formulario admite email y/o WhatsApp, guarda consentimiento y origen; la exportación se limita a contactos con consentimiento. Añadir formulario de contacto general, previsto en el PDF.
3. **Información comercial:** E8-1 y E8-3; incluir productos más vistos y agregados, categorías/marcas más consultadas, búsquedas sin resultados, horarios, carritos y clics hacia WhatsApp. Registrar solo eventos necesarios y documentar el criterio de “solicitud” cuando el usuario sale a WhatsApp. E8-2, export de métricas, queda después del panel: el PDF promete consulta visual en V1.
4. **Preparación de entrega:** CI/CD, staging, configuración de producción, datos iniciales del catálogo, pruebas de extremo a extremo y revisión de seguridad/accesibilidad de flujos públicos y privados.

## V2 y fuera de V1

- E6-3: envío de newsletter desde la plataforma. La V1 captura y exporta contactos; no envía campañas.
- E9-1 a E9-5: calculadoras de ladrillos, cerámicos, revoque, pintura y estimaciones de contrapiso/techo. La pantalla del PDF se trató como referencia visual, no como compromiso funcional V1.
- E8-4: geolocalización aproximada, opcional y sujeta a utilidad/consentimiento.
- Tips y guías administrables, automatización de WhatsApp, pagos online y stock integrado: versiones posteriores.

## Criterios breves para las demás tarjetas existentes

Usar esta tabla al refinar las tarjetas; una fila corresponde a la tarjeta con ese título o ID de la exportación.

| Tarjeta actual | Destino | Criterio de aceptación resumido |
| --- | --- | --- |
| Crear categoría | Sprint 2 | `SUPER_ADMIN`, `ADMIN` y `USER` crean nombre, ícono y descripción; validación y persistencia visibles. |
| S2 BE Editar, desactivar o reactivar categoría | Sprint 2 | Se edita, desactiva y reactiva sin borrado físico. La categoría conserva sus productos al cambiar de estado y estos siguen activos. Una categoría inactiva no puede asignarse a productos nuevos; al reactivarla vuelve a ser elegible. |
| Crear producto | Sprint 2 | Alta con nombre, categoría, descripción, precio y disponibilidad; solo activos se publican. |
| Editar/eliminar producto | Sprint 2 | Cambios visibles en catálogo; la baja es desactivación sin perder referencias. |
| Características comunes + particulares | Sprint 3 | Ficha admite atributos reutilizables y específicos con nombre, valor y unidad opcional. |
| Carga de múltiples fotos + principal | Sprint 3 | Varias imágenes por producto, una principal y orden estable; se muestran por CloudFront. |
| E3-1 Header | Sprint 4 | Logo, navegación, buscador y contador de carrito accesibles en móvil y escritorio. |
| E3-3 Cards de categorías | Sprint 4 | Cada card muestra nombre e ícono y abre productos de esa categoría. |
| E3-6 Footer | Sprint 4 | Logo, navegación, teléfono, email, dirección y redes con enlaces correctos. |
| E4-1 Buscar por nombre | Sprint 4 | Resultados en cards y mensaje claro sin coincidencias; ampliar a marca/palabra clave. |
| E4-2 Detalle de producto | Sprint 4 | Fotos, descripción, atributos, precio orientativo, disponibilidad y consulta. |
| E4-3 Productos relacionados | Sprint 4 | Productos activos de la misma categoría o marca; sin duplicar el producto actual. |
| E3-4 Carril de marcas | Sprint 4 | Marcas activas enlazan a resultados filtrados. |
| E5-1 Agregar al carrito | Sprint 5 | Sin login; producto y cantidad persisten en el navegador. |
| E5-2 Ver/editar carrito | Sprint 5 | Cambiar cantidades y quitar ítems actualiza total y contador. |
| E5-3 Presupuesto por WhatsApp | Sprint 5 | Mensaje incluye productos y cantidades, abre el número comercial, sin pago. |
| E5-4 Consulta de producto por WhatsApp | Sprint 5 | Desde ficha se abre mensaje con identificación del producto. |
| E7-1 Producto destacado/promoción | V1 posterior | Administrador marca un producto activo; aparece en el carril correspondiente. |
| E7-2 Precio promocional con vigencia | V1 posterior | Inicio y fin controlan precio visible; vencido vuelve al precio normal. |
| E3-2 Carril de ofertas | V1 posterior | Solo muestra promociones vigentes y productos activos. |
| E3-5 Home: métodos de pago y servicios | V1 posterior | Cards informativas de pagos, asesoramiento, calidad y envíos; separar promoción de calculadora V2. |
| E6-1 Formulario de suscripción | V1 posterior | Email y/o WhatsApp, aceptación explícita, fecha y origen; estados de éxito/error. |
| E6-2 Extraer contactos | V1 posterior | `ADMIN` y `SUPER_ADMIN` descargan solo contactos con consentimiento. |
| E10-1 Contacto | V1 posterior | Dirección, horario, mapa, WhatsApp, email, redes y formulario general. |
| E10-2 Quiénes somos | V1 posterior | Banner, historia, proyectos y equipo con contenido aprobado por el negocio. |
| E8-1 Registrar eventos | V1 posterior | Búsqueda, vista, carrito y clic de WhatsApp disponibles para agregación sin cuenta de comprador. |
| E8-3 Panel de métricas | V1 posterior | Tendencias, búsquedas sin resultado, productos/categorías/marcas, horarios y consultas; solo roles elevados. |
| E8-2 Export de métricas | V1 posterior, después del panel | Export por período coherente con los filtros del panel. |
| E6-3 Envío newsletter | V2 | Planificar canal, plantillas, bajas y proveedor antes de implementar. |
| E8-4 Ubicación aproximada | V2 opcional | Solo avanzar si hay una fuente útil y un criterio de privacidad acordado. |
| E9-1 Calculadora ladrillos | V2 | Entradas y supuestos explícitos; resultado orientativo. |
| E9-2 Calculadora cerámicos | V2 | Área, medida de pieza y desperdicio; resultado orientativo. |
| E9-3 Calculadora revoques | V2 | Superficie y espesor; resultado orientativo. |
| E9-4 Calculadora pintura | V2 | Superficie, manos y rendimiento; resultado orientativo. |
| E9-5 Contrapisos/techos | V2 opcional | Validar fórmulas con experto antes de publicar estimaciones. |

**Tarjetas nuevas necesarias por el PDF:** gestión de marcas; filtros por categoría/marca/atributos; búsqueda por marca y palabra clave; estado de disponibilidad; formulario de contacto general; carril de productos más consultados si se decide usarlo; datos iniciales y revisión del contenido institucional. Añadirlas durante el refinamiento, no automáticamente al sprint en curso.

## Regla de terminado

Cada tarjeta debe describir valor observable, permisos, estados vacíos/errores y prueba de aceptación. La funcionalidad heredada ya tiene evidencia local en GLG; solo pasa a `Terminado` cuando además se acepta en la revisión del sprint. El despliegue de destino se controla como trabajo separado.
