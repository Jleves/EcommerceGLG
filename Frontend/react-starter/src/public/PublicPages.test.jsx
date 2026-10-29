import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRouter } from '../app/AppRouter.jsx'
import { getPublicCatalog, getPublicProducts, getPublicProduct, getRelatedProducts } from './publicService.js'

vi.mock('./publicService.js', () => ({ getPublicCatalog: vi.fn(), getPublicProducts: vi.fn(), getPublicProduct: vi.fn(), getRelatedProducts: vi.fn() }))

const catalog = { categories: [{ id: 3, nombre: 'Materiales', descripcion: 'Cemento y áridos', icono: 'materiales', activo: true }], facets: [{ id: 8, name: 'Marca', values: ['Acme'] }, { id: 9, name: 'Color', values: ['Gris'] }] }
const product = { id: 7, nombre: 'Cemento Portland', categoriaId: 3, precioReferencia: 1200, disponible: true, primaryThumbnailUrl: null, selectedVariantId: 12 }

function renderRoute(path) { return render(<MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>) }

describe('catálogo público', () => {
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })
  beforeEach(() => {
    vi.clearAllMocks()
    getPublicCatalog.mockResolvedValue(catalog)
    getPublicProducts.mockResolvedValue({ content: [product], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    getRelatedProducts.mockResolvedValue({ content: [] })
    getPublicProduct.mockResolvedValue({ ...product, descripcion: 'Para obra', images: [], caracteristicas: [], variantes: [{ id: 12, precioReferencia: 1200, disponible: true, caracteristicaNombre: 'Color', valor: 'Gris' }, { id: 13, precioReferencia: 1500, disponible: false, caracteristicaNombre: 'Color', valor: 'Blanco' }] })
  })

  it('muestra el home público y solo enlaces listos', async () => {
    renderRoute('/')
    expect(screen.getByRole('heading', { name: 'Los materiales de tu próxima historia.' })).toBeInTheDocument()
    expect((await screen.findAllByRole('link', { name: /Materiales/ }))[0]).toHaveAttribute('href', '/catalog?categoriaId=3')
    expect(screen.queryByText('Ofertas vigentes')).not.toBeInTheDocument()
    expect(screen.queryByText('Mi carrito')).not.toBeInTheDocument()
    expect(screen.queryByText('Envíos')).not.toBeInTheDocument()
  })

  it('escribe el título en dos tramos con una pausa y mantiene su nombre accesible', () => {
    vi.useFakeTimers()
    renderRoute('/')
    const heading = screen.getByRole('heading', { name: 'Los materiales de tu próxima historia.' })
    const typed = heading.querySelector('.hero-title-typed')
    act(() => vi.advanceTimersByTime(630))
    expect(typed).toHaveTextContent('Los materiales')
    act(() => vi.advanceTimersByTime(649))
    expect(typed.textContent).toBe('Los materiales')
    act(() => vi.advanceTimersByTime(3000))
    expect(typed.textContent).toBe('Los materiales de tu próxima historia.')
  })

  it('muestra el título completo sin animación cuando se prefiere movimiento reducido', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    renderRoute('/')
    const heading = screen.getByRole('heading', { name: 'Los materiales de tu próxima historia.' })
    expect(heading.querySelector('.hero-title-typed').textContent).toBe('Los materiales de tu próxima historia.')
  })

  it('muestra hasta ocho categorías en el carril y conserva el enlace al catálogo completo', async () => {
    getPublicCatalog.mockResolvedValue({ ...catalog, categories: Array.from({ length: 9 }, (_, index) => ({
      id: index + 1, nombre: `Categoría ${index + 1}`, descripcion: '', icono: 'materiales', activo: true,
    })) })
    renderRoute('/')
    const rail = await screen.findByLabelText('Categorías disponibles')
    const links = within(rail).getAllByRole('link')
    expect(links).toHaveLength(8)
    expect(links[0]).toHaveAttribute('href', '/catalog?categoriaId=1')
    expect(links[7]).toHaveAttribute('href', '/catalog?categoriaId=8')
    expect(screen.getByRole('link', { name: /Ver todas las categorías/ })).toHaveAttribute('href', '/catalog')
    expect(screen.getByRole('button', { name: 'Categorías siguientes' })).toBeInTheDocument()
  })

  it('combina búsqueda y filtros públicos desde la URL', async () => {
    const user = userEvent.setup()
    renderRoute('/catalog?q=cemento&categoriaId=3')
    await screen.findByText('Cemento Portland')
    expect(getPublicProducts).toHaveBeenCalledWith(expect.objectContaining({ q: 'cemento', categoriaId: '3' }), expect.anything())
    await user.selectOptions(screen.getByLabelText('Marca'), 'Acme')
    await waitFor(() => expect(getPublicProducts).toHaveBeenLastCalledWith(expect.objectContaining({ marca: 'Acme', q: 'cemento', categoriaId: '3' }), expect.anything()))
    await user.selectOptions(screen.getByLabelText('Color'), 'Gris')
    await waitFor(() => expect(getPublicProducts).toHaveBeenLastCalledWith(expect.objectContaining({ atributos: ['9:Gris'] }), expect.anything()))
  })

  it('explica cuándo la búsqueda no encuentra productos', async () => {
    getPublicProducts.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    renderRoute('/catalog?q=sin-coincidencias')
    expect(await screen.findByText(/No encontramos productos con esos criterios/)).toBeInTheDocument()
  })

  it('conserva la variante seleccionada y actualiza precio y disponibilidad', async () => {
    const user = userEvent.setup()
    renderRoute('/products/7?variantId=12')
    expect(await screen.findByRole('heading', { name: 'Cemento Portland' })).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /Blanco/ }))
    expect(screen.getByText('No disponible')).toBeInTheDocument()
    expect(screen.getByText(/1.500/)).toBeInTheDocument()
  })
})
