import { apiRequest } from '../../../services/apiClient.js'
import { ENDPOINTS } from '../../../services/endpoints.js'

export function listCharacteristics({ signal } = {}) {
  return apiRequest(ENDPOINTS.CATALOG.CHARACTERISTICS, { signal })
}
