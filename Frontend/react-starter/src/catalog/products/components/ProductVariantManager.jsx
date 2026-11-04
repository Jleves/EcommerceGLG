import { useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { UnitSelect } from './UnitSelect.jsx'

const pricePattern = /^\d{1,13}(?:\.\d{1,2})?$/
const fieldClass = 'w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-slate-900 shadow-sm focus:border-emerald-600 focus:ring-2 focus:ring-emerald-200'
const labelClass = 'grid gap-1.5 text-sm font-semibold text-slate-700'

function normalized(value) {
  return (value ?? '').trim().toLocaleLowerCase('en-US')
}

function invalidUnit(unit) {
  return unit !== null && (!unit.trim() || unit.length > 100)
}

function variantDescription(variant) {
  return variant.caracteristicaNombre
    ? `${variant.caracteristicaNombre}: ${variant.valor}${variant.unidad ? ` ${variant.unidad}` : ''}`
    : 'sin atributo variable'
}

function VariantRow({ variant, variants, pending, reviewRequired, onUpdate, onChangeStatus }) {
  const [value, setValue] = useState(variant.valor ?? '')
  const [unit, setUnit] = useState(variant.unidad ?? null)
  const [sku, setSku] = useState(variant.sku ?? '')
  const [price, setPrice] = useState(String(variant.precioReferencia))
  const [available, setAvailable] = useState(String(variant.disponible))
  const [validation, setValidation] = useState('')
  const [confirming, setConfirming] = useState(false)
  const lastActive = variant.activo && variants.filter((item) => item.activo).length === 1

  function save(event) {
    event.preventDefault()
    if (pending || reviewRequired) return
    const repeated = variants.some((item) => item.id !== variant.id && item.caracteristicaId === variant.caracteristicaId &&
      normalized(item.valor ?? '') === normalized(value) && normalized(item.unidad ?? '') === normalized(unit))
    if (sku.length > 160 || !pricePattern.test(price) || Number(price) <= 0 || invalidUnit(unit) ||
        (variant.caracteristicaId && (!value.trim() || value.length > 500)) || repeated) {
      setValidation(repeated ? 'Ya existe una variante con ese valor y unidad.' : 'Revisá los campos de esta variante.')
      return
    }
    setValidation('')
    onUpdate(variant.id, { sku: sku.trim() || null, precioReferencia: price, disponible: available === 'true',
      caracteristicaId: variant.caracteristicaId ?? null, valor: variant.caracteristicaId ? value.trim() : null,
      unidad: variant.caracteristicaId ? unit?.trim() || null : null })
  }

  return <form onSubmit={save} className="grid gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm" aria-label={`Editar variante ${variantDescription(variant)}`}>
    <p className="font-semibold">Variante {variantDescription(variant)} - {variant.activo ? 'Activa' : 'Inactiva'}</p>
    {validation && <p role="alert" className="text-red-700">{validation}</p>}
    <fieldset disabled={pending || reviewRequired} className="grid gap-3">
      {variant.caracteristicaId && <div className="grid gap-3 sm:grid-cols-2">
        <label className={labelClass} htmlFor={`variant-value-${variant.id}`}>Valor de la variante
          <input id={`variant-value-${variant.id}`} maxLength={500} value={value} onChange={(event) => setValue(event.target.value)} className={fieldClass} />
        </label>
        <UnitSelect id={`variant-unit-${variant.id}`} label="Unidad" value={unit} onChange={setUnit} />
      </div>}
      <label className={labelClass} htmlFor={`variant-sku-${variant.id}`}>Código (SKU) del sistema de la tienda (opcional)
        <input id={`variant-sku-${variant.id}`} maxLength={160} value={sku} onChange={(event) => setSku(event.target.value)} className={fieldClass} />
      </label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className={labelClass} htmlFor={`variant-price-${variant.id}`}>Precio
          <input id={`variant-price-${variant.id}`} inputMode="decimal" value={price} onChange={(event) => setPrice(event.target.value)} className={fieldClass} />
        </label>
        <label className={labelClass} htmlFor={`variant-available-${variant.id}`}>Disponibilidad
          <select id={`variant-available-${variant.id}`} value={available} onChange={(event) => setAvailable(event.target.value)} className={fieldClass}>
            <option value="true">Disponible</option><option value="false">No disponible</option>
          </select>
        </label>
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="rounded bg-emerald-700 px-4 py-2 font-semibold text-white">Guardar variante</button>
        <button type="button" disabled={lastActive} onClick={() => setConfirming(true)} className="rounded border border-slate-400 px-4 py-2 font-semibold">
          {variant.activo ? 'Desactivar' : 'Reactivar'}
        </button>
      </div>
      {lastActive && <p>No se puede desactivar la última variante activa.</p>}
      {confirming && <div className="grid gap-2 rounded border border-amber-300 bg-amber-50 p-3">
        <p>¿Confirmás {variant.activo ? 'desactivar' : 'reactivar'} la variante {variantDescription(variant)}?</p>
        <div className="flex gap-2">
          <button type="button" onClick={() => setConfirming(false)}>Cancelar</button>
          <button type="button" onClick={() => { setConfirming(false); onChangeStatus(variant.id, variant.activo ? 'deactivate' : 'reactivate') }}>
            Confirmar {variant.activo ? 'desactivación' : 'reactivación'}
          </button>
        </div>
      </div>}
    </fieldset>
  </form>
}

export function ProductVariantManager({ product, characteristics, pending, reviewRequired, error, onUpdateInitial, onUpdate, onCreate, onChangeStatus, onReview, onClose, editing = false }) {
  const variants = product.variantes ?? []
  const initial = variants.find((variant) => variant.id === product.varianteInicialId) ?? variants[0]
  const attributeId = initial?.caracteristicaId ? String(initial.caracteristicaId) : ''
  const [selectedAttribute, setSelectedAttribute] = useState('')
  const [value, setValue] = useState('')
  const [unit, setUnit] = useState(null)
  const [sku, setSku] = useState('')
  const [price, setPrice] = useState('')
  const [available, setAvailable] = useState('')
  const [validation, setValidation] = useState('')
  const canAdd = Boolean(initial?.caracteristicaId && initial?.valor?.trim())
  const sharedIds = new Set((product.caracteristicas ?? []).map((item) => String(item.caracteristicaId)))

  function validAttribute(id) {
    return Boolean(id && !sharedIds.has(id) && characteristics.some((item) => String(item.id) === id))
  }

  function assignInitial(event) {
    event.preventDefault()
    if (pending || reviewRequired) return
    if (!initial || !validAttribute(selectedAttribute) || !value.trim() || value.length > 500 || invalidUnit(unit)) {
      setValidation('Elegí un atributo variable y completá un valor válido para la variante inicial.')
      return
    }
    setValidation('')
    onUpdateInitial(initial.id, {
      sku: initial.sku ?? null, precioReferencia: initial.precioReferencia, disponible: initial.disponible,
      caracteristicaId: Number(selectedAttribute), valor: value.trim(), unidad: unit?.trim() || null,
    })
  }

  function addVariant(event) {
    event.preventDefault()
    if (pending || reviewRequired) return
    const repeated = variants.some((variant) => variant.caracteristicaId === initial?.caracteristicaId &&
      normalized(variant.valor ?? '') === normalized(value) && normalized(variant.unidad ?? '') === normalized(unit))
    if (!canAdd || !validAttribute(attributeId) || !value.trim() || value.length > 500 || invalidUnit(unit) ||
        sku.length > 160 || !pricePattern.test(price) || Number(price) <= 0 || !['true', 'false'].includes(available) || repeated) {
      setValidation(repeated ? 'Ya existe una variante con ese valor y unidad.' : 'Revisá los campos de la variante antes de agregarla.')
      return
    }
    setValidation('')
    onCreate({ sku: sku.trim() || null, precioReferencia: price, disponible: available === 'true',
      caracteristicaId: Number(attributeId), valor: value.trim(), unidad: unit?.trim() || null })
  }

  return <section className="grid gap-5 bg-white p-5 text-slate-900 sm:p-6" aria-label="Gestión de variantes">
    <header className="border-b border-slate-200 pb-4">
      <h2 className="text-2xl font-bold" id={editing ? undefined : 'product-form-title'}>{editing ? `Variantes de ${product.nombre}` : `Producto creado: ${product.nombre}`}</h2>
      <p className="mt-1 text-sm text-slate-700">{editing ? 'Cada variante se guarda por separado.' : 'La ficha y su variante inicial ya están guardadas. Podés completar otras variantes ahora.'}</p>
      <p className="mt-1 text-sm text-slate-600">Las fotos son generales del producto. Después de cerrar, buscálo en la lista y abrí «Imágenes».</p>
    </header>
    <RequestError error={error} />
    {validation && <p role="alert" className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{validation}</p>}
    {reviewRequired && <div className="grid gap-2 rounded border border-amber-300 bg-amber-50 p-3">
      <p role="status">No pudimos confirmar el resultado. Consultá el detalle antes de repetir la operación.</p>
      <button type="button" disabled={pending} onClick={onReview} className="justify-self-start rounded bg-amber-800 px-4 py-2 font-semibold text-white">Consultar detalle del producto</button>
    </div>}
    <section aria-label="Variantes confirmadas" className="grid gap-3">
      <h3 className="text-lg font-bold">Variantes confirmadas</h3>
      {variants.map((variant) => editing ? <VariantRow key={`${variant.id}-${variant.sku}-${variant.precioReferencia}-${variant.disponible}-${variant.activo}-${variant.valor}-${variant.unidad}`} variant={variant} variants={variants} pending={pending} reviewRequired={reviewRequired} onUpdate={onUpdate} onChangeStatus={onChangeStatus} /> : <div key={variant.id} className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm">
        <p className="font-semibold">Variante {variantDescription(variant)}</p>
        <p>Código (SKU) del sistema de la tienda: {variant.sku || 'Sin código'} · Precio de referencia: {variant.precioReferencia} · {variant.disponible ? 'Disponible' : 'No disponible'}</p>
      </div>)}
    </section>
    {!canAdd ? <form onSubmit={assignInitial} className="grid gap-4 rounded-xl border border-emerald-200 p-4">
      <h3 className="font-bold">Asignar valor a la variante inicial</h3>
      <p className="text-sm text-slate-600">Este paso es necesario antes de agregar una segunda variante.</p>
      <fieldset disabled={pending || reviewRequired} className="grid gap-4">
        <label className={labelClass} htmlFor="initial-attribute">Atributo variable
          <select id="initial-attribute" value={selectedAttribute} onChange={(event) => setSelectedAttribute(event.target.value)} className={fieldClass}>
            <option value="">Seleccioná un atributo</option>
            {characteristics.filter((item) => !sharedIds.has(String(item.id))).map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
          </select>
        </label>
        <label className={labelClass} htmlFor="initial-value">Valor de la variante inicial
          <input id="initial-value" maxLength={500} value={value} onChange={(event) => setValue(event.target.value)} className={fieldClass} />
        </label>
        <UnitSelect id="initial-unit" label="Unidad de la variante inicial" value={unit} onChange={setUnit} />
        <button type="submit" className="justify-self-start rounded bg-emerald-700 px-4 py-2 font-semibold text-white">Guardar valor inicial</button>
      </fieldset>
    </form> : <form onSubmit={addVariant} className="grid gap-4 rounded-xl border border-emerald-200 p-4">
      <h3 className="font-bold">Agregar otra variante</h3>
      <p className="text-sm text-slate-600">Atributo variable: {initial.caracteristicaNombre}. Cada variante se guarda por separado.</p>
      <fieldset disabled={pending || reviewRequired} className="grid gap-4">
        <label className={labelClass} htmlFor="new-variant-value">Valor de la nueva variante
          <input id="new-variant-value" maxLength={500} value={value} onChange={(event) => setValue(event.target.value)} className={fieldClass} />
        </label>
        <UnitSelect id="new-variant-unit" label="Unidad de la nueva variante" value={unit} onChange={setUnit} />
        <label className={labelClass} htmlFor="new-variant-sku">Código (SKU) del sistema de la tienda (opcional)
          <input id="new-variant-sku" maxLength={160} value={sku} onChange={(event) => setSku(event.target.value)} className={fieldClass} />
        </label>
        <label className={labelClass} htmlFor="new-variant-price">Precio de referencia
          <input id="new-variant-price" inputMode="decimal" value={price} onChange={(event) => setPrice(event.target.value)} className={fieldClass} />
        </label>
        <label className={labelClass} htmlFor="new-variant-available">Disponibilidad
          <select id="new-variant-available" value={available} onChange={(event) => setAvailable(event.target.value)} className={fieldClass}>
            <option value="">Seleccioná disponibilidad</option><option value="true">Disponible</option><option value="false">No disponible</option>
          </select>
        </label>
        <button type="submit" className="justify-self-start rounded bg-emerald-700 px-4 py-2 font-semibold text-white">Agregar variante</button>
      </fieldset>
    </form>}
    <button type="button" disabled={pending} onClick={onClose} className="justify-self-start rounded border border-slate-300 px-4 py-2 font-semibold">{editing ? 'Cerrar edición' : 'Cerrar alta'}</button>
  </section>
}
