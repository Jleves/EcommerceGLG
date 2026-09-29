import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider.jsx'
import { useAuth } from './useAuth.js'
import { AppRouter } from '../../app/AppRouter.jsx'
import * as service from '../services/authService.js'

vi.mock('../services/authService.js', () => ({
  getCurrentUser: vi.fn(), changePassword: vi.fn(), login: vi.fn(), logout: vi.fn(),
  verifyMfa: vi.fn(), resendMfa: vi.fn(), cancelMfa: vi.fn(),
}))
vi.mock('../../services/apiClient.js', () => ({ ensureCsrfToken: vi.fn().mockResolvedValue('csrf') }))
vi.mock('../services/mfaSettingsService.js', () => ({ getStatus: () => Promise.resolve({ enabled: false, maskedEmail: 'u***@example.com' }) }))
const currentUser = { id: 1, email: 'user@example.com', role: 'USER' }
const authenticated = { status: 'AUTHENTICATED', user: currentUser }
const pending = () => ({ status: 'MFA_REQUIRED', maskedEmail: 'u***@example.com',
  expiresAt: new Date(Date.now() + 300000).toISOString(), resendAvailableAt: new Date(Date.now() - 1000).toISOString() })

function Probe() {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  return <><output data-testid="status">{auth.status}</output>
    <output data-testid="location">{location.pathname}{location.search}</output>
    <button onClick={() => navigate('/app/profile')}>Ir privado</button>
    <button onClick={() => navigate('/forgot-password')}>Abandonar</button></>
}
function mount(path = '/login') {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><Probe /><AppRouter /></AuthProvider></MemoryRouter>)
}
async function start() {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Email'), 'user@example.com')
  await user.type(screen.getByLabelText('Contraseña'), 'password')
  await user.click(screen.getByRole('button', { name: 'Ingresar' }))
  await screen.findByLabelText('Código de verificación')
  return user
}
async function verify(user) {
  await user.click(screen.getByLabelText('Código de verificación'))
  await user.paste('001234')
  expect(screen.getByLabelText('Código de verificación')).toHaveValue('001234')
  await user.click(screen.getByRole('button', { name: 'Confirmar código' }))
}
beforeEach(() => {
  vi.resetAllMocks()
  service.getCurrentUser.mockRejectedValue({ status: 401 })
  service.login.mockResolvedValue(pending())
  service.verifyMfa.mockResolvedValue(authenticated)
  service.cancelMfa.mockResolvedValue(null)
})
it('202 bloquea rutas privadas; acepta pegado con ceros y autentica solo tras confirmar', async () => {
  mount('/app/profile?tab=security')
  const user = await start()
  expect(screen.getByTestId('status')).toHaveTextContent('mfa-required')
  await user.click(screen.getByText('Ir privado'))
  expect(screen.getByLabelText('Código de verificación')).toBeInTheDocument()
  await verify(user)
  await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
  expect(service.verifyMfa).toHaveBeenCalledWith('001234')
  expect(service.cancelMfa).not.toHaveBeenCalled()
  expect(screen.getByTestId('location')).toHaveTextContent('/app/profile?tab=security')
})
it('200 ingresa sin pedir código', async () => {
  service.login.mockResolvedValue(authenticated)
  mount()
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Email'), 'user@example.com')
  await user.type(screen.getByLabelText('Contraseña'), 'password')
  await user.click(screen.getByRole('button', { name: 'Ingresar' }))
  await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
  expect(service.verifyMfa).not.toHaveBeenCalled()
})
it('recarga o acceso directo sin estado vuelve al login', async () => {
  mount('/login/mfa')
  expect(await screen.findByLabelText('Email')).toBeInTheDocument()
  expect(service.resendMfa).not.toHaveBeenCalled()
})
it('reenvía, actualiza la espera y respeta Retry-After', async () => {
  service.resendMfa.mockRejectedValueOnce({ status: 429, retryAfter: 60, message: 'Esperá' })
  mount()
  const user = await start()
  await user.click(screen.getByRole('button', { name: 'Reenviar código' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Esperá')
  expect(screen.getByRole('button', { name: /Reenviar en/ })).toBeDisabled()
})
it('un reenvío exitoso actualiza tiempos y limpia el código', async () => {
  service.resendMfa.mockResolvedValue({ ...pending(), resendAvailableAt: new Date(Date.now() + 60000).toISOString() })
  mount()
  const user = await start()
  await user.type(screen.getByLabelText('Código de verificación'), '123')
  await user.click(screen.getByRole('button', { name: 'Reenviar código' }))
  expect(await screen.findByText(/Enviamos un código nuevo/)).toBeInTheDocument()
  expect(screen.getByLabelText('Código de verificación')).toHaveValue('')
  expect(screen.getByRole('button', { name: /Reenviar en/ })).toBeDisabled()
})
it.each([400, 503])('error de reenvío %s conserva el desafío', async (status) => {
  service.resendMfa.mockRejectedValue({ status, message: 'No se pudo reenviar' })
  mount()
  const user = await start()
  await user.click(screen.getByRole('button', { name: 'Reenviar código' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo reenviar')
  expect(screen.getByTestId('status')).toHaveTextContent('mfa-required')
})
it('código incorrecto no autentica y desafío inválido exige reiniciar', async () => {
  service.verifyMfa.mockRejectedValueOnce({ status: 400, code: 'MFA_CODE_INVALID', message: 'Código incorrecto' })
    .mockRejectedValueOnce({ status: 400, code: 'MFA_CHALLENGE_INVALID', message: 'Desafío inválido' })
  mount()
  const user = await start()
  await verify(user)
  expect(await screen.findByRole('alert')).toHaveTextContent('Código incorrecto')
  await verify(user)
  expect(await screen.findByText(/El código ya no está disponible/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Confirmar código' })).toBeDisabled()
})
it.each([false, true])('cancela y limpia aunque falle la cancelación: %s', async (fails) => {
  if (fails) service.cancelMfa.mockRejectedValue(new Error('red'))
  mount()
  const user = await start()
  await user.click(screen.getByRole('button', { name: 'Cancelar y volver al login' }))
  expect(await screen.findByLabelText('Contraseña')).toHaveValue('')
  expect(service.cancelMfa).toHaveBeenCalledTimes(1)
  if (fails) expect(await screen.findByText(/No se pudo confirmar la cancelación/)).toBeInTheDocument()
})
it('respuesta perdida consulta me sin refresh y no repite código', async () => {
  service.verifyMfa.mockRejectedValue({ status: 0 })
  mount()
  const user = await start()
  service.getCurrentUser.mockResolvedValue(currentUser)
  await verify(user)
  await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
  expect(service.getCurrentUser).toHaveBeenLastCalledWith({ retryAuth: false })
  expect(service.verifyMfa).toHaveBeenCalledTimes(1)
})
it('si tampoco se puede consultar la sesión, solo permite consultar de nuevo', async () => {
  service.verifyMfa.mockRejectedValue({ status: 0 })
  mount()
  const user = await start()
  service.getCurrentUser.mockRejectedValue({ status: 0, message: 'Sin conexión' })
  await verify(user)
  expect(await screen.findByRole('button', { name: 'Consultar sesión' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Confirmar código' })).not.toBeInTheDocument()
  service.getCurrentUser.mockResolvedValue(currentUser)
  await user.click(screen.getByRole('button', { name: 'Consultar sesión' }))
  await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
  expect(service.verifyMfa).toHaveBeenCalledTimes(1)
})
it('consulta 401 después de confirmación incierta reinicia login', async () => {
  service.verifyMfa.mockRejectedValue({ status: 0 })
  mount()
  await verify(await start())
  expect(await screen.findByLabelText('Email')).toBeInTheDocument()
  expect(screen.getByText(/No se confirmó una sesión/)).toBeInTheDocument()
})
it('abandono descarta una confirmación tardía', async () => {
  let resolve
  service.verifyMfa.mockImplementation(() => new Promise((done) => { resolve = done }))
  mount()
  const user = await start()
  await verify(user)
  await user.click(screen.getByText('Abandonar'))
  await waitFor(() => expect(service.cancelMfa).toHaveBeenCalledTimes(1))
  await act(async () => resolve(authenticated))
  expect(screen.getByTestId('status')).toHaveTextContent('anonymous')
})
it('desafío vencido impide confirmar y reenviar', async () => {
  service.login.mockResolvedValue({ ...pending(), expiresAt: new Date(Date.now() - 1000).toISOString() })
  mount()
  await start()
  expect(screen.getByRole('button', { name: 'Confirmar código' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Reenviar código' })).toBeDisabled()
  fireEvent.change(screen.getByLabelText('Código de verificación'), { target: { value: '123456' } })
  expect(service.verifyMfa).not.toHaveBeenCalled()
})

it('no duplica confirmaciones mientras una está pendiente', async () => {
  let resolve
  service.verifyMfa.mockImplementation(() => new Promise((done) => { resolve = done }))
  mount()
  const user = await start()
  await verify(user)
  expect(screen.getByRole('button', { name: 'Confirmar código' })).toBeDisabled()
  fireEvent.submit(screen.getByRole('button', { name: 'Confirmar código' }).closest('form'))
  expect(service.verifyMfa).toHaveBeenCalledTimes(1)
  await act(async () => resolve(authenticated))
})
it('respeta Retry-After del primer paso sin repetir credenciales', async () => {
  service.login.mockRejectedValue({ status: 429, retryAfter: 60, message: 'Demasiados intentos' })
  mount()
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Email'), 'user@example.com')
  await user.type(screen.getByLabelText('Contraseña'), 'password')
  await user.click(screen.getByRole('button', { name: 'Ingresar' }))
  expect(await screen.findByText(/Podés volver a intentar en/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Ingresar' })).toBeDisabled()
  expect(service.login).toHaveBeenCalledTimes(1)
})
