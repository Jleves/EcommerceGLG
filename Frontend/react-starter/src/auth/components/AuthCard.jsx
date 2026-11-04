import { Link } from 'react-router-dom'
import glgLogo from '../../assets/glg-logo-white.png'
import './auth.css'

export function AuthCard({ eyebrow, title, description, children, footer }) {
  return (
    <main className="auth-shell">
      <section className="auth-presentation" aria-label="Presentación">
        <Link className="auth-brand" to="/" aria-label="GLG Corralón, ir al inicio">
          <img src={glgLogo} alt="GLG Corralón Construcciones" />
        </Link>
        <div className="auth-presentation-copy">
          <p className="auth-overline">GLG Construcciones / General Roca</p>
          <h1>El corralón, en tus manos.</h1>
          <p>Accedé para gestionar el catálogo y acompañar cada proyecto de nuestros clientes.</p>
        </div>
      </section>
      <section className="auth-form-panel" aria-label="Acceso del personal">
        <div className="auth-card">
          {eyebrow && <p className="auth-eyebrow">{eyebrow}</p>}
          <h2>{title}</h2>
          {description && <p className="auth-description">{description}</p>}
          {children}
          {footer && <div className="auth-footer">{footer}</div>}
        </div>
      </section>
    </main>
  )
}
