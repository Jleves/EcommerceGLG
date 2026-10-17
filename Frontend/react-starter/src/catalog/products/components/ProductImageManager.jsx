import { useEffect, useRef, useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { deleteProductImage, listProductImages, reorderProductImages, replaceProductImage, setPrimaryProductImage, uploadProductImage } from '../services/productService.js'

const button = 'rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-800 shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-slate-50 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:opacity-50'
const actionButton = 'rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-emerald-800 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:opacity-50'

export function ProductImageManager({ product, onClose }) {
  const dialog = useRef(null)
  const fileInput = useRef(null)
  const inFlight = useRef(false)
  const reviewing = useRef(false)
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ loading: true, images: [], error: null })
  const [file, setFile] = useState(null)
  const [fileKey, setFileKey] = useState(0)
  const [replacement, setReplacement] = useState(null)
  const [confirmation, setConfirmation] = useState(null)
  const [pending, setPending] = useState(false)
  const [mutationError, setMutationError] = useState(null)
  const [needsReview, setNeedsReview] = useState(false)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (dialog.current && !dialog.current.open) {
      if (dialog.current.showModal) dialog.current.showModal()
      else dialog.current.setAttribute('open', '')
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    listProductImages(product.id, { signal: controller.signal }).then(
      (images) => {
        if (!current) return
        setState({ loading: false, images, error: null })
        if (reviewing.current) {
          reviewing.current = false
          setNeedsReview(false)
          setMutationError(null)
          setConfirmation(null)
          setReplacement(null)
          setFile(null)
          setFileKey((value) => value + 1)
          setNotice('Estado actual consultado. Revisá la galería antes de enviar otro cambio.')
        }
      },
      (error) => {
        if (current && error.name !== 'AbortError') setState((previous) => ({ ...previous, loading: false, error }))
      },
    )
    return () => { current = false; controller.abort() }
  }, [product.id, version])

  const blocked = pending || state.loading || Boolean(state.error) || needsReview

  function review() {
    if (pending) return
    reviewing.current = true
    setState((previous) => ({ ...previous, loading: true, error: null }))
    setVersion((value) => value + 1)
  }

  async function mutate(operation, success, reviewOnFailure = false) {
    if (inFlight.current || blocked) return
    inFlight.current = true
    setPending(true)
    setMutationError(null)
    setNotice('')
    let wrote = false
    try {
      await operation()
      wrote = true
      const images = await listProductImages(product.id)
      setState({ loading: false, images, error: null })
      setFile(null)
      setFileKey((value) => value + 1)
      setReplacement(null)
      setConfirmation(null)
      setNotice(success)
    } catch (error) {
      setMutationError(error)
      if (wrote || reviewOnFailure || !error?.status || error.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  function move(index, offset) {
    const ids = state.images.map((image) => image.id)
    const [id] = ids.splice(index, 1)
    ids.splice(index + offset, 0, id)
    if (index + offset === 0 && !state.images[index].primary) {
      mutate(async () => {
        await reorderProductImages(product.id, ids)
        await setPrimaryProductImage(product.id, id)
      }, 'Orden y foto principal actualizados.', true)
    } else {
      mutate(() => reorderProductImages(product.id, ids), 'Orden actualizado.')
    }
  }

  function close() {
    if (!inFlight.current) onClose()
  }

  return <dialog ref={dialog} aria-labelledby="product-images-title" onCancel={(event) => { if (inFlight.current) event.preventDefault(); else close() }} className="m-auto max-h-[90vh] w-[min(94vw,48rem)] overflow-y-auto rounded-xl border-0 bg-white p-0 text-slate-900 shadow-2xl backdrop:bg-slate-950/60">
    <div className="p-5 sm:p-6">
      <div className="flex items-start justify-between gap-4">
        <h2 id="product-images-title" className="text-xl font-bold">Imágenes de {product.nombre}</h2>
        <button type="button" className={button} disabled={pending} onClick={close}>Cerrar</button>
      </div>
      <p className="mt-2 text-sm text-slate-600">Podés subir una foto por vez, hasta cinco por producto. La principal se usa como miniatura pública.</p>
      <p className="mt-1 text-sm text-slate-600">Subir una imagen al primer lugar también la hace principal. Los demás movimientos solo cambian el orden.</p>
      {notice && <p role="status" className="mt-4 rounded bg-emerald-50 p-3">{notice}</p>}
      {mutationError && <div className="mt-4 grid justify-items-start gap-3"><RequestError error={mutationError} />{!needsReview && <button type="button" className={button} onClick={review}>Consultar estado actual</button>}</div>}
      {needsReview && <div className="mt-4 grid justify-items-start gap-3 rounded border border-amber-300 bg-amber-50 p-4">
        <p role="status">No pudimos confirmar el resultado. Consultá la galería actual antes de enviar otro cambio.</p>
        <button type="button" className={button} disabled={state.loading} onClick={review}>Consultar estado actual</button>
      </div>}
      {state.loading ? <p role="status" className="mt-4">Cargando imágenes…</p> : state.error ? <div className="mt-4 grid justify-items-start gap-3"><RequestError error={state.error} /><button type="button" className={button} onClick={review}>Reintentar consulta</button></div> : <>
        <div className="mt-5 grid gap-2">
          <label htmlFor="product-image-file" className="font-semibold">Nueva foto</label>
          <input id="product-image-file" key={fileKey} ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={blocked || state.images.length >= 5} onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
          <div className="flex flex-wrap gap-2">
            <button type="button" className={button} disabled={blocked || state.images.length >= 5} onClick={() => fileInput.current?.click()}>Cargar foto</button>
            <button type="button" className={actionButton} disabled={blocked || !file || state.images.length >= 5} onClick={() => mutate(() => uploadProductImage(product.id, file), 'Foto cargada.')}>Subir imagen</button>
          </div>
          {file ? <p role="status" className="text-sm text-slate-700">Archivo seleccionado: {file.name}</p> : state.images.length < 5 && <p className="text-sm text-slate-600">Elegí un archivo JPEG, PNG o WebP de tu dispositivo para subirlo.</p>}
          {state.images.length >= 5 && <p className="text-sm text-amber-800">Se alcanzó el máximo de cinco fotos.</p>}
        </div>
        {state.images.length === 0 ? <p role="status" className="mt-5">Este producto todavía no tiene fotos.</p> : <ol aria-label="Fotos del producto" className="mt-5 grid gap-4">
          {state.images.map((image, index) => <li key={image.id} className="rounded-lg border border-slate-200 p-4">
            <div className="flex flex-wrap gap-4">
              <img src={image.thumbnailUrl} alt={`Foto ${index + 1} de ${product.nombre}`} className="h-28 w-28 rounded object-contain" />
              <div className="min-w-0 flex-1">
                <p className="font-semibold">Foto {index + 1} {image.primary && <span className="rounded bg-emerald-100 px-2 py-1 text-sm text-emerald-900">Principal</span>}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <button type="button" className={button} disabled={blocked || image.primary} onClick={() => mutate(() => setPrimaryProductImage(product.id, image.id), 'Foto principal actualizada.')}>Elegir principal para foto {index + 1}</button>
                  <button type="button" className={button} disabled={blocked || index === 0} onClick={() => move(index, -1)}>{index === 1 ? 'Subir imagen a principal' : `Subir imagen a posición ${index}`}</button>
                  <button type="button" className={button} disabled={blocked || index === state.images.length - 1} onClick={() => move(index, 1)}>Bajar imagen a posición {index + 2}</button>
                </div>
                <div className="mt-3 grid gap-2">
                  <label htmlFor={`replacement-${image.id}`} className="text-sm font-semibold">Archivo para reemplazar foto {index + 1}</label>
                  <input id={`replacement-${image.id}`} type="file" accept="image/jpeg,image/png,image/webp" disabled={blocked} onChange={(event) => setReplacement({ id: image.id, file: event.target.files?.[0] ?? null })} />
                  <div className="flex flex-wrap gap-2">
                    <button type="button" className={button} disabled={blocked || replacement?.id !== image.id || !replacement.file} onClick={() => setConfirmation({ type: 'replace', image, file: replacement.file })}>Reemplazar foto {index + 1}</button>
                    <button type="button" className={button} disabled={blocked} onClick={() => setConfirmation({ type: 'delete', image })}>Borrar foto {index + 1}</button>
                  </div>
                </div>
              </div>
            </div>
            {confirmation?.image.id === image.id && <div className="mt-4 rounded border border-amber-300 bg-amber-50 p-3">
              <p className="font-semibold">¿Confirmás {confirmation.type === 'replace' ? 'el reemplazo' : 'el borrado'} de la foto {index + 1}?</p>
              <div className="mt-3 flex gap-2">
                <button type="button" className={button} disabled={pending} onClick={() => setConfirmation(null)}>Cancelar</button>
                <button type="button" className={actionButton} disabled={blocked} onClick={() => mutate(() => confirmation.type === 'replace' ? replaceProductImage(product.id, image.id, confirmation.file) : deleteProductImage(product.id, image.id), confirmation.type === 'replace' ? 'Foto reemplazada.' : 'Foto borrada.')}>Confirmar {confirmation.type === 'replace' ? 'reemplazo' : 'borrado'}</button>
              </div>
            </div>}
          </li>)}</ol>}
      </>}
    </div>
  </dialog>
}
