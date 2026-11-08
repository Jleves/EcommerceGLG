import {
  IconBolt, IconBuilding, IconCategory, IconDroplet, IconFlame,
  IconGardenCart, IconGridDots, IconHammer, IconHome, IconLadder,
  IconLamp2, IconNut, IconPaint, IconPipeline, IconPlug, IconPlunger,
  IconTool, IconTools, IconTrafficCone, IconTrowel, IconWall,
} from '@tabler/icons-react'
import { describe, expect, it } from 'vitest'
import { categoryIconOptions, resolveCategoryIcon } from './categoryIcons.js'

describe('categoryIcons', () => {
  it('ofrece una selección breve sin claves ni dibujos duplicados', () => {
    const keys = categoryIconOptions.map(({ key }) => key)
    expect(new Set(keys).size).toBe(keys.length)
    expect(new Set(categoryIconOptions.map(({ Icon }) => Icon)).size).toBe(keys.length)
    expect(keys).toEqual([
      'materiales', 'ceramicos', 'pintura', 'herramientas',
      'plomeria', 'electricidad', 'gas', 'agua',
      'home', 'tool', 'bolt', 'ladder', 'wall', 'hammer',
      'plunger', 'lamp-2', 'nut', 'garden-cart', 'trowel', 'traffic-cone',
    ])
  })

  it.each([
    ['materiales', IconBuilding], ['ceramicos', IconGridDots], ['pintura', IconPaint],
    ['herramientas', IconTools], ['plomeria', IconPipeline],
    ['electricidad', IconPlug], ['gas', IconFlame], ['agua', IconDroplet],
    ['home', IconHome], ['tool', IconTool], ['bolt', IconBolt], ['ladder', IconLadder],
    ['wall', IconWall], ['hammer', IconHammer], ['plunger', IconPlunger],
    ['lamp-2', IconLamp2], ['nut', IconNut], ['garden-cart', IconGardenCart],
    ['trowel', IconTrowel], ['traffic-cone', IconTrafficCone],
  ])('resuelve %s mediante su importación estática', (key, Icon) => {
    expect(resolveCategoryIcon(key)).toEqual({ Icon, known: true })
  })

  it('usa respaldo para una clave previa sin modificarla', () => {
    expect(resolveCategoryIcon('cemento')).toEqual({ Icon: IconCategory, known: false })
  })
})
