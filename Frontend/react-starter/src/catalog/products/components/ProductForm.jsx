import { useRef, useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { fieldError } from '../../../auth/components/fieldErrors.js'
import { UnitSelect } from './UnitSelect.jsx'

const pricePattern = /^\d{1,13}(?:\.\d{1,2})?$/
const fieldClass = 'w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-slate-900 shadow-sm outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-200'
const labelClass = 'grid gap-1.5 text-sm font-semibold text-slate-700'

function invalidUnit(unit) {
  return unit !== null && (!unit.trim() || unit.length > 100)
}

export function ProductForm({ initialValues, categories, subcategories = [], characteristics, onSubmit, onCancel, pending, blocked, error }) {
  const editing = Boolean(initialValues)
  const nextRowKey = useRef(0)
  const [values, setValues] = useState({
    nombre: initialValues?.nombre ?? '',
    categoriaId: String(initialValues?.categoriaId ?? ''),
    subcategoriaId: String(initialValues?.subcategoriaId ?? ''),
    descripcion: initialValues?.descripcion ?? '',
    precioReferencia: String(initialValues?.precioReferencia ?? ''),
    disponible: initialValues?.disponible === undefined ? '' : String(initialValues.disponible),
    sku: '',
    varianteCaracteristicaId: '',
    varianteValor: '',
    varianteUnidad: null,
    caracteristicas: (initialValues?.caracteristicas ?? []).map((item, index) => ({
      rowKey: `initial-${index}`, caracteristicaId: String(item.caracteristicaId), valor: item.valor ?? '', unidad: item.unidad || null,
    })),
  })
  const [validation, setValidation] = useState('')
  const selectedIds = values.caracteristicas.map((item) => item.caracteristicaId)
  const variableId = values.varianteCaracteristicaId
  const existingVariableId = editing ? String(initialValues.variantes?.find((item) => item.caracteristicaId)?.caracteristicaId ?? '') : ''
  const eligibleCategories = categories.filter((item) => item.activo || (editing && String(item.id) === String(initialValues.categoriaId)))
  const eligibleSubcategories = subcategories.filter((item) => String(item.categoriaId) === values.categoriaId)

  function changeCharacteristic(index, changes) {
    setValues((current) => ({ ...current, caracteristicas: current.caracteristicas.map((item, row) => row === index ? { ...item, ...changes } : item) }))
  }

  function submit(event) {
    event.preventDefault()
    if (pending || blocked) return
    if (!values.nombre.trim() || values.nombre.trim().length > 160 || !values.categoriaId ||
        !eligibleCategories.some((item) => String(item.id) === values.categoriaId) ||
        (values.subcategoriaId && !eligibleSubcategories.some((item) => String(item.id) === values.subcategoriaId)) ||
        values.descripcion.length > 1000 || (!editing && (!pricePattern.test(values.precioReferencia) || Number(values.precioReferencia) <= 0 ||
        !['true', 'false'].includes(values.disponible))) ||
        selectedIds.some((id) => !id || (editing && id === existingVariableId)) || new Set(selectedIds).size !== selectedIds.length ||
        values.caracteristicas.some((item) => !item.valor.trim() || item.valor.length > 500 || invalidUnit(item.unidad)) ||
        (!editing && (values.sku.length > 160 || invalidUnit(values.varianteUnidad) || values.varianteValor.length > 500 ||
          (variableId ? !values.varianteValor.trim() || selectedIds.includes(variableId) : Boolean(values.varianteValor.trim() || values.varianteUnidad))))) {
      setValidation('Revisá los campos del producto y sus características.')
      return
    }
    setValidation('')
    onSubmit({
      nombre: values.nombre.trim(), categoriaId: Number(values.categoriaId), descripcion: values.descripcion,
      subcategoriaId: values.subcategoriaId ? Number(values.subcategoriaId) : null,
      ...(!editing && {
        varianteInicial: {
          sku: values.sku.trim() || null, precioReferencia: values.precioReferencia,
          disponible: values.disponible === 'true', caracteristicaId: variableId ? Number(variableId) : null,
          valor: variableId ? values.varianteValor.trim() : null,
          unidad: variableId ? values.varianteUnidad?.trim() || null : null,
        },
      }),
      caracteristicas: values.caracteristicas.map((item) => ({
        caracteristicaId: Number(item.caracteristicaId), valor: item.valor.trim(), unidad: item.unidad?.trim() || null,
      })),
    })
  }

  return <form className="grid gap-5 bg-white p-5 text-slate-900 sm:p-6" onSubmit={submit}>
    <header className="border-b border-slate-200 pb-4">
      <h2 className="text-2xl font-bold" id="product-form-title">{editing ? `Editar ${initialValues.nombre}` : 'Nuevo producto'}</h2>
      <p className="mt-1 text-sm text-slate-600">Completá los datos y guardá los cambios del catálogo.</p>
      {!editing && <p className="mt-2 text-sm text-slate-600">Para cargar fotos, primero guardá el producto. Después buscálo en la lista y abrí «Imágenes».</p>}
    </header>
    <RequestError error={error} />
    {validation && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{validation}</p>}
    <fieldset disabled={pending || blocked} className="grid gap-5">
      <section aria-labelledby="product-details-title" className="grid gap-4 rounded-xl border border-emerald-200 bg-emerald-100/60 p-4 sm:p-5">
        <h3 id="product-details-title" className="text-lg font-bold text-slate-950">Datos del producto</h3>
        <label className={labelClass} htmlFor="product-name">Nombre
          <input id="product-name" required maxLength={160} value={values.nombre} onChange={(event) => setValues({ ...values, nombre: event.target.value })} className={fieldClass} />
        </label>
        {fieldError(error, 'nombre') && <small className="text-red-700">{fieldError(error, 'nombre')}</small>}
        <label className={labelClass} htmlFor="product-category">Categoría
          <select id="product-category" required value={values.categoriaId} onChange={(event) => setValues({ ...values, categoriaId: event.target.value, subcategoriaId: '' })} className={fieldClass}>
            <option value="">Seleccioná una categoría</option>
            {eligibleCategories.map((item) => <option key={item.id} value={item.id}>{item.nombre}{item.activo ? '' : ' (inactiva actual)'}</option>)}
          </select>
          {fieldError(error, 'categoriaId') && <small className="text-red-700">{fieldError(error, 'categoriaId')}</small>}
        </label>
        <label className={labelClass} htmlFor="product-subcategory">Subcategoría
          <select id="product-subcategory" value={values.subcategoriaId} onChange={(event) => setValues({ ...values, subcategoriaId: event.target.value })} className={fieldClass}>
            <option value="">Sin subcategoría</option>
            {eligibleSubcategories.map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
          </select>
          {fieldError(error, 'subcategoriaId') && <small className="text-red-700">{fieldError(error, 'subcategoriaId')}</small>}
        </label>
        <label className={labelClass} htmlFor="product-description">Descripción
          <textarea id="product-description" maxLength={1000} value={values.descripcion} onChange={(event) => setValues({ ...values, descripcion: event.target.value })} className={`${fieldClass} min-h-24 resize-y`} />
          {fieldError(error, 'descripcion') && <small className="text-red-700">{fieldError(error, 'descripcion')}</small>}
        </label>
      </section>
      {!editing && <section aria-labelledby="product-variant-title" className="grid gap-4 rounded-xl border border-emerald-200 bg-emerald-100/60 p-4 sm:p-5">
        <div>
          <h3 id="product-variant-title" className="text-lg font-bold text-slate-950">{editing ? 'Precio y disponibilidad actuales' : 'Variante inicial'}</h3>
          {!editing && <p className="mt-1 text-sm text-slate-800">Podés crear una sola variante sin atributo. Para agregar otra, primero asignale un atributo y valor a esta.</p>}
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className={labelClass} htmlFor="product-price">Precio de referencia
            <input id="product-price" required inputMode="decimal" value={values.precioReferencia} onChange={(event) => setValues({ ...values, precioReferencia: event.target.value })} className={fieldClass} />
            {fieldError(error, 'precioReferencia') && <small className="text-red-700">{fieldError(error, 'precioReferencia')}</small>}
          </label>
          <label className={labelClass} htmlFor="product-available">Disponibilidad
            <select id="product-available" required value={values.disponible} onChange={(event) => setValues({ ...values, disponible: event.target.value })} className={fieldClass}>
              <option value="">Seleccioná disponibilidad</option><option value="true">Disponible</option><option value="false">No disponible</option>
            </select>
            {fieldError(error, 'disponible') && <small className="text-red-700">{fieldError(error, 'disponible')}</small>}
          </label>
        </div>
        {!editing && <>
          <label className={labelClass} htmlFor="product-initial-sku">Código (SKU) del sistema de la tienda (opcional)
            <input id="product-initial-sku" maxLength={160} value={values.sku} onChange={(event) => setValues({ ...values, sku: event.target.value })} className={fieldClass} />
          </label>
          <label className={labelClass} htmlFor="product-initial-characteristic">Atributo variable opcional
            <select id="product-initial-characteristic" value={variableId} onChange={(event) => setValues({ ...values, varianteCaracteristicaId: event.target.value, varianteValor: '', varianteUnidad: null })} className={fieldClass}>
              <option value="">Sin atributo variable</option>
              {characteristics.filter((item) => !selectedIds.includes(String(item.id))).map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
            </select>
          </label>
          {variableId && <div className="grid items-start gap-4 sm:grid-cols-2">
            <label className={labelClass} htmlFor="product-initial-value">Valor de la variante
              <input id="product-initial-value" required maxLength={500} value={values.varianteValor} onChange={(event) => setValues({ ...values, varianteValor: event.target.value })} className={fieldClass} />
            </label>
            <UnitSelect id="product-initial-unit" label="Unidad de la variante" value={values.varianteUnidad} onChange={(unidad) => setValues((current) => ({ ...current, varianteUnidad: unidad }))} />
          </div>}
        </>}
      </section>}
      <section aria-labelledby="product-characteristics-title" className="grid gap-4 rounded-xl border border-emerald-200 bg-emerald-100/60 p-4 sm:p-5">
        <div>
          <h3 id="product-characteristics-title" className="text-lg font-bold text-slate-950">Características</h3>
          <p className="mt-1 text-sm text-slate-800">Agregá los detalles que describen este producto.</p>
        </div>
        {values.caracteristicas.length === 0 && <p className="rounded-lg border border-dashed border-emerald-300 bg-white/70 p-3 text-sm text-slate-600">Este producto todavía no tiene características.</p>}
        {values.caracteristicas.map((item, index) => <div className="grid gap-3 rounded-xl border border-emerald-200 bg-white p-4 shadow-sm" key={item.rowKey}>
          <div className="flex items-center justify-between gap-3">
            <h4 className="font-semibold text-emerald-900">Característica {index + 1}</h4>
            <button type="button" onClick={() => setValues((current) => ({ ...current, caracteristicas: current.caracteristicas.filter((_, row) => row !== index) }))} className="rounded-lg px-3 py-1.5 text-sm font-semibold text-red-700 transition enabled:hover:bg-red-50 focus-visible:outline-2 focus-visible:outline-red-600">Quitar característica</button>
          </div>
          <label className={labelClass} htmlFor={`product-characteristic-${index}`}>Característica
            <select id={`product-characteristic-${index}`} required value={item.caracteristicaId} onChange={(event) => changeCharacteristic(index, { caracteristicaId: event.target.value })} className={fieldClass}>
              <option value="">Seleccioná una característica</option>
              {characteristics.filter((option) => (!selectedIds.includes(String(option.id)) || String(option.id) === item.caracteristicaId) && String(option.id) !== (editing ? existingVariableId : variableId)).map((option) => <option key={option.id} value={option.id}>{option.nombre}</option>)}
            </select>
          </label>
          <div className="grid items-start gap-3 sm:grid-cols-2">
            <label className={labelClass} htmlFor={`product-value-${index}`}>Valor
              <input id={`product-value-${index}`} required maxLength={500} value={item.valor} onChange={(event) => changeCharacteristic(index, { valor: event.target.value })} className={fieldClass} />
            </label>
            <UnitSelect id={`product-unit-${index}`} label="Unidad" value={item.unidad} onChange={(unidad) => changeCharacteristic(index, { unidad })} />
          </div>
        </div>)}
        <button type="button" disabled={selectedIds.length >= characteristics.length - ((editing ? existingVariableId : variableId) ? 1 : 0)} onClick={() => setValues((current) => ({ ...current, caracteristicas: [...current.caracteristicas, { rowKey: `new-${nextRowKey.current++}`, caracteristicaId: '', valor: '', unidad: null }] }))} className="justify-self-start rounded-lg bg-emerald-800 px-4 py-2 font-semibold text-white shadow-sm transition enabled:hover:bg-emerald-900 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-800 disabled:opacity-50">Agregar característica</button>
      </section>
      <div className="flex flex-wrap gap-3 border-t border-slate-200 pt-5">
        <button className="rounded-lg bg-emerald-700 px-5 py-2.5 font-semibold text-white shadow-sm transition enabled:hover:bg-emerald-800 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700" type="submit">{editing ? 'Guardar cambios' : 'Guardar producto'}</button>
        <button className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 font-semibold text-slate-700 transition enabled:hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-500" type="button" onClick={onCancel}>Cancelar</button>
      </div>
    </fieldset>
  </form>
}
