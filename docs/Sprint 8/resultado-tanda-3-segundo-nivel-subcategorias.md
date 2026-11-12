# Resultado de ejecución — tanda 3: segundo nivel de subcategorías

**Estado: PARCIAL.** La implementación React y las pruebas disponibles están realizadas; falta validación integral.

## Cambios realizados

- Alta de raíces e hijos, presentación por categoría con identificación del padre y edición sin traslado. El detalle previo al borrado muestra hijos y productos directos y bloquea la confirmación ante dependencias; el servidor conserva la verificación final.
- Borrado físico de categoría con confirmación y errores; desactivación y reactivación siguen separadas. Mutaciones sin reintentos automáticos.
- Producto con selección opcional de dos niveles, reconstrucción al editar y limpieza al cambiar categoría o padre. Se envía un solo ID.
- Navegación pública y filtros para ambos niveles mediante las URL existentes; se conserva el cierre del menú y retorno de foco móvil.
- Sin cambios de backend, esquema ni contratos.

## Verificaciones

- **Componentes y regresión:** npm test -- --run: 383 pruebas exitosas en 41 archivos. Incluye creación de hijo, selección y cambio de padre, bloqueo por dependencias, conflictos 409, enlaces de ambos niveles y foco al cerrar el menú móvil. Las pruebas React usan mocks y jsdom.
- **Análisis y compilación:** npm run lint y npm run build: PASS.
- **Backend:** mvnw.cmd test y mvnw.cmd package no iniciaron: JAVA_HOME no está configurado correctamente y no se encontró Java en PATH ni en las ubicaciones habituales revisadas.
- **MySQL:** no ejecutado; el cliente Docker no pudo acceder al daemon en este entorno y falta Java para ejecutar las pruebas.
- **Integral y visual:** no ejecutados con servicios reales ni en navegador.

## Incidentes

### INC-008 — Expectativas anteriores al contrato

**Contexto:** primera suite React. **Test/comportamiento afectado:** alta y borrado de subcategoría. **Esperado:** campo de padre opcional y rechazo con productos; **obtenido:** cuatro fallos por esperar payload anterior o desvinculación. **Clasificación:** cambio deliberado de contrato respaldado por el plan; preparación de tests obsoleta. **Causa:** pruebas de la interfaz anterior. **Resolución:** actualizar expectativas y verificar bloqueo. **Código productivo modificado:** sí, por la tanda. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS. **Riesgo:** los mocks no demuestran persistencia.

### INC-009 — Hijo ausente en menú móvil

**Contexto:** prueba nueva de navegación. **Esperado:** enlace del hijo en móvil; **obtenido:** solo raíz. **Clasificación:** defecto de implementación. **Causa:** cambio inicial solo en escritorio. **Resolución:** representar hijos también en móvil y repetir la suite. **Código productivo modificado:** sí. **Tests modificados:** no para alterar la expectativa. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PASS. **Riesgo:** falta comprobar densidad y disposición visual reales.

### INC-010 — Entorno backend no disponible

**Contexto:** Maven y MySQL. **Esperado:** ejecutar test, package y pruebas MySQL. **Obtenido:** Maven se detuvo por JAVA_HOME; Docker devolvió acceso denegado al daemon. **Clasificación:** configuración/entorno. **Causa:** Java no configurado y acceso al daemon no disponible en la sesión. **Resolución:** pendiente de entorno con Java 21 y Docker accesible. **Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no. **Resultado posterior:** PENDIENTE. **Riesgo:** regresión backend e integración no verificadas.

## Faltantes y riesgos

- Recorrido integral crear jerarquía → asignar → filtrar por padre e hijo → rechazar borrado → reasignar → borrar hijo y padre: pendiente por falta de entorno completo. Riesgo: fallas UI/API/persistencia; ejecutarlo con Java 21, MySQL y navegador antes de aprobar.
- Revisión visual y de teclado en escritorio y móvil reales: pendiente; riesgo de menú denso o problemas de foco y disposición no detectados por jsdom. Inspeccionar ambos tamaños.
- Backend test/package y MySQL: pendientes por INC-010; repetir con Java 21 y Docker. No hay validación del entorno desplegado.
- Cobertura específica de todas las combinaciones de árbol, edición con padre fijo y limpieza de filtros no es exhaustiva; completar junto al recorrido integral.

## Cierre

- [x] Administración y catálogo representan ambos niveles.
- [x] Producto admite categoría sola, raíz o hijo; regresión React ejecutada.
- [ ] Recorrido integral, backend/MySQL y revisión visual ejecutados.
- [x] Incidentes, faltantes y riesgos documentados.

**Decisiones pendientes:** ninguna. Antes de aprobar, revisar los faltantes anteriores y verificar el comportamiento con datos reales.

**Archivos modificados:** servicios, componentes y pruebas React de categorías, subcategorías, productos y catálogo público; plan y este resultado.
