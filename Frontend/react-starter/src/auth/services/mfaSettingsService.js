import { apiRequest } from '../../services/apiClient.js'
import { ApiClientError } from '../../services/ApiClientError.js'
import { ENDPOINTS } from '../../services/endpoints.js'
export { cancelMfa as cancel } from './authService.js'

const singleAttempt = { method: 'POST', retryAuth: false, retryCsrf: false }

function timing(response) {
  if (!Number.isFinite(Date.parse(response?.expiresAt)) || !Number.isFinite(Date.parse(response?.resendAvailableAt))) {
    throw new ApiClientError()
  }
  return { expiresAt: response.expiresAt, resendAvailableAt: response.resendAvailableAt }
}

export async function getStatus() {
  const response = await apiRequest(ENDPOINTS.AUTH.MFA_STATUS, { retryAuth: false })
  if (typeof response?.enabled !== 'boolean' || !response.maskedEmail) throw new ApiClientError()
  return { enabled: response.enabled, enabledAt: response.enabledAt, maskedEmail: response.maskedEmail }
}

export async function start(enabled, currentPassword) {
  return timing(await apiRequest(enabled ? ENDPOINTS.AUTH.MFA_ENABLE_START : ENDPOINTS.AUTH.MFA_DISABLE_START,
    { ...singleAttempt, body: { currentPassword }, expectedStatus: 202 }))
}

export function confirm(enabled, code) {
  return apiRequest(enabled ? ENDPOINTS.AUTH.MFA_ENABLE_CONFIRM : ENDPOINTS.AUTH.MFA_DISABLE_CONFIRM,
    { ...singleAttempt, body: { code }, expectedStatus: 204 })
}

export async function resend() {
  return timing(await apiRequest(ENDPOINTS.AUTH.MFA_SETTINGS_RESEND, { ...singleAttempt, expectedStatus: 202 }))
}
