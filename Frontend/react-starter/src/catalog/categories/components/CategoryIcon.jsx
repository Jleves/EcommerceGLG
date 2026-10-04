import { resolveCategoryIcon } from '../icons/categoryIcons.js'

export function CategoryIcon({ name }) {
  const { Icon, known } = resolveCategoryIcon(name)

  return (
    <span className="inline-flex items-center gap-2">
      <Icon aria-hidden="true" size={28} stroke={1.75} />
      {!known && <span className="text-xs text-amber-800">Ícono desconocido: {name}</span>}
    </span>
  )
}
