import { useEffect, useRef, useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { CategoryIcon } from '../../categories/components/CategoryIcon.jsx'
import { SubcategoryForm } from './SubcategoryForm.jsx'
import { createSubcategory, deleteSubcategory, getSubcategory, listSubcategories, updateSubcategory } from '../services/subcategoryService.js'

export function SubcategoryManager({ categories }) {
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ loading: true, data: [], error: null })
  const [form, setForm] = useState(null)
  const [confirming, setConfirming] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState('')
  const [needsReview, setNeedsReview] = useState(false)
  const inFlight = useRef(false)
  const formDialog = useRef(null)

  useEffect(() => {
    if (form && formDialog.current && !formDialog.current.open) {
      if (formDialog.current.showModal) formDialog.current.showModal()
      else formDialog.current.setAttribute('open', '')
    }
  }, [form])

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    listSubcategories(undefined, { signal: controller.signal }).then(
      (data) => { if (current) setState({ loading: false, data, error: null }) },
      (failure) => { if (current && failure.name !== 'AbortError') setState({ loading: false, data: [], error: failure }) },
    )
    return () => { current = false; controller.abort() }
  }, [version, categories])

  function reload() {
    setState((current) => ({ ...current, loading: true }))
    setVersion((current) => current + 1)
  }

  function review() {
    setForm(null)
    setConfirming(null)
    setError(null)
    setNeedsReview(false)
    setNotice('Consultá el estado actual antes de repetir la operación.')
    reload()
  }

  function closeForm() {
    if (inFlight.current) return
    setForm(null)
    setError(null)
  }

  async function submit(payload) {
    if (inFlight.current || needsReview) return
    inFlight.current = true
    setPending(true)
    setError(null)
    try {
      if (form?.id) await updateSubcategory(form.id, payload)
      else await createSubcategory(payload)
      setNotice(form?.id ? 'Subcategoría actualizada.' : 'Subcategoría creada.')
      setForm(null)
      reload()
    } catch (failure) {
      setError(failure)
      if (!failure?.status || failure.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  async function requestDelete(item) {
    if (inFlight.current || needsReview) return
    setError(null)
    setDetailLoading(true)
    try {
      setConfirming(await getSubcategory(item.id))
    } catch (failure) {
      setError(failure)
    } finally {
      setDetailLoading(false)
    }
  }

  async function confirmDelete() {
    if (!confirming || inFlight.current || needsReview) return
    inFlight.current = true
    setPending(true)
    setError(null)
    try {
      await deleteSubcategory(confirming.id)
      setConfirming(null)
      setNotice('Subcategoría eliminada. Los productos conservaron su categoría padre y quedaron sin subcategoría.')
      reload()
    } catch (failure) {
      setError(failure)
      if (!failure?.status || failure.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  const busy = pending || state.loading || Boolean(state.error) || needsReview || detailLoading
  return <section aria-labelledby="subcategory-title" className="mt-10 border-t border-slate-200 pt-8">
    <header className="mb-6">
      <h2 id="subcategory-title" className="text-3xl font-bold text-slate-900">Subcategorías</h2>
      <p className="mt-2 text-slate-600">Subcategorías del catálogo, organizadas por categoría.</p>
    </header>
    <div className="mb-5 flex justify-end">
      <button type="button" aria-label="Crear subcategoría" disabled={busy || Boolean(form) || Boolean(confirming) || !categories.some((item) => item.activo)} onClick={() => { setError(null); setForm({}) }} className="rounded-xl bg-emerald-700 px-4 py-2 font-bold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-emerald-800 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:opacity-50">
        <span className="sm:hidden" aria-hidden="true">+</span>
        <span className="hidden sm:inline" aria-hidden="true">Crear subcategoría</span>
      </button>
    </div>
    {notice && <p role="status" className="mb-3 rounded bg-emerald-50 p-3">{notice}</p>}
    {needsReview && <div role="alert" className="mb-3 rounded bg-amber-50 p-3">No pudimos confirmar el resultado. Consultá el estado actual antes de repetir el cambio.
      <button type="button" onClick={review} className="ml-3 font-bold underline">Consultar estado actual</button>
    </div>}
    {error && <RequestError error={error} />}
    {form && <dialog ref={formDialog} aria-labelledby="subcategory-form-title" onCancel={(event) => { if (inFlight.current) event.preventDefault(); else closeForm() }} className="m-auto max-h-[90vh] w-[min(94vw,42rem)] overflow-y-auto rounded-2xl border-0 bg-transparent p-0 shadow-2xl backdrop:bg-slate-950/60">
      {needsReview && <div className="grid justify-items-start gap-3 border border-amber-300 bg-amber-50 p-4">
        <p role="status">No pudimos confirmar el resultado. Consultá el estado actual antes de enviar otro cambio.</p>
        <button type="button" onClick={review} className="rounded-xl bg-amber-800 px-4 py-2 font-semibold text-white">Consultar estado actual</button>
      </div>}
      <SubcategoryForm key={form.id ?? 'create'} initialValues={form.id ? form : null} categories={categories} onSubmit={submit} onCancel={closeForm} pending={pending} blocked={needsReview} error={error} />
    </dialog>}
    {confirming && <div role="dialog" aria-labelledby="subcategory-delete-title" className="mb-4 rounded border border-amber-300 bg-amber-50 p-4">
      <h3 id="subcategory-delete-title" className="font-bold">Eliminar {confirming.nombre}</h3>
      <p>{confirming.productosAsociados} productos actualmente asociados perderán esta subcategoría. Los productos conservarán su categoría padre. El borrado de la subcategoría es irreversible.</p>
      <p className="text-sm">La cantidad puede cambiar antes de confirmar.</p>
      <div className="mt-3 flex gap-3">
        <button type="button" disabled={pending} onClick={() => setConfirming(null)} className="rounded border px-4 py-2">Cancelar</button>
        <button type="button" disabled={pending || needsReview} onClick={confirmDelete} className="rounded bg-red-700 px-4 py-2 text-white">Confirmar eliminación</button>
      </div>
    </div>}
    {state.loading ? <p role="status">Cargando subcategorías…</p> : state.error ? <div><RequestError error={state.error} /><button type="button" onClick={reload}>Reintentar</button></div> :
      <div className="grid gap-6">{categories.map((category) => <section key={category.id} aria-label={`Subcategorías de ${category.nombre}`}>
        <h3 className="mb-3 text-lg font-bold text-slate-900">{category.nombre}</h3>
        {state.data.filter((item) => item.categoriaId === category.id).length === 0 ? <p className="rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-600">Sin subcategorías.</p> :
          <ul className="grid gap-3">{state.data.filter((item) => item.categoriaId === category.id).map((item) => <li key={item.id} className="rounded-xl border border-slate-200 bg-white p-4">
            <div className="flex items-start gap-4">
              <CategoryIcon name={category.icono} />
              <div>
                <h4 className="font-bold text-slate-900">{item.nombre}</h4>
                {item.descripcion && <p className="mt-1 text-slate-700">{item.descripcion}</p>}
                <p className="mt-2 text-sm text-slate-600">ID {item.id} · Categoría {category.nombre}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <button type="button" disabled={busy || Boolean(form) || Boolean(confirming)} onClick={() => { setError(null); setForm(item) }} aria-label={`Editar subcategoría ${item.nombre}`} className="rounded-xl bg-slate-800 px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-emerald-700 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:opacity-50">Editar</button>
                  <button type="button" disabled={busy || Boolean(form) || Boolean(confirming)} onClick={() => requestDelete(item)} aria-label={`Eliminar subcategoría ${item.nombre}`} className="rounded-xl bg-[#B42318] px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-[#912018] enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#B42318] disabled:opacity-50">Eliminar</button>
                </div>
              </div>
            </div>
          </li>)}</ul>}
      </section>)}</div>}
  </section>
}
