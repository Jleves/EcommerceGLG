import { StrictMode } from 'react'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider.jsx'
import { AppRouter } from '../../app/AppRouter.jsx'
import * as auth from '../services/authService.js'
import * as settings from '../services/mfaSettingsService.js'

vi.mock('../services/authService.js', () => ({ getCurrentUser: vi.fn(), changePassword: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../services/mfaSettingsService.js', () => ({ getStatus: vi.fn(), start: vi.fn(), confirm: vi.fn(), resend: vi.fn(), cancel: vi.fn() }))
vi.mock('../../services/apiClient.js', () => ({ ensureCsrfToken: vi.fn().mockResolvedValue('csrf') }))
const status = (enabled = false) => ({ enabled, enabledAt: enabled ? '2026-09-01T00:00:00Z' : null, maskedEmail: 'u***@example.com' })
const timing = () => ({ expiresAt: new Date(Date.now() + 300000).toISOString(), resendAvailableAt: new Date(Date.now() - 1000).toISOString() })
const passwordLabel = 'Contraseña actual para verificación en dos pasos'
const codeLabel = 'Código para cambiar la verificación en dos pasos'
function Navigation() {
  const navigate = useNavigate()
  return <><button onClick={() => navigate('/forgot-password')}>Salir del perfil</button><button onClick={() => navigate('/app/profile')}>Abrir perfil</button></>
}
function mount(strict = false) {
  const app = <MemoryRouter initialEntries={['/app/profile']}><AuthProvider><Navigation /><AppRouter /></AuthProvider></MemoryRouter>
  return render(strict ? <StrictMode>{app}</StrictMode> : app)
}
async function begin(enabled = false) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText(passwordLabel), 'secure-password')
  await user.click(screen.getByRole('button', { name: enabled ? 'Desactivar verificación en dos pasos' : 'Activar verificación en dos pasos' }))
  return user
}
async function enterCode(user, enabled = false) {
  await user.click(await screen.findByLabelText(codeLabel))
  await user.paste('001234')
  expect(screen.getByLabelText(codeLabel)).toHaveValue('001234')
  await user.click(screen.getByRole('button', { name: enabled ? 'Confirmar desactivación' : 'Confirmar activación' }))
}
beforeEach(() => {
  vi.resetAllMocks()
  auth.getCurrentUser.mockResolvedValue({ id: 1, email: 'user@example.com', role: 'USER' })
  settings.getStatus.mockResolvedValue(status())
  settings.start.mockImplementation(async () => timing())
  settings.resend.mockImplementation(async () => timing())
  settings.confirm.mockResolvedValue(null)
  settings.cancel.mockResolvedValue(null)
})
it.each(['USER', 'ADMIN', 'SUPER_ADMIN'].flatMap((role) => [false, true].map((enabled) => [role, enabled])))(
  'completa configuración propia para %s, estado inicial %s, y vuelve al login sin logout', async (role, enabled) => {
    auth.getCurrentUser.mockResolvedValue({ id: 1, email: 'user@example.com', role })
    settings.getStatus.mockResolvedValue(status(enabled))
    mount()
    const user = await begin(enabled)
    expect(settings.start).toHaveBeenCalledWith(!enabled, 'secure-password')
    expect(screen.getByText(enabled ? 'Activada' : 'Desactivada', { exact: true })).toBeInTheDocument()
    expect(screen.getByText(/Sus accesos actuales pueden seguir vigentes/)).toBeInTheDocument()
    expect(screen.queryByLabelText(passwordLabel)).not.toBeInTheDocument()
    await enterCode(user, enabled)
    expect(await screen.findByText(enabled ? /Verificación en dos pasos desactivada/ : /Verificación en dos pasos activada/)).toBeInTheDocument()
    expect(screen.getByLabelText('Contraseña')).toHaveValue('')
    expect(settings.confirm).toHaveBeenCalledWith(!enabled, '001234')
    expect(auth.logout).not.toHaveBeenCalled()
    expect(settings.cancel).not.toHaveBeenCalled()
  })
it('montar en StrictMode solo consulta estado y conserva cambio de contraseña', async () => {
  mount(true)
  await screen.findByLabelText(passwordLabel)
  expect(screen.getByLabelText('Contraseña actual')).toBeInTheDocument()
  expect(settings.start).not.toHaveBeenCalled()
  expect(settings.resend).not.toHaveBeenCalled()
  expect(settings.cancel).not.toHaveBeenCalled()
})
it.each([false, true])('cancelación no cambia estado y limpia los campos, falla remota: %s', async (fails) => {
  if (fails) settings.cancel.mockRejectedValue({ status: 0 })
  mount()
  const user = await begin()
  await user.type(await screen.findByLabelText(codeLabel), '123')
  await user.click(screen.getByRole('button', { name: 'Cancelar cambio de verificación' }))
  expect(await screen.findByLabelText(passwordLabel)).toHaveValue('')
  expect(screen.getByText('Desactivada', { exact: true })).toBeInTheDocument()
  expect(settings.confirm).not.toHaveBeenCalled()
  if (fails) expect(await screen.findByRole('alert')).toHaveTextContent('no se pudo confirmar la cancelación')
})
it('muestra errores de contraseña sin conservar el secreto ni enviar otro correo', async () => {
  settings.start.mockRejectedValue({ status: 400, message: 'Solicitud rechazada', fieldErrors: [{ field: 'currentPassword', message: 'Contraseña incorrecta' }] })
  mount()
  await begin()
  expect(await screen.findByText('Contraseña incorrecta')).toBeInTheDocument()
  expect(screen.getByLabelText(new RegExp(passwordLabel))).toHaveValue('')
  expect(settings.start).toHaveBeenCalledTimes(1)
})
it('un 401 en consulta inicial solicita login nuevo', async () => {
  settings.getStatus.mockRejectedValue({ status: 401 })
  mount()
  expect(await screen.findByText(/Tu sesión ya no permite cambiar/)).toBeInTheDocument()
  expect(settings.start).not.toHaveBeenCalled()
})
it('un 401 en confirmación no muestra éxito', async () => {
  settings.confirm.mockRejectedValue({ status: 401 })
  mount()
  await enterCode(await begin())
  expect(await screen.findByText(/Tu sesión ya no permite cambiar/)).toBeInTheDocument()
  expect(screen.queryByText(/Verificación en dos pasos activada/)).not.toBeInTheDocument()
})
it('429 bloquea operaciones por Retry-After pero permite cancelar', async () => {
  settings.confirm.mockRejectedValue({ status: 429, retryAfter: 60, message: 'Esperá' })
  mount()
  const user = await begin()
  await enterCode(user)
  expect(await screen.findByText(/Podés volver a intentar en/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Confirmar activación' })).toBeDisabled()
  expect(screen.getByRole('button', { name: /Reenviar en/ })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Cancelar cambio de verificación' })).toBeEnabled()
})
it('fallo SMTP inicial permite comenzar otra vez sin cambiar la opción', async () => {
  settings.start.mockRejectedValueOnce({ status: 503, message: 'Correo no disponible' })
  mount()
  await begin()
  expect(await screen.findByRole('alert')).toHaveTextContent('Correo no disponible')
  expect(screen.getByText('Desactivada', { exact: true })).toBeInTheDocument()
  await begin()
  expect(await screen.findByLabelText(codeLabel)).toBeInTheDocument()
})
it('fallo SMTP de reenvío conserva el desafío y permite reenvío manual', async () => {
  settings.resend.mockRejectedValueOnce({ status: 503, message: 'Correo no disponible' })
    .mockResolvedValueOnce({ ...timing(), resendAvailableAt: new Date(Date.now() + 60000).toISOString() })
  mount()
  const user = await begin()
  await user.click(await screen.findByRole('button', { name: 'Reenviar código de configuración' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Correo no disponible')
  await user.click(screen.getByRole('button', { name: 'Reenviar código de configuración' }))
  expect(await screen.findByText(/Enviamos un código nuevo/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /Reenviar en/ })).toBeDisabled()
  expect(screen.getByText('Desactivada', { exact: true })).toBeInTheDocument()
})
it('400 de código conserva el paso y 409 consulta estado sin repetir confirmación', async () => {
  settings.confirm.mockRejectedValueOnce({ status: 400, code: 'MFA_CODE_INVALID', message: 'Código incorrecto' })
    .mockRejectedValueOnce({ status: 409, code: 'MFA_STATE_CONFLICT' })
  mount()
  const user = await begin()
  await enterCode(user)
  expect(await screen.findByRole('alert')).toHaveTextContent('Código incorrecto')
  expect(screen.getByLabelText(codeLabel)).toHaveValue('')
  settings.getStatus.mockResolvedValue(status(true))
  await enterCode(user)
  expect(await screen.findByText(/La configuración cambió/)).toBeInTheDocument()
  expect(screen.getByText('Activada', { exact: true })).toBeInTheDocument()
  expect(settings.confirm).toHaveBeenCalledTimes(2)
})
it.each([0, 500])('confirmación incierta %s consulta estado y nunca simula éxito', async (failureStatus) => {
  settings.confirm.mockRejectedValue({ status: failureStatus })
  mount()
  await enterCode(await begin())
  expect(await screen.findByText(/Consultamos el estado actual/)).toBeInTheDocument()
  expect(screen.getByLabelText(passwordLabel)).toHaveValue('')
  expect(settings.confirm).toHaveBeenCalledTimes(1)
  expect(settings.getStatus).toHaveBeenCalledTimes(2)
  expect(auth.logout).not.toHaveBeenCalled()
})
it('respuesta perdida y consulta 401 vuelve a login con resultado incierto', async () => {
  settings.confirm.mockRejectedValue({ status: 0 })
  mount()
  const user = await begin()
  settings.getStatus.mockRejectedValue({ status: 401 })
  await enterCode(user)
  expect(await screen.findByText(/No pudimos confirmar si cambió/)).toBeInTheDocument()
  expect(settings.confirm).toHaveBeenCalledTimes(1)
})
it('consulta fallida tras respuesta perdida bloquea cambios hasta consultar de nuevo', async () => {
  settings.confirm.mockRejectedValue({ status: 0 })
  mount()
  const user = await begin()
  settings.getStatus.mockRejectedValue({ status: 0, message: 'Sin conexión' })
  await enterCode(user)
  expect(await screen.findByRole('button', { name: 'Consultar estado MFA' })).toBeInTheDocument()
  expect(screen.queryByLabelText(codeLabel)).not.toBeInTheDocument()
  expect(screen.queryByLabelText(passwordLabel)).not.toBeInTheDocument()
  settings.getStatus.mockResolvedValue(status())
  await user.click(screen.getByRole('button', { name: 'Consultar estado MFA' }))
  expect(await screen.findByLabelText(passwordLabel)).toHaveValue('')
  expect(settings.confirm).toHaveBeenCalledTimes(1)
})
it('evita doble confirmación y no navega antes de su respuesta', async () => {
  let resolve
  settings.confirm.mockImplementation(() => new Promise((done) => { resolve = done }))
  mount()
  const user = await begin()
  await enterCode(user)
  fireEvent.submit(screen.getByLabelText(codeLabel).closest('form'))
  expect(settings.confirm).toHaveBeenCalledTimes(1)
  expect(screen.queryByLabelText('Email')).not.toBeInTheDocument()
  await act(async () => resolve(null))
  expect(await screen.findByLabelText('Email')).toBeInTheDocument()
})
it('vencimiento impide confirmar/reenvío y permite cancelar', async () => {
  settings.start.mockResolvedValue({ ...timing(), expiresAt: new Date(Date.now() - 1000).toISOString() })
  mount()
  await begin()
  expect(await screen.findByText(/El código venció/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Confirmar activación' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Reenviar código de configuración' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Cancelar cambio de verificación' })).toBeEnabled()
})
it('abandonar limpia campos y descarta respuestas tardías', async () => {
  let resolve
  settings.start.mockImplementation(() => new Promise((done) => { resolve = done }))
  mount()
  const user = await begin()
  await user.click(screen.getByText('Salir del perfil'))
  await act(async () => resolve(timing()))
  await user.click(screen.getByText('Abrir perfil'))
  expect(await screen.findByLabelText(passwordLabel)).toHaveValue('')
  expect(screen.queryByLabelText(codeLabel)).not.toBeInTheDocument()
  expect(settings.start).toHaveBeenCalledTimes(1)
  await waitFor(() => expect(settings.getStatus).toHaveBeenCalledTimes(2))
})
