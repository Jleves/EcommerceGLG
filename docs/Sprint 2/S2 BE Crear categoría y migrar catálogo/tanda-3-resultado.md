# Tanda 3 — Resultado de ejecución

**Estado:** COMPLETA (29/09/2026).

## Alcance y cambios

- `POST /api/catalog/categories` crea una categoría y devuelve 201; `GET` devuelve 200 y todas las categorías, incluso inactivas. Ambos requieren sesión y aceptan `SUPER_ADMIN`, `ADMIN` y `USER`.
- El request valida nombre e ícono obligatorios (límites 160 y 100) y descripción opcional (límite 1000). El nombre ocupado produce 409 con `RESOURCE_CONFLICT`; los choques de índice conservan el 409 genérico existente.
- Se mantuvo `SecurityConfig` y su protección CSRF. No se adelantaron edición, baja, lectura pública ni productos.

**Diferencia respecto del plan:** ninguna funcional. Se agregó aislamiento del contexto al test HTTP nuevo por `INC-002`.

## Tests ejecutados

Comando final desde `Backend/spring-starter`, con `JAVA_HOME=C:\Users\jlinf\.jdks\ms-21.0.7`:

```powershell
.\mvnw.cmd -q '-Dtest=CategoryControllerTest,CategoryServiceTest,CategoryServiceMySqlTest,CategoryPersistenceIntegrationTest,MySqlSchemaIntegrationTests,AuthControllerTest,AdminUserProvisioningIntegrationTest,GlobalExceptionHandlerTest,SecurityErrorHandlersTest' test
```

| Grupo | Clases | Pruebas | Resultado demostrado |
| --- | --- | ---: | --- |
| HTTP, validación y seguridad | `CategoryControllerTest` | 4 | Alta y listado H2 para tres roles; lista vacía; datos inválidos y JSON malformado 400; nombre activo/inactivo ocupado 409 con `ApiError`; 401 anónimo, 403 sin CSRF o sin rol autorizado; `/api/admin/users` sigue protegido. |
| Concurrencia MySQL | `CategoryServiceMySqlTest` | 2 | Dos altas simultáneas pasan la comprobación previa; el índice deja una fila. Por HTTP se reciben 201 y 409 con código y requestId. |
| Servicio y persistencia | `CategoryServiceTest`, `CategoryPersistenceIntegrationTest`, `MySqlSchemaIntegrationTests` | 6 | Normalización, listado, mapeo H2, migración V5 y unicidad MySQL. |
| Regresión autenticación, administración y errores | `AuthControllerTest`, `AdminUserProvisioningIntegrationTest`, `GlobalExceptionHandlerTest`, `SecurityErrorHandlersTest` | 30 | Login, CSRF, administración, formato global de errores y handlers de seguridad. |

**Resultado final:** 42/42 PASS; 0 fallos, 0 errores, 0 omitidos. La ejecución incluyó MySQL 8.4 con Testcontainers.

## Incidentes

### INC-001

**Contexto:** primera ejecución Maven de la tanda.

**Test/comportamiento afectado:** ejecución de la suite.

**Esperado:** compilar y correr tests.

**Obtenido:** `JAVA_HOME` no estaba definido; al configurarlo, el sandbox denegó la descarga del parent POM (`Permission denied: getsockopt`).

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** JDK 21 no seleccionado para el proceso y red restringida en el sandbox.

**Resolución:** definir `JAVA_HOME` para Maven y ejecutar con acceso autorizado.

**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS.

**Riesgo o aprendizaje derivado:** los tests MySQL requieren JDK 21, dependencias Maven y Docker disponibles.

### INC-002

**Contexto:** primera regresión combinada, después de que la nueva prueba HTTP y la prueba MySQL pasaran.

**Test/comportamiento afectado:** diez casos de `AdminUserProvisioningIntegrationTest` que preparan una cookie CSRF con `GET /api/auth/csrf`.

**Esperado:** 204 con cookie `XSRF-TOKEN`, como sucede al correr esa clase sola.

**Obtenido:** 204 sin cookie tras ejecutar `CategoryControllerTest` en el mismo proceso; los casos fallaron antes de probar sus endpoints.

**Clasificación:** defecto de aislamiento del test; causa interna exacta todavía no determinada.

**Causa:** interferencia reproducible entre ambas clases al compartir el contexto Spring en caché. La clase de administración sola pasó; ambas juntas fallaron antes del aislamiento y pasaron después.

**Resolución:** marcar `CategoryControllerTest` con `@DirtiesContext(AFTER_CLASS)` para descartar su contexto después de ejecutarla. Se mantuvieron intactos los tests y contratos de administración.

**Código productivo modificado:** no. **Tests modificados:** sí, solo el nuevo test HTTP. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS; regresión combinada 42/42.

**Riesgo o aprendizaje derivado:** conviene investigar el estado de CSRF entre clases si se vuelve a compartir este contexto; el aislamiento aumenta el tiempo de ejecución.

## Tests faltantes y riesgos residuales

**Planificados vs. implementados/ejecutados:** no falta ninguna prueba prevista para esta tanda. La carrera 201/409 se comprobó en MySQL; el 403 de permiso se comprobó con una identidad de test sin rol autorizado, dado que los tres roles de negocio existentes pueden usar la API.

Edición/desactivación, consulta pública y asociación de productos no existen aún y se comprobarán en sus tarjetas. La causa interna exacta de `INC-002` queda como riesgo de mantenimiento de tests, sin fallo observado en la ejecución final.

## Criterio de cierre

- [x] Camino POST → MySQL/H2 → GET demostrado con 201/200.
- [x] Validación, conflicto, autenticación y CSRF devuelven los códigos acordados.
- [x] Carrera MySQL deja una fila y respuestas HTTP 201/409.
- [x] Regresiones relacionadas ejecutadas e incidentes, faltantes y riesgos registrados.

Antes de aprobar otra tanda, revisar el contrato HTTP y el aislamiento de contexto del test CSRF. Esta ejecución se detiene en la tanda 3.
