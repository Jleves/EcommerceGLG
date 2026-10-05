import { categoryIconGroups, resolveCategoryIcon } from '../icons/categoryIcons.js'

export function CategoryIconPicker({ value, onChange, disabled, error }) {
  const unknown = value && !resolveCategoryIcon(value).known
  const { Icon: FallbackIcon } = resolveCategoryIcon(value)

  return (
    <fieldset aria-describedby={error ? 'category-icon-error' : undefined} aria-invalid={Boolean(error)} disabled={disabled} className="grid gap-3">
      <legend className="text-sm font-semibold text-slate-700">Ícono</legend>
      {unknown && (
        <label className="flex items-center gap-2 rounded-xl border border-slate-300 p-3">
          <input type="radio" name="category-icon" value={value} checked onChange={() => onChange(value)} required />
          <FallbackIcon aria-hidden="true" size={22} />
          Conservar ícono actual ({value})
        </label>
      )}
      {categoryIconGroups.map((group) => (
        <div key={group.label} role="group" aria-label={group.label} className="grid gap-2">
          <p className="text-sm font-semibold text-slate-600">{group.label}</p>
          <div className="flex flex-wrap gap-2">
            {group.options.map((option) => <IconOption key={option.key} option={option} value={value} onChange={onChange} />)}
          </div>
        </div>
      ))}
      {error && <small className="text-red-700" id="category-icon-error">{error}</small>}
    </fieldset>
  )
}

function IconOption({ option, value, onChange }) {
  const { key, label, Icon } = option
  return (
    <label className="flex cursor-pointer items-center gap-2 rounded-xl border border-slate-300 bg-white p-3 has-checked:border-emerald-700 has-checked:bg-emerald-50">
      <input type="radio" name="category-icon" value={key} checked={value === key} onChange={() => onChange(key)} required />
      <Icon aria-hidden="true" size={22} />
      {label}
    </label>
  )
}
