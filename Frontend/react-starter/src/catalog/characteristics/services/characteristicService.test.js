import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetApiClientForTests } from '../../../services/apiClient.js'
import {
  createCharacteristic,
  deleteCharacteristic,
  getCharacteristic,
  listCharacteristics,
  updateCharacteristic,
} from './characteristicService.js'

const baseUrl = '/api/catalog/characteristics'
const detailUrl = `${baseUrl}/id%2F%C3%B1%20%3F`
const id = 'id/ñ ?'

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  __resetApiClientForTests()
  document.cookie = 'XSRF-TOKEN=token; Path=/'
})

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
  vi.unstubAllGlobals()
})

describe('characteristicService', () => {
  it('lista y consulta detalle con cookies, señal e ID codificado', async () => {
    const list = [{ id: 1, nombre: 'Color', cantidadProductos: 2 }]
    const detail = list[0]
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(list))
      .mockResolvedValueOnce(jsonResponse(detail))
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal

    await expect(listCharacteristics({ signal })).resolves.toEqual(list)
    await expect(getCharacteristic(id, { signal })).resolves.toEqual(detail)
    expect(fetchMock.mock.calls.map(([url, options]) => [url, options.method, options.credentials, options.signal])).toEqual([
      [baseUrl, 'GET', 'include', signal],
      [detailUrl, 'GET', 'include', signal],
    ])
  })

  it('envía POST y PUT con solo nombre, cookies y CSRF', async () => {
    const created = { id: 3, nombre: 'Color', cantidadProductos: 0 }
    const updated = { ...created, nombre: 'Tono' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(created, 201))
      .mockResolvedValueOnce(jsonResponse(updated))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createCharacteristic({ nombre: 'Color', ignorar: true })).resolves.toEqual(created)
    await expect(updateCharacteristic(id, { nombre: 'Tono', ignorar: true })).resolves.toEqual(updated)
    expect(fetchMock.mock.calls.map(([url, options]) => [
      url, options.method, options.credentials, options.headers.get('X-XSRF-TOKEN'),
      options.headers.get('Content-Type'), JSON.parse(options.body),
    ])).toEqual([
      [baseUrl, 'POST', 'include', 'token', 'application/json', { nombre: 'Color' }],
      [detailUrl, 'PUT', 'include', 'token', 'application/json', { nombre: 'Tono' }],
    ])
  })

  it('exige 204 y cuerpo vacío al eliminar', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse({ unexpected: true }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(deleteCharacteristic(id)).resolves.toBeNull()
    await expect(deleteCharacteristic(id)).rejects.toMatchObject({ status: 0 })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls.map(([url, options]) => [
      url, options.method, options.credentials, options.headers.get('X-XSRF-TOKEN'), options.body,
    ])).toEqual([
      [detailUrl, 'DELETE', 'include', 'token', undefined],
      [detailUrl, 'DELETE', 'include', 'token', undefined],
    ])
  })

  const mutations = [
    ['POST', () => createCharacteristic({ nombre: 'Color' }), baseUrl],
    ['PUT', () => updateCharacteristic(id, { nombre: 'Tono' }), detailUrl],
    ['DELETE', () => deleteCharacteristic(id), detailUrl],
  ]

  it.each(mutations)('%s no reintenta ante 401', async (method, mutate, url) => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 'AUTH_REQUIRED' }, 401))
    vi.stubGlobal('fetch', fetchMock)
    await expect(mutate()).rejects.toMatchObject({ status: 401 })
    expect(fetchMock.mock.calls.filter(([endpoint]) => endpoint === url)).toHaveLength(1)
    expect(fetchMock.mock.calls.some(([endpoint]) => endpoint === '/api/auth/refresh')).toBe(false)
    expect(fetchMock.mock.calls[0][1].method).toBe(method)
  })

  it.each(mutations)('%s renueva CSRF para un intento manual pero no repite la mutación', async (method, mutate, url) => {
    const fetchMock = vi.fn().mockImplementation(async (endpoint) => endpoint === '/api/auth/csrf'
      ? new Response(null, { status: 204 })
      : jsonResponse({ code: 'CSRF_TOKEN_INVALID' }, 403))
    vi.stubGlobal('fetch', fetchMock)
    await expect(mutate()).rejects.toMatchObject({ status: 403 })
    expect(fetchMock.mock.calls.filter(([endpoint]) => endpoint === url)).toHaveLength(1)
    expect(fetchMock.mock.calls.filter(([endpoint]) => endpoint === '/api/auth/csrf')).toHaveLength(1)
    expect(fetchMock.mock.calls[0][1].method).toBe(method)
  })

  it.each(mutations)('%s no reintenta ante HTTP 5xx', async (method, mutate, url) => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ message: 'No disponible' }, 503))
    vi.stubGlobal('fetch', fetchMock)
    await expect(mutate()).rejects.toMatchObject({ status: 503 })
    expect(fetchMock.mock.calls.filter(([endpoint]) => endpoint === url)).toHaveLength(1)
    expect(fetchMock.mock.calls[0][1].method).toBe(method)
  })

  it.each(mutations)('%s no reintenta ante falla de red', async (method, mutate, url) => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('offline'))
    vi.stubGlobal('fetch', fetchMock)
    await expect(mutate()).rejects.toMatchObject({ status: 0 })
    expect(fetchMock.mock.calls.filter(([endpoint]) => endpoint === url)).toHaveLength(1)
    expect(fetchMock.mock.calls[0][1].method).toBe(method)
  })
})
