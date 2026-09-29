import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { __resetApiClientForTests } from '../../services/apiClient.js'
import { createUser, deactivateUser, getUser, listUsers, reactivateUser, updateUserEmail } from './adminUserService.js'

beforeEach(() => {
  __resetApiClientForTests()
  document.cookie = 'XSRF-TOKEN=admin-csrf; Path=/'
})

it('envía solo email y nunca repite automáticamente una mutación por error HTTP', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'INTERNAL_ERROR' }), {
    status: 500, headers: { 'Content-Type': 'application/json' },
  }))
  vi.stubGlobal('fetch', fetchMock)
  await expect(updateUserEmail(7, 'new@example.com')).rejects.toMatchObject({ status: 500 })
  expect(fetchMock).toHaveBeenCalledTimes(1)
  expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/users/7/email')
  expect(fetchMock.mock.calls[0][1].method).toBe('PATCH')
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ email: 'new@example.com' })
})

it('usa los endpoints de baja y reactivación sin body de actor, rol ni MFA', async () => {
  const response = { id: 7, enabled: false }
  const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify(response), {
    headers: { 'Content-Type': 'application/json' },
  })))
  vi.stubGlobal('fetch', fetchMock)
  await deactivateUser(7)
  await reactivateUser(7)
  expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
    '/api/admin/users/7/deactivate', '/api/admin/users/7/reactivate',
  ])
  for (const [, options] of fetchMock.mock.calls) {
    expect(options.method).toBe('POST')
    expect(options.body).toBeUndefined()
  }
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
})

it('envía el alta por el cliente HTTP con cookies y CSRF y devuelve el usuario público', async () => {
  const created = { id: 4, email: 'new@example.com', role: 'USER', enabled: true }
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(created), {
    status: 201, headers: { 'Content-Type': 'application/json' },
  }))
  vi.stubGlobal('fetch', fetchMock)
  const form = { email: created.email, password: 'initial-password', role: 'USER' }
  expect(await createUser(form)).toEqual(created)
  const [url, options] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/admin/users')
  expect(options.method).toBe('POST')
  expect(options.credentials).toBe('include')
  expect(options.headers.get('X-XSRF-TOKEN')).toBe('admin-csrf')
  expect(JSON.parse(options.body)).toEqual(form)
})

it('consulta la página acotada y el detalle con GET autenticado', async () => {
  const page = { content: [], page: 2, size: 20, totalElements: 40, totalPages: 2 }
  const detail = { id: 7, email: 'user@example.com', mfaEnabled: false }
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify(page), { headers: { 'Content-Type': 'application/json' } }))
    .mockResolvedValueOnce(new Response(JSON.stringify(detail), { headers: { 'Content-Type': 'application/json' } }))
  vi.stubGlobal('fetch', fetchMock)
  expect(await listUsers(2)).toEqual(page)
  expect(await getUser(7)).toEqual(detail)
  expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
    '/api/admin/users?page=2&size=20', '/api/admin/users/7',
  ])
  for (const [, options] of fetchMock.mock.calls) {
    expect(options.method).toBe('GET')
    expect(options.credentials).toBe('include')
    expect(options.body).toBeUndefined()
  }
})
