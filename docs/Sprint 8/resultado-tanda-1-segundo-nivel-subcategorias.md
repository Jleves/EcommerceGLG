# Resultado de ejecución — tanda 1: gestión y borrado restringido

**Estado: COMPLETA** para el alcance de la tanda. La interfaz y los filtros jerárquicos siguen pendientes de las tandas posteriores.

## Cambios y diferencias respecto del plan

- Alta y respuestas de subcategorías con padre opcional; detalle con hijos y productos directos. Se valida categoría activa, pertenencia, profundidad y unicidad entre hermanos. La edición conserva el padre.
- Borrados restringidos de subcategorías y nuevo DELETE de categorías: 204 para nodos vacíos, 404 inexistentes y 409 con dependencias identificadas, sin desvincular productos. Una categoría vacía puede eliminarse activa o inactiva.
- Bloqueos coordinados de categoría, subcategoría y producto, incluida la actualización de categoría de productos. La edición de subcategoría bloquea primero su categoría mediante consulta por subcategoría; no hay migraciones nuevas.
- Se actualizó el modelo inicial y el plan. El formulario y la navegación no se modificaron. La actualización de producto ahora comprueba y bloquea la categoría antes de bloquear el producto; si ambos IDs son inexistentes se informa primero la categoría.

## Pruebas ejecutadas

Java 21, Maven wrapper y agente Byte Buddy explícito. Las ejecuciones con MySQL 8.4 usaron Testcontainers y Docker con acceso autorizado.

- **HTTP e integración H2:** SubcategoryControllerTest, 6 casos: alta por niveles, padres inválidos, profundidad, duplicados, mismo nombre en padres diferentes, padre fijo, detalle, borrados 204/404/409, autorización y CSRF del DELETE de categoría, incluida categoría inactiva. CategoryControllerTest, 7 casos, y ProductSubcategoryHttpTest, 3 casos: regresión de contratos existentes.
- **Unitarias:** CategoryServiceTest, 9 casos, y ProductServiceTest, 10 casos: dependencias de categoría (hijos, productos o ambos), borrado vacío y regresión de producto y bloqueos.
- **MySQL:** SubcategoryDeleteMySqlTest, 3 casos: borrado vacío, rechazo por productos y por hijos junto con productos, sin pérdida de referencias.
- **Concurrencia MySQL:** ProductSubcategoryConcurrencyMySqlTest, 6 casos: creación/asignación y borrado en ambos órdenes, alta de hijo frente al borrado de padre y cambio de categoría frente al borrado; SubcategoryCreateConcurrencyMySqlTest, 1 caso: altas de igual nombre dejan una fila y un conflicto; ProductCategoryConcurrencyMySqlTest, 2 casos: regresión de concurrencia de categoría.
- Las ejecuciones finales de cada grupo listado terminaron sin fallos ni omisiones. No se ejecutó la suite completa del backend ni package; se ejecutó la regresión directamente relacionada.

## Incidentes

### INC-004 — Inicio de las pruebas y Mockito

**Contexto:** primera ejecución focalizada. **Test/comportamiento afectado:** compilación y ejecución de pruebas. **Esperado:** ejecutar los casos. **Obtenido:** el primer intento no compiló porque el test unitario aún instanciaba el servicio con el constructor anterior; el segundo quedó detenido al adjuntar Mockito. **Clasificación:** adaptación de test y entorno. **Causa:** nuevas dependencias del servicio y probable bloqueo de adjunción dinámica documentado en INC-002. **Resolución:** actualizar mocks de constructor e iniciar Byte Buddy explícitamente. **Código productivo modificado:** no por el incidente. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS. **Riesgo o aprendizaje:** otras ejecuciones pueden requerir el agente explícito.

### INC-005 — Expectativas de bloqueo anteriores

**Contexto:** ProductServiceTest. **Test/comportamiento afectado:** dos casos de actualización y consulta de categoría. **Esperado:** llamada a findById; **obtenido:** lockById. **Clasificación:** expectativa de test obsoleta por el orden de bloqueo previsto. **Causa:** ahora se bloquea la categoría antes del producto para coordinar el borrado. **Resolución:** actualizar mocks y verificaciones, conservando las comprobaciones de comportamiento. **Código productivo modificado:** sí, por la implementación prevista. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS, 10 casos. **Riesgo o aprendizaje:** cambia el orden de consulta y la prioridad de 404 si faltan ambos recursos.

### INC-006 — Instrumentación de alta concurrente

**Contexto:** SubcategoryCreateConcurrencyMySqlTest. **Test/comportamiento afectado:** dos altas del mismo nombre. **Esperado:** ambas alcanzaban una consulta interceptada y una fallaba por restricción de base; **obtenido:** ninguna alcanzó la consulta anterior. **Clasificación:** test obsoleto. **Causa:** el servicio usa consulta por hermanos y serializa mediante bloqueo de categoría. **Resolución:** verificar el resultado observable: una creación, un conflicto y una sola fila. **Código productivo modificado:** sí, por el bloqueo planificado. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS en MySQL. **Riesgo o aprendizaje:** el test ya no fuerza dos lecturas simultáneas porque el bloqueo las impide.

### INC-007 — Acceso inicial a Docker

**Contexto:** primer intento con Testcontainers. **Test/comportamiento afectado:** pruebas MySQL. **Esperado:** iniciar MySQL; **obtenido:** acceso denegado al socket y seis casos omitidos. **Clasificación:** entorno. **Causa:** restricción de acceso a Docker. **Resolución:** reejecución con acceso autorizado. **Código productivo modificado:** no. **Tests modificados:** no por este incidente. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS en los grupos MySQL indicados. **Riesgo o aprendizaje:** se necesita acceso a Docker para repetirlos.

## Pruebas faltantes y riesgos

- No se ejecutó la regresión completa de backend: podrían existir interacciones ajenas a los componentes probados. Recomendación: ejecutar la suite completa antes de integración o despliegue.
- No se instrumentaron pruebas específicas del orden inverso para alta de hijo frente a borrado de padre ni cambio de categoría frente a borrado de categoría. El orden inverso de asignación a subcategoría sí se comprobó. Riesgo: errores de respuesta o bloqueo en esos dos intercalados; recomendar pruebas MySQL adicionales antes del despliegue.
- La interfaz actual todavía advierte que la eliminación desvincula productos; el backend ahora la rechaza. La gestión visual y el manejo de 409 pertenecen a la tanda 3. No validar la experiencia administrativa como terminada antes de esa tanda.
- Los filtros por descendientes y el entorno desplegado no se comprobaron; corresponden a validaciones posteriores.

## Criterio de cierre

- [x] Profundidad, pertenencia y unicidad por hermanos comprobadas en HTTP y persistencia existente.
- [x] Borrado vacío y rechazo por dependencias comprobados; productos conservados.
- [x] Pruebas HTTP, seguridad, MySQL y concurrencia pertinentes ejecutadas; faltantes y riesgos indicados.
- [x] Incidentes registrados y sin decisiones funcionales o arquitectónicas pendientes.

**Revisión humana antes de la tanda 2:** revisar el cambio de contrato de borrado, la advertencia desactualizada de la interfaz y los intercalados concurrentes aún no cubiertos. No se inició la tanda 2.

## Archivos modificados

- Backend: controladores, servicios, repositorios y DTO de categorías y subcategorías; servicio y repositorio de productos.
- Pruebas: CategoryServiceTest, ProductServiceTest, ProductSubcategoryConcurrencyMySqlTest, SubcategoryControllerTest, SubcategoryCreateConcurrencyMySqlTest y SubcategoryDeleteMySqlTest.
- Documentación: modelo inicial, plan de Sprint 8 y este resultado.
