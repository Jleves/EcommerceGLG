import { useState } from 'react'
import { FormField } from '../../../auth/components/FormField.jsx'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { fieldError } from '../../../auth/components/fieldErrors.js'
import { CategoryIconPicker } from './CategoryIconPicker.jsx'

export function CategoryForm({ initialValues, onSubmit, onCancel, pending, blocked, error }) {
  const [values, setValues] = useState({
    nombre: initialValues?.nombre ?? '',
    descripcion: initialValues?.descripcion ?? '',
    icono: initialValues?.icono ?? '',
  })
  const editing = Boolean(initialValues)

  function submit(event) {
    event.preventDefault()
    if (pending || blocked || !values.icono) return
    onSubmit({ nombre: values.nombre.trim(), descripcion: values.descripcion, icono: values.icono })
  }

  return (
    <form onSubmit={submit} className="grid gap-5 rounded-xl border border-slate-200 bg-white p-5">
      <h2 id="category-form-title" className="text-xl font-bold text-slate-900">{editing ? `Editar ${initialValues.nombre}` : 'Nueva categoría'}</h2>
      <RequestError error={error} />
      <fieldset disabled={pending || blocked} className="grid gap-5">
        <FormField id="category-name" label="Nombre" required maxLength={160} value={values.nombre} error={fieldError(error, 'nombre')} onChange={(event) => setValues({ ...values, nombre: event.target.value })} />
        <CategoryIconPicker value={values.icono} onChange={(icono) => setValues({ ...values, icono })} error={fieldError(error, 'icono')} />
        <label className="grid gap-2 text-sm font-semibold text-slate-700" htmlFor="category-description">
          Descripción
          <textarea id="category-description" maxLength={1000} value={values.descripcion} aria-invalid={Boolean(fieldError(error, 'descripcion'))} aria-describedby={fieldError(error, 'descripcion') ? 'category-description-error' : undefined} onChange={(event) => setValues({ ...values, descripcion: event.target.value })} className="min-h-24 rounded-xl border border-slate-300 p-3" />
          {fieldError(error, 'descripcion') && <small className="text-red-700" id="category-description-error">{fieldError(error, 'descripcion')}</small>}
        </label>
        <div className="flex flex-wrap gap-3">
          <button type="submit" className="rounded-xl bg-emerald-700 px-4 py-2 font-bold text-white disabled:opacity-50">{editing ? 'Guardar cambios' : 'Guardar categoría'}</button>
          <button type="button" onClick={onCancel} className="rounded-xl border border-slate-300 px-4 py-2 font-semibold">{editing ? 'Cancelar edición' : 'Cancelar'}</button>
        </div>
      </fieldset>
    </form>
  )
}
