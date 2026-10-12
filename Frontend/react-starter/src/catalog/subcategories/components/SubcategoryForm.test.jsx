import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SubcategoryForm } from './SubcategoryForm.jsx'

const categories = [{ id: 1, nombre: 'Activa', activo: true }, { id: 2, nombre: 'Inactiva', activo: false }]

describe('SubcategoryForm', () => {
  it('crea solo bajo un padre activo y no permite cambiar el padre al editar', async () => {
    const user = userEvent.setup()
    const submit = vi.fn()
    const { rerender } = render(<SubcategoryForm categories={categories} onSubmit={submit} onCancel={() => {}} />)
    expect(screen.getByRole('combobox', { name: 'Categoría padre' })).not.toHaveTextContent('Inactiva')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Categoría padre' }), '1')
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Chapas')
    await user.click(screen.getByRole('button', { name: 'Guardar subcategoría' }))
    expect(submit).toHaveBeenCalledWith({ categoriaId: 1, nombre: 'Chapas', descripcion: '' })
    rerender(<SubcategoryForm key="edit" initialValues={{ id: 4, categoriaId: 2, nombre: 'Vieja', descripcion: '' }} categories={categories} onSubmit={submit} onCancel={() => {}} />)
    expect(screen.queryByRole('combobox', { name: 'Categoría padre' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Guardar subcategoría' }))
    expect(submit).toHaveBeenLastCalledWith({ nombre: 'Vieja', descripcion: '' })
  })
})
