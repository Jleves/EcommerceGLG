import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { UnitSelect } from './UnitSelect.jsx'

function Example({ initial = null }) {
  const [unit, setUnit] = useState(initial)
  return <><UnitSelect id="unit" label="Unidad" value={unit} onChange={setUnit} /><output data-testid="unit">{unit === null ? 'null' : unit}</output></>
}

describe('UnitSelect', () => {
  it('ofrece las siete abreviaturas acordadas y envía null para Sin unidad', async () => {
    const user = userEvent.setup()
    render(<Example />)
    const select = screen.getByRole('combobox', { name: 'Unidad' })
    expect(select).toHaveValue('none')
    expect([...select.options].map((option) => option.value)).toEqual(['none', 'kg', 'g', 'L', 'mL', 'mm', 'cm', 'in', 'other'])
    await user.selectOptions(select, 'mL')
    expect(screen.getByTestId('unit')).toHaveTextContent('mL')
    await user.selectOptions(select, 'none')
    expect(screen.getByTestId('unit')).toHaveTextContent('null')
  })

  it('conserva una unidad existente fuera de lista y descarta la anterior al cambiar de opción', async () => {
    const user = userEvent.setup()
    render(<Example initial="bolsa" />)
    const select = screen.getByRole('combobox', { name: 'Unidad' })
    expect(select).toHaveValue('other')
    expect(screen.getByRole('textbox', { name: 'Otra unidad' })).toHaveValue('bolsa')
    await user.selectOptions(select, 'kg')
    expect(screen.queryByRole('textbox', { name: 'Otra unidad' })).not.toBeInTheDocument()
    expect(screen.getByTestId('unit')).toHaveTextContent('kg')
    await user.selectOptions(select, 'other')
    expect(screen.getByRole('textbox', { name: 'Otra unidad' })).toHaveValue('')
    await user.type(screen.getByRole('textbox', { name: 'Otra unidad' }), ' caja ')
    expect(screen.getByRole('textbox', { name: 'Otra unidad' })).toHaveValue(' caja ')
    await user.selectOptions(select, 'none')
    expect(screen.getByTestId('unit')).toHaveTextContent('null')
  })
})
