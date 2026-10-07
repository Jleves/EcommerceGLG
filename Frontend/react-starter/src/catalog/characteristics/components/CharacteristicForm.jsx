import { useState } from 'react'
import { FormField } from '../../../auth/components/FormField.jsx'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { fieldError } from '../../../auth/components/fieldErrors.js'

export function CharacteristicForm({ initialValues, onSubmit, onCancel, pending, blocked, error }) {
  const [nombre, setNombre] = useState(initialValues?.nombre ?? '')
  const [validationError, setValidationError] = useState('')
  const editing = Boolean(initialValues)

  function submit(event) {
    event.preventDefault()
    if (pending || blocked) return
    const trimmed = nombre.trim()
    if (!trimmed) {
      setValidationError('Ingresá un nombre.')
      return
    }
    if (trimmed.length > 160) {
      setValidationError('El nombre no puede superar los 160 caracteres.')
      return
    }
    setValidationError('')
    onSubmit({ nombre: trimmed })
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-5 rounded-xl bg-white p-5">
      <h2 id="characteristic-form-title" className="text-xl font-bold text-slate-900">{editing ? `Editar ${initialValues.nombre}` : 'Nueva característica'}</h2>
      <RequestError error={error} />
      <fieldset className="grid gap-5">
        <FormField id="characteristic-name" label="Nombre" aria-label="Nombre" required maxLength={160} disabled={pending || blocked} value={nombre} error={validationError || fieldError(error, 'nombre')} onChange={(event) => { setNombre(event.target.value); setValidationError('') }} />
        <div className="flex flex-wrap gap-3">
          <button type="submit" disabled={pending || blocked} className="rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white enabled:hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:opacity-50">{editing ? 'Guardar cambios' : 'Guardar característica'}</button>
          <button type="button" disabled={pending} onClick={onCancel} className="rounded-lg border border-slate-300 px-4 py-2 font-semibold text-slate-800 enabled:hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-700 disabled:opacity-50">Cancelar</button>
        </div>
      </fieldset>
    </form>
  )
}
