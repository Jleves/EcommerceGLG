import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../services/apiClient.js'
import { login, logout, verifyMfa, resendMfa, getCurrentUser } from './authService.js'

vi.mock('../../services/apiClient.js', () => ({ apiRequest: vi.fn() }))

describe('authService', () => {
  beforeEach(() => localStorage.clear())

  it.each([false, true])('cancela el desafío antes del logout, incluso si falla: %s', async (fails) => {
    apiRequest.mockReset()
    if (fails) apiRequest.mockRejectedValueOnce(new Error('network'))
    else apiRequest.mockResolvedValueOnce(undefined)
    apiRequest.mockResolvedValueOnce(undefined)
    await logout()
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/auth/mfa/cancel', { method: 'POST', retryAuth: false, retryCsrf: false })
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/auth/logout', { method: 'POST', retryAuth: false })
  })

  it('normaliza el usuario sin guardar credenciales en almacenamiento web', async () => {
    apiRequest.mockResolvedValue({
      status: 'AUTHENTICATED',
      user: { id: 7, username: 'user@example.com', email: 'user@example.com', rol: 'USER' },
    })

    const user = await login({ email: 'user@example.com', password: 'secret123' })

    expect(user).toEqual({ status: 'AUTHENTICATED', user: { id: 7, email: 'user@example.com', role: 'USER' } })
    expect(localStorage).toHaveLength(0)
    expect(sessionStorage).toHaveLength(0)
  })
})

it('conserva MFA_REQUIRED y rechaza respuestas incompletas sin autenticar', async () => {
  const pending = { status: 'MFA_REQUIRED', maskedEmail: 'u***@example.com', expiresAt: '2030-01-01T00:05:00Z', resendAvailableAt: '2030-01-01T00:01:00Z' }
  apiRequest.mockResolvedValueOnce(pending)
  await expect(login({})).resolves.toEqual(pending)
  apiRequest.mockResolvedValueOnce({ user: { id: 1 } })
  await expect(login({})).rejects.toThrow()
})

it('envía el código como string y desactiva reintentos en las operaciones MFA', async () => {
  apiRequest.mockReset().mockResolvedValue({ status: 'AUTHENTICATED', user: { id: 1, role: 'USER' } })
  await verifyMfa('001234')
  expect(apiRequest).toHaveBeenLastCalledWith('/api/auth/mfa/login/verify', {
    method: 'POST', retryAuth: false, retryCsrf: false, body: { code: '001234' },
  })
  await resendMfa()
  expect(apiRequest).toHaveBeenLastCalledWith('/api/auth/mfa/login/resend', {
    method: 'POST', retryAuth: false, retryCsrf: false,
  })
  await getCurrentUser({ retryAuth: false })
  expect(apiRequest).toHaveBeenLastCalledWith('/api/auth/me', { retryAuth: false })
})
