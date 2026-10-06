import { apiRequest } from '../../../services/apiClient.js'
import { ENDPOINTS } from '../../../services/endpoints.js'

export function listProducts(page = 0, nombre, { signal } = {}) {
  const query = new URLSearchParams({ page: String(page), size: '20' })
  if (nombre !== undefined) query.set('nombre', nombre)
  return apiRequest(`${ENDPOINTS.CATALOG.PRODUCTS}?${query}`, { signal })
}
