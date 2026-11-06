# Resultado — Tanda 1: persistencia y API privada de ofertas

**Estado: COMPLETA** — 06/11/2026. Se implementó solo la tanda 1. No se inició la lectura pública, el panel ni el carrito de tandas posteriores. No se creó ningún commit.

## Alcance implementado

- Flyway V13 crea `variant_offers`: una oferta por variante, precio final positivo, FK, unicidad y fechas técnicas. No hay fechas de vigencia ni desactivación de ofertas. `precioReferencia` permanece en `product_variants`.
- `GET /api/catalog/offers` lista todas las ofertas guardadas con paginación, incluso las asociadas a productos o variantes inactivos. `POST` crea, `PUT /{id}` modifica el precio final y `DELETE /{id}` borra físicamente la fila y responde `204`.
- El alta exige producto y variante activos, pero permite `disponible=false`. Una oferta existente puede editarse o terminarse con el producto o variante inactivos. Los tres roles `SUPER_ADMIN`, `ADMIN` y `USER` están autorizados; un visitante no puede leer ni mutar la API privada. Las mutaciones requieren CSRF.
- El servicio toma el bloqueo del producto antes de guardar una oferta. Las dos rutas existentes de edición del precio de referencia consultan la oferta dentro de ese bloqueo: conservan el precio final fijo si el nuevo precio base lo supera y rechazan con `409` el cambio que lo igualaría o superaría. La API también responde `409` si el precio final no es inferior al base; importes nulos, no positivos o con precisión inválida responden `400`.
- La respuesta administrativa contiene producto, variante, precios, porcentaje entero hacia abajo, disponibilidad y estados para el futuro panel. El formato de error sigue `ApiError`.

## Diferencias respecto del plan

- Para evitar leer una entidad potencialmente vieja antes de obtener el bloqueo, la mutación consulta primero solo el ID del producto, bloquea ese producto y luego carga la oferta o variante. Esto respeta el orden de bloqueo previsto.
- En un alta concurrente, MySQL puede detectar la duplicación mediante `uk_variant_offers_variant` y devolver `DataIntegrityViolationException` antes de que el servicio vea la otra fila. `GlobalExceptionHandler` la traduce a `409 RESOURCE_CONFLICT`; se comprobó el estado final de una sola fila.
- No se realizaron cambios de las tandas 2–5.

## Tests ejecutados

| Grupo | Ejecución y evidencia | Resultado |
| --- | --- | --- |
| Migración H2/MySQL | `OfferMigrationH2Test` y `OfferMigrationMySqlTest` prueban V13, precio base intacto, unicidad y CHECK positivo | 2 PASS |
| HTTP y servicio | `OfferControllerTest` prueba roles, creación, edición, listado ordenado/paginado, baja física, ofertas inactivas, validaciones y cambios del precio base | 6 PASS |
| Seguridad | Casos HTTP de visitante `401`, rol no autorizado y CSRF ausente `403`; los tres roles permitidos operan | PASS dentro de los 6 |
| Concurrencia MySQL | `OfferConcurrencyMySqlTest`: dos altas sobre una variante y alta frente a edición invalidante del precio base | 2 PASS |
| Regresión enfocada | `ProductServiceTest` (10), `ProductControllerTest` (10), `ProductVariantFlowTest` (6), `PublicProductHttpTest` (7) | 33 PASS |
| Suite completa y empaquetado | `./mvnw.cmd package` con JDK 21 y agente Mockito al arranque, fuera del sandbox para Docker/loopback; reportes Surefire de esa ejecución | 644 tests, 0 fallos, 0 errores, 0 omitidos; exit 0 y JAR generado |

El primer `./mvnw.cmd test` dentro del sandbox falló por restricciones de entorno, detalladas en INC-103. La ejecución integral posterior de `package` incluyó la fase de test y pasó. Se utilizó `-DargLine=-javaagent:.../mockito-core-5.23.0.jar` solo en el comando, sin cambiar `pom.xml`, para evitar el intento de adjuntar el agente dinámicamente. `git diff --check` terminó sin errores de espacios; las advertencias de conversión LF/CRLF son de Git en Windows.

Después de `package` se amplió únicamente un caso de `OfferControllerTest` para comprobar edición y baja con producto y variante inactivos. La clase se volvió a ejecutar: 6/6 PASS. El código productivo no cambió después del empaquetado.

## Incidentes

### INC-101 — Preparación incorrecta de dos tests HTTP nuevos

**Contexto:** primera ejecución de `OfferControllerTest`.

**Test/comportamiento afectado:** edición de una oferta tras desactivar y comprobación de ausencia de escrituras anónimas.

**Esperado:** poder editar una oferta oculta y observar cero ofertas creadas por peticiones no autorizadas.

**Obtenido:** el test intentó desactivar la única variante activa (regla existente que lo prohíbe); otro test había dejado una fila en el contexto compartido.

**Clasificación:** defecto del test.

**Causa:** preparación incompatible con el flujo de variantes y falta de aislamiento entre métodos.

**Resolución:** usar desactivación del producto para el flujo de edición y limpiar ofertas antes de cada método; probar además el rechazo del alta de una variante inactiva preparada como dato de prueba.

**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS (6/6). **Riesgo o aprendizaje derivado:** la última variante activa no se puede desactivar mediante la API existente.

### INC-102 — Excepción real en doble alta concurrente

**Contexto:** primera ejecución MySQL de `OfferConcurrencyMySqlTest`.

**Test/comportamiento afectado:** dos altas simultáneas para una variante.

**Esperado:** una oferta persistida y segunda operación rechazada.

**Obtenido:** una oferta persistida; la segunda recibió `DataIntegrityViolationException` por la clave única, mientras el test esperaba exclusivamente `OfferConflictException`.

**Clasificación:** defecto de expectativa del test; el rechazo y el estado persistido eran correctos.

**Causa:** bajo la concurrencia y el aislamiento de MySQL, la restricción única puede actuar antes de la comprobación de duplicado del servicio.

**Resolución:** comprobar la excepción de integridad y una sola fila para esa variante. El manejador HTTP existente traduce esa excepción a `409`.

**Código productivo modificado:** no. **Tests modificados:** sí. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS (2/2 en MySQL). **Riesgo o aprendizaje derivado:** el texto del `409` concurrente puede ser genérico, aunque el contrato HTTP y la unicidad se mantienen.

### INC-103 — Restricciones del sandbox durante la verificación

**Contexto:** pruebas iniciales dentro del sandbox.

**Test/comportamiento afectado:** Mockito, Testcontainers, pruebas SMTP y una prueba HTTP con Tomcat.

**Esperado:** ejecutar la suite completa y MySQL.

**Obtenido:** el agente dinámico de Mockito no pudo abrir su pipe; Docker devolvió permiso denegado; cinco casos SMTP no pudieron conectar y Tomcat no pudo establecer loopback.

**Clasificación:** configuración/infraestructura/entorno.

**Causa:** restricciones de acceso a pipes y sockets dentro del sandbox, confirmadas por los errores y por `docker info`.

**Resolución:** iniciar Mockito con `-javaagent` en el comando y ejecutar las pruebas con acceso a Docker y loopback fuera del sandbox. No se alteraron tests ajenos ni configuración del repositorio.

**Código productivo modificado:** no. **Tests modificados:** no. **Configuración/migraciones modificadas:** no.

**Resultado posterior:** PASS: `package` completo, 644 tests sin fallos ni omisiones. **Riesgo o aprendizaje derivado:** la verificación integral requiere ese acceso de entorno.

## Tests faltantes y riesgos residuales

- No faltan las categorías de pruebas previstas para la tanda 1: H2, MySQL, HTTP, seguridad, concurrencia y regresión se ejecutaron. La suite completa y el JAR también terminaron correctamente.
- La lectura pública y la representación de precios no fueron probadas porque pertenecen a las tandas 2 y 4. Riesgo: una API privada correcta no garantiza que el catálogo muestre la oferta; tratar en esas tandas.
- No se validó un entorno de despliegue. Riesgo: diferencias de configuración o datos; validar cuando se prepare ese entorno, sin atribuirle la prueba local.
- El porcentaje administrativo es entero hacia abajo; un descuento real menor al 1 % se devuelve como `0`, y el frontend futuro deberá mostrar `<1 %` según el plan.

## Archivos modificados

**Nuevos:** migración V13; entidad, repositorio, controller, servicio, DTO y excepción bajo `catalog/product/offer`; `OfferControllerTest`, `OfferMigrationH2Test`, `OfferMigrationMySqlTest` y `OfferConcurrencyMySqlTest`; este resultado.

**Modificados:** `ProductServiceImpl`, `ProductVariantService`, `ProductVariantRepository`, `SecurityConfig`, `GlobalExceptionHandler`, fixture de `ProductServiceTest` y el apartado de resultado de la tanda 1 en el plan vivo.

## Criterio de cierre

- [x] Migración y unicidad comprobadas en H2 y MySQL.
- [x] API privada, roles, CSRF, errores y baja física comprobados.
- [x] Edición de precios y concurrencia verificadas.
- [x] Regresión enfocada y suite completa ejecutadas; JAR generado.
- [x] Incidentes, límites y riesgos registrados.
- [x] Tandas posteriores sin iniciar; ningún commit creado.

**Revisión humana antes de la tanda 2:** confirmar el contrato administrativo de la respuesta y el carácter físico de la baja; revisar que el porcentaje entero y el mensaje genérico del conflicto concurrente sean adecuados para el futuro panel.
