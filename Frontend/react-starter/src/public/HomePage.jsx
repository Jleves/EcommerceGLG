import { useEffect, useRef, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { IconArrowLeft, IconArrowRight, IconBuildingStore, IconCreditCard, IconBrandWhatsapp } from '@tabler/icons-react'
import { resolveCategoryIcon } from '../catalog/categories/icons/categoryIcons.js'
import { brandLogos, campaignPhotos, whatsappNumber } from './publicConfig.js'

const heroFirstPart = 'Los materiales'
const heroTitle = `${heroFirstPart} de tu próxima historia.`

function HeroTitle() {
  const [visibleLength, setVisibleLength] = useState(() => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? heroTitle.length : 0)

  useEffect(() => {
    const media = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    let count = 0
    let timer
    const typeNext = () => {
      count += 1
      setVisibleLength(count)
      if (count < heroTitle.length) timer = window.setTimeout(typeNext, count === heroFirstPart.length ? 650 : count < heroFirstPart.length ? 45 : 75)
    }
    const stopForReducedMotion = () => {
      if (media?.matches) {
        window.clearTimeout(timer)
        setVisibleLength(heroTitle.length)
      }
    }
    if (!media?.matches) timer = window.setTimeout(typeNext, 45)
    media?.addEventListener('change', stopForReducedMotion)
    return () => {
      window.clearTimeout(timer)
      media?.removeEventListener('change', stopForReducedMotion)
    }
  }, [])

  return <h1 aria-label={heroTitle}><span className="hero-title-reserve" aria-hidden="true">{heroTitle}</span><span className={`hero-title-typed${visibleLength < heroTitle.length ? ' is-typing' : ''}`} aria-hidden="true">{heroTitle.slice(0, visibleLength)}</span></h1>
}

const campaignFadeMs = 400

function CampaignImage({ photo, outgoing = false, entering = false }) {
  return <div className={`campaign-layer${entering ? ' campaign-layer--entering' : ''}`} aria-hidden={outgoing || undefined}>
    <img className="campaign-backdrop" src={photo.src} alt="" aria-hidden="true" />
    <div className={`campaign-slide${photo.portrait ? ' campaign-slide--portrait' : ''}`}>
      <img src={photo.src} alt={outgoing ? '' : photo.alt} />
      {photo.caption && <span className="campaign-caption">{photo.caption}</span>}
    </div>
  </div>
}

function CampaignCarousel() {
  const [current, setCurrent] = useState(0)
  const [previous, setPrevious] = useState(null)
  const [paused, setPaused] = useState(false)
  const [reducedMotion, setReducedMotion] = useState(() => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false)
  useEffect(() => {
    const media = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    if (!media) return undefined
    const update = () => {
      setReducedMotion(media.matches)
      if (media.matches) setPrevious(null)
    }
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])
  useEffect(() => {
    if (campaignPhotos.length < 2 || paused || reducedMotion) return undefined
    const timer = window.setInterval(() => {
      setPrevious(current)
      setCurrent((current + 1) % campaignPhotos.length)
    }, 6000)
    return () => window.clearInterval(timer)
  }, [current, paused, reducedMotion])
  useEffect(() => {
    if (previous === null) return undefined
    const timer = window.setTimeout(() => setPrevious(null), campaignFadeMs)
    return () => window.clearTimeout(timer)
  }, [current, previous])
  function showPhoto(index) {
    if (index === current) return
    setPrevious(reducedMotion ? null : current)
    setCurrent(index)
  }
  const hasPhotos = campaignPhotos.length > 0
  return (
    <div className="campaign" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)} onFocusCapture={() => setPaused(true)} onBlurCapture={() => setPaused(false)} aria-label="Campañas de GLG">
      <div className="campaign-frame">
        {hasPhotos ? <>
          {previous !== null && <CampaignImage photo={campaignPhotos[previous]} outgoing />}
          <CampaignImage key={current} photo={campaignPhotos[current]} entering={previous !== null} />
        </> : <p>Las fotos de campaña estarán disponibles próximamente.</p>}
      </div>
      {campaignPhotos.length > 1 && <div className="campaign-controls">
        <div className="campaign-dots" aria-label="Elegir foto de campaña">{campaignPhotos.map((photo, index) => <button key={photo.src} type="button" className={index === current ? 'active' : ''} onClick={() => showPhoto(index)} aria-label={`Ver foto ${index + 1}`} aria-current={index === current ? 'true' : undefined} />)}</div>
        <div><button type="button" onClick={() => showPhoto((current - 1 + campaignPhotos.length) % campaignPhotos.length)} aria-label="Foto anterior"><IconArrowLeft size={18} /></button><button type="button" onClick={() => showPhoto((current + 1) % campaignPhotos.length)} aria-label="Foto siguiente"><IconArrowRight size={18} /></button></div>
      </div>}
    </div>
  )
}

export function HomePage() {
  const { catalog } = useOutletContext()
  const categories = catalog.data?.categories ?? []
  const visibleCategories = categories.slice(0, 8)
  const categoryRailRef = useRef(null)
  const [railPosition, setRailPosition] = useState({ previous: false, next: false })
  const brandFacet = catalog.data?.facets?.find((facet) => facet.name.toLowerCase() === 'marca')
  const visibleBrands = brandLogos.filter((brand) => brandFacet?.values.some((value) => value.toLowerCase() === brand.name.toLowerCase()))

  useEffect(() => {
    const rail = categoryRailRef.current
    if (!rail) return undefined
    const updatePosition = () => setRailPosition({
      previous: rail.scrollLeft > 2,
      next: rail.scrollLeft + rail.clientWidth < rail.scrollWidth - 2,
    })
    updatePosition()
    rail.addEventListener('scroll', updatePosition, { passive: true })
    window.addEventListener('resize', updatePosition)
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(updatePosition)
    observer?.observe(rail)
    return () => {
      rail.removeEventListener('scroll', updatePosition)
      window.removeEventListener('resize', updatePosition)
      observer?.disconnect()
    }
  }, [catalog.loading, catalog.error, visibleCategories.length])

  function moveCategoryRail(direction) {
    const rail = categoryRailRef.current
    const card = rail?.querySelector('.category-stone')
    if (!rail || !card) return
    const gap = parseFloat(window.getComputedStyle(rail).gap) || 0
    rail.scrollBy({
      left: direction * (card.getBoundingClientRect().width + gap),
      behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    })
  }

  return <>
    <section className="home-hero"><div className="public-container hero-grid">
      <div className="hero-copy"><span className="eyebrow">Materiales para tus proyectos</span><HeroTitle /><p>Todo lo que necesitás para cada etapa de tu obra, en un solo lugar. Explorá el catálogo y consultanos por WhatsApp.</p><Link className="accent-button" to="/catalog">Explorar categorías <IconArrowRight size={17} /></Link></div>
      <CampaignCarousel />
    </div></section>
    <section className="home-services public-container" aria-label="Información comercial">
      <article><IconCreditCard aria-hidden="true" /><div><h2>Medios de pago</h2><p>Consultá las opciones disponibles.</p></div></article>
      <article><IconBrandWhatsapp aria-hidden="true" /><div><h2>WhatsApp</h2><p>{whatsappNumber ? <a href={`https://wa.me/${whatsappNumber}`} target="_blank" rel="noreferrer">Te ayudamos con tu compra.</a> : 'Te ayudamos con tu compra.'}</p></div></article>
      <article><IconBuildingStore aria-hidden="true" /><div><h2>Sucursal</h2><p><Link to="/contacto">Visitá nuestro local en General Roca.</Link></p></div></article>
    </section>
    <section className="home-categories"><div className="public-container"><div className="section-heading"><div><span className="eyebrow">Explorá por categoría</span><h2>Todo para tu obra</h2><p>Elegí lo que necesitás y encontrá materiales para cada proyecto.</p></div><div className="category-actions"><Link to="/catalog">Ver todas las categorías →</Link>{visibleCategories.length > 1 && <div className="category-controls" aria-label="Desplazar categorías"><button type="button" onClick={() => moveCategoryRail(-1)} disabled={!railPosition.previous} aria-label="Categorías anteriores"><IconArrowLeft size={18} aria-hidden="true" /></button><button type="button" onClick={() => moveCategoryRail(1)} disabled={!railPosition.next} aria-label="Categorías siguientes"><IconArrowRight size={18} aria-hidden="true" /></button></div>}</div></div>
      {catalog.loading ? <p role="status">Cargando categorías…</p> : catalog.error ? <p role="alert">No pudimos cargar las categorías. Probá nuevamente más tarde.</p> : categories.length === 0 ? <p>Todavía no hay categorías disponibles.</p> : <div className="category-ground"><div className="category-rail" ref={categoryRailRef} aria-label="Categorías disponibles">{visibleCategories.map((category) => { const { Icon } = resolveCategoryIcon(category.icono); return <div className="category-stone" key={category.id}><Link className="category-card" to={`/catalog?categoriaId=${category.id}`}><span className="category-fasteners" aria-hidden="true"><i /><i /><i /><i /></span><div className="category-art"><Icon size={72} stroke={1.3} aria-hidden="true" /></div><div className="category-label"><strong>{category.nombre}</strong><span>{category.descripcion || 'Ver productos'}</span><b aria-hidden="true">→</b></div></Link></div> })}</div></div>}
    </div></section>
    <section className="home-brands"><div className="public-container"><div className="section-heading"><div><span className="eyebrow">Marcas que nos acompañan</span><h2>Trabajamos con grandes marcas</h2></div></div>{catalog.loading ? <p role="status">Cargando marcas…</p> : visibleBrands.length > 0 ? <div className="brand-track">{visibleBrands.map((brand) => <Link key={brand.name} to={`/catalog?marca=${encodeURIComponent(brand.name)}`} aria-label={`Ver productos de ${brand.name}`}><img src={brand.src} alt={brand.name} loading="lazy" /></Link>)}</div> : <p className="brand-pending">Los logos de marcas se incorporarán próximamente.</p>}</div></section>
    {whatsappNumber && <a className="home-whatsapp" href={`https://wa.me/${whatsappNumber}`} target="_blank" rel="noopener noreferrer" aria-label="Chatear por WhatsApp"><IconBrandWhatsapp size={30} stroke={1.8} aria-hidden="true" /></a>}
  </>
}
