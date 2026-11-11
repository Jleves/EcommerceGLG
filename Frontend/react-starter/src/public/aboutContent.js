// Completar con contenido real y autorizado antes de habilitar la publicación.
// Las fotos deben importarse desde src/assets y describir a las personas o la obra retratada.
export const aboutContent = {
  approved: false,
  heroPhoto: null, // { src, alt }
  history: '',
  projects: [], // [{ title, contribution, image: { src, alt } }]
  teamMoment: null, // Opcional: { quote, image: { src, alt } }
}

export const aboutPagePublished = Boolean(
  aboutContent.approved
  && aboutContent.heroPhoto?.src
  && aboutContent.heroPhoto?.alt
  && aboutContent.history?.trim()
  && aboutContent.projects.length > 0
  && aboutContent.projects.every((project) =>
    project.title?.trim() && project.contribution?.trim() && project.image?.src && project.image?.alt),
)
