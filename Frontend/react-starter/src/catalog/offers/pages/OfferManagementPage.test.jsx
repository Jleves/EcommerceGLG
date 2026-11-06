import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getProduct, listProducts } from '../../products/services/productService.js'
import { createOffer, deleteOffer, listOffers, listOffersByProducts } from '../services/offerService.js'
import { OfferManagementPage } from './OfferManagementPage.jsx'

vi.mock('../../products/services/productService.js', () => ({ getProduct: vi.fn(), listProducts: vi.fn() }))
vi.mock('../services/offerService.js', () => ({ createOffer: vi.fn(), updateOffer: vi.fn(), deleteOffer: vi.fn(), listOffers: vi.fn(), listOffersByProducts: vi.fn() }))

const offer = { id: 8, productoId: 1, productoNombre: 'Arena', varianteId: 11, precioReferencia: 100, precioOferta: 80, porcentajeDescuento: 20, productoActivo: false, varianteActiva: true, disponible: true }
const page = (content, index = 0, totalPages = 1) => ({ content, page: index, size: 20, totalPages, totalElements: content.length })

function renderPage() {
  return render(<MemoryRouter initialEntries={['/app/catalog/offers']}><Routes><Route path="/app/catalog/offers" element={<OfferManagementPage />} /></Routes></MemoryRouter>)
}

describe('OfferManagementPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listOffers.mockResolvedValue(page([offer]))
    listProducts.mockResolvedValue(page([{ id: 1, nombre: 'Arena', activo: true, variantes: [{ id: 11, activo: true }] }]))
    getProduct.mockResolvedValue({ id: 1, nombre: 'Arena', activo: true, variantes: [{ id: 11, activo: true, disponible: true, precioReferencia: 100 }] })
    listOffersByProducts.mockResolvedValue([])
  })

  it('lista ofertas ocultas, pagina y termina con confirmación', async () => {
    const user = userEvent.setup()
    let deleted = false
    listOffers.mockImplementation(async (index) => deleted ? page([]) : index === 0 ? page([offer], 0, 2) : page([], 1, 2))
    deleteOffer.mockImplementation(async () => { deleted = true; return null })
    renderPage()
    expect(await screen.findByText(/Oculta temporalmente/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(listOffers).toHaveBeenCalledWith(1, { signal: expect.any(AbortSignal) }))
    await user.click(screen.getByRole('button', { name: 'Anterior' }))
    await screen.findByText(/Oculta temporalmente/)
    await user.click(screen.getByRole('button', { name: 'Terminar' }))
    expect(deleteOffer).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Confirmar terminación' }))
    await waitFor(() => expect(deleteOffer).toHaveBeenCalledWith(8))
    expect(await screen.findByText('No hay ofertas en esta página.')).toBeInTheDocument()
  })

  it('permite elegir producto activo y crear una oferta', async () => {
    const user = userEvent.setup()
    listOffers.mockResolvedValue(page([]))
    createOffer.mockResolvedValue({ id: 9, varianteId: 11 })
    renderPage()
    await screen.findByText('No hay ofertas en esta página.')
    await user.click(screen.getByRole('button', { name: 'Crear oferta' }))
    await user.click(await screen.findByRole('button', { name: 'Elegir' }))
    await screen.findByText(/Arena · ID 1/)
    await user.type(screen.getByRole('textbox', { name: 'Precio final de oferta' }), '70')
    await user.click(within(screen.getByRole('dialog', { name: 'Gestionar oferta' })).getByRole('button', { name: 'Crear oferta' }))
    await waitFor(() => expect(createOffer).toHaveBeenCalledWith(11, '70'))
  })

  it('no repite una baja incierta antes de consultar la lista', async () => {
    const user = userEvent.setup()
    deleteOffer.mockRejectedValue(new Error('sin respuesta'))
    renderPage()
    await screen.findByText(/Oculta temporalmente/)
    await user.click(screen.getByRole('button', { name: 'Terminar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar terminación' }))
    expect(await screen.findByRole('button', { name: 'Consultar estado actual' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar terminación' })).toBeDisabled()
    expect(deleteOffer).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Consultar estado actual' }))
    await waitFor(() => expect(listOffers).toHaveBeenCalledTimes(2))
  })
})
