# Resultado de ejecución — Tanda 4

**Fecha:** 17/10/2026  
**Estado:** COMPLETA

## Alcance implementado

- Cada producto activo o inactivo muestra «Imágenes». El botón abre un único diálogo nativo identificado con el nombre del producto; al cerrarlo se desmonta y la siguiente apertura consulta la galería correspondiente.
- Dentro del diálogo se ven las miniaturas en orden y la marca «Principal». Se puede cargar una foto, elegir principal, mover con «Subir» y «Bajar», reemplazar y borrar. Reemplazo y borrado piden confirmación dentro del mismo diálogo. Al llegar a cinco fotos se deshabilita la carga.
- El servicio frontend usa los seis contratos administrativos de imágenes existentes. Carga y reemplazo envían `FormData` con `file`, sin `Content-Type` manual; `apiClient` mantiene cookies y CSRF. Las mutaciones no tienen reintentos automáticos.
- Después de cada mutación exitosa se consulta la galería antes de mostrarla. Ante una respuesta incierta, o una consulta fallida posterior al cambio, se bloquean nuevas mutaciones hasta una lectura exitosa. Crear y editar datos de producto siguen sin archivos.

**Fuera de alcance:** cambios de backend, AWS, migraciones, pantallas públicas y el recorrido integral de staging de la tanda 5.  
**Contexto adicional requerido:** se inspeccionaron el cliente HTTP y el controlador de imágenes para confirmar multipart, CSRF y cuerpos JSON, además de los tests existentes de Productos y la convención local de diálogo. No se requirió ampliar la arquitectura.

## Cambios realizados

- Nuevo `ProductImageManager.jsx` y sus tests de componente.
- Integración del botón y modal en `ProductManagementPage.jsx`; controles de lista adaptados para envolver en pantallas angostas.
- Rutas en `endpoints.js`, funciones en `productService.js` y pruebas de su contrato HTTP.
- Tras la revisión en navegador, se aclaró en el alta que las fotos se cargan después de guardar. El aviso de creación también indica el siguiente paso.
- En una segunda revisión, «Cargar foto» pasó a abrir el selector del dispositivo y «Subir imagen» quedó como acción de envío. Se muestra el nombre del archivo elegido y se aclara que se sube una foto por vez. El contrato de la API y el máximo de cinco no cambiaron.
- A pedido del usuario, «Subir imagen a principal» mueve la imagen al primer lugar y luego la marca principal. Los demás botones indican la posición de destino y solo cambian el orden. Si falla una de las dos solicitudes necesarias para subir al primer lugar, se exige consultar la galería antes de otro cambio.
- **Diferencias respecto del plan:** mover al primer lugar también elige principal, decisión posterior del usuario. No cambió la API; la lectura posterior a cada mutación muestra el estado confirmado por el servidor.

## Tests ejecutados

| Grupo | Qué se ejecutó | Qué demuestra | Resultado |
| --- | --- | --- | --- |
| Componentes | `ProductImageManager.test.jsx` (5) | Diálogo y miniaturas, carga sin cerrarlo, máximo cinco, principal y orden sin archivos, confirmaciones internas, resultado incierto, Escape y bloqueo durante envío | 5 PASS |
| Integración y regresión de Productos | `ProductManagementPage.test.jsx` (20), `ProductForm.test.jsx` (3) | Producto correcto, estado limpio al cambiar de producto, creación seguida de «Imágenes», formulario sin `FormData`, edición y flujos previos | 23 PASS |
| HTTP y seguridad de cliente | `productService.test.js` (10), `apiClient.test.js` (14) | Endpoints, métodos, orden JSON, multipart sin encabezado manual, cookies, CSRF y ausencia de reintentos en mutaciones | 24 PASS |
| Regresión frontend completa | `npm test -- --maxWorkers=1` | Suite completa de 28 archivos | 263 PASS, 0 FAIL |
| Calidad y construcción | `npm run lint`, `npm run build`, `git diff --check` | ESLint, compilación Vite y espacios del diff | PASS; solo avisos LF/CRLF de Git |

Después del ajuste de textos de ayuda, se repitieron las pruebas relacionadas de modal, pantalla y formulario (28 PASS), ESLint y build (PASS). La suite completa indicada arriba corresponde a la implementación anterior a ese ajuste de textos.

Después del ajuste de los dos botones, se repitieron las pruebas del modal y la pantalla (25 PASS), ESLint y build (PASS).

Después del cambio de orden/principal, se repitieron las pruebas del modal y la pantalla (27 PASS), ESLint y build (PASS). Se añadieron casos de destino intermedio y fallo entre las dos solicitudes.

**Tests exitosos al cierre:** 263 de la suite completa en serie. **Tests fallidos al cierre:** 0 en esa corrida. **Incidente observado:** la suite paralela tuvo 1 fallo en cada una de dos corridas (262 PASS, 1 FAIL), detallado abajo.

## Incidentes

### INC-401 — Navegación MFA observada antes de completarse en la suite paralela

**Contexto:** regresión frontend completa ejecutada en paralelo tras la implementación.  
**Test/comportamiento afectado:** `MfaLoginFlow.test.jsx`, caso «202 bloquea rutas privadas; acepta pegado con ceros y autentica solo tras confirmar».  
**Esperado:** después de autenticar, ubicación `/app/profile?tab=security`.  
**Obtenido:** el test leyó `/login/mfa` inmediatamente después de observar estado `authenticated`; 1 fallo en cada una de dos corridas paralelas. El mismo archivo pasó aislado (17/17) y la suite completa pasó en serie (263/263).  
**Clasificación:** defecto probable de sincronización del test; no hay evidencia de defecto en imágenes.  
**Causa:** el test espera el cambio de estado de autenticación, pero comprueba la navegación sin esperar a que se complete. La carga paralela hace visible esa diferencia temporal.  
**Resolución:** se mantuvo intacto por estar fuera de la tanda. Se repitió aislado y luego toda la suite en serie; ambos resultados fueron PASS.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS en aislamiento y suite completa en serie; falla reproducible en la corrida paralela.  
**Riesgo o aprendizaje derivado:** CI paralela puede informar un falso fallo hasta que se sincronice la expectativa de navegación en una tarea de autenticación.

## Tests faltantes y riesgos residuales

- **Recorrido real en staging con navegador, backend y CDN:** no ejecutado; está previsto para la tanda 5. Riesgo: fallos de configuración o entrega de imágenes que los mocks no detecten. Impacto: miniaturas rotas o errores de carga reales. Recomendación: recorrer carga y gestión completa en staging.
- **Prueba automatizada con lector de pantalla o navegador real del diálogo nativo:** no ejecutada; los tests comprueban nombre accesible, controles etiquetados, confirmaciones y Escape en jsdom. Riesgo: diferencias de foco o comportamiento del navegador. Impacto: uso menos claro con tecnología asistiva. Recomendación: revisión manual en la validación integral.
- **Cambio combinado de orden y principal:** usa dos endpoints existentes, sin transacción conjunta. Ante fallo parcial el modal bloquea cambios y exige relectura, pero puede quedar temporalmente la imagen primera sin ser principal. Recomendación: comprobar este caso en staging durante la tanda 5.

**Decisiones pendientes descubiertas:** ninguna para esta tanda. El incidente MFA se deja registrado para su área correspondiente; no cambia el contrato de imágenes.

## Archivos modificados

- Nuevos: `ProductImageManager.jsx`, `ProductImageManager.test.jsx` y este resultado.
- Modificados: `ProductManagementPage.jsx`, `ProductManagementPage.test.jsx`, `ProductForm.jsx`, `productService.js`, `productService.test.js`, `endpoints.js` y la sección de resultado de la tanda 4 en `PLAN_IMPLEMENTACION_IMAGENES.md`.

## Criterio de cierre

- [x] Cada producto tiene botón «Imágenes» y se consulta la galería de ese producto.
- [x] Carga, principal, orden, reemplazo y borrado ocurren dentro de un solo modal; reemplazo y borrado requieren confirmación.
- [x] Resultado incierto bloquea reenvío hasta consultar el estado; crear y editar datos no incluyen archivos.
- [x] Tests de interacción, accesibilidad básica, HTTP y regresión relacionada pasan; lint y build pasan.
- [x] Incidente de la suite paralela, pruebas faltantes y riesgos quedan visibles.

Antes de aprobar la tanda 5, revisar el flujo en navegador real y el incidente MFA si CI ejecuta los tests en paralelo. La implementación se detiene en la tanda 4.
