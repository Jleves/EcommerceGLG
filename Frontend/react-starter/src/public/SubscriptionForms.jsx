import { useState } from 'react'
import { IconBrandWhatsapp, IconMail } from '@tabler/icons-react'
import { subscribe } from './publicService.js'

const channels = [
  { key: 'EMAIL', title: 'Por email', field: 'Tu email', type: 'email', placeholder: 'nombre@ejemplo.com',
    consent: 'Acepto recibir novedades y promociones de GLG Construcciones por email.', Icon: IconMail },
  { key: 'WHATSAPP', title: 'Por WhatsApp', field: 'Tu celular argentino', type: 'tel',
    placeholder: '2984 123456', consent: 'Acepto recibir novedades y promociones de GLG Construcciones por WhatsApp.', Icon: IconBrandWhatsapp },
]

function SubscriptionForm({ channel }) {
  const [contact, setContact] = useState('')
  const [consent, setConsent] = useState(false)
  const [status, setStatus] = useState('idle')
  const [error, setError] = useState('')
  const { Icon } = channel

  async function submit(event) {
    event.preventDefault()
    if (status === 'sending' || !consent) return
    setStatus('sending')
    setError('')
    try {
      await subscribe(channel.key, contact.trim(), true)
      setStatus('success')
      setContact('')
      setConsent(false)
    } catch (failure) {
      setStatus('error')
      setError(failure.status === 429
        ? `Demasiados intentos. Probá nuevamente${failure.retryAfter ? ` en ${Math.ceil(failure.retryAfter / 60)} minuto(s)` : ' más tarde'}.`
        : failure.status === 400 ? 'Revisá el dato ingresado y volvé a intentar.'
          : 'No pudimos guardar tu suscripción. Probá nuevamente.')
    }
  }

  return <form className="subscription-card" onSubmit={submit} aria-label={`Suscripción ${channel.title}`}>
    <div className="subscription-card-heading"><span className="subscription-icon"><Icon size={26} aria-hidden="true" /></span><h3>{channel.title}</h3></div>
    <label className="subscription-field">{channel.field}
      <input type={channel.type} value={contact} onChange={(event) => setContact(event.target.value)}
        placeholder={channel.placeholder} required maxLength={254} disabled={status === 'sending'}
        autoComplete={channel.key === 'EMAIL' ? 'email' : 'tel'} />
    </label>
    {channel.key === 'WHATSAPP' && <p className="subscription-help">Ingresá diez dígitos de área y número, con o sin +54 9.</p>}
    <label className="subscription-consent"><input type="checkbox" checked={consent}
      onChange={(event) => setConsent(event.target.checked)} required disabled={status === 'sending'} />
      <span>{channel.consent}</span></label>
    <button type="submit" disabled={status === 'sending' || !consent}>{status === 'sending' ? 'Guardando…' : 'Suscribirme'}</button>
    {status === 'success' && <p className="subscription-feedback" role="status">Listo, registramos tu suscripción {channel.title.toLowerCase()}.</p>}
    {status === 'error' && <p className="subscription-feedback subscription-feedback-error" role="alert">{error}</p>}
  </form>
}

export function SubscriptionForms() {
  return <section className="home-subscriptions" aria-labelledby="home-subscriptions-title">
    <div className="public-container">
      <div className="subscription-intro"><span className="eyebrow">Novedades para tu obra</span>
        <h2 id="home-subscriptions-title">Enterate de lo nuevo en GLG</h2>
        <p>Elegí cómo querés recibir novedades y promociones. Podés suscribirte a uno o a ambos canales por separado.</p>
      </div>
      <div className="subscription-grid">{channels.map((channel) => <SubscriptionForm key={channel.key} channel={channel} />)}</div>
    </div>
  </section>
}
