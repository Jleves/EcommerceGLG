import { useEffect, useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { listCharacteristics } from '../services/characteristicService.js'

export function CharacteristicManagementPage() {
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ loading: true, data: null, error: null })

  useEffect(() => {
    const controller = new AbortController()
    let current = true

    listCharacteristics({ signal: controller.signal }).then(
      (data) => { if (current) setState({ loading: false, data, error: null }) },
      (error) => {
        if (current && error.name !== 'AbortError') setState({ loading: false, data: null, error })
      },
    )

    return () => { current = false; controller.abort() }
  }, [version])

  function retry() {
    setState({ loading: true, data: null, error: null })
    setVersion((value) => value + 1)
  }

  return (
    <section className="mx-auto max-w-5xl">
      <header className="mb-6">
        <h1 className="text-3xl font-bold text-slate-900">Características</h1>
        <p className="mt-2 text-slate-600">Características disponibles para los productos del catálogo.</p>
      </header>

      <div className="mb-5 flex justify-end">
        <button aria-label="Crear característica" className="rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white shadow-sm transition-all duration-200 enabled:hover:-translate-y-0.5 enabled:hover:bg-emerald-800 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50" disabled type="button">
          <span className="sm:hidden" aria-hidden="true">+</span>
          <span className="hidden sm:inline" aria-hidden="true">Crear característica</span>
        </button>
      </div>

      {state.loading ? <p role="status">Cargando características…</p> : state.error ? (
        <div className="grid justify-items-start gap-3">
          <RequestError error={state.error} />
          <button className="rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700" onClick={retry} type="button">Reintentar</button>
        </div>
      ) : state.data.length === 0 ? <p role="status">No hay características.</p> : (
        <ul className="grid gap-3">
          {state.data.map((characteristic) => (
            <li className="rounded-xl border border-slate-200 bg-white p-4" key={characteristic.id}>
              <h2 className="font-bold text-slate-900">{characteristic.nombre}</h2>
              <p className="mt-2 text-sm text-slate-600">{characteristic.cantidadProductos} {characteristic.cantidadProductos === 1 ? 'producto' : 'productos'}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
