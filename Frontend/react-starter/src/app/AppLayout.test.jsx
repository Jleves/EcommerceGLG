import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/context/AuthContext.js'
import { AppLayout } from './AppLayout.jsx'

describe('AppLayout', () => {
  it('abre y cierra el menú, marca la sección activa y navega sin perder los permisos', async () => {
    const user = userEvent.setup()
    render(
      <AuthContext.Provider value={{ user: { email: 'admin@example.com', role: 'SUPER_ADMIN' }, logout: vi.fn() }}>
        <MemoryRouter initialEntries={['/app/profile']}>
          <Routes>
            <Route path="/app" element={<AppLayout />}>
              <Route path="profile" element={<p>Perfil privado</p>} />
              <Route path="catalog/categories" element={<p>Gestión de categorías</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    const toggle = screen.getByRole('button', { name: 'Abrir menú' })
    await user.click(toggle)
    expect(screen.getByRole('button', { name: 'Cerrar menú', expanded: true })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Perfil' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Usuarios' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Perfil' })).toHaveFocus()
    await user.keyboard('{Shift>}{Tab}{/Shift}')
    expect(screen.getByRole('link', { name: 'Crear usuario' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Abrir menú', expanded: false })).toHaveFocus()

    await user.click(screen.getByRole('button', { name: 'Abrir menú' }))
    await user.click(screen.getByRole('link', { name: 'Categorías' }))
    expect(screen.getByText('Gestión de categorías')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Categorías' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Abrir menú', expanded: false })).toBeInTheDocument()
  })

  it('conserva la sesión visible cuando falla el logout', async () => {
    const user = userEvent.setup()
    const logout = vi.fn().mockRejectedValue(Object.assign(new Error('No se pudo cerrar la sesión.'), {
      requestId: 'req-logout',
    }))

    render(
      <AuthContext.Provider value={{ user: { email: 'user@example.com' }, logout }}>
        <MemoryRouter initialEntries={['/app/profile']}>
          <Routes>
            <Route path="/app" element={<AppLayout />}>
              <Route path="profile" element={<p>Contenido privado</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    expect(logout).toHaveBeenCalledOnce()
    expect(screen.getByText('user@example.com')).toBeInTheDocument()
    expect(screen.getByText('Contenido privado')).toBeInTheDocument()
    expect(screen.getByText('No se pudo cerrar la sesión.')).toBeInTheDocument()
    expect(screen.getByText('Referencia: req-logout')).toBeInTheDocument()
  })
})
