import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetApiClientForTests } from '../../../services/apiClient.js'
import { createProduct, deactivateProduct, deleteProductImage, getProduct, listProductImages, listProducts, reactivateProduct, reorderProductImages, replaceProductImage, setPrimaryProductImage, updateProduct, uploadProductImage } from './productService.js'
import { listCharacteristics } from '../../characteristics/services/characteristicService.js'

function jsonResponse(data) {
  return new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } })
}

beforeEach(() => __resetApiClientForTests())
afterEach(() => vi.unstubAllGlobals())

describe('productService', () => {
  it('combina filtros de clasificación y nombre con la página', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ content: [] }))
    vi.stubGlobal('fetch', fetchMock)
    await listProducts(2, 'cal', { categoriaId: 3, subcategoriaId: 9 })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/catalog/products?page=2&size=20&nombre=cal&categoriaId=3&subcategoriaId=9')
  })
  it('omite nombre sin filtro y envía página, tamaño, cookies y señal', async () => {
    const page = { content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()

    await expect(listProducts(2, undefined, { signal: controller.signal })).resolves.toEqual(page)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/catalog/products?page=2&size=20')
    expect(options.method).toBe('GET')
    expect(options.credentials).toBe('include')
    expect(options.signal).toBe(controller.signal)
  })

  it('codifica el nombre de búsqueda sin perder caracteres especiales', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ content: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await listProducts(0, 'Caño 1%_')
    const url = fetchMock.mock.calls[0][0]
    expect(url).toBe('/api/catalog/products?page=0&size=20&nombre=Ca%C3%B1o+1%25_')
  })

  it('lee detalle y características y envía POST/PUT con cookies y CSRF sin reintentos', async () => {
    document.cookie = 'XSRF-TOKEN=token; Path=/'
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    await getProduct(7)
    await listCharacteristics()
    await createProduct({ nombre: 'Cal', caracteristicas: [] })
    await updateProduct(7, { nombre: 'Cal nueva', caracteristicas: [] })
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/catalog/products/7', '/api/catalog/characteristics', '/api/catalog/products', '/api/catalog/products/7',
    ])
    expect(fetchMock.mock.calls.slice(2).map(([, options]) => [options.method, options.credentials, options.headers.get('X-XSRF-TOKEN')])).toEqual([
      ['POST', 'include', 'token'], ['PUT', 'include', 'token'],
    ])
    expect(JSON.parse(fetchMock.mock.calls[3][1].body)).toEqual({ nombre: 'Cal nueva', caracteristicas: [] })
    document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
  })

  it('envía ambas transiciones con cookies y CSRF al endpoint correcto', async () => {
    document.cookie = 'XSRF-TOKEN=token; Path=/'
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ activo: false }))
    vi.stubGlobal('fetch', fetchMock)
    await deactivateProduct(7)
    await reactivateProduct(7)
    expect(fetchMock.mock.calls.map(([url, options]) => [url, options.method, options.credentials, options.headers.get('X-XSRF-TOKEN')])).toEqual([
      ['/api/catalog/products/7/deactivate', 'POST', 'include', 'token'],
      ['/api/catalog/products/7/reactivate', 'POST', 'include', 'token'],
    ])
    document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
  })

  it.each([
    [401, { message: 'Sesión vencida' }],
    [403, { code: 'CSRF_TOKEN_INVALID', message: 'CSRF inválido' }],
    [503, { message: 'Servidor no disponible' }],
  ])('no repite la transición ante HTTP %s', async (status, data) => {
    document.cookie = 'XSRF-TOKEN=token; Path=/'
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(deactivateProduct(7)).rejects.toMatchObject({ status })
    expect(fetchMock).toHaveBeenCalledTimes(status === 403 ? 2 : 1)
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/catalog/products/7/deactivate')).toHaveLength(1)
    document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
  })

  it('no repite la transición ante falla de red', async () => {
    document.cookie = 'XSRF-TOKEN=token; Path=/'
    const fetchMock = vi.fn().mockRejectedValue(new Error('offline'))
    vi.stubGlobal('fetch', fetchMock)
    await expect(reactivateProduct(7)).rejects.toMatchObject({ status: 0 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
  })

  it('envía las seis operaciones de imágenes al contrato existente, con multipart y CSRF', async () => {
    document.cookie = 'XSRF-TOKEN=token; Path=/'
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['imagen'], 'foto.png', { type: 'image/png' })

    await listProductImages(7)
    await uploadProductImage(7, file)
    await setPrimaryProductImage(7, 9)
    await reorderProductImages(7, [9, 8])
    await replaceProductImage(7, 9, file)
    await deleteProductImage(7, 9)

    expect(fetchMock.mock.calls.map(([url, options]) => [url, options.method])).toEqual([
      ['/api/catalog/products/7/images', 'GET'],
      ['/api/catalog/products/7/images', 'POST'],
      ['/api/catalog/products/7/images/9/primary', 'PUT'],
      ['/api/catalog/products/7/images/order', 'PUT'],
      ['/api/catalog/products/7/images/9', 'PUT'],
      ['/api/catalog/products/7/images/9', 'DELETE'],
    ])
    for (const index of [1, 4]) {
      const options = fetchMock.mock.calls[index][1]
      expect(options.body).toBeInstanceOf(FormData)
      expect(options.body.get('file')).toBe(file)
      expect(options.headers.has('Content-Type')).toBe(false)
    }
    expect(JSON.parse(fetchMock.mock.calls[3][1].body)).toEqual({ imageIds: [9, 8] })
    for (const [, options] of fetchMock.mock.calls.slice(1)) {
      expect(options.headers.get('X-XSRF-TOKEN')).toBe('token')
      expect(options.credentials).toBe('include')
    }
    document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
  })
})
