# Ajuste posterior a la Tanda 4 — galería sin reemplazo

**Fecha:** 17/10/2026  
**Estado:** COMPLETO

## Decisión y comportamiento vigente

Se retiró «Reemplazar foto» para simplificar la galería. Borrar una foto la retira de MySQL y de la API en la transacción, compacta las posiciones de las restantes y, si era la principal, convierte en principal a la primera restante. El trabajador conserva la eliminación persistente de sus dos objetos S3 con reintentos. Si quedan cuatro fotos, el producto puede permanecer con cuatro; el usuario puede subir otra después, hasta el máximo de cinco.

## Cambios realizados

- Se retiraron el selector y la confirmación de reemplazo del modal, su función del cliente HTTP y el `PUT /api/catalog/products/{productId}/images/{imageId}` del controlador.
- Se retiraron del servicio la reserva, subida, publicación, bloqueo y retención de 25 horas exclusivos del reemplazo. No se modificó la migración ni el esquema: la revisión sigue identificando las claves de cada carga y `image_storage_operations` sigue siendo necesaria para cargas incompletas y borrados.
- Se conservaron el trabajador y su tratamiento de operaciones anteriores aún pendientes. Una prueba comprueba que puede limpiar una carga pendiente de reemplazo anterior sin borrar la foto publicada.
- La confirmación de borrado ahora explica la compactación y la promoción de principal. El plan vigente y la validación pendiente de la Tanda 5 se actualizaron; los resultados originales de las tandas permanecen como historial.

**Diferencia respecto del plan original:** se elimina por decisión posterior del usuario la operación de reemplazo de la API y del frontend. Es un cambio de contrato deliberado.

## Pruebas ejecutadas

| Grupo | Qué demuestra | Resultado |
| --- | --- | --- |
| Frontend: modal, pantalla y servicio HTTP | No existe la acción de reemplazo; borrar requiere confirmación, deja cuatro fotos visibles y habilita una carga opcional; los otros controles conservan su comportamiento | 37 PASS |
| Backend HTTP `ProductImageControllerTest` | `PUT /images/{imageId}` ya no está disponible; carga, orden, principal, borrado, roles y CSRF siguen funcionando | 4 PASS |
| Backend `ProductImageFlowTest` | Borrado y promoción, cupo liberado tras borrar de cinco a cuatro, carga opcional, limpieza con reintento y tratamiento de una operación anterior | 9 PASS tras corregir INC-501 |
| Lectura pública `PublicProductHttpTest` | Contrato público relacionado sin regresión | 3 PASS |
| MySQL 8.4 `ProductImageMySqlIT` | Borrado conserva una principal y posiciones consecutivas en MySQL real; límite concurrente de cinco | 2 PASS |
| Calidad | ESLint, build Vite y `git diff --check` | PASS; Git solo avisó conversión LF/CRLF |

Los tests Java se ejecutaron fuera del sandbox porque Mockito necesita adjuntar su agente; la primera corrida dentro del sandbox quedó sin finalizar. No se ejecutó un recorrido real de AWS en este ajuste: continúa previsto para la Tanda 5.

## Incidente INC-501 — Simulación de borrado dependía de otras pruebas

**Contexto:** primera ejecución de `ProductImageFlowTest` después de adaptar la prueba de reintento al borrado.  
**Test/comportamiento afectado:** `failedDeleteCleanupRetriesWithoutRestoringPhoto`.  
**Esperado:** el fallo simulado de S3 deja `attempts=1` y el siguiente intento termina la limpieza sin restaurar la foto.  
**Obtenido:** `attempts=0` para la operación de esa foto; la corrida tuvo 9 tests, 1 fallo.  
**Clasificación:** defecto del test.  
**Causa:** el almacenamiento simulado fallaba en la segunda eliminación *global*. Otra operación de limpieza pendiente podía consumir ese fallo antes de la foto que examinaba el test.  
**Resolución:** hacer fallar la eliminación de la clave de detalle de esa foto en particular y repetir el mismo grupo.  
**Código productivo modificado por el incidente:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.  
**Resultado posterior:** PASS, 9/9.  
**Riesgo o aprendizaje derivado:** simular fallos por clave evita dependencia del orden en que el trabajador procesa operaciones ajenas.

## Faltantes y riesgos residuales

- **Staging real:** falta comprobar en navegador y con MySQL/S3/CloudFront de staging el recorrido borrar de cinco a cuatro, conservar cuatro sin subir y cargar otra opcionalmente. Riesgo: configuración o entrega CDN no cubierta por almacenamiento simulado. Se incorpora a la Tanda 5.
- **Operaciones anteriores:** el trabajador conserva el procesamiento de limpiezas ya registradas, pero no se hizo una migración ni una auditoría de datos de staging. Riesgo: alguna operación histórica puede seguir pendiente de reintento; revisar los logs de limpieza en la validación operativa.

**Decisiones pendientes:** ninguna para el ajuste. No se adelantó la Tanda 5.

## Cierre

- [x] Reemplazo retirado del modal, cliente y backend.
- [x] Borrado compacta y promueve principal; cuatro fotos pueden permanecer y admiten otra carga opcional.
- [x] Limpieza persistente de borrados y cargas fallidas conservada.
- [x] Pruebas enfocadas, MySQL real, lint y build pasaron; incidente y faltantes registrados.
