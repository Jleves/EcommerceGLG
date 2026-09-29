# S2 BE — Crear categoría · Tanda 1: esquema y repositorio

**Fecha:** 29/09/2026

**Estado:** COMPLETA

## Alcance implementado

- Flyway V5 crea `categories` con columnas y límites del plan, auditoría, `activo=true` por defecto y `UNIQUE(nombre_normalizado)` para todas las filas.
- `Category` mapea la tabla y hereda `EntidadAuditable`; `CategoryRepository` expone `existsByNombreNormalizado(...)` y `findAllByOrderByNombreAscIdAsc()`.
- No se implementaron normalización automática, DTO, servicio, controller ni API. La clave normalizada se informará desde el servicio de la tanda 2.

**Diferencia respecto del plan:** la prueba MySQL se añadió a `MySqlSchemaIntegrationTests` existente para reutilizar su contenedor. No hubo cambio funcional de alcance.

## Pruebas ejecutadas

| Grupo | Ejecución | Qué demuestra | Resultado |
| --- | --- | --- | --- |
| Integración H2 | `CategoryPersistenceIntegrationTest` | V5 aplica, Hibernate valida el mapeo, persiste una categoría, consulta por clave normalizada y lista activas e inactivas en orden. | PASS: 1 test |
| Integración MySQL 8.4 | `MySqlSchemaIntegrationTests` | Flyway aplica V5; existe el índice único; la segunda inserción con la misma clave, aunque inactiva, falla y queda una sola fila. | PASS: 2 tests |
| Regresión | `SpringStarterApplicationTests`, `UserPersistenceIntegrationTests` | Arranca el contexto completo con V5 y sigue funcionando la persistencia de usuarios. | PASS: 2 tests |

**Comandos:** desde `Backend/spring-starter`, con `JAVA_HOME=C:\Users\jlinf\.jdks\ms-21.0.7`:

```powershell
.\mvnw.cmd '-Dtest=CategoryPersistenceIntegrationTest,MySqlSchemaIntegrationTests' test
.\mvnw.cmd '-Dtest=UserPersistenceIntegrationTests,SpringStarterApplicationTests' test
```

**Resultado total:** 5 tests ejecutados, 5 exitosos, 0 fallidos, 0 omitidos. Los dos builds terminaron en `BUILD SUCCESS`.

## INC-001 — Maven no pudo iniciar en el entorno inicial

**Contexto:** primera ejecución de tests de la tanda; después, primer intento de regresión dentro del sandbox.

**Test/comportamiento afectado:** ejecución de Maven, antes de las aserciones de los tests.

**Esperado:** resolver dependencias, compilar y ejecutar los tests seleccionados.

**Obtenido:** inicialmente `JAVA_HOME` no estaba definido. Tras apuntar al JDK 21 instalado, Maven no pudo obtener el parent POM por la restricción de red del sandbox (`Permission denied: getsockopt`).

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** confirmada: variable de entorno ausente y acceso de red restringido en la ejecución aislada.

**Resolución:** definir `JAVA_HOME` solo para los comandos de test y ejecutarlos con acceso a dependencias autorizado. No se cambiaron tests ni código productivo para resolverlo.

**Código productivo modificado por el incidente:** no.

**Tests modificados por el incidente:** no.

**Configuración/migraciones modificadas por el incidente:** no.

**Resultado posterior:** PASS, ambos comandos de test.

**Riesgo o aprendizaje derivado:** próximas tandas deben ejecutar Maven con JDK 21 y acceso efectivo a dependencias; un fallo de resolución anterior a la compilación no informa sobre la calidad del código.

## Tests faltantes y riesgos residuales

No faltan pruebas previstas para esta tanda. La concurrencia de dos altas desde servicio y su traducción a 409 todavía no están probadas porque servicio y API corresponden a tandas posteriores. La restricción única sí se comprobó en MySQL mediante dos inserciones secuenciales.

La colación real de MySQL puede considerar equivalentes variantes con acentos; el plan acepta que se rechacen como duplicadas. H2 cubre mapeo y consultas rápidas; la garantía del índice se verificó en MySQL.

## Cierre de tanda

- [x] Flyway V5 aplica en H2 y MySQL; Hibernate valida la entidad.
- [x] MySQL impide el duplicado normalizado; el repositorio lista en orden estable e incluye inactivas.
- [x] Se ejecutó regresión relacionada con arranque y persistencia previa.
- [x] Incidente, pruebas no aplicables todavía y riesgos residuales quedaron registrados.

**Revisión humana antes de la tanda 2:** confirmar el esquema V5 y que `nombre_normalizado` sea calculado únicamente por el futuro servicio. La siguiente tanda requiere aprobación explícita.
