# S2 BE — Crear categoría · Tanda 2: lógica de negocio

**Fecha:** 29/09/2026  
**Estado:** COMPLETA

## Alcance implementado

`CategoryService.create` recorta el nombre, calcula `nombre_normalizado` con `Locale.ROOT`, consulta duplicados sin filtrar por estado y guarda una categoría activa mediante `saveAndFlush`. Devuelve `CategoryResponse` sin la clave interna. `list` mapea el orden del repositorio e incluye filas inactivas. La creación es transaccional y el listado usa transacción de solo lectura.

Se agregaron los records `CreateCategoryRequest` y `CategoryResponse`, el contrato y la implementación del servicio y `CategoryConflictException`. La validación Bean Validation, la API y la traducción de errores a HTTP corresponden a la tanda 3.

**Diferencias respecto del plan:** ninguna funcional. La prueba MySQL fuerza el punto de carrera mediante un espía del repositorio y una consulta JDBC real, para que ambas altas hayan visto el nombre libre antes de guardar.

**Contexto adicional requerido:** se revisaron las convenciones existentes de servicios, excepciones y Testcontainers para ajustar los tests. No se necesitó ampliar la arquitectura ni modificar la migración V5.

## Pruebas ejecutadas

| Grupo | Ejecución | Comportamiento demostrado | Resultado final |
| --- | --- | --- | --- |
| Unitarios | `CategoryServiceTest` | Nombre recortado y clave en minúsculas, categoría activa, descripción opcional, conflicto antes de guardar para clave ocupada, lista vacía y mapeo de orden con inactivas. | PASS: 3 tests |
| Concurrencia / integración MySQL 8.4 | `CategoryServiceMySqlTest` | Dos altas pasan la consulta de existencia; una inserta, la otra recibe `DataIntegrityViolationException` por el índice único y queda una fila. | PASS: 1 test |
| Regresión H2 | `CategoryPersistenceIntegrationTest` | Flyway V5, mapeo JPA, consulta y orden del repositorio siguen válidos. | PASS: 1 test |
| Regresión MySQL | `MySqlSchemaIntegrationTests` | V5, índice único y esquema anterior siguen válidos. | PASS: 2 tests |
| Regresión de arranque y usuarios | `SpringStarterApplicationTests`, `UserPersistenceIntegrationTests` | El contexto inicia y la persistencia de usuarios sigue funcionando. | PASS: 2 tests |

**Comando final:** desde `Backend/spring-starter`, con `JAVA_HOME=C:\Users\jlinf\.jdks\ms-21.0.7`:

```powershell
.\mvnw.cmd '-Dtest=CategoryServiceTest,CategoryServiceMySqlTest,CategoryPersistenceIntegrationTest,MySqlSchemaIntegrationTests,SpringStarterApplicationTests,UserPersistenceIntegrationTests' test
```

**Resultado final:** 9 ejecutados, 9 exitosos, 0 fallidos, 0 omitidos; `BUILD SUCCESS`.

## INC-001 — Maven sin acceso a dependencias en el sandbox

**Contexto:** primera ejecución de pruebas de esta tanda.  
**Test/comportamiento afectado:** compilación y ejecución de todos los tests.  
**Esperado:** Maven resuelve el parent POM y ejecuta la suite.  
**Obtenido:** `Permission denied: getsockopt` al obtener `spring-boot-starter-parent:4.1.0`.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** restricción de red del sandbox, igual que en la tanda 1.  
**Resolución:** reejecutar Maven con acceso autorizado; JDK 21 configurado en `JAVA_HOME`.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** un error de resolución antes de compilar no informa sobre el comportamiento del servicio.

## INC-002 — Preparación incorrecta de la carrera MySQL

**Contexto:** primera ejecución de `CategoryServiceMySqlTest` y repetición diagnóstica.  
**Test/comportamiento afectado:** dos altas simultáneas del mismo nombre.  
**Esperado:** ambas consultan que el nombre está libre; una inserta y la otra falla por el índice único.  
**Obtenido:** el contador de la barrera quedó en 2; ambas tareas fallaron antes de ella con `MockitoException: Cannot call abstract real method on java object!`.  
**Clasificación:** defecto del test.  
**Causa:** `invocation.callRealMethod()` no puede ejecutar el método abstracto `existsByNombreNormalizado` de la interfaz Spring Data espiada.  
**Resolución:** el espía consulta la misma tabla MySQL mediante `JdbcTemplate`, conserva la barrera y devuelve el resultado real de existencia. No se alteró la expectativa ni el código productivo.  
**Código productivo modificado:** no. **Tests modificados:** sí, `CategoryServiceMySqlTest`. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS; ambas consultas terminan antes de las inserciones, una solicitud falla con `DataIntegrityViolationException` y queda una fila.  
**Riesgo o aprendizaje derivado:** un espía de interfaz Spring Data no permite delegar al método abstracto con `callRealMethod()`.

## Tests faltantes y riesgos residuales

No faltan pruebas planificadas para la tanda 2. La validación de campos, autorización, códigos HTTP y formato `ApiError` no están probados ni implementados porque pertenecen a la tanda 3. El riesgo residual es que el servicio aún no está disponible por API y las entradas inválidas no reciben validación HTTP; la colisión de índice ya tiene excepción de persistencia, pero aún no respuesta 409. Deben cubrirse al ejecutar la tanda 3.

No se hallaron decisiones pendientes.

## Cierre de tanda

- [x] Alta y listado funcionan desde el servicio; los nombres existentes activos e inactivos cuentan como ocupados.
- [x] Dos altas concurrentes en MySQL dejan una sola fila y la perdedora falla por la restricción única.
- [x] Se ejecutaron las regresiones relacionadas de esquema, arranque y persistencia de usuarios.
- [x] Se registraron incidentes, pruebas ejecutadas, límites de cobertura y riesgos residuales.

**Revisión humana antes de la tanda 3:** revisar el contrato del DTO y la excepción de conflicto; confirmar que la futura API traduzca tanto el conflicto de negocio como la colisión del índice a 409. La tanda 3 requiere aprobación explícita.
