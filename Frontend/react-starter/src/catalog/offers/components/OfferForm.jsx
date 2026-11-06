import { useEffect, useRef, useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { getProduct } from '../../products/services/productService.js'
import { createOffer, listOffersByProducts, updateOffer } from '../services/offerService.js'

const pricePattern = /^\d{1,13}(?:\.\d{1,2})?$/

function cents(value) {
  const [whole, fraction = ''] = String(value).split('.')
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
}

function variantLabel(variant) {
  const detail = variant.caracteristicaNombre
    ? `${variant.caracteristicaNombre}: ${variant.valor}${variant.unidad ? ` ${variant.unidad}` : ''}`
    : 'Variante inicial'
  return `${detail} · ID ${variant.id}${variant.sku ? ` · ${variant.sku}` : ''}${variant.activo ? '' : ' · Inactiva'}`
}

export function OfferForm({ productId, initialVariantId, onSaved, onCancel, onBusyChange = () => {} }) {
  const [revision, setRevision] = useState(0)
  const [state, setState] = useState({ loading: true, product: null, offers: [], error: null })
  const [variantId, setVariantId] = useState('')
  const [price, setPrice] = useState('')
  const [validation, setValidation] = useState('')
  const [mutationError, setMutationError] = useState(null)
  const [needsReview, setNeedsReview] = useState(false)
  const [pending, setPending] = useState(false)
  const inFlight = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      getProduct(productId, { signal: controller.signal }),
      listOffersByProducts([productId], { signal: controller.signal }),
    ]).then(([product, offers]) => {
      if (controller.signal.aborted) return
      const first = product.variantes?.find((variant) => variant.id === initialVariantId)
        ?? product.variantes?.find((variant) => offers.some((offer) => offer.varianteId === variant.id))
        ?? product.variantes?.find((variant) => variant.activo)
        ?? product.variantes?.[0]
      setVariantId(String(first?.id ?? ''))
      setPrice(String(offers.find((offer) => offer.varianteId === first?.id)?.precioOferta ?? ''))
      setState({ loading: false, product, offers, error: null })
      setNeedsReview(false)
    }, (error) => {
      if (!controller.signal.aborted) setState({ loading: false, product: null, offers: [], error })
    })
    return () => controller.abort()
  }, [productId, initialVariantId, revision])

  const variant = state.product?.variantes?.find((item) => String(item.id) === variantId)
  const existing = state.offers.find((offer) => String(offer.varianteId) === variantId)

  function selectVariant(id) {
    setVariantId(id)
    setPrice(String(state.offers.find((offer) => String(offer.varianteId) === id)?.precioOferta ?? ''))
    setMutationError(null)
    setValidation('')
  }

  async function submit(event) {
    event.preventDefault()
    if (inFlight.current || needsReview || !variant) return
    const normalized = price.trim()
    if (!pricePattern.test(normalized) || cents(normalized) <= 0n || cents(normalized) >= cents(variant.precioReferencia)) {
      setValidation('Ingresá un precio positivo, con hasta dos decimales y menor que el precio de referencia.')
      return
    }
    setValidation('')
    setMutationError(null)
    inFlight.current = true
    setPending(true)
    onBusyChange(true)
    try {
      const result = existing
        ? await updateOffer(existing.id, normalized)
        : await createOffer(variant.id, normalized)
      if (!result?.id || result.varianteId !== variant.id) {
        setNeedsReview(true)
        setMutationError({ message: 'No se pudo confirmar el resultado. Consultá el estado actual antes de intentar otro cambio.' })
        return
      }
      onSaved()
    } catch (error) {
      setMutationError(error)
      if (error?.status === 409) setRevision((value) => value + 1)
      if (!error?.status || error.status >= 500) setNeedsReview(true)
    } finally {
      inFlight.current = false
      setPending(false)
      onBusyChange(false)
    }
  }

  if (state.loading) return <p role="status">Cargando oferta…</p>
  if (state.error) return <div className="grid gap-3"><RequestError error={state.error} /><button type="button" onClick={() => setRevision((value) => value + 1)}>Reintentar</button><button type="button" onClick={onCancel}>Cerrar</button></div>

  return <form onSubmit={submit} className="grid gap-4">
    <p className="font-semibold">{state.product.nombre} · ID {state.product.id}</p>
    {mutationError && <RequestError error={mutationError} />}
    {needsReview && <div className="grid gap-2 rounded border border-amber-300 bg-amber-50 p-3">
      <p role="status">El resultado es incierto. Consultá el producto y sus ofertas antes de reenviar.</p>
      <button type="button" onClick={() => setRevision((value) => value + 1)}>Consultar estado actual</button>
    </div>}
    <label className="grid gap-1">Variante
      <select value={variantId} onChange={(event) => selectVariant(event.target.value)} disabled={pending || needsReview} className="rounded border p-2">
        {state.product.variantes?.map((item) => <option key={item.id} value={item.id}>{variantLabel(item)}</option>)}
      </select>
    </label>
    {variant && <p>Precio de referencia: {variant.precioReferencia} · {variant.disponible ? 'Disponible' : 'No disponible'}</p>}
    {existing && <p>Oferta actual: {existing.precioOferta} · {existing.productoActivo && existing.varianteActiva ? 'Publicada' : 'Oculta temporalmente'}</p>}
    <label className="grid gap-1">Precio final de oferta
      <input value={price} onChange={(event) => { setPrice(event.target.value); setValidation('') }} inputMode="decimal" disabled={pending || needsReview || (!existing && (!state.product.activo || !variant?.activo))} className="rounded border p-2" />
    </label>
    {!existing && (!state.product.activo || !variant?.activo) && <p role="status">El producto y la variante deben estar activos para crear una oferta.</p>}
    {validation && <p role="alert" className="text-red-700">{validation}</p>}
    <div className="flex justify-end gap-3">
      <button type="button" onClick={onCancel} disabled={pending}>Cerrar</button>
      <button type="submit" disabled={pending || needsReview || !variant || (!existing && (!state.product.activo || !variant.activo))} className="rounded bg-emerald-700 px-4 py-2 font-semibold text-white disabled:opacity-50">{pending ? 'Guardando…' : existing ? 'Guardar cambios' : 'Crear oferta'}</button>
    </div>
  </form>
}
