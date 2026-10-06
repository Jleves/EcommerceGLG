import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listProducts } from '../services/productService.js'
import { ProductManagementPage } from './ProductManagementPage.jsx'

vi.mock('../services/productService.js', () => ({ listProducts: vi.fn() }))

const active = { id: 1, nombre: 'Arena', categoriaId: 3, descripcion: 'Fina', precioReferencia: 250, disponible: true, activo: true, destacado: false }
const inactive = { id: 2, nombre: 'Cemento', categoriaId: 4, descripcion: '', precioReferencia: 500, disponible: false, activo: false, destacado: true }
const makePage = (page, content = [], totalPages = 2) => ({ content, page, size: 20, totalElements: 21, totalPages })

function Location() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}{location.search}</output>
}

function renderPage(path = '/app/catalog/products') {
  return render(<MemoryRouter initialEntries={[path]}><Location /><Routes><Route path="/app/catalog/products" element={<ProductManagementPage />} /></Routes></MemoryRouter>)
}

describe('ProductManagementPage', () => {
  beforeEach(() => vi.resetAllMocks())

  it('carga productos activos e inactivos, permite recorrer páginas y conserva el filtro', async () => {
    listProducts.mockResolvedValue(makePage(0, [active, inactive]))
      .mockResolvedValueOnce(makePage(0, [active, inactive])).mockResolvedValueOnce(makePage(1, []))
    renderPage('/app/catalog/products?nombre=arena&page=0')
    expect(screen.getByText('Cargando productos…')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Arena' })).toBeInTheDocument()
    expect(screen.getByText(/ID 1 · Categoría 3 · Activo · Disponible/)).toBeInTheDocument()
    expect(screen.getByText(/ID 2 · Categoría 4 · Inactivo · No disponible/)).toBeInTheDocument()
    expect(screen.getByText(/Precio de referencia: 500 · Destacado/)).toBeInTheDocument()
    expect(listProducts).toHaveBeenCalledWith(0, 'arena', { signal: expect.any(AbortSignal) })
    await userEvent.setup().click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(await screen.findByText('No hay productos en esta página.')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('nombre=arena&page=1')
    expect(listProducts).toHaveBeenLastCalledWith(1, 'arena', { signal: expect.any(AbortSignal) })
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Anterior' }))
    expect(screen.getByTestId('location')).toHaveTextContent('nombre=arena&page=0')
  })

  it('busca al enviar, recorta el nombre, reinicia página y limpia el filtro', async () => {
    listProducts.mockResolvedValue(makePage(2, [active], 4))
    const user = userEvent.setup()
    renderPage('/app/catalog/products?page=2')
    await screen.findByRole('heading', { name: 'Arena' })
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), '  Cemento  ')
    expect(listProducts).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Buscar' }))
    expect(screen.getByTestId('location')).toHaveTextContent('nombre=Cemento&page=0')
    await waitFor(() => expect(listProducts).toHaveBeenLastCalledWith(0, 'Cemento', { signal: expect.any(AbortSignal) }))
    await user.click(screen.getByRole('button', { name: 'Limpiar búsqueda' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/app/catalog/products?page=0')
    await waitFor(() => expect(listProducts).toHaveBeenLastCalledWith(0, undefined, { signal: expect.any(AbortSignal) }))
  })

  it('rechaza una búsqueda vacía sin alterar la URL ni repetir la consulta', async () => {
    listProducts.mockResolvedValue(makePage(0, [active]))
    renderPage()
    await screen.findByRole('heading', { name: 'Arena' })
    const user = userEvent.setup()
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), '   ')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Ingresá un nombre')
    expect(screen.getByTestId('location')).toHaveTextContent('/app/catalog/products')
    expect(screen.getByRole('heading', { name: 'Arena' })).toBeInTheDocument()
    expect(listProducts).toHaveBeenCalledTimes(1)
  })

  it('no consulta un enlace directo con nombre vacío y permite limpiarlo', async () => {
    listProducts.mockResolvedValue(makePage(0, []))
    renderPage('/app/catalog/products?nombre=%20%20&page=0')
    expect(screen.getByRole('alert')).toHaveTextContent('Ingresá un nombre')
    expect(listProducts).not.toHaveBeenCalled()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Limpiar búsqueda' }))
    await waitFor(() => expect(listProducts).toHaveBeenCalledWith(0, undefined, { signal: expect.any(AbortSignal) }))
  })

  it('muestra error y reintenta; descarta respuestas anteriores', async () => {
    let finishOld
    listProducts.mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve }))
      .mockRejectedValueOnce(new Error('Servidor no disponible'))
      .mockResolvedValueOnce(makePage(0, [inactive]))
    const user = userEvent.setup()
    renderPage()
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Cemento')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Servidor no disponible')
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('heading', { name: 'Cemento' })).toBeInTheDocument()
    await act(async () => finishOld(makePage(0, [active])))
    expect(screen.queryByRole('heading', { name: 'Arena' })).not.toBeInTheDocument()
  })
})
