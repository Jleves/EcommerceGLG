import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { RequestError } from '../auth/components/RequestError.jsx'
import { useAuth } from '../auth/context/useAuth.js'
import glgLogo from '../assets/glg-logo-black.png'
import './admin.css'

export function AppLayout() {
  const { user, logout } = useAuth()
  const [error, setError] = useState(null)
  const [loggingOut, setLoggingOut] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const menuButton = useRef(null)
  const navigation = useRef(null)
  const location = useLocation()

  useEffect(() => { setMenuOpen(false) }, [location.pathname])
  useEffect(() => {
    if (!menuOpen) return undefined
    navigation.current?.querySelector('a[aria-current="page"]')?.focus()
    function closeOnEscape(event) {
      if (event.key === 'Escape') {
        setMenuOpen(false)
        menuButton.current?.focus()
      } else if (event.key === 'Tab') {
        const links = navigation.current?.querySelectorAll('a')
        if (!links?.length) return
        const first = links[0]
        const last = links[links.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [menuOpen])

  const navClass = ({ isActive }) => `admin-nav-link${isActive ? ' admin-nav-link-active' : ''}`

  async function handleLogout() {
    setError(null)
    setLoggingOut(true)
    try {
      await logout()
    } catch (requestError) {
      setError(requestError)
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className="admin-shell">
      <header className="admin-header">
        <div className="admin-header-start">
          <button ref={menuButton} className="admin-menu-toggle" type="button" aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'} aria-controls="admin-navigation" aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}>
            <span aria-hidden="true">☰</span>
          </button>
          <NavLink className="admin-brand" to="/app/profile" aria-label="GLG Corralón, ir al perfil"><img src={glgLogo} alt="" /><span>Administración</span></NavLink>
        </div>
        <div className="admin-header-account">
          <span className="admin-account-email">{user?.email}</span>
          <button className="admin-logout" disabled={loggingOut} onClick={handleLogout} type="button">
            {loggingOut ? 'Cerrando…' : 'Cerrar sesión'}
          </button>
        </div>
      </header>
      <div className="admin-layout">
        {menuOpen && <button className="admin-menu-backdrop" type="button" aria-label="Cerrar menú" onClick={() => setMenuOpen(false)} />}
        <aside ref={navigation} id="admin-navigation" className={`admin-nav${menuOpen ? ' admin-nav-open' : ''}`} aria-label="Navegación principal">
          <p className="admin-nav-caption">Gestión</p>
          <NavLink className={navClass} onClick={() => setMenuOpen(false)} to="/app/profile">Perfil</NavLink>
          <NavLink className={navClass} onClick={() => setMenuOpen(false)} to="/app/catalog/categories">Categorías</NavLink>
          <NavLink className={navClass} onClick={() => setMenuOpen(false)} to="/app/catalog/characteristics">Características</NavLink>
          <NavLink className={navClass} onClick={() => setMenuOpen(false)} to="/app/catalog/products">Productos</NavLink>
          <NavLink className={navClass} onClick={() => setMenuOpen(false)} to="/app/catalog/offers">Ofertas</NavLink>
          {['ADMIN', 'SUPER_ADMIN'].includes(user?.role) && (
            <>
              <p className="admin-nav-caption admin-nav-caption-secondary">Suscriptores</p>
              <NavLink className={navClass} onClick={() => setMenuOpen(false)} to="/app/admin/subscriptions/email">Email</NavLink>
              <NavLink className={navClass} onClick={() => setMenuOpen(false)} to="/app/admin/subscriptions/whatsapp">WhatsApp</NavLink>
            </>
          )}
          {user?.role === 'SUPER_ADMIN' && (
            <>
              <p className="admin-nav-caption admin-nav-caption-secondary">Cuentas</p>
              <NavLink className={navClass} onClick={() => setMenuOpen(false)} to="/app/admin/users" end>Usuarios</NavLink>
              <NavLink className={navClass} onClick={() => setMenuOpen(false)} to="/app/admin/users/new">Crear usuario</NavLink>
            </>
          )}
        </aside>
        <main className="admin-main" inert={menuOpen}>
          <RequestError error={error} />
          <Outlet />
        </main>
      </div>
    </div>
  )
}
