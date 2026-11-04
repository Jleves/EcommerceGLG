import { describe, expect, it } from 'vitest'
import { cartMessage, variantLabel } from './cartUtils.js'

describe('mensaje de presupuesto', () => {
  it('incluye variantes y cantidades sin precios', () => {
    const lines = [
      { name: 'Pintura', variantName: 'Color: Blanco', quantity: 2, unitPrice: 1200 },
      { name: 'Cemento', variantName: 'Bolsa 50 kg', quantity: 1, unitPrice: 900 },
    ]
    expect(cartMessage(lines)).toBe('Hola, quisiera pedir un presupuesto para:\n• Pintura — Color: Blanco — 2 unidades\n• Cemento — Bolsa 50 kg — 1 unidad')
    expect(cartMessage(lines)).not.toContain('1200')
    expect(variantLabel({ id: 5, sku: 'Bolsa 50 kg' })).toBe('Bolsa 50 kg')
  })
})
