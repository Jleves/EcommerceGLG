import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { listProducts } from '../services/productService.js'

export function ProductManagementPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawPage = searchParams.get('page')
  const page = rawPage !== null && /^(0|[1-9]\d*)$/.test(rawPage) && Number.isSafeInteger(Number(rawPage))
    ? Number(rawPage) : 0
  const urlNombre = searchParams.get('nombre')
  const invalidUrlNombre = urlNombre !== null && !urlNombre.trim()
  const nombre = urlNombre === null ? undefined : urlNombre.trim()
  const key = `${page}\u0000${nombre ?? ''}`
  const [draft, setDraft] = useState(nombre ?? '')
  const [searchError, setSearchError] = useState('')
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ key, version, loading: true, data: null, error: null })
  const currentState = state.key === key && state.version === version
    ? state : { loading: true, data: null, error: null }

  useEffect(() => {
    setDraft(nombre ?? '')
    setSearchError('')
  }, [nombre])

  useEffect(() => {
    if (invalidUrlNombre) return undefined
    const controller = new AbortController()
    let current = true
    listProducts(page, nombre, { signal: controller.signal }).then(
      (data) => { if (current) setState({ key, version, loading: false, data, error: null }) },
      (error) => {
        if (current && error.name !== 'AbortError') setState({ key, version, loading: false, data: null, error })
      },
    )
    return () => { current = false; controller.abort() }
  }, [page, nombre, key, version, invalidUrlNombre])

  function search(event) {
    event.preventDefault()
    const trimmed = draft.trim()
    if (!trimmed) {
      setSearchError('Ingresá un nombre para buscar.')
      return
    }
    setSearchError('')
    setSearchParams({ nombre: trimmed, page: '0' })
    if (page === 0 && nombre === trimmed) setVersion((value) => value + 1)
  }

  function clearSearch() {
    setDraft('')
    setSearchError('')
    setSearchParams({ page: '0' })
    if (page === 0 && nombre === undefined) setVersion((value) => value + 1)
  }

  function goToPage(nextPage) {
    setSearchParams(nombre === undefined
      ? { page: String(nextPage) }
      : { nombre, page: String(nextPage) })
  }

  return (
    <section className="mx-auto max-w-5xl">
      <header className="mb-6">
        <h1 className="text-3xl font-bold text-slate-900">Productos</h1>
        <p className="mt-2 text-slate-600">Productos activos e inactivos del catálogo.</p>
      </header>
      <form className="mb-6" onSubmit={search}>
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid min-w-48 grow gap-1">
            <label className="font-semibold text-slate-800" htmlFor="product-name-search">Nombre</label>
            <input aria-describedby={(searchError || invalidUrlNombre) ? 'product-name-search-error' : undefined} aria-invalid={Boolean(searchError || invalidUrlNombre)} className="rounded-lg border border-slate-300 bg-white px-3 py-2" id="product-name-search" value={draft} onChange={(event) => { setDraft(event.target.value); setSearchError('') }} />
          </div>
          <button className="rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white" type="submit">Buscar</button>
          <button className="rounded-lg border border-slate-300 px-4 py-2 font-semibold" onClick={clearSearch} type="button">Limpiar búsqueda</button>
        </div>
        {(searchError || invalidUrlNombre) && <p className="mt-1 text-sm text-red-700" id="product-name-search-error" role="alert">Ingresá un nombre para buscar.</p>}
      </form>
      {invalidUrlNombre ? <p role="status">Corregí la búsqueda o limpiala para consultar productos.</p> : currentState.loading ? (
        <p role="status">Cargando productos…</p>
      ) : currentState.error ? (
        <div className="grid justify-items-start gap-3">
          <RequestError error={currentState.error} />
          <button className="rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white" onClick={() => setVersion((value) => value + 1)} type="button">Reintentar</button>
        </div>
      ) : (
        <>
          {currentState.data.content.length === 0 ? <p role="status">No hay productos en esta página.</p> : (
            <ul className="grid gap-3">
              {currentState.data.content.map((product) => (
                <li className="rounded-xl border border-slate-200 bg-white p-4" key={product.id}>
                  <h2 className="font-bold text-slate-900">{product.nombre}</h2>
                  {product.descripcion && <p className="mt-1 text-slate-700">{product.descripcion}</p>}
                  <p className="mt-2 text-sm text-slate-600">ID {product.id} · Categoría {product.categoriaId} · {product.activo ? 'Activo' : 'Inactivo'} · {product.disponible ? 'Disponible' : 'No disponible'}</p>
                  <p className="mt-1 text-sm text-slate-600">Precio de referencia: {product.precioReferencia} · {product.destacado ? 'Destacado' : 'No destacado'}</p>
                </li>
              ))}
            </ul>
          )}
          <nav aria-label="Páginas de productos" className="mt-6 flex items-center gap-4">
            <button className="rounded-lg border border-slate-300 px-4 py-2 disabled:opacity-50" disabled={page === 0} onClick={() => goToPage(page - 1)} type="button">Anterior</button>
            <span>Página {page + 1} de {Math.max(1, currentState.data.totalPages)} · {currentState.data.totalElements} productos</span>
            <button className="rounded-lg border border-slate-300 px-4 py-2 disabled:opacity-50" disabled={page + 1 >= currentState.data.totalPages} onClick={() => goToPage(page + 1)} type="button">Siguiente</button>
          </nav>
        </>
      )}
    </section>
  )
}
