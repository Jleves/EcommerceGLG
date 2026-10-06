import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetApiClientForTests } from '../../../services/apiClient.js'
import { listProducts } from './productService.js'

function jsonResponse(data) {
  return new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } })
}

beforeEach(() => __resetApiClientForTests())
afterEach(() => vi.unstubAllGlobals())

describe('productService', () => {
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
})
