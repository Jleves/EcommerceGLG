import { useCallback, useEffect, useRef, useState } from 'react'
import { IconShoppingCart, IconX, IconTrash } from '@tabler/icons-react'
import { formatPrice } from './formatPrice.js'
import { getPublicProduct } from './publicService.js'
import { useCart } from './cartContext.js'
import { cartMessage, quoteVariantDetails, variantLabel } from './cartUtils.js'
import { whatsappNumber } from './publicConfig.js'

function refreshedLine(line, product) {
  const variant = product.variantes.find((item) => item.id === line.variantId)
  if (!variant) return { ...line, missing: true }
  return { ...line, name: product.nombre, variantName: variantLabel(variant), ...quoteVariantDetails(variant),
    unitPrice: variant.precioOferta ?? variant.precioReferencia, available: variant.disponible, missing: false,
    imageUrl: product.images?.find((image) => image.primary)?.thumbnailUrl || line.imageUrl }
}

function QuantityInput({ line, setQuantity }) {
  const [draft, setDraft] = useState(String(line.quantity))
  useEffect(() => setDraft(String(line.quantity)), [line.quantity])
  function commit() {
    const amount = Number(draft)
    if (Number.isSafeInteger(amount) && amount >= 1 && amount <= 9999) setQuantity(line.productId, line.variantId, amount)
    else setDraft(String(line.quantity))
  }
  return <input type="number" min="1" max="9999" value={draft} onChange={(event) => setDraft(event.target.value)} onBlur={commit} onKeyDown={(event) => { if (event.key === 'Enter') event.currentTarget.blur() }} />
}

export function CartPanel() {
  const { lines, setQuantity, remove, replace } = useCart()
  const [open, setOpen] = useState(false)
  const [status, setStatus] = useState('idle')
  const [message, setMessage] = useState('')
  const opener = useRef(null)
  const closeButton = useRef(null)
  const panel = useRef(null)
  const linesRef = useRef(lines)
  linesRef.current = lines
  const count = lines.reduce((sum, line) => sum + line.quantity, 0)
  const total = lines.reduce((sum, line) => sum + (line.missing ? 0 : Number(line.unitPrice) * line.quantity), 0)
  const hasMissing = lines.some((line) => line.missing)

  const refresh = useCallback(async () => {
    const current = linesRef.current
    if (!current.length) { setStatus('ready'); return [] }
    setStatus('loading')
    setMessage('')
    const ids = [...new Set(current.map((line) => line.productId))]
    const responses = await Promise.all(ids.map(async (id) => {
      try { return [id, await getPublicProduct(id)] }
      catch (error) { return [id, error] }
    }))
    const result = new Map(responses)
    const failure = responses.some(([, value]) => value instanceof Error && value.status !== 404)
    if (failure) { setStatus('error'); setMessage('No pudimos actualizar el carrito. Probá nuevamente.'); return null }
    const replacements = new Map(current.map((line) => {
      const product = result.get(line.productId)
      return [`${line.productId}:${line.variantId}`, product instanceof Error ? { ...line, missing: true } : refreshedLine(line, product)]
    }))
    replace(replacements)
    setStatus('ready')
    const updated = linesRef.current.map((line) => ({ ...replacements.get(`${line.productId}:${line.variantId}`), quantity: line.quantity }))
    return updated.some((line) => line.missing) ? null : updated
  }, [replace])

  useEffect(() => { if (open) { refresh(); closeButton.current?.focus() } }, [open, refresh])
  useEffect(() => {
    if (!open) return undefined
    function onKey(event) { if (event.key === 'Escape') { setOpen(false); opener.current?.focus() } }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])
  function close() { setOpen(false); opener.current?.focus() }
  function keepFocus(event) {
    if (event.key !== 'Tab') return
    const controls = [...panel.current.querySelectorAll('button:not([disabled]), input:not([disabled]), a[href]')]
    const first = controls[0]
    const last = controls.at(-1)
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
  }
  async function requestQuote() {
    if (!whatsappNumber || !linesRef.current.length || status !== 'ready' || hasMissing) return
    const popup = window.open('', '_blank')
    const updated = await refresh()
    if (!updated?.length) { popup?.close(); return }
    const url = `https://wa.me/${whatsappNumber}?text=${encodeURIComponent(cartMessage(updated))}`
    if (popup) { popup.opener = null; popup.location.href = url }
    else window.location.href = url
  }

  return <>
    <button ref={opener} className="site-cart" type="button" aria-label={`Abrir carrito, ${count} ${count === 1 ? 'unidad' : 'unidades'}`} onClick={() => setOpen(true)}>
      <IconShoppingCart size={25} aria-hidden="true" />{count > 0 && <span className="site-cart-count" aria-hidden="true">{count}</span>}
    </button>
    {open && <div className="cart-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) close() }}>
      <aside ref={panel} className="cart-panel" role="dialog" aria-modal="true" aria-label="Mi carrito" onKeyDown={keepFocus}>
        <div className="cart-panel-heading"><h2>Mi carrito</h2><button ref={closeButton} type="button" onClick={close} aria-label="Cerrar carrito"><IconX size={22} /></button></div>
        {lines.length === 0 ? <div className="cart-empty"><IconShoppingCart size={40} aria-hidden="true" /><p>Tu carrito está vacío.</p><button type="button" onClick={close}>Seguir explorando</button></div> : <>
          <div className="cart-lines">{lines.map((line) => <article className="cart-line" key={`${line.productId}:${line.variantId}`}>
            <div className="cart-line-image">{line.imageUrl ? <img src={line.imageUrl} alt="" /> : <IconShoppingCart size={25} aria-hidden="true" />}</div>
            <div className="cart-line-content"><strong>{line.name}</strong><span>{line.variantName}</span><span className={line.missing || !line.available ? 'cart-warning' : ''}>{line.missing ? 'Ya no está publicado' : line.available ? 'Disponible' : 'No disponible'}</span>
              <div className="cart-line-controls"><label>Cantidad <QuantityInput line={line} setQuantity={setQuantity} /></label><button type="button" onClick={() => remove(line.productId, line.variantId)} aria-label={`Quitar ${line.name}, ${line.variantName}`}><IconTrash size={18} /></button></div>
              {!line.missing && <span className="cart-line-subtotal">Subtotal: {formatPrice(Number(line.unitPrice) * line.quantity)}</span>}
            </div>
          </article>)}</div>
          <div className="cart-summary"><div><strong>Total orientativo</strong><strong>{formatPrice(total)}</strong></div><p>Precios orientativos. GLG confirmará el presupuesto.</p>
            {status === 'loading' && <p role="status">Actualizando productos…</p>}
            {status === 'error' && <><p role="alert">{message}</p><button type="button" onClick={refresh}>Reintentar</button></>}
            {hasMissing && <p role="alert">Quitá los productos que ya no están publicados para pedir presupuesto.</p>}
            {!whatsappNumber && <p>Falta configurar el número de WhatsApp para pedir presupuesto.</p>}
            <button className="accent-button" type="button" disabled={!whatsappNumber || status !== 'ready' || hasMissing} onClick={requestQuote}>Pedir presupuesto</button>
          </div>
        </>}
      </aside>
    </div>}
  </>
}
