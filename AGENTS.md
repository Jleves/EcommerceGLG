# Guía de trabajo para GLG Corralón

## Proyecto

GLG Corralón es el ecommerce de GLG Construcciones. Sirve a compradores y al personal que administra el catálogo. En la V1, la solicitud de presupuesto se inicia por WhatsApp; no hay pagos online. El proyecto está en desarrollo y validado localmente por partes. Antes de describir una función como disponible, comprobá su estado en el código.

## Mapa del repositorio

- `Backend/spring-starter`: API Spring Boot con Java 21, MySQL y migraciones Flyway.
- `Frontend/react-starter`: aplicación React/Vite y pruebas de interfaz.
- `docs`: decisiones de producto, planes y resultados de implementación.
- `deploy`: plantillas y procedimientos de preparación; su existencia no demuestra que se haya realizado un despliegue.

## Fuentes de verdad y forma de trabajar

- Usá el código para conocer el comportamiento actual; [el modelo inicial](docs/modelo-inicial.md) para las decisiones de diseño; y el plan indicado en la tarea para su alcance. Distinguí siempre lo implementado de lo propuesto. Si las fuentes se contradicen, señalalo.
- Revisá los archivos y pruebas relacionados con la tarea, respetá las convenciones existentes y evitá cambios ajenos al alcance pedido.
- En una tarea normal, si aparece una decisión funcional o técnica relevante no prevista, proponé una opción razonada, avanzá dentro del alcance y dejá explícita la decisión en el resultado.
- Usá [el prompt de planificación](PROMPT_GENERAR_PLAN_IMPLEMENTACION.md) solo cuando se pida generar un plan por tandas. Usá [el prompt de ejecución](PROMPT_EJECUTAR_TANDA.md) solo cuando se pida ejecutar una tanda concreta. En ese caso, seguí su regla más estricta: detené la parte afectada ante una decisión relevante no prevista y marcala como `DECISIÓN PENDIENTE`; no comiences la tanda siguiente.

## Verificación y cuidados

- Elegí pruebas proporcionales al cambio; ampliá la verificación cuando afecte seguridad, persistencia, contratos o varios flujos. Desde `Backend/spring-starter`, usá `./mvnw.cmd test` y, cuando corresponda, `./mvnw.cmd package` (en PowerShell también funciona `.\mvnw.cmd`). Requiere Java 21.
- Desde `Frontend/react-starter`, usá `npm test`, `npm run lint` y `npm run build` según lo modificado. Instalá dependencias con `npm ci` cuando haga falta.
- Algunas pruebas de integración necesitan Docker y MySQL; los recorridos de navegador necesitan además un navegador y otras dependencias. Consultá [la guía e2e](Frontend/react-starter/e2e/README.md). Una prueba local no valida por sí sola el entorno de destino.
- No incorpores contraseñas, claves ni credenciales reales al repositorio. Revisá con especial cuidado autenticación, MFA, permisos, migraciones Flyway y contratos entre la API y el frontend.

## Al finalizar

Resumí qué cambió, qué comprobaste, qué quedó sin comprobar y los riesgos concretos que permanecen. Si una verificación falla, informá el resultado y la causa conocida sin presentar la tarea como plenamente validada.
