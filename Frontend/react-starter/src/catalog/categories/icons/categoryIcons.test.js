import {
  IconBuilding, IconCategory, IconDroplet, IconFlame, IconGridDots,
  IconPaint, IconPipeline, IconPlug, IconTools,
} from '@tabler/icons-react'
import { describe, expect, it } from 'vitest'
import { categoryIconGroups, resolveCategoryIcon } from './categoryIcons.js'

describe('categoryIcons', () => {
  it('mantiene grupos y claves únicas del registro acordado', () => {
    expect(categoryIconGroups.map(({ label }) => label)).toEqual([
      'Construcción y acabados', 'Herramientas', 'Instalaciones',
    ])
    const keys = categoryIconGroups.flatMap(({ options }) => options.map(({ key }) => key))
    expect(new Set(keys).size).toBe(keys.length)
    expect(keys).toEqual([
      'materiales', 'ceramicos', 'pintura', 'herramientas',
      'plomeria', 'electricidad', 'gas', 'agua',
    ])
  })

  it.each([
    ['materiales', IconBuilding], ['ceramicos', IconGridDots], ['pintura', IconPaint],
    ['herramientas', IconTools], ['plomeria', IconPipeline],
    ['electricidad', IconPlug], ['gas', IconFlame], ['agua', IconDroplet],
  ])('resuelve %s mediante su importación estática', (key, Icon) => {
    expect(resolveCategoryIcon(key)).toEqual({ Icon, known: true })
  })

  it('usa respaldo para una clave previa sin modificarla', () => {
    expect(resolveCategoryIcon('cemento')).toEqual({ Icon: IconCategory, known: false })
  })
})
