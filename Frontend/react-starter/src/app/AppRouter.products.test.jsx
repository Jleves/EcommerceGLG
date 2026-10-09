import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/context/AuthContext.js'
import { listProducts } from '../catalog/products/services/productService.js'
import { listCategories } from '../catalog/categories/services/categoryService.js'
import { createCharacteristic, deleteCharacteristic, getCharacteristic, listCharacteristics, updateCharacteristic } from '../catalog/characteristics/services/characteristicService.js'
import { AppRouter } from './AppRouter.jsx'

vi.mock('../catalog/products/services/productService.js', () => ({ listProducts: vi.fn() }))
vi.mock('../catalog/categories/services/categoryService.js', () => ({ listCategories: vi.fn() }))
vi.mock('../catalog/characteristics/services/characteristicService.js', () => ({ listCharacteristics: vi.fn(), getCharacteristic: vi.fn(), createCharacteristic: vi.fn(), updateCharacteristic: vi.fn(), deleteCharacteristic: vi.fn() }))
vi.mock('../auth/pages/LoginPage.jsx', () => ({ LoginPage: () => <p>Ingreso público</p> }))
vi.mock('../admin/pages/UserListPage.jsx', () => ({ UserListPage: () => <p>Administración de usuarios</p> }))

function renderRoute(path, role) {
  const authenticated = Boolean(role)
  return render(
    <AuthContext.Provider value={{ status: authenticated ? 'authenticated' : 'anonymous', isAuthenticated: authenticated, user: authenticated ? { email: 'test@example.com', role } : null, logout: vi.fn() }}>
      <MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('rutas administrativas del catálogo', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listProducts.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    listCategories.mockResolvedValue([])
    listCharacteristics.mockResolvedValue([])
  })

  it.each(['SUPER_ADMIN', 'ADMIN', 'USER'])('permite consultar productos con sesión %s', async (role) => {
    renderRoute('/app/catalog/products', role)
    expect(await screen.findByRole('heading', { name: 'Productos' })).toBeInTheDocument()
    expect(listProducts).toHaveBeenCalledWith(0, undefined, { signal: expect.any(AbortSignal) })
    expect(screen.getByRole('link', { name: 'Productos' })).toHaveAttribute('href', '/app/catalog/products')
  })

  it('redirige un acceso directo sin sesión antes de consultar productos', () => {
    renderRoute('/app/catalog/products?nombre=arena&page=1', null)
    expect(screen.getByText('Ingreso público')).toBeInTheDocument()
    expect(listProducts).not.toHaveBeenCalled()
  })

  it.each(['SUPER_ADMIN', 'ADMIN', 'USER'])('permite consultar características con sesión %s', async (role) => {
    renderRoute('/app/catalog/characteristics', role)
    expect(await screen.findByRole('heading', { name: 'Características' })).toBeInTheDocument()
    expect(listCharacteristics).toHaveBeenCalledWith({ signal: expect.any(AbortSignal) })
    expect(screen.getByRole('link', { name: 'Características' })).toHaveAttribute('href', '/app/catalog/characteristics')
    expect(Boolean(screen.queryByRole('link', { name: 'Usuarios' }))).toBe(role === 'SUPER_ADMIN')
  })

  it('redirige el acceso directo anónimo a características sin consultar la API', () => {
    renderRoute('/app/catalog/characteristics', null)
    expect(screen.getByText('Ingreso público')).toBeInTheDocument()
    expect(listCharacteristics).not.toHaveBeenCalled()
  })

  it('mantiene categorías bajo sesión y usuarios solo para SUPER_ADMIN', () => {
    const anonymous = renderRoute('/app/catalog/categories', null)
    expect(screen.getByText('Ingreso público')).toBeInTheDocument()
    anonymous.unmount()

    const user = renderRoute('/app/admin/users', 'USER')
    expect(screen.queryByText('Administración de usuarios')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Usuarios' })).not.toBeInTheDocument()
    user.unmount()

    renderRoute('/app/admin/users', 'SUPER_ADMIN')
    expect(screen.getByText('Administración de usuarios')).toBeInTheDocument()
  })

  it('vuelve de Productos a Categorías dentro de las rutas protegidas', async () => {
    renderRoute('/app/catalog/products', 'ADMIN')
    await screen.findByRole('heading', { name: 'Productos' })
    await userEvent.setup().click(screen.getByRole('link', { name: 'Categorías' }))
    expect(await screen.findByRole('heading', { name: 'Categorías' })).toBeInTheDocument()
    expect(listCategories).toHaveBeenCalledWith({ signal: expect.any(AbortSignal) })
  })

  it('navega de Productos a Características dentro de la sesión', async () => {
    renderRoute('/app/catalog/products', 'ADMIN')
    await screen.findByRole('heading', { name: 'Productos' })
    await userEvent.setup().click(screen.getByRole('link', { name: 'Características' }))
    expect(await screen.findByRole('heading', { name: 'Características' })).toBeInTheDocument()
    expect(listCharacteristics).toHaveBeenCalledWith({ signal: expect.any(AbortSignal) })
  })

  it('recorre listar, crear, renombrar y eliminar antes de volver a Productos y Categorías', async () => {
    listCharacteristics.mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ id: 7, nombre: 'Color', cantidadProductos: 0 }])
      .mockResolvedValueOnce([{ id: 7, nombre: 'Tono', cantidadProductos: 0 }])
      .mockResolvedValueOnce([])
    createCharacteristic.mockResolvedValue({ id: 7, nombre: 'Color', cantidadProductos: 0 })
    getCharacteristic.mockResolvedValueOnce({ id: 7, nombre: 'Color', cantidadProductos: 0 })
      .mockResolvedValueOnce({ id: 7, nombre: 'Tono', cantidadProductos: 0 })
    updateCharacteristic.mockResolvedValue({ id: 7, nombre: 'Tono', cantidadProductos: 0 })
    deleteCharacteristic.mockResolvedValue(null)
    const user = userEvent.setup()
    renderRoute('/app/catalog/characteristics', 'ADMIN')
    await screen.findByText('No hay características.')
    await user.click(screen.getByRole('button', { name: 'Crear característica' }))
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Color')
    await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
    await user.click(await screen.findByRole('button', { name: 'Editar Color' }))
    const name = await screen.findByRole('textbox', { name: 'Nombre' })
    await user.clear(name)
    await user.type(name, 'Tono')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await user.click(await screen.findByRole('button', { name: 'Eliminar Tono' }))
    await user.click(await screen.findByRole('button', { name: 'Confirmar eliminación' }))
    await screen.findByText('No hay características.')
    expect(createCharacteristic).toHaveBeenCalledWith({ nombre: 'Color' })
    expect(updateCharacteristic).toHaveBeenCalledWith(7, { nombre: 'Tono' })
    expect(deleteCharacteristic).toHaveBeenCalledExactlyOnceWith(7)
    await user.click(screen.getByRole('link', { name: 'Productos' }))
    expect(await screen.findByRole('heading', { name: 'Productos' })).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Categorías' }))
    expect(await screen.findByRole('heading', { name: 'Categorías' })).toBeInTheDocument()
  })
})
