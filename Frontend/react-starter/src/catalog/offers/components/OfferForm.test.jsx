import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getProduct } from '../../products/services/productService.js'
import { createOffer, listOffersByProducts, updateOffer } from '../services/offerService.js'
import { OfferForm } from './OfferForm.jsx'

vi.mock('../../products/services/productService.js', () => ({ getProduct: vi.fn() }))
vi.mock('../services/offerService.js', () => ({ createOffer: vi.fn(), updateOffer: vi.fn(), listOffersByProducts: vi.fn() }))

const product = { id: 1, nombre: 'Arena', activo: true, variantes: [
  { id: 11, precioReferencia: '100.00', activo: true, disponible: false },
  { id: 12, precioReferencia: '200.00', activo: true, disponible: true },
] }
const offer = { id: 8, productoId: 1, varianteId: 12, precioOferta: '150.00', productoActivo: true, varianteActiva: true }

describe('OfferForm', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    getProduct.mockResolvedValue(product)
    listOffersByProducts.mockResolvedValue([])
  })

  it('crea para la variante elegida y rechaza precio igual al base', async () => {
    const user = userEvent.setup()
    const saved = vi.fn()
    createOffer.mockResolvedValue({ id: 4, varianteId: 12 })
    render(<OfferForm productId={1} onSaved={saved} onCancel={vi.fn()} />)
    await screen.findByText(/Arena · ID 1/)
    await user.selectOptions(screen.getByRole('combobox', { name: 'Variante' }), '12')
    await user.type(screen.getByRole('textbox', { name: 'Precio final de oferta' }), '200')
    await user.click(screen.getByRole('button', { name: 'Crear oferta' }))
    expect(screen.getByRole('alert')).toHaveTextContent('menor que el precio de referencia')
    expect(createOffer).not.toHaveBeenCalled()
    await user.clear(screen.getByRole('textbox', { name: 'Precio final de oferta' }))
    await user.type(screen.getByRole('textbox', { name: 'Precio final de oferta' }), '180.50')
    await user.click(screen.getByRole('button', { name: 'Crear oferta' }))
    await waitFor(() => expect(saved).toHaveBeenCalledOnce())
    expect(createOffer).toHaveBeenCalledWith(12, '180.50')
  })

  it('precarga oferta existente y permite editar aunque el producto esté inactivo', async () => {
    const user = userEvent.setup()
    getProduct.mockResolvedValue({ ...product, activo: false })
    listOffersByProducts.mockResolvedValue([{ ...offer, productoActivo: false }])
    updateOffer.mockResolvedValue({ id: 8, varianteId: 12 })
    render(<OfferForm productId={1} initialVariantId={12} onSaved={vi.fn()} onCancel={vi.fn()} />)
    expect(await screen.findByText(/Oculta temporalmente/)).toBeInTheDocument()
    const input = screen.getByRole('textbox', { name: 'Precio final de oferta' })
    expect(input).toHaveValue('150.00')
    await user.clear(input)
    await user.type(input, '140.00')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(updateOffer).toHaveBeenCalledWith(8, '140.00'))
  })

  it('bloquea repetición tras resultado incierto hasta consultar estado', async () => {
    const user = userEvent.setup()
    createOffer.mockRejectedValueOnce(new Error('sin respuesta'))
    render(<OfferForm productId={1} onSaved={vi.fn()} onCancel={vi.fn()} />)
    await screen.findByText(/Arena · ID 1/)
    await user.type(screen.getByRole('textbox', { name: 'Precio final de oferta' }), '80')
    await user.click(screen.getByRole('button', { name: 'Crear oferta' }))
    expect(await screen.findByRole('button', { name: 'Consultar estado actual' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear oferta' })).toBeDisabled()
    expect(createOffer).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Consultar estado actual' }))
    await waitFor(() => expect(getProduct).toHaveBeenCalledTimes(2))
  })

  it('mantiene el formulario tras 409 y consulta datos actuales', async () => {
    const user = userEvent.setup()
    createOffer.mockRejectedValue({ status: 409, message: 'La variante ya tiene una oferta.' })
    render(<OfferForm productId={1} onSaved={vi.fn()} onCancel={vi.fn()} />)
    await screen.findByText(/Arena · ID 1/)
    await user.type(screen.getByRole('textbox', { name: 'Precio final de oferta' }), '80')
    await user.click(screen.getByRole('button', { name: 'Crear oferta' }))
    expect(await screen.findByText('La variante ya tiene una oferta.')).toBeInTheDocument()
    await waitFor(() => expect(getProduct).toHaveBeenCalledTimes(2))
  })

  it('muestra 400 sin repetir el envío ni exigir consulta', async () => {
    const user = userEvent.setup()
    createOffer.mockRejectedValue({ status: 400, message: 'Precio inválido.' })
    render(<OfferForm productId={1} onSaved={vi.fn()} onCancel={vi.fn()} />)
    await screen.findByText(/Arena · ID 1/)
    await user.type(screen.getByRole('textbox', { name: 'Precio final de oferta' }), '80')
    await user.click(screen.getByRole('button', { name: 'Crear oferta' }))
    expect(await screen.findByText('Precio inválido.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Consultar estado actual' })).not.toBeInTheDocument()
    expect(createOffer).toHaveBeenCalledTimes(1)
  })

  it('bloquea el doble envío mientras espera la respuesta', async () => {
    const user = userEvent.setup()
    let finish
    createOffer.mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    render(<OfferForm productId={1} onSaved={vi.fn()} onCancel={vi.fn()} />)
    await screen.findByText(/Arena · ID 1/)
    await user.type(screen.getByRole('textbox', { name: 'Precio final de oferta' }), '80')
    await user.click(screen.getByRole('button', { name: 'Crear oferta' }))
    expect(screen.getByRole('button', { name: 'Guardando…' })).toBeDisabled()
    expect(createOffer).toHaveBeenCalledTimes(1)
    finish({ id: 4, varianteId: 11 })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Crear oferta' })).toBeEnabled())
  })
})
