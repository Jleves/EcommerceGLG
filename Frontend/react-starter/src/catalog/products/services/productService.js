import { apiRequest } from '../../../services/apiClient.js'
import { ENDPOINTS } from '../../../services/endpoints.js'

export function listProducts(page = 0, nombre, { signal, categoriaId, subcategoriaId } = {}) {
  const query = new URLSearchParams({ page: String(page), size: '20' })
  if (nombre !== undefined) query.set('nombre', nombre)
  if (categoriaId != null) query.set('categoriaId', String(categoriaId))
  if (subcategoriaId != null) query.set('subcategoriaId', String(subcategoriaId))
  return apiRequest(`${ENDPOINTS.CATALOG.PRODUCTS}?${query}`, { signal })
}

const mutationOptions = { retryAuth: false, retryCsrf: false }

export function getProduct(id, { signal } = {}) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT(id), { signal })
}

export function createProduct(payload) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCTS, { ...mutationOptions, method: 'POST', body: payload })
}

export function updateProduct(id, payload) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT(id), { ...mutationOptions, method: 'PUT', body: payload })
}

export function createVariant(productId, payload) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_VARIANTS(productId), { ...mutationOptions, method: 'POST', body: payload })
}

export function updateVariant(productId, variantId, payload) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_VARIANT(productId, variantId), { ...mutationOptions, method: 'PUT', body: payload })
}

export function deactivateProduct(id) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_DEACTIVATE(id), { ...mutationOptions, method: 'POST' })
}

export function reactivateProduct(id) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_REACTIVATE(id), { ...mutationOptions, method: 'POST' })
}

export function listProductImages(id, { signal } = {}) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_IMAGES(id), { signal })
}

function imageBody(file) {
  const body = new FormData()
  body.append('file', file)
  return body
}

export function uploadProductImage(id, file) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_IMAGES(id), { ...mutationOptions, method: 'POST', body: imageBody(file) })
}

export function setPrimaryProductImage(id, imageId) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_IMAGE_PRIMARY(id, imageId), { ...mutationOptions, method: 'PUT' })
}

export function reorderProductImages(id, imageIds) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_IMAGES_ORDER(id), { ...mutationOptions, method: 'PUT', body: { imageIds } })
}

export function deleteProductImage(id, imageId) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_IMAGE(id, imageId), { ...mutationOptions, method: 'DELETE' })
}
