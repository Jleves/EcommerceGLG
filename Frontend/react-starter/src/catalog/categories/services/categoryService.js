import { apiRequest } from '../../../services/apiClient.js'
import { ENDPOINTS } from '../../../services/endpoints.js'

const mutationOptions = { retryAuth: false, retryCsrf: false }

function categoryPayload({ nombre, descripcion, icono }) {
  return { nombre, descripcion, icono }
}

export function listCategories({ signal } = {}) {
  return apiRequest(ENDPOINTS.CATALOG.CATEGORIES, { signal })
}

export function createCategory(values) {
  return apiRequest(ENDPOINTS.CATALOG.CATEGORIES, {
    ...mutationOptions,
    method: 'POST',
    body: categoryPayload(values),
  })
}

export function updateCategory(id, values) {
  return apiRequest(ENDPOINTS.CATALOG.CATEGORY(id), {
    ...mutationOptions,
    method: 'PUT',
    body: categoryPayload(values),
  })
}

export function deactivateCategory(id) {
  return apiRequest(ENDPOINTS.CATALOG.CATEGORY_DEACTIVATE(id), {
    ...mutationOptions,
    method: 'POST',
  })
}

export function reactivateCategory(id) {
  return apiRequest(ENDPOINTS.CATALOG.CATEGORY_REACTIVATE(id), {
    ...mutationOptions,
    method: 'POST',
  })
}
