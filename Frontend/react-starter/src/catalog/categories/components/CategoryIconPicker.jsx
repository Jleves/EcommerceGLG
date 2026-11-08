import { categoryIconOptions, resolveCategoryIcon } from '../icons/categoryIcons.js'

export function CategoryIconPicker({ value, onChange, disabled, error }) {
  const unknown = value && !resolveCategoryIcon(value).known
  const { Icon: FallbackIcon } = resolveCategoryIcon(value)

  return (
    <fieldset aria-describedby={error ? 'category-icon-error' : undefined} aria-invalid={Boolean(error)} disabled={disabled} className="grid gap-3">
      <legend className="text-sm font-semibold text-slate-700">Ícono</legend>
      <div className="flex flex-wrap gap-2" role="group" aria-label="Elegir ícono">
        {unknown && <IconOption option={{ key: value, label: `Ícono actual (${value})`, Icon: FallbackIcon }} value={value} onChange={onChange} />}
        {categoryIconOptions.map((option) => <IconOption key={option.key} option={option} value={value} onChange={onChange} />)}
      </div>
      {error && <small className="text-red-700" id="category-icon-error">{error}</small>}
    </fieldset>
  )
}

function IconOption({ option, value, onChange }) {
  const { key, label, Icon } = option
  return (
    <label title={label} className="flex size-12 cursor-pointer items-center justify-center rounded-lg border border-slate-300 bg-white text-slate-700 has-checked:border-emerald-700 has-checked:bg-emerald-50 has-checked:text-emerald-800 focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-emerald-700">
      <input className="sr-only" type="radio" name="category-icon" value={key} checked={value === key} onChange={() => onChange(key)} aria-label={label} required />
      <Icon aria-hidden="true" size={26} stroke={1.7} />
    </label>
  )
}
