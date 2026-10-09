# S2 FE Gestionar características — resultado de tanda 4

**Estado:** COMPLETA.

## Alcance y precondiciones

La tanda 3 estaba implementada: ruta protegida, listado, formularios de alta y edición, servicio HTTP con `getCharacteristic` y `deleteCharacteristic`, y bloqueo de mutaciones ante red/5xx. Se comprobó el código de la página, servicio y tests relacionados antes de editar. Esta tanda incorporó únicamente eliminación y regresión integral. Quedaron fuera cambios de backend, base de datos, valores de características dentro de Productos y nuevas decisiones de arquitectura. No fue necesario ampliar el análisis más allá de la página, servicio, rutas, estilos de Productos y tests directamente relacionados.

## Cambios realizados

- Cada card ofrece **Eliminar** con el patrón rojo `#B42318`, hover, foco y apariencia deshabilitada. La card sigue neutra y **Editar** usa azul pizarra `#1E293B`.
- Antes de mostrar la confirmación se consulta el detalle por ID con cancelación al cerrar/desmontar. El diálogo compacto y centrado muestra el nombre y `cantidadProductos` de esa respuesta, explica que se quitan los valores y unidades asociados sin borrar productos y advierte que la cantidad puede cambiar.
- Cancelar y Escape cierran sin escribir. Confirmar invoca DELETE una vez, impide duplicados y cierre mientras está pendiente, y recarga la lista tras 204. El aviso de éxito usa el temporizador existente de cuatro segundos.
- Un 404 en detalle o DELETE ofrece **Actualizar lista**. Red y 5xx mantienen el error visible y bloquean nuevas mutaciones hasta completar **Consultar estado actual**. Al terminar esa lectura se cierra la confirmación anterior para volver a revisar la lista.
- Se amplió la prueba de rutas con el recorrido listar → crear → renombrar → eliminar → Productos → Categorías. Los roles y el rechazo anónimo siguen cubiertos por los tests de rutas existentes.

**Diferencias respecto del plan:** la revisión correcta tras un DELETE incierto cierra el diálogo previo, de modo que una eventual nueva eliminación requiere otro GET y otra confirmación.

## Tests ejecutados

| Categoría | Ejecución | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| UI y operación | `CharacteristicManagementPage.test.jsx` | GET por ID, conteos 0 y 3, impacto, cancelar/Escape, DELETE único, bloqueo, recarga, 404, red, 5xx, revisión, clases de card/botones/diálogo | 20/20 PASS |
| Seguridad y recorrido | `AppRouter.products.test.jsx` | tres roles admitidos, anónimo sin GET, Usuarios restringido, flujo CRUD simulado y regreso a Productos/Categorías | 12/12 PASS |
| Contrato HTTP | `characteristicService.test.js`, `apiClient.test.js` | DELETE espera 204, cookies/CSRF, mutaciones sin reintentos, URL y señales de lectura | 29/29 PASS en suite |
| Regresión | `npm test -- --maxWorkers=1` | frontend completo: auth, administración, Productos, Categorías y Características | 245/245 PASS |
| Calidad/compilación | `npm run lint`; `npm run build` | reglas estáticas y compilación Vite | PASS / PASS |

La suite completa con paralelismo predeterminado arrojó 242/243 en dos ejecuciones previas a los dos últimos casos de eliminación: solo falló el test MFA descrito en INC-007. Aislado pasó 17/17 y la suite secuencial final pasó 245/245. No se modificó código ni tests de MFA.

## Incidentes

### INC-005 — esbuild no puede leer Vite en el sandbox

**Contexto:** primer intento de Vitest focal.

**Test/comportamiento afectado:** arranque del runner.

**Esperado:** cargar `vite.config.js` y ejecutar tests.

**Obtenido:** `Access is denied` al leer un directorio superior y error al resolver `vite.config.js`.

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** restricción de lectura del sandbox, ya observada en tandas previas.

**Resolución:** ejecución aprobada fuera del sandbox.

**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS.

**Riesgo o aprendizaje derivado:** Vitest y build precisan el permiso de lectura usado en las tandas anteriores.

### INC-006 — aviso de revisión duplicado en eliminación

**Contexto:** prueba de error 5xx y consulta posterior con el diálogo aún abierto.

**Test/comportamiento afectado:** recuperación de DELETE incierto.

**Esperado:** una acción clara para consultar el estado antes de otra mutación.

**Obtenido:** dos botones **Consultar estado actual**, uno en la página y otro en el diálogo.

**Clasificación:** defecto de implementación.

**Causa:** el aviso global de la tanda 3 no excluía el nuevo diálogo.

**Resolución:** mostrar el aviso global solo cuando no hay diálogo; conservar la acción dentro del diálogo y cerrarlo tras una consulta correcta. Se mantuvo la intención del test y se acotó su búsqueda al diálogo.

**Código productivo modificado:** sí. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS.

**Riesgo o aprendizaje derivado:** los estados globales de recuperación deben coordinarse con cada modal.

### INC-007 — navegación MFA sensible al paralelismo de la suite

**Contexto:** dos ejecuciones de `npm test` completo.

**Test/comportamiento afectado:** `MfaLoginFlow.test.jsx`, “202 bloquea rutas privadas; acepta pegado con ceros y autentica solo tras confirmar”.

**Esperado:** ubicación `/app/profile?tab=security` después de confirmar MFA.

**Obtenido:** en ambas ejecuciones paralelas el estado ya era `authenticated`, pero la ubicación seguía en `/login/mfa` al verificarla. El mismo test pasó aislado y en la suite secuencial.

**Clasificación:** defecto probable del test (espera la autenticación, pero comprueba la navegación sin esperar su finalización); causa definitiva pendiente de confirmación.

**Causa:** probable carrera de render/navegación bajo carga. No hay cambios de esta tanda en el flujo MFA.

**Resolución:** se ejecutó el test aislado y la suite secuencial; no se alteró el test ajeno al alcance para forzarlo a verde.

**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS aislado y secuencial; FAIL intermitente con paralelismo predeterminado.

**Riesgo o aprendizaje derivado:** el comando de suite predeterminado puede fallar por una expectativa de navegación sensible al tiempo; conviene investigarlo en una tarea propia.

## Tests faltantes y riesgos residuales

| Prueba faltante | Motivo | Riesgo / impacto | Recomendación |
| --- | --- | --- | --- |
| Recorrido manual en navegador con backend y MySQL reales, eliminando una característica asociada a productos | No se dispuso de ese entorno en esta tanda | Los mocks no demuestran el borrado real de asociaciones ni la permanencia de los productos; también falta validación visual en móvil/escritorio | Ejecutarlo antes de dar por validado el flujo en despliegue |
| Conteo exacto al instante del DELETE ante cambios concurrentes | El GET y DELETE son solicitudes separadas; el plan define el conteo como informativo | El número mostrado puede diferir de las asociaciones borradas | Mantener la advertencia visible; si se necesitara garantía estricta, requeriría una decisión de contrato backend posterior |

La lectura posterior a una respuesta incierta desbloquea acciones, pero no demuestra si el DELETE anterior se aplicó. El usuario debe revisar la lista y abrir una confirmación nueva.

## Criterio de cierre

- [x] Eliminación confirmada, recarga y advertencia de que los productos no se borran.
- [x] Permisos, errores, resultado incierto y regresión automatizada comprobados.
- [x] Incidentes, pruebas faltantes y riesgos residuales registrados.

**Revisión humana antes de aprobar trabajo posterior:** comprobar el flujo con un backend real, especialmente una característica usada por productos, y decidir si se abre una corrección independiente para la intermitencia del test MFA.
