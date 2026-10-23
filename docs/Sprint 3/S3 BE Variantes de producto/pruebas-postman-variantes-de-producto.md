# Pruebas de Postman — S3 BE Variantes de producto

Guía manual para el [plan de implementación](s3-be-variantes-de-producto-plan-implementacion.md), contrastada con los controladores, DTO, servicios y pruebas HTTP del backend actual. Ejecutá los casos en orden sobre una base de prueba. Los `id` son variables obtenidas de respuestas anteriores. Los JSON de respuesta muestran **los campos que hay que comprobar**; puede haber otros campos y, en los errores, `timestamp` y `requestId` cambian en cada solicitud. No hay endpoints para borrar variantes o productos.

## Preparación de Postman

1. Creá un environment con `baseUrl = http://localhost:8080` y seleccioná una base con migraciones Flyway V10 y V11 aplicadas.
2. Hacé `GET {{baseUrl}}/api/auth/csrf`. **HTTP 204**, cuerpo vacío; Postman debe guardar la cookie `XSRF-TOKEN`.
3. Hacé `POST {{baseUrl}}/api/auth/login` con `Content-Type: application/json` y `X-XSRF-TOKEN: <valor de la cookie XSRF-TOKEN>`:

```json
{"email":"tu-usuario@ejemplo.com","password":"tu-contraseña"}
```

   Resultado normal: **HTTP 200**, JSON con `"status":"AUTHENTICATED"` y `user`, más cookies de autenticación. Si devuelve **HTTP 202** con `"status":"MFA_REQUIRED"`, completá el desafío MFA de esa cuenta antes de continuar. Usá un usuario con rol `SUPER_ADMIN`, `ADMIN` o `USER`.
4. Conservá las cookies del mismo host. En **todos** los `POST`, `PUT` y `DELETE`, enviá `X-XSRF-TOKEN` actualizado desde la cookie `XSRF-TOKEN`. Los `GET` no llevan ese header. En Postman elegí **Body → raw → JSON** para cuerpos y **Body → none** para las demás solicitudes.
5. En Postman, reemplazá o guardá los IDs devueltos como `{{categoriaId}}`, `{{colorId}}`, `{{marcaId}}`, `{{productoId}}`, `{{rojoId}}`, `{{blancoId}}`, `{{celesteId}}`, `{{otroProductoId}}` y `{{otraVarianteId}}`. En los cuerpos JSON, los IDs numéricos van **sin comillas**. Usá nombres de categoría y característica únicos si la base ya tiene datos.

Formato de error real, ilustrado con un conflicto. `fieldErrors` se omite cuando está vacío:

```json
{
  "timestamp": "2026-10-23T12:00:00Z",
  "status": 409,
  "code": "RESOURCE_CONFLICT",
  "message": "El SKU ya está en uso.",
  "path": "/api/catalog/products/123/variants",
  "requestId": "valor-generado"
}
```

En cada respuesta negativa, repetí el `GET` administrativo del recurso para verificar que la operación no cambió el estado.

## 1. Datos de prueba y variante inicial — Tandas 1 y 2

### P01 — Categoría y definiciones

**POST** `{{baseUrl}}/api/catalog/categories`

```json
{"nombre":"Pinturas Postman S3","descripcion":"Datos de prueba de variantes","icono":"pintura"}
```

**HTTP 201**. Guardá `id` como `categoriaId`.

```json
{"id":123,"nombre":"Pinturas Postman S3","descripcion":"Datos de prueba de variantes","icono":"pintura","activo":true}
```

**POST** `{{baseUrl}}/api/catalog/characteristics`, una vez con cada cuerpo:

```json
{"nombre":"Color Postman S3"}
```

```json
{"nombre":"Marca Postman S3"}
```

Cada alta devuelve **HTTP 201**. Guardá `colorId` y `marcaId`, respectivamente. Respuesta esperada para Color:

```json
{"id":201,"nombre":"Color Postman S3","cantidadProductos":0,"cantidadVariantes":0}
```

### P02 — Alta heredada: una variante activa sin valor

**POST** `{{baseUrl}}/api/catalog/products`

```json
{
  "nombre":"Pintura Acme 20 L",
  "categoriaId":{{categoriaId}},
  "descripcion":"Línea exterior",
  "precioReferencia":20.00,
  "disponible":true,
  "caracteristicas":[{"caracteristicaId":{{marcaId}},"valor":"Acme"}]
}
```

**HTTP 201**. Guardá `id` como `productoId` y `varianteInicialId` como `rojoId`. La variante inicial todavía no tiene Color:

```json
{
  "id":301,
  "nombre":"Pintura Acme 20 L",
  "categoriaId":123,
  "descripcion":"Línea exterior",
  "precioReferencia":20.00,
  "disponible":true,
  "activo":true,
  "destacado":false,
  "caracteristicas":[{"caracteristicaId":202,"nombre":"Marca Postman S3","valor":"Acme","unidad":null}],
  "subcategoriaId":null,
  "varianteInicialId":401,
  "variantes":[{"id":401,"sku":null,"precioReferencia":20.00,"disponible":true,"activo":true,"caracteristicaId":null,"caracteristicaNombre":null,"valor":null,"unidad":null}]
}
```

### P03 — Alta gradual: segunda variante antes de rotular la primera

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants`

```json
{"sku":"ACME-BLANCO","precioReferencia":17.00,"disponible":false,"caracteristicaId":{{colorId}},"valor":"Blanco"}
```

**HTTP 409**. Ninguna variante nueva debe persistirse:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"Todas las variantes deben tener un valor antes de agregar otra.","path":"/api/catalog/products/301/variants"}
```

### P04 — Rotular la variante inicial, sin cambiar su ID

**PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{rojoId}}`

```json
{"sku":"ACME-ROJO","precioReferencia":20.00,"disponible":true,"caracteristicaId":{{colorId}},"valor":"Rojo","unidad":null}
```

**HTTP 200**. El `id` sigue siendo `rojoId`:

```json
{"id":401,"sku":"ACME-ROJO","precioReferencia":20.00,"disponible":true,"activo":true,"caracteristicaId":201,"caracteristicaNombre":"Color Postman S3","valor":"Rojo","unidad":null}
```

### P05 — Agregar Blanco y Celeste

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants`

```json
{"sku":"ACME-BLANCO","precioReferencia":17.00,"disponible":false,"caracteristicaId":{{colorId}},"valor":"Blanco","unidad":null}
```

**HTTP 201**. Guardá `id` como `blancoId`:

```json
{"id":402,"sku":"ACME-BLANCO","precioReferencia":17.00,"disponible":false,"activo":true,"caracteristicaId":201,"caracteristicaNombre":"Color Postman S3","valor":"Blanco","unidad":null}
```

Repetí el **POST** con:

```json
{"sku":"ACME-CELESTE","precioReferencia":18.00,"disponible":true,"caracteristicaId":{{colorId}},"valor":"Celeste","unidad":null}
```

**HTTP 201**. Guardá `id` como `celesteId`:

```json
{"id":403,"sku":"ACME-CELESTE","precioReferencia":18.00,"disponible":true,"activo":true,"caracteristicaId":201,"caracteristicaNombre":"Color Postman S3","valor":"Celeste","unidad":null}
```

### P06 — Consulta administrativa, incluidas inactivas

**GET** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants` → **HTTP 200**. Respuesta completa, ordenada por ID ascendente:

```json
[
  {"id":401,"sku":"ACME-ROJO","precioReferencia":20.00,"disponible":true,"activo":true,"caracteristicaId":201,"caracteristicaNombre":"Color Postman S3","valor":"Rojo","unidad":null},
  {"id":402,"sku":"ACME-BLANCO","precioReferencia":17.00,"disponible":false,"activo":true,"caracteristicaId":201,"caracteristicaNombre":"Color Postman S3","valor":"Blanco","unidad":null},
  {"id":403,"sku":"ACME-CELESTE","precioReferencia":18.00,"disponible":true,"activo":true,"caracteristicaId":201,"caracteristicaNombre":"Color Postman S3","valor":"Celeste","unidad":null}
]
```

**GET** `{{baseUrl}}/api/catalog/products/{{productoId}}` → **HTTP 200**. Comprobá que `variantes` contiene los tres objetos anteriores, `varianteInicialId` sigue siendo `rojoId`, `caracteristicas` conserva Marca y los campos escalares de compatibilidad se leen de la primera variante:

```json
{"id":301,"precioReferencia":20.00,"disponible":true,"varianteInicialId":401,"variantes":[{"id":401},{"id":402},{"id":403}]}
```

**GET** `{{baseUrl}}/api/catalog/products?page=0&size=100&nombre=Pintura%20Acme%2020%20L` → **HTTP 200**. Buscá `productoId` dentro de `content`; la ficha administrativa resumida también trae `variantes` y `varianteInicialId`:

```json
{"content":[{"id":301,"varianteInicialId":401,"variantes":[{"id":401},{"id":402},{"id":403}]}],"page":0,"size":100}
```

Si el filtro coincide con otros productos, `content` puede tener más filas y los totales cambian.

## 2. Validaciones de identidad, SKU y atributos — Tanda 2

### P07 — Valor repetido, aunque la variante original esté inactiva

Primero **POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{blancoId}}/deactivate`, sin cuerpo. **HTTP 200**:

```json
{"id":402,"valor":"Blanco","activo":false}
```

Después **POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants`:

```json
{"precioReferencia":19.00,"disponible":true,"caracteristicaId":{{colorId}},"valor":" blanco ","unidad":""}
```

**HTTP 409**, porque se normalizan mayúsculas, espacios y unidad vacía:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"Ya existe una variante con ese valor y unidad."}
```

### P08 — Cambiar el atributo variable con varias variantes

**PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{celesteId}}`

```json
{"sku":"ACME-CELESTE","precioReferencia":18.00,"disponible":true,"caracteristicaId":{{marcaId}},"valor":"Grande"}
```

**HTTP 409**:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"No se puede cambiar el atributo variable con varias variantes."}
```

### P09 — Característica variable compartida en el producto

**PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}`

```json
{"nombre":"Pintura Acme 20 L","categoriaId":{{categoriaId}},"caracteristicas":[{"caracteristicaId":{{colorId}},"valor":"Rojo"}]}
```

**HTTP 409**:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"El atributo variable no puede ser compartido por el producto."}
```

Marca continúa como característica compartida tras el rechazo.

### P10 — SKU único en todo el catálogo

**POST** `{{baseUrl}}/api/catalog/products` para otro producto, usando el formato heredado:

```json
{"nombre":"Pintura de prueba SKU","categoriaId":{{categoriaId}},"precioReferencia":30.00,"disponible":true}
```

**HTTP 201**. Guardá `otroProductoId` y `otraVarianteId` desde `varianteInicialId`:

```json
{"id":302,"varianteInicialId":404,"variantes":[{"id":404,"sku":null,"precioReferencia":30.00,"disponible":true,"activo":true}]}
```

**PUT** `{{baseUrl}}/api/catalog/products/{{otroProductoId}}/variants/{{otraVarianteId}}`:

```json
{"sku":" acme-rojo ","precioReferencia":30.00,"disponible":true}
```

**HTTP 409**; el SKU se recorta y compara sin distinguir mayúsculas:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"El SKU ya está en uso."}
```

### P11 — Valor y unidad forman la identidad

Esta prueba usa un producto separado de **una sola variante** para no alterar el atributo Color de la secuencia principal. Primero actualizá `otraVarianteId`:

**PUT** `{{baseUrl}}/api/catalog/products/{{otroProductoId}}/variants/{{otraVarianteId}}`

```json
{"sku":null,"precioReferencia":30.00,"disponible":true,"caracteristicaId":{{colorId}},"valor":"20","unidad":"L"}
```

**HTTP 200**:

```json
{"id":404,"sku":null,"caracteristicaId":201,"valor":"20","unidad":"L"}
```

**POST** `{{baseUrl}}/api/catalog/products/{{otroProductoId}}/variants`:

```json
{"sku":null,"precioReferencia":31.00,"disponible":true,"caracteristicaId":{{colorId}},"valor":"20","unidad":"kg"}
```

**HTTP 201**. El nuevo `id` es distinto de `otraVarianteId`:

```json
{"sku":null,"precioReferencia":31.00,"disponible":true,"activo":true,"caracteristicaId":201,"valor":"20","unidad":"kg"}
```

### P12 — Borrado de definición en uso y contadores

**GET** `{{baseUrl}}/api/catalog/characteristics/{{colorId}}` → **HTTP 200**. Después de P11, Color está usado por cinco variantes, incluida Blanco inactiva:

```json
{"id":201,"nombre":"Color Postman S3","cantidadProductos":0,"cantidadVariantes":5}
```

**DELETE** `{{baseUrl}}/api/catalog/characteristics/{{colorId}}`, sin cuerpo → **HTTP 409**:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"La característica está en uso por variantes."}
```

Repetí el `GET`: sigue devolviendo **HTTP 200** y `cantidadVariantes: 5`.

## 3. Estado y compatibilidad de edición — Tanda 2

### P13 — Desactivar, reactivar y conservar IDs

El estado de Blanco ya es `false` desde P07. **POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{celesteId}}/deactivate`, sin cuerpo → **HTTP 200**:

```json
{"id":403,"valor":"Celeste","activo":false}
```

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{rojoId}}/deactivate`, sin cuerpo → **HTTP 409**:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"No se puede desactivar la última variante activa."}
```

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{blancoId}}/reactivate`, sin cuerpo → **HTTP 200**:

```json
{"id":402,"valor":"Blanco","activo":true}
```

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{celesteId}}/reactivate`, sin cuerpo → **HTTP 200**:

```json
{"id":403,"valor":"Celeste","activo":true}
```

Los tres IDs deben coincidir con los originales.

### P14 — `PUT` heredado bloqueado en producto multivariante

**PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}`

```json
{"nombre":"Pintura modificada","categoriaId":{{categoriaId}},"precioReferencia":25.00,"disponible":true}
```

**HTTP 409**. Alcanza con que esté presente **uno** de los campos heredados `precioReferencia` o `disponible`, incluso si su valor es `null`:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"El precio y la disponibilidad se editan por variante."}
```

### P15 — `PUT` de datos comunes permitido

**PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}`

```json
{"nombre":"Pintura Acme exterior 20 L","categoriaId":{{categoriaId}},"descripcion":"Actualizada en Postman"}
```

**HTTP 200**. No se envían precio ni disponibilidad; si se omite `caracteristicas`, Marca se conserva:

```json
{"id":301,"nombre":"Pintura Acme exterior 20 L","descripcion":"Actualizada en Postman","precioReferencia":20.00,"disponible":true,"caracteristicas":[{"nombre":"Marca Postman S3","valor":"Acme"}],"variantes":[{"id":401},{"id":402},{"id":403}]}
```

### P16 — Edición heredada permitida con una sola variante

Para este caso creá un **tercer** producto de prueba de una variante:

**POST** `{{baseUrl}}/api/catalog/products`

```json
{"nombre":"Producto legado S3","categoriaId":{{categoriaId}},"precioReferencia":8.00,"disponible":true}
```

**HTTP 201**. Guardá su `id` como `legadoId` y su `varianteInicialId` como `legadoVarianteId`:

```json
{"id":303,"precioReferencia":8.00,"disponible":true,"varianteInicialId":406,"variantes":[{"id":406,"precioReferencia":8.00,"disponible":true}]}
```

**PUT** `{{baseUrl}}/api/catalog/products/{{legadoId}}`

```json
{"nombre":"Producto legado S3 editado","categoriaId":{{categoriaId}},"precioReferencia":9.50,"disponible":false}
```

**HTTP 200**. Los escalares y la única variante reflejan el mismo valor:

```json
{"id":303,"precioReferencia":9.50,"disponible":false,"varianteInicialId":406,"variantes":[{"id":406,"precioReferencia":9.50,"disponible":false}]}
```

### P17 — Alta con `varianteInicial` y fuentes incompatibles

**POST** `{{baseUrl}}/api/catalog/products`

```json
{"nombre":"Producto anidado S3","categoriaId":{{categoriaId}},"varianteInicial":{"sku":"S3-ANIDADO","precioReferencia":12.50,"disponible":true,"caracteristicaId":{{colorId}},"valor":"Azul"}}
```

**HTTP 201**. Guardá `id` como `anidadoId` y su variante inicial como `anidadoVarianteId`:

```json
{"id":304,"precioReferencia":12.50,"disponible":true,"varianteInicialId":407,"variantes":[{"id":407,"sku":"S3-ANIDADO","valor":"Azul","activo":true}]}
```

**POST** `{{baseUrl}}/api/catalog/products` con ambas fuentes:

```json
{"nombre":"Producto inválido S3","categoriaId":{{categoriaId}},"precioReferencia":10.00,"disponible":true,"varianteInicial":{"precioReferencia":12.50,"disponible":true}}
```

**HTTP 400**:

```json
{"status":400,"code":"VALIDATION_ERROR","message":"Indique la variante inicial o precio y disponibilidad heredados."}
```

El producto inválido no debe aparecer en el listado administrativo.

## 4. Lectura pública — Tanda 3

### P18 — Selección inicial: disponible de menor precio

**GET** `{{baseUrl}}/api/public/products/{{productoId}}`, sin sesión si querés comprobar acceso público → **HTTP 200**. Rojo 20 y Celeste 18 están disponibles; Blanco 17 no. Se selecciona Celeste:

```json
{
  "id":301,
  "nombre":"Pintura Acme exterior 20 L",
  "precioReferencia":18.00,
  "disponible":true,
  "activo":true,
  "selectedVariantId":403,
  "caracteristicas":[{"nombre":"Marca Postman S3","valor":"Acme"}],
  "images":[],
  "variantes":[
    {"id":401,"caracteristicaId":201,"caracteristicaNombre":"Color Postman S3","valor":"Rojo","unidad":null,"sku":"ACME-ROJO","precioReferencia":20.00,"disponible":true},
    {"id":402,"caracteristicaId":201,"caracteristicaNombre":"Color Postman S3","valor":"Blanco","unidad":null,"sku":"ACME-BLANCO","precioReferencia":17.00,"disponible":false},
    {"id":403,"caracteristicaId":201,"caracteristicaNombre":"Color Postman S3","valor":"Celeste","unidad":null,"sku":"ACME-CELESTE","precioReferencia":18.00,"disponible":true}
  ]
}
```

`images: []` supone que no subiste fotos. El SKU aparece en `variantes`, no como campo del producto.

**GET** `{{baseUrl}}/api/public/products?page=0&size=100` → **HTTP 200**. Encontrá el producto dentro de `content`:

```json
{"content":[{"id":301,"precioReferencia":18.00,"disponible":true,"selectedVariantId":403,"primaryThumbnailUrl":null}],"page":0,"size":100}
```

El listado no incluye `sku` ni `variantes`; `primaryThumbnailUrl` es `null` sin fotos publicadas. Los totales y el resto de `content` dependen de la base.

### P19 — Variante inactiva: no aparece en el detalle público

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{celesteId}}/deactivate`, sin cuerpo → **HTTP 200**:

```json
{"id":403,"activo":false}
```

**GET** `{{baseUrl}}/api/public/products/{{productoId}}` → **HTTP 200**. La selección pasa a Rojo; Celeste desaparece de `variantes` públicas, pero continúa en el `GET` administrativo:

```json
{"selectedVariantId":401,"precioReferencia":20.00,"disponible":true,"variantes":[{"id":401},{"id":402}]}
```

### P20 — Ninguna activa disponible: menor precio activo

**PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{rojoId}}`

```json
{"sku":"ACME-ROJO","precioReferencia":20.00,"disponible":false,"caracteristicaId":{{colorId}},"valor":"Rojo"}
```

**HTTP 200**:

```json
{"id":401,"precioReferencia":20.00,"disponible":false,"activo":true}
```

**GET** `{{baseUrl}}/api/public/products/{{productoId}}` → **HTTP 200**. Blanco y Rojo están activos, ambos no disponibles; se selecciona Blanco por menor precio:

```json
{"selectedVariantId":402,"precioReferencia":17.00,"disponible":false,"variantes":[{"id":401,"disponible":false},{"id":402,"disponible":false}]}
```

Para probar el desempate por ID, hacé **PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{blancoId}}`:

```json
{"sku":"ACME-BLANCO","precioReferencia":20.00,"disponible":false,"caracteristicaId":{{colorId}},"valor":"Blanco"}
```

**HTTP 200**, JSON con `{"id":402,"precioReferencia":20.00,"disponible":false}`. Luego **GET** público del detalle → **HTTP 200**:

```json
{"selectedVariantId":401,"precioReferencia":20.00,"disponible":false}
```

Rojo y Blanco empatan en disponibilidad y precio; gana el ID menor. Antes de P21, devolvé Blanco a `17.00` con el mismo `PUT` cambiando solo `precioReferencia`; esperado **HTTP 200** con `{"id":402,"precioReferencia":17.00}`.

### P21 — Producto inactivo y bloqueo de última variante

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/deactivate`, sin cuerpo → **HTTP 200**:

```json
{"id":301,"activo":false}
```

**GET** `{{baseUrl}}/api/public/products/{{productoId}}` → **HTTP 404**:

```json
{"status":404,"code":"RESOURCE_NOT_FOUND","message":"El producto solicitado no existe."}
```

El listado público tampoco incluye `productoId`; la administración sí lo muestra. Aun con el producto inactivo, **POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{rojoId}}/deactivate`, sin cuerpo → **HTTP 200**:

```json
{"id":401,"activo":false}
```

Blanco queda como única activa. **POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{blancoId}}/deactivate`, sin cuerpo → **HTTP 409**:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"No se puede desactivar la última variante activa."}
```

Restaurá Rojo con **POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/{{rojoId}}/reactivate` → **HTTP 200**, `{"id":401,"activo":true}`. Después **POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/reactivate` → **HTTP 200**, `{"id":301,"activo":true}`.

### P22 — Fotos comunes a la ficha, si ya hay una publicada

Este caso requiere una foto publicada en `productoId` por el flujo de imágenes del catálogo. **GET** `{{baseUrl}}/api/public/products/{{productoId}}` → **HTTP 200**:

```json
{"id":301,"images":[{"id":501,"position":1,"primary":true,"thumbnailUrl":"https://...","detailUrl":"https://..."}],"variantes":[{"id":401},{"id":402}]}
```

Cambiar la variante seleccionada con disponibilidad/precio y repetir el `GET` debe cambiar `selectedVariantId` y los escalares, **sin cambiar** `images`. En el listado público, `primaryThumbnailUrl` coincide con la miniatura publicada. Si no hay foto publicada, el resultado esperado es `images: []` y `primaryThumbnailUrl: null`; no se puede validar con Postman la rama de galería sin ese dato de prueba.

## 5. Validación HTTP y seguridad

### P23 — Solicitudes inválidas y recursos inexistentes

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants` con precio cero:

```json
{"precioReferencia":0,"disponible":true,"caracteristicaId":{{colorId}},"valor":"Verde"}
```

**HTTP 400** por Bean Validation. `fieldErrors` contiene al menos una entrada con `field: "precioReferencia"`:

```json
{"status":400,"code":"VALIDATION_ERROR","message":"Hay datos inválidos en la solicitud.","fieldErrors":[{"field":"precioReferencia"}]}
```

**POST** con valor sin característica:

```json
{"precioReferencia":22.00,"disponible":true,"valor":"Verde"}
```

**HTTP 409** en este producto con varias variantes, porque falla antes la regla de atributo común:

```json
{"status":409,"code":"RESOURCE_CONFLICT","message":"El atributo variable debe ser el mismo para todas las variantes."}
```

Para aislar la validación de valor sin característica, usá `PUT {{baseUrl}}/api/catalog/products/{{legadoId}}/variants/{{legadoVarianteId}}` con:

```json
{"precioReferencia":9.50,"disponible":false,"valor":"Verde"}
```

**HTTP 400**:

```json
{"status":400,"code":"VALIDATION_ERROR","message":"El valor requiere una característica."}
```

**GET** `{{baseUrl}}/api/catalog/products/999999999/variants` → **HTTP 404**:

```json
{"status":404,"code":"RESOURCE_NOT_FOUND","message":"El producto solicitado no existe."}
```

**PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants/999999999`:

```json
{"precioReferencia":22.00,"disponible":true,"caracteristicaId":{{colorId}},"valor":"Verde"}
```

**HTTP 404**:

```json
{"status":404,"code":"RESOURCE_NOT_FOUND","message":"La variante solicitada no existe."}
```

### P24 — Acceso por rol y CSRF

Probá **GET** `{{baseUrl}}/api/catalog/products/{{productoId}}/variants` sin cookies → **HTTP 401**:

```json
{"status":401,"code":"AUTH_REQUIRED","message":"Se requiere autenticación."}
```

Con sesión válida, quitá solamente el header `X-XSRF-TOKEN` de un **POST** administrativo → **HTTP 403**:

```json
{"status":403,"code":"CSRF_TOKEN_INVALID","message":"El token CSRF es inválido o está ausente."}
```

Con una cuenta autenticada sin rol de catálogo, el mismo `GET` → **HTTP 403**:

```json
{"status":403,"code":"ACCESS_DENIED","message":"No tenés permisos para realizar esta acción."}
```

Con `SUPER_ADMIN`, `ADMIN` o `USER`, el `GET` administrativo devuelve **HTTP 200** y las mutaciones con CSRF válido siguen las respuestas de esta guía. Los dos `GET /api/public/products...` devuelven **HTTP 200** sin sesión para productos activos. Probá la seguridad con cuentas de prueba reales, sin poner credenciales en archivos del repositorio.

## 6. Alcance que Postman no demuestra

- **V10 / backfill:** sobre una base migrada desde V9, elegí un producto que existía antes y hacé `GET /api/catalog/products/{id}`. Esperado: **HTTP 200**, `varianteInicialId` no nulo, una variante activa con el precio y disponibilidad anteriores. Para confirmar que *todos* los productos fueron migrados, FKs, restricción de precio y unicidad de SKU a nivel MySQL, hacen falta la inspección de base y las pruebas de migración/persistencia; un caso HTTP aislado no alcanza.
- **V11 / fuente única:** las respuestas y ediciones de P14–P20 comprueban el comportamiento HTTP desde variantes. Que `products.precio_referencia` y `products.disponible` realmente hayan sido eliminadas requiere inspeccionar el esquema migrado y la prueba de V11.
- **Concurrencia:** el bloqueo de producto y la unicidad global de SKU ante carreras requieren las pruebas MySQL de concurrencia. La secuencia manual de Postman es serial.
- **Despliegue y frontend:** esta guía no valida staging ni el formulario React. El plan registra la suite frontend global con una falla MFA ajena al catálogo; no la presentes como verde por completar estos requests.

## Checklist de cierre manual

- [ ] Alta heredada y alta con `varianteInicial` crean exactamente una variante activa; fuentes mezcladas dan 400.
- [ ] Alta gradual, identidad valor + unidad, SKU global, característica compartida y borrado en uso respetan sus 409.
- [ ] Desactivación/reactivación conservan IDs y no permiten dejar cero variantes activas.
- [ ] Administración incluye inactivas; el detalle público las omite y el listado no expone SKU.
- [ ] `selectedVariantId`, precio y disponibilidad siguen prioridad disponible → menor precio → menor ID.
- [ ] Producto inactivo no aparece públicamente; foto publicada, si existe, sigue ligada a la ficha.
- [ ] Acceso administrativo exige rol y CSRF; los GET públicos funcionan sin sesión.
