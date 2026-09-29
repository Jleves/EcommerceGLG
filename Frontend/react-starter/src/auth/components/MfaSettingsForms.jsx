import { useState } from 'react'
import { FormField } from './FormField.jsx'
import { fieldError } from './fieldErrors.js'

const buttonClass = 'rounded-xl bg-emerald-700 px-4 py-3 font-bold text-white disabled:cursor-not-allowed disabled:opacity-60'

export function MfaSettingsPasswordForm({ enabled, disabled, error, onSubmit }) {
  const [password, setPassword] = useState('')
  function submit(event) {
    event.preventDefault()
    if (disabled || !password) return
    const value = password
    setPassword('')
    void onSubmit(value)
  }
  return <form className="mt-5 grid gap-5" onSubmit={submit}>
    <fieldset className="grid gap-5" disabled={disabled}>
      <FormField id="mfa-current-password" label="Contraseña actual para verificación en dos pasos" type="password"
        autoComplete="current-password" required maxLength={72} value={password}
        error={fieldError(error, 'currentPassword')} onChange={(event) => setPassword(event.target.value)} />
      <button className={buttonClass} type="submit">{enabled ? 'Desactivar verificación en dos pasos' : 'Activar verificación en dos pasos'}</button>
    </fieldset>
  </form>
}

export function MfaSettingsCodeForm({ enabled, disabled, busy, expired, remaining, resendWait, onConfirm, onResend, onCancel }) {
  const [code, setCode] = useState('')
  function submit(event) {
    event.preventDefault()
    if (disabled || expired || !/^\d{6}$/.test(code)) return
    const value = code
    setCode('')
    void onConfirm(value)
  }
  return <form className="mt-5 grid gap-5" onSubmit={submit}>
    <p role="status">{expired ? 'El código venció o ya no está disponible. Cancelá para comenzar de nuevo.' : `El código vence en ${remaining} segundos.`}</p>
    <p className="text-slate-600">Al confirmar se cerrará esta sesión y las demás dejarán de poder renovarse. Sus accesos actuales pueden seguir vigentes hasta vencer.</p>
    <FormField id="mfa-settings-code" label="Código para cambiar la verificación en dos pasos" type="text"
      inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required
      value={code} disabled={disabled || expired}
      onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))} />
    <button className={buttonClass} type="submit" disabled={disabled || expired || code.length !== 6}>
      {enabled ? 'Confirmar activación' : 'Confirmar desactivación'}
    </button>
    <button className={buttonClass} type="button" disabled={disabled || expired || resendWait > 0}
      onClick={() => { setCode(''); void onResend() }}>
      {resendWait > 0 ? `Reenviar en ${resendWait} s` : 'Reenviar código de configuración'}
    </button>
    <button className={buttonClass} type="button" disabled={busy}
      onClick={() => { setCode(''); void onCancel() }}>Cancelar cambio de verificación</button>
  </form>
}
