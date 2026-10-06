import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ProductForm } from './ProductForm.jsx'

const categories = [{ id: 1, nombre: 'Activa', activo: true }, { id: 2, nombre: 'Inactiva', activo: false }]
const characteristics = [{ id: 3, nombre: 'Color' }, { id: 4, nombre: 'Peso' }]

describe('ProductForm', () => {
  it('valida precio positivo, escala, categoría y disponibilidad antes de enviar', async () => {
    const submit = vi.fn()
    const user = userEvent.setup()
    render(<ProductForm categories={categories} characteristics={characteristics} onSubmit={submit} onCancel={() => {}} />)
    expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveAttribute('maxLength', '160')
    expect(screen.getByRole('textbox', { name: 'Descripción' })).toHaveAttribute('maxLength', '1000')
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Cal')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Categoría' }), '1')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Disponibilidad' }), 'false')
    const price = screen.getByRole('textbox', { name: 'Precio de referencia' })
    await user.type(price, '0')
    await user.click(screen.getByRole('button', { name: 'Guardar producto' }))
    expect(submit).not.toHaveBeenCalled()
    await user.clear(price)
    await user.type(price, '1.234')
    await user.click(screen.getByRole('button', { name: 'Guardar producto' }))
    expect(submit).not.toHaveBeenCalled()
    await user.clear(price)
    await user.type(price, '0.01')
    await user.click(screen.getByRole('button', { name: 'Guardar producto' }))
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ precioReferencia: '0.01', disponible: false }))
  })

  it('evita características duplicadas y exige valor; permite quitarlas todas', async () => {
    const submit = vi.fn()
    const user = userEvent.setup()
    render(<ProductForm categories={categories} characteristics={characteristics} initialValues={{ nombre: 'Arena', categoriaId: 1, descripcion: '', precioReferencia: 10, disponible: true, caracteristicas: [{ caracteristicaId: 3, valor: 'Rojo', unidad: '' }] }} onSubmit={submit} onCancel={() => {}} />)
    await user.click(screen.getByRole('button', { name: 'Agregar característica' }))
    expect(screen.getAllByRole('textbox', { name: 'Valor' })[1]).toHaveAttribute('maxLength', '500')
    expect(screen.getAllByRole('textbox', { name: 'Unidad' })[1]).toHaveAttribute('maxLength', '100')
    expect(screen.getAllByRole('combobox', { name: 'Característica' })[1]).not.toHaveTextContent('Color')
    await user.selectOptions(screen.getAllByRole('combobox', { name: 'Característica' })[1], '4')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(submit).not.toHaveBeenCalled()
    await user.type(screen.getAllByRole('textbox', { name: 'Valor' })[1], '25')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ caracteristicas: [{ caracteristicaId: 3, valor: 'Rojo', unidad: '' }, { caracteristicaId: 4, valor: '25', unidad: '' }] }))
    await user.click(screen.getAllByRole('button', { name: 'Quitar característica' })[1])
    await user.click(screen.getByRole('button', { name: 'Quitar característica' }))
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(submit).toHaveBeenLastCalledWith(expect.objectContaining({ caracteristicas: [] }))
  })
})
