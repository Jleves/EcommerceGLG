import { useCallback, useEffect, useState } from 'react'
import { CartContext } from './cartContext.js'
import { variantLabel } from './cartUtils.js'

const STORAGE_KEY = 'glg-public-cart-v1'

function validQuantity(value) {
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 && number <= 9999 ? number : null
}

function readCart() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    if (!Array.isArray(saved)) return []
    return saved.filter((line) => line && Number.isSafeInteger(Number(line.productId)) && Number.isSafeInteger(Number(line.variantId)) && validQuantity(line.quantity) && typeof line.name === 'string')
      .map((line) => ({ ...line, productId: Number(line.productId), variantId: Number(line.variantId), quantity: validQuantity(line.quantity) }))
  } catch {
    return []
  }
}

export function CartProvider({ children }) {
  const [lines, setLines] = useState(readCart)
  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(lines)) } catch { /* Storage may be unavailable. */ }
  }, [lines])
  const update = useCallback((change) => setLines(change), [])
  const add = useCallback((product, variant, quantity) => {
    const amount = validQuantity(quantity)
    if (!amount) return false
    update((current) => {
      const index = current.findIndex((line) => line.productId === product.id && line.variantId === variant.id)
      const snapshot = { productId: product.id, variantId: variant.id, name: product.nombre,
        variantName: variantLabel(variant), unitPrice: variant.precioOferta ?? variant.precioReferencia,
        available: variant.disponible, imageUrl: product.primaryThumbnailUrl || product.images?.find((image) => image.primary)?.thumbnailUrl || null }
      if (index < 0) return [...current, { ...snapshot, quantity: amount }]
      return current.map((line, position) => position === index ? { ...line, ...snapshot, quantity: Math.min(9999, line.quantity + amount), missing: false } : line)
    })
    return true
  }, [update])
  const setQuantity = useCallback((productId, variantId, value) => {
    const amount = validQuantity(value)
    if (!amount) return
    update((current) => current.map((line) => line.productId === productId && line.variantId === variantId ? { ...line, quantity: amount } : line))
  }, [update])
  const remove = useCallback((productId, variantId) => update((current) => current.filter((line) => line.productId !== productId || line.variantId !== variantId)), [update])
  const replace = useCallback((replacements) => update((current) => current.map((line) => {
    const replacement = replacements.get(`${line.productId}:${line.variantId}`)
    return replacement ? { ...replacement, quantity: line.quantity } : line
  })), [update])
  return <CartContext.Provider value={{ lines, add, setQuantity, remove, replace }}>{children}</CartContext.Provider>
}
