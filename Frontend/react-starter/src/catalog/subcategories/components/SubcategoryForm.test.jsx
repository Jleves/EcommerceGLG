import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SubcategoryForm } from './SubcategoryForm.jsx'

const categories = [{ id: 1, nombre: 'Activa', activo: true }, { id: 2, nombre: 'Inactiva', activo: false }]

describe('SubcategoryForm', () => {
  it('crea un hijo solo bajo una raíz', async () => {
    const user = userEvent.setup(), submit = vi.fn()
    const nodes = [{ id: 4, categoriaId: 1, nombre: 'Raíz' }, { id: 5, categoriaId: 1, nombre: 'Hijo', subcategoriaPadreId: 4 }]
    render(<SubcategoryForm categories={categories} subcategories={nodes} onSubmit={submit} onCancel={() => {}} />)
    await user.selectOptions(screen.getByRole('combobox', { name: 'Categoría padre' }), '1')
    const parent = screen.getByRole('combobox', { name: 'Subcategoría padre (opcional)' })
    expect(parent).not.toHaveTextContent('Hijo')
    await user.selectOptions(parent, '4')
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Nuevo')
    await user.click(screen.getByRole('button', { name: 'Guardar subcategoría' }))
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ subcategoriaPadreId: 4 }))
  })
  it('crea solo bajo un padre activo y no permite cambiar el padre al editar', async () => {
    const user = userEvent.setup()
    const submit = vi.fn()
    const { rerender } = render(<SubcategoryForm categories={categories} onSubmit={submit} onCancel={() => {}} />)
    expect(screen.getByRole('combobox', { name: 'Categoría padre' })).not.toHaveTextContent('Inactiva')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Categoría padre' }), '1')
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Chapas')
    await user.click(screen.getByRole('button', { name: 'Guardar subcategoría' }))
    expect(submit).toHaveBeenCalledWith({ categoriaId: 1, nombre: 'Chapas', descripcion: '', subcategoriaPadreId: null })
    rerender(<SubcategoryForm key="edit" initialValues={{ id: 4, categoriaId: 2, nombre: 'Vieja', descripcion: '' }} categories={categories} onSubmit={submit} onCancel={() => {}} />)
    expect(screen.queryByRole('combobox', { name: 'Categoría padre' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Guardar subcategoría' }))
    expect(submit).toHaveBeenLastCalledWith({ nombre: 'Vieja', descripcion: '' })
  })
})
