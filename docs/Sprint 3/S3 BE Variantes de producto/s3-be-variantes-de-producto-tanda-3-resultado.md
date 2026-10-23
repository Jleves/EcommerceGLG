# S3 BE — Variantes de producto: resultado de Tanda 3

**Fecha:** 23/10/2026. **Estado:** PARCIAL (implementación completa; suite frontend global no verde). **Plan:** [Tanda 3](s3-be-variantes-de-producto-plan-implementacion.md#tanda-3--lectura-pública-y-eliminación-del-precio-duplicado).

## Revisión previa y alcance

Las tandas 1 y 2 seguían implementadas y el árbol de trabajo estaba limpio. Se revisaron los servicios, DTO y repositorios de producto/variante, V6/V10, la lectura pública, las pruebas relacionadas y el formulario React que aún envía precio/disponibilidad heredados. No se encontraron decisiones de arquitectura pendientes. Esta tanda abarca selección pública y retiro de la duplicación persistente; quedan fuera la interfaz de gestión de variantes, carrito, presupuesto y despliegue.

## Cambios realizados

- La lista pública pagina productos activos con al menos una variante activa mediante `EXISTS`, luego carga las variantes de la página en lote. Selecciona la disponible más barata, o la activa más barata si ninguna está disponible; desempata por ID.
- Lista y detalle conservan `precioReferencia` y `disponible` calculados desde la variante seleccionada y añaden `selectedVariantId`. El detalle incluye solo variantes activas con ID, atributo, valor, unidad, SKU, precio y disponibilidad. La miniatura, las características compartidas y la galería siguen ligadas al producto.
- V11 retira la restricción de precio y las dos columnas antiguas de `products`. `Product` y sus servicios dejan de leerlas o escribirlas. El alta y el PUT heredados siguen aplicando precio/disponibilidad a la única variante; el PUT con esos campos continúa rechazado para productos con varias variantes.
- Los escalares administrativos siguen representando la variante de menor ID y ya no tienen respaldo alternativo en `Product`. Las pruebas que construían productos con esos campos se adaptaron; las pruebas históricas de V10 se fijaron en esa versión.

**Diferencias respecto del plan:** ninguna funcional. `PublicProductController` no necesitó cambios porque conserva rutas y parámetros.

## Tests ejecutados

| Categoría | Qué demuestra | Resultado |
| --- | --- | --- |
| Selección/HTTP/seguridad H2 | Precio, disponibilidad, desempate, paginación por producto, visibilidad, DTO, galería y rutas | PASS; `PublicProductHttpTest`: 5/5 en suite final |
| Migración H2/MySQL | Backfill y retiro de columnas, preservación de valores | PASS; V10 histórico y V11 nuevos en ambos motores |
| Compatibilidad administrativa | Alta/edición heredadas y rechazo de PUT multivariante | PASS en corrida dirigida y suite final |
| Suite backend | Regresión general con Docker/MySQL | PASS; `mvnw.cmd test -q`: 621 pruebas en 106 clases, 0 fallas, 0 errores, 0 omisiones. Primera corrida: 1 error corregido en prueba histórica |
| Frontend completo | Regresión React | FAIL en dos corridas: 264/265 PASS; mismo test MFA ajeno a productos |
| Frontend dirigido | Formulario, gestión y servicio de productos; repetición de MFA | PASS; productos 40/40 y productos + MFA 57/57 |
| Empaquetado | Artefacto backend | PASS; `mvnw.cmd package -q -DskipTests` |

## Incidentes

### INC-001 — Mockito detenido dentro del sandbox

**Contexto:** la primera corrida dirigida dentro del sandbox quedó sin progreso tras cargar el agente de Mockito. **Test/comportamiento afectado:** pruebas Spring Boot con Mockito. **Esperado:** terminar y generar reportes Surefire. **Obtenido:** sin resultado hasta la interrupción. **Clasificación:** configuración/infraestructura/entorno. **Causa:** restricción del sandbox, consistente con las tandas anteriores. **Resolución:** repetir fuera del sandbox. **Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS en corrida dirigida. **Riesgo o aprendizaje derivado:** estas pruebas requieren ejecución fuera del sandbox mientras persista la restricción.

### INC-002 — Pruebas que dependían de las columnas antiguas

**Contexto:** al quitar los campos de `Product`, `test-compile` detectó fixtures y aserciones que aún los usaban. **Test/comportamiento afectado:** persistencia, servicio, HTTP e imágenes. **Esperado:** verificar el contrato de producto y precio en su fuente vigente. **Obtenido:** errores de compilación por métodos retirados. **Clasificación:** cambio deliberado de contrato respaldado por la tanda 3. **Causa:** las pruebas correspondían al esquema anterior. **Resolución:** mover las verificaciones de precio/disponibilidad a variantes; conservar las verificaciones propias de producto; fijar V10 en sus pruebas históricas. **Código productivo modificado:** sí. **Tests modificados:** sí. **Configuración/migraciones modificadas:** sí. **Resultado posterior:** `test-compile`, corrida dirigida y suite final PASS. **Riesgo o aprendizaje derivado:** mantener separadas las pruebas de versiones históricas y del esquema actual.

### INC-003 — Migración histórica de subcategorías consultó columna retirada

**Contexto:** la primera suite completa compiló antes de corregir la consulta final de `SubcategoryMigrationUpgradeMySqlTest`. **Test/comportamiento afectado:** preservación del precio y categoría tras migrar de V7 a la versión actual. **Esperado:** producto y precio migrado presentes. **Obtenido:** `SQLSyntaxErrorException` por `p.precio_referencia` tras V11. **Clasificación:** cambio deliberado de contrato. **Causa:** la prueba consultaba el precio en la tabla anterior. **Resolución:** unir `product_variants` y comprobar allí el mismo valor. **Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS en la segunda suite MySQL (1/1); suite backend final 621/621 PASS. **Riesgo o aprendizaje derivado:** las pruebas de actualización hasta la versión actual deben comprobar la ubicación vigente de los datos.

### INC-004 — Fallo aislado en la suite frontend completa

**Contexto:** `npm test` se ejecutó dos veces, la segunda sin Maven en paralelo. **Test/comportamiento afectado:** navegación tras verificar MFA en `MfaLoginFlow.test.jsx`. **Esperado:** `/app/profile?tab=security`. **Obtenido:** `/login/mfa` en una de 265 pruebas de cada corrida. **Clasificación:** probable defecto de sincronización del test; causa exacta todavía no confirmada. **Causa:** el test espera el estado `authenticated` y comprueba la navegación en una aserción inmediata; esta secuencia puede observar el estado antes del cambio de ruta. No hay cambios frontend ni relación observada con el contrato de producto. **Resolución:** no se modificó el test ajeno al alcance; se repitió con la regresión de productos y pasó. **Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS en repetición dirigida (57/57) y regresión de productos (40/40); FAIL en la segunda suite completa, de nuevo 264/265. **Riesgo o aprendizaje derivado:** la suite frontend global sigue no verde y requiere investigar la sincronización de la prueba MFA en su propia tarea.

### INC-005 — Aviso de Surefire al cerrar la JVM de pruebas

**Contexto:** al terminar la suite backend se imprimió `Surefire is going to kill self fork JVM` tras 30 segundos de espera. **Test/comportamiento afectado:** cierre del proceso de pruebas, no una aserción. **Esperado:** salida limpia. **Obtenido:** aviso de Surefire, con código de salida 0 y 621 pruebas PASS. **Clasificación:** configuración/infraestructura/entorno. **Causa:** no determinada; ya se había observado en la tanda 2. **Resolución:** no se alteró código ni configuración por un aviso de cierre; se verificaron código de salida y reportes XML. **Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS con aviso. **Riesgo o aprendizaje derivado:** vigilar el cierre del fork en futuras corridas, sin confundir el aviso con una falla de prueba.

## Tests faltantes y riesgos residuales

- No faltó ninguna prueba planificada para esta tanda. La suite frontend global sigue fallando por INC-004; el comportamiento de productos sí quedó cubierto por 40 pruebas dirigidas.
- No se comprobó staging, despliegue coordinado ni recorrido de navegador. La verificación local no demuestra que V11 y el backend correspondiente se hayan instalado juntos en destino; un backend anterior no es compatible con el esquema V11. Se recomienda validar esa secuencia en staging antes de desplegar.
- La interfaz React actual administra precio/disponibilidad de un producto monovariante. La gestión visual multivariante, carrito y presupuesto pertenecen a tarjetas posteriores.
- La suite backend termina con un aviso de cierre de Surefire (INC-005) aunque el proceso sale con código 0 y los reportes de las 106 clases ejecutadas indican 621/621 PASS.

## Archivos modificados

V11; entidad, repositorios, servicios y DTO de producto/variante; pruebas HTTP, persistencia, migraciones y fixtures relacionados; este informe y la sección de resultado del plan.

## Criterio de cierre

- [x] API pública selecciona y expone el ID correcto sin listar variantes inactivas.
- [x] Precio/disponibilidad tienen una sola fuente persistente y las fotos siguen ligadas al producto.
- [x] Suite backend final, MySQL, `package` y regresión pertinente de productos documentados.
- [ ] Suite frontend global sin fallas; el caso MFA de INC-004 falla de manera reproducible solo en esa corrida completa.
