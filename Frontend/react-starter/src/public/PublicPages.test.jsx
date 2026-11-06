import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRouter } from '../app/AppRouter.jsx'
import { getPublicCatalog, getPublicProducts, getPublicProduct, getRelatedProducts, getPublicOffers } from './publicService.js'

vi.mock('./publicService.js', () => ({ getPublicCatalog: vi.fn(), getPublicProducts: vi.fn(), getPublicProduct: vi.fn(), getRelatedProducts: vi.fn(), getPublicOffers: vi.fn() }))

const catalog = { categories: [{ id: 3, nombre: 'Materiales', descripcion: 'Cemento y áridos', icono: 'materiales', activo: true }, { id: 4, nombre: 'Herramientas', descripcion: '', icono: 'tools', activo: true }], subcategories: [{ id: 11, categoriaId: 3, nombre: 'Cementos' }], facets: [{ id: 8, name: 'Marca', values: ['Acme'] }, { id: 9, name: 'Color', values: ['Gris'] }] }
const product = { id: 7, nombre: 'Cemento Portland', categoriaId: 3, precioReferencia: 1200, disponible: true, primaryThumbnailUrl: null, selectedVariantId: 12 }

function renderRoute(path) { return render(<MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>) }

describe('catálogo público', () => {
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    getPublicCatalog.mockResolvedValue(catalog)
    getPublicProducts.mockResolvedValue({ content: [product], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    getPublicOffers.mockResolvedValue({ content: [], page: 0, size: 6, totalElements: 0, totalPages: 0 })
    getRelatedProducts.mockResolvedValue({ content: [] })
    getPublicProduct.mockResolvedValue({ ...product, descripcion: 'Para obra', images: [], caracteristicas: [], variantes: [{ id: 12, precioReferencia: 1200, disponible: true, caracteristicaNombre: 'Color', valor: 'Gris' }, { id: 13, precioReferencia: 1500, disponible: false, caracteristicaNombre: 'Color', valor: 'Blanco' }] })
  })

  it('muestra el home público y solo enlaces listos', async () => {
    const { container } = renderRoute('/')
    expect(screen.getByRole('heading', { name: 'Los materiales de tu próxima historia.' })).toBeInTheDocument()
    expect((await screen.findAllByRole('link', { name: /Materiales/ }))[0]).toHaveAttribute('href', '/catalog?categoriaId=3')
    expect(screen.getByRole('link', { name: /Ver todas las ofertas/ })).toHaveAttribute('href', '/ofertas')
    expect([...container.querySelectorAll('main > section')].map((section) => section.className).indexOf('home-offers'))
      .toBeLessThan([...container.querySelectorAll('main > section')].map((section) => section.className).indexOf('home-categories'))
    expect(screen.queryByText('Mi carrito')).not.toBeInTheDocument()
    expect(screen.queryByText('Envíos')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Enterate de lo nuevo en GLG' })).toBeInTheDocument()
    expect(container.querySelector('.home-brands')?.nextElementSibling).toHaveClass('home-subscriptions')
  })

  it('muestra el acceso flotante a WhatsApp solo en el home', () => {
    const home = renderRoute('/')
    expect(screen.getByRole('link', { name: 'Chatear por WhatsApp' })).toHaveAttribute('href', 'https://wa.me/5492984400705')
    home.unmount()

    const catalogPage = renderRoute('/catalog')
    expect(screen.queryByRole('link', { name: 'Chatear por WhatsApp' })).not.toBeInTheDocument()
    catalogPage.unmount()

    renderRoute('/products/7')
    expect(screen.queryByRole('link', { name: 'Chatear por WhatsApp' })).not.toBeInTheDocument()
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
    expect(screen.getAllByRole('search')).toHaveLength(1)
    expect(screen.getByRole('textbox', { name: 'Buscar productos o marcas' })).toHaveValue('cemento')
    expect(getPublicProducts).toHaveBeenCalledWith(expect.objectContaining({ q: 'cemento', categoriaId: '3' }), expect.anything())
    await user.selectOptions(screen.getByLabelText('Marca'), 'Acme')
    await waitFor(() => expect(getPublicProducts).toHaveBeenLastCalledWith(expect.objectContaining({ marca: 'Acme', q: 'cemento', categoriaId: '3' }), expect.anything()))
    await user.selectOptions(screen.getByLabelText('Color'), 'Gris')
    await waitFor(() => expect(getPublicProducts).toHaveBeenLastCalledWith(expect.objectContaining({ atributos: ['9:Gris'] }), expect.anything()))
    await user.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(screen.getByRole('textbox', { name: 'Buscar productos o marcas' })).toHaveValue('')
  })

  it('busca desde el encabezado del catálogo', async () => {
    const user = userEvent.setup()
    renderRoute('/catalog')
    await user.type(screen.getByRole('textbox', { name: 'Buscar productos o marcas' }), 'cal')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))
    await waitFor(() => expect(getPublicProducts).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'cal' }), expect.anything()))
    expect(screen.getByRole('textbox', { name: 'Buscar productos o marcas' })).toHaveValue('cal')
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

  it('agrega desde la grilla, acumula unidades y conserva el carrito entre visitas', async () => {
    const user = userEvent.setup()
    const first = renderRoute('/catalog')
    await user.click(await screen.findByRole('button', { name: 'Agregar al carrito' }))
    expect(await screen.findByText('Agregado al carrito')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Agregar al carrito' }))
    expect(screen.getByRole('button', { name: 'Abrir carrito, 2 unidades' })).toBeInTheDocument()
    first.unmount()
    renderRoute('/catalog')
    await user.click(screen.getByRole('button', { name: 'Abrir carrito, 2 unidades' }))
    const dialog = screen.getByRole('dialog', { name: 'Mi carrito' })
    expect(within(dialog).getByText('Color: Gris')).toBeInTheDocument()
    expect(within(dialog).getByText(/Subtotal:.*2.400/)).toBeInTheDocument()
    await user.clear(within(dialog).getByRole('spinbutton', { name: 'Cantidad' }))
    await user.type(within(dialog).getByRole('spinbutton', { name: 'Cantidad' }), '3')
    await user.tab()
    expect(document.querySelector('.site-cart')).toHaveAttribute('aria-label', 'Abrir carrito, 3 unidades')
    await user.click(within(dialog).getByRole('button', { name: /Quitar Cemento Portland/ }))
    expect(within(dialog).getByText('Tu carrito está vacío.')).toBeInTheDocument()
  })

  it('agrega la variante no disponible y cantidad elegida desde el detalle', async () => {
    const user = userEvent.setup()
    renderRoute('/products/7?variantId=12')
    await user.click(await screen.findByRole('radio', { name: /Blanco/ }))
    const quantity = screen.getByRole('spinbutton', { name: 'Cantidad' })
    await user.clear(quantity)
    await user.type(quantity, '4')
    await user.click(screen.getByRole('button', { name: 'Agregar al carrito' }))
    await user.click(screen.getByRole('button', { name: 'Abrir carrito, 4 unidades' }))
    const dialog = screen.getByRole('dialog', { name: 'Mi carrito' })
    expect(within(dialog).getByText('Color: Blanco')).toBeInTheDocument()
    expect(within(dialog).getByText('No disponible')).toBeInTheDocument()
    expect(within(dialog).getByText(/Subtotal:.*6.000/)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Pedir presupuesto' })).toBeEnabled()
    expect(within(dialog).queryByText(/Falta configurar el número/)).not.toBeInTheDocument()
  })

  it('envía desde el carrito el SKU y la presentación vigentes', async () => {
    const user = userEvent.setup()
    const popup = { location: { href: '' }, close: vi.fn() }
    vi.stubGlobal('open', vi.fn(() => popup))
    renderRoute('/catalog')
    await user.click(await screen.findByRole('button', { name: 'Agregar al carrito' }))
    getPublicProduct.mockResolvedValue({ ...product, nombre: 'Cemento actualizado', images: [], variantes: [{ id: 12, sku: 'CEM-50', precioReferencia: 1200, disponible: true, caracteristicaNombre: 'Peso', valor: '50', unidad: 'kg' }] })
    await user.click(screen.getByRole('button', { name: 'Abrir carrito, 1 unidad' }))
    await user.click(await within(screen.getByRole('dialog', { name: 'Mi carrito' })).findByRole('button', { name: 'Pedir presupuesto' }))
    await waitFor(() => expect(popup.location.href).toContain('https://wa.me/5492984400705?text='))
    expect(new URL(popup.location.href).searchParams.get('text')).toBe('Hola, quisiera pedir un presupuesto:\n\nProducto: Cemento actualizado\nPresentación: Peso 50 kg\nCantidad: 1 unidad\nCódigo: CEM-50')
  })

  it('envía desde el detalle una ficha sin código ni opción interna si faltan', async () => {
    const user = userEvent.setup()
    const popup = { location: { href: '' }, close: vi.fn() }
    vi.stubGlobal('open', vi.fn(() => popup))
    renderRoute('/products/7')
    await screen.findByRole('heading', { name: 'Cemento Portland' })
    getPublicProduct.mockResolvedValue({ ...product, nombre: 'Cemento actualizado', images: [], variantes: [{ id: 12, sku: null, precioReferencia: 1200, disponible: true, caracteristicaNombre: null, valor: null, unidad: null }] })
    await user.click(screen.getByRole('button', { name: 'Pedir presupuesto' }))
    await waitFor(() => expect(popup.location.href).toContain('https://wa.me/5492984400705?text='))
    expect(new URL(popup.location.href).searchParams.get('text')).toBe('Hola, quisiera pedir un presupuesto:\n\nProducto: Cemento actualizado\nCantidad: 1 unidad')
  })

  it('marca una variante retirada y permite reintentar después de un fallo de red', async () => {
    const user = userEvent.setup()
    renderRoute('/catalog')
    await user.click(await screen.findByRole('button', { name: 'Agregar al carrito' }))
    getPublicProduct.mockRejectedValueOnce(new Error('network'))
    await user.click(screen.getByRole('button', { name: 'Abrir carrito, 1 unidad' }))
    const dialog = screen.getByRole('dialog', { name: 'Mi carrito' })
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('No pudimos actualizar')
    getPublicProduct.mockResolvedValueOnce({ ...product, images: [], variantes: [] })
    await user.click(within(dialog).getByRole('button', { name: 'Reintentar' }))
    expect(await within(dialog).findByText('Ya no está publicado')).toBeInTheDocument()
    expect(within(dialog).getByText(/Quitá los productos/)).toBeInTheDocument()
  })

  it('muestra seis ofertas ordenadas en el home y enlaza a la página completa', async () => {
    const offers = Array.from({ length: 6 }, (_, index) => ({ ...product, id: index + 1, nombre: `Oferta ${index + 1}`, selectedVariantId: index + 20, precioOferta: 900 - index * 10, porcentajeDescuento: 25 + index }))
    getPublicOffers.mockResolvedValue({ content: offers, page: 0, size: 6, totalElements: 8, totalPages: 2 })
    renderRoute('/')
    const rail = await screen.findByLabelText('Ofertas destacadas')
    expect(getPublicOffers).toHaveBeenCalledWith(0, 6, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(within(rail).getAllByRole('article')).toHaveLength(6)
    expect(within(rail).getAllByRole('heading').map((heading) => heading.textContent)).toEqual(offers.map((offer) => offer.nombre))
    expect(screen.getByRole('link', { name: /Ver todas las ofertas/ })).toHaveAttribute('href', '/ofertas')
    expect(screen.getByRole('button', { name: 'Ofertas siguientes' })).toBeInTheDocument()
  })

  it('explica carga, ausencia y error del carril sin afectar el resto del home', async () => {
    getPublicOffers.mockReturnValueOnce(new Promise(() => {}))
    const loading = renderRoute('/')
    expect(screen.getByText('Cargando ofertas…')).toHaveAttribute('role', 'status')
    loading.unmount()

    const empty = renderRoute('/')
    expect(await screen.findByText('Todavía no hay ofertas disponibles.')).toBeInTheDocument()
    empty.unmount()

    getPublicOffers.mockRejectedValueOnce(new Error('network'))
    renderRoute('/')
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar las ofertas')
    expect(screen.getByRole('link', { name: /Ver todas las ofertas/ })).toHaveAttribute('href', '/ofertas')
  })

  it('pagina ofertas sin filtros y distingue carga, vacío y error', async () => {
    const user = userEvent.setup()
    getPublicOffers.mockImplementation((page) => Promise.resolve({ content: [{ ...product, id: page + 7, nombre: `Oferta página ${page + 1}`, precioOferta: 800, porcentajeDescuento: 33 }], page, size: 20, totalElements: 21, totalPages: 2 }))
    const first = renderRoute('/ofertas')
    expect(screen.getByRole('status')).toHaveTextContent('Cargando ofertas')
    expect(await screen.findByText('Oferta página 1')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(await screen.findByText('Oferta página 2')).toBeInTheDocument()
    expect(getPublicOffers).toHaveBeenLastCalledWith(1, 20, expect.anything())
    expect(screen.queryByRole('complementary', { name: 'Filtros de productos' })).not.toBeInTheDocument()
    first.unmount()

    getPublicOffers.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    const empty = renderRoute('/ofertas')
    expect(await screen.findByText('Todavía no hay ofertas disponibles.')).toBeInTheDocument()
    empty.unmount()

    getPublicOffers.mockRejectedValue(new Error('network'))
    renderRoute('/ofertas')
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar las ofertas')
  })

  it('muestra precio tachado y porcentaje de la variante seleccionada, incluso menor a 1 %', async () => {
    const user = userEvent.setup()
    getPublicProduct.mockResolvedValue({ ...product, images: [], caracteristicas: [], variantes: [
      { id: 12, precioReferencia: 1200, precioOferta: 900, porcentajeDescuento: 25, disponible: true, caracteristicaNombre: 'Color', valor: 'Gris' },
      { id: 13, precioReferencia: 1500, precioOferta: 1499, porcentajeDescuento: 0, disponible: true, caracteristicaNombre: 'Color', valor: 'Blanco' },
      { id: 14, precioReferencia: 2000, precioOferta: null, porcentajeDescuento: null, disponible: true, caracteristicaNombre: 'Color', valor: 'Negro' },
    ] })
    renderRoute('/products/7')
    expect(await screen.findByRole('heading', { name: 'Cemento Portland' })).toBeInTheDocument()
    expect(document.querySelector('.detail-offer-price del')).toHaveTextContent(/1.200/)
    expect(document.querySelector('.detail-offer-price strong')).toHaveTextContent(/900/)
    expect(document.querySelector('.detail-offer-price .offer-discount')).toHaveTextContent('25 %')
    await user.click(screen.getByRole('radio', { name: /Blanco/ }))
    expect(document.querySelector('.detail-offer-price strong')).toHaveTextContent(/1.499/)
    expect(document.querySelector('.detail-offer-price .offer-discount')).toHaveTextContent('<1 %')
    await user.click(screen.getByRole('radio', { name: /Negro/ }))
    expect(document.querySelector('.detail-offer-price')).not.toBeInTheDocument()
    expect(document.querySelector('.detail-price')).toHaveTextContent(/2.000/)
  })

  it('muestra oferta en resultados filtrados y relacionados sin cambiar el precio de productos normales', async () => {
    getPublicProducts.mockResolvedValue({ content: [{ ...product, precioOferta: 900, porcentajeDescuento: 25 }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    const catalogPage = renderRoute('/catalog?categoriaId=3')
    expect(await screen.findByText('25 % de descuento')).toBeInTheDocument()
    expect(document.querySelector('.product-card del')).toHaveTextContent(/1.200/)
    expect(getPublicProducts).toHaveBeenCalledWith(expect.objectContaining({ categoriaId: '3' }), expect.anything())
    catalogPage.unmount()

    getRelatedProducts.mockResolvedValue({ content: [{ ...product, id: 8, nombre: 'Cal', precioOferta: 1100, porcentajeDescuento: 8 }, { ...product, id: 9, nombre: 'Arena' }] })
    renderRoute('/products/7')
    const related = await screen.findByRole('heading', { name: 'Productos relacionados' })
    const section = related.closest('section')
    expect(within(section).getByText('8 % de descuento')).toBeInTheDocument()
    expect(within(section).getByText('Arena').closest('.product-card').querySelector('del')).toBeNull()
  })

  it('usa el precio vigente al agregar, refrescar y presupuestar después de editar y terminar una oferta', async () => {
    const user = userEvent.setup()
    const popup = { location: { href: '' }, close: vi.fn() }
    vi.stubGlobal('open', vi.fn(() => popup))
    let offerPrice = 900
    getPublicProducts.mockResolvedValue({ content: [{ ...product, precioOferta: 900, porcentajeDescuento: 25 }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    getPublicProduct.mockImplementation(() => Promise.resolve({ ...product, images: [], variantes: [{ id: 12, precioReferencia: 1200, precioOferta: offerPrice, porcentajeDescuento: offerPrice == null ? null : 25, disponible: true, sku: 'CEM-50' }] }))
    renderRoute('/catalog?categoriaId=3')
    expect(await screen.findByText('25 % de descuento')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Agregar al carrito' }))
    await user.click(screen.getByRole('button', { name: 'Abrir carrito, 1 unidad' }))
    const dialog = screen.getByRole('dialog', { name: 'Mi carrito' })
    expect(await within(dialog).findByText(/Subtotal:.*900/)).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem('glg-public-cart-v1'))[0].unitPrice).toBe(900)
    await user.click(within(dialog).getByRole('button', { name: 'Cerrar carrito' }))

    offerPrice = 800
    await user.click(screen.getByRole('button', { name: 'Abrir carrito, 1 unidad' }))
    expect(await within(screen.getByRole('dialog', { name: 'Mi carrito' })).findByText(/Subtotal:.*800/)).toBeInTheDocument()
    await waitFor(() => expect(JSON.parse(localStorage.getItem('glg-public-cart-v1'))[0].unitPrice).toBe(800))
    await user.click(within(screen.getByRole('dialog', { name: 'Mi carrito' })).getByRole('button', { name: 'Cerrar carrito' }))

    offerPrice = null
    await user.click(screen.getByRole('button', { name: 'Abrir carrito, 1 unidad' }))
    expect(await within(screen.getByRole('dialog', { name: 'Mi carrito' })).findByText(/Subtotal:.*1.200/)).toBeInTheDocument()
    await waitFor(() => expect(JSON.parse(localStorage.getItem('glg-public-cart-v1'))[0].unitPrice).toBe(1200))
    await user.click(within(screen.getByRole('dialog', { name: 'Mi carrito' })).getByRole('button', { name: 'Pedir presupuesto' }))
    await waitFor(() => expect(popup.location.href).toContain('https://wa.me/'))
    expect(new URL(popup.location.href).searchParams.get('text')).not.toMatch(/\$|1200|1\.200|800|900/)
  })
})
