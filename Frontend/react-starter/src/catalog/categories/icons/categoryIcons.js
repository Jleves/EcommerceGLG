import {
  IconBolt,
  IconBuilding,
  IconCategory,
  IconDroplet,
  IconFlame,
  IconGardenCart,
  IconGridDots,
  IconHammer,
  IconHome,
  IconLadder,
  IconLamp2,
  IconNut,
  IconPaint,
  IconPipeline,
  IconPlug,
  IconPlunger,
  IconTool,
  IconTools,
  IconTrafficCone,
  IconTrowel,
  IconWall,
} from '@tabler/icons-react'

export const categoryIconOptions = [
  { key: 'materiales', label: 'Edificio', Icon: IconBuilding },
  { key: 'ceramicos', label: 'Cuadrícula de puntos', Icon: IconGridDots },
  { key: 'pintura', label: 'Rodillo', Icon: IconPaint },
  { key: 'herramientas', label: 'Herramientas cruzadas', Icon: IconTools },
  { key: 'plomeria', label: 'Tubería', Icon: IconPipeline },
  { key: 'electricidad', label: 'Enchufe', Icon: IconPlug },
  { key: 'gas', label: 'Llama', Icon: IconFlame },
  { key: 'agua', label: 'Gota', Icon: IconDroplet },
  { key: 'home', label: 'Casa', Icon: IconHome },
  { key: 'tool', label: 'Llave', Icon: IconTool },
  { key: 'bolt', label: 'Rayo', Icon: IconBolt },
  { key: 'ladder', label: 'Escalera', Icon: IconLadder },
  { key: 'wall', label: 'Pared de ladrillos', Icon: IconWall },
  { key: 'hammer', label: 'Martillo', Icon: IconHammer },
  { key: 'plunger', label: 'Destapacaños', Icon: IconPlunger },
  { key: 'lamp-2', label: 'Lámpara', Icon: IconLamp2 },
  { key: 'nut', label: 'Tuerca', Icon: IconNut },
  { key: 'garden-cart', label: 'Carretilla', Icon: IconGardenCart },
  { key: 'trowel', label: 'Cuchara de albañil', Icon: IconTrowel },
  { key: 'traffic-cone', label: 'Cono', Icon: IconTrafficCone },
]

const iconsByKey = new Map(categoryIconOptions.map(({ key, Icon }) => [key, Icon]))

export function resolveCategoryIcon(name) {
  return { Icon: iconsByKey.get(name) ?? IconCategory, known: iconsByKey.has(name) }
}
