import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/context/AuthContext.js'
import { AppRouter } from '../app/AppRouter.jsx'

vi.mock('./pages/UserListPage.jsx', () => ({ UserListPage: () => <p>Listado de usuarios</p> }))
vi.mock('./pages/UserDetailPage.jsx', () => ({ UserDetailPage: () => <p>Detalle de usuario</p> }))
vi.mock('./pages/CreateUserPage.jsx', () => ({ CreateUserPage: () => <p>Formulario de alta</p> }))
vi.mock('./pages/SubscriptionListPage.jsx', () => ({ SubscriptionListPage: ({ channel }) => <p>Suscriptores {channel}</p> }))
vi.mock('../app/ProfilePage.jsx', () => ({ ProfilePage: () => <p>Perfil privado</p> }))
vi.mock('../catalog/categories/pages/CategoryManagementPage.jsx', () => ({ CategoryManagementPage: () => <p>Listado de categorías</p> }))
vi.mock('../auth/pages/LoginPage.jsx', () => ({ LoginPage: () => <p>Ingresar</p> }))

describe('Acceso administrativo', () => {
  it.each([
    ['/app/admin/subscriptions/email', 'Suscriptores EMAIL'],
    ['/app/admin/subscriptions/whatsapp', 'Suscriptores WHATSAPP'],
  ])('limita %s a personal administrativo', (path, content) => {
    for (const role of ['SUPER_ADMIN', 'ADMIN', 'USER', null]) {
      render(
        <AuthContext.Provider value={{ user: role ? { role } : null, status: role ? 'authenticated' : 'anonymous', isAuthenticated: Boolean(role) }}>
          <MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>
        </AuthContext.Provider>,
      )
      if (role === 'SUPER_ADMIN' || role === 'ADMIN') {
        expect(screen.getByText(content)).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Email' })).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'WhatsApp' })).toBeInTheDocument()
      } else {
        expect(screen.queryByText(content)).not.toBeInTheDocument()
        expect(screen.queryByRole('link', { name: 'Email' })).not.toBeInTheDocument()
      }
      cleanup()
    }
  })
  it('permite categorías a los tres roles y redirige anónimos a login', () => {
    for (const role of ['SUPER_ADMIN', 'ADMIN', 'USER', null]) {
      render(
        <AuthContext.Provider value={{ user: role ? { role } : null, status: role ? 'authenticated' : 'anonymous', isAuthenticated: Boolean(role) }}>
          <MemoryRouter initialEntries={['/app/catalog/categories']}><AppRouter /></MemoryRouter>
        </AuthContext.Provider>,
      )
      if (role) {
        expect(screen.getByText('Listado de categorías')).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Categorías' })).toHaveAttribute('href', '/app/catalog/categories')
      } else {
        expect(screen.queryByText('Listado de categorías')).not.toBeInTheDocument()
        expect(screen.getByText('Ingresar')).toBeInTheDocument()
      }
      cleanup()
    }
  })

  it.each([
    ['/app/admin/users', 'Listado de usuarios'],
    ['/app/admin/users/7', 'Detalle de usuario'],
    ['/app/admin/users/new', 'Formulario de alta'],
  ])('protege %s y la navegación', (path, content) => {
    for (const role of ['SUPER_ADMIN', 'ADMIN', 'USER', null]) {
      render(
        <AuthContext.Provider value={{ user: role ? { role } : null, status: role ? 'authenticated' : 'anonymous', isAuthenticated: Boolean(role) }}>
          <MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>
        </AuthContext.Provider>,
      )
      if (role === 'SUPER_ADMIN') {
        expect(screen.getByText(content)).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Usuarios' })).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Crear usuario' })).toBeInTheDocument()
      } else {
        expect(screen.queryByText(content)).not.toBeInTheDocument()
        expect(screen.queryByRole('link', { name: 'Usuarios' })).not.toBeInTheDocument()
        expect(screen.queryByRole('link', { name: 'Crear usuario' })).not.toBeInTheDocument()
        expect(screen.getByText(role ? 'Perfil privado' : 'Ingresar')).toBeInTheDocument()
      }
      cleanup()
    }
  })
})
