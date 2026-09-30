# S2 BE Editar o desactivar categoría — resultado de la tanda 3

**Estado:** COMPLETA (30/09/2026).

## Revisión previa y alcance

Las tandas 1 y 2 estaban implementadas, sus resultados documentados y el árbol de trabajo estaba limpio. `CategoryService` ya exponía `update`, `deactivate` y `reactivate`; `UpdateCategoryRequest` y los handlers de 400/404/409 estaban disponibles. El controller carecía de las tres rutas y `SecurityConfig` no tenía un matcher explícito para categorías. No fue necesario ampliar el contexto más allá del plan, esos archivos, las pruebas relacionadas y la regresión de administración.

**Alcance implementado:** las tres rutas HTTP devuelven `200 CategoryResponse`, mantienen las validaciones y errores acordados, y exigen sesión, CSRF y uno de los roles `SUPER_ADMIN`, `ADMIN` o `USER`. La regla de URL se colocó antes de `anyRequest().authenticated()`; permanece la autorización de método del controller.

**Fuera de alcance:** lectura por ID (tarjeta de listados), elegibilidad de categorías al crear productos y conservación de productos relacionados (tarjeta **S2 BE Crear producto**). No se cambiaron servicio, repositorio, esquema ni migraciones.

**Archivos previstos y modificados:** `CategoryController.java` para las rutas, `SecurityConfig.java` para el matcher, `CategoryControllerTest.java` para contrato y seguridad, este resultado y el plan vivo. No hubo diferencias funcionales respecto del plan ni refactors adicionales.

## Pruebas ejecutadas

Desde `Backend/spring-starter`, con JDK 21:

```powershell
$env:JAVA_HOME='C:\Users\jlinf\.jdks\ms-21.0.7'
.\mvnw.cmd -q '-Dtest=CategoryControllerTest,CategoryServiceTest,CategoryPersistenceIntegrationTest,AdminUserProvisioningIntegrationTest' test
```

| Grupo | Resultado | Comportamiento demostrado |
| --- | --- | --- |
| HTTP e integración H2 — `CategoryControllerTest` | 7/7 | PUT y ambas transiciones para los tres roles; repetición de transiciones; datos persistidos y listado que conserva categorías; 400 de validación, 404 de ID inexistente, 409 por nombre ocupado con `ApiError`; regresión del alta y listado. |
| Seguridad — dentro de `CategoryControllerTest` | Incluida en 7/7 | Las tres rutas rechazan anónimo con 401, escritura sin CSRF con 403 e identidad sin rol permitido con 403. |
| Servicio e integración — `CategoryServiceTest`, `CategoryPersistenceIntegrationTest` | 11/11 | Regresión de edición, transiciones, unicidad, estado persistido y ausencia de borrado físico. |
| Regresión administrativa — `AdminUserProvisioningIntegrationTest` | 12/12 | La regla de categorías no amplía permisos de `/api/admin/users`; se mantienen sus flujos y controles de acceso. |

**Total:** 30 exitosas, 0 fallidas, 0 omitidas. `git diff --check` no detectó errores de whitespace.

## INC-003 — Maven no resuelve el parent POM en el sandbox

**Contexto:** primer intento de ejecutar las cuatro clases de prueba.

**Test/comportamiento afectado:** ninguna prueba llegó a iniciarse.

**Esperado:** resolver dependencias y ejecutar los tests seleccionados.

**Obtenido:** `Permission denied: getsockopt` al obtener `spring-boot-starter-parent:4.1.0` desde Maven Central.

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** red bloqueada en el sandbox, como en las tandas anteriores.

**Resolución:** repetir el mismo comando con acceso autorizado. No se alteraron código, tests ni configuración por este incidente.

**Código productivo modificado:** no por el incidente.  
**Tests modificados:** no por el incidente.  
**Configuración/migraciones modificadas:** no por el incidente.  

**Resultado posterior:** PASS, 30/30.

**Riesgo o aprendizaje derivado:** las verificaciones Maven de este entorno requieren acceso autorizado para resolver dependencias.

## Tests faltantes y riesgos residuales

De los tests planificados para la tanda 3 no quedó ninguno sin implementar o ejecutar. Las pruebas de producto previstas para la aceptación final siguen faltando porque `Product` todavía no existe: no se comprueba por HTTP el rechazo de una categoría inactiva al crear un producto ni que un producto existente conserve relación y estado al desactivar su categoría. El impacto potencial es que el criterio completo de la tarjeta aún no esté satisfecho; corresponde cubrirlo en **S2 BE Crear producto** y repetir la conservación cuando exista la relación.

La integración HTTP se ejecutó con H2. La protección de una carrera de nombres depende del índice único MySQL comprobado en la tanda 1; esta tanda no agregó una prueba concurrente HTTP. No surgieron decisiones pendientes.

## Criterio de cierre

- [x] Tres rutas responden con contrato y errores acordados.
- [x] Roles, anonimato y CSRF comprobados.
- [x] Regresión de categorías y administración ejecutada.
- [x] Dependencia de productos permanece visible para la aceptación final.

Antes de aprobar la siguiente tanda o la aceptación final, el responsable humano debería revisar el contrato de las tres rutas y mantener explícita la prueba pendiente de productos en su tarjeta. Esta tanda se detiene aquí.
