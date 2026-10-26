import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ProductVariantManager } from './ProductVariantManager.jsx'

const characteristics = [{ id: 3, nombre: 'Color' }, { id: 4, nombre: 'Peso' }]
const initial = { id: 11, sku: null, precioReferencia: 100, disponible: true, caracteristicaId: null, caracteristicaNombre: null, valor: null, unidad: null }
const product = { id: 1, nombre: 'Pintura', varianteInicialId: 11, caracteristicas: [{ caracteristicaId: 4, valor: '20', unidad: 'L' }], variantes: [initial] }

function show(overrides = {}) {
  const callbacks = { onUpdateInitial: vi.fn(), onCreate: vi.fn(), onReview: vi.fn(), onClose: vi.fn() }
  render(<ProductVariantManager product={product} characteristics={characteristics} {...callbacks} {...overrides} />)
  return callbacks
}

describe('ProductVariantManager', () => {
  it('exige asignar atributo y valor a la inicial antes de permitir otra variante', async () => {
    const user = userEvent.setup()
    const { onUpdateInitial, onCreate } = show()
    expect(screen.queryByRole('button', { name: 'Agregar variante' })).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Atributo variable' })).not.toHaveTextContent('Peso')
    await user.click(screen.getByRole('button', { name: 'Guardar valor inicial' }))
    expect(onUpdateInitial).not.toHaveBeenCalled()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Atributo variable' }), '3')
    await user.type(screen.getByRole('textbox', { name: 'Valor de la variante inicial' }), 'Rojo')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Unidad de la variante inicial' }), 'kg')
    await user.click(screen.getByRole('button', { name: 'Guardar valor inicial' }))
    expect(onUpdateInitial).toHaveBeenCalledWith(11, { sku: null, precioReferencia: 100, disponible: true, caracteristicaId: 3, valor: 'Rojo', unidad: 'kg' })
    expect(onCreate).not.toHaveBeenCalled()
  })

  it('rechaza valor y unidad repetidos normalizados y admite una variante distinta', async () => {
    const user = userEvent.setup()
    const onCreate = vi.fn()
    const labeled = { ...initial, caracteristicaId: 3, caracteristicaNombre: 'Color', valor: 'Rojo', unidad: 'L' }
    render(<ProductVariantManager product={{ ...product, variantes: [labeled] }} characteristics={characteristics} onCreate={onCreate} onUpdateInitial={vi.fn()} onReview={vi.fn()} onClose={vi.fn()} />)
    await user.type(screen.getByRole('textbox', { name: 'Valor de la nueva variante' }), ' rojo ')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Unidad de la nueva variante' }), 'other')
    await user.type(screen.getByRole('textbox', { name: 'Otra unidad' }), ' l ')
    await user.type(screen.getByRole('textbox', { name: 'Precio de referencia' }), '120')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Disponibilidad' }), 'false')
    await user.click(screen.getByRole('button', { name: 'Agregar variante' }))
    expect(onCreate).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('Ya existe una variante')
    await user.clear(screen.getByRole('textbox', { name: 'Valor de la nueva variante' }))
    await user.type(screen.getByRole('textbox', { name: 'Valor de la nueva variante' }), 'Blanco')
    await user.click(screen.getByRole('button', { name: 'Agregar variante' }))
    expect(onCreate).toHaveBeenCalledWith({ sku: null, precioReferencia: '120', disponible: false, caracteristicaId: 3, valor: 'Blanco', unidad: 'l' })
  })

  it('bloquea el reenvío tras un resultado incierto hasta consultar el detalle', async () => {
    const user = userEvent.setup()
    const onReview = vi.fn()
    const labeled = { ...initial, caracteristicaId: 3, caracteristicaNombre: 'Color', valor: 'Rojo' }
    render(<ProductVariantManager product={{ ...product, variantes: [labeled] }} characteristics={characteristics} reviewRequired onReview={onReview} onCreate={vi.fn()} onUpdateInitial={vi.fn()} onClose={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Agregar variante' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Consultar detalle del producto' }))
    expect(onReview).toHaveBeenCalledTimes(1)
  })

  it('valida precio positivo y limita SKU, valor y unidad al contrato', async () => {
    const user = userEvent.setup()
    const onCreate = vi.fn()
    const labeled = { ...initial, caracteristicaId: 3, caracteristicaNombre: 'Color', valor: 'Rojo' }
    render(<ProductVariantManager product={{ ...product, variantes: [labeled] }} characteristics={characteristics} onCreate={onCreate} onUpdateInitial={vi.fn()} onReview={vi.fn()} onClose={vi.fn()} />)
    expect(screen.getByRole('textbox', { name: 'Identificador de la variante (opcional)' })).toHaveAttribute('maxLength', '160')
    expect(screen.getByRole('textbox', { name: 'Valor de la nueva variante' })).toHaveAttribute('maxLength', '500')
    expect(screen.getByRole('combobox', { name: 'Unidad de la nueva variante' })).toHaveTextContent('Kilogramo (kg)')
    await user.type(screen.getByRole('textbox', { name: 'Valor de la nueva variante' }), 'Blanco')
    await user.type(screen.getByRole('textbox', { name: 'Precio de referencia' }), '0')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Disponibilidad' }), 'true')
    await user.click(screen.getByRole('button', { name: 'Agregar variante' }))
    expect(onCreate).not.toHaveBeenCalled()
    await user.clear(screen.getByRole('textbox', { name: 'Precio de referencia' }))
    await user.type(screen.getByRole('textbox', { name: 'Precio de referencia' }), '1.234')
    await user.click(screen.getByRole('button', { name: 'Agregar variante' }))
    expect(onCreate).not.toHaveBeenCalled()
  })

  it('no envía Otra vacía y permite cambiar a Sin unidad', async () => {
    const user = userEvent.setup()
    const onCreate = vi.fn()
    const labeled = { ...initial, caracteristicaId: 3, caracteristicaNombre: 'Color', valor: 'Rojo' }
    render(<ProductVariantManager product={{ ...product, variantes: [labeled] }} characteristics={characteristics} onCreate={onCreate} onUpdateInitial={vi.fn()} onReview={vi.fn()} onClose={vi.fn()} />)
    await user.type(screen.getByRole('textbox', { name: 'Valor de la nueva variante' }), 'Blanco')
    await user.type(screen.getByRole('textbox', { name: 'Precio de referencia' }), '120')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Disponibilidad' }), 'true')
    const unit = screen.getByRole('combobox', { name: 'Unidad de la nueva variante' })
    await user.selectOptions(unit, 'other')
    await user.click(screen.getByRole('button', { name: 'Agregar variante' }))
    expect(onCreate).not.toHaveBeenCalled()
    await user.selectOptions(unit, 'none')
    await user.click(screen.getByRole('button', { name: 'Agregar variante' }))
    expect(onCreate).toHaveBeenCalledWith(expect.objectContaining({ valor: 'Blanco', unidad: null }))
  })
})
