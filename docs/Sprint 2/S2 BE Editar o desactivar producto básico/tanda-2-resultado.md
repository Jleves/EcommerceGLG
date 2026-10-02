# S2 BE — Editar o desactivar producto básico: resultado de la tanda 2

**Fecha:** 02/10/2026  
**Estado:** COMPLETA

## Alcance y verificación previa

La tanda 1 dejó `ProductService.update(...)` y `ProductRepository.lockById(...)` transaccionales, con bloqueo pesimista de la fila de producto. El árbol de trabajo estaba limpio al iniciar esta tanda. `ProductController` aún solo expone el alta, y `ProductResponse` ya incluye `activo` y los datos necesarios para devolver cada transición.

**Alcance implementado:** métodos de servicio para desactivar y reactivar el producto, idempotentes y sin borrado físico; pruebas unitarias, integración H2 y concurrencia MySQL. **Fuera de alcance:** rutas HTTP, autorización y CSRF de las transiciones (tanda 3), edición de características, variantes y frontend. No fue necesario ampliar el análisis fuera del servicio, sus tests, el patrón MySQL de concurrencia y el entorno Docker usado para probar.

**Archivos previstos:** `ProductService`, `ProductServiceImpl`, `ProductServiceTest`, `ProductServiceIntegrationTest` y una prueba MySQL nueva. `ProductRepository` ya tenía el bloqueo necesario y no se modificó. Tampoco se necesitaron DTO, migración ni configuración nueva.

## Cambios realizados

- `ProductService` expone `deactivate(id)` y `reactivate(id)`.
- `ProductServiceImpl` ejecuta ambos métodos en transacciones, obtiene el producto con `lockById(id)`, fija únicamente `activo` y devuelve `ProductResponse` tras `saveAndFlush`. Un producto inexistente produce `ResourceNotFoundException` antes de cualquier guardado.
- Repetir la transición solicitada devuelve el estado actual sin error. No se consulta ni modifica la categoría, de modo que la reactivación funciona aunque la categoría esté inactiva. Nombre, descripción, precio, disponibilidad, `destacado`, relación e ID permanecen intactos.
- La nueva prueba MySQL verifica una espera real sobre `products` mientras una transacción desactiva el producto y otra intenta reactivarlo.

**Diferencias respecto del plan:** ninguna funcional. La ejecución de MySQL necesitó variables de entorno solo para el contenedor de Maven por la conectividad de Testcontainers en Docker Desktop; el repositorio no cambió por ese incidente.

## Tests ejecutados

Comando final dentro de `maven:3.9-eclipse-temurin-21`, con el repositorio y `/var/run/docker.sock` montados:

```text
TESTCONTAINERS_RYUK_DISABLED=true TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal
mvn -B -q '-Dtest=ProductServiceTest,ProductServiceIntegrationTest,ProductStatusConcurrencyMySqlTest,ProductCategoryConcurrencyMySqlTest,ProductPersistenceIntegrationTest,ProductControllerTest' test
```

Los reportes Surefire finales registran **23 pruebas, 0 fallos, 0 errores y 0 omisiones**. Las tres pruebas MySQL de concurrencia se ejecutaron, sin quedar omitidas.

| Grupo | Clases y cantidad | Qué demuestra | Resultado |
| --- | --- | --- | --- |
| Unitarios de servicio | `ProductServiceTest` (10) | Desactivar/reactivar dos veces, respuesta correcta, conservación de campos, categoría inactiva y ausencia de guardado si falta producto; también regresión de alta/edición. | PASS |
| Integración H2/Flyway | `ProductServiceIntegrationTest` (4) | Los estados se recargan tras cada transición; no cambia la fila, categoría ni datos; reactivación con categoría inactiva; alta/edición anteriores conservan sus reglas. | PASS |
| Concurrencia MySQL nueva | `ProductStatusConcurrencyMySqlTest` (1) | La reactivación espera el bloqueo de la desactivación sobre `products`; tras ambos commits queda el estado final esperado y los datos conservados. | PASS |
| Regresión concurrencia MySQL | `ProductCategoryConcurrencyMySqlTest` (2) | El bloqueo de categoría del alta sigue ordenando alta y desactivación de categoría. | PASS |
| Regresión persistencia y HTTP | `ProductPersistenceIntegrationTest` (1), `ProductControllerTest` (5) | V6, mapeo y alta HTTP conservan persistencia, validación y seguridad existentes. | PASS |

**Tests planificados frente a ejecutados:** se cubrieron unitarios, integración H2, referencia inexistente, categoría inactiva, concurrencia MySQL y regresión relacionada. **Tests faltantes de esta tanda:** ninguno.

## Incidentes

### INC-T2-001 — Ryuk inaccesible desde Maven en Docker Desktop

**Contexto:** primer intento de ejecutar la selección con Docker socket montado para Testcontainers.  
**Test/comportamiento afectado:** arranque de las pruebas MySQL, antes de que se ejecutaran los métodos de prueba.  
**Esperado:** Testcontainers inicia Ryuk y luego MySQL 8.4, aplicando Flyway V1–V6.  
**Obtenido:** Ryuk inició, pero el proceso Maven no pudo conectarse a `172.17.0.1` y repitió `Connection refused`; se interrumpió esa ejecución.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** confirmada la falta de conectividad con la IP anunciada para Ryuk desde el contenedor Maven; la causa de red específica de Docker Desktop queda como hipótesis.  
**Resolución:** repetir el comando de prueba con `TESTCONTAINERS_RYUK_DISABLED=true` y `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. MySQL 8.4 inició, migró y ejecutó las pruebas. Las variables solo afectaron ese proceso; no se cambiaron archivos de configuración. Al finalizar, `docker ps` no mostró contenedores en ejecución.  
**Código productivo modificado:** no por el incidente.  
**Tests modificados:** no por el incidente.  
**Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS; 23/23 pruebas, incluidas tres MySQL de concurrencia.  
**Riesgo o aprendizaje derivado:** en este host, las pruebas MySQL iniciadas desde un contenedor Maven necesitan la configuración anterior o un entorno Docker con conectividad equivalente. Con Ryuk desactivado debe verificarse la limpieza al terminar.

## Tests faltantes y riesgos residuales

**Tests faltantes de la tanda 2:** ninguno. **No probado todavía:** rutas HTTP de estado, autenticación, autorización, CSRF y `ApiError` de estas operaciones; corresponde a la tanda 3. Tampoco se prueba un flujo de frontend, fuera de esta tarjeta BE.

**Riesgos residuales:** los métodos solo son accesibles internamente hasta que se publique el controller. El bloqueo serializa mutaciones de una fila, pero no detecta formularios desactualizados; ese riesgo ya consta en el plan. La prueba MySQL verificó el orden desactivar → reactivar con espera real; no modela toda posible secuencia de ediciones concurrentes.

**Decisiones pendientes descubiertas:** ninguna.

## Cierre de la tanda

- [x] Desactivación y reactivación idempotentes y persistidas.
- [x] Fila, categoría y demás datos conservados, incluso con categoría inactiva.
- [x] Producto inexistente rechazado sin guardado.
- [x] Bloqueo de producto observado en MySQL; regresión de categoría y alta verificada.
- [x] Incidente de entorno, pruebas faltantes y riesgos residuales registrados; plan vivo actualizado.

**Archivos de la tanda:** `ProductService.java`, `ProductServiceImpl.java`, `ProductServiceTest.java`, `ProductServiceIntegrationTest.java`, `ProductStatusConcurrencyMySqlTest.java`, el plan vivo y este resultado. No se creó commit.

**Revisión humana antes de la tanda 3:** comprobar la semántica idempotente, la independencia respecto del estado de categoría y el reporte de concurrencia MySQL. La implementación se detiene aquí.
