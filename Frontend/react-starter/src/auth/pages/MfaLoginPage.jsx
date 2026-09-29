import { useEffect, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth.js'
import { AuthCard } from '../components/AuthCard.jsx'
import { FormField } from '../components/FormField.jsx'
import { RequestError } from '../components/RequestError.jsx'
import { LoadingScreen } from '../../app/LoadingScreen.jsx'

const buttonClass = 'cursor-pointer rounded-xl bg-emerald-700 px-4 py-3 font-bold text-white disabled:cursor-not-allowed disabled:opacity-60'
const terminalCodes = new Set(['MFA_CHALLENGE_INVALID'])

export function MfaLoginPage() {
  const { status, mfa, mfaBusy, mfaUncertain, runMfa, cancelMfa } = useAuth()
  const destination = useRef(mfa?.destination || '/app/profile')
  const [code, setCode] = useState('')
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [now, setNow] = useState(() => Date.now())
  const [blockedUntil, setBlockedUntil] = useState(0)
  const [terminal, setTerminal] = useState(false)

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  if (status === 'loading') return <LoadingScreen />
  if (status === 'authenticated') return <Navigate to={destination.current} replace />
  if (status !== 'mfa-required' || !mfa) return <Navigate to="/login" replace />

  const remaining = Math.max(0, Math.ceil((Date.parse(mfa.expiresAt) - now) / 1000))
  const resendWait = Math.max(0, Math.ceil((Math.max(Date.parse(mfa.resendAvailableAt), blockedUntil) - now) / 1000))
  const limited = now < blockedUntil
  const expired = remaining === 0 || terminal

  async function submit(action) {
    if (mfaBusy || (action !== 'recover' && (expired || limited || mfaUncertain))
        || (action === 'verify' && !/^\d{6}$/.test(code)) || (action === 'resend' && resendWait > 0)) return
    setError(null)
    setNotice(null)
    const submitted = code
    setCode('')
    try {
      await runMfa(action, submitted)
      if (action === 'resend') setNotice('Enviamos un código nuevo. Usá el último correo recibido.')
    } catch (failure) {
      setError(failure)
      if (failure.retryAfter != null) setBlockedUntil(Date.now() + failure.retryAfter * 1000)
      if (terminalCodes.has(failure.code)) setTerminal(true)
    }
  }

  return (
    <AuthCard eyebrow="Verificación en dos pasos" title="Ingresá el código" description={`Enviamos un código a ${mfa.maskedEmail}.`}>
      <form className="mt-7 grid gap-5" onSubmit={(event) => { event.preventDefault(); void submit('verify') }}>
        <RequestError error={error} />
        {notice && <p role="status">{notice}</p>}
        {mfaUncertain ? <>
          <p role="status">No pudimos confirmar el resultado. Consultá si se inició la sesión antes de volver a ingresar.</p>
          <button className={buttonClass} type="button" disabled={mfaBusy} onClick={() => submit('recover')}>Consultar sesión</button>
        </> : <>
          <p role="status">{expired ? 'El código ya no está disponible. Volvé a ingresar.' : `El código vence en ${remaining} segundos.`}</p>
          <FormField id="mfa-code" label="Código de verificación" type="text" inputMode="numeric" autoComplete="one-time-code"
            pattern="[0-9]{6}" maxLength={6} required value={code} disabled={mfaBusy || expired}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))} />
          <button className={buttonClass} type="submit" disabled={mfaBusy || expired || limited || code.length !== 6}>Confirmar código</button>
          <button className={buttonClass} type="button" disabled={mfaBusy || expired || resendWait > 0} onClick={() => submit('resend')}>
            {resendWait > 0 ? `Reenviar en ${resendWait} s` : 'Reenviar código'}
          </button>
        </>}
        <button className={buttonClass} type="button" disabled={mfaBusy} onClick={() => { setCode(''); void cancelMfa() }}>Cancelar y volver al login</button>
      </form>
    </AuthCard>
  )
}
