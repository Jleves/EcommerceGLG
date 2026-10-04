import { useEffect, useState } from 'react'
import { RequestError } from '../../../auth/components/RequestError.jsx'
import { CategoryIcon } from '../components/CategoryIcon.jsx'
import { listCategories } from '../services/categoryService.js'

export function CategoryManagementPage() {
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ loading: true, data: null, error: null })

  useEffect(() => {
    const controller = new AbortController()
    let current = true

    listCategories({ signal: controller.signal }).then(
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
        <h1 className="text-3xl font-bold text-slate-900">Categorías</h1>
        <p className="mt-2 text-slate-600">Categorías activas e inactivas del catálogo.</p>
      </header>

      {state.loading ? <p role="status">Cargando categorías…</p> : state.error ? (
        <div className="grid justify-items-start gap-4">
          <RequestError error={state.error} />
          <button className="rounded-xl bg-emerald-700 px-4 py-2 font-bold text-white hover:bg-emerald-800" onClick={retry} type="button">Reintentar</button>
        </div>
      ) : state.data.length === 0 ? <p role="status">No hay categorías.</p> : (
        <ul className="grid gap-3">
          {state.data.map((category) => (
            <li className="rounded-xl border border-slate-200 bg-white p-4" key={category.id}>
              <div className="flex items-start gap-4">
                <CategoryIcon name={category.icono} />
                <div>
                  <h2 className="font-bold text-slate-900">{category.nombre}</h2>
                  {category.descripcion && <p className="mt-1 text-slate-700">{category.descripcion}</p>}
                  <p className="mt-2 text-sm text-slate-600">ID {category.id} · {category.activo ? 'Activa' : 'Inactiva'}</p>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
