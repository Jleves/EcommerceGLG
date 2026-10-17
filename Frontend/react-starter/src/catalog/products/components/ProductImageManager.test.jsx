import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { deleteProductImage, listProductImages, reorderProductImages, setPrimaryProductImage, uploadProductImage } from '../services/productService.js'
import { ProductImageManager } from './ProductImageManager.jsx'

vi.mock('../services/productService.js', () => ({
  listProductImages: vi.fn(), uploadProductImage: vi.fn(), setPrimaryProductImage: vi.fn(),
  reorderProductImages: vi.fn(), deleteProductImage: vi.fn(),
}))

const product = { id: 7, nombre: 'Arena' }
const first = { id: 11, position: 1, primary: true, thumbnailUrl: 'https://cdn.test/11.webp', detailUrl: 'https://cdn.test/11-detail.webp' }
const second = { id: 12, position: 2, primary: false, thumbnailUrl: 'https://cdn.test/12.webp', detailUrl: 'https://cdn.test/12-detail.webp' }

describe('ProductImageManager', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listProductImages.mockResolvedValue([first, second])
    uploadProductImage.mockResolvedValue({})
    setPrimaryProductImage.mockResolvedValue({})
    reorderProductImages.mockResolvedValue([])
    deleteProductImage.mockResolvedValue(null)
  })

  it('muestra galería y carga dentro del mismo diálogo accesible', async () => {
    const user = userEvent.setup()
    listProductImages.mockResolvedValueOnce([first, second]).mockResolvedValueOnce([first, second, { ...second, id: 13, position: 3 }])
    render(<ProductImageManager product={product} onClose={vi.fn()} />)
    const dialog = screen.getByRole('dialog', { name: 'Imágenes de Arena' })
    expect(dialog).toHaveAttribute('open')
    expect(await within(dialog).findByRole('img', { name: 'Foto 1 de Arena' })).toHaveAttribute('src', first.thumbnailUrl)
    expect(within(dialog).getByText('Principal')).toBeInTheDocument()
    expect(within(dialog).getByText(/Podés subir una foto por vez/)).toBeInTheDocument()
    expect(within(dialog).getByText(/Elegí un archivo JPEG, PNG o WebP/)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Cargar foto' })).toBeEnabled()
    expect(within(dialog).getByRole('button', { name: 'Subir imagen' })).toBeDisabled()
    const pickerClick = vi.spyOn(within(dialog).getByLabelText('Nueva foto'), 'click')
    await user.click(within(dialog).getByRole('button', { name: 'Cargar foto' }))
    expect(pickerClick).toHaveBeenCalledTimes(1)
    await user.upload(within(dialog).getByLabelText('Nueva foto'), new File(['x'], 'foto.png', { type: 'image/png' }))
    expect(within(dialog).getByText('Archivo seleccionado: foto.png')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Subir imagen' }))
    await waitFor(() => expect(uploadProductImage).toHaveBeenCalledWith(7, expect.any(File)))
    expect(await within(dialog).findByRole('img', { name: 'Foto 3 de Arena' })).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBe(dialog)
  })

  it('bloquea la sexta foto y convierte en principal a la imagen que sube al primer lugar', async () => {
    const user = userEvent.setup()
    const five = [first, second, ...[13, 14, 15].map((id, index) => ({ ...second, id, position: index + 3 }))]
    listProductImages.mockResolvedValue(five)
    render(<ProductImageManager product={product} onClose={vi.fn()} />)
    await screen.findByText('Se alcanzó el máximo de cinco fotos.')
    expect(screen.getByText(/Subir una imagen al primer lugar también la hace principal/)).toBeInTheDocument()
    expect(screen.getByLabelText('Nueva foto')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cargar foto' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Subir imagen' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Subir imagen a principal' }))
    await waitFor(() => expect(reorderProductImages).toHaveBeenCalledWith(7, [12, 11, 13, 14, 15]))
    await waitFor(() => expect(setPrimaryProductImage).toHaveBeenCalledWith(7, 12))
    expect(reorderProductImages.mock.invocationCallOrder[0]).toBeLessThan(setPrimaryProductImage.mock.invocationCallOrder[0])
    await waitFor(() => expect(screen.getByRole('button', { name: 'Elegir principal para foto 3' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Elegir principal para foto 3' }))
    await waitFor(() => expect(setPrimaryProductImage).toHaveBeenCalledWith(7, 13))
    expect(screen.getByRole('button', { name: 'Bajar imagen a posición 2' })).toBeEnabled()
    expect(uploadProductImage).not.toHaveBeenCalled()
  })

  it('nombra el destino intermedio y no cambia la principal al mover entre posiciones 2 y 3', async () => {
    const user = userEvent.setup()
    listProductImages.mockResolvedValue([first, second, { ...second, id: 13, position: 3 }])
    render(<ProductImageManager product={product} onClose={vi.fn()} />)
    await screen.findByRole('img', { name: 'Foto 3 de Arena' })
    await user.click(screen.getByRole('button', { name: 'Subir imagen a posición 2' }))
    await waitFor(() => expect(reorderProductImages).toHaveBeenCalledWith(7, [11, 13, 12]))
    expect(setPrimaryProductImage).not.toHaveBeenCalled()
  })

  it('exige revisar la galería si se reordena pero falla el cambio de principal', async () => {
    const user = userEvent.setup()
    setPrimaryProductImage.mockRejectedValueOnce({ status: 409, message: 'Conflicto' })
    render(<ProductImageManager product={product} onClose={vi.fn()} />)
    await screen.findByRole('img', { name: 'Foto 2 de Arena' })
    await user.click(screen.getByRole('button', { name: 'Subir imagen a principal' }))
    expect(await screen.findByText('Conflicto')).toBeInTheDocument()
    expect(reorderProductImages).toHaveBeenCalledWith(7, [12, 11])
    expect(screen.getByRole('button', { name: 'Subir imagen a principal' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Consultar estado actual' })).toBeEnabled()
  })

  it('confirma el borrado, muestra el orden restante y libera un lugar para otra carga', async () => {
    const user = userEvent.setup()
    const third = { ...second, id: 13, position: 3 }
    const fourth = { ...second, id: 14, position: 4 }
    const fifth = { ...second, id: 15, position: 5 }
    listProductImages.mockResolvedValueOnce([first, second, third, fourth, fifth])
      .mockResolvedValueOnce([{ ...second, position: 1, primary: true }, { ...third, position: 2 }, { ...fourth, position: 3 }, { ...fifth, position: 4 }])
    render(<ProductImageManager product={product} onClose={vi.fn()} />)
    await screen.findByRole('img', { name: 'Foto 1 de Arena' })
    expect(screen.getByRole('button', { name: 'Cargar foto' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Borrar foto 1' }))
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    expect(screen.getByText(/si era la principal, la primera restante será la nueva principal/)).toBeInTheDocument()
    expect(deleteProductImage).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(deleteProductImage).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Borrar foto 1' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar borrado' }))
    await waitFor(() => expect(deleteProductImage).toHaveBeenCalledWith(7, 11))
    await waitFor(() => expect(screen.getAllByRole('img')).toHaveLength(4))
    expect(screen.getByRole('img', { name: 'Foto 1 de Arena' })).toHaveAttribute('src', second.thumbnailUrl)
    expect(screen.getByText('Principal')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cargar foto' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Subir imagen' })).toBeDisabled()
  })

  it('bloquea otro envío tras resultado incierto hasta una lectura exitosa', async () => {
    const user = userEvent.setup()
    uploadProductImage.mockRejectedValueOnce({ status: 0, message: 'Sin respuesta' })
    render(<ProductImageManager product={product} onClose={vi.fn()} />)
    await screen.findByRole('img', { name: 'Foto 1 de Arena' })
    await user.upload(screen.getByLabelText('Nueva foto'), new File(['x'], 'foto.png', { type: 'image/png' }))
    await user.click(screen.getByRole('button', { name: 'Subir imagen' }))
    expect(await screen.findByText('Sin respuesta')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Subir imagen' })).toBeDisabled()
    listProductImages.mockRejectedValueOnce(new Error('Lectura fallida'))
    await user.click(screen.getByRole('button', { name: 'Consultar estado actual' }))
    expect(await screen.findByText('Lectura fallida')).toBeInTheDocument()
    expect(uploadProductImage).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Reintentar consulta' }))
    expect(await screen.findByText(/Estado actual consultado/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Subir imagen' })).toBeDisabled()
  })

  it('cierra con Escape sin conservar estado y evita cerrar durante una mutación', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    let resolveUpload
    uploadProductImage.mockImplementation(() => new Promise((resolve) => { resolveUpload = resolve }))
    render(<ProductImageManager product={product} onClose={onClose} />)
    const dialog = screen.getByRole('dialog', { name: 'Imágenes de Arena' })
    await screen.findByRole('img', { name: 'Foto 1 de Arena' })
    await user.upload(screen.getByLabelText('Nueva foto'), new File(['x'], 'foto.png', { type: 'image/png' }))
    await user.click(screen.getByRole('button', { name: 'Subir imagen' }))
    expect(screen.getByRole('button', { name: 'Cerrar' })).toBeDisabled()
    fireEvent(dialog, new Event('cancel', { bubbles: true, cancelable: true }))
    expect(onClose).not.toHaveBeenCalled()
    await act(async () => resolveUpload({}))
    fireEvent(dialog, new Event('cancel', { bubbles: true, cancelable: true }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
