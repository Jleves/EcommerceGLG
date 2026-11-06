import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { RequestError } from '../../auth/components/RequestError.jsx'
import { exportSubscriptions, listSubscriptions } from '../services/adminSubscriptionService.js'

const dateFormat = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Buenos_Aires',
})

function displayDate(value) {
  return value ? dateFormat.format(new Date(value)) : '—'
}

export function SubscriptionListPage({ channel }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawPage = searchParams.get('page')
  const page = rawPage !== null && /^(0|[1-9]\d*)$/.test(rawPage) && Number.isSafeInteger(Number(rawPage))
    ? Number(rawPage) : 0
  const [state, setState] = useState({ channel, page, loading: true, data: null, error: null })
  const [download, setDownload] = useState({ loading: false, error: null })
  const current = state.channel === channel && state.page === page ? state : { loading: true, data: null, error: null }

  useEffect(() => {
    const controller = new AbortController()
    let mounted = true
    listSubscriptions(channel, page, { signal: controller.signal }).then(
      (data) => { if (mounted) setState({ channel, page, loading: false, data, error: null }) },
      (error) => { if (mounted && error.name !== 'AbortError') setState({ channel, page, loading: false, data: null, error }) },
    )
    return () => { mounted = false; controller.abort() }
  }, [channel, page])

  useEffect(() => { setDownload({ loading: false, error: null }) }, [channel])

  function goToPage(next) {
    setSearchParams(next === 0 ? {} : { page: String(next) })
  }

  async function handleDownload() {
    setDownload({ loading: true, error: null })
    try {
      const blob = await exportSubscriptions(channel)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      const dateParts = new Intl.DateTimeFormat('en-US', { timeZone: 'America/Buenos_Aires',
        year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
      const date = ['year', 'month', 'day'].map((type) => dateParts.find((part) => part.type === type).value).join('-')
      link.download = `suscriptores-${channel.toLowerCase()}-${date}.xlsx`
      document.body.append(link)
      link.click()
      link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
      setDownload({ loading: false, error: null })
    } catch (error) {
      setDownload({ loading: false, error })
    }
  }

  const label = channel === 'EMAIL' ? 'Email' : 'WhatsApp'
  return <section className="mx-auto max-w-5xl">
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div><h1 className="text-3xl font-bold">Suscriptores por {label}</h1>
        <p className="mt-2 text-slate-600">La tabla incluye bajas. El Excel contiene solo contactos activos.</p></div>
      <button className="rounded-lg bg-emerald-700 px-4 py-3 font-bold text-white disabled:opacity-50" type="button"
        disabled={download.loading} onClick={handleDownload}>{download.loading ? 'Preparando Excel…' : 'Descargar Excel'}</button>
    </div>
    <RequestError error={download.error} />
    {current.loading ? <p role="status">Cargando suscriptores…</p> : current.error ? <RequestError error={current.error} /> : <>
      {current.data.content.length === 0 ? <p role="status">No hay suscriptores en esta página.</p> : <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left"><thead className="bg-slate-100"><tr>
          <th className="p-3">{label}</th><th className="p-3">Consentimiento</th><th className="p-3">Estado</th><th className="p-3">Baja</th>
        </tr></thead><tbody>{current.data.content.map((row) => <tr key={row.id} className="border-t border-slate-200">
          <td className="p-3 [overflow-wrap:anywhere]">{row.contact}</td><td className="p-3">{displayDate(row.consentedAt)}</td>
          <td className="p-3">{row.active ? 'Activo' : 'Baja'}</td><td className="p-3">{displayDate(row.withdrawnAt)}</td>
        </tr>)}</tbody></table>
      </div>}
      <nav aria-label={`Páginas de suscriptores por ${label}`} className="mt-6 flex items-center gap-4">
        <button className="rounded-lg border border-slate-300 px-4 py-2 disabled:opacity-50" type="button" disabled={page === 0} onClick={() => goToPage(page - 1)}>Anterior</button>
        <span>Página {page + 1} de {Math.max(1, current.data.totalPages)} · {current.data.totalElements} suscriptores</span>
        <button className="rounded-lg border border-slate-300 px-4 py-2 disabled:opacity-50" type="button" disabled={page + 1 >= current.data.totalPages} onClick={() => goToPage(page + 1)}>Siguiente</button>
      </nav>
    </>}
  </section>
}
