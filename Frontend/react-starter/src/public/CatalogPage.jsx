import { useEffect, useState } from 'react'
import { useOutletContext, useSearchParams } from 'react-router-dom'
import { IconSearch } from '@tabler/icons-react'
import { ProductCard } from './ProductCard.jsx'
import { getPublicProducts } from './publicService.js'

export function CatalogPage() {
  const { catalog } = useOutletContext()
  const [searchParams, setSearchParams] = useSearchParams()
  const q = searchParams.get('q')?.trim() ?? ''
  const categoriaId = searchParams.get('categoriaId') ?? ''
  const marca = searchParams.get('marca') ?? ''
  const atributos = searchParams.getAll('atributo')
  const rawPage = searchParams.get('page')
  const page = rawPage && /^\d+$/.test(rawPage) ? Number(rawPage) : 0
  const [draft, setDraft] = useState(q)
  const [result, setResult] = useState({ loading: true, data: null, error: null })
  const key = searchParams.toString()

  useEffect(() => setDraft(q), [q])
  useEffect(() => {
    const controller = new AbortController()
    setResult({ loading: true, data: null, error: null })
    const params = new URLSearchParams(key)
    const rawPage = params.get('page')
    const requestPage = rawPage && /^\d+$/.test(rawPage) ? Number(rawPage) : 0
    getPublicProducts({ page: requestPage, q: params.get('q')?.trim() ?? '',
      categoriaId: params.get('categoriaId') ?? '', marca: params.get('marca') ?? '',
      atributos: params.getAll('atributo') }, { signal: controller.signal }).then(
      (data) => setResult({ loading: false, data, error: null }),
      (error) => { if (error.name !== 'AbortError') setResult({ loading: false, data: null, error }) },
    )
    return () => controller.abort()
  }, [key])

  function updateParams(changes) {
    const next = new URLSearchParams(searchParams)
    for (const [name, value] of Object.entries(changes)) {
      if (value) next.set(name, value)
      else next.delete(name)
    }
    next.delete('page')
    setSearchParams(next)
  }
  function search(event) { event.preventDefault(); updateParams({ q: draft.trim() }) }
  function changeAttribute(id, value) {
    const next = new URLSearchParams(searchParams)
    const remaining = next.getAll('atributo').filter((item) => !item.startsWith(`${id}:`))
    next.delete('atributo')
    for (const item of remaining) next.append('atributo', item)
    if (value) next.append('atributo', `${id}:${value}`)
    next.delete('page')
    setSearchParams(next)
  }

  const categories = catalog.data?.categories ?? []
  const facets = catalog.data?.facets ?? []
  const brands = facets.find((facet) => facet.name.toLowerCase() === 'marca')?.values ?? []
  return <section className="catalog-page public-container">
    <div className="catalog-intro"><span className="eyebrow">Catálogo público</span><h1>Materiales para tu obra</h1><p>Buscá productos y explorá las categorías disponibles.</p></div>
    <form className="catalog-search" role="search" onSubmit={search}><IconSearch size={20} aria-hidden="true" /><input aria-label="Buscar en el catálogo" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="Producto, marca o palabra clave" /><button className="purple-button" type="submit">Buscar</button></form>
    <div className="catalog-layout"><aside className="catalog-filters" aria-label="Filtros de productos"><h2>Filtrar productos</h2>
      {catalog.error && <p role="alert">No se pudieron cargar los filtros.</p>}
      <label>Categoría<select value={categoriaId} onChange={(event) => updateParams({ categoriaId: event.target.value })}><option value="">Todas</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.nombre}</option>)}</select></label>
      <label>Marca<select value={marca} onChange={(event) => updateParams({ marca: event.target.value })}><option value="">Todas</option>{brands.map((brand) => <option key={brand} value={brand}>{brand}</option>)}</select></label>
      {facets.filter((facet) => facet.name.toLowerCase() !== 'marca').map((facet) => <label key={facet.id}>{facet.name}<select value={atributos.find((item) => item.startsWith(`${facet.id}:`))?.slice(String(facet.id).length + 1) ?? ''} onChange={(event) => changeAttribute(facet.id, event.target.value)}><option value="">Todos</option>{facet.values.map((value) => <option key={value} value={value}>{value}</option>)}</select></label>)}
      <button className="clear-filters" type="button" onClick={() => { setDraft(''); setSearchParams({}) }}>Limpiar filtros</button>
    </aside>
      <div className="catalog-results"><div className="results-title"><h2>Productos</h2>{result.data && <span>{result.data.totalElements} resultados</span>}</div>
        {result.loading ? <p role="status">Cargando productos…</p> : result.error ? <p role="alert">No pudimos cargar los productos. Probá nuevamente.</p> : result.data.content.length === 0 ? <p className="empty-results">No encontramos productos con esos criterios. Probá otra búsqueda o limpiá los filtros.</p> : <div className="product-grid">{result.data.content.map((product) => <ProductCard key={product.id} product={product} />)}</div>}
        {result.data?.totalPages > 1 && <div className="pagination"><button type="button" disabled={page === 0} onClick={() => updatePage(page - 1)}>Anterior</button><span>Página {page + 1} de {result.data.totalPages}</span><button type="button" disabled={page + 1 >= result.data.totalPages} onClick={() => updatePage(page + 1)}>Siguiente</button></div>}
      </div>
    </div>
  </section>

  function updatePage(nextPage) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }
}
