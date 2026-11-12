import { useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { fieldError } from '../../../auth/components/fieldErrors.js'

export function SubcategoryForm({ initialValues, categories, subcategories = [], parentId, onSubmit, onCancel, pending, blocked, error }) {
  const editing = Boolean(initialValues)
  const [values, setValues] = useState({
    categoriaId: String(initialValues?.categoriaId ?? parentId ?? ''),
    subcategoriaPadreId: String(initialValues?.subcategoriaPadreId ?? ''),
    nombre: initialValues?.nombre ?? '',
    descripcion: initialValues?.descripcion ?? '',
  })
  const [validation, setValidation] = useState('')

  function submit(event) {
    event.preventDefault()
    if (pending || blocked) return
    if (!values.nombre.trim() || values.nombre.trim().length > 160 || values.descripcion.length > 1000 ||
        (!editing && !categories.some((category) => category.activo && String(category.id) === values.categoriaId))) {
      setValidation('Revisá los datos de la subcategoría.')
      return
    }
    setValidation('')
    onSubmit(editing
      ? { nombre: values.nombre.trim(), descripcion: values.descripcion }
      : { categoriaId: Number(values.categoriaId), nombre: values.nombre.trim(), descripcion: values.descripcion, subcategoriaPadreId: values.subcategoriaPadreId ? Number(values.subcategoriaPadreId) : null })
  }

  return <form onSubmit={submit} className="grid gap-4 rounded-xl bg-white p-5">
    <h2 id="subcategory-form-title" className="text-xl font-bold">{editing ? `Editar ${initialValues.nombre}` : 'Nueva subcategoría'}</h2>
    <RequestError error={error} />
    {validation && <p role="alert">{validation}</p>}
    <fieldset disabled={pending || blocked} className="grid gap-4">
      {editing ? <p>Categoría padre: {categories.find((item) => String(item.id) === values.categoriaId)?.nombre ?? values.categoriaId}</p> :
        <label htmlFor="subcategory-category">Categoría padre
          <select id="subcategory-category" required value={values.categoriaId} onChange={(event) => setValues({ ...values, categoriaId: event.target.value, subcategoriaPadreId: '' })} className="w-full rounded-lg border p-2">
            <option value="">Seleccioná una categoría</option>
            {categories.filter((item) => item.activo).map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
          </select>
          {fieldError(error, 'categoriaId') && <small role="alert">{fieldError(error, 'categoriaId')}</small>}
        </label>}
      {editing ? <p>Subcategoría padre: {subcategories.find((item) => String(item.id) === values.subcategoriaPadreId)?.nombre ?? 'Ninguna'}</p> :
        <label htmlFor="subcategory-parent">Subcategoría padre (opcional)
          <select id="subcategory-parent" value={values.subcategoriaPadreId} onChange={(event) => setValues({ ...values, subcategoriaPadreId: event.target.value })} className="w-full rounded-lg border p-2">
            <option value="">Ninguna — primer nivel</option>
            {subcategories.filter((item) => String(item.categoriaId) === values.categoriaId && item.subcategoriaPadreId == null).map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
          </select>
        </label>}
      <label htmlFor="subcategory-name">Nombre
        <input id="subcategory-name" required maxLength={160} value={values.nombre} onChange={(event) => setValues({ ...values, nombre: event.target.value })} className="w-full rounded-lg border p-2" />
        {fieldError(error, 'nombre') && <small role="alert">{fieldError(error, 'nombre')}</small>}
      </label>
      <label htmlFor="subcategory-description">Descripción
        <textarea id="subcategory-description" maxLength={1000} value={values.descripcion} onChange={(event) => setValues({ ...values, descripcion: event.target.value })} className="w-full rounded-lg border p-2" />
        {fieldError(error, 'descripcion') && <small role="alert">{fieldError(error, 'descripcion')}</small>}
      </label>
      <div className="flex gap-3">
        <button type="submit" className="rounded-lg bg-emerald-700 px-4 py-2 text-white">Guardar subcategoría</button>
        <button type="button" onClick={onCancel} className="rounded-lg border px-4 py-2">Cancelar</button>
      </div>
    </fieldset>
  </form>
}
