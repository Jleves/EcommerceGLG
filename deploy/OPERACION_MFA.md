# Operación y despliegue de MFA por correo

Este procedimiento corresponde a las tandas 1–9 de [PLAN_DOBLE_FACTOR_EMAIL.md](../PLAN_DOBLE_FACTOR_EMAIL.md). El ensayo local usa cuentas descartables, MySQL y SMTP de pruebas. Publicar en producción, respaldar/restaurar datos reales y cambiar secretos del entorno son operaciones posteriores del responsable del despliegue.

## Configuración de la entrega

Usar [backend.env.example](backend.env.example) como inventario para el gestor de entorno; **Spring Boot no importa archivos `.env` automáticamente**. Sus marcadores no son credenciales utilizables. No activar el perfil `test` en producción: contiene claves conocidas, H2 y cookies sin Secure. Compilar con Java 21 y Node 22; conservar juntos el SHA del código, JAR, `dist`, lock de npm y versiones de secretos (nunca sus valores).

| Grupo | Variables y valores iniciales | Regla operativa |
|---|---|---|
| Base | `URL_DB`, `USERNAME_DB`, `PASSWORD_DB`, `DRIVER_DB=com.mysql.cj.jdbc.Driver` | MySQL compartido por todas las instancias; Flyway valida/aplica hasta V4, incluida la fila de coordinación administrativa. Configurar transporte y permisos de DB conforme al entorno. |
| JWT | `JWT_SECRET`, `JWT_ACCESS_EXPIRATION=15m`, `JWT_REFRESH_EXPIRATION=3h` | Clave Base64 de al menos 32 bytes aleatorios. Cada access requiere usuario habilitado y su sesión `sid` vigente; revocar la sesión impide nuevas autorizaciones aunque el JWT no haya vencido. |
| HMAC | `MFA_HMAC_SECRET` | Otra clave Base64 de al menos 32 bytes aleatorios, independiente de JWT, igual en todas las instancias. Rotar coordinadamente. |
| Cookies y recuperación | `COOKIE_SECURE=true`, `PASSWORD_RESET_TOKEN_TTL=30m` | Secure en HTTPS; recuperación de contraseña conserva MFA. |
| Desafío | `MFA_CHALLENGE_TTL=5m`, `MFA_MAX_FAILED_ATTEMPTS=5` | Vencimiento absoluto y cinco códigos incorrectos por desafío. |
| Reenvío | `MFA_RESEND_COOLDOWN=60s`, `MFA_MAX_RESENDS=3` | Cambia generación/código; no extiende vencimiento ni reinicia intentos. |
| Cuenta | `MFA_AGGREGATE_WINDOW=15m`, `MFA_MAX_CODE_ISSUES_PER_WINDOW=5`, `MFA_MAX_CODE_FAILURES_PER_WINDOW=10` | Contadores persistidos compartidos entre propósitos y desafíos; los fallos SMTP consumen emisiones. |
| Login | `MFA_LOGIN_WINDOW=15m`, `MFA_MAX_LOGIN_ATTEMPTS_PER_ACCOUNT=10`, `MFA_MAX_LOGIN_ATTEMPTS_PER_IP=30` | Límites combinados por email normalizado e IP. |
| Proxy | `MFA_TRUSTED_PROXY_ADDRESSES` vacío por defecto | Solo IP literales confiables; el proxy debe limpiar/controlar X-Forwarded-For. No confiar en encabezados de clientes directos. |
| SMTP | `EMAIL_HOST`, `EMAIL_PORT=587`, `EMAIL_USERNAME`, `EMAIL_PASSWORD` | Autenticación y STARTTLS obligatorio; username se usa también como remitente. Requiere proveedor compatible y remitente autorizado. |
| Enlaces | `EMAIL_VERIFICATION_BASE_URL`, `FRONTEND_BASE_URL` | URLs públicas correctas del entorno; no configuran CORS ni cambian las rutas relativas de React. |
| Bootstrap | `INIT_SUPERADMIN_ENABLED=false`, email/password vacíos | No habilitar recurrentemente para operar MFA; no existe bypass de MFA por rol. |

Las duraciones MFA deben ser positivas y sus límites enteros al menos 1. Los fallos de contraseña actual comparten un presupuesto fijo de cinco en 15 minutos con cambio de contraseña; no hay variable MFA adicional para ese contador. Distribuir configuraciones idénticas a todas las instancias.

Generar las claves mediante un CSPRNG y entregarlas al gestor de secretos por un canal que no registre su valor. Por ejemplo, asignar `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))` directamente al mecanismo de carga del secreto en PowerShell moderno; generar cada clave por separado. No reutilizar claves de `application-test.properties` ni imprimirlas en logs de CI.

Los timeouts JavaMail de conexión, lectura y escritura son **10 segundos cada uno**, fijados en `MailConfig`, no un timeout total ni variables de esta plantilla. La entrega es síncrona fuera de la transacción DB. Configurar el timeout del proxy para permitir el conjunto de esas operaciones y medirlo en el entorno. No habilitar reintentos automáticos de POST en proxy/cliente: una respuesta perdida puede seguir a un commit exitoso. SMTP SENT significa aceptación, no entrega a bandeja.

## HTTP, cookies y limpieza

Para esta versión servir React y `/api` en un mismo origen HTTPS. El proxy debe conservar el Host público (y el puerto público si corresponde) y presentar correctamente el esquema HTTPS a Spring mediante una configuración de forwarded headers controlada por infraestructura. Bloquear acceso directo al backend y sobrescribir encabezados reenviados. Conservar `Set-Cookie`, `X-XSRF-TOKEN`, `Retry-After` y `X-Request-Id`; no cachear respuestas de autenticación. Verificar que Spring vea el origen público y no rechace los POST como cross-origin.

React usa rutas relativas `/api/...` y lee `XSRF-TOKEN` con `document.cookie`. El CORS actual autoriza exclusivamente `http://localhost:5173`, credenciales, `Content-Type` y `X-XSRF-TOKEN`; expone `Retry-After` y `X-Request-Id`. No existe variable `CORS_ALLOWED_ORIGINS`. Separar frontend/API en dominios distintos requiere un cambio específico en cliente, CSRF y CORS; esta guía no lo da por soportado. No agregar `*` ni quitar CSRF para resolver un 403.

| Cookie | Path | HttpOnly | Vigencia |
|---|---|---|---|
| ACCESS_TOKEN | `/` | Sí | Access JWT, 15 min por defecto |
| REFRESH_TOKEN | `/api/auth` | Sí | Refresh, 3 h por defecto |
| MFA_CHALLENGE | `/api/auth/mfa` | Sí | Tiempo restante del desafío, máximo 5 min por defecto |
| XSRF-TOKEN | `/` | No, React necesita leerla | Cookie CSRF de sesión |

Todas son host-only, SameSite=Lax y Secure según `COOKIE_SECURE`. En desarrollo HTTP local se permite `false`; en HTTPS de destino debe ser `true`. No reescribir Domain ni Path en el proxy. El logout de React intenta cancelar primero el desafío: `/logout` no recibe su cookie por el Path. Una copia de desafío LOGIN cuya cancelación falló sigue vigente hasta consumo, reemplazo o vencimiento.

`ChallengeCleanupService.removeExpiredRecords` corre una hora después del arranque y luego con demora fija de una hora. Elimina desafíos cuya expiración pasó hace más de un día y contadores cuyo inicio de ventana es anterior a la mayor ventana configurada más un día. No elimina ventanas activas. No hay variable de retención/cron; no truncar tablas para resolver 429. La autorización verifica vencimiento aunque aún exista la fila. Mantener relojes sincronizados y revisar crecimiento de tablas/errores del scheduler; la limpieza se ejecuta en cada instancia, sin coordinador dedicado.

## Preparación y publicación coordinada

1. Identificar entorno, responsable, ventana de mantenimiento y versión de retorno **compatible con MFA**. Registrar SHA de backend/frontend y referencias de secretos. Si aún no existe una versión de retorno compatible, el plan de recuperación es mantener mantenimiento y reparar hacia adelante.
2. Respaldar MySQL según el procedimiento del entorno y verificar una restauración en una base aislada antes de la ventana. Incluir esquema, `flyway_schema_history`, usuarios, sesiones, desafíos y límites. Para tablas InnoDB, un ejemplo con credenciales ya configuradas es `mysqldump --login-path=starter-backup --single-transaction --routines --triggers --events --result-file=RUTA_SEGURA/antes-mfa.sql NOMBRE_DB`. Sustituir ruta/base; no ejecutar DDL concurrente. Restringir y cifrar el respaldo, definir retención y registrar ubicación, fecha y prueba de restauración. No incluirlo en Git. El nombre del login-path y el mecanismo concreto de respaldo dependen de infraestructura.
3. Preparar secretos HMAC/JWT independientes, SMTP autenticado con STARTTLS y remitente autorizado, URLs, cookies y proxy. Comprobar DNS, conectividad, certificados y aceptación/recepción con cuentas de prueba en el destino. No enviar códigos a cuentas reales como ensayo automático.
4. Construir y verificar una pareja de artefactos del mismo SHA: backend `mvnw.cmd verify` con Docker activo; frontend `npm.cmd ci`, `npm.cmd test`, `npm.cmd run lint`, `npm.cmd run build`. Revisar XML de Surefire: MySQL no puede aparecer omitido. Ejecutar también el navegador indicado abajo.
5. Poner las rutas de la aplicación en mantenimiento en el ingreso y evitar que haya acceso directo a instancias. No existe un feature flag de activación MFA. Para probar antes de abrir a usuarios, permitir únicamente el acceso del equipo de ensayo en la infraestructura. Drenar solicitudes y trabajos de escritura existentes antes del cambio.
6. Aplicar V3 mediante Flyway, sin editar migraciones aplicadas ni ejecutar su SQL manualmente por segunda vez. Puede desplegarse primero la migración aditiva: cuentas previas quedan desactivadas con versión cero. Una forma con los artefactos actuales es arrancar el nuevo backend aislado del tráfico; el arranque aplica Flyway y valida JPA. No ejecutar `flyway clean`, bajar esquema ni modificar checksums para ocultar un error.
7. Comprobar `flyway_schema_history` hasta V3 con éxito y las columnas/tablas esperadas. Publicar backend y `dist` compatibles detrás del ingreso cerrado; todas las instancias deben usar la misma clave HMAC. No hacer rolling rollback hacia un backend que ignore MFA. Servir el nuevo HTML sin caché obsoleta y conservar assets con hash durante la transición de pestañas abiertas.
8. Con ingreso restringido, ensayar los tres roles: login sin MFA → activar con contraseña/código → nuevo login con código → logout → login con código → desactivar → login sin código. Verificar notificaciones, `/me` rechazado en pendiente, cookies/CSRF, reenvío bajo espera y refresh revocado tras configurar. En destino usar HTTPS y comprobar realmente `Secure`; el ensayo local no lo acredita.
9. Abrir tráfico únicamente tras ese resultado y observar 400/401/403/429/503, latencia SMTP, errores de notificación y crecimiento de tablas. Registrar hora, responsables, versión, resultado y limitaciones. No activar MFA masivamente mediante SQL: cada usuario confirma acceso a su correo.

El respaldo es protección ante desastre, no el mecanismo normal para volver una versión de aplicación atrás: restaurar una copia anterior a activaciones podría borrar MFA y permitir un acceso debilitado. Conservar las activaciones y cambios de contraseña realizados desde el respaldo.

## Corte coordinado de credenciales de gestión administrativa (D01/D02)

El backend nuevo emite JWT con `sub` igual al ID inmutable del usuario y valida en cada solicitud autenticada que `sid` pertenezca a ese usuario y corresponda a una sesión no revocada ni vencida. Los JWT anteriores cuyo `sub` era el correo no tienen fallback y se rechazan. El corte exige que backend y frontend compatibles estén listos antes de reabrir tráfico. No ejecutar estos pasos automáticamente al arrancar una instancia.

### Ensayo obligatorio en base descartable

1. Crear un entorno aislado desde una copia restaurada/sanitizada con el esquema actual (Flyway V4), incluidos usuarios con y sin MFA, sesiones, desafíos pendientes y límites. Registrar SHA de backend/frontend y versión de MySQL; usar claves y SMTP de prueba. Nunca apuntar los pasos siguientes a producción.
2. Antes de la ventana simulada, iniciar la versión nueva con ingreso restringido. Con cuentas descartables registrar: un access JWT antiguo por email, uno nuevo por ID, refresh vigente, desafío LOGIN pendiente y desafío ENABLE/DISABLE pendiente. Confirmar que el usuario MFA y `security_version`, contraseña y contadores tienen valores conocidos. El token previo se prepara de forma controlada en fixtures de ensayo; no guardar secretos en la evidencia.
3. Cerrar ingreso, drenar solicitudes y detener o aislar **todas** las instancias antiguas y cualquier proceso que emita/revoque sesiones o desafíos. Confirmar cero escrituras en curso. El paso evita que una instancia antigua vuelva a emitir credenciales tras el corte.
4. En una única conexión administrativa a la base descartable, ejecutar la transacción de corte una sola vez:

```sql
START TRANSACTION;
UPDATE auth_sessions
SET revoked_at = UTC_TIMESTAMP(6), version = version + 1,
    updated_at = UTC_TIMESTAMP(6)
WHERE revoked_at IS NULL;

UPDATE auth_challenges
SET invalidated_at = UTC_TIMESTAMP(6), version = version + 1
WHERE consumed_at IS NULL AND invalidated_at IS NULL;
COMMIT;
```

   No borrar filas, usuarios, MFA, `security_version`, contraseñas ni `auth_rate_limits`. Registrar cantidades afectadas que reporte el cliente SQL. Si la transacción falla antes del commit, detenerse, comprobar que no quedó una transacción abierta y volver a evaluar desde el estado confirmado; no seguir con publicación parcial.
5. Verificar antes de abrir tráfico:

```sql
SELECT COUNT(*) AS sesiones_no_revocadas
FROM auth_sessions WHERE revoked_at IS NULL;
SELECT COUNT(*) AS desafios_no_invalidos
FROM auth_challenges
WHERE consumed_at IS NULL AND invalidated_at IS NULL;
SELECT COUNT(*) AS usuarios_mfa
FROM users WHERE email_mfa_enabled = TRUE;
```

   Las dos primeras cifras deben ser cero en la copia de ensayo tras el corte; la tercera y los valores de `security_version`/contadores deben conservarse. Los resultados previos/posteriores se comparan sin exportar emails, hashes, cookies, tokens ni códigos.
6. Publicar únicamente el backend/frontend compatibles, con el mismo `JWT_SECRET` entre instancias y tráfico aún restringido. Comprobar readiness y versión de esquema. El access antiguo debe recibir 401; refresh de una sesión revocada debe rechazarse; los desafíos anteriores no deben completar ni activar MFA. Un login nuevo por email/contraseña debe funcionar; si la cuenta tiene MFA, solo debe autenticarse tras un desafío nuevo. Confirmar que MFA y contadores no se reiniciaron. Ejecutar el smoke administrativo con SUPER_ADMIN y comprobar que USER/ADMIN reciben rechazo.
7. Repetir el paso de corte sobre el estado ya cortado únicamente como comprobación de repetición: debe dejar las cantidades en cero y no modificar usuarios, MFA ni límites. Esta repetición revoca también sesiones/challenges creados desde el primer corte, por lo que se hace antes del smoke o con cuentas nuevas aisladas; no se presenta como operación inocua sobre una sesión recién creada.
8. Registrar comandos, resultados, tiempos, SHA, versión de DB, cantidades, responsable y cualquier incidente. El ensayo se aprueba solo si el estado de datos y todas las comprobaciones HTTP coinciden; retirar la copia descartable al terminar según el procedimiento de datos de prueba.

### Publicación y retorno compatible

El corte es deliberadamente incompatible con el backend anterior. No reabrir tráfico ni hacer rollback hacia una versión que resuelve JWT por email o no valida `sid`: podría admitir credenciales previas y romper la garantía de identidad/revocación. Antes de una ventana real, la persona responsable debe identificar y verificar por SHA una pareja de retorno que incluya **ambas** capacidades (JWT por ID y validación de sesión), valide el esquema vigente y tenga un procedimiento de despliegue probado. Si no existe esa pareja, retorno de aplicación significa mantener mantenimiento y reparar hacia adelante, nunca volver al artefacto anterior. Restaurar backup tampoco es rollback ordinario: puede rehabilitar sesiones/desafíos; requiere reconciliación explícita antes de abrir. Las migraciones aplicadas no se revierten ni editan.

Producción permanece bloqueada hasta que se documenten responsables nominales, ventana, backup y restauración verificados, pareja de retorno compatible, resultado completo del ensayo aislado, smoke y autorización operativa del entorno. La documentación no constituye esa autorización ni prueba que producción haya sido modificada.

## Diagnóstico sin secretos

Correlacionar `X-Request-Id`, hora UTC, ruta, estado HTTP e ID interno del desafío/usuario cuando figure en logs. La entrega registra ID/generación/resultado; las notificaciones, ID de usuario. No recopilar body, contraseña, código, cookie, Authorization, secreto HMAC, URL de recuperación ni cuerpos SMTP. No habilitar debug JavaMail, logs de argumentos ni captura de payload en proxy/APM. Proteger acceso y retención de registros y respaldos.

| Síntoma | Comprobación y recuperación |
|---|---|
| 503 `MFA_DELIVERY_UNAVAILABLE` | Revisar conectividad, STARTTLS/certificado, autenticación, remitente, cuota y estado del proveedor sin publicar su respuesta sensible. Sin cookie nueva, reiniciar con contraseña. Con desafío acreditado, reenvío manual sujeto a límites. |
| PENDING persistente | Posible interrupción entre persistencia, SMTP y marca final. No cambiar a SENT manualmente. Reenvío genera otro código; el desafío vencido obliga a reiniciar. |
| SENT pero correo ausente | Consultar trazabilidad del proveedor, destinatario registrado y spam; aceptación no garantiza bandeja. No recuperar el código desde DB (solo contiene HMAC). |
| 429 `MFA_RATE_LIMITED` | Respetar Retry-After. Revisar presupuesto de cuenta/IP, fallos de contraseña y proxy confiable. Reiniciar pantalla, proceso o desafío no reinicia la ventana. |
| 400 `MFA_CODE_INVALID` | Código string de seis dígitos, última generación, sin recortar ceros; no reintentar automáticamente. |
| 400 `MFA_CHALLENGE_INVALID` | Vencimiento, consumo, cookie ausente/incorrecta, propósito, sesión original o versión cambiada. Reiniciar con contraseña; comprobar coherencia de HMAC entre instancias. |
| 409 `MFA_STATE_CONFLICT` | Consultar estado confirmado; no simular un cambio local ni repetir confirmación automáticamente. |
| 401 en configuración | Satisface la regla de sesión original activa; nuevo login si ya no puede renovarse. Revocar refresh impide usar ese sid aunque quede un access JWT vigente. |
| 403 CSRF/CORS | Obtener CSRF, revisar cookie/header, Secure/HTTPS y origen que ve el backend. No desactivar controles. |
| Respuesta de confirmación perdida | Login: consultar `/me`; configuración: consultar estado si queda sesión. Si no se puede determinar, solicitar login nuevo sin afirmar éxito ni reenviar POST. |
| Fallo de notificación posterior | El cambio MFA ya está confirmado; no revertirlo. Registrar incidencia. No hay cola durable ni reintentos automáticos; confirmar estado mediante API. |

Quien pierda acceso al correo debe recuperarlo con su proveedor. No hay códigos de respaldo ni endpoint administrativo para desactivar MFA ajeno. Cambiar/resetear contraseña no desactiva el factor.

## Rotación HMAC

1. Cerrar ingreso a todas las instancias y detener/drenar las operaciones de autenticación y escrituras relacionadas. Esperar a que terminen los envíos y sus transacciones finales; si se detienen procesos, mantenerlos fuera de servicio. No basta esperar diez segundos ni pausar solo `/mfa`: login y contraseña también producen/invalidan desafíos.
2. Con todas las instancias detenidas o sin operaciones en curso, ejecutar sobre la base correcta, mediante una conexión administrativa autorizada:

```sql
START TRANSACTION;
UPDATE auth_challenges
SET invalidated_at = UTC_TIMESTAMP(6), version = version + 1
WHERE consumed_at IS NULL AND invalidated_at IS NULL;
COMMIT;

SELECT COUNT(*) AS pendientes_no_invalidados
FROM auth_challenges
WHERE consumed_at IS NULL AND invalidated_at IS NULL;
```

3. El conteo debe ser cero antes de reabrir. Distribuir la nueva clave a todas las instancias y reiniciar; comprobar referencias/versiones del secreto sin imprimirlo. No cambiar JWT, `users.email_mfa_enabled`, `security_version` ni borrar contadores por esta rotación.
4. Ensayar que el desafío anterior se rechaza y uno nuevo permite completar el login con correo. Reabrir solo con todas las instancias consistentes. Si la nueva clave no puede desplegarse, mantener mantenimiento o volver coordinadamente a la clave anterior, conservando las invalidaciones ya realizadas; los códigos anteriores no se rehabilitan.

## Rollback y recuperación

Retirar tráfico y desplegar la última pareja backend/frontend que aplique MFA sobre el esquema V3 actual. Mantener datos, claves coherentes y límites; verificar un login de cuenta activada antes de reabrir. Si esa versión no está disponible, conservar mantenimiento y reparar una versión compatible. No redirigir tráfico hacia un backend antiguo que omita el factor, ni establecer `email_mfa_enabled=false`, ni borrar desafíos/contadores como atajo.

Una caída SMTP no permite saltar el segundo paso. Los usuarios con MFA quedan sin nuevos logins hasta recuperar entrega; las sesiones existentes conservan sus reglas normales. Desde T2 de gestión administrativa (D02), el filtro exige una sesión propia, habilitada y vigente para cada access JWT. Revocar sesiones por cambio MFA/contraseña o logout impide nuevas autorizaciones tras el commit, incluso si el access no venció; no cancela solicitudes ya autorizadas. Una falla al consultar la sesión no permite acceso. Este contrato requiere el backend actualizado; el corte coordinado y su ensayo siguen pendientes de T12, sin despliegue realizado por T2.

Si un desastre exige restaurar datos, mantener ingreso cerrado hasta reconciliar las activaciones y cambios posteriores al punto restaurado mediante el procedimiento de recuperación del entorno. Nunca abrir un respaldo pre-MFA como si reflejara la protección actual de las cuentas.

## Ensayo y evidencia

Desde backend, con Docker, Java 21, dependencias frontend y Edge instalado en Windows:

```powershell
$env:MFA_BROWSER_CHANNEL='msedge'
.\mvnw.cmd '-Dtest=MfaBrowserIT,SmtpMfaEmailSenderTest,MfaMigrationUpgradeMySqlTest' test
```

También puede instalarse Chromium con `npx playwright install chromium` desde frontend y omitir la variable de canal. El [harness](../Frontend/react-starter/e2e/README.md) crea una base y buzón descartables, usa puertos libres y cierra recursos. No modifica cuentas de desarrollo. Comprueba ciclo MFA y notificaciones de activación/desactivación para los tres roles; el test SMTP comprueba aceptación/rechazo/timeout y configuración TLS; el de migración verifica V2 → V3 con datos previos.

El ensayo SMTP local desactiva TLS/auth únicamente en el contexto de prueba y no demuestra conexión TLS al proveedor de destino. El recorrido no ejecuta respaldo/restauración productivos, rotación de secretos real ni publicación. Registrar esos resultados al ejecutar la ventana del entorno, junto con su responsable; no confundir instrucciones preparadas con operaciones realizadas. La evidencia local de esta entrega y los commits existentes se registran en el plan.
