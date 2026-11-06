import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { SubscriptionListPage } from './SubscriptionListPage.jsx'
import { exportSubscriptions, listSubscriptions } from '../services/adminSubscriptionService.js'

vi.mock('../services/adminSubscriptionService.js', () => ({
  listSubscriptions: vi.fn(), exportSubscriptions: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
  listSubscriptions.mockResolvedValue({ content: [
    { id: 1, contact: 'active@example.com', consentedAt: '2026-01-01T12:00:00Z', withdrawnAt: null, active: true },
    { id: 2, contact: 'gone@example.com', consentedAt: '2026-01-01T12:00:00Z', withdrawnAt: '2026-02-01T12:00:00Z', active: false },
  ], totalElements: 22, totalPages: 2 })
  exportSubscriptions.mockResolvedValue(new Blob(['xlsx']))
})

function renderPage(path = '/email') {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/email" element={<SubscriptionListPage channel="EMAIL" />} />
    <Route path="/whatsapp" element={<SubscriptionListPage channel="WHATSAPP" />} />
  </Routes></MemoryRouter>)
}

it('muestra contactos, bajas y cambia de página', async () => {
  const user = userEvent.setup()
  renderPage()
  const table = await screen.findByRole('table')
  expect(within(table).getByText('active@example.com')).toBeInTheDocument()
  expect(within(table).getByText('gone@example.com')).toBeInTheDocument()
  expect(within(table).getByRole('cell', { name: 'Baja' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Siguiente' }))
  expect(listSubscriptions).toHaveBeenLastCalledWith('EMAIL', 1, expect.anything())
  expect(await screen.findByText(/Página 2 de 2/)).toBeInTheDocument()
})

it('descarga el canal completo y muestra errores de descarga', async () => {
  const user = userEvent.setup()
  const createObjectURL = vi.fn().mockReturnValue('blob:test')
  const revokeObjectURL = vi.fn()
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }))
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  renderPage('/whatsapp')
  await screen.findByRole('table')
  await user.click(screen.getByRole('button', { name: 'Descargar Excel' }))
  expect(exportSubscriptions).toHaveBeenCalledWith('WHATSAPP')
  expect(createObjectURL).toHaveBeenCalledOnce()
  expect(click).toHaveBeenCalledOnce()
  exportSubscriptions.mockRejectedValueOnce(new Error('Descarga fallida'))
  await user.click(screen.getByRole('button', { name: 'Descargar Excel' }))
  expect(await screen.findByText('Descarga fallida')).toBeInTheDocument()
  click.mockRestore()
})

it('muestra el vacío y el error de consulta', async () => {
  listSubscriptions.mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0 })
  const rendered = renderPage()
  expect(await screen.findByText('No hay suscriptores en esta página.')).toBeInTheDocument()
  rendered.unmount()
  listSubscriptions.mockRejectedValueOnce(new Error('Consulta fallida'))
  renderPage()
  expect(await screen.findByText('Consulta fallida')).toBeInTheDocument()
})
