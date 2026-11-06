import { apiRequest } from '../../services/apiClient.js'
import { ENDPOINTS } from '../../services/endpoints.js'

export function listSubscriptions(channel, page = 0, { signal } = {}) {
  const query = new URLSearchParams({ channel, page: String(page), size: '20' })
  return apiRequest(`${ENDPOINTS.ADMIN.SUBSCRIPTIONS}?${query}`, { signal })
}

export function exportSubscriptions(channel) {
  const query = new URLSearchParams({ channel })
  return apiRequest(`${ENDPOINTS.ADMIN.SUBSCRIPTIONS_EXPORT}?${query}`, { responseType: 'blob' })
}
