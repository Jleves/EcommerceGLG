# Ejecutar una tanda de implementación

Implementá exclusivamente la tanda indicada del plan de implementación proporcionado.

El plan y la propuesta asociada son la referencia para conocer alcance, decisiones y restricciones.

# Regla principal

Implementá solamente el alcance de esta tanda.

NO comiences trabajo perteneciente a tandas posteriores aunque resulte conveniente hacerlo ahora.

Si aparece una dependencia inesperada que obliga a modificar arquitectura, alcance o una decisión ya tomada:

**DETENER esa parte de la implementación y marcarla como DECISIÓN PENDIENTE.**

No resolver silenciosamente decisiones arquitectónicas o funcionales relevantes.

---

# 1. Revisión previa — contexto mínimo necesario

Antes de modificar código, construí únicamente el contexto necesario para ejecutar esta tanda.

## Regla de contexto

NO releas ni reanalices todo el proyecto, toda la propuesta, todo el plan ni el historial completo de tandas anteriores por defecto.

Revisá solamente:

1. la sección correspondiente a la tanda actual;
2. las decisiones de la propuesta directamente relacionadas con esta tanda;
3. las dependencias directas indicadas por el plan;
4. los resultados de tandas anteriores únicamente cuando sean una precondición de la tanda actual;
5. las clases y archivos realmente involucrados;
6. los tests existentes relacionados;
7. los cambios recientes de Git necesarios para comprobar que las precondiciones siguen siendo válidas.

Ampliá el contexto solamente cuando aparezca una dependencia, contradicción, contrato o riesgo que lo justifique.

Si necesitás ampliar el análisis, explicá brevemente qué información adicional necesitás revisar y por qué.

No reconstruyas decisiones que ya estén documentadas y continúen siendo válidas.

## Verificación previa

Con ese contexto mínimo:

1. verificá que las precondiciones de la tanda continúen siendo válidas;
2. verificá que el código actual sea compatible con lo indicado por el plan;
3. identificá los componentes que realmente necesitan modificación;
4. identificá los tests directamente relacionados.

Informá brevemente:

## Alcance que se va a implementar

Qué comportamiento pertenece a esta tanda.

## Fuera de alcance

Qué cambios relacionados pertenecen a otras tandas y NO se realizarán ahora.

## Archivos previstos

Qué archivos/clases probablemente serán modificados y por qué.

## Contexto adicional requerido

Indicá solamente si fue necesario ampliar el análisis más allá del contexto mínimo y explicá por qué.

Si descubrís una contradicción importante entre plan y código actual, no continúes automáticamente.

# 2. Implementación

Realizá los cambios necesarios respetando:

* arquitectura existente;
* decisiones documentadas;
* responsabilidades de las capas;
* convenciones del proyecto;
* alcance de la tanda.

Evitá refactors no relacionados.

Si un refactor adicional resulta imprescindible para implementar correctamente la tanda, documentá:

* por qué es necesario;
* alcance;
* riesgo;
* archivos afectados.

No amplíes silenciosamente el alcance.

---

# 3. Tests

Implementá y ejecutá los tests definidos para esta tanda.

Separá los resultados por categoría cuando corresponda:

* unitarios;
* integración;
* seguridad/autorización;
* concurrencia;
* regresión.

Para cada grupo indicá:

* qué se ejecutó;
* qué comportamiento demuestra;
* resultado.

No uses únicamente:

> "Todos los tests pasan."

El resultado debe permitir conocer qué fue realmente comprobado.

---

# 4. Protocolo obligatorio ante tests fallidos

Si un test falla, NO modificar inmediatamente el test para conseguir que quede verde.

Primero analizá y registrá:

1. test que falló;
2. comportamiento que intenta verificar;
3. resultado esperado;
4. resultado obtenido;
5. causa confirmada o probable.

Clasificá el incidente como:

* defecto de implementación;
* defecto del test;
* cambio deliberado de contrato;
* configuración/infraestructura/entorno;
* problema de datos;
* causa todavía no determinada.

Después decidí qué corresponde modificar.

## Si falla la implementación

Corregí el código productivo y conservá el test cuando su expectativa siga siendo válida.

## Si falla el test

Modificá el test únicamente cuando puedas justificar que su expectativa, preparación o implementación es incorrecta.

## Si cambió el contrato

No adaptes automáticamente el test.

Verificá que el cambio esté respaldado por la propuesta/plan. Si no lo está, marcá una DECISIÓN PENDIENTE.

## Si la causa no está determinada

No realices cambios especulativos únicamente para obtener verde.

Investigá o dejá el incidente pendiente.

---

# 5. Registro de incidentes

Todo incidente relevante encontrado durante la tanda debe incorporarse al documento de implementación.

Formato:

## INC-[identificador]

**Contexto:**
...

**Test/comportamiento afectado:**
...

**Esperado:**
...

**Obtenido:**
...

**Clasificación:**
...

**Causa:**
...

**Resolución:**
...

**Código productivo modificado:** sí/no
**Tests modificados:** sí/no
**Configuración/migraciones modificadas:** sí/no

**Resultado posterior:** PASS / FAIL / PENDIENTE

**Riesgo o aprendizaje derivado:**
...

No ocultes intentos fallidos relevantes que hayan cambiado la comprensión del problema.

No es necesario registrar errores triviales de edición sin impacto técnico.

---

# 6. Tests faltantes

Al finalizar, compará:

```text
tests planificados
        vs
tests realmente implementados/ejecutados
```

Declarar explícitamente cualquier prueba faltante.

Para cada faltante indicá:

* comportamiento no probado;
* motivo;
* riesgo residual;
* impacto potencial;
* recomendación.

No considerar implícitamente aceptado un riesgo porque la implementación funcione o porque otros tests estén verdes.

---

# 7. Regresión

Ejecutá los tests de regresión razonablemente relacionados con los componentes modificados.

Si no es posible ejecutar determinada regresión:

* indicarlo;
* explicar por qué;
* registrar el riesgo.

No afirmar que no existen regresiones únicamente porque los tests nuevos pasen.

---

# 8. Actualizar el documento vivo

Actualizá la sección `Resultado de ejecución` correspondiente a esta tanda sin eliminar la planificación original.

Debe quedar registrado:

## Resultado de ejecución

**Estado:** COMPLETA / PARCIAL / BLOQUEADA

**Cambios realizados**

...

**Diferencias respecto del plan**

...

**Tests ejecutados**

...

**Tests exitosos**

...

**Tests fallidos**

...

**Incidentes**

...

**Tests faltantes**

...

**Riesgos residuales**

...

**Decisiones pendientes descubiertas**

...

**Archivos modificados**

...

---

# 9. Cierre de tanda

Antes de finalizar, compará el resultado con el criterio de finalización definido en el plan.

Presentá un checklist:

```text
[x] ...
[x] ...
[ ] ...
```

Una tanda no debe declararse COMPLETA únicamente porque compile o porque los tests ejecutados estén verdes.

Si existen faltantes importantes, utilizar PARCIAL o BLOQUEADA según corresponda.

---

# 10. Detenerse

Una vez finalizada la tanda:

**DETENER LA IMPLEMENTACIÓN.**

No comenzar la siguiente tanda.

No aprovechar el contexto restante para adelantar cambios futuros.

Entregar un resumen corto indicando:

* qué cambió;
* qué tests demostraron;
* qué incidentes aparecieron;
* qué quedó sin probar;
* qué riesgos permanecen;
* qué debería revisar el responsable humano antes de aprobar la siguiente tanda.

La siguiente tanda solamente se ejecutará después de aprobación explícita.
