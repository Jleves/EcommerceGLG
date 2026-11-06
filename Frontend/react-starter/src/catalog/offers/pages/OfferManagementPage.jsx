import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { listProducts } from '../../products/services/productService.js'
import { OfferForm } from '../components/OfferForm.jsx'
import { deleteOffer, listOffers } from '../services/offerService.js'

function ProductPicker({ onSelect, onCancel }) {
  const [draft, setDraft] = useState('')
  const [name, setName] = useState(undefined)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const key = `${page}\u0000${name ?? ''}\u0000${revision}`
  const [state, setState] = useState({ key, loading: true, data: null, error: null })
  const currentState = state.key === key ? state : { loading: true, data: null, error: null }

  useEffect(() => {
    const controller = new AbortController()
    listProducts(page, name, { signal: controller.signal }).then(
      (data) => { if (!controller.signal.aborted) setState({ key, loading: false, data, error: null }) },
      (error) => { if (!controller.signal.aborted) setState({ key, loading: false, data: null, error }) },
    )
    return () => controller.abort()
  }, [page, name, revision, key])

  return <div className="grid gap-4">
    <form onSubmit={(event) => { event.preventDefault(); setPage(0); setName(draft.trim() || undefined) }} className="flex gap-2">
      <label className="grid grow gap-1">Buscar producto por nombre<input value={draft} onChange={(event) => setDraft(event.target.value)} className="rounded border p-2" /></label>
      <button type="submit" className="self-end rounded bg-emerald-700 px-4 py-2 text-white">Buscar</button>
    </form>
    {currentState.loading ? <p role="status">Cargando productos…</p> : currentState.error ? <div className="grid gap-2"><RequestError error={currentState.error} /><button type="button" onClick={() => setRevision((value) => value + 1)}>Reintentar</button></div> : <>
      {currentState.data.content.length === 0 ? <p>No hay productos en esta página.</p> : <ul className="grid gap-2">
        {currentState.data.content.map((product) => <li key={product.id} className="flex items-center justify-between gap-3 rounded border p-2">
          <span>{product.nombre} · ID {product.id} {product.activo ? '' : '· Inactivo'}</span>
          <button type="button" disabled={!product.activo || !product.variantes?.some((variant) => variant.activo)} onClick={() => onSelect(product.id)} className="rounded border px-3 py-1 disabled:opacity-50">Elegir</button>
        </li>)}
      </ul>}
      <nav aria-label="Páginas de selección de productos" className="flex items-center gap-3">
        <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button>
        <span>Página {page + 1} de {Math.max(1, currentState.data.totalPages)}</span>
        <button type="button" disabled={page + 1 >= currentState.data.totalPages} onClick={() => setPage(page + 1)}>Siguiente</button>
      </nav>
    </>}
    <button type="button" onClick={onCancel} className="justify-self-end">Cerrar</button>
  </div>
}

export function OfferManagementPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawPage = searchParams.get('page')
  const page = rawPage !== null && /^(0|[1-9]\d*)$/.test(rawPage) && Number.isSafeInteger(Number(rawPage)) ? Number(rawPage) : 0
  const [version, setVersion] = useState(0)
  const key = `${page}\u0000${version}`
  const [state, setState] = useState({ key, loading: true, data: null, error: null })
  const currentState = state.key === key ? state : { loading: true, data: null, error: null }
  const [editing, setEditing] = useState(null)
  const [confirming, setConfirming] = useState(null)
  const [mutationError, setMutationError] = useState(null)
  const [needsReview, setNeedsReview] = useState(false)
  const [pending, setPending] = useState(false)
  const [formBusy, setFormBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const inFlight = useRef(false)
  const editorDialog = useRef(null)
  const deleteDialog = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    listOffers(page, { signal: controller.signal }).then(
      (data) => {
        if (controller.signal.aborted) return
        if (page > 0 && data.content.length === 0 && page >= data.totalPages) setSearchParams({ page: String(Math.max(0, data.totalPages - 1)) })
        else setState({ key, loading: false, data, error: null })
      },
      (error) => { if (!controller.signal.aborted) setState({ key, loading: false, data: null, error }) },
    )
    return () => controller.abort()
  }, [page, version, key, setSearchParams])

  useEffect(() => {
    if (editing && editorDialog.current && !editorDialog.current.open) {
      if (editorDialog.current.showModal) editorDialog.current.showModal()
      else editorDialog.current.setAttribute('open', '')
    }
  }, [editing])
  useEffect(() => {
    if (confirming && deleteDialog.current && !deleteDialog.current.open) {
      if (deleteDialog.current.showModal) deleteDialog.current.showModal()
      else deleteDialog.current.setAttribute('open', '')
    }
  }, [confirming])

  function refresh() {
    setVersion((value) => value + 1)
  }

  function saved() {
    setEditing(null)
    setNotice('Oferta guardada.')
    refresh()
  }

  async function terminate() {
    if (!confirming || inFlight.current || needsReview) return
    inFlight.current = true
    setPending(true)
    setMutationError(null)
    try {
      await deleteOffer(confirming.id)
      setConfirming(null)
      setNotice('Oferta terminada.')
      refresh()
    } catch (error) {
      setMutationError(error)
      if (!error?.status || error.status >= 500) setNeedsReview(true)
      if (error?.status === 409) refresh()
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  function review() {
    setNeedsReview(false)
    setConfirming(null)
    setMutationError(null)
    refresh()
  }

  return <section className="mx-auto max-w-5xl">
    <header className="mb-6 flex items-center justify-between gap-3">
      <div><h1 className="text-3xl font-bold">Ofertas</h1><p className="mt-2 text-slate-600">Gestión de precios finales por variante.</p></div>
      <button type="button" disabled={currentState.loading || needsReview} onClick={() => setEditing({ picker: true })} className="rounded bg-emerald-700 px-4 py-2 font-semibold text-white disabled:opacity-50">Crear oferta</button>
    </header>
    {notice && <p role="status" className="mb-4 rounded bg-emerald-50 p-3">{notice}</p>}
    {mutationError && <RequestError error={mutationError} />}
    {needsReview && <div className="mb-4 grid gap-2 rounded border border-amber-300 bg-amber-50 p-3"><p role="status">Resultado incierto. Consultá la lista antes de intentar otra baja.</p><button type="button" onClick={review}>Consultar estado actual</button></div>}
    {editing && <dialog ref={editorDialog} aria-label="Gestionar oferta" onCancel={(event) => { if (formBusy) event.preventDefault(); else setEditing(null) }} className="m-auto max-h-[90vh] w-[min(94vw,40rem)] overflow-y-auto rounded-xl border-0 p-5 shadow-2xl backdrop:bg-slate-950/60">
      <h2 className="mb-4 text-xl font-bold">{editing.picker ? 'Elegir producto' : 'Oferta por variante'}</h2>
      {editing.picker ? <ProductPicker onSelect={(productId) => setEditing({ productId })} onCancel={() => setEditing(null)} />
        : <OfferForm key={editing.productId} productId={editing.productId} initialVariantId={editing.variantId} onSaved={saved} onBusyChange={setFormBusy} onCancel={() => setEditing(null)} />}
    </dialog>}
    {confirming && <dialog ref={deleteDialog} aria-label="Terminar oferta" onCancel={(event) => { if (pending || needsReview) event.preventDefault(); else setConfirming(null) }} className="m-auto w-[min(94vw,30rem)] rounded-xl border-0 p-5 shadow-2xl backdrop:bg-slate-950/60">
      <h2 className="text-xl font-bold">Terminar oferta</h2>
      <p className="my-3">¿Confirmás terminar la oferta de {confirming.productoNombre}, variante ID {confirming.varianteId}? La fila se eliminará.</p>
      <div className="flex justify-end gap-3"><button type="button" disabled={pending || needsReview} onClick={() => setConfirming(null)}>Cancelar</button><button type="button" disabled={pending || needsReview} onClick={terminate} className="rounded bg-red-700 px-4 py-2 text-white disabled:opacity-50">Confirmar terminación</button></div>
    </dialog>}
    {currentState.loading ? <p role="status">Cargando ofertas…</p> : currentState.error ? <div className="grid gap-3"><RequestError error={currentState.error} /><button type="button" onClick={refresh}>Reintentar</button></div> : <>
      {currentState.data.content.length === 0 ? <p role="status">No hay ofertas en esta página.</p> : <ul className="grid gap-3">
        {currentState.data.content.map((offer) => <li key={offer.id} className="rounded-xl border p-4">
          <h2 className="font-bold">{offer.productoNombre} · Variante ID {offer.varianteId}</h2>
          <p>{offer.sku ? `SKU ${offer.sku} · ` : ''}Precio de referencia {offer.precioReferencia} · Oferta {offer.precioOferta} · {offer.porcentajeDescuento === 0 ? '<1 %' : `${offer.porcentajeDescuento} %`}</p>
          <p>{offer.productoActivo && offer.varianteActiva ? 'Publicada' : 'Oculta temporalmente'} · {offer.disponible ? 'Disponible' : 'No disponible'}</p>
          <div className="mt-3 flex gap-3"><button type="button" disabled={needsReview || pending} onClick={() => setEditing({ productId: offer.productoId, variantId: offer.varianteId })}>Editar</button><button type="button" disabled={needsReview || pending} onClick={() => setConfirming(offer)}>Terminar</button></div>
        </li>)}
      </ul>}
      <nav aria-label="Páginas de ofertas" className="mt-6 flex gap-3"><button type="button" disabled={page === 0} onClick={() => setSearchParams({ page: String(page - 1) })}>Anterior</button><span>Página {page + 1} de {Math.max(1, currentState.data.totalPages)} · {currentState.data.totalElements} ofertas</span><button type="button" disabled={page + 1 >= currentState.data.totalPages} onClick={() => setSearchParams({ page: String(page + 1) })}>Siguiente</button></nav>
    </>}
  </section>
}
