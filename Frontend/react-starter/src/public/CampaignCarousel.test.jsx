import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { AppRouter } from '../app/AppRouter.jsx'
import { getPublicCatalog } from './publicService.js'

vi.mock('./publicService.js', () => ({ getPublicCatalog: vi.fn() }))
vi.mock('./publicConfig.js', () => ({ campaignPhotos: [
  { src: 'data:image/png;base64,one', alt: 'Campaña uno' },
  { src: 'data:image/png;base64,two', alt: 'Campaña dos' },
  { src: 'data:image/png;base64,three', alt: 'Campaña tres' },
], brandLogos: [], whatsappNumber: '' }))

beforeEach(() => getPublicCatalog.mockResolvedValue({ categories: [], facets: [] }))
afterEach(() => vi.useRealTimers())

it('cambia solo la foto con el control manual', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter initialEntries={['/']}><AppRouter /></MemoryRouter>)
  expect(screen.getByRole('heading', { name: /Construí con confianza/ })).toBeInTheDocument()
  expect(screen.getByAltText('Campaña uno')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Foto siguiente' }))
  expect(screen.getByAltText('Campaña dos')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: /Construí con confianza/ })).toBeInTheDocument()
})

it('avanza automáticamente cuando hay fotos', () => {
  vi.useFakeTimers()
  render(<MemoryRouter initialEntries={['/']}><AppRouter /></MemoryRouter>)
  act(() => vi.advanceTimersByTime(6000))
  expect(screen.getByAltText('Campaña dos')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: /Construí con confianza/ })).toBeInTheDocument()
})
