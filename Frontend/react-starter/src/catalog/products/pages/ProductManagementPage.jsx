import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { listCategories } from '../../categories/services/categoryService.js'
import { listCharacteristics } from '../../characteristics/services/characteristicService.js'
import { listSubcategories } from '../../subcategories/services/subcategoryService.js'
import { ProductForm } from '../components/ProductForm.jsx'
import { ProductImageManager } from '../components/ProductImageManager.jsx'
import { createProduct, deactivateProduct, getProduct, listProducts, reactivateProduct, updateProduct } from '../services/productService.js'

const buttonInteraction = 'shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50'
const greenButton = `${buttonInteraction} bg-emerald-700 text-white enabled:hover:bg-emerald-800 focus-visible:outline-emerald-700`
const amberButton = `${buttonInteraction} bg-amber-800 text-white enabled:hover:bg-amber-900 focus-visible:outline-amber-800`
const lightButton = `${buttonInteraction} border border-slate-300 bg-white text-slate-800 enabled:hover:border-slate-400 enabled:hover:bg-slate-50 focus-visible:outline-slate-500`
const deactivateButton = `${buttonInteraction} bg-[#B42318] text-white enabled:hover:bg-[#912018] focus-visible:outline-[#B42318]`
const reactivateButton = `${buttonInteraction} bg-[#047857] text-white enabled:hover:bg-[#065F46] focus-visible:outline-[#047857]`
const activeCard = 'border-[#A7F3D0] bg-[#ECFDF5] text-[#064E3B]'
const inactiveCard = 'border-[#FECDD3] bg-[#FFF1F2] text-[#881337]'

export function ProductManagementPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawPage = searchParams.get('page')
  const page = rawPage !== null && /^(0|[1-9]\d*)$/.test(rawPage) && Number.isSafeInteger(Number(rawPage))
    ? Number(rawPage) : 0
  const urlNombre = searchParams.get('nombre')
  const invalidUrlNombre = urlNombre !== null && !urlNombre.trim()
  const nombre = urlNombre === null ? undefined : urlNombre.trim()
  const categoriaId = searchParams.get('categoriaId') || undefined
  const subcategoriaId = searchParams.get('subcategoriaId') || undefined
  const key = `${page}\u0000${nombre ?? ''}\u0000${categoriaId ?? ''}\u0000${subcategoriaId ?? ''}`
  const [draft, setDraft] = useState(nombre ?? '')
  const [searchError, setSearchError] = useState('')
  const [filters, setFilters] = useState({ categories: [], subcategories: [], error: null })
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ key, version, loading: true, data: null, error: null })
  const [formTarget, setFormTarget] = useState(null)
  const [imageTarget, setImageTarget] = useState(null)
  const [formData, setFormData] = useState(null)
  const [formError, setFormError] = useState(null)
  const [mutationError, setMutationError] = useState(null)
  const [statusError, setStatusError] = useState(null)
  const [confirming, setConfirming] = useState(null)
  const [pending, setPending] = useState(false)
  const [needsReview, setNeedsReview] = useState(false)
  const [notice, setNotice] = useState('')
  const inFlight = useRef(false)
  const reviewing = useRef(false)
  const dialog = useRef(null)
  const confirmDialog = useRef(null)
  const loadingForm = formTarget !== null && formData === null && !formError
  const currentState = state.key === key && state.version === version
    ? state : { loading: true, data: null, error: null }

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    Promise.all([listCategories({ signal: controller.signal }), listSubcategories(undefined, { signal: controller.signal })]).then(
      ([categories, subcategories]) => { if (current) setFilters({ categories, subcategories, error: null }) },
      (error) => { if (current && error.name !== 'AbortError') setFilters({ categories: [], subcategories: [], error }) },
    )
    return () => { current = false; controller.abort() }
  }, [version])

  useEffect(() => {
    if (!notice) return undefined
    const timeout = window.setTimeout(() => setNotice(''), 4000)
    return () => window.clearTimeout(timeout)
  }, [notice])

  useEffect(() => {
    setDraft(nombre ?? '')
    setSearchError('')
  }, [nombre])

  useEffect(() => {
    if (invalidUrlNombre) return undefined
    const controller = new AbortController()
    let current = true
    listProducts(page, nombre, { signal: controller.signal, ...(categoriaId && { categoriaId }), ...(subcategoriaId && { subcategoriaId }) }).then(
      (data) => {
        if (!current) return
        if (page > 0 && data.content.length === 0 && page >= data.totalPages) {
          const lastPage = Math.max(0, data.totalPages - 1)
          setSearchParams({ ...(nombre !== undefined && { nombre }), ...(categoriaId && { categoriaId }), ...(subcategoriaId && { subcategoriaId }), page: String(lastPage) })
        } else {
          setState({ key, version, loading: false, data, error: null })
          if (reviewing.current) {
            reviewing.current = false
            setNeedsReview(false)
            setNotice('Estado actual consultado. Revisá la lista antes de enviar otra solicitud.')
          }
        }
      },
      (error) => {
        if (current && error.name !== 'AbortError') setState({ key, version, loading: false, data: null, error })
      },
    )
    return () => { current = false; controller.abort() }
  }, [page, nombre, categoriaId, subcategoriaId, key, version, invalidUrlNombre, setSearchParams])

  useEffect(() => {
    if (!formTarget) return undefined
    const controller = new AbortController()
    let current = true
    Promise.all([
      listCategories({ signal: controller.signal }),
      listSubcategories(undefined, { signal: controller.signal }),
      listCharacteristics({ signal: controller.signal }),
      formTarget === 'create' ? Promise.resolve(null) : getProduct(formTarget, { signal: controller.signal }),
    ]).then(
      ([categories, subcategories, characteristics, product]) => { if (current) setFormData({ categories, subcategories, characteristics, product }) },
      (error) => { if (current && error.name !== 'AbortError') setFormError(error) },
    )
    return () => { current = false; controller.abort() }
  }, [formTarget])

  useEffect(() => {
    if (formTarget && dialog.current && !dialog.current.open) {
      if (dialog.current.showModal) dialog.current.showModal()
      else dialog.current.setAttribute('open', '')
    }
  }, [formTarget])

  useEffect(() => {
    if (confirming && confirmDialog.current && !confirmDialog.current.open) {
      if (confirmDialog.current.showModal) confirmDialog.current.showModal()
      else confirmDialog.current.setAttribute('open', '')
    }
  }, [confirming])

  function openForm(target) {
    if (inFlight.current || needsReview || confirming || imageTarget || currentState.loading || currentState.error) return
    setFormData(null)
    setFormError(null)
    setMutationError(null)
    setNotice('')
    setFormTarget(target)
  }

  function closeForm() {
    if (inFlight.current) return
    setFormTarget(null)
    setFormData(null)
    setMutationError(null)
  }

  async function submitProduct(payload) {
    if (inFlight.current || needsReview || !formData || currentState.loading || currentState.error) return
    inFlight.current = true
    setPending(true)
    setMutationError(null)
    try {
      if (formTarget === 'create') await createProduct(payload)
      else await updateProduct(formTarget, payload)
      setNotice(formTarget === 'create' ? 'Producto creado. Para cargar fotos, buscálo en la lista y abrí «Imágenes».' : 'Producto actualizado.')
      setFormTarget(null)
      setFormData(null)
      setVersion((value) => value + 1)
    } catch (error) {
      setMutationError(error)
      if (!error?.status || error.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  function requestStatusChange(product) {
    if (inFlight.current || needsReview || formTarget || imageTarget || confirming || currentState.loading || currentState.error) return
    setStatusError(null)
    setNotice('')
    setConfirming(product)
  }

  async function confirmStatusChange() {
    if (!confirming || inFlight.current || needsReview || currentState.loading || currentState.error) return
    const product = confirming
    inFlight.current = true
    setPending(true)
    setConfirming(null)
    setStatusError(null)
    try {
      if (product.activo) await deactivateProduct(product.id)
      else await reactivateProduct(product.id)
      setNotice(product.activo ? 'Producto desactivado.' : 'Producto reactivado.')
      setVersion((value) => value + 1)
    } catch (error) {
      setStatusError(error)
      if (!error?.status || error.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  function reviewCurrentState() {
    reviewing.current = true
    setStatusError(null)
    setVersion((value) => value + 1)
    setState({ key, version: version + 1, loading: true, data: null, error: null })
  }

  function search(event) {
    event.preventDefault()
    const trimmed = draft.trim()
    if (!trimmed && !categoriaId && !subcategoriaId) {
      setSearchError('Ingresá un nombre para buscar.')
      return
    }
    setSearchError('')
    setSearchParams({ ...(trimmed && { nombre: trimmed }), ...(categoriaId && { categoriaId }), ...(subcategoriaId && { subcategoriaId }), page: '0' })
    if (page === 0 && nombre === trimmed) setVersion((value) => value + 1)
  }

  function clearSearch() {
    setDraft('')
    setSearchError('')
    setSearchParams({ page: '0' })
    if (page === 0 && nombre === undefined) setVersion((value) => value + 1)
  }

  function goToPage(nextPage) {
    setSearchParams({ ...(nombre !== undefined && { nombre }), ...(categoriaId && { categoriaId }), ...(subcategoriaId && { subcategoriaId }), page: String(nextPage) })
  }

  function changeCategoryFilter(value) {
    setSearchParams({ ...(nombre !== undefined && { nombre }), ...(value && { categoriaId: value }), page: '0' })
  }

  function changeSubcategoryFilter(value) {
    setSearchParams({ ...(nombre !== undefined && { nombre }), ...(categoriaId && { categoriaId }), ...(value && { subcategoriaId: value }), page: '0' })
  }

  return (
    <section className="mx-auto max-w-5xl">
      <header className="mb-6">
        <h1 className="text-3xl font-bold text-slate-900">Productos</h1>
        <p className="mt-2 text-slate-600">Productos activos e inactivos del catálogo.</p>
      </header>
      <div className="mb-5 flex justify-end">
        <button aria-label="Crear producto" className={`rounded-lg px-4 py-2 font-semibold ${greenButton}`} disabled={invalidUrlNombre || currentState.loading || Boolean(currentState.error) || needsReview || Boolean(formTarget) || Boolean(imageTarget) || Boolean(confirming) || pending} onClick={() => openForm('create')} type="button">
          <span className="sm:hidden" aria-hidden="true">+</span>
          <span className="hidden sm:inline" aria-hidden="true">Crear producto</span>
        </button>
      </div>
      {notice && <p role="status" className="mb-4 rounded bg-emerald-50 p-3">{notice}</p>}
      {needsReview && <div className="mb-4 grid justify-items-start gap-3 rounded border border-amber-300 bg-amber-50 p-4">
        <p role="status">No pudimos confirmar el resultado. Consultá el estado actual antes de enviar otro cambio; la lista no confirma por sí sola si la solicitud anterior se aplicó.</p>
        <button type="button" disabled={currentState.loading} onClick={reviewCurrentState} className={`rounded-lg px-4 py-2 font-semibold ${amberButton}`}>Consultar estado actual</button>
      </div>}
      {statusError && <div className="mb-4 grid justify-items-start gap-3"><RequestError error={statusError} />{!needsReview && <button type="button" disabled={currentState.loading} onClick={reviewCurrentState} className={`rounded-lg px-4 py-2 font-semibold ${amberButton}`}>Consultar estado actual</button>}</div>}
      {confirming && <dialog ref={confirmDialog} aria-labelledby="product-status-title" aria-describedby="product-status-description" onCancel={() => setConfirming(null)} className="m-auto w-[min(92vw,27rem)] rounded-2xl border-0 bg-white p-0 text-slate-900 shadow-2xl backdrop:bg-slate-950/50">
        <div className={`border-t-4 p-5 sm:p-6 ${confirming.activo ? 'border-[#B42318]' : 'border-[#047857]'}`}>
          <h2 id="product-status-title" className="text-lg font-bold">{confirming.activo ? 'Desactivar' : 'Reactivar'} {confirming.nombre}</h2>
          <p id="product-status-description" className="mt-2 text-sm text-slate-700">¿Confirmás {confirming.activo ? 'la desactivación' : 'la reactivación'} de {confirming.nombre}?</p>
          <div className="mt-5 flex flex-wrap justify-end gap-3">
            <button type="button" onClick={() => setConfirming(null)} className={`rounded-lg px-4 py-2 font-semibold ${lightButton}`}>Cancelar</button>
            <button type="button" onClick={confirmStatusChange} className={`rounded-lg px-4 py-2 font-semibold ${confirming.activo ? deactivateButton : reactivateButton}`}>Confirmar {confirming.activo ? 'desactivación' : 'reactivación'}</button>
          </div>
        </div>
      </dialog>}
      {formTarget && <dialog ref={dialog} aria-labelledby="product-form-title" onCancel={(event) => { if (inFlight.current) event.preventDefault(); else closeForm() }} className="m-auto max-h-[90vh] w-[min(94vw,42rem)] overflow-y-auto rounded-xl border-0 p-0 shadow-2xl backdrop:bg-slate-950/60">
        {needsReview && <p role="status" className="m-4 rounded bg-amber-50 p-3">Resultado incierto. Consultá el estado actual antes de guardar otra vez.</p>}
        {loadingForm && <p role="status" className="p-5">Cargando formulario…</p>}
        {formError && <div className="grid gap-3 p-5"><RequestError error={formError} /><button type="button" onClick={closeForm} className={`justify-self-start rounded-lg px-4 py-2 font-semibold ${lightButton}`}>Cerrar</button></div>}
        {formData && <ProductForm initialValues={formData.product} categories={formData.categories} subcategories={formData.subcategories} characteristics={formData.characteristics} onSubmit={submitProduct} onCancel={closeForm} pending={pending || currentState.loading || Boolean(currentState.error)} blocked={needsReview} error={mutationError} />}
      </dialog>}
      {imageTarget && <ProductImageManager key={imageTarget.id} product={imageTarget} onClose={() => setImageTarget(null)} />}
      <form className="mb-6" onSubmit={search}>
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid min-w-48 grow gap-1">
            <label className="font-semibold text-slate-800" htmlFor="product-name-search">Nombre</label>
            <input aria-describedby={(searchError || invalidUrlNombre) ? 'product-name-search-error' : undefined} aria-invalid={Boolean(searchError || invalidUrlNombre)} className="rounded-lg border border-slate-300 bg-white px-3 py-2" id="product-name-search" value={draft} onChange={(event) => { setDraft(event.target.value); setSearchError('') }} />
          </div>
          <button className={`rounded-lg px-4 py-2 font-semibold ${greenButton}`} disabled={pending || Boolean(confirming)} type="submit">Buscar</button>
          <button className={`rounded-lg px-4 py-2 font-semibold ${lightButton}`} disabled={pending || Boolean(confirming)} onClick={clearSearch} type="button">Limpiar búsqueda</button>
        </div>
        {(searchError || invalidUrlNombre) && <p className="mt-1 text-sm text-red-700" id="product-name-search-error" role="alert">Ingresá un nombre para buscar.</p>}
      </form>
      <div className="mb-5 flex flex-wrap gap-3">
        <label htmlFor="product-category-filter">Filtrar categoría
          <select id="product-category-filter" value={categoriaId ?? ''} onChange={(event) => changeCategoryFilter(event.target.value)} className="ml-2 rounded border p-2">
            <option value="">Todas</option>
            {filters.categories.map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
          </select>
        </label>
        <label htmlFor="product-subcategory-filter">Filtrar subcategoría
          <select id="product-subcategory-filter" value={subcategoriaId ?? ''} onChange={(event) => changeSubcategoryFilter(event.target.value)} className="ml-2 rounded border p-2">
            <option value="">Todas</option>
            {filters.subcategories.filter((item) => !categoriaId || String(item.categoriaId) === categoriaId).map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
          </select>
        </label>
        {filters.error && <RequestError error={filters.error} />}
      </div>
      {invalidUrlNombre ? <p role="status">Corregí la búsqueda o limpiala para consultar productos.</p> : currentState.loading ? (
        <p role="status">Cargando productos…</p>
      ) : currentState.error ? (
        <div className="grid justify-items-start gap-3">
          <RequestError error={currentState.error} />
          <button className={`rounded-lg px-4 py-2 font-semibold ${greenButton}`} onClick={() => setVersion((value) => value + 1)} type="button">Reintentar</button>
        </div>
      ) : (
        <>
          {currentState.data.content.length === 0 ? <p role="status">No hay productos en esta página.</p> : (
            <ul className="grid gap-3">
              {currentState.data.content.map((product) => (
                <li className={`rounded-xl border p-4 ${product.activo ? activeCard : inactiveCard}`} key={product.id}>
                  <h2 className="font-bold">{product.nombre}</h2>
                  {product.descripcion && <p className="mt-1">{product.descripcion}</p>}
                  <p className="mt-2 text-sm">ID {product.id} · Categoría {product.categoriaId} · {product.activo ? 'Activo' : 'Inactivo'} · {product.disponible ? 'Disponible' : 'No disponible'}</p>
                  <p className="text-sm">Subcategoría: {product.subcategoriaId ? (filters.subcategories.find((item) => item.id === product.subcategoriaId)?.nombre ?? `ID ${product.subcategoriaId}`) : 'Sin subcategoría'}</p>
                  <p className="mt-1 text-sm">Precio de referencia: {product.precioReferencia} · {product.destacado ? 'Destacado' : 'No destacado'}</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button aria-label={`Editar ${product.nombre}`} className={`rounded-lg bg-slate-800 px-4 py-2 font-semibold text-white enabled:hover:bg-slate-700 focus-visible:outline-slate-800 ${buttonInteraction}`} disabled={needsReview || Boolean(formTarget) || Boolean(imageTarget) || Boolean(confirming) || pending} onClick={() => openForm(product.id)} type="button">Editar</button>
                    <button aria-label={`Imágenes de ${product.nombre}`} className={`rounded-lg px-4 py-2 font-semibold ${lightButton}`} disabled={needsReview || Boolean(formTarget) || Boolean(imageTarget) || Boolean(confirming) || pending} onClick={() => setImageTarget(product)} type="button">Imágenes</button>
                    <button aria-label={`${product.activo ? 'Desactivar' : 'Reactivar'} ${product.nombre}`} className={`rounded-lg px-4 py-2 font-semibold ${product.activo ? deactivateButton : reactivateButton}`} disabled={needsReview || Boolean(formTarget) || Boolean(imageTarget) || Boolean(confirming) || pending} onClick={() => requestStatusChange(product)} type="button">{product.activo ? 'Desactivar' : 'Reactivar'}</button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <nav aria-label="Páginas de productos" className="mt-6 flex items-center gap-4">
            <button className={`rounded-lg px-4 py-2 font-semibold ${lightButton}`} disabled={page === 0 || pending || Boolean(confirming)} onClick={() => goToPage(page - 1)} type="button">Anterior</button>
            <span>Página {page + 1} de {Math.max(1, currentState.data.totalPages)} · {currentState.data.totalElements} productos</span>
            <button className={`rounded-lg px-4 py-2 font-semibold ${lightButton}`} disabled={page + 1 >= currentState.data.totalPages || pending || Boolean(confirming)} onClick={() => goToPage(page + 1)} type="button">Siguiente</button>
          </nav>
        </>
      )}
    </section>
  )
}
