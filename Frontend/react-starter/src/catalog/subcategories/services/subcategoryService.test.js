import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetApiClientForTests } from '../../../services/apiClient.js'
import { createSubcategory, deleteSubcategory, getSubcategory, listSubcategories, updateSubcategory } from './subcategoryService.js'

beforeEach(() => __resetApiClientForTests())
afterEach(() => vi.unstubAllGlobals())

describe('subcategoryService', () => {
  it('usa rutas y payloads del contrato con cookies y CSRF', async () => {
    document.cookie = 'XSRF-TOKEN=token; Path=/'
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { headers: { 'Content-Type': 'application/json' } })))
    vi.stubGlobal('fetch', fetchMock)
    await listSubcategories(2)
    await getSubcategory(5)
    await createSubcategory({ categoriaId: 2, nombre: 'Chapas', descripcion: '' })
    await updateSubcategory(5, { nombre: 'Perfiles', descripcion: 'Metales' })
    await deleteSubcategory(5)
    expect(fetchMock.mock.calls.map(([url, options]) => [url, options.method])).toEqual([
      ['/api/catalog/subcategories?categoriaId=2', 'GET'],
      ['/api/catalog/subcategories/5', 'GET'],
      ['/api/catalog/subcategories', 'POST'],
      ['/api/catalog/subcategories/5', 'PUT'],
      ['/api/catalog/subcategories/5', 'DELETE'],
    ])
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({ categoriaId: 2, nombre: 'Chapas', descripcion: '' })
    expect(JSON.parse(fetchMock.mock.calls[3][1].body)).toEqual({ nombre: 'Perfiles', descripcion: 'Metales' })
    for (const [, options] of fetchMock.mock.calls.slice(2)) {
      expect(options.credentials).toBe('include')
      expect(options.headers.get('X-XSRF-TOKEN')).toBe('token')
    }
    document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
  })

  it('propaga errores HTTP de mutación', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Duplicada' }), { status: 409, headers: { 'Content-Type': 'application/json' } })))
    await expect(createSubcategory({ categoriaId: 1, nombre: 'Chapas' })).rejects.toMatchObject({ status: 409 })
  })
})
