import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listCharacteristics } from '../services/characteristicService.js'
import { CharacteristicManagementPage } from './CharacteristicManagementPage.jsx'

vi.mock('../services/characteristicService.js', () => ({ listCharacteristics: vi.fn() }))

describe('CharacteristicManagementPage', () => {
  beforeEach(() => vi.resetAllMocks())

  it('muestra carga y conserva el orden y conteos recibidos en cards neutras', async () => {
    let resolveList
    listCharacteristics.mockImplementation(() => new Promise((resolve) => { resolveList = resolve }))
    render(<CharacteristicManagementPage />)

    expect(screen.getByRole('status')).toHaveTextContent('Cargando características')
    expect(listCharacteristics).toHaveBeenCalledWith({ signal: expect.any(AbortSignal) })

    resolveList([
      { id: 9, nombre: 'Peso', cantidadProductos: 2 },
      { id: 3, nombre: 'Alto', cantidadProductos: 1 },
    ])
    const cards = await screen.findAllByRole('listitem')
    expect(within(cards[0]).getByRole('heading', { name: 'Peso' })).toBeInTheDocument()
    expect(within(cards[0]).getByText('2 productos')).toBeInTheDocument()
    expect(within(cards[1]).getByRole('heading', { name: 'Alto' })).toBeInTheDocument()
    expect(within(cards[1]).getByText('1 producto')).toBeInTheDocument()
    for (const card of cards) {
      expect(card).toHaveClass('bg-white', 'border-slate-200')
      expect(card).not.toHaveTextContent(/activ[oa]|inactiv[oa]/i)
    }
  })

  it('muestra vacío y mantiene el alta visible, alineada a la derecha y con texto adaptable', async () => {
    listCharacteristics.mockResolvedValue([])
    render(<CharacteristicManagementPage />)
    expect(await screen.findByRole('status')).toHaveTextContent('No hay características')

    const create = screen.getByRole('button', { name: 'Crear característica' })
    expect(create.parentElement).toHaveClass('justify-end')
    expect(within(create).getByText('+')).toHaveClass('sm:hidden')
    expect(within(create).getByText('Crear característica')).toHaveClass('hidden', 'sm:inline')
    expect(create).toBeDisabled()
  })

  it('muestra error y reintenta solamente la lectura', async () => {
    listCharacteristics.mockRejectedValueOnce(new Error('Error de consulta')).mockResolvedValueOnce([{ id: 1, nombre: 'Color', cantidadProductos: 0 }])
    render(<CharacteristicManagementPage />)
    expect(await screen.findByText('Error de consulta')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('heading', { name: 'Color' })).toBeInTheDocument()
    expect(screen.getByText('0 productos')).toBeInTheDocument()
    expect(listCharacteristics).toHaveBeenCalledTimes(2)
    expect(screen.queryByText('Error de consulta')).not.toBeInTheDocument()
  })

  it('cancela la lectura al desmontar', async () => {
    listCharacteristics.mockImplementation(() => new Promise(() => {}))
    const view = render(<CharacteristicManagementPage />)
    const signal = listCharacteristics.mock.calls[0][0].signal
    expect(signal.aborted).toBe(false)
    view.unmount()
    await waitFor(() => expect(signal.aborted).toBe(true))
  })
})
