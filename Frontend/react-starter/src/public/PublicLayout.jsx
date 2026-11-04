import { useEffect, useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { IconBrandWhatsapp, IconSearch } from '@tabler/icons-react'
import headerLogo from '../assets/glg-logo-black.png'
import footerLogo from '../assets/glg-logo-white.png'
import { whatsappNumber } from './publicConfig.js'
import { getPublicCatalog } from './publicService.js'
import { DesktopCategoryNavigation, MobileCategoryNavigation } from './CategoryNavigation.jsx'
import './public.css'

export function PublicLayout() {
  const navigate = useNavigate()
  const [catalog, setCatalog] = useState({ loading: true, data: null, error: null })
  const [query, setQuery] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getPublicCatalog({ signal: controller.signal }).then(
      (data) => setCatalog({ loading: false, data, error: null }),
      (error) => { if (error.name !== 'AbortError') setCatalog({ loading: false, data: null, error }) },
    )
    return () => controller.abort()
  }, [])

  function search(event) {
    event.preventDefault()
    const term = query.trim()
    navigate(term ? `/catalog?q=${encodeURIComponent(term)}` : '/catalog')
  }

  return (
    <div className="public-site">
      <header className="site-header">
        <div className="site-announcement"><span>GLG CONSTRUCCIONES / GENERAL ROCA</span><span>Materiales para construir en el Alto Valle</span></div>
        <div className="site-main-header">
          <Link className="site-logo" to="/" aria-label="GLG Corralón, inicio"><img src={headerLogo} alt="GLG Corralón Construcciones" /></Link>
          <form className="site-search" onSubmit={search} role="search">
            <IconSearch size={19} aria-hidden="true" />
            <input aria-label="Buscar productos o marcas" placeholder="Buscar materiales, marcas o productos" value={query} onChange={(event) => setQuery(event.target.value)} />
            <button type="submit" aria-label="Buscar">Buscar</button>
          </form>
          {whatsappNumber && <a className="site-chat" href={`https://wa.me/${whatsappNumber}`} target="_blank" rel="noreferrer"><IconBrandWhatsapp size={18} /> Chatear</a>}
          <MobileCategoryNavigation catalog={catalog} />
        </div>
        <DesktopCategoryNavigation catalog={catalog} />
      </header>
      <main id="contenido"><Outlet context={{ catalog }} /></main>
      <footer className="site-footer">
        <div className="footer-grid public-container">
          <div><Link className="footer-logo" to="/"><img src={footerLogo} alt="GLG Corralón Construcciones" /></Link><p>General Roca para el Alto Valle</p><span>Materiales para hacer realidad tus proyectos.</span></div>
          <div><h2>Accesos rápidos</h2><Link to="/">Inicio</Link><Link to="/catalog">Categorías y productos</Link></div>
          <div><h2>Contacto</h2><p>General Roca, Río Negro</p>{whatsappNumber && <a href={`https://wa.me/${whatsappNumber}`} target="_blank" rel="noreferrer">WhatsApp</a>}</div>
        </div>
        <div className="footer-bottom public-container">© GLG Construcciones</div>
      </footer>
    </div>
  )
}
