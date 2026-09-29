import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listUsers } from '../services/adminUserService.js'
import { UserListPage } from './UserListPage.jsx'

vi.mock('../services/adminUserService.js', () => ({ listUsers: vi.fn() }))

const makePage = (page, content = []) => ({ content, page, size: 20, totalElements: 21, totalPages: 2 })
const account = { id: 4, email: 'one@example.com', role: 'USER', enabled: false }

function ChangePage() {
  const navigate = useNavigate()
  return <button onClick={() => navigate('/app/admin/users?page=1')} type="button">Ir a página 2</button>
}

function renderPage(path = '/app/admin/users', withNavigation = false) {
  render(<MemoryRouter initialEntries={[path]}>{withNavigation && <ChangePage />}<Routes><Route path="/app/admin/users" element={<UserListPage />} /><Route path="/app/admin/users/:id" element={<p>Detalle elegido</p>} /></Routes></MemoryRouter>)
}

describe('UserListPage', () => {
  beforeEach(() => vi.resetAllMocks())

  it('muestra carga, cuentas activas/inactivas y paginación', async () => {
    listUsers.mockResolvedValueOnce(makePage(0, [account])).mockResolvedValueOnce(makePage(1))
    renderPage()
    expect(screen.getByRole('status')).toHaveTextContent('Cargando usuarios')
    expect(await screen.findByRole('link', { name: /one@example.com/ })).toHaveAttribute('href', '/app/admin/users/4')
    expect(screen.getByText(/Inactiva/)).toBeInTheDocument()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(listUsers).toHaveBeenLastCalledWith(1, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(await screen.findByText('No hay usuarios en esta página.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
  })

  it('muestra error sin datos anteriores', async () => {
    listUsers.mockRejectedValue(new Error('Servidor no disponible'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Servidor no disponible')
    expect(screen.queryByText(account.email)).not.toBeInTheDocument()
  })

  it('descarta una respuesta tardía de otra página', async () => {
    let finishOld
    listUsers.mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve }))
      .mockResolvedValueOnce(makePage(1, [{ ...account, id: 8, email: 'new@example.com' }]))
    renderPage('/app/admin/users?page=0', true)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Ir a página 2' }))
    expect(await screen.findByRole('link', { name: /new@example.com/ })).toBeInTheDocument()
    await act(async () => finishOld(makePage(0, [account])))
    expect(screen.getByRole('link', { name: /new@example.com/ })).toBeInTheDocument()
    expect(screen.queryByText(account.email)).not.toBeInTheDocument()
  })

  it('abre el detalle al pulsar los datos de la tarjeta', async () => {
    listUsers.mockResolvedValue(makePage(0, [account]))
    renderPage()
    await userEvent.setup().click(await screen.findByText(/ID 4 · USER · Inactiva/))
    expect(screen.getByText('Detalle elegido')).toBeInTheDocument()
  })
})
