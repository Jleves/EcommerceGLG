import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacteristicForm } from './CharacteristicForm.jsx'

describe('CharacteristicForm', () => {
  it('exige nombre no vacío, rechaza espacios y envía el valor recortado', async () => {
    const submit = vi.fn()
    const user = userEvent.setup()
    render(<CharacteristicForm onSubmit={submit} onCancel={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
    expect(screen.getByText('Ingresá un nombre.')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), '   ')
    await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
    expect(submit).not.toHaveBeenCalled()
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Color ')
    await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
    expect(submit).toHaveBeenCalledTimes(1)
    expect(submit).toHaveBeenCalledWith({ nombre: 'Color' })
  })

  it('limita a 160 caracteres y muestra el error de campo recibido de la API', async () => {
    const submit = vi.fn()
    const user = userEvent.setup()
    render(<CharacteristicForm onSubmit={submit} onCancel={vi.fn()} error={{ message: 'Dato inválido', fieldErrors: [{ field: 'nombre', message: 'Nombre inválido' }] }} />)
    const input = screen.getByRole('textbox', { name: 'Nombre' })
    expect(input).toHaveAttribute('maxLength', '160')
    expect(screen.getByText('Nombre inválido')).toBeInTheDocument()
    await user.type(input, 'a'.repeat(161))
    expect(input).toHaveValue('a'.repeat(160))
    await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
    expect(submit).toHaveBeenCalledWith({ nombre: 'a'.repeat(160) })
  })

  it('precarga el nombre y bloquea controles durante envío', () => {
    render(<CharacteristicForm initialValues={{ id: 7, nombre: 'Ancho' }} onSubmit={vi.fn()} onCancel={vi.fn()} pending />)
    expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveValue('Ancho')
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled()
  })
})
