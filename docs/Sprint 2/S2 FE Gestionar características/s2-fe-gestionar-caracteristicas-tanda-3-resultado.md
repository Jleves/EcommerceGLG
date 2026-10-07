# S2 FE Gestionar características — resultado de la tanda 3

**Estado:** COMPLETA.

## Alcance implementado

- Alta desde el botón superior, incluso con listado vacío. Modal centrado de un campo, nombre obligatorio, máximo 160 caracteres y envío recortado.
- Edición de la definición elegida mediante GET por ID antes de precargar el nombre. Carga, errores de consulta y cancelación del GET al cerrar.
- POST/PUT enviados una sola vez, cierre y recarga tras éxito. Durante el envío no se puede duplicar ni cerrar por botón o Escape.
- Errores 400/409 conservan el formulario y el valor. Un 404 al consultar o guardar orienta a actualizar la lista. Red/5xx bloquea mutaciones hasta una lectura correcta de **Consultar estado actual**; la pantalla advierte que esa lectura no determina si la solicitud anterior se aplicó.
- Avisos de éxito programados por cuatro segundos, con reinicio del plazo ante uno nuevo y limpieza al desmontar. Los errores permanecen hasta intervención del usuario.

**Fuera de alcance:** eliminación, advertencia de impacto, confirmación DELETE y recorrido integral de la tanda 4.

**Diferencias respecto del plan:** ninguna funcional. El formulario deja habilitada la acción **Cancelar** durante el bloqueo de revisión, mientras mantiene inhabilitados el campo y el envío; durante una solicitud pendiente también se bloquea Cancelar.

**Contexto adicional requerido:** se revisaron el patrón de formularios y errores de Categorías y los tests de rutas, Productos y Categorías para implementar y verificar la recuperación y la regresión. No fue necesario reconsiderar arquitectura ni contratos.

## Tests ejecutados

| Categoría | Qué se ejecutó | Qué demuestra | Resultado |
| --- | --- | --- | --- |
| Formulario | `CharacteristicForm.test.jsx` (3) | Obligatorio, espacios, límite 160, recorte, error de campo, precarga y bloqueo | PASS |
| Alta y edición | `CharacteristicManagementPage.test.jsx` (12) | GET por ID, POST/PUT con ID y cuerpo correctos, recarga, conteo recibido, cierre y cancelación | PASS |
| Errores y operación | Casos de página incluidos arriba | 400/409 conservan valor, 404 exige refrescar, red/503 bloquean, consulta fallida mantiene bloqueo y consulta correcta lo levanta | PASS |
| Avisos | Caso de temporizador incluido arriba | Programación a 4000 ms, reinicio, desaparición al ejecutarse y limpieza | PASS |
| Servicio y regresión | `characteristicService.test.js`, `AppRouter.products.test.jsx`, `AppLayout.test.jsx`, `ProductManagementPage.test.jsx`, `CategoryManagementPage.test.jsx` | Contrato HTTP sin reintentos mutantes, acceso por rol y anónimo, navegación, selector de Productos y gestión de Categorías | PASS |
| Calidad | `npm run lint`, `npm run build` | Reglas estáticas y empaquetado de producción | PASS |

**Total de regresión relacionada:** 83 tests en 7 archivos; 83 exitosos, 0 fallidos al cierre.

## Incidentes

### INC-003 — Lectura denegada a esbuild en sandbox

**Contexto:** primer intento de Vitest y de build dentro del sandbox.

**Test/comportamiento afectado:** carga de `vite.config.js` por esbuild.

**Esperado:** iniciar los tests y compilar.

**Obtenido:** `Cannot read directory "../../../../../..": Access is denied` y fallo de resolución de `vite.config.js`.

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** restricción de lectura del sandbox ya observada en tandas 1 y 2.

**Resolución:** repetir Vitest y build fuera del sandbox mediante la escalación disponible. No se cambió la configuración.

**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS.

**Riesgo o aprendizaje derivado:** en este entorno, una falla inicial de esbuild dentro del sandbox no indica un defecto del proyecto.

### INC-004 — Expectativas incorrectas en tests nuevos

**Contexto:** la primera corrida focal falló al buscar el campo por nombre exacto cuando había un error, y la siguiente falló en las pruebas de Escape, texto de advertencia y limpieza del aviso.

**Test/comportamiento afectado:** acceso al nombre, cierre por Escape, bloqueo por red y temporizador de avisos.

**Esperado:** hallar el campo como «Nombre», cerrar el modal tras `cancel`, mostrar la advertencia persistente y limpiar el temporizador del aviso.

**Obtenido:** `FormField` incluía el error dentro del `label` y por eso el nombre accesible pasaba a «Nombre + error»; el evento nativo se despachaba fuera de `act`; el test buscaba una frase distinta de la usada dentro del modal; el espía de `clearTimeout` contaba también temporizadores internos de `userEvent`.

**Clasificación:** defecto del test y detalle de accesibilidad del campo.

**Causa:** consultas y mediciones demasiado generales; el campo no tenía un nombre accesible estable mientras se mostraba el error.

**Resolución:** se asignó `aria-label="Nombre"` al campo y se acotaron los tests al evento gestionado por Testing Library, al texto real y a los identificadores de los temporizadores de cuatro segundos.

**Código productivo modificado:** sí. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS.

**Riesgo o aprendizaje derivado:** probar el nombre accesible con el error visible evita regresiones de formularios; los espías de temporizadores deben aislar el temporizador de la funcionalidad probada.

## Tests planificados frente a ejecutados

- **Formulario:** cubierto por tests directos de validación y envío. El atributo `maxLength` impide introducir un carácter 161; la API sigue validando.
- **Alta/edición:** cubierto en UI y contrato de servicio. La recarga usa un conteo de productos simulado y el mismo ID; no demuestra físicamente que MySQL conserve asociaciones tras PUT.
- **Errores y operación:** cubiertos 400, 404, 409, red y 503; Cancelar, Escape, pendiente y revisión posterior. No se probó una respuesta real del backend en navegador.
- **Avisos:** cubiertos plazo de 4000 ms, renovación, limpieza y persistencia de errores. No hubo inspección visual manual de transiciones.

**Pruebas faltantes dentro de la tanda:** ninguna automatizada planificada. La verificación de asociaciones reales y apariencia en navegador con backend real queda sin ejecutar en esta tanda; riesgo residual de diferencias de integración o presentación. Se recomienda incluirla en el recorrido integral de la tanda 4.

## Riesgos residuales y decisiones

- Una lectura correcta tras red/5xx desbloquea mutaciones, pero no demuestra si la solicitud anterior se aplicó. La advertencia permanece en el flujo de revisión hasta consultar; la persona debe inspeccionar la lista.
- La API no expone control de versión: otra edición concurrente entre GET y PUT podría sobrescribirse.
- La preservación real de asociaciones se apoya en el contrato backend existente; los tests de esta tanda usan el servicio simulado.
- No se descubrieron decisiones pendientes ni se modificaron backend, base de datos, dependencias o configuración.

## Archivos modificados

- `Frontend/react-starter/src/catalog/characteristics/components/CharacteristicForm.jsx`
- `Frontend/react-starter/src/catalog/characteristics/components/CharacteristicForm.test.jsx`
- `Frontend/react-starter/src/catalog/characteristics/pages/CharacteristicManagementPage.jsx`
- `Frontend/react-starter/src/catalog/characteristics/pages/CharacteristicManagementPage.test.jsx`
- `docs/Sprint 2/S2 FE Gestionar características/s2-fe-gestionar-caracteristicas-plan-implementacion.md`
- Este informe.

## Criterio de cierre

- [x] Alta y edición con precarga por ID funcionan en la ruta protegida para los roles permitidos.
- [x] Validación, errores, cancelación y resultado incierto comprobados.
- [x] Tests ejecutados, incidentes y riesgos registrados.

La tanda 4 sigue pendiente de aprobación y ejecución.
