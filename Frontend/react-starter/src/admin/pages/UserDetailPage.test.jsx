import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { deactivateUser, getUser, reactivateUser, updateUserEmail } from '../services/adminUserService.js'
import { UserDetailPage } from './UserDetailPage.jsx'

vi.mock('../services/adminUserService.js', () => ({ getUser: vi.fn(), updateUserEmail: vi.fn(), deactivateUser: vi.fn(), reactivateUser: vi.fn() }))
const endSession = vi.fn()
let actor = { id: 1, role: 'SUPER_ADMIN' }
vi.mock('../../auth/context/useAuth.js', () => ({ useAuth: () => ({ user: actor, endSession }) }))

function renderDetail(id = '7') {
  render(<MemoryRouter initialEntries={[`/app/admin/users/${id}`]}><Routes><Route path="/app/admin/users/:id" element={<UserDetailPage />} /><Route path="/login" element={<p>Ingreso</p>} /></Routes></MemoryRouter>)
}

function SwitchUser() {
  const navigate = useNavigate()
  return <button onClick={() => navigate('/app/admin/users/8')} type="button">Elegir otro</button>
}

describe('UserDetailPage', () => {
  beforeEach(() => { vi.resetAllMocks(); actor = { id: 1, role: 'SUPER_ADMIN' } })

  it('muestra carga y datos seguros con estado MFA', async () => {
    getUser.mockResolvedValue({ id: 7, email: 'one@example.com', role: 'ADMIN', enabled: true, mfaEnabled: true, createdAt: '2026-09-26T10:00:00', updatedAt: null })
    renderDetail()
    expect(screen.getByRole('status')).toHaveTextContent('Cargando usuario')
    expect(await screen.findByText('one@example.com')).toBeInTheDocument()
    expect(screen.getByText('Activado')).toBeInTheDocument()
    expect(screen.getByText('2026-09-26T10:00:00')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Volver a usuarios/ })).toHaveAttribute('href', '/app/admin/users')
  })

  it('distingue 404 de otros errores', async () => {
    getUser.mockRejectedValue({ status: 404, message: 'No encontrado' })
    renderDetail()
    expect(await screen.findByRole('alert')).toHaveTextContent('El usuario no existe.')
  })

  it('muestra el error de lectura', async () => {
    getUser.mockRejectedValue(new Error('Servidor no disponible'))
    renderDetail()
    expect(await screen.findByRole('alert')).toHaveTextContent('Servidor no disponible')
  })

  it('descarta el detalle anterior cuando cambia la selección', async () => {
    let finishOld
    getUser.mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve }))
      .mockResolvedValueOnce({ id: 8, email: 'new@example.com', role: 'USER', enabled: true, mfaEnabled: false })
    render(<MemoryRouter initialEntries={['/app/admin/users/7']}><SwitchUser /><Routes><Route path="/app/admin/users/:id" element={<UserDetailPage />} /></Routes></MemoryRouter>)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Elegir otro' }))
    expect(await screen.findByText('new@example.com')).toBeInTheDocument()
    await act(async () => finishOld({ id: 7, email: 'old@example.com' }))
    expect(screen.queryByText('old@example.com')).not.toBeInTheDocument()
  })

  it('confirma el correo con un solo PATCH y muestra el estado devuelto', async () => {
    getUser.mockResolvedValue({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: false })
    updateUserEmail.mockResolvedValue({ id: 7, email: 'new@example.com', enabled: true, mfaEnabled: false })
    renderDetail()
    const user = userEvent.setup()
    await user.clear(await screen.findByLabelText('Nuevo email'))
    await user.type(screen.getByLabelText('Nuevo email'), 'NEW@example.com')
    await user.click(screen.getByRole('button', { name: 'Guardar email' }))
    expect(updateUserEmail).toHaveBeenCalledExactlyOnceWith('7', 'new@example.com')
    expect(await screen.findByRole('status')).toHaveTextContent('Cambio confirmado')
    expect(screen.getByText('new@example.com')).toBeInTheDocument()
  })

  it('no repite la baja cuando la respuesta es incierta y consulta el detalle', async () => {
    getUser.mockResolvedValueOnce({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: true })
      .mockResolvedValueOnce({ id: 7, email: 'old@example.com', enabled: false, mfaEnabled: true })
    deactivateUser.mockRejectedValue({ status: 0 })
    renderDetail()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Desactivar cuenta' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar baja' }))
    expect(await screen.findByRole('status')).toHaveTextContent('No se pudo confirmar')
    expect(deactivateUser).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Guardar email' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Consultar estado actual' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Esta consulta no confirma')
    expect(await screen.findByRole('button', { name: 'Reactivar cuenta' })).toBeInTheDocument()
    expect(deactivateUser).toHaveBeenCalledTimes(1)
  })

  it('no ofrece baja propia y conserva baja ajena con MFA', async () => {
    actor = { id: 7, role: 'SUPER_ADMIN' }
    getUser.mockResolvedValue({ id: 7, email: 'self@example.com', enabled: true, mfaEnabled: true })
    renderDetail()
    expect(await screen.findByText('No podés desactivar tu propia cuenta.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Desactivar cuenta' })).not.toBeInTheDocument()
    expect(screen.getByText(/titular debe desactivar MFA/)).toBeInTheDocument()
  })

  it('muestra conflicto específico sin confirmar éxito', async () => {
    getUser.mockResolvedValue({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: false })
    updateUserEmail.mockRejectedValue({ status: 409, code: 'ADMIN_EMAIL_IN_USE' })
    renderDetail()
    await screen.findByLabelText('Nuevo email')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Guardar email' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Ese email ya está en uso')
    expect(screen.queryByText('Cambio confirmado por el servidor.')).not.toBeInTheDocument()
  })

  it.each([400, 401, 403, 404, 429])('muestra rechazo %s sin éxito ni reintento', async (status) => {
    getUser.mockResolvedValue({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: false })
    updateUserEmail.mockRejectedValue(Object.assign(new Error(`Rechazo ${status}`), { status }))
    renderDetail()
    await screen.findByLabelText('Nuevo email')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Guardar email' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(`Rechazo ${status}`)
    expect(updateUserEmail).toHaveBeenCalledTimes(1)
    expect(screen.queryByText('Cambio confirmado por el servidor.')).not.toBeInTheDocument()
  })

  it('trata 5xx como incierto y bloquea otra mutación hasta consultar', async () => {
    getUser.mockResolvedValue({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: false })
    updateUserEmail.mockRejectedValue({ status: 503 })
    renderDetail()
    await screen.findByLabelText('Nuevo email')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Guardar email' }))
    expect(await screen.findByRole('status')).toHaveTextContent('No se pudo confirmar')
    expect(screen.getByRole('button', { name: 'Guardar email' })).toBeDisabled()
    expect(updateUserEmail).toHaveBeenCalledTimes(1)
  })

  it('cierra sesión solo tras confirmar un cambio de email propio', async () => {
    actor = { id: 7, role: 'SUPER_ADMIN' }
    getUser.mockResolvedValue({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: false })
    updateUserEmail.mockResolvedValue({ id: 7, email: 'new@example.com', enabled: true, mfaEnabled: false })
    renderDetail()
    const user = userEvent.setup()
    await user.clear(await screen.findByLabelText('Nuevo email'))
    await user.type(screen.getByLabelText('Nuevo email'), 'new@example.com')
    await user.click(screen.getByRole('button', { name: 'Guardar email' }))
    expect(endSession).toHaveBeenCalledWith('admin-email-changed')
  })

  it('trata email idéntico como no-op y conserva la sesión propia', async () => {
    actor = { id: 7, role: 'SUPER_ADMIN' }
    getUser.mockResolvedValue({ id: 7, email: 'same@example.com', enabled: true, mfaEnabled: true })
    updateUserEmail.mockResolvedValue({ id: 7, email: 'same@example.com', enabled: true, mfaEnabled: true })
    renderDetail()
    await screen.findByLabelText('Nuevo email')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Guardar email' }))
    expect(await screen.findByRole('status')).toHaveTextContent('No hubo cambios')
    expect(endSession).not.toHaveBeenCalled()
  })

  it('pide nuevo ingreso sin afirmar éxito cuando la consulta propia termina en 401', async () => {
    actor = { id: 7, role: 'SUPER_ADMIN' }
    getUser.mockResolvedValueOnce({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: false })
      .mockRejectedValueOnce({ status: 401 })
    updateUserEmail.mockRejectedValue({ status: 0 })
    renderDetail()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Guardar email' }))
    await user.click(await screen.findByRole('button', { name: 'Consultar estado actual' }))
    expect(endSession).toHaveBeenCalledWith('admin-email-uncertain')
    expect(updateUserEmail).toHaveBeenCalledTimes(1)
  })

  it('confirma reactivación sin afirmar que MFA fue eliminado', async () => {
    getUser.mockResolvedValue({ id: 7, email: 'old@example.com', enabled: false, mfaEnabled: true })
    reactivateUser.mockResolvedValue({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: true })
    renderDetail()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Reactivar cuenta' }))
    expect(screen.getByText(/conserva MFA/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirmar reactivación' }))
    expect(reactivateUser).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole('status')).toHaveTextContent('Cambio confirmado')
  })

  it('bloquea doble confirmación mientras la baja sigue pendiente', async () => {
    let finish
    getUser.mockResolvedValue({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: false })
    deactivateUser.mockReturnValue(new Promise((resolve) => { finish = resolve }))
    renderDetail()
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Desactivar cuenta' }))
    const confirm = screen.getByRole('button', { name: 'Confirmar baja' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    expect(deactivateUser).toHaveBeenCalledTimes(1)
    await act(async () => finish({ id: 7, email: 'old@example.com', enabled: false, mfaEnabled: false }))
  })

  it('descarta respuesta de mutación al abandonar el detalle', async () => {
    let finish
    getUser.mockResolvedValue({ id: 7, email: 'old@example.com', enabled: true, mfaEnabled: false })
    updateUserEmail.mockReturnValue(new Promise((resolve) => { finish = resolve }))
    const { unmount } = render(<MemoryRouter initialEntries={['/app/admin/users/7']}><Routes><Route path="/app/admin/users/:id" element={<UserDetailPage />} /></Routes></MemoryRouter>)
    await screen.findByLabelText('Nuevo email')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Guardar email' }))
    unmount()
    await act(async () => finish({ id: 7, email: 'changed@example.com', enabled: true }))
    expect(endSession).not.toHaveBeenCalled()
  })
})
