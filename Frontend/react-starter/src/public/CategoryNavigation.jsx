import { Fragment, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { IconChevronDown, IconMenu2, IconX } from '@tabler/icons-react'

const categoryUrl = (id) => `/catalog?categoriaId=${id}`
const subcategoryUrl = (categoryId, id) => `/catalog?categoriaId=${categoryId}&subcategoriaId=${id}`

function childrenOf(catalog, categoryId) {
  return (catalog.data?.subcategories ?? []).filter((subcategory) => subcategory.categoriaId === categoryId && subcategory.subcategoriaPadreId == null)
}

function descendantsOf(catalog, id) {
  return (catalog.data?.subcategories ?? []).filter((item) => item.subcategoriaPadreId === id)
}

export function DesktopCategoryNavigation({ catalog, showAbout = false }) {
  const [open, setOpen] = useState(false)
  const triggerRef = useRef(null)
  const location = useLocation()
  const categories = catalog.data?.categories ?? []

  useEffect(() => {
    setOpen(false)
  }, [location.pathname, location.search])

  return <nav className="site-nav" aria-label="Navegación pública">
    <div className="site-nav-inner" onMouseLeave={() => setOpen(false)} onFocusCapture={(event) => { if (event.target.matches('.site-nav-link, .site-nav-chevron')) setOpen(true) }} onKeyDown={(event) => {
      if (event.key === 'Escape' && open) { triggerRef.current?.focus(); setOpen(false) }
    }} onBlurCapture={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
    }}>
      <Link className="site-nav-link" to="/catalog" ref={triggerRef} onMouseEnter={() => setOpen(true)} onClick={() => setOpen(false)}>Categorías</Link>
      <button className="site-nav-chevron" type="button" aria-label="Desplegar categorías" aria-expanded={open} onMouseEnter={() => setOpen(true)} onClick={() => setOpen(true)}><IconChevronDown size={17} /></button>
      <Link className="site-nav-offers" to="/ofertas" aria-current={location.pathname === '/ofertas' ? 'page' : undefined} onMouseEnter={() => setOpen(false)} onFocus={() => setOpen(false)}>NUESTRAS OFERTAS</Link>
      <Link className="site-nav-contact" to="/contacto" aria-current={location.pathname === '/contacto' ? 'page' : undefined} onMouseEnter={() => setOpen(false)} onFocus={() => setOpen(false)}>Contacto</Link>
      {open && <div className="site-nav-menu">
        {catalog.loading ? <p role="status">Cargando categorías…</p> : catalog.error ? <p role="alert">No pudimos cargar las categorías.</p> : categories.length === 0 ? <p>No hay categorías disponibles.</p> : categories.map((category) => {
          const subcategories = childrenOf(catalog, category.id)
          return <div className="site-nav-category" key={category.id}>
            <Link className="site-nav-category-link" to={categoryUrl(category.id)} onClick={() => setOpen(false)}>{category.nombre}</Link>
            {subcategories.length > 0 && <div className="site-nav-subcategories">
                {subcategories.map((subcategory) => <Fragment key={subcategory.id}><Link to={subcategoryUrl(category.id, subcategory.id)} onClick={() => setOpen(false)}>{subcategory.nombre}</Link>{descendantsOf(catalog, subcategory.id).map((child) => <Link key={child.id} style={{ paddingLeft: '1rem' }} to={subcategoryUrl(category.id, child.id)} onClick={() => setOpen(false)}>{child.nombre}</Link>)}</Fragment>)}
              </div>}
          </div>
        })}
      </div>}
      {showAbout && <Link className="site-nav-about" to="/quienes-somos" onMouseEnter={() => setOpen(false)} onFocus={() => setOpen(false)}>Quiénes somos</Link>}
    </div>
  </nav>
}

export function MobileCategoryNavigation({ catalog, showAbout = false }) {
  const [open, setOpen] = useState(false)
  const [expanded, setExpanded] = useState(null)
  const triggerRef = useRef(null)
  const panelRef = useRef(null)
  const location = useLocation()
  const categories = catalog.data?.categories ?? []

  useEffect(() => {
    setOpen(false)
    setExpanded(null)
  }, [location.pathname, location.search])

  useEffect(() => {
    if (!open) return undefined
    const trigger = triggerRef.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panelRef.current?.querySelector('button')?.focus()
    function onKeyDown(event) {
      if (event.key === 'Escape') { setOpen(false); return }
      if (event.key !== 'Tab' || !panelRef.current) return
      const focusable = [...panelRef.current.querySelectorAll('button, a[href]')]
      const first = focusable[0]
      const last = focusable.at(-1)
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
    }
    function onResize(event) { if (event.matches) setOpen(false) }
    const desktop = window.matchMedia?.('(min-width: 901px)')
    desktop?.addEventListener('change', onResize)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      desktop?.removeEventListener('change', onResize)
      document.removeEventListener('keydown', onKeyDown)
      trigger?.focus()
    }
  }, [open])

  return <>
    <button className="site-menu-trigger" type="button" ref={triggerRef} aria-label="Abrir menú" aria-expanded={open} onClick={() => { setExpanded(null); setOpen(true) }}><IconMenu2 size={23} /></button>
    {open && <div className="mobile-menu-layer">
      <button className="mobile-menu-backdrop" type="button" tabIndex={-1} aria-label="Cerrar menú" onClick={() => setOpen(false)} />
      <aside className="mobile-menu-panel" role="dialog" aria-modal="true" aria-label="Menú" ref={panelRef}>
        <div className="mobile-menu-heading"><strong>Menú</strong><button type="button" aria-label="Cerrar menú" onClick={() => setOpen(false)}><IconX size={22} /></button></div>
        <nav className="mobile-menu-categories" aria-label="Navegación móvil">
          <Link className="mobile-menu-offers" to="/ofertas" aria-current={location.pathname === '/ofertas' ? 'page' : undefined} onClick={() => setOpen(false)}>NUESTRAS OFERTAS</Link>
          {showAbout && <Link className="mobile-category-link mobile-about-link" to="/quienes-somos" onClick={() => setOpen(false)}>Quiénes somos</Link>}
          <h2 className="mobile-menu-section-title">Categorías</h2>
          {catalog.loading ? <p role="status">Cargando categorías…</p> : catalog.error ? <p role="alert">No pudimos cargar las categorías.</p> : categories.length === 0 ? <p>No hay categorías disponibles.</p> : categories.map((category) => {
            const subcategories = childrenOf(catalog, category.id)
            return <div className="mobile-menu-category" key={category.id}>
              {subcategories.length > 0 ? <>
                <button className="mobile-category-toggle" type="button" aria-expanded={expanded === category.id} onClick={() => setExpanded(expanded === category.id ? null : category.id)}>{category.nombre}<IconChevronDown size={18} /></button>
                {expanded === category.id && <div className="mobile-subcategories">
                  <Link to={categoryUrl(category.id)} onClick={() => setOpen(false)}>Ver todos</Link>
                  {subcategories.map((subcategory) => <Fragment key={subcategory.id}><Link to={subcategoryUrl(category.id, subcategory.id)} onClick={() => setOpen(false)}>{subcategory.nombre}</Link>{descendantsOf(catalog, subcategory.id).map((child) => <Link key={child.id} style={{ paddingLeft: '1rem' }} to={subcategoryUrl(category.id, child.id)} onClick={() => setOpen(false)}>{child.nombre}</Link>)}</Fragment>)}
                </div>}
              </> : <Link className="mobile-category-link" to={categoryUrl(category.id)} onClick={() => setOpen(false)}>{category.nombre}</Link>}
            </div>
          })}
          <Link className="mobile-menu-contact" to="/contacto" aria-current={location.pathname === '/contacto' ? 'page' : undefined} onClick={() => setOpen(false)}>Contacto</Link>
        </nav>
      </aside>
    </div>}
  </>
}
