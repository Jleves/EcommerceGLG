# Tanda 3 — resultado de ejecución

**Plan:** [s2-be-editar-o-eliminar-caracteristica-plan-implementacion.md](s2-be-editar-o-eliminar-caracteristica-plan-implementacion.md).  
**Estado:** COMPLETA.

## Alcance y precondiciones

La tanda 2 estaba implementada, sus pruebas existían y el árbol de trabajo estaba limpio. El alta de producto ya bloqueaba las definiciones solicitadas en orden ascendente. Se implementó el borrado físico de una característica y de sus asociaciones en una transacción, con 204 sin cuerpo o 404 para ID inexistente. Los productos y sus otras características se conservan.

La edición de asociaciones durante el PUT de producto y su carrera con el borrado pertenecen a la tanda 4. No se modificaron migraciones ni el esquema. Los archivos previstos fueron controlador, interfaz y servicio de características, repositorio de asociaciones, dos archivos de pruebas y documentación. No fue necesario ampliar el análisis más allá de los archivos y tests relacionados; se inspeccionó V7 para confirmar las claves foráneas sin cascada.

## Cambios realizados

- `CharacteristicController.delete` expone `DELETE /api/catalog/characteristics/{id}` para los tres roles de catálogo, con CSRF aplicado por la configuración existente.
- `CharacteristicServiceImpl.delete` bloquea la fila, borra asociaciones y luego la definición dentro de `@Transactional`; `flush` fuerza el segundo DELETE antes de confirmar.
- `ProductCharacteristicRepository.deleteByCharacteristicId` ejecuta el borrado explícito de asociaciones.

**Diferencia respecto del plan:** la API de `JpaRepository` disponible no incluye `deleteAndFlush`; `delete` seguido de `flush` mantiene la secuencia y la atomicidad previstas. El trigger usado para inducir el fallo de rollback requiere `log_bin_trust_function_creators=1` solo en el contenedor MySQL del test.

## Tests ejecutados

Comando final desde `Backend/spring-starter`, con `JAVA_HOME=C:\Users\jlinf\.jdks\ms-21.0.7`:

```powershell
.\mvnw.cmd '-Dtest=Characteristic*Test,ProductCharacteristic*Test,ProductControllerTest,GlobalExceptionHandlerTest' test
```

| Categoría | Comprobación | Resultado |
| --- | --- | --- |
| Integración MySQL | Definición usada por dos productos, incluido uno inactivo: se borran solo sus valores; productos y otra definición permanecen. Definición sin uso: borrado exitoso. | PASS |
| Atomicidad MySQL | Un trigger provoca un fallo al borrar la definición, después del borrado de asociaciones; nombre, valor y unidad siguen presentes tras rollback. | PASS |
| Concurrencia MySQL | Alta confirmada primero: el borrado espera y quita su asociación. Borrado confirmado primero: el alta espera, rechaza el ID inexistente y no crea producto ni huérfano. | PASS |
| HTTP y seguridad H2 | 204 sin cuerpo, 404 `ApiError`, SUPER_ADMIN/ADMIN/USER admitidos, CSRF exigido y anónimos/otro rol rechazados. | PASS |
| Regresión | Alta, consulta, renombrado, conteos, flujo de producto, atomicidad de alta, edición básica y manejo de errores. | PASS |

**Totales de la corrida final:** 53 tests, 0 fallos, 0 errores, 0 omitidos. De ellos, 7 son nuevos de esta tanda (4 MySQL y 3 HTTP/H2); los otros 46 son regresión relacionada. No se añadieron tests unitarios aislados: las garantías de esta tanda dependen de transacciones, SQL y seguridad HTTP.

## Incidentes

### INC-004 — Dependencias Maven inaccesibles en sandbox

**Contexto:** primera ejecución de pruebas.  
**Test/comportamiento afectado:** toda la suite, antes de compilar.  
**Esperado:** Maven resuelve el parent POM y ejecuta pruebas.  
**Obtenido:** `Permission denied: getsockopt` al resolver Spring Boot 4.1.0.  
**Clasificación:** configuración/infraestructura/entorno.  
**Causa:** red restringida en sandbox.  
**Resolución:** ejecución Maven con acceso a dependencias autorizado.  
**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** nuevas corridas pueden requerir el mismo acceso.

### INC-005 — Método de repositorio no disponible

**Contexto:** primera compilación con el servicio de borrado.  
**Test/comportamiento afectado:** compilación previa a todos los tests; secuencia de borrado.  
**Esperado:** eliminar y descargar la definición luego de las asociaciones.  
**Obtenido:** compilación falló porque `JpaRepository` no define `deleteAndFlush`.  
**Clasificación:** defecto de implementación.  
**Causa:** el plan usó un nombre de operación que no existe en la API utilizada.  
**Resolución:** `repository.delete(definition)` seguido de `repository.flush()` dentro de la misma transacción.  
**Código productivo modificado:** sí. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** ninguno adicional; las pruebas MySQL verificaron el orden y rollback.

### INC-006 — MySQL impidió crear el trigger de fallo

**Contexto:** primera corrida del test de rollback.  
**Test/comportamiento afectado:** `CharacteristicDeleteMySqlTest.failureDeletingDefinitionRollsBackRemovedValues`.  
**Esperado:** trigger creado; falla el segundo DELETE y la transacción restaura asociaciones.  
**Obtenido:** MySQL error 1419 al crear el trigger, antes de invocar el servicio.  
**Clasificación:** configuración/infraestructura/entorno del test.  
**Causa:** binary logging activo y usuario sin privilegio SUPER.  
**Resolución:** habilitar `log_bin_trust_function_creators=1` en el contenedor de esta clase de test; no afecta producción.  
**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** configuración del contenedor de test sí; migraciones no.  
**Resultado posterior:** PASS.  
**Riesgo o aprendizaje derivado:** el test requiere esta opción para inducir el fallo mediante trigger.

## Tests faltantes y riesgos

**Planificados frente a ejecutados:** integración MySQL, rollback, ambos órdenes de concurrencia MySQL y HTTP/seguridad están implementados y pasaron. No faltan pruebas definidas para la tanda.

No se hizo una prueba de carga con muchas altas simultáneas. Su comportamiento bajo saturación, incluidos posibles timeouts de bloqueo, queda como riesgo operativo: podría producir errores transitorios; se recomienda evaluarlo si el volumen de escrituras aumenta. La carrera entre borrado y edición de valores no se probó porque esa escritura pertenece a la tanda 4; allí debe verificarse que no haya huérfanos ni cambios parciales. El conteo previo al borrado sigue siendo informativo.

**Decisiones pendientes:** ninguna.

## Criterio de cierre

- [x] Borrado, rollback y conservación de productos demostrados en MySQL.
- [x] Ambos órdenes de carrera con alta verificados.
- [x] Permisos, errores, tests faltantes, riesgos y resultado registrados.

**Revisión humana antes de aprobar la tanda 4:** comprobar el contrato del DELETE y el riesgo operativo de timeouts bajo carga. La siguiente tanda requiere aprobación explícita.
