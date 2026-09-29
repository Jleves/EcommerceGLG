import { apiRequest } from '../../services/apiClient.js'
import { ENDPOINTS } from '../../services/endpoints.js'
import { ApiClientError } from '../../services/ApiClientError.js'

const singleAttempt = { method: 'POST', retryAuth: false, retryCsrf: false }

function loginResult(response) {
  if (response?.status === 'AUTHENTICATED' && response.user) {
    return { status: response.status, user: normalizeUser(response.user) }
  }
  if (response?.status === 'MFA_REQUIRED' && response.maskedEmail
      && Number.isFinite(Date.parse(response.expiresAt)) && Number.isFinite(Date.parse(response.resendAvailableAt))) {
    return { status: response.status, maskedEmail: response.maskedEmail,
      expiresAt: response.expiresAt, resendAvailableAt: response.resendAvailableAt }
  }
  throw new ApiClientError({ fallbackMessage: 'No se pudo confirmar el resultado del ingreso.' })
}

function normalizeUser(user) {
  if (!user) return null
  const normalized = { ...user, role: user.role || user.rol }
  delete normalized.rol
  delete normalized.username
  return normalized
}

export async function login(credentials) {
  const response = await apiRequest(ENDPOINTS.AUTH.LOGIN, {
    ...singleAttempt,
    body: credentials,
    retryAuth: false,
  })
  return loginResult(response)
}

export async function verifyMfa(code) {
  return loginResult(await apiRequest(ENDPOINTS.AUTH.MFA_VERIFY, { ...singleAttempt, body: { code } }))
}

export function resendMfa() {
  return apiRequest(ENDPOINTS.AUTH.MFA_RESEND, singleAttempt)
}

export function cancelMfa() {
  return apiRequest(ENDPOINTS.AUTH.MFA_CANCEL, singleAttempt)
}

export async function logout() {
  try {
    await cancelMfa()
  } catch {
    // Still revoke the session if challenge cancellation is unavailable.
  }
  return apiRequest(ENDPOINTS.AUTH.LOGOUT, {
    method: 'POST',
    retryAuth: false,
  })
}

export function changePassword({ currentPassword, newPassword }) {
  return apiRequest(ENDPOINTS.AUTH.CHANGE_PASSWORD, {
    method: 'POST', body: { currentPassword, newPassword },
  })
}

export async function getCurrentUser(options) {
  const user = await apiRequest(ENDPOINTS.AUTH.ME, options)
  if (!user) throw new ApiClientError()
  return normalizeUser(user)
}

export async function requestPasswordReset(email) {
  return apiRequest(ENDPOINTS.AUTH.FORGOT_PASSWORD, {
    method: 'POST',
    body: { email },
    retryAuth: false,
  })
}

export async function validateResetToken(token) {
  return apiRequest(ENDPOINTS.AUTH.VALIDATE_RESET_TOKEN(token), {
    retryAuth: false,
  })
}

export async function resetPassword({ token, newPassword }) {
  return apiRequest(ENDPOINTS.AUTH.RESET_PASSWORD, {
    method: 'POST',
    body: { token, newPassword },
    retryAuth: false,
  })
}
