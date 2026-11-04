import { describe, expect, it } from 'vitest'
import { cartMessage, quoteVariantDetails, variantLabel } from './cartUtils.js'

describe('mensaje de presupuesto', () => {
  it('separa artículos en fichas con presentación, código y cantidad', () => {
    const lines = [
      { name: 'Pintura', ...quoteVariantDetails({ caracteristicaNombre: 'Color', valor: 'Blanco', unidad: null, sku: 'P-01' }), quantity: 2, unitPrice: 1200 },
      { name: 'Cemento', ...quoteVariantDetails({ caracteristicaNombre: 'Peso', valor: '50', unidad: 'kg', sku: 'C-50' }), quantity: 1, unitPrice: 900 },
    ]
    expect(cartMessage(lines)).toBe('Hola, quisiera pedir un presupuesto:\n\nProducto: Pintura\nPresentación: Color Blanco\nCantidad: 2 unidades\nCódigo: P-01\n\nProducto: Cemento\nPresentación: Peso 50 kg\nCantidad: 1 unidad\nCódigo: C-50')
    expect(cartMessage(lines)).not.toContain('1200')
  })

  it('omite presentación y código si faltan, sin usar el id interno', () => {
    const variant = { id: 5, sku: null, caracteristicaNombre: null, valor: null }
    expect(variantLabel(variant)).toBe('Opción 5')
    expect(cartMessage([{ name: 'Cal hidratada', ...quoteVariantDetails(variant), quantity: 1 }]))
      .toBe('Hola, quisiera pedir un presupuesto:\n\nProducto: Cal hidratada\nCantidad: 1 unidad')
  })

  it('incluye el SKU aunque no exista presentación y descarta uno vacío', () => {
    expect(quoteVariantDetails({ sku: '  CAL-1  ' })).toEqual({ presentation: null, sku: 'CAL-1' })
    expect(quoteVariantDetails({ sku: '  ' })).toEqual({ presentation: null, sku: null })
    expect(cartMessage([{ name: 'Cal', ...quoteVariantDetails({ sku: 'CAL-1' }), quantity: 3 }]))
      .toBe('Hola, quisiera pedir un presupuesto:\n\nProducto: Cal\nCantidad: 3 unidades\nCódigo: CAL-1')
  })
})
