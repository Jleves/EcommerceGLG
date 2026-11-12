import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSubcategory, deleteSubcategory, getSubcategory, listSubcategories, updateSubcategory } from '../services/subcategoryService.js'
import { SubcategoryManager } from './SubcategoryManager.jsx'

vi.mock('../services/subcategoryService.js', () => ({
  listSubcategories: vi.fn(), getSubcategory: vi.fn(), createSubcategory: vi.fn(), updateSubcategory: vi.fn(), deleteSubcategory: vi.fn(),
}))

const categories = [{ id: 1, nombre: 'Metales', activo: true }]
const subcategories = [{ id: 4, categoriaId: 1, nombre: 'Chapas', descripcion: '' }]

describe('SubcategoryManager', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listSubcategories.mockResolvedValue(subcategories)
    getSubcategory.mockResolvedValue({ ...subcategories[0], productosAsociados: 0, hijosAsociados: 0 })
    createSubcategory.mockResolvedValue({})
    updateSubcategory.mockResolvedValue({})
    deleteSubcategory.mockResolvedValue(undefined)
  })

  it('crea, edita y refresca la lista del padre', async () => {
    const user = userEvent.setup()
    render(<SubcategoryManager categories={categories} />)
    await screen.findByText('Chapas')
    await user.click(screen.getByRole('button', { name: 'Crear subcategoría' }))
    const form = screen.getByRole('dialog')
    expect(form).toHaveAttribute('open')
    expect(form).toHaveAttribute('aria-labelledby', 'subcategory-form-title')
    await user.selectOptions(within(form).getByRole('combobox', { name: 'Categoría padre' }), '1')
    await user.type(within(form).getByRole('textbox', { name: 'Nombre' }), 'Perfiles')
    await user.click(within(form).getByRole('button', { name: 'Guardar subcategoría' }))
    await waitFor(() => expect(createSubcategory).toHaveBeenCalledWith({ categoriaId: 1, nombre: 'Perfiles', descripcion: '', subcategoriaPadreId: null }))
    await waitFor(() => expect(listSubcategories).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Editar subcategoría Chapas' }))
    expect(screen.getByRole('dialog')).toHaveAttribute('open')
    await user.click(screen.getByRole('button', { name: 'Guardar subcategoría' }))
    expect(updateSubcategory).toHaveBeenCalledWith(4, { nombre: 'Chapas', descripcion: '' })
  })

  it('cierra el modal con Cancelar o Escape sin crear una subcategoría', async () => {
    const user = userEvent.setup()
    render(<SubcategoryManager categories={categories} />)
    await screen.findByText('Chapas')
    await user.click(screen.getByRole('button', { name: 'Crear subcategoría' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Crear subcategoría' }))
    act(() => screen.getByRole('dialog').dispatchEvent(new Event('cancel', { bubbles: true, cancelable: true })))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(createSubcategory).not.toHaveBeenCalled()
  })

  it('permite consultar el estado desde el modal tras un guardado incierto', async () => {
    const user = userEvent.setup()
    createSubcategory.mockRejectedValueOnce(new Error('Sin respuesta'))
    render(<SubcategoryManager categories={categories} />)
    await screen.findByText('Chapas')
    await user.click(screen.getByRole('button', { name: 'Crear subcategoría' }))
    const dialog = screen.getByRole('dialog')
    await user.selectOptions(within(dialog).getByRole('combobox', { name: 'Categoría padre' }), '1')
    await user.type(within(dialog).getByRole('textbox', { name: 'Nombre' }), 'Perfiles')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar subcategoría' }))
    const reviewButton = await within(dialog).findByRole('button', { name: 'Consultar estado actual' })
    expect(within(dialog).getByRole('button', { name: 'Guardar subcategoría' })).toBeDisabled()
    await user.click(reviewButton)
    await waitFor(() => expect(listSubcategories).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('muestra recuento y advertencia antes de borrar, luego refresca tras 204', async () => {
    const user = userEvent.setup()
    getSubcategory.mockResolvedValueOnce({ ...subcategories[0], productosAsociados: 2, hijosAsociados: 1 })
    render(<SubcategoryManager categories={categories} />)
    await screen.findByText('Chapas')
    await user.click(screen.getByRole('button', { name: 'Eliminar subcategoría Chapas' }))
    let dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/Hijos: 1. Productos directos: 2/)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Confirmar eliminación' })).toBeDisabled()
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar subcategoría Chapas' }))
    dialog = await screen.findByRole('dialog')
    expect(deleteSubcategory).not.toHaveBeenCalled()
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar eliminación' }))
    await waitFor(() => expect(deleteSubcategory).toHaveBeenCalledWith(4))
    await waitFor(() => expect(listSubcategories).toHaveBeenCalledTimes(2))
    expect(screen.getByText('Subcategoría eliminada.')).toBeInTheDocument()
  })

  it('muestra un conflicto concurrente sin anunciar eliminación', async () => {
    const user = userEvent.setup()
    deleteSubcategory.mockRejectedValueOnce({ status: 409, message: 'Tiene hijos asociados' })
    render(<SubcategoryManager categories={categories} />)
    await screen.findByText('Chapas')
    await user.click(screen.getByRole('button', { name: 'Eliminar subcategoría Chapas' }))
    await user.click(await screen.findByRole('button', { name: 'Confirmar eliminación' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Tiene hijos asociados')
    expect(screen.queryByText('Subcategoría eliminada.')).not.toBeInTheDocument()
  })

  it('exige consultar estado tras un resultado de red incierto', async () => {
    const user = userEvent.setup()
    deleteSubcategory.mockRejectedValueOnce(new Error('Sin respuesta'))
    render(<SubcategoryManager categories={categories} />)
    await screen.findByText('Chapas')
    await user.click(screen.getByRole('button', { name: 'Eliminar subcategoría Chapas' }))
    await user.click(await screen.findByRole('button', { name: 'Confirmar eliminación' }))
    expect(await screen.findByRole('button', { name: 'Consultar estado actual' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar eliminación' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Consultar estado actual' }))
    await waitFor(() => expect(listSubcategories).toHaveBeenCalledTimes(2))
    expect(deleteSubcategory).toHaveBeenCalledTimes(1)
  })
})
