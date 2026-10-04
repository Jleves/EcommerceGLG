# Pruebas de API en Postman — catálogo Sprint 2

Guía manual para los seis planes de creación, edición y cambio de estado de categorías, productos y características. Los números `1` y `2` en los JSON son **IDs ilustrativos**: reemplazalos por los IDs que devuelva tu API. Cada caso indica método, ruta, cuerpo, objetivo y resultado esperado.

## Preparación

1. Definí `{{baseUrl}}`, por ejemplo `http://localhost:8080`.
2. Ejecutá `GET {{baseUrl}}/api/auth/csrf`. Esperado: **204** y cookie `XSRF-TOKEN`.
3. Iniciá sesión mediante `POST {{baseUrl}}/api/auth/login`:

```json
{
  "email": "tu-usuario@ejemplo.com",
  "password": "tu-contraseña"
}
```

En el login y todas las solicitudes `POST`, `PUT` y `DELETE`, enviá el header `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`. Conservá las cookies de Postman para el mismo host; el catálogo usa `ACCESS_TOKEN`. Si el login devuelve **202 `MFA_REQUIRED`**, completá el desafío MFA. Usá una cuenta `SUPER_ADMIN`, `ADMIN` o `USER`.

Usá **Body → raw → JSON** para los casos con JSON y **Body → none** para los casos sin cuerpo. Guardá los IDs obtenidos como `{{categoriaId}}`, `{{otraCategoriaId}}`, `{{marcaId}}`, `{{pesoId}}`, `{{productoId}}` y `{{productoConCaracteristicasId}}`.

Los errores tienen `timestamp`, `status`, `code`, `message`, `path` y `requestId`; `fieldErrors` aparece cuando hay detalles de validación. Ejemplo ilustrativo:

```json
{
  "timestamp": "2026-10-02T12:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Hay datos inválidos en la solicitud.",
  "path": "/api/catalog/categories",
  "requestId": "valor-generado",
  "fieldErrors": []
}
```

En cada prueba negativa, consultá el recurso después para confirmar que no hubo cambios parciales.

## 1. Crear y listar categorías

### C1 — Alta válida

**POST** `{{baseUrl}}/api/catalog/categories`. Prueba recorte del nombre, descripción y estado inicial.

```json
{
  "nombre": "  Cementos  ",
  "descripcion": "Materiales para construcción",
  "icono": "cemento"
}
```

**Esperado:** **201**, ID generado, `nombre: "Cementos"`, datos enviados y `activo: true`. Guardá `{{categoriaId}}`.

### C2 — Descripción omitida

**POST** `{{baseUrl}}/api/catalog/categories`.

```json
{
  "nombre": "Herramientas",
  "icono": "herramientas"
}
```

**Esperado:** **201**, `descripcion: null`, `activo: true`. Guardá `{{otraCategoriaId}}`.

### C3 — Duplicado con espacios y mayúsculas

**POST** `{{baseUrl}}/api/catalog/categories`, después de C1.

```json
{
  "nombre": " CEMENTOS ",
  "descripcion": "Duplicada",
  "icono": "otro"
}
```

**Esperado:** **409 `RESOURCE_CONFLICT`**; C1 queda intacta.

### C4 — Campos obligatorios en blanco

**POST** `{{baseUrl}}/api/catalog/categories`.

```json
{
  "nombre": "   ",
  "descripcion": "No debe guardarse",
  "icono": " "
}
```

**Esperado:** **400 `VALIDATION_ERROR`**, con errores de `nombre` e `icono`.

### C5 — Listado

**GET** `{{baseUrl}}/api/catalog/categories`, sin body.

**Esperado:** **200**, arreglo ordenado por nombre e ID, incluidas las categorías inactivas. En una base vacía devuelve `[]`.

## 2. Editar y cambiar el estado de categorías

### C6 — Edición completa

**PUT** `{{baseUrl}}/api/catalog/categories/{{categoriaId}}`.

```json
{
  "nombre": "  Cementos y cales  ",
  "descripcion": "Cemento, cal y mezclas",
  "icono": "bolsa"
}
```

**Esperado:** **200**, mismo ID, `nombre: "Cementos y cales"` y estado `activo` conservado.

### C7 — Edición con nombre ocupado

**PUT** `{{baseUrl}}/api/catalog/categories/{{categoriaId}}`.

```json
{
  "nombre": " HERRAMIENTAS ",
  "descripcion": "No debe aplicarse",
  "icono": "otro"
}
```

**Esperado:** **409 `RESOURCE_CONFLICT`**; C6 sigue vigente.

### C8 — Desactivar

**POST** `{{baseUrl}}/api/catalog/categories/{{categoriaId}}/deactivate`, sin body.

**Esperado:** **200**, mismo ID, `activo: false`. Repetir devuelve **200** con `activo: false`. C5 sigue mostrando la categoría.

### C9 — Editar categoría inactiva

**PUT** `{{baseUrl}}/api/catalog/categories/{{categoriaId}}`.

```json
{
  "nombre": "Cementos y cales",
  "descripcion": null,
  "icono": "bolsa"
}
```

**Esperado:** **200**, `descripcion: null` y `activo: false`.

### C10 — Reactivar

**POST** `{{baseUrl}}/api/catalog/categories/{{categoriaId}}/reactivate`, sin body.

**Esperado:** **200**, `activo: true`; repetir también devuelve **200**.

### C11 — Categoría inexistente

**PUT** `{{baseUrl}}/api/catalog/categories/99999999`.

```json
{
  "nombre": "Inexistente",
  "descripcion": null,
  "icono": "otro"
}
```

**Esperado:** **404 `RESOURCE_NOT_FOUND`**. Desactivar o reactivar el mismo ID también devuelve **404**. No hay `GET /api/catalog/categories/{id}`; consultá C5.

## 3. Crear y consultar características

### H1 — Alta válida

**POST** `{{baseUrl}}/api/catalog/characteristics`.

```json
{
  "nombre": "  Marca  "
}
```

**Esperado:** **201**, ID generado, `nombre: "Marca"`, `cantidadProductos: 0`. Guardá `{{marcaId}}`.

### H2 — Segunda característica

**POST** `{{baseUrl}}/api/catalog/characteristics`.

```json
{
  "nombre": "Peso"
}
```

**Esperado:** **201**, `cantidadProductos: 0`. Guardá `{{pesoId}}`.

### H3 — Duplicado normalizado

**POST** `{{baseUrl}}/api/catalog/characteristics`.

```json
{
  "nombre": " MARCA "
}
```

**Esperado:** **409 `RESOURCE_CONFLICT`**.

### H4 — Nombre en blanco

**POST** `{{baseUrl}}/api/catalog/characteristics`.

```json
{
  "nombre": "   "
}
```

**Esperado:** **400 `VALIDATION_ERROR`**.

### H5 — Listado y consultas por ID

Sin body:

| Solicitud | Respuesta esperada |
| --- | --- |
| `GET {{baseUrl}}/api/catalog/characteristics` | **200**, arreglo ordenado por nombre e ID; `[]` si está vacío. |
| `GET {{baseUrl}}/api/catalog/characteristics/{{marcaId}}` | **200**, `cantidadProductos: 0` antes de asociarla. |
| `GET {{baseUrl}}/api/catalog/characteristics/99999999` | **404 `RESOURCE_NOT_FOUND`**. |

## 4. Crear productos básicos y con características

Usá una categoría activa en P1–P5. Reemplazá los IDs ilustrativos dentro de cada JSON.

### P1 — Producto básico

**POST** `{{baseUrl}}/api/catalog/products`.

```json
{
  "nombre": "  Cemento Portland 50 kg  ",
  "categoriaId": 1,
  "descripcion": "Bolsa de cemento",
  "precioReferencia": 12500.50,
  "disponible": true
}
```

**Esperado:** **201**, ID generado, nombre recortado, `activo: true`, `destacado: false`, `caracteristicas: []` y precio conservado. Guardá `{{productoId}}`.

### P2 — Producto con valores

**POST** `{{baseUrl}}/api/catalog/products`.

```json
{
  "nombre": "Cemento reforzado 50 kg",
  "categoriaId": 1,
  "descripcion": null,
  "precioReferencia": 15999.99,
  "disponible": false,
  "caracteristicas": [
    {
      "caracteristicaId": 1,
      "valor": "  Acme  ",
      "unidad": null
    },
    {
      "caracteristicaId": 2,
      "valor": "  50  ",
      "unidad": "  kg  "
    }
  ]
}
```

**Esperado:** **201**. En `caracteristicas`, `Marca` tiene `valor: "Acme"` y `Peso` tiene `valor: "50"`, `unidad: "kg"`. Guardá `{{productoConCaracteristicasId}}`. H5 sobre ambas definiciones debe mostrar `cantidadProductos: 1`.

### P3 — Arreglo vacío explícito

**POST** `{{baseUrl}}/api/catalog/products`.

```json
{
  "nombre": "Cal hidratada",
  "categoriaId": 1,
  "descripcion": null,
  "precioReferencia": 8400.00,
  "disponible": true,
  "caracteristicas": []
}
```

**Esperado:** **201**, `caracteristicas: []`.

### P4 — Nombre de producto repetido

Repetí exactamente P1. **Esperado:** **201** con otro ID; los nombres de producto pueden repetirse.

### P5 — Categoría inexistente

**POST** `{{baseUrl}}/api/catalog/products`.

```json
{
  "nombre": "Producto sin categoría",
  "categoriaId": 99999999,
  "descripcion": null,
  "precioReferencia": 100.00,
  "disponible": true
}
```

**Esperado:** **404 `RESOURCE_NOT_FOUND`**, sin producto nuevo.

### P6 — Categoría inactiva

Ejecutá C8 y luego P1 usando `{{categoriaId}}`. **Esperado:** **409 `RESOURCE_CONFLICT`**, sin producto nuevo. Reactivá la categoría con C10 para seguir.

### P7 — Característica duplicada

**POST** `{{baseUrl}}/api/catalog/products`.

```json
{
  "nombre": "Prueba de duplicado",
  "categoriaId": 1,
  "descripcion": null,
  "precioReferencia": 100.00,
  "disponible": true,
  "caracteristicas": [
    { "caracteristicaId": 1, "valor": "Acme", "unidad": null },
    { "caracteristicaId": 1, "valor": "Otra", "unidad": null }
  ]
}
```

**Esperado:** **400 `VALIDATION_ERROR`**, sin producto ni asociaciones nuevas.

### P8 — Característica inexistente

**POST** `{{baseUrl}}/api/catalog/products`.

```json
{
  "nombre": "Prueba de referencia",
  "categoriaId": 1,
  "descripcion": null,
  "precioReferencia": 100.00,
  "disponible": true,
  "caracteristicas": [
    { "caracteristicaId": 99999999, "valor": "Valor", "unidad": null }
  ]
}
```

**Esperado:** **404 `RESOURCE_NOT_FOUND`**, sin producto nuevo.

### P9 — Precio inválido

**POST** `{{baseUrl}}/api/catalog/products`.

```json
{
  "nombre": "Precio inválido",
  "categoriaId": 1,
  "descripcion": null,
  "precioReferencia": 10.123,
  "disponible": true
}
```

**Esperado:** **400 `VALIDATION_ERROR`**; no se redondea. Repetí con `precioReferencia` igual a `0`, `-1` y `10000000000000.00`: también **400**. `nombre` en blanco, `categoriaId: 0` o `disponible: null` también dan **400**.

## 5. Consultar, editar y cambiar el estado de productos

### P10 — Consulta por ID

**GET** `{{baseUrl}}/api/catalog/products/{{productoId}}`, sin body.

**Esperado:** **200**, datos persistidos y arreglo `caracteristicas`. Para el ID `99999999`: **404 `RESOURCE_NOT_FOUND`**.

### P11 — Edición básica completa

**PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}`.

```json
{
  "nombre": "  Cemento Portland actualizado  ",
  "categoriaId": 1,
  "descripcion": null,
  "precioReferencia": 13000.25,
  "disponible": false
}
```

**Esperado:** **200**, mismo ID, nombre recortado, descripción `null`, precio y disponibilidad nuevos. Conserva `activo`, `destacado` y las asociaciones, porque `caracteristicas` está omitido.

### P12 — Desactivar producto

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/deactivate`, sin body.

**Esperado:** **200**, `activo: false`; repetir devuelve **200**. P10 sigue encontrando el producto.

### P13 — Editar producto inactivo y asignar categoría inactiva

Desactivá `{{otraCategoriaId}}`. Después, **PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}`.

```json
{
  "nombre": "Cemento en categoría inactiva",
  "categoriaId": 2,
  "descripcion": "Edición permitida",
  "precioReferencia": 14000.00,
  "disponible": true
}
```

**Esperado:** **200**, categoría nueva y `activo: false`. La edición admite la categoría inactiva aunque el alta P6 la rechace.

### P14 — Reactivar producto con categoría inactiva

**POST** `{{baseUrl}}/api/catalog/products/{{productoId}}/reactivate`, sin body.

**Esperado:** **200**, `activo: true`, conservando la categoría. Repetir devuelve **200**. Reactivá la categoría al terminar.

### P15 — Referencia inexistente en edición

**PUT** `{{baseUrl}}/api/catalog/products/{{productoId}}`.

```json
{
  "nombre": "Cambio que no debe guardarse",
  "categoriaId": 99999999,
  "descripcion": null,
  "precioReferencia": 100.00,
  "disponible": true
}
```

**Esperado:** **404 `RESOURCE_NOT_FOUND`**; P10 muestra los datos anteriores. `PUT`, desactivar o reactivar el producto `99999999` también devuelve **404**.

## 6. Renombrar y eliminar características; editar valores

### H6 — Renombrar sin perder asociaciones

**PUT** `{{baseUrl}}/api/catalog/characteristics/{{marcaId}}`.

```json
{
  "nombre": "  Fabricante  "
}
```

**Esperado:** **200**, mismo ID, `nombre: "Fabricante"`, `cantidadProductos: 1`. P10 sobre `{{productoConCaracteristicasId}}` muestra el nombre nuevo y conserva `valor: "Acme"`.

### H7 — Renombrar con nombre ocupado

**PUT** `{{baseUrl}}/api/catalog/characteristics/{{marcaId}}`.

```json
{
  "nombre": " PESO "
}
```

**Esperado:** **409 `RESOURCE_CONFLICT`**; H6 sigue vigente. Cambiar solo las mayúsculas del nombre propio sí devuelve **200**.

### P16 — Omitir características al editar

**PUT** `{{baseUrl}}/api/catalog/products/{{productoConCaracteristicasId}}`.

```json
{
  "nombre": "Cemento reforzado editado",
  "categoriaId": 1,
  "descripcion": "Sin tocar valores",
  "precioReferencia": 16000.00,
  "disponible": true
}
```

**Esperado:** **200**; conserva las dos características y sus valores.

### P17 — Reemplazar el conjunto completo

**PUT** `{{baseUrl}}/api/catalog/products/{{productoConCaracteristicasId}}`.

```json
{
  "nombre": "Cemento reforzado editado",
  "categoriaId": 1,
  "descripcion": "Valores actualizados",
  "precioReferencia": 16000.00,
  "disponible": true,
  "caracteristicas": [
    { "caracteristicaId": 1, "valor": "  Nueva marca  ", "unidad": "   " }
  ]
}
```

**Esperado:** **200**; solo queda `Fabricante`, `valor: "Nueva marca"`, `unidad: null`. `Peso` pasa a `cantidadProductos: 0`. El GET del producto confirma el conjunto final.

### P18 — Lista vacía elimina todas las asociaciones

**PUT** `{{baseUrl}}/api/catalog/products/{{productoConCaracteristicasId}}`.

```json
{
  "nombre": "Cemento reforzado editado",
  "categoriaId": 1,
  "descripcion": "Sin características",
  "precioReferencia": 16000.00,
  "disponible": true,
  "caracteristicas": []
}
```

**Esperado:** **200**, `caracteristicas: []`; `Fabricante` pasa a `cantidadProductos: 0`.

### P19 — `null` explícito se rechaza

**PUT** `{{baseUrl}}/api/catalog/products/{{productoConCaracteristicasId}}`.

```json
{
  "nombre": "Cambio rechazado",
  "categoriaId": 1,
  "descripcion": null,
  "precioReferencia": 100.00,
  "disponible": true,
  "caracteristicas": null
}
```

**Esperado:** **400 `VALIDATION_ERROR`**; nombre, precio y asociaciones anteriores intactos.

### P20 — ID duplicado durante edición

**PUT** `{{baseUrl}}/api/catalog/products/{{productoConCaracteristicasId}}`.

```json
{
  "nombre": "Cambio rechazado",
  "categoriaId": 1,
  "descripcion": null,
  "precioReferencia": 100.00,
  "disponible": true,
  "caracteristicas": [
    { "caracteristicaId": 1, "valor": "Uno", "unidad": null },
    { "caracteristicaId": 1, "valor": "Dos", "unidad": null }
  ]
}
```

**Esperado:** **400 `VALIDATION_ERROR`**, sin cambios parciales. Con un solo elemento de `caracteristicaId: 99999999`, esperá **404 `RESOURCE_NOT_FOUND`**.

### H8 — Borrar una definición usada

Volvé a asociar `{{marcaId}}` a P2 mediante P17. Después ejecutá **DELETE** `{{baseUrl}}/api/catalog/characteristics/{{marcaId}}`, sin body.

**Esperado:** **204**, sin cuerpo. El GET de la característica devuelve **404**. El producto sigue existiendo sin esa asociación y conserva las demás. Repetir el DELETE devuelve **404**. Borrar una definición sin uso también devuelve **204**.

## 7. Formato, límites y seguridad

| Prueba | Cómo hacerla | Esperado |
| --- | --- | --- |
| JSON malformado | Enviar `{"nombre":` en un POST de categoría o característica. | **400 `MALFORMED_REQUEST`**. |
| Límite de nombre | Enviar `nombre` de 161 caracteres. | **400 `VALIDATION_ERROR`**. |
| Límite de icono | Enviar `icono` de 101 caracteres. | **400 `VALIDATION_ERROR`**. |
| Límite de descripción | Enviar `descripcion` de 1001 caracteres. | **400 `VALIDATION_ERROR`**. |
| Límite de valor o unidad | Enviar `valor` de 501 o `unidad` de 101 caracteres en una asociación. | **400 `VALIDATION_ERROR`**. |
| Anónimo | Quitar cookies y hacer un GET de catálogo. | **401 `AUTH_REQUIRED`**. |
| Sin CSRF | Con sesión válida, quitar `X-XSRF-TOKEN` de una mutación. | **403 `CSRF_TOKEN_INVALID`**. |
| Rol no admitido | Con sesión de ese rol y CSRF válido, consultar o modificar catálogo. | **403 `ACCESS_DENIED`**. |

Los flujos anteriores prueban contratos y persistencia observables por HTTP. Las garantías de concurrencia, bloqueo y rollback inducido de los planes requieren solicitudes coordinadas o pruebas de integración; una secuencia manual de JSON no demuestra por sí sola esos casos.
