import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRouter } from '../app/AppRouter.jsx'
import { getPublicCatalog, getPublicOffers } from './publicService.js'

vi.mock('./publicService.js', () => ({ getPublicCatalog: vi.fn(), getPublicOffers: vi.fn() }))

function renderRoute(path = '/contacto') {
  return render(<MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>)
}

describe('página de contacto', () => {
  beforeEach(() => {
    getPublicCatalog.mockResolvedValue({ categories: [], subcategories: [], facets: [] })
    getPublicOffers.mockResolvedValue({ content: [], page: 0, size: 6, totalElements: 0, totalPages: 0 })
  })
  afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals() })

  it('abre desde el encabezado y muestra ubicación, mapa y horarios', async () => {
    renderRoute('/')
    fireEvent.click(within(screen.getByRole('banner')).getByRole('link', { name: 'Contacto' }))

    expect(screen.getByRole('heading', { name: 'Encontranos en General Roca' })).toBeInTheDocument()
    expect(screen.getByText('Evita 1929, General Roca, Río Negro')).toBeInTheDocument()
    expect(screen.getByTitle('Mapa de GLG Corralón en Evita 1929, General Roca')).toHaveAttribute('loading', 'lazy')
    expect(screen.getByTitle('Mapa de GLG Corralón en Evita 1929, General Roca').getAttribute('src')).toContain('output=embed')
    expect(screen.getByRole('link', { name: /Abrir en Google Maps/ })).toHaveAttribute('href', 'https://maps.app.goo.gl/4oWbz6Rux28LCRLk8')

    const hours = screen.getByRole('heading', { name: 'Horarios' }).closest('section')
    for (const day of ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes']) {
      expect(within(hours).getByText(day).nextElementSibling).toHaveTextContent('8:00–18:30')
    }
    expect(within(hours).getByText('Sábado').nextElementSibling).toHaveTextContent('8:30–14:30')
    expect(within(hours).getByText('Domingo').nextElementSibling).toHaveTextContent('9:30–13:30')
  })

  it('muestra el WhatsApp y las redes con sus enlaces', () => {
    renderRoute()
    expect(screen.getByRole('link', { name: '+54 9 298 440-0705' })).toHaveAttribute('href', 'https://wa.me/5492984400705')
    expect(screen.getByRole('link', { name: /Facebook/ })).toHaveAttribute('href', 'https://www.facebook.com/share/1CXLp6bNtK/')
    expect(screen.getByRole('link', { name: /Instagram/ })).toHaveAttribute('href', 'https://www.instagram.com/corralonglg?dlrf=MXFoOG43eXZ3eTl3NA==')
    expect(within(screen.getByRole('contentinfo')).getByRole('link', { name: 'Contacto' })).toHaveAttribute('href', '/contacto')
  })

  it('enlaza la tarjeta Sucursal del home', () => {
    renderRoute('/')
    expect(screen.getByRole('link', { name: 'Visitá nuestro local en General Roca.' })).toHaveAttribute('href', '/contacto')
  })

  it('copia el número visible y confirma el resultado', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    renderRoute()
    fireEvent.click(screen.getByRole('button', { name: 'Copiar número' }))
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('+54 9 298 440-0705'))
    expect(screen.getByRole('status')).toHaveTextContent('Número copiado')
  })

  it('indica cómo copiar el número si falla el portapapeles', async () => {
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error('blocked')) } })
    renderRoute()
    fireEvent.click(screen.getByRole('button', { name: 'Copiar número' }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Seleccioná el número para copiarlo'))
    expect(screen.getByRole('link', { name: '+54 9 298 440-0705' })).toBeInTheDocument()
  })
})
