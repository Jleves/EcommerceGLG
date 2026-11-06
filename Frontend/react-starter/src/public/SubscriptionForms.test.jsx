import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { SubscriptionForms } from './SubscriptionForms.jsx'
import { subscribe } from './publicService.js'

vi.mock('./publicService.js', () => ({ subscribe: vi.fn() }))

beforeEach(() => { subscribe.mockReset(); subscribe.mockResolvedValue(null) })

it('shows two independent forms with explicit consent', async () => {
  const user = userEvent.setup()
  render(<SubscriptionForms />)
  const email = screen.getByRole('form', { name: 'Suscripción Por email' })
  const whatsapp = screen.getByRole('form', { name: 'Suscripción Por WhatsApp' })
  expect(within(email).getByRole('button', { name: 'Suscribirme' })).toBeDisabled()
  await user.type(within(email).getByRole('textbox', { name: 'Tu email' }), 'a@b.com')
  await user.click(within(email).getByRole('checkbox'))
  await user.click(within(email).getByRole('button', { name: 'Suscribirme' }))
  expect(subscribe).toHaveBeenCalledWith('EMAIL', 'a@b.com', true)
  expect(await within(email).findByRole('status')).toHaveTextContent('por email')
  expect(within(whatsapp).getByRole('checkbox')).not.toBeChecked()
  await user.type(within(whatsapp).getByRole('textbox', { name: 'Tu celular argentino' }), '2984 123456')
  await user.click(within(whatsapp).getByRole('checkbox'))
  await user.click(within(whatsapp).getByRole('button', { name: 'Suscribirme' }))
  expect(subscribe).toHaveBeenCalledWith('WHATSAPP', '2984 123456', true)
})

it('shows rate limit and permits retry', async () => {
  const user = userEvent.setup()
  subscribe.mockRejectedValueOnce({ status: 429, retryAfter: 120 })
  render(<SubscriptionForms />)
  const email = screen.getByRole('form', { name: 'Suscripción Por email' })
  await user.type(within(email).getByRole('textbox', { name: 'Tu email' }), 'a@b.com')
  await user.click(within(email).getByRole('checkbox'))
  await user.click(within(email).getByRole('button', { name: 'Suscribirme' }))
  expect(await within(email).findByRole('alert')).toHaveTextContent('2 minuto(s)')
  await user.click(within(email).getByRole('button', { name: 'Suscribirme' }))
  expect(await within(email).findByRole('status')).toBeInTheDocument()
})

it('shows a recoverable network error and blocks a duplicate while sending', async () => {
  const user = userEvent.setup()
  let rejectRequest
  subscribe.mockImplementationOnce(() => new Promise((_, reject) => { rejectRequest = reject }))
  render(<SubscriptionForms />)
  const whatsapp = screen.getByRole('form', { name: 'Suscripción Por WhatsApp' })
  await user.type(within(whatsapp).getByRole('textbox', { name: 'Tu celular argentino' }), '2984 123456')
  await user.click(within(whatsapp).getByRole('checkbox'))
  await user.click(within(whatsapp).getByRole('button', { name: 'Suscribirme' }))
  expect(within(whatsapp).getByRole('button', { name: 'Guardando…' })).toBeDisabled()
  expect(subscribe).toHaveBeenCalledTimes(1)
  rejectRequest({ status: 0 })
  expect(await within(whatsapp).findByRole('alert')).toHaveTextContent('No pudimos guardar')
  await user.click(within(whatsapp).getByRole('button', { name: 'Suscribirme' }))
  expect(await within(whatsapp).findByRole('status')).toBeInTheDocument()
  expect(subscribe).toHaveBeenCalledTimes(2)
})
