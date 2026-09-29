import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AuthCard } from '../components/AuthCard.jsx'
import { FormField } from '../components/FormField.jsx'
import { RequestError } from '../components/RequestError.jsx'
import { fieldError } from '../components/fieldErrors.js'
import { useAuth } from '../context/useAuth.js'

export function LoginPage() {
  const { login, authNotice } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [blockedUntil, setBlockedUntil] = useState(0)
  const [now, setNow] = useState(() => Date.now())
  const wait = Math.max(0, Math.ceil((blockedUntil - now) / 1000))

  useEffect(() => {
    if (!blockedUntil) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [blockedUntil])

  const destination = location.state?.from || '/app/profile'

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting || wait > 0) return
    setError(null)
    setSubmitting(true)
    try {
      const result = await login(form, destination)
      setForm({ email: '', password: '' })
      if (result) navigate(result.status === 'MFA_REQUIRED' ? '/login/mfa' : destination, { replace: true })
    } catch (requestError) {
      setError(requestError)
      if (requestError.retryAfter != null) {
        setNow(Date.now())
        setBlockedUntil(Date.now() + requestError.retryAfter * 1000)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard
      eyebrow="Bienvenido"
      title="Ingresá a tu cuenta"
      description="Usá las credenciales configuradas para tu entorno."
      footer={<span>¿Olvidaste tu contraseña? <Link to="/forgot-password">Recuperarla</Link></span>}
    >
      {(authNotice === 'password-changed' || location.state?.passwordChanged) && <p className="mt-5 text-emerald-800" role="status">Contraseña actualizada. Ingresá con tu contraseña nueva.</p>}
      <form className="mt-7 grid gap-5" onSubmit={handleSubmit}>
        {authNotice === 'mfa-cancel-unconfirmed' && <p role="status">Volvé a ingresar. No se pudo confirmar la cancelación del código anterior.</p>}
        {authNotice === 'mfa-restart' && <p role="status">No se confirmó una sesión. Ingresá nuevamente para solicitar otro código.</p>}
        {authNotice === 'mfa-enabled' && <p role="status">Verificación en dos pasos activada. Volvé a ingresar con tu contraseña y el código que recibirás por correo.</p>}
        {authNotice === 'mfa-disabled' && <p role="status">Verificación en dos pasos desactivada. Volvé a ingresar.</p>}
        {authNotice === 'mfa-settings-session-expired' && <p role="status">Tu sesión ya no permite cambiar la verificación en dos pasos. Volvé a ingresar para consultar su estado.</p>}
        {authNotice === 'mfa-settings-uncertain' && <p role="status">No pudimos confirmar si cambió la verificación en dos pasos. Volvé a ingresar y consultá su estado en el perfil.</p>}
        {authNotice === 'admin-email-changed' && <p role="status">Email actualizado. Ingresá con el nuevo email.</p>}
        {authNotice === 'admin-email-uncertain' && <p role="status">No pudimos confirmar si cambió tu email. Volvé a ingresar y consultá el estado de la cuenta.</p>}
        <RequestError error={error} />
        <FormField id="email" label="Email" type="email" autoComplete="email" required value={form.email} error={fieldError(error, 'email')} onChange={(event) => setForm({ ...form, email: event.target.value })} />
        <FormField id="password" label="Contraseña" type="password" autoComplete="current-password" required value={form.password} error={fieldError(error, 'password')} onChange={(event) => setForm({ ...form, password: event.target.value })} />
        {wait > 0 && <p role="status">Podés volver a intentar en {wait} segundos.</p>}
        <button className="cursor-pointer rounded-xl bg-emerald-700 px-4 py-3 font-bold text-white transition hover:-translate-y-px hover:bg-emerald-800 disabled:cursor-wait disabled:opacity-60" disabled={submitting || wait > 0} type="submit">
          {submitting ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </AuthCard>
  )
}
