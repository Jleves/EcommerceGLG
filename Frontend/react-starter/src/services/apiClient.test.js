import { beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetApiClientForTests, apiRequest } from './apiClient.js'

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiClient', () => {
  beforeEach(() => {
    __resetApiClientForTests()
    document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
    vi.stubGlobal('fetch', vi.fn())
  })

  it('incluye cookies y el token CSRF en mutaciones', async () => {
    document.cookie = 'XSRF-TOKEN=csrf%20value; Path=/'
    fetch.mockResolvedValue(jsonResponse({ created: true }))

    await apiRequest('/api/items', { method: 'POST', body: { name: 'item' } })

    const [, config] = fetch.mock.calls[0]
    expect(config.credentials).toBe('include')
    expect(config.headers.get('X-XSRF-TOKEN')).toBe('csrf value')
    expect(config.headers.get('Content-Type')).toBe('application/json')
    expect(config.body).toBe(JSON.stringify({ name: 'item' }))
  })

  it('comparte un único refresh entre solicitudes concurrentes', async () => {
    document.cookie = 'XSRF-TOKEN=csrf; Path=/'
    let protectedCalls = 0
    let refreshCalls = 0
    fetch.mockImplementation(async (endpoint) => {
      if (endpoint === '/api/auth/refresh') {
        refreshCalls += 1
        await Promise.resolve()
        return new Response(null, { status: 204 })
      }
      protectedCalls += 1
      return protectedCalls <= 2
        ? jsonResponse({ code: 'AUTH_TOKEN_EXPIRED', message: 'Expiró' }, 401)
        : jsonResponse({ ok: true })
    })

    const responses = await Promise.all([
      apiRequest('/api/private/one'),
      apiRequest('/api/private/two'),
    ])

    expect(refreshCalls).toBe(1)
    expect(protectedCalls).toBe(4)
    expect(responses).toEqual([{ ok: true }, { ok: true }])
  })

  it('renueva un CSRF inválido y reintenta una sola vez', async () => {
    document.cookie = 'XSRF-TOKEN=stale; Path=/'
    let mutationCalls = 0
    fetch.mockImplementation(async (endpoint) => {
      if (endpoint === '/api/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=fresh; Path=/'
        return new Response(null, { status: 204 })
      }
      mutationCalls += 1
      return mutationCalls === 1
        ? jsonResponse({ code: 'CSRF_TOKEN_INVALID', message: 'CSRF inválido' }, 403)
        : jsonResponse({ ok: true })
    })

    await expect(apiRequest('/api/items', { method: 'DELETE' })).resolves.toEqual({ ok: true })
    expect(mutationCalls).toBe(2)
    expect(fetch.mock.calls.at(-1)[1].headers.get('X-XSRF-TOKEN')).toBe('fresh')
  })

  it('no intenta refresh para login', async () => {
    document.cookie = 'XSRF-TOKEN=csrf; Path=/'
    fetch.mockResolvedValue(jsonResponse({ code: 'AUTH_INVALID_CREDENTIALS', message: 'Credenciales inválidas' }, 401))

    await expect(apiRequest('/api/auth/login', { method: 'POST' })).rejects.toMatchObject({
      status: 401,
      code: 'AUTH_INVALID_CREDENTIALS',
    })
    expect(fetch).toHaveBeenCalledTimes(1)
  })
})

it.each(['/api/auth/mfa/login/verify', '/api/auth/mfa/login/resend', '/api/auth/mfa/cancel'])('MFA no renueva sesión: %s', async (endpoint) => {
  __resetApiClientForTests()
  document.cookie = 'XSRF-TOKEN=csrf; Path=/'
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'AUTH_REQUIRED' }, 401)))
  await expect(apiRequest(endpoint, { method: 'POST' })).rejects.toMatchObject({ status: 401 })
  expect(fetch).toHaveBeenCalledTimes(1)
})

it('MFA renueva CSRF para el próximo intento manual pero no repite el POST', async () => {
  __resetApiClientForTests()
  document.cookie = 'XSRF-TOKEN=csrf; Path=/'
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async (endpoint) => {
    if (endpoint === '/api/auth/csrf') return new Response(null, { status: 204 })
    return jsonResponse({ code: 'CSRF_TOKEN_INVALID' }, 403)
  }))
  await expect(apiRequest('/api/auth/mfa/login/verify', { method: 'POST', retryCsrf: false }))
    .rejects.toMatchObject({ status: 403 })
  expect(fetch.mock.calls.filter(([endpoint]) => endpoint === '/api/auth/mfa/login/verify')).toHaveLength(1)
})

it('conserva Retry-After sin reintentar y maneja fallo de red', async () => {
  document.cookie = 'XSRF-TOKEN=csrf; Path=/'
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'MFA_RATE_LIMITED' }), {
    status: 429, headers: { 'Content-Type': 'application/json', 'Retry-After': '42' },
  })))
  await expect(apiRequest('/api/auth/mfa/login/resend', { method: 'POST' })).rejects.toMatchObject({ retryAfter: 42 })
  expect(fetch).toHaveBeenCalledTimes(1)
  fetch.mockRejectedValueOnce(new TypeError('network'))
  await expect(apiRequest('/api/auth/mfa/login/verify', { method: 'POST' })).rejects.toMatchObject({ status: 0 })
  expect(fetch).toHaveBeenCalledTimes(2)
})

it('exige el 204 esperado antes de confirmar un cambio MFA', async () => {
  __resetApiClientForTests()
  document.cookie = 'XSRF-TOKEN=csrf; Path=/'
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ unexpected: true }, 200)))
  await expect(apiRequest('/api/auth/mfa/enable/confirm', {
    method: 'POST', retryAuth: false, retryCsrf: false, expectedStatus: 204,
  })).rejects.toMatchObject({ status: 0 })
  expect(fetch).toHaveBeenCalledTimes(1)
  fetch.mockResolvedValueOnce(new Response(null, { status: 204 }))
  await expect(apiRequest('/api/auth/mfa/enable/confirm', {
    method: 'POST', retryAuth: false, retryCsrf: false, expectedStatus: 204,
  })).resolves.toBeNull()
})

it.each([401, 403, 429, 503])('configuración MFA no repite mutaciones ante %s', async (status) => {
  __resetApiClientForTests()
  document.cookie = 'XSRF-TOKEN=csrf; Path=/'
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async (endpoint) => endpoint === '/api/auth/csrf'
    ? new Response(null, { status: 204 }) : jsonResponse({ code: status === 403 ? 'CSRF_TOKEN_INVALID' : 'MFA_RATE_LIMITED' }, status)))
  await expect(apiRequest('/api/auth/mfa/disable/confirm', {
    method: 'POST', retryAuth: false, retryCsrf: false, expectedStatus: 204,
  })).rejects.toMatchObject({ status })
  expect(fetch.mock.calls.filter(([endpoint]) => endpoint === '/api/auth/mfa/disable/confirm')).toHaveLength(1)
  expect(fetch.mock.calls.some(([endpoint]) => endpoint === '/api/auth/refresh')).toBe(false)
})
