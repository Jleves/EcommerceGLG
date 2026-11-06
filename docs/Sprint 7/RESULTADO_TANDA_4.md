# Resultado — Tanda 4: sitio público y carrito

**Estado: COMPLETA** — 06/11/2026. Se implementó solo la interfaz pública y el carrito de la tanda 4. No se inició la validación integral de la tanda 5 ni se creó commit.

## Revisión previa y alcance

La tanda 2 estaba aplicada: `GET /api/public/offers` devuelve productos distintos ordenados, con `precioOferta` y `porcentajeDescuento` anulables; catálogo y ficha eligen la variante correspondiente. El árbol de trabajo estaba limpio. El frontend todavía mostraba únicamente `precioReferencia` y lo copiaba al carrito. Se revisaron las vistas públicas, el servicio HTTP, la ruta, el carrito y sus pruebas relacionadas. No fue necesario ampliar el análisis fuera de esas dependencias directas.

Se previeron cambios en servicio y ruta públicos, home, página nueva de ofertas, tarjeta, ficha, carrito, estilos y pruebas. Quedaron fuera de alcance cambios de backend, administración, documentación histórica de promociones y recorrido integral con API/MySQL y navegador reales.

## Cambios realizados

- `getPublicOffers(page, size)` consulta `/api/public/offers` con lectura anónima y señal de cancelación. `/ofertas` presenta 20 productos por página, sin filtros, con estados de carga, vacío y error.
- El home muestra las seis mejores ofertas en el orden recibido, con carril y controles de desplazamiento equivalentes a categorías, más un enlace a `/ofertas`. La referencia a «ocho mejores» en el plan se corrigió a seis según confirmación del solicitante.
- Tras la revisión visual del solicitante, el carril del home pasó a tarjetas horizontales compactas y redujo su espacio vertical; el catálogo y `/ofertas` conservan sus tarjetas normales. En el navegador local a 754 px de ancho, la sección mide aproximadamente 438 px.
- Ante dos comentarios visuales posteriores, el ancho de la tarjeta horizontal se redujo de 560 a 370 px y la columna de imagen de 180 a 145 px. El bloque de texto y el pie miden 223 px en el navegador local a 754 px de ancho; se confirmó visualmente que caben la información y el botón y queda visible gran parte de la tarjeta siguiente. El build volvió a pasar.
- En una nueva revisión visual, el pie del carril pasó a ocupar ambas columnas de la tarjeta. El botón queda centrado bajo imagen y descripción, sin el espacio vacío separado bajo la imagen. Se verificó en el navegador local y se repitieron build y `git diff --check` con resultado PASS.
- A pedido del solicitante, la sección completa de ofertas se movió antes de categorías en el home. Una prueba de interfaz verifica el orden de las secciones; pasaron las 25 pruebas enfocadas de páginas públicas, el build y `git diff --check`.
- La franja de información comercial pasó a ocupar todo el ancho y usa el terracota de marca (`#a34a2e`); las tres tarjetas permanecen claras y alineadas dentro del contenedor público. Se comprobó en el navegador a 795 y 390 px: tres columnas en escritorio y dos más una tarjeta completa en móvil. Las 30 pruebas públicas relacionadas, lint y build pasaron.
- A pedido del solicitante, el fondo de esa franja se suavizó a `#c98269`; las tarjetas y el terracota de suscripción conservaron sus colores. Se comprobó el color aplicado en el navegador y se repitió el build.
- En la revisión siguiente, el solicitante eligió `#fffdf8` para el fondo de la franja de servicios. Se aplicó el token `--surface` sin modificar las tarjetas, sus bordes ni el terracota de suscripción.
- La indicación posterior precisó que servicios debe tener exactamente el mismo terracota e intensidad que suscripción. El fondo de servicios volvió a `var(--accent)` (`#a34a2e`), el mismo token usado en suscripción.
- Se probó un degradado para suavizar los límites de la franja. El solicitante prefirió quitarlo y se volvió temporalmente al fondo terracota uniforme.
- En una nueva revisión visual, se pidió que la franja de servicios tenga el mismo negro mate y el borde izquierdo del hero. Se aplicó `var(--charcoal)` al fondo y el borde `var(--accent-light)` de 8 px, reducido a 5 px en móvil; las tarjetas claras conservan su contenido y estilo.
- La opción elegida para servicios usa el fondo crema `var(--sand-soft)`, sin borde izquierdo, con una línea terracota centrada inicialmente de 64 × 2 px sobre las tarjetas. El espacio vertical pasa a 38 px en escritorio y 30 px en móvil. Se conservan las tarjetas claras y su distribución adaptable. Se revisó en navegador a 795 y 390 px, sin desborde horizontal; `npm run build` y `git diff --check` pasaron.
- A pedido del solicitante, la línea superior se extendió a 96 × 2 px y se agregó otra igual, centrada debajo de las tarjetas. Ambas mantienen el mismo color terracota y la separación adaptable. Se revisaron en navegador de escritorio y móvil; `npm run build` y `git diff --check` pasaron.
- En la revisión siguiente se quitó la línea superior. El estado actual conserva solo la línea inferior, centrada, de 100 × 2 px; el fondo crema y las tarjetas no cambian. Se comprobó visualmente en el navegador; `npm run build` y `git diff --check` pasaron.
- Las tres tarjetas de servicios reciben un efecto suave al pasar el cursor: borde terracota claro, sombra y elevación de 2 px. El mismo efecto acompaña el foco de teclado en las tarjetas con enlace; en pantallas táctiles no se activa el hover. Se comprobó visualmente la primera tarjeta en el navegador; `npm run build` y `git diff --check` pasaron.
- `ProductCard` y la ficha muestran el precio original con `<del>`, precio final destacado y descuento. Un porcentaje serializado como `0` se ve `<1 %`; al elegir otra variante cambia el precio y el distintivo. Sin oferta se muestra solo el precio de referencia.
- El carrito guarda `precioOferta ?? precioReferencia` al agregar y vuelve a consultarlo al abrir y antes de pedir presupuesto. Si se edita o termina una oferta, se actualizan subtotal, total y `localStorage`. El texto explicativo usa «Precios orientativos» y `cartMessage(...)` sigue sin importes.

**Diferencias respecto del plan:** no hizo falta extraer un componente de precios compartido; las vistas solo comparten el formato existente y las reglas visuales CSS. No hubo cambios de contrato ni backend.

## Tests ejecutados

| Grupo | Qué demuestra | Resultado |
| --- | --- | --- |
| Servicio y rutas públicas | URL, página, tamaño, lectura anónima y cancelación; navegación a `/ofertas` | PASS |
| Home y página | Seis ofertas en orden, enlace, paginación de 20, carga, vacío y error | PASS |
| Tarjeta y ficha | Precio tachado y final, `<1 %`, cambio a variante sin oferta, catálogo filtrado y relacionados | PASS |
| Carrito y WhatsApp | Alta con oferta, edición y terminación tras refrescar, subtotal y `localStorage` vigentes, mensaje sin importes | PASS |
| Regresión frontend | `npm test`: 40 archivos, 350 pruebas; incluye catálogo, carrito, contacto y panel administrativo | 350 PASS, 0 fallos |
| Calidad y empaquetado | `npm run lint`, `npm run build`, `git diff --check` | PASS |

Después del ajuste visual se repitió `npm run build` con resultado PASS y se verificó la presentación del home en el navegador local. No se repitió la suite de pruebas por tratarse solo de estilos.

## Incidentes

### INC-401 — Consulta ambigua en una prueba del carril

**Contexto:** primera ejecución enfocada después de agregar la prueba de estados del home.

**Test/comportamiento afectado:** `PublicPages.test.jsx`, estado de carga del carril de ofertas.

**Esperado:** localizar el mensaje «Cargando ofertas…» con `role="status"`.

**Obtenido:** la consulta global `getByRole('status')` encontró también los estados de categorías y marcas y el test falló; no indicó una falla de la interfaz.

**Clasificación:** defecto del test.

**Causa:** el selector asumía incorrectamente que el home tenía un único estado de carga.

**Resolución:** seleccionar el texto específico de ofertas y verificar su rol; se repitió la prueba enfocada y luego la suite completa.

**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS. **Riesgo o aprendizaje derivado:** en una vista con cargas independientes, los tests deben buscar el estado del componente que verifican.

## Tests faltantes y riesgos residuales

No faltan pruebas planificadas para esta tanda. La visualización se verificó con pruebas de componentes y datos simulados; no se recorrió el ciclo completo con API, MySQL y navegador reales, que corresponde a la tanda 5. `localStorage` puede conservar temporalmente una copia anterior hasta que se abre el carrito; el presupuesto vuelve a consultar el precio vigente. Una prueba local no valida el entorno de despliegue.

## Criterio de cierre

```text
[x] Seis ofertas y navegación a /ofertas con paginación y estados verificados.
[x] Precios y variantes consistentes en tarjetas, catálogo y ficha.
[x] Carrito usa el importe vigente al abrir y antes de presupuestar.
[x] WhatsApp conserva el mensaje sin importes.
[x] Suite frontend, lint, build y diff --check terminados con éxito.
[x] Incidente, límite de validación y riesgos residuales registrados.
[x] Sin cambios de la tanda 5.
```

**Decisiones pendientes descubiertas:** ninguna. Antes de aprobar la siguiente tanda, el responsable humano debería revisar la presentación visual y el recorrido con datos reales previstos para la tanda 5.
