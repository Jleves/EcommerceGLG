import { apiRequest } from '../../../services/apiClient.js'
import { ENDPOINTS } from '../../../services/endpoints.js'

const mutationOptions = { retryAuth: false, retryCsrf: false }

export function listCharacteristics({ signal } = {}) {
  return apiRequest(ENDPOINTS.CATALOG.CHARACTERISTICS, { signal })
}

export function getCharacteristic(id, { signal } = {}) {
  return apiRequest(ENDPOINTS.CATALOG.CHARACTERISTIC(id), { signal })
}

export function createCharacteristic({ nombre }) {
  return apiRequest(ENDPOINTS.CATALOG.CHARACTERISTICS, {
    ...mutationOptions,
    method: 'POST',
    body: { nombre },
  })
}

export function updateCharacteristic(id, { nombre }) {
  return apiRequest(ENDPOINTS.CATALOG.CHARACTERISTIC(id), {
    ...mutationOptions,
    method: 'PUT',
    body: { nombre },
  })
}

export function deleteCharacteristic(id) {
  return apiRequest(ENDPOINTS.CATALOG.CHARACTERISTIC(id), {
    ...mutationOptions,
    method: 'DELETE',
    expectedStatus: 204,
  })
}
