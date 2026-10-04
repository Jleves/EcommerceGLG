import {
  IconBuilding,
  IconCategory,
  IconDroplet,
  IconFlame,
  IconGridDots,
  IconPaint,
  IconPipeline,
  IconPlug,
  IconTools,
} from '@tabler/icons-react'

export const categoryIconGroups = [
  {
    label: 'Construcción y acabados',
    options: [
      { key: 'materiales', label: 'Materiales', Icon: IconBuilding },
      { key: 'ceramicos', label: 'Cerámicos', Icon: IconGridDots },
      { key: 'pintura', label: 'Pintura', Icon: IconPaint },
    ],
  },
  {
    label: 'Herramientas',
    options: [{ key: 'herramientas', label: 'Herramientas', Icon: IconTools }],
  },
  {
    label: 'Instalaciones',
    options: [
      { key: 'plomeria', label: 'Plomería', Icon: IconPipeline },
      { key: 'electricidad', label: 'Electricidad', Icon: IconPlug },
      { key: 'gas', label: 'Gas', Icon: IconFlame },
      { key: 'agua', label: 'Agua', Icon: IconDroplet },
    ],
  },
]

const iconsByKey = new Map(categoryIconGroups.flatMap((group) =>
  group.options.map((option) => [option.key, option.Icon])))

export function resolveCategoryIcon(name) {
  return { Icon: iconsByKey.get(name) ?? IconCategory, known: iconsByKey.has(name) }
}
