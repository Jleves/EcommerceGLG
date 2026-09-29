import { beforeEach, expect, it, vi } from 'vitest'
import { apiRequest } from '../../services/apiClient.js'
import * as settings from './mfaSettingsService.js'

vi.mock('../../services/apiClient.js', () => ({ apiRequest: vi.fn() }))
const times = { expiresAt: '2030-01-01T00:05:00Z', resendAvailableAt: '2030-01-01T00:01:00Z' }
beforeEach(() => vi.resetAllMocks())
it.each([true, false])('usa endpoints propios y cuerpos mínimos, sin reintentos: %s', async (enabled) => {
  const purpose = enabled ? 'enable' : 'disable'
  apiRequest.mockResolvedValueOnce(times).mockResolvedValueOnce(null)
  await expect(settings.start(enabled, 'secret')).resolves.toEqual(times)
  expect(apiRequest).toHaveBeenNthCalledWith(1, `/api/auth/mfa/${purpose}/start`, {
    method: 'POST', retryAuth: false, retryCsrf: false, expectedStatus: 202, body: { currentPassword: 'secret' },
  })
  await settings.confirm(enabled, '001234')
  expect(apiRequest).toHaveBeenNthCalledWith(2, `/api/auth/mfa/${purpose}/confirm`, {
    method: 'POST', retryAuth: false, retryCsrf: false, expectedStatus: 204, body: { code: '001234' },
  })
})
it('consulta estado sin refresh y no devuelve campos ajenos al contrato', async () => {
  const status = { enabled: false, enabledAt: null, maskedEmail: 'u***@example.com' }
  apiRequest.mockResolvedValue({ ...status, secret: 'unused' })
  await expect(settings.getStatus()).resolves.toEqual(status)
  expect(apiRequest).toHaveBeenCalledWith('/api/auth/mfa/status', { retryAuth: false })
})
it('reenvía configuración y cancela sin refresh ni repetición CSRF', async () => {
  apiRequest.mockResolvedValue(times)
  await settings.resend()
  expect(apiRequest).toHaveBeenLastCalledWith('/api/auth/mfa/settings/resend', {
    method: 'POST', retryAuth: false, retryCsrf: false, expectedStatus: 202,
  })
  await settings.cancel()
  expect(apiRequest).toHaveBeenLastCalledWith('/api/auth/mfa/cancel', {
    method: 'POST', retryAuth: false, retryCsrf: false,
  })
})
it('rechaza respuestas incompletas sin asumir estado ni desafío', async () => {
  apiRequest.mockResolvedValue(null)
  await expect(settings.getStatus()).rejects.toThrow()
  await expect(settings.start(true, 'secret')).rejects.toThrow()
  await expect(settings.resend()).rejects.toThrow()
})
