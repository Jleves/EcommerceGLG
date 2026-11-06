import { apiRequest } from '../../../services/apiClient.js'
import { ENDPOINTS } from '../../../services/endpoints.js'

const mutationOptions = { retryAuth: false, retryCsrf: false }

export function listOffers(page = 0, { signal } = {}) {
  return apiRequest(`${ENDPOINTS.CATALOG.OFFERS}?${new URLSearchParams({ page: String(page), size: '20' })}`, { signal })
}

export function listOffersByProducts(productIds, { signal } = {}) {
  if (!productIds.length) return Promise.resolve([])
  const query = new URLSearchParams()
  productIds.forEach((id) => query.append('productIds', String(id)))
  return apiRequest(`${ENDPOINTS.CATALOG.OFFERS_BY_PRODUCTS}?${query}`, { signal })
}

export function createOffer(variantId, precioOferta) {
  return apiRequest(ENDPOINTS.CATALOG.OFFERS, { ...mutationOptions, method: 'POST', body: { variantId, precioOferta } })
}

export function updateOffer(id, precioOferta) {
  return apiRequest(ENDPOINTS.CATALOG.OFFER(id), { ...mutationOptions, method: 'PUT', body: { precioOferta } })
}

export function deleteOffer(id) {
  return apiRequest(ENDPOINTS.CATALOG.OFFER(id), { ...mutationOptions, method: 'DELETE', expectedStatus: 204 })
}
