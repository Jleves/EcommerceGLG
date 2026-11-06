# Resultado — Tanda 3: gestión de ofertas en el panel

**Estado: COMPLETA** — 06/11/2026. La gestión prevista está implementada y los gates de backend y frontend pasan. El test de Contacto se actualizó tras confirmar que el encabezado corto es una decisión deliberada del solicitante. No se inició la tanda 4 ni se creó un commit.

## Alcance implementado

- Se agregó `GET /api/catalog/offers/by-products?productIds=...` para consultar las ofertas de los productos visibles en la página administrativa. Devuelve `OfferResponse` también para ofertas ocultas por producto o variante inactivos. Valida IDs y conserva la autorización de los tres roles de catálogo.
- La ruta privada `/app/catalog/offers` lista 20 ofertas por página, indica publicación y disponibilidad, permite elegir un producto activo para el alta, editar y terminar con confirmación. La terminación recarga la lista y la fila desaparece.
- Productos consulta ofertas por los IDs de su página y muestra `En oferta` si cualquier variante tiene una fila, independientemente de `destacado` o de su publicación. El botón `Oferta` abre el mismo formulario que usa la pantalla de Ofertas.
- El formulario consulta producto y ofertas actuales, permite elegir variante, precarga el precio existente, valida el precio final con precisión decimal y evita el doble envío. Tras un resultado incierto exige consultar de nuevo antes de reenviar; ante `409` recarga los datos actuales.
- Se reutilizó `apiRequest` para CSRF y se deshabilitaron los reintentos automáticos de mutaciones, como en Productos. No se modificó la interfaz pública ni el carrito.

**Diferencia respecto del plan original:** la API administrativa de ofertas solo ofrecía una lista paginada global. Por decisión consultada y aprobada antes de implementar, se agregó la consulta privada por IDs de producto en esta tanda.

**Archivos modificados:** `OfferController`, `OfferService`, `OfferRepository` y `OfferControllerTest`; `AppRouter`, `AppLayout`, `endpoints.js`, `ProductManagementPage` y sus pruebas. Se crearon `catalog/offers/{services,components,pages}` con sus pruebas. Se corrigió la expectativa de `ContactPage.test.jsx` para el texto aprobado y se actualizaron este resultado y el plan vivo. No hubo migraciones ni cambios de configuración.

**Decisiones pendientes descubiertas:** ninguna. La única ampliación de contrato se resolvió antes de la implementación.

## Verificación

| Grupo | Ejecutado y comportamiento demostrado | Resultado |
| --- | --- | --- |
| Backend HTTP y seguridad | `OfferControllerTest`: filtro por IDs, oferta oculta, vacío, IDs inválidos, `SUPER_ADMIN`/`ADMIN`/`USER`, visitante y rol no autorizado, junto a la regresión de ofertas existente | 7/7 PASS |
| Servicios y rutas frontend | `offerService.test.js` y `AppRouter.products.test.jsx`: verbos, cuerpos, paginación, ausencia de reintentos de mutaciones y ruta protegida | PASS |
| Interfaz y regresión frontend | `OfferForm.test.jsx`, `OfferManagementPage.test.jsx`, `ProductManagementPage.test.jsx`: alta y edición, variante, cartel, paginación, baja confirmada, `400/409`, respuesta incierta, doble envío y controles previos de Productos | 55/55 PASS en ejecución enfocada |
| Suite frontend | `npm test` tras corregir la expectativa de Contacto | 343/343 PASS en 39 archivos. La ejecución enfocada de ofertas, rutas y Productos obtuvo 55/55 PASS. |
| Calidad y compilación frontend | `npm run lint`, `npm run build`, `git diff --check` | PASS |
| Backend completo y empaquetado | `./mvnw.cmd package` con JDK 21 y agente Mockito; reportes Surefire de la ejecución y JAR generado | 649 tests, 0 fallos, 0 errores, 0 omitidos; exit 0 |

## Incidentes

### INC-301 — Diálogos y preparación de pruebas nuevas

**Contexto:** primera ejecución enfocada del panel. **Test/comportamiento afectado:** apertura de los diálogos de Ofertas, retorno entre páginas y actualización del cartel tras un alta. **Esperado:** diálogos accesibles y estado recargado. **Obtenido:** los diálogos no eran visibles en jsdom; una respuesta simulada devolvía una página vacía al volver y otra aserción retenía una fila ya reemplazada por React. **Clasificación:** defecto de implementación en el respaldo de `showModal`; defectos de preparación de los tests. **Causa:** faltaba el atributo `open` cuando `showModal` no existe y dos expectativas usaban estado de prueba obsoleto. **Resolución:** agregar el respaldo ya usado en Productos y corregir los datos y la consulta de los tests. **Código productivo modificado:** sí. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS, 55/55 en la ejecución enfocada final. **Riesgo o aprendizaje derivado:** la interacción en navegador real sigue para la tanda 5.

### INC-302 — Restricciones del sandbox en la suite backend

**Contexto:** primer `package` dentro del sandbox. **Test/comportamiento afectado:** suites MySQL, SMTP y servidor HTTP de suscripciones. **Esperado:** ejecutar y empaquetar toda la suite. **Obtenido:** Docker denegó el pipe, 175 pruebas quedaron omitidas y seis pruebas ajenas a ofertas fallaron por sockets SMTP y loopback; 613 se ejecutaron. **Clasificación:** configuración/infraestructura/entorno. **Causa:** falta de acceso a pipes y sockets en el sandbox. **Resolución:** repetir `package` con acceso a Docker y loopback, sin modificar pruebas o configuración. **Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS, 649 tests sin fallos, errores u omisiones; JAR generado. **Riesgo o aprendizaje derivado:** una ejecución dentro del sandbox no valida toda la suite.

### INC-303 — Expectativa desactualizada de Contacto

**Contexto:** primer `npm test` completo y reproducción aislada con `npm test -- src/public/ContactPage.test.jsx`. **Test/comportamiento afectado:** `página de contacto > abre desde el encabezado y muestra ubicación, mapa y horarios`. **Esperado por el test anterior:** encabezado «Encontranos en General Roca o escribinos para consultar lo que necesitás.». **Obtenido:** el componente renderiza «Encontranos en General Roca». **Clasificación:** defecto del test por cambio deliberado de texto. **Causa:** el solicitante confirmó que acortó el encabezado porque prefiere esa redacción; la expectativa no se había actualizado. **Resolución:** ajustar únicamente la aserción del encabezado al texto aprobado, conservando las demás comprobaciones de navegación, ubicación, mapa y horarios. **Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS, 5/5 en Contacto y 343/343 en `npm test` completo. **Riesgo o aprendizaje derivado:** ante una discrepancia de texto, confirmar la intención antes de atribuir el fallo al producto.

## Tests faltantes y riesgos residuales

- No se ejecutó interacción en navegador real ni un recorrido con frontend, API y MySQL juntos; eso pertenece a la tanda 5. Podrían aparecer diferencias de foco, modal o estado entre jsdom y navegador. Recomendación: probar ambos accesos y la baja en el recorrido integral.
- El nuevo GET por IDs se probó con H2 y MockMvc, pero no con una llamada HTTP sobre MySQL. Riesgo: una diferencia de consulta o serialización específica del entorno podría pasar inadvertida. Recomendación: incluirlo en el recorrido integral de la tanda 5.
- El listado y el cartel dependen de una lectura posterior a cada mutación y pueden quedar antiguos si otra sesión modifica una oferta; `409` fuerza recarga y una respuesta incierta exige consulta manual. La tanda 5 debe comprobar la experiencia con dos sesiones.
- No hay pruebas planificadas de esta tanda pendientes de ejecución. La prueba local no valida el entorno de despliegue.

## Cierre frente al criterio de la tanda

```text
[x] Ambas entradas de gestión implementadas con el mismo formulario.
[x] Estados de carga, conflicto, respuesta incierta y baja cubiertos por pruebas enfocadas.
[x] Navegación protegida comprobada para los tres roles permitidos.
[x] Lint y build frontend pasan.
[x] `npm test` completo en verde: 343/343 pruebas.
[x] Empaquetado backend con MySQL y regresión completa: 649 pruebas exitosas.
```

Antes de aprobar la tanda siguiente, revisar el contrato del nuevo GET administrativo. La validación en navegador y del ciclo completo queda expresamente para la tanda 5.
