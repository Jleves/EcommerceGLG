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

export function deactivateProduct(id) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_DEACTIVATE(id), { ...mutationOptions, method: 'POST' })
}

export function reactivateProduct(id) {
  return apiRequest(ENDPOINTS.CATALOG.PRODUCT_REACTIVATE(id), { ...mutationOptions, method: 'POST' })
}
