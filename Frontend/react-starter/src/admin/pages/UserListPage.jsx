import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { RequestError } from '../../auth/components/RequestError.jsx'
import { listUsers } from '../services/adminUserService.js'

export function UserListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawPage = searchParams.get('page')
  const page = rawPage !== null && /^(0|[1-9]\d*)$/.test(rawPage) && Number.isSafeInteger(Number(rawPage))
    ? Number(rawPage) : 0
  const [state, setState] = useState({ page, loading: true, data: null, error: null })
  const currentState = state.page === page ? state : { loading: true, data: null, error: null }

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    listUsers(page, { signal: controller.signal }).then(
      (data) => { if (current) setState({ page, loading: false, data, error: null }) },
      (error) => {
        if (current && error.name !== 'AbortError') setState({ page, loading: false, data: null, error })
      },
    )
    return () => { current = false; controller.abort() }
  }, [page])

  function goToPage(nextPage) {
    setState({ page: nextPage, loading: true, data: null, error: null })
    setSearchParams(nextPage === 0 ? {} : { page: String(nextPage) })
  }

  return (
    <section className="mx-auto max-w-5xl">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div><h1 className="text-3xl font-bold text-slate-900">Usuarios</h1><p className="mt-2 text-slate-600">Cuentas activas e inactivas, ordenadas por identificador.</p></div>
        <Link className="rounded-xl bg-emerald-700 px-4 py-3 font-bold text-white hover:bg-emerald-800 hover:no-underline" to="/app/admin/users/new">Crear usuario</Link>
      </div>
      {currentState.loading ? <p role="status">Cargando usuarios…</p> : currentState.error ? <RequestError error={currentState.error} /> : (
        <>
          {currentState.data.content.length === 0 ? <p role="status">No hay usuarios en esta página.</p> : (
            <ul className="grid gap-3">
              {currentState.data.content.map((user) => (
                <li key={user.id}>
                  <Link className="block rounded-xl border border-slate-200 bg-white p-4 text-slate-900 hover:bg-slate-100 hover:no-underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700" to={`/app/admin/users/${user.id}`}>
                    <span className="font-semibold [overflow-wrap:anywhere]">{user.email}</span>
                    <span className="mt-1 block text-sm text-slate-600">ID {user.id} · {user.role} · {user.enabled ? 'Activa' : 'Inactiva'}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <nav aria-label="Páginas de usuarios" className="mt-6 flex items-center gap-4">
            <button className="rounded-lg border border-slate-300 px-4 py-2 disabled:opacity-50" disabled={page === 0} onClick={() => goToPage(page - 1)} type="button">Anterior</button>
            <span>Página {page + 1} de {Math.max(1, currentState.data.totalPages)} · {currentState.data.totalElements} usuarios</span>
            <button className="rounded-lg border border-slate-300 px-4 py-2 disabled:opacity-50" disabled={page + 1 >= currentState.data.totalPages} onClick={() => goToPage(page + 1)} type="button">Siguiente</button>
          </nav>
        </>
      )}
    </section>
  )
}
