import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/context/AuthContext.js'
import { AppRouter } from '../app/AppRouter.jsx'

vi.mock('./pages/UserListPage.jsx', () => ({ UserListPage: () => <p>Listado de usuarios</p> }))
vi.mock('./pages/UserDetailPage.jsx', () => ({ UserDetailPage: () => <p>Detalle de usuario</p> }))
vi.mock('./pages/CreateUserPage.jsx', () => ({ CreateUserPage: () => <p>Formulario de alta</p> }))
vi.mock('../app/ProfilePage.jsx', () => ({ ProfilePage: () => <p>Perfil privado</p> }))
vi.mock('../auth/pages/LoginPage.jsx', () => ({ LoginPage: () => <p>Ingresar</p> }))

describe('Acceso administrativo', () => {
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
