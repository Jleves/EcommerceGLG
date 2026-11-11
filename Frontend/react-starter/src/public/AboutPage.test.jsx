import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AppRouter } from '../app/AppRouter.jsx'
import { AboutPage } from './AboutPage.jsx'
import { aboutPagePublished } from './aboutContent.js'
import { DesktopCategoryNavigation, MobileCategoryNavigation } from './CategoryNavigation.jsx'
import { aboutPreviewContent } from './aboutPreviewContent.js'
import { getPublicCatalog, getPublicOffers } from './publicService.js'

vi.mock('./publicService.js', () => ({ getPublicCatalog: vi.fn(), getPublicOffers: vi.fn() }))

const approvedContent = {
  heroPhoto: { src: '/equipo.webp', alt: 'Equipo de GLG en el corralón' },
  history: 'Nuestra historia real, aprobada por GLG.',
  projects: [{ title: 'Obra del barrio', contribution: 'Acompañamos la selección de materiales.', image: { src: '/obra.webp', alt: 'Obra del barrio durante la construcción' } }],
  teamMoment: { quote: 'Nos gusta escuchar cada proyecto.', image: { src: '/trabajo.webp', alt: 'Integrante del equipo preparando materiales' } },
}

describe('Quiénes somos', () => {
  it('mantiene la ruta y sus enlaces ocultos mientras falta contenido aprobado', async () => {
    getPublicCatalog.mockResolvedValue({ categories: [], subcategories: [], facets: [] })
    getPublicOffers.mockResolvedValue({ content: [], page: 0, size: 6, totalElements: 0, totalPages: 0 })
    expect(aboutPagePublished).toBe(false)
    render(<MemoryRouter initialEntries={['/quienes-somos']}><AppRouter /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'Los materiales de tu próxima historia.' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Quiénes somos' })).not.toBeInTheDocument()
  })

  it('presenta fotos descriptivas, historia y tarjetas sin navegación', () => {
    render(<MemoryRouter><AboutPage content={approvedContent} /></MemoryRouter>)
    expect(screen.getByRole('heading', { level: 1, name: 'Personas detrás de cada obra.' })).toBeInTheDocument()
    expect(screen.getByText(approvedContent.history)).toBeInTheDocument()
    expect(screen.getByRole('img', { name: approvedContent.heroPhoto.alt })).toHaveAttribute('src', approvedContent.heroPhoto.src)
    const card = screen.getByRole('article')
    expect(within(card).getByRole('img', { name: approvedContent.projects[0].image.alt })).toBeInTheDocument()
    expect(within(card).getByText(approvedContent.projects[0].contribution)).toBeInTheDocument()
    expect(within(card).queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Vení a conocernos' })).toHaveAttribute('href', '/contacto')
  })

  it('permite revisar una maqueta local marcada como vista previa', () => {
    getPublicCatalog.mockResolvedValue({ categories: [], subcategories: [], facets: [] })
    render(<MemoryRouter initialEntries={['/quienes-somos/vista-previa']}><AppRouter /></MemoryRouter>)
    expect(screen.getByRole('status')).toHaveTextContent('Vista previa local')
    expect(screen.getByRole('heading', { name: 'Proyecto pendiente de confirmar' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: aboutPreviewContent.heroPhoto.alt })).toHaveAttribute('src', aboutPreviewContent.heroPhoto.src)
    expect(screen.getByRole('img', { name: aboutPreviewContent.projects[0].image.alt })).toHaveAttribute('src', aboutPreviewContent.projects[0].image.src)
  })

  it('incluye el acceso en navegación de escritorio y menú móvil al publicar', () => {
    const catalog = { loading: false, data: { categories: [], subcategories: [] }, error: null }
    render(<MemoryRouter><DesktopCategoryNavigation catalog={catalog} showAbout /><MobileCategoryNavigation catalog={catalog} showAbout /></MemoryRouter>)
    expect(within(screen.getByRole('navigation', { name: 'Navegación pública' })).getByRole('link', { name: 'Quiénes somos' })).toHaveAttribute('href', '/quienes-somos')
    fireEvent.click(screen.getByRole('button', { name: 'Abrir menú' }))
    expect(within(screen.getByRole('dialog', { name: 'Menú' })).getByRole('link', { name: 'Quiénes somos' })).toHaveAttribute('href', '/quienes-somos')
  })
})
