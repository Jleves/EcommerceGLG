# Tanda 1 — Consultas para la administración: resultado

**Estado:** COMPLETA. **Fecha:** 2026-10-02.  
**Plan:** [s2-be-editar-o-eliminar-caracteristica-plan-implementacion.md](s2-be-editar-o-eliminar-caracteristica-plan-implementacion.md).

## Alcance ejecutado

- `CharacteristicResponse` suma `cantidadProductos`. El alta devuelve cero; `GET /api/catalog/characteristics` usa un conteo agrupado y `GET /api/catalog/characteristics/{id}` cuenta la definición solicitada. Se incluyen productos activos e inactivos.
- `GET /api/catalog/products/{id}` usa `ProductService.getById` y el mapeo existente de `ProductResponse` para entregar características con nombre actual, valor y unidad; admite productos inactivos y devuelve 404 si falta.
- Se reutilizó la seguridad existente de catálogo para `SUPER_ADMIN`, `ADMIN` y `USER`. No se alteraron esquemas, mutaciones ni configuración.

## Diferencias respecto del plan

Se agregó una prueba específica de las consultas nuevas en MySQL 8.4 además de las pruebas H2 previstas. No hubo diferencias funcionales.

## Tests ejecutados

Comandos desde `Backend/spring-starter`, con `JAVA_HOME=C:\Users\jlinf\.jdks\ms-21.0.7` y Maven con acceso a dependencias:

```powershell
.\mvnw.cmd '-Dtest=CharacteristicUsageQueryTest,CharacteristicControllerTest,ProductCharacteristicControllerTest,ProductCharacteristicServiceIntegrationTest,ProductServiceTest' test
.\mvnw.cmd '-Dtest=ProductControllerTest,ProductCharacteristicFlowMySqlTest' test
.\mvnw.cmd '-Dtest=CharacteristicUsageMySqlTest' test
```

| Grupo | Qué demuestra | Resultado |
| --- | --- | --- |
| Integración H2 — `CharacteristicUsageQueryTest` | Conteos 0, 1 y 2; inclusión de producto inactivo; actualización del conteo tras quitar una asociación; GET de producto con valor/unidad; alta de definición con conteo cero. | 3 PASS |
| HTTP y seguridad — `CharacteristicUsageQueryTest` | Tres roles acceden al GET de producto; anónimo y otro rol no; producto inexistente devuelve 404. Las respuestas HTTP de características incluyen el conteo. | Incluido en los 3 PASS |
| Regresión H2 — `CharacteristicControllerTest`, `ProductCharacteristicControllerTest`, `ProductCharacteristicServiceIntegrationTest`, `ProductServiceTest` | Alta/lectura previas de características y producto, validaciones, persistencia de valores y edición básica que los conserva. | 20 PASS |
| Regresión HTTP/MySQL — `ProductControllerTest` y `ProductCharacteristicFlowMySqlTest` | Rutas de producto existentes y flujo de alta con asociaciones en MySQL siguen funcionando. | 11 PASS |
| Integración MySQL — `CharacteristicUsageMySqlTest` | El conteo agrupado y por ID, y el detalle de producto inactivo con valores y unidad, funcionan en MySQL 8.4. | 1 PASS |

**Total:** 35 tests, 0 fallos, 0 errores, 0 omitidos. `git diff --check` sin errores.

## INC-001 — Maven no pudo iniciarse en el entorno inicial

**Tanda:** 1. **Contexto:** ejecución de los tests seleccionados.  
**Test/comportamiento afectado:** todos, antes de compilar.  
**Esperado:** Maven descarga dependencias y ejecuta los tests.  
**Obtenido:** primero faltó `JAVA_HOME`; después el sandbox impidió descargar el parent POM (`Permission denied: getsockopt`).  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** JDK 21 instalado pero no configurado para esa shell; red del sandbox restringida.  
**Resolución:** configurar `JAVA_HOME` solo para los comandos y repetir Maven con acceso a dependencias autorizado.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS, 35 tests.  
**Riesgo o aprendizaje derivado:** las futuras tandas deberán ejecutar Maven con JDK 21 y acceso a dependencias.

## Tests faltantes y riesgos residuales

No faltan las pruebas exigidas por la tanda. No se midió el tiempo de respuesta con un catálogo de gran tamaño; el listado usa una única consulta agrupada de conteos, pero conviene observar su rendimiento si crece el volumen. El conteo puede cambiar entre lectura y confirmación de borrado: es informativo, como establece el contrato.

Renombrado, eliminación, rollback de eliminación y edición de asociaciones quedan sin probar porque corresponden a las tandas 2–4. No se acepta ni adelanta su implementación en esta tanda.

## Archivos

**Código modificado:** `CharacteristicResponse`, `ProductCharacteristicRepository`, `CharacteristicServiceImpl`, `ProductController`, `ProductService` y `ProductServiceImpl`.  
**Tests nuevos:** `CharacteristicUsageQueryTest` y `CharacteristicUsageMySqlTest`.  
**Documentación:** plan vivo y este resultado.  
**Cambios locales previos:** permanecen los de `ProductController`, `ProductControllerTest` y documentación de edición básica; no se incluyeron en este alcance.

**Commit sugerido:** `feat: expose characteristic usage and product detail`. No se creó commit.

## Criterio de cierre

- [x] Conteos y precarga verificados, incluidos productos inactivos.
- [x] Permisos y 404 verificados.
- [x] Regresión relacionada y consulta MySQL ejecutadas.
- [x] Tests faltantes, incidente y riesgos registrados.
- [x] Plan vivo y resultado actualizados.

**Revisión humana antes de aprobar la siguiente tanda:** comprobar el contrato JSON `cantidadProductos` y la respuesta de `GET /api/catalog/products/{id}` para la pantalla administrativa. La tanda 2 requiere aprobación explícita.
