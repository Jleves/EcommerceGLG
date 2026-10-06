# Tanda 2 — resultado de ejecución

**Estado:** COMPLETA.

## Alcance ejecutado

Se agregó **Productos** a la navegación privada y `/app/catalog/products` a `AppRouter` bajo `ProtectedRoute`. La pantalla consulta `GET /api/catalog/products` mediante `apiRequest` con cookies, `size=20` y `AbortSignal`; muestra activos e inactivos, metadatos, carga, vacío, error y reintento. Buscar aplica el nombre al enviar, recorta espacios, vuelve a la página 0 y conserva filtro y página en la URL. **Limpiar búsqueda** quita el filtro. Un envío vacío conserva la URL y el listado; un enlace directo con `nombre=` muestra error y no consulta la API.

El acceso directo sin sesión redirige al login antes de montar la página o llamar al servicio. `SUPER_ADMIN`, `ADMIN` y `USER` mantienen acceso al listado; las rutas de usuarios siguen reservadas a `SUPER_ADMIN`. No se agregaron acciones de alta, edición, cambio de estado ni búsqueda pública. No fue necesario ampliar el contexto más allá del plan de la tanda, su dependencia, el enrutado, los servicios y tests relacionados.

## Diferencias respecto del plan

Ninguna funcional. Se usó el guard de sesión existente para la nueva ruta y se agregaron pruebas específicas de navegación directa.

## Tests ejecutados

| Grupo | Ejecución | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| Contrato HTTP | `productService.test.js` (2) | `page`, `size=20`, `nombre` opcional y codificado, cookies y `AbortSignal` | PASS |
| Interfaz | `ProductManagementPage.test.jsx` (5) | Resúmenes, carga, vacío, error/reintento, búsqueda al enviar, URL, paginación, validación y descarte de respuesta tardía | PASS |
| Seguridad de rutas | `AppRouter.products.test.jsx` (5) | Acceso de los tres roles, redirección anónima sin consulta y protección de categorías/usuarios | PASS |
| Regresión | Suite completa del frontend | Categorías, usuarios, autenticación y servicios existentes | PASS |
| Estática y compilación | `npm run lint`; `npm run build` | Reglas ESLint y build de producción | PASS |

Corrida final: `npm test -- --maxWorkers=2` — **20 archivos, 179 pruebas exitosas, 0 fallidas**. Lint y build terminaron con código 0.

## INC-001 — consulta ambigua en un test nuevo

**Contexto:** primera corrida de las pruebas de la tanda.  
**Test/comportamiento afectado:** mensaje de carga de `ProductManagementPage`.  
**Esperado:** encontrar el mensaje «Cargando productos…».  
**Obtenido:** `getByRole('status')` encontró tanto el mensaje como el `<output>` auxiliar que muestra la URL.  
**Clasificación:** defecto del test.  
**Causa:** `<output>` posee implícitamente el rol `status`.  
**Resolución:** consultar el texto exacto del mensaje de carga.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** los elementos auxiliares del test pueden compartir roles accesibles con la interfaz.

## INC-002 — mock incompleto al volver de página

**Contexto:** segunda corrida de las pruebas de la tanda.  
**Test/comportamiento afectado:** paginación con **Siguiente** y **Anterior**.  
**Esperado:** tres consultas con una respuesta para cada navegación.  
**Obtenido:** la tercera llamada devolvió `undefined` porque solo se configuraron dos respuestas del mock.  
**Clasificación:** defecto del test.  
**Causa:** preparación incompleta del recorrido de ida y vuelta.  
**Resolución:** dar una respuesta predeterminada además de las dos respuestas específicas.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** los mocks de navegación deben cubrir cada solicitud esperada.

## INC-003 — comprobación intermitente en test MFA existente

**Contexto:** primera corrida de la suite completa con la concurrencia predeterminada.  
**Test/comportamiento afectado:** `MfaLoginFlow.test.jsx`, autenticación tras confirmar el código.  
**Esperado:** URL `/app/profile?tab=security` tras el estado `authenticated`.  
**Obtenido:** la comprobación inmediata todavía vio `/login/mfa`; las otras 178 pruebas pasaron.  
**Clasificación:** causa todavía no determinada; probable sincronización del test bajo concurrencia.  
**Causa:** el test espera el estado autenticado, pero comprueba la navegación sin esperar su finalización. La prueba pasó aislada y en la suite posterior con dos workers.  
**Resolución:** se verificó aislada y se repitió la suite completa con menor concurrencia; no se modificaron código ni test MFA porque no se confirmó una regresión.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** esa prueba puede seguir siendo intermitente con alta concurrencia; conviene investigarla en una tarea específica si reaparece.

La primera ejecución de Vitest dentro del sandbox no pudo leer `vite.config.js` por restricciones del entorno. Se repitió fuera del sandbox sin cambiar archivos del proyecto.

## Tests faltantes y riesgos residuales

No faltan grupos de prueba planificados. No se verificó manualmente el recorrido contra un backend en ejecución ni se midió rendimiento con datos reales. Los tests de componente usan respuestas simuladas y el de contrato HTTP comprueba la URL y las opciones enviadas a `fetch`; el riesgo residual es una diferencia de integración o rendimiento en un entorno desplegado.

## Decisiones pendientes

Ninguna.

## Criterio de cierre

- [x] Listado y búsqueda navegables para `SUPER_ADMIN`, `ADMIN` y `USER`, con acceso anónimo bloqueado.
- [x] Contrato HTTP, interfaz, protección de rutas y regresión verificados; lint y build aprobados.
- [x] Incidentes, pruebas faltantes y riesgos residuales registrados.

La siguiente tanda requiere aprobación explícita según el plan.
