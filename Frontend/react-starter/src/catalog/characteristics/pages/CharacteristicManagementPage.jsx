import { useEffect, useRef, useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { CharacteristicForm } from '../components/CharacteristicForm.jsx'
import { createCharacteristic, deleteCharacteristic, getCharacteristic, listCharacteristics, updateCharacteristic } from '../services/characteristicService.js'

export function CharacteristicManagementPage() {
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [form, setForm] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [mutationError, setMutationError] = useState(null)
  const [pending, setPending] = useState(false)
  const [needsReview, setNeedsReview] = useState(false)
  const [notice, setNotice] = useState(null)
  const inFlight = useRef(false)
  const reviewing = useRef(false)
  const detailController = useRef(null)
  const dialog = useRef(null)
  const deleteDialog = useRef(null)

  useEffect(() => {
    if (!notice || notice.persistent) return undefined
    const timeout = window.setTimeout(() => setNotice(null), 4000)
    return () => window.clearTimeout(timeout)
  }, [notice])

  useEffect(() => {
    if (form && dialog.current && !dialog.current.open) {
      if (dialog.current.showModal) dialog.current.showModal()
      else dialog.current.setAttribute('open', '')
    }
  }, [form])

  useEffect(() => {
    if (deleting && deleteDialog.current && !deleteDialog.current.open) {
      if (deleteDialog.current.showModal) deleteDialog.current.showModal()
      else deleteDialog.current.setAttribute('open', '')
    }
  }, [deleting])

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    listCharacteristics({ signal: controller.signal }).then(
      (data) => {
        if (!current) return
        setState({ loading: false, data, error: null })
        if (reviewing.current) {
          reviewing.current = false
          setNeedsReview(false)
          setDeleting(null)
          setNotice({ text: 'Estado actual consultado. Revisá la lista antes de enviar otra solicitud.', persistent: true })
        }
      },
      (error) => {
        if (current && error?.name !== 'AbortError') setState({ loading: false, data: null, error })
      },
    )
    return () => { current = false; controller.abort() }
  }, [version])

  useEffect(() => () => detailController.current?.abort(), [])

  function reload() {
    setState({ loading: true, data: null, error: null })
    setVersion((value) => value + 1)
  }

  function reviewCurrentState() {
    if (state.loading) return
    reviewing.current = true
    reload()
  }

  function openCreate() {
    if (inFlight.current || needsReview || state.loading || state.error || form || deleting) return
    setMutationError(null)
    setNotice(null)
    setForm({ mode: 'create' })
  }

  async function openEdit(id) {
    if (inFlight.current || needsReview || state.loading || state.error || form || deleting) return
    const controller = new AbortController()
    detailController.current = controller
    setMutationError(null)
    setNotice(null)
    setForm({ mode: 'edit', id, loading: true })
    try {
      const detail = await getCharacteristic(id, { signal: controller.signal })
      if (!controller.signal.aborted) setForm({ mode: 'edit', id, detail })
    } catch (error) {
      if (!controller.signal.aborted && error?.name !== 'AbortError') setForm({ mode: 'edit', id, error })
    }
  }

  function closeForm() {
    if (inFlight.current) return
    detailController.current?.abort()
    detailController.current = null
    setForm(null)
    setMutationError(null)
  }

  async function requestDelete(id) {
    if (inFlight.current || needsReview || state.loading || state.error || form || deleting) return
    const controller = new AbortController()
    detailController.current = controller
    setMutationError(null)
    setNotice(null)
    setDeleting({ id, loading: true })
    try {
      const detail = await getCharacteristic(id, { signal: controller.signal })
      if (!controller.signal.aborted) setDeleting({ id, detail })
    } catch (error) {
      if (!controller.signal.aborted && error?.name !== 'AbortError') setDeleting({ id, error })
    }
  }

  function closeDelete() {
    if (inFlight.current) return
    detailController.current?.abort()
    detailController.current = null
    setDeleting(null)
    setMutationError(null)
  }

  async function confirmDelete() {
    if (inFlight.current || needsReview || state.loading || state.error || !deleting?.detail || mutationError?.status === 404) return
    const id = deleting.id
    inFlight.current = true
    setPending(true)
    setMutationError(null)
    try {
      await deleteCharacteristic(id)
      setDeleting(null)
      setNotice({ text: 'Característica eliminada.' })
      reload()
    } catch (error) {
      setMutationError(error)
      if (!error?.status || error.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  async function submit(values) {
    if (inFlight.current || needsReview || state.loading || state.error || !form || form.loading || form.error) return
    const currentForm = form
    inFlight.current = true
    setPending(true)
    setMutationError(null)
    try {
      if (currentForm.mode === 'edit') await updateCharacteristic(currentForm.id, values)
      else await createCharacteristic(values)
      setForm(null)
      setNotice({ text: currentForm.mode === 'edit' ? 'Característica actualizada.' : 'Característica creada.' })
      reload()
    } catch (error) {
      setMutationError(error)
      if (!error?.status || error.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  const unavailable = pending || needsReview || state.loading || Boolean(state.error) || Boolean(form) || Boolean(deleting)
  const stale = form?.error?.status === 404 || mutationError?.status === 404

  return (
    <section className="mx-auto max-w-5xl">
      <header className="mb-6">
        <h1 className="text-3xl font-bold text-slate-900">Características</h1>
        <p className="mt-2 text-slate-600">Características disponibles para los productos del catálogo.</p>
      </header>

      {notice && <p role="status" className="mb-4 rounded-xl bg-emerald-50 p-3 text-emerald-900">{notice.text}</p>}
      {needsReview && !form && !deleting && <div className="mb-4 grid justify-items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-4">
        <p role="status">No pudimos confirmar el resultado. Consultá el estado actual antes de enviar otro cambio; la lista no confirma por sí sola si la solicitud anterior se aplicó.</p>
        <button type="button" disabled={state.loading} onClick={reviewCurrentState} className="rounded-lg bg-amber-800 px-4 py-2 font-semibold text-white disabled:opacity-50">Consultar estado actual</button>
      </div>}

      <div className="mb-5 flex justify-end">
        <button aria-label="Crear característica" className="rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-emerald-800 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50" disabled={unavailable} onClick={openCreate} type="button">
          <span className="sm:hidden" aria-hidden="true">+</span>
          <span className="hidden sm:inline" aria-hidden="true">Crear característica</span>
        </button>
      </div>

      {form && <dialog ref={dialog} aria-labelledby="characteristic-form-title" onCancel={(event) => { event.preventDefault(); closeForm() }} className="m-auto w-[min(94vw,38rem)] max-h-[90vh] overflow-y-auto rounded-2xl border-0 bg-white p-0 shadow-2xl backdrop:bg-slate-950/60">
        {form.loading ? <div className="p-5"><h2 id="characteristic-form-title" className="text-xl font-bold">Editar característica</h2><p role="status">Cargando característica…</p></div> : form.error ? <div className="grid justify-items-start gap-3 p-5">
          <h2 id="characteristic-form-title" className="text-xl font-bold">Editar característica</h2>
          <RequestError error={form.error} />
          {form.error.status === 404 && <p>La característica ya no está disponible. Actualizá la lista.</p>}
          <div className="flex gap-3">
            {form.error.status === 404 && <button type="button" onClick={() => { closeForm(); reload() }} className="rounded-lg bg-amber-800 px-4 py-2 font-semibold text-white">Actualizar lista</button>}
            <button type="button" onClick={closeForm} className="rounded-lg border border-slate-300 px-4 py-2 font-semibold">Cerrar</button>
          </div>
        </div> : <>
          {needsReview && <div className="grid justify-items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-4">
            <p role="status">Consultá el estado actual antes de enviar otro cambio. La lista no confirma por sí sola si la solicitud anterior se aplicó.</p>
            <button type="button" disabled={state.loading} onClick={reviewCurrentState} className="rounded-lg bg-amber-800 px-4 py-2 font-semibold text-white disabled:opacity-50">Consultar estado actual</button>
          </div>}
          {state.error && <div className="p-4"><RequestError error={state.error} /></div>}
          {stale && <div className="grid justify-items-start gap-3 p-5 pb-0"><p role="status">La característica ya no está disponible. Actualizá la lista.</p><button type="button" onClick={() => { closeForm(); reload() }} className="rounded-lg bg-amber-800 px-4 py-2 font-semibold text-white">Actualizar lista</button></div>}
          <CharacteristicForm key={form.mode === 'edit' ? `edit-${form.id}` : 'create'} initialValues={form.detail} onSubmit={submit} onCancel={closeForm} pending={pending || state.loading || Boolean(state.error)} blocked={needsReview || stale} error={mutationError} />
        </>}
      </dialog>}

      {deleting && <dialog ref={deleteDialog} aria-labelledby="characteristic-delete-title" aria-describedby="characteristic-delete-description" onCancel={(event) => { event.preventDefault(); closeDelete() }} className="m-auto w-[min(92vw,27rem)] max-h-[90vh] overflow-y-auto rounded-2xl border-0 bg-white p-0 text-slate-900 shadow-2xl backdrop:bg-slate-950/50">
        <div className="border-t-4 border-[#B42318] p-5 sm:p-6">
          <h2 id="characteristic-delete-title" className="text-lg font-bold">Eliminar característica</h2>
          {deleting.loading ? <p role="status" className="mt-2">Consultando característica…</p> : deleting.error ? <div className="mt-3 grid justify-items-start gap-3">
            <RequestError error={deleting.error} />
            {deleting.error.status === 404 && <p>La característica ya no está disponible. Actualizá la lista.</p>}
            {deleting.error.status === 404 && <button type="button" onClick={() => { closeDelete(); reload() }} className="rounded-lg bg-amber-800 px-4 py-2 font-semibold text-white">Actualizar lista</button>}
          </div> : <>
            <p id="characteristic-delete-description" className="mt-2 text-sm text-slate-700">¿Confirmás la eliminación de <strong>{deleting.detail.nombre}</strong>? Está asociada a {deleting.detail.cantidadProductos} {deleting.detail.cantidadProductos === 1 ? 'producto' : 'productos'}. Se quitarán los valores y unidades asociados, pero no se borrarán los productos. La cantidad puede cambiar antes de confirmar.</p>
            {mutationError && <div className="mt-3"><RequestError error={mutationError} /></div>}
            {mutationError?.status === 404 && <div className="mt-3 grid justify-items-start gap-2"><p>La característica ya no está disponible. Actualizá la lista.</p><button type="button" onClick={() => { closeDelete(); reload() }} className="rounded-lg bg-amber-800 px-4 py-2 font-semibold text-white">Actualizar lista</button></div>}
            {needsReview && <div className="mt-3 grid justify-items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-3"><p role="status">No pudimos confirmar el resultado. Consultá el estado actual antes de enviar otro cambio; la lista no confirma por sí sola si la solicitud anterior se aplicó.</p><button type="button" disabled={state.loading} onClick={reviewCurrentState} className="rounded-lg bg-amber-800 px-4 py-2 font-semibold text-white disabled:opacity-50">Consultar estado actual</button></div>}
            {state.error && <div className="mt-3"><RequestError error={state.error} /></div>}
            <div className="mt-5 flex flex-wrap justify-end gap-3">
              <button type="button" disabled={pending} onClick={closeDelete} className="rounded-lg border border-slate-300 px-4 py-2 font-semibold text-slate-900 enabled:hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-700 disabled:cursor-not-allowed disabled:opacity-50">Cancelar</button>
              <button type="button" disabled={pending || needsReview || state.loading || Boolean(state.error) || mutationError?.status === 404} onClick={confirmDelete} className="rounded-lg bg-[#B42318] px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-[#912018] enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#B42318] disabled:cursor-not-allowed disabled:opacity-50">Confirmar eliminación</button>
            </div>
          </>}
          {deleting.error && <button type="button" onClick={closeDelete} className="mt-4 rounded-lg border border-slate-300 px-4 py-2 font-semibold">Cerrar</button>}
        </div>
      </dialog>}

      {state.loading ? <p role="status">Cargando características…</p> : state.error ? (
        <div className="grid justify-items-start gap-3">
          <RequestError error={state.error} />
          <button className="rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700" onClick={needsReview ? reviewCurrentState : reload} type="button">Reintentar</button>
        </div>
      ) : state.data.length === 0 ? <p role="status">No hay características.</p> : (
        <ul className="grid gap-3">
          {state.data.map((characteristic) => (
            <li className="rounded-xl border border-slate-200 bg-white p-4" key={characteristic.id}>
              <h2 className="font-bold text-slate-900">{characteristic.nombre}</h2>
              <p className="mt-2 text-sm text-slate-600">{characteristic.cantidadProductos} {characteristic.cantidadProductos === 1 ? 'producto' : 'productos'}</p>
              <button type="button" aria-label={`Editar ${characteristic.nombre}`} disabled={unavailable} onClick={() => openEdit(characteristic.id)} className="mt-3 rounded-lg bg-[#1E293B] px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-slate-700 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#1E293B] disabled:cursor-not-allowed disabled:opacity-50">Editar</button>
              <button type="button" aria-label={`Eliminar ${characteristic.nombre}`} disabled={unavailable} onClick={() => requestDelete(characteristic.id)} className="ml-2 mt-3 rounded-lg bg-[#B42318] px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-[#912018] enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#B42318] disabled:cursor-not-allowed disabled:opacity-50">Eliminar</button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
