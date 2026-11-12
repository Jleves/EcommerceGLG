import { apiRequest } from '../../../services/apiClient.js'
import { ENDPOINTS } from '../../../services/endpoints.js'

const mutationOptions = { retryAuth: false, retryCsrf: false }

export function listSubcategories(categoriaId, { signal } = {}) {
  const query = categoriaId == null ? '' : `?${new URLSearchParams({ categoriaId: String(categoriaId) })}`
  return apiRequest(`${ENDPOINTS.CATALOG.SUBCATEGORIES}${query}`, { signal })
}

export function getSubcategory(id, { signal } = {}) {
  return apiRequest(ENDPOINTS.CATALOG.SUBCATEGORY(id), { signal })
}

export function createSubcategory({ categoriaId, nombre, descripcion, subcategoriaPadreId }) {
  return apiRequest(ENDPOINTS.CATALOG.SUBCATEGORIES, {
    ...mutationOptions, method: 'POST', body: { categoriaId, nombre, descripcion, subcategoriaPadreId },
  })
}

export function updateSubcategory(id, { nombre, descripcion }) {
  return apiRequest(ENDPOINTS.CATALOG.SUBCATEGORY(id), {
    ...mutationOptions, method: 'PUT', body: { nombre, descripcion },
  })
}

export function deleteSubcategory(id) {
  return apiRequest(ENDPOINTS.CATALOG.SUBCATEGORY(id), { ...mutationOptions, method: 'DELETE' })
}
