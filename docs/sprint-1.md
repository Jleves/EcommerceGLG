# Sprint 1 — Integrar la base de GLG

**Duración:** dos semanas. **Capacidad inicial:** una persona, 10–20 horas por semana. **Objetivo:** GLG ejecutable con autenticación heredada, roles correctos y modelo de catálogo acordado. El compromiso se revisa al iniciar el sprint según disponibilidad real; ninguna tarjeta se considera terminada por existir en el starter.

## Compromiso propuesto

1. **Integrar la copia del starter.** Código fuente en GLGCorralon, sin historial de la plantilla, secretos, logs, `target`, `node_modules` ni `dist`; nombre de aplicación y documentación ajustados. Resultado: arranque local reproducible.
2. **Validar autenticación y roles en GLG.** Login, cambio de contraseña, MFA por correo y CRUD acotado de usuarios (alta, consulta, cambio de email, activar/desactivar) en backend y React. `SUPER_ADMIN` es el único rol autorizado para cuentas; `ADMIN` y `USER` reciben 403 desde la API.
3. **Definir el modelo de catálogo.** Revisar `modelo-inicial.md` con el negocio y convertirlo en criterios de aceptación para las primeras migraciones del Sprint 2. Las tablas de catálogo no son parte del compromiso de este sprint.

## Trabajo que continúa en backlog

- S3/CloudFront, política de subida y múltiples imágenes: Sprint 3.
- CI/CD, staging y producción: preparación de despliegue posterior. Para este sprint basta ejecutar las comprobaciones locales.
- Estado global de carrito, páginas públicas y theming definitivo de React: sprints de catálogo y carrito.
- Migraciones de categorías, productos, promociones y suscriptores: empezar por categorías/marcas/productos en Sprint 2.

## Criterios de aceptación

- Backend compila, pasa pruebas unitarias/integración disponibles con perfil `test` y aplica Flyway sobre una MySQL de prueba.
- Frontend pasa tests, lint y build; un usuario puede iniciar sesión, cambiar su contraseña y activar/desactivar MFA con un SMTP de prueba.
- Un `SUPER_ADMIN` puede crear y consultar cuentas, cambiar email y activar/desactivar; `ADMIN` y `USER` no acceden a esas pantallas ni endpoints.
- Las fallas de MySQL, SMTP o Docker se registran como bloqueo de validación, sin marcar la tarjeta como terminada.
- Se realiza revisión del incremento y retrospectiva breve. La velocidad observada, no la cantidad de tarjetas movidas, define el siguiente compromiso.

## Estado de validación local

La copia GLG pasó las suites automatizadas de backend y frontend y los recorridos completos con Edge, MySQL y Mailpit. La evidencia está en `validacion-sprint-1.md`. Esto valida el incremento localmente; antes de marcarlo entregado al cliente faltan la revisión del modelo por el negocio y la configuración/verificación del entorno de destino.

