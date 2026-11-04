import { apiRequest } from '../services/apiClient.js'
import { ENDPOINTS } from '../services/endpoints.js'

const publicOptions = { retryAuth: false }

export function getPublicCatalog({ signal } = {}) {
  return apiRequest(ENDPOINTS.PUBLIC.CATALOG, { ...publicOptions, signal })
}

export function getPublicProducts(params = {}, { signal } = {}) {
  const query = new URLSearchParams()
  query.set('page', String(params.page ?? 0))
  query.set('size', String(params.size ?? 20))
  if (params.q) query.set('q', params.q)
  if (params.categoriaId) query.set('categoriaId', params.categoriaId)
  if (params.subcategoriaId) query.set('subcategoriaId', params.subcategoriaId)
  if (params.marca) query.set('marca', params.marca)
  for (const attribute of params.atributos ?? []) query.append('atributo', attribute)
  return apiRequest(`${ENDPOINTS.PUBLIC.PRODUCTS}?${query}`, { ...publicOptions, signal })
}

export function getPublicProduct(id, { signal } = {}) {
  return apiRequest(ENDPOINTS.PUBLIC.PRODUCT(id), { ...publicOptions, signal })
}

export function getRelatedProducts(id, { signal } = {}) {
  return apiRequest(ENDPOINTS.PUBLIC.RELATED(id), { ...publicOptions, signal })
}
