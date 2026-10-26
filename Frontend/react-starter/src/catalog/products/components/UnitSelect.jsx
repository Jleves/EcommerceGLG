import { useState } from 'react'

const options = [
  { value: 'kg', label: 'Kilogramo (kg)' },
  { value: 'g', label: 'Gramo (g)' },
  { value: 'L', label: 'Litro (L)' },
  { value: 'mL', label: 'Mililitro (mL)' },
  { value: 'mm', label: 'Milímetro (mm)' },
  { value: 'cm', label: 'Centímetro (cm)' },
  { value: 'in', label: 'Pulgada (in)' },
]

const knownUnits = new Set(options.map((option) => option.value))
const fieldClass = 'w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-slate-900 shadow-sm outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-200'
const labelClass = 'grid gap-1.5 text-sm font-semibold text-slate-700'

function initialMode(value) {
  if (value == null || value === '') return 'none'
  return knownUnits.has(value) ? value : 'other'
}

export function UnitSelect({ id, label, value, onChange }) {
  const [mode, setMode] = useState(() => initialMode(value))

  function choose(event) {
    const selected = event.target.value
    setMode(selected)
    onChange(selected === 'none' ? null : selected === 'other' ? '' : selected)
  }

  return <div className="grid gap-2">
    <label className={labelClass} htmlFor={id}>{label}
      <select id={id} value={mode} onChange={choose} className={fieldClass}>
        <option value="none">Sin unidad</option>
        {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        <option value="other">Otra…</option>
      </select>
    </label>
    {mode === 'other' && <label className={labelClass} htmlFor={`${id}-other`}>Otra unidad
      <input id={`${id}-other`} required maxLength={100} value={value ?? ''} onChange={(event) => onChange(event.target.value)} className={fieldClass} />
    </label>}
  </div>
}
