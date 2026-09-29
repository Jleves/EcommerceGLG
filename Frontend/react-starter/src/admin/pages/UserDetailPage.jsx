import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { FormField } from '../../auth/components/FormField.jsx'
import { RequestError } from '../../auth/components/RequestError.jsx'
import { fieldError } from '../../auth/components/fieldErrors.js'
import { useAuth } from '../../auth/context/useAuth.js'
import { deactivateUser, getUser, reactivateUser, updateUserEmail } from '../services/adminUserService.js'

const isUncertain = (error) => !error?.status || error.status >= 500
const messages = {
  ADMIN_EMAIL_IN_USE: 'Ese email ya está en uso.',
  ADMIN_EMAIL_MFA_ENABLED: 'El titular debe desactivar MFA antes de cambiar su email.',
  ADMIN_SELF_DEACTIVATION: 'No podés desactivar tu propia cuenta.',
  ADMIN_LAST_SUPER_ADMIN: 'No se puede desactivar al último SUPER_ADMIN activo.',
}
const displayError = (error) => ({ ...error, message: messages[error?.code] || error?.message || 'No se pudo completar la solicitud.' })

export function UserDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user: actor, endSession } = useAuth()
  const [state, setState] = useState({ id, loading: true, user: null, error: null })
  const [email, setEmail] = useState('')
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [pending, setPending] = useState(false)
  const [needsReview, setNeedsReview] = useState(false)
  const [confirming, setConfirming] = useState(null)
  const inFlight = useRef(false)
  const generation = useRef(0)
  const current = state.id === id ? state : { loading: true, user: null, error: null }
  const self = String(actor?.id) === String(id)

  useEffect(() => {
    const controller = new AbortController()
    const turn = ++generation.current
    getUser(id, { signal: controller.signal }).then(
      (user) => {
        if (turn === generation.current) {
          setState({ id, loading: false, user, error: null })
          setEmail(user.email)
        }
      },
      (requestError) => {
        if (turn === generation.current && requestError.name !== 'AbortError') {
          setState({ id, loading: false, user: null, error: requestError })
        }
      },
    )
    // Invalidate pending reads and writes when this detail is abandoned.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    return () => { if (generation.current === turn) generation.current++; controller.abort() }
  }, [id])

  async function reviewDetail() {
    if (inFlight.current) return
    inFlight.current = true
    setPending(true)
    setError(null)
    const turn = generation.current
    try {
      const user = await getUser(id, { retryAuth: false })
      if (turn !== generation.current) return
      setState({ id, loading: false, user, error: null })
      setEmail(user.email)
      setNeedsReview(false)
      setNotice('Estado actual consultado. Esta consulta no confirma si la acción anterior se aplicó.')
    } catch (requestError) {
      if (turn !== generation.current) return
      if (requestError.status === 401 && self) {
        endSession('admin-email-uncertain')
        navigate('/login', { replace: true })
      } else setError(displayError(requestError))
    } finally {
      inFlight.current = false
      if (turn === generation.current) setPending(false)
    }
  }

  async function perform(action) {
    if (inFlight.current || needsReview || !current.user) return
    inFlight.current = true
    setPending(true)
    setError(null)
    setNotice(null)
    setConfirming(null)
    const turn = generation.current
    const previous = current.user
    try {
      const updated = action === 'email' ? await updateUserEmail(id, email.trim().toLowerCase())
        : action === 'deactivate' ? await deactivateUser(id) : await reactivateUser(id)
      if (turn !== generation.current) return
      if (action === 'email' && self && updated.email !== previous.email) {
        endSession('admin-email-changed')
        navigate('/login', { replace: true })
        return
      }
      setState({ id, loading: false, user: updated, error: null })
      setEmail(updated.email)
      const noOp = action === 'email' ? updated.email === previous.email : updated.enabled === previous.enabled
      setNotice(noOp ? 'No hubo cambios; el estado ya era el solicitado.' : 'Cambio confirmado por el servidor.')
    } catch (requestError) {
      if (turn !== generation.current) return
      if (isUncertain(requestError)) {
        setNeedsReview(true)
        setNotice('No se pudo confirmar el resultado. Consultá el detalle antes de decidir qué hacer; no se repetirá la acción automáticamente.')
      } else setError(displayError(requestError))
    } finally {
      inFlight.current = false
      if (turn === generation.current) setPending(false)
    }
  }

  const rows = current.user && [
    ['Identificador', current.user.id], ['Email', current.user.email], ['Rol', current.user.role],
    ['Estado', current.user.enabled ? 'Activa' : 'Inactiva'],
    ['MFA', current.user.mfaEnabled ? 'Activado' : 'Desactivado'],
    ['Creada', current.user.createdAt || '—'], ['Actualizada', current.user.updatedAt || '—'],
  ]

  return <section className="mx-auto max-w-3xl">
    <Link to="/app/admin/users">← Volver a usuarios</Link>
    <h1 className="mt-6 text-3xl font-bold text-slate-900">Detalle de usuario</h1>
    {current.loading ? <p className="mt-5" role="status">Cargando usuario…</p> : current.error?.status === 404 ?
      <p className="mt-5" role="alert">El usuario no existe.</p> : current.error ?
      <div className="mt-5"><RequestError error={current.error} /></div> : <>
        <dl className="mt-6 rounded-2xl border border-slate-200 bg-white p-6">
          {rows.map(([label, value]) => <div className="grid gap-1 border-b border-slate-100 py-3 last:border-0 sm:grid-cols-[10rem_1fr]" key={label}>
            <dt className="text-slate-500">{label}</dt><dd className="m-0 [overflow-wrap:anywhere] font-semibold">{value}</dd>
          </div>)}
        </dl>
        {notice && <p className="mt-5" role="status">{notice}</p>}
        <RequestError error={error} />
        {needsReview && <button className="mt-4 cursor-pointer rounded-xl border px-4 py-2 transition-all duration-150 enabled:hover:bg-slate-50 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-wait disabled:opacity-50" disabled={pending} onClick={reviewDetail} type="button">Consultar estado actual</button>}
        <div className="mt-6 grid gap-6 rounded-2xl border border-slate-200 bg-white p-6">
          <form onSubmit={(event) => { event.preventDefault(); void perform('email') }}>
            <h2 className="mb-3 text-xl font-semibold">Cambiar email</h2>
            {current.user.mfaEnabled && <p className="mb-3">El titular debe desactivar MFA antes de cambiar a otro email. La baja de otra cuenta sigue disponible.</p>}
            <FormField id="admin-email" label="Nuevo email" type="email" required maxLength={320} value={email} error={fieldError(error, 'email')} onChange={(event) => setEmail(event.target.value)} />
            <button className="mt-3 cursor-pointer rounded-xl bg-emerald-700 px-4 py-2 text-white transition-all duration-150 enabled:hover:bg-emerald-800 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-wait disabled:opacity-50" disabled={pending || needsReview} type="submit">Guardar email</button>
          </form>
          <div>
            <h2 className="mb-3 text-xl font-semibold">Estado de la cuenta</h2>
            {current.user.enabled && !self && <button className="cursor-pointer rounded-xl border px-4 py-2 transition-all duration-150 enabled:hover:bg-slate-50 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-wait disabled:opacity-50" disabled={pending || needsReview} onClick={() => setConfirming('deactivate')} type="button">Desactivar cuenta</button>}
            {current.user.enabled && self && <p>No podés desactivar tu propia cuenta.</p>}
            {!current.user.enabled && <button className="cursor-pointer rounded-xl border px-4 py-2 transition-all duration-150 enabled:hover:bg-slate-50 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-wait disabled:opacity-50" disabled={pending || needsReview} onClick={() => setConfirming('reactivate')} type="button">Reactivar cuenta</button>}
            {confirming && <div className="mt-3 rounded-xl border border-amber-300 bg-amber-50 p-4">
              <p>Confirmá {confirming === 'deactivate' ? 'la baja' : 'la reactivación'} de {current.user.email}. La reactivación requiere un nuevo ingreso y conserva MFA.</p>
              <button className="mt-2 cursor-pointer rounded-xl bg-emerald-700 px-4 py-2 text-white transition-all duration-150 enabled:hover:bg-emerald-800 enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-wait disabled:opacity-50" disabled={pending} onClick={() => void perform(confirming)} type="button">Confirmar {confirming === 'deactivate' ? 'baja' : 'reactivación'}</button>
              <button className="ml-2 cursor-pointer rounded-xl border px-4 py-2 transition-all duration-150 enabled:hover:bg-white enabled:hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-wait disabled:opacity-50" disabled={pending} onClick={() => setConfirming(null)} type="button">Cancelar</button>
            </div>}
          </div>
        </div>
      </>}
  </section>
}
