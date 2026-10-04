import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetApiClientForTests } from '../../../services/apiClient.js'
import {
  createCategory,
  deactivateCategory,
  listCategories,
  reactivateCategory,
  updateCategory,
} from './categoryService.js'

const category = { id: 7, nombre: 'Plomería', descripcion: 'Caños y conexiones', icono: 'plomeria', activo: true }
const form = { nombre: category.nombre, descripcion: category.descripcion, icono: category.icono }

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  __resetApiClientForTests()
  document.cookie = 'XSRF-TOKEN=category-csrf; Path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
})

describe('categoryService', () => {
  it('lista categorías con GET autenticado, conserva la respuesta y acepta AbortSignal', async () => {
    const categories = [category, { ...category, id: 8, nombre: 'Pintura', activo: false }]
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(categories))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()

    await expect(listCategories({ signal: controller.signal })).resolves.toEqual(categories)

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/catalog/categories')
    expect(options.method).toBe('GET')
    expect(options.credentials).toBe('include')
    expect(options.body).toBeUndefined()
    expect(options.signal).toBe(controller.signal)
  })

  it.each([
    ['crear', () => createCategory({ ...form, id: 99, activo: false }), '/api/catalog/categories', 'POST', 201],
    ['editar', () => updateCategory(7, { ...form, id: 99, activo: false }), '/api/catalog/categories/7', 'PUT', 200],
  ])('%s envía solo los tres campos editables con cookies y CSRF', async (_name, action, endpoint, method, status) => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(category, status))
    vi.stubGlobal('fetch', fetchMock)

    await expect(action()).resolves.toEqual(category)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe(endpoint)
    expect(options.method).toBe(method)
    expect(options.credentials).toBe('include')
    expect(options.headers.get('X-XSRF-TOKEN')).toBe('category-csrf')
    expect(options.headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(options.body)).toEqual(form)
  })

  it.each([
    ['desactivar', deactivateCategory, '/api/catalog/categories/7/deactivate', false],
    ['reactivar', reactivateCategory, '/api/catalog/categories/7/reactivate', true],
  ])('%s usa POST sin body y devuelve el estado del servidor', async (_name, action, endpoint, activo) => {
    const response = { ...category, activo }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(response))
    vi.stubGlobal('fetch', fetchMock)

    await expect(action(7)).resolves.toEqual(response)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe(endpoint)
    expect(options.method).toBe('POST')
    expect(options.credentials).toBe('include')
    expect(options.headers.get('X-XSRF-TOKEN')).toBe('category-csrf')
    expect(options.body).toBeUndefined()
  })

  const mutations = [
    ['alta', () => createCategory(form)],
    ['edición', () => updateCategory(7, form)],
    ['desactivación', () => deactivateCategory(7)],
    ['reactivación', () => reactivateCategory(7)],
  ]

  it.each(mutations)('%s no reintenta ante 401 ni renueva la sesión', async (_name, action) => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 'AUTH_TOKEN_EXPIRED' }, 401))
    vi.stubGlobal('fetch', fetchMock)

    await expect(action()).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).not.toBe('/api/auth/refresh')
  })

  it.each(mutations)('%s no reintenta tras un CSRF inválido', async (_name, action) => {
    const fetchMock = vi.fn().mockImplementation(async (url) => url === '/api/auth/csrf'
      ? new Response(null, { status: 204 })
      : jsonResponse({ code: 'CSRF_TOKEN_INVALID' }, 403))
    vi.stubGlobal('fetch', fetchMock)

    await expect(action()).rejects.toMatchObject({ status: 403, code: 'CSRF_TOKEN_INVALID' })
    expect(fetchMock.mock.calls.filter(([url]) => url.startsWith('/api/catalog/categories'))).toHaveLength(1)
    expect(fetchMock.mock.calls.some(([url]) => url === '/api/auth/refresh')).toBe(false)
  })

  it.each(mutations)('%s no reintenta ante 5xx', async (_name, action) => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 'INTERNAL_ERROR' }, 503))
    vi.stubGlobal('fetch', fetchMock)

    await expect(action()).rejects.toMatchObject({ status: 503 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('no repite el alta cuando falla la conexión', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('network'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createCategory(form)).rejects.toMatchObject({ status: 0 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
