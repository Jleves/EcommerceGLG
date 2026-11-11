import { Link } from 'react-router-dom'
import { aboutContent } from './aboutContent.js'
import './about.css'

export function AboutPage({ content = aboutContent }) {
  return <div className="about-page">
    {content.preview && <div className="about-preview-notice" role="status">Vista previa local · Textos de muestra. Pendiente de aprobación para publicar.</div>}
    <section className="about-hero" aria-labelledby="about-title">
      <img className="about-hero-photo" src={content.heroPhoto.src} alt={content.heroPhoto.alt} />
      <div className="about-hero-shade" />
      <div className="public-container about-hero-content">
        <span className="eyebrow">Quiénes somos</span>
        <h1 id="about-title">Personas detrás de cada obra.</h1>
        <p>Detrás de cada obra hay personas. En GLG Corralón queremos escuchar lo que necesitás, ayudarte a elegir los materiales y acompañarte en cada etapa de tu proyecto. Nos encontrás en General Roca, con ganas de conversar y poner manos a la obra.</p>
      </div>
    </section>

    <section className="about-history public-container" aria-labelledby="about-history-title">
      <span className="eyebrow">De dónde venimos</span>
      <h2 id="about-history-title">Nuestra historia</h2>
      <p>{content.history}</p>
    </section>

    {content.teamMoment?.quote && content.teamMoment.image?.src && content.teamMoment.image?.alt && <section className="about-team-moment" aria-label="Un momento con el equipo">
      <div className="public-container about-team-moment-inner">
        <img src={content.teamMoment.image.src} alt={content.teamMoment.image.alt} loading="lazy" />
        <blockquote>“{content.teamMoment.quote}”</blockquote>
      </div>
    </section>}

    <section className="about-projects" aria-labelledby="about-projects-title"><div className="public-container">
      <span className="eyebrow">Manos a la obra</span>
      <h2 id="about-projects-title">Proyectos en los que colaboramos</h2>
      <div className="about-project-grid">{content.projects.map((project) => <article className="about-project-card" key={project.title}>
        <img src={project.image.src} alt={project.image.alt} loading="lazy" />
        <div><h3>{project.title}</h3><p>{project.contribution}</p></div>
      </article>)}</div>
    </div></section>

    <section className="about-closing"><div className="public-container">
      <span className="eyebrow">GLG Corralón · General Roca</span>
      <h2>Manos a la obra, juntos.</h2>
      <p>Nos gusta conocer a quienes construyen con nosotros.</p>
      <Link className="accent-button" to="/contacto">Vení a conocernos</Link>
    </div></section>
  </div>
}
