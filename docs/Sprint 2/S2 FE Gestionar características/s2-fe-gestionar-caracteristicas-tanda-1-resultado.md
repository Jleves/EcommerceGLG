# S2 FE Gestionar características — resultado de la tanda 1

**Estado:** COMPLETA  
**Fecha:** 2026-10-06

## Alcance implementado

- Se trasladó `listCharacteristics` al módulo `catalog/characteristics/services/` y se agregaron `getCharacteristic`, `createCharacteristic`, `updateCharacteristic` y `deleteCharacteristic`.
- `ENDPOINTS.CATALOG.CHARACTERISTIC(id)` codifica el ID. POST y PUT envían solo `{ nombre }`. DELETE exige HTTP 204. Las lecturas admiten `AbortSignal`.
- Las tres mutaciones usan `apiRequest` con `retryAuth: false` y `retryCsrf: false`. `apiRequest` proporciona cookies y CSRF. Se actualizaron los imports y mocks de Productos y se retiró el servicio anterior.

No se implementaron pantalla, ruta, navegación, formularios ni permisos de frontend: corresponden a las tandas 2 a 4. No hubo cambios de backend, dependencias ni configuración.

## Diferencias respecto del plan

Ninguna funcional. Este archivo separado complementa la sección `Resultado de ejecución` del plan.

## Tests ejecutados

| Categoría | Ejecución | Qué demuestra | Resultado |
| --- | --- | --- | --- |
| Contrato HTTP | `characteristicService.test.js` (15 tests) | URL de colección y detalle con ID codificado, GET con señal, POST/PUT con solo `nombre`, cookies, CSRF, DELETE 204 y rechazo de otra respuesta | PASS |
| Seguridad de mutación | Casos parametrizados del mismo archivo | POST, PUT y DELETE se envían una sola vez ante 401, CSRF inválido, HTTP 503 y error de red; ante 403 puede renovarse el token CSRF para un intento manual sin repetir la mutación | PASS |
| Regresión de Productos | `productService.test.js` (8) y `ProductManagementPage.test.jsx` (18) | Productos sigue consultando características desde el nuevo servicio y conserva el selector y los flujos relacionados | PASS |
| Regresión del cliente HTTP | `apiClient.test.js` (14) | Conserva cookies, CSRF, errores y política general de reintentos de la capa compartida | PASS |
| Análisis y compilación | `npm run lint`; `npm run build` | Lint sin errores y bundle con el nuevo import resuelto | PASS |

**Total:** 55 tests aprobados en 4 archivos; ninguno fallido por comportamiento.

## INC-001 — esbuild no pudo leer la configuración en el sandbox

**Contexto:** primer intento de Vitest y build dentro del sandbox.  
**Test/comportamiento afectado:** arranque de Vitest y compilación de Vite; no llegaron a evaluar el código.  
**Esperado:** cargar `vite.config.js` y ejecutar las verificaciones.  
**Obtenido:** `Cannot read directory "../../../../../..": Access is denied` y fallo al resolver `vite.config.js`.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** permiso de lectura insuficiente para esbuild en el entorno restringido.  
**Resolución:** repetir ambas ejecuciones con autorización fuera del sandbox; Vitest, lint y build finalizaron correctamente.  
**Código productivo modificado:** no.  
**Tests modificados:** no.  
**Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** la ejecución de Vite/Vitest en este entorno puede requerir autorización de lectura adicional.

## Tests faltantes y riesgos residuales

No faltan pruebas de las planificadas para la tanda 1. Las pruebas de contrato simulan `fetch`; no constituyen un recorrido con backend y navegador reales. Ese recorrido queda para la tanda 4. La interfaz de gestión y la autorización de su ruta siguen sin implementar y se tratarán en las tandas 2 a 4.

No se descubrieron decisiones pendientes.

## Archivos modificados

- `Frontend/react-starter/src/services/endpoints.js`
- `Frontend/react-starter/src/catalog/characteristics/services/characteristicService.js` y `characteristicService.test.js`
- `Frontend/react-starter/src/catalog/products/pages/ProductManagementPage.jsx` y `ProductManagementPage.test.jsx`
- `Frontend/react-starter/src/catalog/products/services/productService.test.js`
- Retirado `Frontend/react-starter/src/catalog/products/services/characteristicService.js`
- Plan de implementación y este resultado

## Criterio de cierre

- [x] Contrato HTTP y ausencia de reintentos comprobados.
- [x] Productos sigue leyendo características mediante el nuevo import.
- [x] Tests ejecutados, incidente y riesgos registrados.

Antes de aprobar la siguiente tanda, conviene revisar el contrato del servicio y confirmar que se mantiene el alcance de ruta, navegación y listado previsto para la tanda 2.
