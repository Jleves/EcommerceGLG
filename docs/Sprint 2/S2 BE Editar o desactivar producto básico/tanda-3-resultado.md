# S2 BE — Editar o desactivar producto básico: resultado de la tanda 3

**Fecha:** 02/10/2026  
**Estado:** COMPLETA

## Alcance y verificación previa

Las tandas 1 y 2 dejaron `UpdateProductRequest`, `ProductService.update/deactivate/reactivate` y el bloqueo de fila en `ProductRepository`. Al iniciar esta tanda, `ProductController` solo exponía el alta. `SecurityConfig` ya cubría `/api/catalog/products/**` para `SUPER_ADMIN`, `ADMIN` y `USER`, con sesión y CSRF; `GlobalExceptionHandler` ya traducía validación, JSON o ID inválido y referencias inexistentes. El árbol de trabajo estaba limpio.

**Alcance implementado:** tres rutas HTTP privadas y pruebas de contrato, seguridad, errores y recorrido completo. **Fuera de alcance:** características, variantes, fotos, promociones, `destacado`, listados, consulta por ID, frontend y ficha pública. No fue necesario ampliar el análisis más allá del controller, DTO y servicios conectados, seguridad/errores existentes y pruebas relacionadas.

**Archivos previstos:** `ProductController.java`, `ProductControllerTest.java`, el plan vivo y este resultado. No fue necesario cambiar `SecurityConfig`, `GlobalExceptionHandler`, repositorios, migraciones ni configuración.

## Cambios realizados

- `PUT /api/catalog/products/{id}` recibe `@Valid UpdateProductRequest`, delega en `ProductService.update` y responde `200 ProductResponse`. Los cinco datos básicos se persisten; `activo` y `destacado` se conservan aunque el JSON envíe esas propiedades adicionales.
- `POST /api/catalog/products/{id}/deactivate` y `/reactivate` delegan en el servicio y responden `200 ProductResponse`. Las transiciones son idempotentes y conservan ID, categoría y datos básicos.
- El controller usa la anotación de rol existente. El filtro de sesión, CSRF y los manejadores de `ApiError` existentes se aplican a las tres rutas.
- Cinco pruebas HTTP nuevas verifican los tres roles admitidos, las respuestas y la persistencia; entradas inválidas y referencias inexistentes; rechazo de solicitudes sin sesión, sin CSRF o con rol no admitido; y el flujo crear → editar → desactivar → editar inactivo → reactivar con categoría inactiva → recargar. El alta sigue rechazando esa categoría.

**Diferencias respecto del plan:** ninguna funcional. Las clases de seguridad y errores no requirieron ajustes porque las pruebas confirmaron su comportamiento.

## Tests ejecutados

Comando dentro de `maven:3.9-eclipse-temurin-21`, con el repositorio y `/var/run/docker.sock` montados:

```text
TESTCONTAINERS_RYUK_DISABLED=true TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal
mvn -B -q '-Dtest=ProductControllerTest,CategoryControllerTest,ProductServiceTest,ProductServiceIntegrationTest,ProductStatusConcurrencyMySqlTest,ProductCategoryConcurrencyMySqlTest' test
```

Los reportes Surefire registran **34 pruebas, 0 fallos, 0 errores y 0 omisiones**. Las tres pruebas MySQL se ejecutaron. `docker ps` no mostró contenedores en ejecución al terminar.

| Grupo | Clases y cantidad | Qué demuestra | Resultado |
| --- | --- | --- | --- |
| HTTP/contrato, validación y seguridad | `ProductControllerTest` (10; 5 nuevas) | Edición y transiciones con los tres roles; `200 ProductResponse`, idempotencia, `activo`/`destacado` conservados, 400/404 `ApiError` con `requestId`, rechazo 401/403 sin escritura y flujo completo con recarga. Incluye 5 pruebas previas del alta. | PASS |
| Regresión HTTP de categoría | `CategoryControllerTest` (7) | Las rutas y reglas de categoría siguen funcionando, incluidas autorización y transiciones de estado. | PASS |
| Unitarios de servicio | `ProductServiceTest` (10) | Reglas de alta, edición y estado, incluidas categoría inactiva e inexistencias. | PASS |
| Integración H2/Flyway | `ProductServiceIntegrationTest` (4) | Persistencia de edición y estados, conservación de referencias y regla de alta. | PASS |
| Concurrencia MySQL | `ProductStatusConcurrencyMySqlTest` (1), `ProductCategoryConcurrencyMySqlTest` (2) | Bloqueo de producto y regresión del bloqueo de categoría con MySQL 8.4. | PASS |

**Tests planificados frente a ejecutados:** se cubrieron contrato de las tres rutas, campos/estados, idempotencia, todos los casos de validación listados, JSON inválido, ID no numérico, referencias 404, categoría inactiva, los tres roles, sesión, CSRF, recorrido integral y regresión de alta/categoría. **Tests fallidos:** ninguno. **Tests faltantes de esta tanda:** ninguno.

## Incidentes

No hubo incidentes de implementación, tests, datos ni contrato en esta tanda. La configuración de Testcontainers aplicada al comando proviene del incidente de entorno `INC-T2-001` ya registrado en [tanda-2-resultado.md](tanda-2-resultado.md); no se cambió ningún archivo por ello.

## Tests faltantes y riesgos residuales

**Tests faltantes de la tanda 3:** ninguno. No se ejecutó la suite global del backend: el riesgo residual es que una regresión en un módulo no incluido en la selección pase inadvertida. Tampoco se probaron UI React, lectura administrativa, ficha pública ni características/variantes futuras, fuera del alcance de esta tarjeta; la UI aún no puede mostrar por sí sola los cambios y Sprint 3 deberá preservar ID y referencias al ampliar el modelo.

Las mutaciones concurrentes sobre el mismo producto están serializadas por bloqueo, pero un `PUT` basado en un formulario viejo no se detecta: prevalece la última edición válida. Es una decisión de alcance documentada en el plan, sin control de versiones. Las pruebas MySQL cubren los órdenes de concurrencia previstos en tandas anteriores, no todas las intercalaciones posibles.

**Decisiones pendientes descubiertas:** ninguna.

## Cierre de la tanda

- [x] `PUT` y ambas rutas `POST` responden según contrato y persisten cambios.
- [x] Roles, sesión, CSRF, validación y errores comprobados por HTTP sin escritura en los rechazos.
- [x] Baja lógica y reactivación conservan ID, datos y referencias, incluso con categoría inactiva.
- [x] Flujo completo con recarga y regresión de alta/categoría ejecutados.
- [x] Incidentes, faltantes, riesgos y resultado registrados en el plan vivo.

**Archivos de la tanda:** `ProductController.java`, `ProductControllerTest.java`, `s2-be-editar-producto-basico-plan-implementacion.md` y este resultado. No se creó commit.

**Revisión humana:** confirmar el contrato HTTP y la decisión de aceptar categorías inactivas en edición/reactivación; considerar el riesgo de última escritura válida y el alcance de la regresión seleccionada. La implementación se detiene en la tanda 3.
