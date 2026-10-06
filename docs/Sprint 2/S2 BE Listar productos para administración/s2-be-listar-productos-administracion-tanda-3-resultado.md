# Tanda 3 — resultado de ejecución

**Estado:** COMPLETA.

## Alcance ejecutado

Se incorporaron los modales de alta y edición desde la lista de Productos. El alta usa solo categorías activas; la edición carga el detalle completo y permite conservar la categoría inactiva actual. El formulario maneja nombre, categoría, descripción, precio, disponibilidad y cero o más características sin IDs duplicados. La edición envía siempre `caracteristicas`, incluso `[]`; no envía `activo` ni `destacado`. Las mutaciones deshabilitan los reintentos automáticos de autenticación y CSRF, bloquean envíos simultáneos y recargan la página actual. Si la página queda fuera del total, se consulta la última disponible.

Ante 400/404 se mantienen el modal y sus valores. Ante red/5xx se impide otra mutación hasta **Consultar estado actual**; la lectura no determina por sí sola si la solicitud previa se aplicó. La ruta `/app/catalog/products` sigue bajo `ProtectedRoute`, accesible solo con sesión para `SUPER_ADMIN`, `ADMIN` y `USER`. Categorías también requiere sesión y `/app/admin/users` sigue reservado a `SUPER_ADMIN`.

**Fuera de alcance:** desactivar, reactivar y confirmar cambios de estado, previstos para la tanda 4. No se modificaron endpoints ni permisos del backend.

**Contexto adicional requerido:** se consultaron los DTO de productos y características para confirmar límites, presencia obligatoria de la lista en edición y precisión decimal; se revisó el guard de rutas existente para verificar la protección pedida.

## Diferencias respecto del plan

Ninguna funcional. El precio viaja como cadena decimal JSON para no perder precisión antes de que el backend lo convierta en `BigDecimal`.

## Tests ejecutados

| Grupo | Ejecución | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| Formulario | `ProductForm.test.jsx` (2) | Reglas de precio, límites de campos, valor requerido, IDs únicos y lista vacía al quitar asociaciones | PASS |
| Alta, edición y errores | `ProductManagementPage.test.jsx` (13) | Alta sin y con varias características, edición con categoría inactiva, 400/404, cancelación, bloqueo durante envío, resultado incierto, recarga y última página | PASS |
| Contrato HTTP | `productService.test.js` (3) | Rutas de detalle y definiciones, POST/PUT con cookies, CSRF y cuerpo con características | PASS |
| Seguridad de rutas | `AppRouter.products.test.jsx` (5), `ProtectedRoute.test.jsx` (3), `AdminAccess.test.jsx` (4) | Acceso de roles permitidos; redirección sin sesión antes de solicitar productos; usuarios solo para `SUPER_ADMIN` | PASS |
| Regresión | `npm test -- --maxWorkers=2` | Categorías, usuarios, autenticación y lectura de productos | PASS: 190/190, 21 archivos |
| Verificación final focal | `npm test -- --run ...` | Productos, formulario, servicios y rutas tras agregar los dos últimos casos | PASS: 23/23 |
| Estática y compilación | `npm run lint`, `npm run build`, `git diff --check` | ESLint, bundle de producción y ausencia de errores de whitespace | PASS |

## Incidentes

### INC-301 — consulta ambigua en tests del modal

**Contexto:** primera corrida focal de pruebas nuevas.  
**Test/comportamiento afectado:** alta y error 400; selección del campo Nombre.  
**Esperado:** seleccionar el campo del modal.  
**Obtenido:** `getByRole` encontró también el buscador de la página.  
**Clasificación:** defecto del test.  
**Causa:** ambos controles tenían la etiqueta Nombre.  
**Resolución:** acotar las consultas al `dialog` mediante `within`.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** las consultas de formularios modales deben tener alcance explícito.

### INC-302 — etiqueta accesible alterada por error de campo

**Contexto:** corrida focal posterior, tras respuesta 400.  
**Test/comportamiento afectado:** conservación del nombre ingresado.  
**Esperado:** el campo conservaría el nombre y la etiqueta accesible `Nombre`.  
**Obtenido:** la etiqueta accesible era `Nombre Nombre repetido`.  
**Clasificación:** defecto de implementación.  
**Causa:** el mensaje de error estaba dentro del elemento `label`.  
**Resolución:** mover el mensaje fuera del `label`.  
**Código productivo modificado:** sí. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** los mensajes de validación no deben modificar el nombre accesible del control.

### INC-303 — precio fraccionario menor que uno rechazado

**Contexto:** revisión del patrón de validación antes del cierre.  
**Test/comportamiento afectado:** precio `0.01`.  
**Esperado:** precio positivo con dos decimales aceptado.  
**Obtenido:** el patrón inicial exigía un dígito no nulo antes del punto.  
**Clasificación:** defecto de implementación.  
**Causa:** el lookahead del patrón terminaba al llegar al punto decimal.  
**Resolución:** validar formato y positividad por separado; agregar test para `0.01`.  
**Código productivo modificado:** sí. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** los límites decimales requieren casos menores que uno.

### INC-304 — lectura de configuración denegada en sandbox

**Contexto:** primeras ejecuciones de Vitest y build.  
**Test/comportamiento afectado:** inicio del runner y compilación de Vite.  
**Esperado:** leer `vite.config.js`.  
**Obtenido:** `Access is denied` al resolver la configuración.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** restricción de lectura del sandbox.  
**Resolución:** ejecutar Vitest y build fuera del sandbox mediante aprobación de herramienta.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** repetir estas verificaciones fuera del sandbox en este entorno.

## Tests faltantes y riesgos residuales

No se hizo recorrido manual con backend y navegador reales. La integración HTTP real de los nuevos modales queda por comprobar en un entorno ejecutado; los tests usan respuestas simuladas. El riesgo es una diferencia de integración no representada por los mocks. Se recomienda validar el recorrido completo en tanda 4, como establece el plan.

El rendimiento con datos de producción y la edición concurrente entre sesiones no quedan probados. Las transiciones de estado y su resultado incierto corresponden a tanda 4. La lectura de recuperación permite revisar el estado, pero no demuestra la aplicación de la mutación previa.

**Decisiones pendientes descubiertas:** ninguna.

## Archivos modificados

- `Frontend/react-starter/src/catalog/products/pages/ProductManagementPage.jsx` y su test.
- `Frontend/react-starter/src/catalog/products/components/ProductForm.jsx` y su test.
- `Frontend/react-starter/src/catalog/products/services/productService.js`, `characteristicService.js` y test del servicio.
- `Frontend/react-starter/src/services/endpoints.js`.
- Este resultado y la sección `Resultado de ejecución` del plan vivo.

## Criterio de cierre

- [x] Alta y edición completas funcionan con características y categorías elegibles.
- [x] Errores, cancelación y envíos duplicados comprobados.
- [x] Tests y regresión de lectura ejecutados; incidentes registrados.

La tanda 4 requiere aprobación explícita antes de ejecutarse.
