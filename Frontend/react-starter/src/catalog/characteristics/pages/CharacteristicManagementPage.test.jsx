import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createCharacteristic, getCharacteristic, listCharacteristics, updateCharacteristic } from '../services/characteristicService.js'
import { CharacteristicManagementPage } from './CharacteristicManagementPage.jsx'

vi.mock('../services/characteristicService.js', () => ({
  listCharacteristics: vi.fn(),
  getCharacteristic: vi.fn(),
  createCharacteristic: vi.fn(),
  updateCharacteristic: vi.fn(),
}))

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
    expect(create).toBeEnabled()
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

  it('crea una vez, recarga y muestra éxito temporal', async () => {
    listCharacteristics.mockResolvedValueOnce([]).mockResolvedValueOnce([{ id: 5, nombre: 'Color', cantidadProductos: 0 }])
    let resolveCreate
    createCharacteristic.mockImplementation(() => new Promise((resolve) => { resolveCreate = resolve }))
    const user = userEvent.setup()
    render(<CharacteristicManagementPage />)
    await screen.findByText('No hay características.')
    await user.click(screen.getByRole('button', { name: 'Crear característica' }))
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), ' Color ')
    await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
    expect(createCharacteristic).toHaveBeenCalledTimes(1)
    expect(createCharacteristic).toHaveBeenCalledWith({ nombre: 'Color' })
    expect(screen.getByRole('button', { name: 'Guardar característica' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled()
    const cancel = new Event('cancel', { cancelable: true })
    screen.getByRole('dialog').dispatchEvent(cancel)
    expect(cancel.defaultPrevented).toBe(true)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    resolveCreate({ id: 5, nombre: 'Color' })
    expect(await screen.findByRole('heading', { name: 'Color' })).toBeInTheDocument()
    expect(screen.getByText('Característica creada.')).toBeInTheDocument()
    expect(listCharacteristics).toHaveBeenCalledTimes(2)
  })

  it('consulta el detalle por ID antes de editar y conserva el ID y conteo en la recarga', async () => {
    listCharacteristics.mockResolvedValueOnce([{ id: 9, nombre: 'Viejo', cantidadProductos: 2 }]).mockResolvedValueOnce([{ id: 9, nombre: 'Nuevo', cantidadProductos: 2 }])
    getCharacteristic.mockResolvedValue({ id: 9, nombre: 'Nombre desde detalle', cantidadProductos: 2 })
    updateCharacteristic.mockResolvedValue({ id: 9, nombre: 'Nuevo', cantidadProductos: 2 })
    const user = userEvent.setup()
    render(<CharacteristicManagementPage />)
    await user.click(await screen.findByRole('button', { name: 'Editar Viejo' }))
    expect(getCharacteristic).toHaveBeenCalledWith(9, { signal: expect.any(AbortSignal) })
    const input = await screen.findByRole('textbox', { name: 'Nombre' })
    expect(input).toHaveValue('Nombre desde detalle')
    await user.clear(input)
    await user.type(input, ' Nuevo ')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(updateCharacteristic).toHaveBeenCalledTimes(1)
    expect(updateCharacteristic).toHaveBeenCalledWith(9, { nombre: 'Nuevo' })
    expect(await screen.findByRole('heading', { name: 'Nuevo' })).toBeInTheDocument()
    expect(screen.getByText('2 productos')).toBeInTheDocument()
  })

  it('mantiene el formulario y el valor ante 400 y 409', async () => {
    listCharacteristics.mockResolvedValue([])
    createCharacteristic.mockRejectedValueOnce({ status: 400, message: 'Dato inválido', fieldErrors: [{ field: 'nombre', message: 'Nombre rechazado' }] }).mockRejectedValueOnce({ status: 409, message: 'Nombre duplicado' })
    const user = userEvent.setup()
    render(<CharacteristicManagementPage />)
    await screen.findByText('No hay características.')
    await user.click(screen.getByRole('button', { name: 'Crear característica' }))
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Color')
    await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
    expect(await screen.findByText('Nombre rechazado')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveValue('Color')
    await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
    expect(await screen.findByText('Nombre duplicado')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveValue('Color')
  })

  it('un 404 en detalle o guardado exige actualizar la lista', async () => {
    listCharacteristics.mockResolvedValue([{ id: 1, nombre: 'Color', cantidadProductos: 0 }])
    getCharacteristic.mockRejectedValueOnce({ status: 404, message: 'No existe' }).mockResolvedValueOnce({ id: 1, nombre: 'Color' })
    updateCharacteristic.mockRejectedValue({ status: 404, message: 'No existe' })
    const user = userEvent.setup()
    render(<CharacteristicManagementPage />)
    await user.click(await screen.findByRole('button', { name: 'Editar Color' }))
    expect(await screen.findByText('La característica ya no está disponible. Actualizá la lista.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Actualizar lista' }))
    await screen.findByRole('button', { name: 'Editar Color' })
    await user.click(screen.getByRole('button', { name: 'Editar Color' }))
    await screen.findByRole('textbox', { name: 'Nombre' })
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByRole('button', { name: 'Actualizar lista' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeDisabled()
  })

  it('cancelar y Escape no escriben', async () => {
    listCharacteristics.mockResolvedValue([])
    const user = userEvent.setup()
    render(<CharacteristicManagementPage />)
    await screen.findByText('No hay características.')
    await user.click(screen.getByRole('button', { name: 'Crear característica' }))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Crear característica' }))
    fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(createCharacteristic).not.toHaveBeenCalled()
    expect(updateCharacteristic).not.toHaveBeenCalled()
  })

  it('red o 5xx bloquea mutaciones hasta una consulta correcta', async () => {
    listCharacteristics.mockResolvedValueOnce([]).mockRejectedValueOnce(new Error('Sin conexión')).mockResolvedValueOnce([])
    createCharacteristic.mockRejectedValue({ message: 'Sin conexión' })
    const user = userEvent.setup()
    render(<CharacteristicManagementPage />)
    await screen.findByText('No hay características.')
    await user.click(screen.getByRole('button', { name: 'Crear característica' }))
    await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Color')
    await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
    expect(await screen.findByText(/Consultá el estado actual antes de enviar otro cambio/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar característica' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Consultar estado actual' }))
    await waitFor(() => expect(listCharacteristics).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('button', { name: 'Guardar característica' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Consultar estado actual' }))
    expect(await screen.findByText(/Estado actual consultado/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar característica' })).toBeEnabled()
    expect(createCharacteristic).toHaveBeenCalledTimes(1)
  })

  it('un HTTP 5xx también exige consultar antes de editar', async () => {
    listCharacteristics.mockResolvedValue([{ id: 8, nombre: 'Peso', cantidadProductos: 1 }])
    getCharacteristic.mockResolvedValue({ id: 8, nombre: 'Peso', cantidadProductos: 1 })
    updateCharacteristic.mockRejectedValue({ status: 503, message: 'Servidor no disponible' })
    const user = userEvent.setup()
    render(<CharacteristicManagementPage />)
    await user.click(await screen.findByRole('button', { name: 'Editar Peso' }))
    await screen.findByRole('textbox', { name: 'Nombre' })
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByText('Servidor no disponible')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Editar Peso' })).toBeDisabled()
    expect(updateCharacteristic).toHaveBeenCalledTimes(1)
  })

  it('renueva el plazo de avisos y limpia el temporizador al desmontar', async () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout')
    const clearTimeoutSpy = vi.spyOn(window, 'clearTimeout')
    listCharacteristics.mockResolvedValue([])
    createCharacteristic.mockResolvedValue({ id: 1, nombre: 'Color' })
    const user = userEvent.setup()
    const view = render(<CharacteristicManagementPage />)
    try {
      await screen.findByText('No hay características.')
      await user.click(screen.getByRole('button', { name: 'Crear característica' }))
      await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Color')
      await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
      expect(await screen.findByText('Característica creada.')).toBeInTheDocument()
      expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), 4000)
      await screen.findByText('No hay características.')
      await user.click(screen.getByRole('button', { name: 'Crear característica' }))
      await user.type(screen.getByRole('textbox', { name: 'Nombre' }), 'Peso')
      await user.click(screen.getByRole('button', { name: 'Guardar característica' }))
      expect(await screen.findByText('Característica creada.')).toBeInTheDocument()
      const noticeTimers = setTimeoutSpy.mock.calls.flatMap((args, index) => args[1] === 4000 ? [setTimeoutSpy.mock.results[index].value] : [])
      expect(noticeTimers).toHaveLength(2)
      expect(clearTimeoutSpy).toHaveBeenCalledWith(noticeTimers[0])
      const latestNoticeCallback = setTimeoutSpy.mock.calls.filter((args) => args[1] === 4000).at(-1)[0]
      act(() => latestNoticeCallback())
      expect(screen.queryByText('Característica creada.')).not.toBeInTheDocument()
      view.unmount()
      expect(clearTimeoutSpy).toHaveBeenCalledWith(noticeTimers[1])
    } finally {
      setTimeoutSpy.mockRestore()
      clearTimeoutSpy.mockRestore()
    }
  })
})
