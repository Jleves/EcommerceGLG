import { useEffect, useRef, useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { CategoryForm } from '../components/CategoryForm.jsx'
import { CategoryIcon } from '../components/CategoryIcon.jsx'
import { createCategory, deactivateCategory, listCategories, reactivateCategory, updateCategory } from '../services/categoryService.js'

export function CategoryManagementPage() {
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [editing, setEditing] = useState(null)
  const [creating, setCreating] = useState(false)
  const [formVersion, setFormVersion] = useState(0)
  const [mutationError, setMutationError] = useState(null)
  const [pending, setPending] = useState(false)
  const [needsReview, setNeedsReview] = useState(false)
  const [notice, setNotice] = useState('')
  const [confirming, setConfirming] = useState(null)
  const [statusError, setStatusError] = useState(null)
  const [confirmedStatus, setConfirmedStatus] = useState('')
  const inFlight = useRef(false)
  const reviewing = useRef(false)
  const formDialog = useRef(null)

  useEffect(() => {
    if (!notice) return undefined
    const timeout = window.setTimeout(() => setNotice(''), 4000)
    return () => window.clearTimeout(timeout)
  }, [notice])

  useEffect(() => {
    if ((creating || editing) && !formDialog.current.open) {
      if (formDialog.current.showModal) formDialog.current.showModal()
      else formDialog.current.setAttribute('open', '')
    }
  }, [creating, editing])

  useEffect(() => {
    const controller = new AbortController()
    let current = true

    listCategories({ signal: controller.signal }).then(
      (data) => {
        if (current) {
          setState({ loading: false, data, error: null })
          setConfirmedStatus('')
          setStatusError(null)
          if (reviewing.current) {
            setNeedsReview(false)
            reviewing.current = false
            setNotice('Estado actual consultado. Revisá la lista antes de enviar otra solicitud.')
          }
        }
      },
      (error) => {
        if (current && error.name !== 'AbortError') setState({ loading: false, data: null, error })
      },
    )

    return () => { current = false; controller.abort() }
  }, [version])

  function retry() {
    setState({ loading: true, data: null, error: null })
    setVersion((value) => value + 1)
  }

  function reviewCurrentState() {
    reviewing.current = true
    retry()
  }

  function selectEdit(category) {
    if (pending || needsReview || confirming || creating) return
    setEditing(category)
    setMutationError(null)
    setNotice('')
  }

  function closeForm() {
    if (inFlight.current) return
    setCreating(false)
    setEditing(null)
    setFormVersion((value) => value + 1)
    setMutationError(null)
  }

  async function submit(payload) {
    if (inFlight.current || needsReview || state.loading || state.error) return
    inFlight.current = true
    setPending(true)
    setMutationError(null)
    setNotice('')
    try {
      if (editing) await updateCategory(editing.id, payload)
      else await createCategory(payload)
      setEditing(null)
      setCreating(false)
      setFormVersion((value) => value + 1)
      setNotice(editing ? 'Categoría actualizada.' : 'Categoría creada.')
      retry()
    } catch (error) {
      setMutationError(error)
      if (!error?.status || error.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  function requestStatusChange(category) {
    if (inFlight.current || needsReview || state.loading || state.error || confirming || creating || editing) return
    setStatusError(null)
    setConfirming(category)
  }

  async function confirmStatusChange() {
    if (!confirming || inFlight.current || needsReview || state.loading || state.error) return
    const category = confirming
    inFlight.current = true
    setPending(true)
    setConfirming(null)
    setStatusError(null)
    setNotice('')
    try {
      if (category.activo) await deactivateCategory(category.id)
      else await reactivateCategory(category.id)
      const message = category.activo ? 'Categoría desactivada.' : 'Categoría reactivada.'
      setConfirmedStatus(message)
      setNotice(message)
      retry()
    } catch (error) {
      setStatusError(error)
      if (!error?.status || error.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  return (
    <section className="mx-auto max-w-5xl">
      <header className="mb-6">
        <h1 className="text-3xl font-bold text-slate-900">Categorías</h1>
        <p className="mt-2 text-slate-600">Categorías activas e inactivas del catálogo.</p>
      </header>

      <div className="mb-5 flex justify-end">
        <button type="button" aria-label="Crear categoría" disabled={pending || needsReview || state.loading || Boolean(state.error) || Boolean(confirming) || Boolean(editing)} onClick={() => { setMutationError(null); setNotice(''); setCreating(true) }} className="rounded-xl bg-emerald-700 px-4 py-2 font-bold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-emerald-800 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:opacity-50">
          <span className="sm:hidden" aria-hidden="true">+</span>
          <span className="hidden sm:inline" aria-hidden="true">Crear categoría</span>
        </button>
      </div>

      <div className="mb-6 grid gap-4">
        {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-emerald-900">{notice}</p>}
        {confirmedStatus && state.error && <p role="status" className="rounded-xl bg-amber-50 p-3 text-amber-900">{confirmedStatus} La lista no pudo actualizarse. Consultá el estado actual.</p>}
        {statusError && <div className="grid justify-items-start gap-3">
          <RequestError error={statusError} />
          {!needsReview && <button type="button" disabled={state.loading} onClick={reviewCurrentState} className="rounded-xl bg-amber-800 px-4 py-2 font-semibold text-white disabled:opacity-50">Consultar estado actual</button>}
        </div>}
        {needsReview && !creating && !editing && <div className="grid justify-items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-4">
          <p role="status">No pudimos confirmar el resultado. Consultá el estado actual antes de enviar otro cambio; la lista no confirma por sí sola si la solicitud anterior se aplicó.</p>
          <button type="button" disabled={state.loading} onClick={reviewCurrentState} className="rounded-xl bg-amber-800 px-4 py-2 font-semibold text-white disabled:opacity-50">Consultar estado actual</button>
        </div>}
      </div>

      {confirming && <div role="dialog" aria-labelledby="category-status-title" className="mb-6 rounded-xl border border-amber-300 bg-amber-50 p-4">
        <h2 id="category-status-title" className="font-bold text-slate-900">{confirming.activo ? 'Desactivar' : 'Reactivar'} {confirming.nombre}</h2>
        <p className="mt-2 text-slate-700">¿Confirmás {confirming.activo ? 'la desactivación' : 'la reactivación'} de {confirming.nombre}?</p>
        <div className="mt-4 flex gap-3">
          <button type="button" onClick={() => setConfirming(null)} className="rounded-xl border border-slate-300 px-4 py-2 font-semibold">Cancelar</button>
          <button type="button" onClick={confirmStatusChange} className={`rounded-xl px-4 py-2 font-semibold text-white ${confirming.activo ? 'bg-amber-800 hover:bg-amber-900' : 'bg-emerald-700 hover:bg-emerald-800'}`}>Confirmar {confirming.activo ? 'desactivación' : 'reactivación'}</button>
        </div>
      </div>}

      {(creating || editing) && <dialog ref={formDialog} aria-labelledby="category-form-title" onCancel={(event) => { if (inFlight.current) event.preventDefault(); else closeForm() }} className="m-auto w-[min(94vw,42rem)] max-h-[90vh] overflow-y-auto rounded-2xl border-0 bg-transparent p-0 shadow-2xl backdrop:bg-slate-950/60">
        <div className="bg-white p-1">
          {needsReview && <div className="grid justify-items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-4">
            <p role="status">No pudimos confirmar el resultado. Consultá el estado actual antes de enviar otro cambio; la lista no confirma por sí sola si la solicitud anterior se aplicó.</p>
            <button type="button" disabled={state.loading} onClick={reviewCurrentState} className="rounded-xl bg-amber-800 px-4 py-2 font-semibold text-white disabled:opacity-50">Consultar estado actual</button>
          </div>}
          {state.error && <RequestError error={state.error} />}
          <CategoryForm key={editing ? `${editing.id}-${formVersion}` : `create-${formVersion}`} initialValues={editing} onSubmit={submit} onCancel={closeForm} pending={pending || state.loading || Boolean(state.error)} blocked={needsReview} error={mutationError} />
        </div>
      </dialog>}

      {state.loading ? <p role="status">Cargando categorías…</p> : state.error ? (
        <div className="grid justify-items-start gap-4">
          <RequestError error={state.error} />
          <button className="rounded-xl bg-emerald-700 px-4 py-2 font-bold text-white hover:bg-emerald-800" onClick={retry} type="button">Reintentar</button>
        </div>
      ) : state.data.length === 0 ? <p role="status">No hay categorías.</p> : (
        <ul className="grid gap-3">
          {state.data.map((category) => (
            <li className="rounded-xl border border-slate-200 bg-white p-4" key={category.id}>
              <div className="flex items-start gap-4">
                <CategoryIcon name={category.icono} />
                <div>
                  <h2 className="font-bold text-slate-900">{category.nombre}</h2>
                  {category.descripcion && <p className="mt-1 text-slate-700">{category.descripcion}</p>}
                  <p className="mt-2 text-sm text-slate-600">ID {category.id} · {category.activo ? 'Activa' : 'Inactiva'}</p>
                  <div className="mt-3 flex gap-2">
                    <button className="rounded-xl bg-slate-800 px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-emerald-700 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:opacity-50" type="button" aria-label={`Editar ${category.nombre}`} disabled={pending || needsReview || state.loading || Boolean(confirming) || creating || Boolean(editing)} onClick={() => selectEdit(category)}>Editar</button>
                    <button className={`rounded-xl px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50 ${category.activo ? 'bg-amber-800 enabled:hover:bg-amber-900 focus-visible:outline-amber-800' : 'bg-emerald-700 enabled:hover:bg-emerald-800 focus-visible:outline-emerald-700'}`} type="button" aria-label={`${category.activo ? 'Desactivar' : 'Reactivar'} ${category.nombre}`} disabled={pending || needsReview || state.loading || Boolean(confirming) || creating || Boolean(editing)} onClick={() => requestStatusChange(category)}>{category.activo ? 'Desactivar' : 'Reactivar'}</button>
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
