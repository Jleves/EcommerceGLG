import { expect, it, vi } from 'vitest'
import { apiRequest } from '../services/apiClient.js'
import { getPublicOffers } from './publicService.js'

vi.mock('../services/apiClient.js', () => ({ apiRequest: vi.fn() }))

it('consulta la página pública de ofertas sin autenticación y admite cancelación', async () => {
  const signal = new AbortController().signal
  const page = { content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 }
  apiRequest.mockResolvedValueOnce(page)
  await expect(getPublicOffers(2, 20, { signal })).resolves.toEqual(page)
  expect(apiRequest).toHaveBeenCalledWith('/api/public/offers?page=2&size=20', { retryAuth: false, signal })
})
