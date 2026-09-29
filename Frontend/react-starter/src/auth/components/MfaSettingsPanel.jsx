import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth.js'
import * as settingsService from '../services/mfaSettingsService.js'
import { RequestError } from './RequestError.jsx'
import { MfaSettingsCodeForm, MfaSettingsPasswordForm } from './MfaSettingsForms.jsx'

export function MfaSettingsPanel() {
  const { endSession } = useAuth()
  const navigate = useNavigate()
  const [settings, setSettings] = useState(null)
  const [challenge, setChallenge] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [blockedUntil, setBlockedUntil] = useState(0)
  const [now, setNow] = useState(() => Date.now())
  const mounted = useRef(false)
  const inFlight = useRef(false)

  useEffect(() => {
    mounted.current = true
    let active = true
    // Mount only reads state; never send a code from an effect or cleanup.
    settingsService.getStatus().then((value) => {
      if (active) setSettings(value)
    }).catch((failure) => {
      if (!active) return
      if (failure.retryAfter != null) setBlockedUntil(Date.now() + failure.retryAfter * 1000)
      if (failure.status === 401) endSession('mfa-settings-session-expired')
      else setError(failure)
    }).finally(() => { if (active) setLoading(false) })
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => { active = false; mounted.current = false; clearInterval(timer) }
  }, [endSession])

  function finish(noticeCode) {
    setChallenge(null)
    endSession(noticeCode)
    navigate('/login', { replace: true })
  }

  // Read authoritative state after conflicts/uncertain responses, without replaying mutations.
  async function reconcile(wasUncertain) {
    setChallenge(null)
    setSettings(null)
    setUncertain(wasUncertain)
    try {
      const current = await settingsService.getStatus()
      if (!mounted.current) return
      setSettings(current)
      setUncertain(false)
      setNotice(wasUncertain
        ? 'Consultamos el estado actual. No se repitió el cambio; para intentarlo de nuevo, comenzá con tu contraseña.'
        : 'La configuración cambió. Consultamos su estado actual; comenzá de nuevo si querés modificarla.')
    } catch (failure) {
      if (!mounted.current) return
      if (failure.status === 401) finish(wasUncertain ? 'mfa-settings-uncertain' : 'mfa-settings-session-expired')
      else setError(failure)
    }
  }

  async function perform(action, value) {
    if (inFlight.current || loading) return
    if (action !== 'status' && action !== 'cancel' && (uncertain || Date.now() < blockedUntil)) return
    if (action === 'start' && (!settings || challenge)) return
    if ((action === 'confirm' || action === 'resend') && (!challenge || Date.now() >= Date.parse(challenge.expiresAt))) return
    if (action === 'resend' && Date.now() < Date.parse(challenge.resendAvailableAt)) return
    inFlight.current = true
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      if (action === 'status') {
        const current = await settingsService.getStatus()
        if (mounted.current) {
          setSettings(current)
          setUncertain(false)
          setNotice('Estado consultado. No se repitió ningún cambio.')
        }
      } else if (action === 'start') {
        const enabled = !settings.enabled
        const timing = await settingsService.start(enabled, value)
        if (mounted.current) setChallenge({ ...timing, enabled })
      } else if (action === 'confirm') {
        await settingsService.confirm(challenge.enabled, value)
        if (mounted.current) finish(challenge.enabled ? 'mfa-enabled' : 'mfa-disabled')
      } else if (action === 'resend') {
        const timing = await settingsService.resend()
        if (mounted.current) {
          setChallenge((pending) => ({ ...pending, ...timing }))
          setNotice('Enviamos un código nuevo. Usá el último correo recibido.')
        }
      } else if (action === 'cancel') {
        setChallenge(null)
        await settingsService.cancel()
        if (mounted.current) setNotice('Solicitud cancelada. No se cambió la configuración desde este formulario.')
      }
    } catch (failure) {
      if (!mounted.current) return
      if (failure.retryAfter != null) setBlockedUntil(Date.now() + failure.retryAfter * 1000)
      if (action === 'cancel') {
        setError({ message: 'Se limpió el formulario, pero no se pudo confirmar la cancelación en el servidor. El código anterior puede seguir vigente hasta vencer o ser reemplazado.' })
      } else if (failure.status === 401) {
        finish(uncertain ? 'mfa-settings-uncertain' : 'mfa-settings-session-expired')
      } else if (failure.status === 409) {
        await reconcile(false)
      } else if (action === 'confirm' && (!failure.status || failure.status >= 500)) {
        await reconcile(true)
      } else {
        setError(failure)
        if (failure.code === 'MFA_CHALLENGE_INVALID') setChallenge(null)
      }
    } finally {
      inFlight.current = false
      if (mounted.current) setBusy(false)
    }
  }

  const remaining = challenge ? Math.max(0, Math.ceil((Date.parse(challenge.expiresAt) - now) / 1000)) : 0
  const wait = Math.max(0, Math.ceil((blockedUntil - now) / 1000))
  const resendWait = challenge ? Math.max(wait, Math.ceil((Date.parse(challenge.resendAvailableAt) - now) / 1000)) : 0
  return <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8" aria-labelledby="mfa-settings-title">
    <h2 id="mfa-settings-title" className="text-xl font-bold text-slate-900">Verificación en dos pasos por correo</h2>
    <p className="mt-2 text-slate-600">Administrá la protección de tu cuenta. Para cambiarla necesitás tu contraseña actual y un código nuevo enviado a tu correo.</p>
    <div className="mt-5 grid gap-3">
      <RequestError error={error} />
      {loading && <p role="status">Consultando verificación en dos pasos…</p>}
      {settings && <p>Estado consultado: <strong>{settings.enabled ? 'Activada' : 'Desactivada'}</strong>. Correo: {settings.maskedEmail}.</p>}
      {notice && <p role="status">{notice}</p>}
      {wait > 0 && <p role="status">Podés volver a intentar en {wait} segundos.</p>}
      {uncertain && <p role="status">No pudimos confirmar el resultado. Consultá el estado o volvé a ingresar antes de iniciar otro cambio.</p>}
      {!loading && (!settings || uncertain) && <div className="flex gap-4">
        <button type="button" disabled={busy || wait > 0} onClick={() => perform('status')}>Consultar estado MFA</button>
        <button type="button" disabled={busy} onClick={() => finish(uncertain ? 'mfa-settings-uncertain' : 'mfa-settings-session-expired')}>Volver al login</button>
      </div>}
      {!loading && settings && !uncertain && (challenge
        ? <MfaSettingsCodeForm enabled={challenge.enabled} disabled={busy || wait > 0} busy={busy} expired={remaining === 0}
            remaining={remaining} resendWait={resendWait} onConfirm={(code) => perform('confirm', code)}
            onResend={() => perform('resend')} onCancel={() => perform('cancel')} />
        : <MfaSettingsPasswordForm enabled={settings.enabled} disabled={busy || wait > 0} error={error}
            onSubmit={(password) => perform('start', password)} />)}
    </div>
  </section>
}
