# S2 BE — Crear producto básico: resultado de la tanda 1

**Fecha:** 2026-09-30  
**Estado:** COMPLETA

## Alcance y verificación previa

Se implementó únicamente la persistencia del producto básico: migración V6, entidad JPA, repositorio y pruebas directas de persistencia. V5 ya contenía `categories(id)`, y el perfil de pruebas aplicaba Flyway y validaba el mapeo Hibernate. No fue necesario ampliar el análisis más allá del esquema V5, la entidad y las pruebas de categoría, la auditoría compartida y la configuración de tests. Los cambios preexistentes del árbol de trabajo en diagramas y documentación ajena a esta tanda se conservaron.

Quedan fuera de alcance DTO, servicio de alta, reglas de categoría activa, coordinación concurrente, autorización, CSRF y endpoint HTTP (tandas 2 y 3), así como variantes e imágenes de tarjetas posteriores.

## Cambios realizados

- `V6__create_products.sql`: tabla `products`, FK e índice sobre `category_id`, `DECIMAL(15,2)` con restricción de precio positivo, estados predeterminados y columnas de auditoría. El nombre no tiene índice único.
- `Product`: campos básicos, asociación `@ManyToOne(fetch = LAZY)` con `Category`, auditoría heredada y estados iniciales `activo=true`, `destacado=false`.
- `ProductRepository`: operaciones JPA estándar, incluidas `saveAndFlush` y `findById`.
- Pruebas de integración en H2 y MySQL 8.4.

**Diferencias respecto del plan:** ninguna funcional. Maven se ejecutó en un contenedor temporal con Java 21 por falta de JDK local.

## Tests ejecutados

Comando final: `mvn -B -q '-Dtest=ProductPersistenceIntegrationTest,ProductPersistenceMySqlTest,CategoryPersistenceIntegrationTest' test`, dentro de `maven:3.9-eclipse-temurin-21` con acceso a Docker para Testcontainers. Los reportes Surefire registran 7 pruebas, 0 fallos, 0 errores y 0 omisiones.

| Categoría | Prueba | Comportamiento demostrado | Resultado |
| --- | --- | --- | --- |
| Integración H2/Flyway | `ProductPersistenceIntegrationTest` (1) | V6 y mapeo JPA válidos; guarda y recarga categoría, precio exacto de 13 enteros y 2 decimales, disponibilidad, estados y auditoría. | PASS |
| Integración MySQL/Testcontainers | `ProductPersistenceMySqlTest` (3) | V6 y mapeo válidos en MySQL 8.4; precio exacto y auditoría; FK impide categorías inexistentes o borrar una referenciada; `CHECK` impide precio cero o negativo. | PASS |
| Regresión H2 | `CategoryPersistenceIntegrationTest` (3) | Alta, consultas, actualización y transiciones de estado de categoría siguen funcionando con V6 aplicada. | PASS |

**Tests planificados vs. ejecutados:** las dos pruebas de integración previstas para la tanda se implementaron y ejecutaron. No faltan pruebas planificadas. No se ejecutaron tests de servicio, concurrencia o HTTP porque corresponden a las tandas 2 y 3.

## Incidentes

### INC-T1-001

**Contexto:** primera ejecución de `ProductPersistenceMySqlTest.mysqlRejectsMissingCategoryAndNonPositivePrice` en MySQL 8.4.  
**Test/comportamiento afectado:** rechazo de `precio_referencia = 0` por la restricción `CHECK`.  
**Esperado:** el motor rechaza la inserción; el test esperaba `DataIntegrityViolationException`.  
**Obtenido:** el motor rechazó la inserción con código MySQL 3819, pero `JdbcTemplate` la tradujo a `UncategorizedSQLException`.  
**Clasificación:** defecto del test.  
**Causa:** la expectativa fijaba una subclase Spring que no corresponde a la traducción de esta violación `CHECK` con SQL state `HY000`.  
**Resolución:** el test comprueba ahora la causa `SQLException` y el código MySQL 3819. Se mantuvo la restricción productiva.  
**Código productivo modificado:** no.  
**Tests modificados:** sí.  
**Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** las excepciones traducidas por Spring pueden variar según el código SQL; la prueba de esta restricción verifica la respuesta específica de MySQL.

### INC-T1-002

**Contexto:** intento inicial de ejecutar `mvnw.cmd` en Windows.  
**Test/comportamiento afectado:** toda la suite seleccionada.  
**Esperado:** iniciar Maven con Java 21.  
**Obtenido:** el wrapper informó que `JAVA_HOME` no estaba definido correctamente; no se detectó JDK local.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** JDK local ausente de las rutas disponibles.  
**Resolución:** ejecución en la imagen temporal `maven:3.9-eclipse-temurin-21`, con el código montado y Testcontainers conectado al daemon Docker.  
**Código productivo modificado:** no.  
**Tests modificados:** no.  
**Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** para repetir la suite en Windows se necesita configurar Java 21 o usar el mismo entorno de contenedor.

## Riesgos residuales y decisiones

La tabla y el repositorio todavía pueden usarse sin comprobar si una categoría está activa; el servicio y sus pruebas se incorporarán en la tanda 2. No se validan requests ni se expone HTTP hasta la tanda 3. **Decisiones pendientes descubiertas:** ninguna.

## Criterio de cierre

- [x] Flyway V6 y mapeo JPA validan en H2 y MySQL.
- [x] FK, precio exacto y positivo, y auditoría comprobados; incidentes y faltantes registrados.
- [x] Plan vivo y este resultado documentan la ejecución real.

Antes de aprobar la tanda 2, revisar el DDL de V6, el mapeo `Product` y la cobertura de las restricciones MySQL. No se inició la tanda 2.
