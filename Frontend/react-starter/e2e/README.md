# Validación MFA en navegador

Este recorrido usa React/Vite, Spring Boot por HTTP, MySQL 8.4 y Mailpit SMTP reales. Crea tres cuentas efímeras (USER, ADMIN y SUPER_ADMIN) en una base aislada. No usa las cuentas ni la configuración local de desarrollo.

Requisitos: Java 21, Docker activo, Node y dependencias de frontend (`npm ci`). Instalar Chromium con `npx playwright install chromium`, o seleccionar un navegador instalado con `MFA_BROWSER_CHANNEL=msedge` / `chrome`.

Desde `Backend/spring-starter`:

```powershell
# Opcional: usar Edge instalado en Windows.
$env:MFA_BROWSER_CHANNEL = 'msedge'
.\mvnw.cmd '-Dtest=MfaBrowserIT' test
```

La prueba falla si falta Docker o navegador; no se omite silenciosamente. `mvnw verify` ejecuta la suite habitual; el recorrido se invoca por separado porque necesita navegador y dependencias Node. Vite utiliza un puerto libre y preserva Host en el proxy para mantener el mismo origen. No modifica el servidor de desarrollo existente. MySQL y Mailpit se eliminan al finalizar; Node cierra navegador y Vite en `finally`.

Por cada rol: login sin MFA → activar con contraseña y código recibido por SMTP → login con código → logout → login con código → desactivar → login sin código. Comprueba cookie HttpOnly y Path, ausencia de access token antes del código, rechazo de `/me`, almacenamiento web vacío, notificaciones de ambos cambios y estado persistido final (MFA desactivado, versión 2).

Solo el contexto de esta prueba desactiva TLS/autenticación SMTP para Mailpit local. La configuración productiva sigue exigiendo STARTTLS. No se acredita entrega de un proveedor externo ni TLS productivo con este recorrido. Los errores de SMTP, reenvío, códigos inválidos, respuesta perdida y carreras se complementan con las suites backend y React.

## Gestión administrativa T11

Desde `Backend/spring-starter`, con Java 21, Docker, dependencias Node y Edge/Chromium disponibles:

```powershell
$env:ADMIN_BROWSER_CHANNEL = 'msedge' # omitir para Chromium de Playwright
.\mvnw.cmd '-Dtest=AdminUserBrowserIT' test
```

`AdminUserBrowserIT` inicia una base MySQL 8.4 vacía, aplica Flyway, inicia Mailpit y Spring Boot por HTTP y ejecuta `admin-user-lifecycle.mjs` en Vite/Playwright. Comprueba los tres roles, alta, listado, detalle, activación y desactivación MFA del titular por correo, conflicto de cambio de email con MFA, baja y reactivación con credenciales anteriores inválidas, cambio de email permitido y respuesta perdida después de commit con una sola mutación. El test Java comprueba el estado final persistido. Es optativo, falla si no hay Docker o navegador y cierra sus recursos al terminar.
