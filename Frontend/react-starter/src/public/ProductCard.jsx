import { useState } from 'react'
import { Link } from 'react-router-dom'
import { IconPackage } from '@tabler/icons-react'
import { formatPrice } from './formatPrice.js'

export function ProductCard({ product }) {
  const [failed, setFailed] = useState(false)
  const link = `/products/${product.id}?variantId=${product.selectedVariantId}`
  return (
    <Link className="product-card" to={link}>
      <div className="product-card-image">
        {product.primaryThumbnailUrl && !failed ? <img src={product.primaryThumbnailUrl} alt="" loading="lazy" onError={() => setFailed(true)} /> : <IconPackage size={42} stroke={1.3} aria-hidden="true" />}
      </div>
      <div className="product-card-info">
        <span className="product-card-eyebrow">Producto</span>
        <h3>{product.nombre}</h3>
        <strong>{formatPrice(product.precioReferencia)}</strong>
        <span className="product-card-availability">{product.disponible ? 'Precio de referencia' : 'No disponible'}</span>
        <span className="product-card-action">Ver producto →</span>
      </div>
    </Link>
  )
}
