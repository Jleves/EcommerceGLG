# GLG Corralón

Base técnica del ecommerce de GLG Construcciones. El frontend usa React/Vite y el backend Spring Boot con MySQL. La venta de la V1 termina en una consulta por WhatsApp; no incluye pagos online.

## Estado

Este repositorio se creó a partir de una copia de `spring-starter`, sin su historial Git ni sus logs o artefactos. La autenticación, MFA por correo y gestión de usuarios heredadas **pasaron pruebas locales integradas en GLG**; aún requieren configuración y revisión en el entorno de destino. El catálogo público y administrativo todavía no están implementados. Ver [Sprint 1](docs/sprint-1.md), [evidencia de validación](docs/validacion-sprint-1.md) y [backlog para Trello](docs/trello-backlog.md).

## Ejecutar localmente

- Java 21, Node.js y MySQL.
- Backend: configurar variables equivalentes a `deploy/backend.env.example` con valores locales reales; desde `Backend/spring-starter`, ejecutar `./mvnw.cmd spring-boot:run` en Windows. Spring aplica las migraciones Flyway al iniciar.
- Frontend: desde `Frontend/react-starter`, ejecutar `npm ci` y `npm run dev`. Vite reenvía `/api` a `http://localhost:8080`.
- Para pruebas sin MySQL ni SMTP reales, el backend tiene perfil `test` con H2 y simulaciones; ese resultado no equivale a una validación de despliegue.

No guardar contraseñas, claves JWT/MFA ni credenciales SMTP en el repositorio. El archivo de `deploy` es solo una plantilla con marcadores.

## Pruebas

Desde `Backend/spring-starter`: `./mvnw.cmd test` y `./mvnw.cmd package` con Java 21.

Desde `Frontend/react-starter`: `npm ci`, `npm test`, `npm run lint` y `npm run build`.

Las pruebas con MySQL Testcontainers y Mailpit requieren Docker. Los recorridos de navegador se invocan aparte según [e2e/README](Frontend/react-starter/e2e/README.md). El procedimiento de MFA está en [Operación MFA](deploy/OPERACION_MFA.md).

