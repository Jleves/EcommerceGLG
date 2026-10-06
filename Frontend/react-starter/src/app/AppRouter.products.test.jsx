import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/context/AuthContext.js'
import { listProducts } from '../catalog/products/services/productService.js'
import { AppRouter } from './AppRouter.jsx'

vi.mock('../catalog/products/services/productService.js', () => ({ listProducts: vi.fn() }))
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
})
