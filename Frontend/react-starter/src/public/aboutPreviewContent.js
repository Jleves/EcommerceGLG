import localPhoto from '../assets/Local.jpeg'
import quinchoPhoto from '../assets/Quincho.jpeg'

// Vista previa local con fotos provistas para revisión; los textos aún son de muestra.
export const aboutPreviewContent = {
  heroPhoto: { src: localPhoto, alt: 'Fachada de GLG Construcciones y materiales frente al local' },
  history: 'Acá irá la historia del corralón, contada con palabras del equipo y aprobada por GLG.',
  projects: [{
    title: 'Proyecto pendiente de confirmar',
    contribution: 'Acá se contará qué materiales o asesoramiento aportó GLG a una obra real.',
    image: { src: quinchoPhoto, alt: 'Vista aérea de un quincho en construcción' },
  }],
}
