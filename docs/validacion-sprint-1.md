# Evidencia local de Sprint 1 — 28/09/2026

La copia GLG se probó en Windows con Java 21, Node, Edge, Docker Desktop, MySQL 8.4 Testcontainers y Mailpit. No se usaron cuentas ni datos productivos.

| Comprobación | Resultado |
| --- | --- |
| `npm ci --offline` | Dependencias instaladas desde caché. |
| `npm test` | 115 pruebas, 14 archivos, todas correctas. |
| `npm run lint` | Correcto. |
| `npm run build` | Correcto; Vite generó `dist/`, ignorado por Git. |
| `mvnw.cmd test` | 462 pruebas, 0 fallos/errores/omitidas. Incluye MySQL Testcontainers y Flyway V1–V4. |
| `mvnw.cmd -Dtest=MfaBrowserIT,AdminUserBrowserIT test` | 2 recorridos, 0 fallos. React/Vite + Spring HTTP + Edge + MySQL + Mailpit. |

Los recorridos verifican login con los tres roles, activar/desactivar MFA, acceso protegido, alta/consulta/cambio de email/desactivación/reactivación de cuentas y rechazo de `ADMIN`/`USER` para gestión de usuarios. El suite general también prueba cambio de contraseña y revocación de sesiones.

**Límites:** Mailpit demuestra aceptación y lectura de mensajes en SMTP local; no acredita entrega con un proveedor externo. No se configuraron dominio, TLS, proxy, base de datos ni secretos de producción. La revisión del modelo de catálogo con el negocio y la aceptación del sprint siguen pendientes. Los directorios `target/`, `dist/` y `node_modules/` son resultados locales ignorados por Git.

