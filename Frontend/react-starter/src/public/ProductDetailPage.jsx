import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { IconPackage } from '@tabler/icons-react'
import { ProductCard } from './ProductCard.jsx'
import { formatPrice } from './formatPrice.js'
import { getPublicProduct, getRelatedProducts } from './publicService.js'
import { useCart } from './cartContext.js'
import { cartMessage, quoteVariantDetails } from './cartUtils.js'
import { whatsappNumber } from './publicConfig.js'

export function ProductDetailPage() {
  const { id } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const [state, setState] = useState({ loading: true, product: null, related: [], error: null })
  const [photoIndex, setPhotoIndex] = useState(0)
  const [imageFailed, setImageFailed] = useState(false)
  const [quantity, setQuantity] = useState(1)
  const [feedback, setFeedback] = useState('')
  const [quoting, setQuoting] = useState(false)
  const { add } = useCart()
  useEffect(() => {
    const controller = new AbortController()
    setState({ loading: true, product: null, related: [], error: null })
    Promise.all([getPublicProduct(id, { signal: controller.signal }),
      getRelatedProducts(id, { signal: controller.signal }).catch(() => ({ content: [] }))]).then(
      ([product, related]) => { setState({ loading: false, product, related: related.content, error: null }); setPhotoIndex(Math.max(0, product.images.findIndex((image) => image.primary))); setImageFailed(false) },
      (error) => { if (error.name !== 'AbortError') setState({ loading: false, product: null, related: [], error }) },
    )
    return () => controller.abort()
  }, [id])
  const product = state.product
  const requestedVariant = searchParams.get('variantId')
  const selected = product?.variantes.find((variant) => String(variant.id) === requestedVariant)
    ?? product?.variantes.find((variant) => variant.id === product.selectedVariantId)
  const photo = product?.images[photoIndex]

  function addToCart() {
    if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > 9999) return
    add(product, selected, quantity)
    setFeedback('Agregado al carrito')
  }
  async function requestQuote() {
    if (!whatsappNumber || quoting || !Number.isSafeInteger(quantity) || quantity < 1 || quantity > 9999) return
    const popup = window.open('', '_blank')
    setQuoting(true)
    setFeedback('')
    try {
      const current = await getPublicProduct(product.id)
      const variant = current.variantes.find((item) => item.id === selected.id)
      if (!variant) { popup?.close(); setFeedback('Esta variante ya no está publicada.'); return }
      const line = { name: current.nombre, ...quoteVariantDetails(variant), quantity }
      const url = `https://wa.me/${whatsappNumber}?text=${encodeURIComponent(cartMessage([line]))}`
      if (popup) { popup.opener = null; popup.location.href = url }
      else window.location.href = url
    } catch { popup?.close(); setFeedback('No pudimos actualizar el producto. Reintentá.') }
    finally { setQuoting(false) }
  }

  if (state.loading) return <div className="public-container detail-status" role="status">Cargando producto…</div>
  if (state.error) return <div className="public-container detail-status" role="alert"><h1>Producto no disponible</h1><p>No pudimos abrir esta ficha.</p><Link to="/catalog">Volver al catálogo</Link></div>
  return <div className="detail-page public-container">
    <div className="breadcrumbs"><Link to="/">Inicio</Link><span>/</span><Link to="/catalog">Catálogo</Link><span>/</span><span>{product.nombre}</span></div>
    <div className="detail-grid"><div className="detail-gallery"><div className="detail-main-image">{photo && !imageFailed ? <img src={photo.detailUrl} alt={`${product.nombre}, foto ${photoIndex + 1}`} onError={() => setImageFailed(true)} /> : <IconPackage size={80} stroke={1} aria-label="Imagen no disponible" />}</div>{product.images.length > 1 && <div className="detail-thumbnails">{product.images.map((image, index) => <button key={image.id} type="button" className={index === photoIndex ? 'selected' : ''} onClick={() => { setPhotoIndex(index); setImageFailed(false) }} aria-label={`Ver foto ${index + 1}`} aria-current={index === photoIndex ? 'true' : undefined}><img src={image.thumbnailUrl} alt="" loading="lazy" /></button>)}</div>}</div>
      <div className="detail-info"><span className="eyebrow">Materiales para tu obra</span><h1>{product.nombre}</h1>{selected.precioOferta != null ? <div className="offer-price detail-offer-price"><del aria-label={`Precio original ${formatPrice(selected.precioReferencia)}`}>{formatPrice(selected.precioReferencia)}</del><strong className="detail-price">{formatPrice(selected.precioOferta)}</strong><span className="offer-discount">{selected.porcentajeDescuento === 0 ? '<1 %' : `${selected.porcentajeDescuento} %`} de descuento</span></div> : <strong className="detail-price">{formatPrice(selected.precioReferencia)}</strong>}<p className="detail-price-caption">{selected.precioOferta != null ? 'Precio de oferta para presupuesto' : 'Precio de referencia para presupuesto'}</p><span className={`availability ${selected.disponible ? 'available' : ''}`}>{selected.disponible ? 'Disponible' : 'No disponible'}</span>
        {product.variantes.length > 1 && <fieldset className="variant-picker"><legend>Elegí una variante</legend>{product.variantes.map((variant) => <label key={variant.id}><input type="radio" name="variant" checked={variant.id === selected.id} onChange={() => { const next = new URLSearchParams(searchParams); next.set('variantId', String(variant.id)); setSearchParams(next) }} /><span>{variant.caracteristicaNombre ? `${variant.caracteristicaNombre}: ${variant.valor}${variant.unidad ? ` ${variant.unidad}` : ''}` : variant.sku || `Opción ${variant.id}`}</span></label>)}</fieldset>}
        <div className="detail-cart-actions"><label>Cantidad <input type="number" min="1" max="9999" value={quantity} onChange={(event) => setQuantity(Number(event.target.value))} /></label><button className="accent-button" type="button" onClick={addToCart}>Agregar al carrito</button><button type="button" disabled={!whatsappNumber || quoting} onClick={requestQuote}>Pedir presupuesto</button></div>
        {!whatsappNumber && <p className="quote-notice">Falta configurar el número de WhatsApp para pedir presupuesto.</p>}
        <p className="detail-cart-feedback" role="status">{feedback}</p>
        {product.descripcion && <div className="detail-description"><h2>Descripción</h2><p>{product.descripcion}</p></div>}
        {product.caracteristicas.length > 0 && <div className="detail-attributes"><h2>Características</h2><dl>{product.caracteristicas.map((item) => <div key={item.caracteristicaId}><dt>{item.nombre}</dt><dd>{item.valor}{item.unidad ? ` ${item.unidad}` : ''}</dd></div>)}</dl></div>}
      </div>
    </div>
    {state.related.length > 0 && <section className="related-products"><span className="eyebrow">También puede interesarte</span><h2>Productos relacionados</h2><div className="product-grid">{state.related.map((item) => <ProductCard key={item.id} product={item} />)}</div></section>}
  </div>
}
