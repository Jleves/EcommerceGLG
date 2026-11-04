export function variantLabel(variant) {
  if (variant.caracteristicaNombre && variant.valor) return `${variant.caracteristicaNombre}: ${variant.valor}${variant.unidad ? ` ${variant.unidad}` : ''}`
  return variant.sku || `Opción ${variant.id}`
}

export function cartMessage(lines) {
  return `Hola, quisiera pedir un presupuesto para:\n${lines.map((line) => `• ${line.name} — ${line.variantName} — ${line.quantity} ${line.quantity === 1 ? 'unidad' : 'unidades'}`).join('\n')}`
}
