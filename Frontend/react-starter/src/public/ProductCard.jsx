import { useState } from 'react'
import { Link } from 'react-router-dom'
import { IconPackage } from '@tabler/icons-react'
import { formatPrice } from './formatPrice.js'
import { getPublicProduct } from './publicService.js'
import { useCart } from './cartContext.js'

export function ProductCard({ product }) {
  const [failed, setFailed] = useState(false)
  const [adding, setAdding] = useState(false)
  const [feedback, setFeedback] = useState('')
  const { add } = useCart()
  const link = `/products/${product.id}?variantId=${product.selectedVariantId}`
  async function addToCart() {
    setAdding(true)
    setFeedback('')
    try {
      const detail = await getPublicProduct(product.id)
      const variant = detail.variantes.find((item) => item.id === product.selectedVariantId)
      if (!variant) throw new Error('Variante no disponible')
      add(detail, variant, 1)
      setFeedback('Agregado al carrito')
    } catch { setFeedback('No pudimos agregar el producto. Reintentá.') }
    finally { setAdding(false) }
  }
  return (
    <article className="product-card">
      <Link className="product-card-link" to={link}>
      <div className="product-card-image">
        {product.primaryThumbnailUrl && !failed ? <img src={product.primaryThumbnailUrl} alt="" loading="lazy" onError={() => setFailed(true)} /> : <IconPackage size={42} stroke={1.3} aria-hidden="true" />}
      </div>
      <div className="product-card-info">
        <span className="product-card-eyebrow">Producto</span>
        <h3>{product.nombre}</h3>
        {product.precioOferta != null ? <div className="offer-price"><del aria-label={`Precio original ${formatPrice(product.precioReferencia)}`}>{formatPrice(product.precioReferencia)}</del><strong>{formatPrice(product.precioOferta)}</strong><span className="offer-discount">{product.porcentajeDescuento === 0 ? '<1 %' : `${product.porcentajeDescuento} %`} de descuento</span></div> : <strong>{formatPrice(product.precioReferencia)}</strong>}
        <span className="product-card-availability">{product.disponible ? product.precioOferta != null ? 'Precio de oferta' : 'Precio de referencia' : 'No disponible'}</span>
        <span className="product-card-action">Ver producto →</span>
      </div>
      </Link>
      <div className="product-card-footer"><button type="button" disabled={adding} onClick={addToCart}>{adding ? 'Agregando…' : 'Agregar al carrito'}</button><span role="status">{feedback}</span></div>
    </article>
  )
}
