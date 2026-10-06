# Prompt reutilizable: cards y acciones por estado

Copiá el texto siguiente en otro chat y reemplazá `[SECCIÓN]` por la sección que querés modificar (por ejemplo, **Categorías**). El resultado de Productos está en `Frontend/react-starter/src/catalog/products/pages/ProductManagementPage.jsx` y sus pruebas en `ProductManagementPage.test.jsx`.

---

Quiero aplicar en **[SECCIÓN]** el mismo patrón visual y de interacción que se implementó en la página de Productos. Revisá primero la página, sus componentes y sus pruebas; adaptá los nombres, estados y acciones a esta sección. Si alguna parte del patrón ya existe, conservá su comportamiento y completá sólo lo que falte.

1. **Cards según estado.** Para cada elemento activo, usá fondo menta `#ECFDF5`, borde `#A7F3D0` y texto verde oscuro `#064E3B`. Para cada elemento inactivo, usá fondo rosa pálido `#FFF1F2`, borde `#FECDD3` y texto bordó `#881337`. Mantené legibles el título, la descripción y los datos secundarios.
2. **Botones en contraste.** El botón de edición debe tener fondo azul pizarra `#1E293B` y texto blanco en ambos estados. El botón para desactivar debe ser rojo `#B42318` con texto blanco; el botón para reactivar, verde `#047857` con texto blanco. Usá el mismo color de acción en la confirmación. La card muestra el **estado actual** y el botón muestra la **acción siguiente**.
3. **Hover y foco.** Todos los botones de la página, diálogos y formularios deben mostrar un efecto visible al pasar el mouse y un foco de teclado claro. Agregá transición; usá `enabled:hover:` en botones que pueden estar deshabilitados para que el efecto no aparezca mientras lo estén. Conservá una apariencia deshabilitada reconocible.
4. **Avisos temporales.** Los mensajes breves de operación o consulta exitosa deben desaparecer a los **4 segundos**. Reiniciá el plazo al mostrar un aviso nuevo y limpiá el temporizador al cambiar el aviso o desmontar el componente. Los errores y mensajes que requieren una acción o revisión del usuario deben permanecer visibles.
5. **Confirmación compacta.** Al solicitar un cambio de estado, abrí un diálogo pequeño y centrado con fondo atenuado, título, pregunta concreta y botones «Cancelar» y «Confirmar». La lista y su card permanecen visibles detrás; el diálogo no ocupa espacio dentro de la página ni reemplaza la card. Permití cancelar con el botón o con Escape. No ejecutes el cambio hasta que el usuario confirme. Conservá los bloqueos que impiden solicitudes duplicadas o cambios durante una operación pendiente.
6. **Alcance y verificación.** No cambies las API ni las reglas de negocio. Agregá o ajustá pruebas para cards activas e inactivas, colores de botones, hover y estados deshabilitados, duración y reinicio de avisos, apertura y cancelación del diálogo, y confirmación de ambas acciones. Ejecutá las pruebas relevantes y el lint. Revisá visualmente el contraste y el tamaño del diálogo en el navegador; si no hay datos para ver uno de los estados, indicá esa limitación.

Al terminar, mostrame los archivos modificados, un resumen de los cambios y el resultado de las verificaciones.
