import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ProductCard } from './ProductCard.jsx'
import { getPublicOffers } from './publicService.js'

export function OffersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawPage = searchParams.get('page')
  const page = rawPage && /^\d+$/.test(rawPage) ? Number(rawPage) : 0
  const [result, setResult] = useState({ loading: true, data: null, error: null })

  useEffect(() => {
    const controller = new AbortController()
    setResult({ loading: true, data: null, error: null })
    getPublicOffers(page, 20, { signal: controller.signal }).then(
      (data) => setResult({ loading: false, data, error: null }),
      (error) => { if (error.name !== 'AbortError') setResult({ loading: false, data: null, error }) },
    )
    return () => controller.abort()
  }, [page])

  function updatePage(nextPage) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  return <section className="offers-page public-container">
    <div className="catalog-intro"><span className="eyebrow">Ofertas para tu obra</span><h1>Ofertas</h1><p>Encontrá materiales con precio especial para tu próximo proyecto.</p></div>
    {result.loading ? <p role="status">Cargando ofertas…</p> : result.error ? <p role="alert">No pudimos cargar las ofertas. Probá nuevamente más tarde.</p> : result.data.content.length === 0 ? <div className="empty-results"><p>Todavía no hay ofertas disponibles.</p><Link to="/catalog">Explorar el catálogo</Link></div> : <div className="product-grid">{result.data.content.map((product) => <ProductCard key={product.id} product={product} />)}</div>}
    {result.data?.totalPages > 1 && <div className="pagination"><button type="button" disabled={page === 0} onClick={() => updatePage(page - 1)}>Anterior</button><span>Página {page + 1} de {result.data.totalPages}</span><button type="button" disabled={page + 1 >= result.data.totalPages} onClick={() => updatePage(page + 1)}>Siguiente</button></div>}
  </section>
}
