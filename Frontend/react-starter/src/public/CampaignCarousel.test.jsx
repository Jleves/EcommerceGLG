import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { AppRouter } from '../app/AppRouter.jsx'
import { getPublicCatalog } from './publicService.js'

vi.mock('./publicService.js', () => ({ getPublicCatalog: vi.fn() }))
vi.mock('./publicConfig.js', () => ({ campaignPhotos: [
  { src: 'data:image/png;base64,one', alt: 'Campaña uno', caption: 'Estamos en General Roca', portrait: true },
  { src: 'data:image/png;base64,two', alt: 'Campaña dos' },
  { src: 'data:image/png;base64,three', alt: 'Campaña tres' },
], brandLogos: [], whatsappNumber: '' }))

beforeEach(() => getPublicCatalog.mockResolvedValue({ categories: [], facets: [] }))
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it('funde las fotos con el control manual y retira la anterior', async () => {
  const user = userEvent.setup()
  const { container } = render(<MemoryRouter initialEntries={['/']}><AppRouter /></MemoryRouter>)
  expect(screen.getByRole('heading', { name: 'Los materiales de tu próxima historia.' })).toBeInTheDocument()
  expect(screen.getByAltText('Campaña uno')).toBeInTheDocument()
  expect(screen.getByText('Estamos en General Roca')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Foto siguiente' }))
  expect(screen.getByAltText('Campaña dos')).toBeInTheDocument()
  expect(container.querySelectorAll('.campaign-layer')).toHaveLength(2)
  await waitFor(() => expect(screen.queryByText('Estamos en General Roca')).not.toBeInTheDocument())
  expect(screen.getByRole('heading', { name: 'Los materiales de tu próxima historia.' })).toBeInTheDocument()
})

it('avanza automáticamente cuando hay fotos', () => {
  vi.useFakeTimers()
  render(<MemoryRouter initialEntries={['/']}><AppRouter /></MemoryRouter>)
  act(() => vi.advanceTimersByTime(6000))
  expect(screen.getByAltText('Campaña dos')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Los materiales de tu próxima historia.' })).toBeInTheDocument()
})

it('cambia sin fundido cuando se prefiere movimiento reducido', async () => {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
  const user = userEvent.setup()
  const { container } = render(<MemoryRouter initialEntries={['/']}><AppRouter /></MemoryRouter>)
  await user.click(screen.getByRole('button', { name: 'Foto siguiente' }))
  expect(screen.getByAltText('Campaña dos')).toBeInTheDocument()
  expect(container.querySelectorAll('.campaign-layer')).toHaveLength(1)
  expect(screen.queryByText('Estamos en General Roca')).not.toBeInTheDocument()
})
