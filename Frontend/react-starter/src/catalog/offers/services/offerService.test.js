import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../../services/apiClient.js'
import { createOffer, deleteOffer, listOffers, listOffersByProducts, updateOffer } from './offerService.js'

vi.mock('../../../services/apiClient.js', () => ({ apiRequest: vi.fn() }))

describe('offerService', () => {
  beforeEach(() => vi.resetAllMocks())

  it('usa las consultas privadas y pagina a 20', async () => {
    await listOffers(2, { signal: 'signal' })
    await listOffersByProducts([7, 9], { signal: 'signal' })
    await listOffersByProducts([])
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/catalog/offers?page=2&size=20', { signal: 'signal' })
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/catalog/offers/by-products?productIds=7&productIds=9', { signal: 'signal' })
    expect(apiRequest).toHaveBeenCalledTimes(2)
  })

  it('envía verbos y cuerpos correctos sin reintentos de mutaciones', async () => {
    await createOffer(11, '80.00')
    await updateOffer(3, '70.00')
    await deleteOffer(3)
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/catalog/offers', { method: 'POST', body: { variantId: 11, precioOferta: '80.00' }, retryAuth: false, retryCsrf: false })
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/catalog/offers/3', { method: 'PUT', body: { precioOferta: '70.00' }, retryAuth: false, retryCsrf: false })
    expect(apiRequest).toHaveBeenNthCalledWith(3, '/api/catalog/offers/3', { method: 'DELETE', expectedStatus: 204, retryAuth: false, retryCsrf: false })
  })
})
