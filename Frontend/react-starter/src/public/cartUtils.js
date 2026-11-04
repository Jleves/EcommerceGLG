export function variantLabel(variant) {
  if (variant.caracteristicaNombre && variant.valor) return `${variant.caracteristicaNombre}: ${variant.valor}${variant.unidad ? ` ${variant.unidad}` : ''}`
  return variant.sku || `Opción ${variant.id}`
}

export function quoteVariantDetails(variant) {
  return {
    presentation: variant.caracteristicaNombre && variant.valor
      ? `${variant.caracteristicaNombre} ${variant.valor}${variant.unidad ? ` ${variant.unidad}` : ''}`
      : null,
    sku: variant.sku?.trim() || null,
  }
}

export function cartMessage(lines) {
  return `Hola, quisiera pedir un presupuesto:\n\n${lines.map((line) => [
    `Producto: ${line.name}`,
    ...(line.presentation ? [`Presentación: ${line.presentation}`] : []),
    `Cantidad: ${line.quantity} ${line.quantity === 1 ? 'unidad' : 'unidades'}`,
    ...(line.sku ? [`Código: ${line.sku}`] : []),
  ].join('\n')).join('\n\n')}`
}
