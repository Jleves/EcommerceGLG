import { useState } from 'react'
import { IconBrandFacebook, IconBrandInstagram, IconBrandWhatsapp, IconBuildingStore, IconCopy, IconMapPin } from '@tabler/icons-react'
import { whatsappNumber } from './publicConfig.js'
import './contact.css'

const mapsUrl = 'https://maps.app.goo.gl/4oWbz6Rux28LCRLk8'
const mapEmbedUrl = 'https://www.google.com/maps?q=Evita%201929%2C%20General%20Roca%2C%20R%C3%ADo%20Negro&output=embed'
const facebookUrl = 'https://www.facebook.com/share/1CXLp6bNtK/'
const instagramUrl = 'https://www.instagram.com/corralonglg?dlrf=MXFoOG43eXZ3eTl3NA=='
const displayNumber = whatsappNumber.replace(/^54(9)(\d{3})(\d{3})(\d{4})$/, '+54 $1 $2 $3-$4')

const hours = [
  ['Lunes', '8:00–18:30'],
  ['Martes', '8:00–18:30'],
  ['Miércoles', '8:00–18:30'],
  ['Jueves', '8:00–18:30'],
  ['Viernes', '8:00–18:30'],
  ['Sábado', '8:30–14:30'],
  ['Domingo', '9:30–13:30'],
]

export function ContactPage() {
  const [copyStatus, setCopyStatus] = useState('')

  async function copyNumber() {
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable')
      await navigator.clipboard.writeText(displayNumber)
      setCopyStatus('Número copiado')
    } catch {
      setCopyStatus('No se pudo copiar. Seleccioná el número para copiarlo.')
    }
  }

  return <div className="contact-page">
    <section className="contact-intro">
      <div className="public-container">
        <span className="eyebrow">Estamos cerca de tu obra</span>
        <h1>Encontranos en General Roca </h1>
      </div>
    </section>

    <div className="public-container contact-content">
      <div className="contact-main-grid">
        <section className="contact-card contact-location" aria-labelledby="contact-location-title">
          <div className="contact-card-heading"><IconMapPin aria-hidden="true" /><div><span className="eyebrow">Visitá el corralón</span><h2 id="contact-location-title">Dónde estamos</h2></div></div>
          <p>Evita 1929, General Roca, Río Negro</p>
          <div className="contact-map"><iframe title="Mapa de GLG Corralón en Evita 1929, General Roca" src={mapEmbedUrl} loading="lazy" referrerPolicy="no-referrer-when-downgrade" allowFullScreen /></div>
          <a className="contact-text-link" href={mapsUrl} target="_blank" rel="noopener noreferrer">Abrir en Google Maps <span aria-hidden="true">↗</span></a>
        </section>

        <section className="contact-card contact-hours" aria-labelledby="contact-hours-title">
          <div className="contact-card-heading"><IconBuildingStore aria-hidden="true" /><div><span className="eyebrow">Organizá tu visita</span><h2 id="contact-hours-title">Horarios</h2></div></div>
          <dl>{hours.map(([day, time]) => <div key={day}><dt>{day}</dt><dd>{time}</dd></div>)}</dl>
        </section>
      </div>

      <div className="contact-links-grid">
        <section className="contact-card contact-whatsapp" aria-labelledby="contact-whatsapp-title">
          <span className="eyebrow">Escribinos</span>
          <h2 id="contact-whatsapp-title">Consultanos por WhatsApp</h2>
          <p>Te ayudamos a encontrar los materiales para tu proyecto.</p>
          {whatsappNumber && <div className="contact-phone-row">
            <a href={`https://wa.me/${whatsappNumber}`} target="_blank" rel="noopener noreferrer"><IconBrandWhatsapp size={21} aria-hidden="true" />{displayNumber}</a>
            <button type="button" onClick={copyNumber}><IconCopy size={17} aria-hidden="true" />Copiar número</button>
          </div>}
          <p className="contact-copy-status" role="status" aria-live="polite">{copyStatus}</p>
        </section>

        <section className="contact-card contact-social" aria-labelledby="contact-social-title">
          <span className="eyebrow">Novedades y proyectos</span>
          <h2 id="contact-social-title">Seguinos en nuestras redes</h2>
          <div className="contact-social-links">
            <a href={facebookUrl} target="_blank" rel="noopener noreferrer"><IconBrandFacebook size={23} aria-hidden="true" />Facebook <span aria-hidden="true">↗</span></a>
            <a href={instagramUrl} target="_blank" rel="noopener noreferrer"><IconBrandInstagram size={23} aria-hidden="true" />Instagram <span aria-hidden="true">↗</span></a>
          </div>
        </section>
      </div>
    </div>
  </div>
}
