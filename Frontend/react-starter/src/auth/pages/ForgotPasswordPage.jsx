import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AuthCard } from '../components/AuthCard.jsx'
import { FormField } from '../components/FormField.jsx'
import { RequestError } from '../components/RequestError.jsx'
import { fieldError } from '../components/fieldErrors.js'
import { requestPasswordReset } from '../services/authService.js'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const response = await requestPasswordReset(email)
      setMessage(response || 'Si el correo existe, recibirás instrucciones.')
    } catch (requestError) {
      setError(requestError)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard eyebrow="Acceso para el personal" title="Recuperar contraseña" description="Te enviaremos las instrucciones si encontramos una cuenta asociada." footer={<Link to="/login">Volver al ingreso</Link>}>
      {message ? <div className="auth-success mt-6" role="status">{message}</div> : (
        <form className="mt-7 grid gap-5" onSubmit={handleSubmit}>
          <RequestError error={error} />
          <FormField id="email" label="Email" type="email" autoComplete="email" required value={email} error={fieldError(error, 'email')} onChange={(event) => setEmail(event.target.value)} />
          <button className="auth-primary cursor-pointer px-4 py-3 transition disabled:cursor-wait" disabled={submitting} type="submit">{submitting ? 'Enviando…' : 'Enviar instrucciones'}</button>
        </form>
      )}
    </AuthCard>
  )
}
