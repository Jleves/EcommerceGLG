import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listCategories } from '../services/categoryService.js'
import { CategoryManagementPage } from './CategoryManagementPage.jsx'

vi.mock('../services/categoryService.js', () => ({ listCategories: vi.fn() }))

const categories = [
  { id: 2, nombre: 'Plomería', descripcion: 'Caños', icono: 'plomeria', activo: true },
  { id: 1, nombre: 'Anterior', descripcion: '', icono: 'cemento', activo: false },
]

describe('CategoryManagementPage', () => {
  beforeEach(() => vi.resetAllMocks())

  it('muestra carga, orden recibido, estados e íconos conocidos/desconocidos', async () => {
    listCategories.mockResolvedValue(categories)
    render(<CategoryManagementPage />)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando categorías')
    expect(listCategories).toHaveBeenCalledWith({ signal: expect.any(AbortSignal) })
    expect(await screen.findByText('Plomería')).toBeInTheDocument()
    expect(screen.getByText('Caños')).toBeInTheDocument()
    expect(screen.getByText('ID 2 · Activa')).toBeInTheDocument()
    expect(screen.getByText('ID 1 · Inactiva')).toBeInTheDocument()
    expect(screen.getByText('Ícono desconocido: cemento')).toBeInTheDocument()
    expect(screen.queryByText('Ícono desconocido: plomeria')).not.toBeInTheDocument()
    expect(screen.getAllByRole('listitem').map((item) => item.querySelector('h2').textContent))
      .toEqual(['Plomería', 'Anterior'])
    expect(screen.getAllByRole('listitem')[0].querySelector('svg')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')[1].querySelector('svg')).toBeInTheDocument()
  })

  it('muestra lista vacía', async () => {
    listCategories.mockResolvedValue([])
    render(<CategoryManagementPage />)
    expect(await screen.findByText('No hay categorías.')).toBeInTheDocument()
  })

  it('muestra error y permite reintentar sin mostrar datos obsoletos', async () => {
    listCategories.mockRejectedValueOnce(new Error('Servidor no disponible')).mockResolvedValueOnce(categories)
    render(<CategoryManagementPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Servidor no disponible')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Plomería')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(listCategories).toHaveBeenCalledTimes(2)
  })

  it('aborta e ignora una respuesta tardía al desmontar', async () => {
    let finish
    listCategories.mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    const { unmount } = render(<CategoryManagementPage />)
    const signal = listCategories.mock.calls[0][0].signal
    unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => finish(categories))
    expect(screen.queryByText('Plomería')).not.toBeInTheDocument()
  })
})
