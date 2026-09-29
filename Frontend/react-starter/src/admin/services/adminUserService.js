import { apiRequest } from '../../services/apiClient.js'
import { ENDPOINTS } from '../../services/endpoints.js'

export function createUser(form) {
  return apiRequest(ENDPOINTS.ADMIN.USERS, { method: 'POST', body: form })
}

export function listUsers(page = 0, { signal } = {}) {
  const query = new URLSearchParams({ page: String(page), size: '20' })
  return apiRequest(`${ENDPOINTS.ADMIN.USERS}?${query}`, { signal })
}

export function getUser(id, { signal, retryAuth = true } = {}) {
  return apiRequest(ENDPOINTS.ADMIN.USER(id), { signal, retryAuth })
}

// An uncertain response must never cause an automatic second mutation.
const mutationOptions = { retryAuth: false, retryCsrf: false }

export function updateUserEmail(id, email) {
  return apiRequest(ENDPOINTS.ADMIN.USER_EMAIL(id), {
    ...mutationOptions, method: 'PATCH', body: { email },
  })
}

export function deactivateUser(id) {
  return apiRequest(ENDPOINTS.ADMIN.USER_DEACTIVATE(id), {
    ...mutationOptions, method: 'POST',
  })
}

export function reactivateUser(id) {
  return apiRequest(ENDPOINTS.ADMIN.USER_REACTIVATE(id), {
    ...mutationOptions, method: 'POST',
  })
}
