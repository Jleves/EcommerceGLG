import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRouter } from '../app/AppRouter.jsx'
import { getPublicCatalog, getPublicProducts, getPublicProduct, getRelatedProducts } from './publicService.js'

vi.mock('./publicService.js', () => ({ getPublicCatalog: vi.fn(), getPublicProducts: vi.fn(), getPublicProduct: vi.fn(), getRelatedProducts: vi.fn() }))

const catalog = { categories: [{ id: 3, nombre: 'Materiales', descripcion: 'Cemento y áridos', icono: 'materiales', activo: true }, { id: 4, nombre: 'Herramientas', descripcion: '', icono: 'tools', activo: true }], subcategories: [{ id: 11, categoriaId: 3, nombre: 'Cementos' }], facets: [{ id: 8, name: 'Marca', values: ['Acme'] }, { id: 9, name: 'Color', values: ['Gris'] }] }
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

  it('despliega categorías y subcategorías en escritorio y filtra al navegar', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    const nav = screen.getByRole('navigation', { name: 'Navegación pública' })
    fireEvent.mouseEnter(within(nav).getByRole('link', { name: 'Categorías' }))
    expect(within(nav).queryByRole('link', { name: 'Todas las categorías' })).not.toBeInTheDocument()
    fireEvent.mouseEnter(await within(nav).findByRole('link', { name: 'Materiales' }))
    const subcategory = within(nav).getByRole('link', { name: 'Cementos' })
    expect(subcategory).toHaveAttribute('href', '/catalog?categoriaId=3&subcategoriaId=11')
    subcategory.focus()
    await user.keyboard('{Escape}')
    expect(within(nav).queryByRole('link', { name: 'Cementos' })).not.toBeInTheDocument()
    fireEvent.mouseEnter(within(nav).getByRole('link', { name: 'Categorías' }))
    fireEvent.mouseEnter(await within(nav).findByRole('link', { name: 'Materiales' }))
    await user.click(within(nav).getByRole('link', { name: 'Cementos' }))
    await waitFor(() => expect(getPublicProducts).toHaveBeenCalledWith(expect.objectContaining({ categoriaId: '3', subcategoriaId: '11' }), expect.anything()))
  })

  it('mantiene abierto el desplegable al pulsar su flecha después del hover', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    const nav = screen.getByRole('navigation', { name: 'Navegación pública' })
    fireEvent.mouseEnter(within(nav).getByRole('link', { name: 'Categorías' }))
    await within(nav).findByRole('link', { name: 'Materiales' })
    await user.click(within(nav).getByRole('button', { name: 'Desplegar categorías' }))
    expect(within(nav).getByRole('button', { name: 'Desplegar categorías' })).toHaveAttribute('aria-expanded', 'true')
  })

  it('abre el panel móvil, expande una categoría completa y cierra con Escape', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    await user.click(screen.getByRole('button', { name: 'Abrir menú de categorías' }))
    const dialog = screen.getByRole('dialog', { name: 'Menú de categorías' })
    await user.click(within(dialog).getByRole('button', { name: 'Materiales' }))
    expect(within(dialog).getByRole('link', { name: 'Ver todos' })).toHaveAttribute('href', '/catalog?categoriaId=3')
    expect(within(dialog).getByRole('link', { name: 'Cementos' })).toHaveAttribute('href', '/catalog?categoriaId=3&subcategoriaId=11')
    expect(within(dialog).getByRole('link', { name: 'Herramientas' })).toHaveAttribute('href', '/catalog?categoriaId=4')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Menú de categorías' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Abrir menú de categorías' }))
    await user.click(screen.getByRole('button', { name: 'Cerrar menú de categorías' }))
    expect(screen.queryByRole('dialog', { name: 'Menú de categorías' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Abrir menú de categorías' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Menú de categorías' })).getByRole('button', { name: 'Materiales' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Menú de categorías' })).getByRole('link', { name: 'Cementos' }))
    expect(screen.queryByRole('dialog', { name: 'Menú de categorías' })).not.toBeInTheDocument()
    await waitFor(() => expect(getPublicProducts).toHaveBeenCalledWith(expect.objectContaining({ categoriaId: '3', subcategoriaId: '11' }), expect.anything()))
  })

  it('muestra el filtro de subcategoría del enlace directo y lo limpia al cambiar el padre', async () => {
    const user = userEvent.setup()
    renderRoute('/catalog?categoriaId=3&subcategoriaId=11')
    expect(await screen.findByLabelText('Subcategoría')).toHaveValue('11')
    await waitFor(() => expect(getPublicProducts).toHaveBeenCalledWith(expect.objectContaining({ categoriaId: '3', subcategoriaId: '11' }), expect.anything()))
    await user.selectOptions(screen.getByLabelText('Categoría'), '4')
    await waitFor(() => expect(getPublicProducts).toHaveBeenLastCalledWith(expect.objectContaining({ categoriaId: '4', subcategoriaId: '' }), expect.anything()))
    expect(screen.queryByLabelText('Subcategoría')).not.toBeInTheDocument()
  })

  it('explica los estados de carga y error dentro de la navegación', async () => {
    getPublicCatalog.mockReturnValueOnce(new Promise(() => {}))
    const first = renderRoute('/')
    const nav = screen.getByRole('navigation', { name: 'Navegación pública' })
    fireEvent.mouseEnter(within(nav).getByRole('link', { name: 'Categorías' }))
    expect(within(nav).getByRole('status')).toHaveTextContent('Cargando categorías')
    first.unmount()

    getPublicCatalog.mockRejectedValueOnce(new Error('network'))
    renderRoute('/')
    const nextNav = screen.getByRole('navigation', { name: 'Navegación pública' })
    fireEvent.mouseEnter(within(nextNav).getByRole('link', { name: 'Categorías' }))
    expect(await within(nextNav).findByRole('alert')).toHaveTextContent('No pudimos cargar las categorías')
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
