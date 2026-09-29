import { chromium, expect } from '@playwright/test'
import { createServer } from 'vite'

// Started by AdminUserBrowserIT against disposable MySQL and Mailpit.
const server = await createServer({ server: { host: 'localhost', port: 0, proxy: { '/api': { target: process.env.ADMIN_API_URL, changeOrigin: false } } } })
await server.listen()
const base = server.resolvedUrls.local[0]
let browser
try {
  browser = await chromium.launch({ headless: true, ...(process.env.ADMIN_BROWSER_CHANNEL ? { channel: process.env.ADMIN_BROWSER_CHANNEL } : {}) })
  const contexts = {}
  const pages = {}
  for (const role of ['user', 'admin', 'super_admin']) {
    contexts[role] = await browser.newContext()
    pages[role] = await contexts[role].newPage()
    await pages[role].goto(`${base}login`)
    await pages[role].getByLabel('Email', { exact: true }).fill(`${role}@example.com`)
    await pages[role].getByLabel('Contraseña', { exact: true }).fill('browser-test-password')
    await pages[role].getByRole('button', { name: 'Ingresar', exact: true }).click()
    await expect(pages[role].getByRole('button', { name: 'Cerrar sesión' })).toBeVisible()
    const response = await contexts[role].request.get(`${base}api/admin/users`)
    expect(response.status()).toBe(role === 'super_admin' ? 200 : 403)
    if (role !== 'super_admin') {
      await pages[role].goto(`${base}app/admin/users`)
      await expect(pages[role].getByRole('heading', { name: 'Usuarios' })).toHaveCount(0)
    }
  }

  const admin = pages.super_admin
  const withoutCsrf = await contexts.super_admin.request.post(`${base}api/admin/users`, {
    data: { email: 'csrf-blocked@example.com', password: 'browser-test-password', role: 'USER' },
  })
  expect(withoutCsrf.status()).toBe(403)
  await admin.goto(`${base}app/admin/users/new`)
  await admin.getByLabel('Email', { exact: true }).fill('managed@example.com')
  await admin.getByLabel('Contraseña inicial').fill('browser-test-password')
  await admin.getByRole('button', { name: 'Crear cuenta' }).click()
  await expect(admin.getByText('Cuenta creada: managed@example.com. Rol: USER.')).toBeVisible()
  await admin.goto(`${base}app/admin/users`)
  await admin.getByRole('link', { name: /managed@example.com/ }).click()
  await expect(admin.getByRole('heading', { name: 'Detalle de usuario' })).toBeVisible()
  const detailUrl = admin.url()

  const targetContext = await browser.newContext()
  const target = await targetContext.newPage()
  await target.goto(`${base}login`)
  await target.getByLabel('Email', { exact: true }).fill('managed@example.com')
  await target.getByLabel('Contraseña', { exact: true }).fill('browser-test-password')
  await target.getByRole('button', { name: 'Ingresar', exact: true }).click()
  await expect(target.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible()
  await target.goto(`${base}app/profile`)
  await target.getByLabel('Contraseña actual para verificación en dos pasos', { exact: true }).fill('browser-test-password')
  await target.getByRole('button', { name: 'Activar verificación en dos pasos', exact: true }).click()
  const seen = new Set()
  async function messageCode() {
    let code
    await expect.poll(async () => {
      const list = await (await fetch(`${process.env.ADMIN_MAIL_URL}/api/v1/messages`)).json()
      for (const item of list.messages) {
        if (seen.has(item.ID) || !item.To.some(to => to.Address === 'managed@example.com')) continue
        const message = await (await fetch(`${process.env.ADMIN_MAIL_URL}/api/v1/message/${item.ID}`)).json()
        seen.add(item.ID)
        const match = (message.Text + message.HTML).match(/\b\d{6}\b/)
        if (match) { code = match[0]; return true }
      }
      return false
    }, { timeout: 10000 }).toBe(true)
    return code
  }
  await target.getByLabel('Código para cambiar la verificación en dos pasos').fill(await messageCode())
  await target.getByRole('button', { name: 'Confirmar activación', exact: true }).click()
  await expect(target.getByLabel('Contraseña', { exact: true })).toBeVisible()

  await admin.goto(detailUrl)
  await admin.getByLabel('Nuevo email').fill('blocked@example.com')
  await admin.getByRole('button', { name: 'Guardar email' }).click()
  await expect(admin.getByText('El titular debe desactivar MFA antes de cambiar su email.')).toBeVisible()
  await admin.getByRole('button', { name: 'Desactivar cuenta' }).click()
  await admin.getByRole('button', { name: 'Confirmar baja' }).click()
  await expect(admin.getByText('Inactiva', { exact: true })).toBeVisible()
  expect((await targetContext.request.get(`${base}api/auth/me`)).status()).toBe(401)
  await admin.getByRole('button', { name: 'Reactivar cuenta' }).click()
  await admin.getByRole('button', { name: 'Confirmar reactivación' }).click()
  await expect(admin.getByText('Activa', { exact: true })).toBeVisible()
  await expect(admin.getByText('Activado', { exact: true })).toBeVisible()
  expect((await targetContext.request.get(`${base}api/auth/me`)).status()).toBe(401)

  await target.goto(`${base}login`)
  await target.getByLabel('Email', { exact: true }).fill('managed@example.com')
  await target.getByLabel('Contraseña', { exact: true }).fill('browser-test-password')
  await target.getByRole('button', { name: 'Ingresar', exact: true }).click()
  await target.getByLabel('Código de verificación').fill(await messageCode())
  await target.getByRole('button', { name: 'Confirmar código', exact: true }).click()
  await expect(target.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible()
  await target.goto(`${base}app/profile`)
  await target.getByLabel('Contraseña actual para verificación en dos pasos', { exact: true }).fill('browser-test-password')
  await target.getByRole('button', { name: 'Desactivar verificación en dos pasos', exact: true }).click()
  await target.getByLabel('Código para cambiar la verificación en dos pasos').fill(await messageCode())
  await target.getByRole('button', { name: 'Confirmar desactivación', exact: true }).click()
  await expect(target.getByLabel('Contraseña', { exact: true })).toBeVisible()

  await admin.goto(detailUrl)
  await admin.getByLabel('Nuevo email').fill('changed@example.com')
  await admin.getByRole('button', { name: 'Guardar email' }).click()
  await expect(admin.getByText('Cambio confirmado por el servidor.')).toBeVisible()
  await expect(admin.getByText('changed@example.com', { exact: true })).toBeVisible()
  expect((await targetContext.request.get(`${base}api/auth/me`)).status()).toBe(401)
  await admin.getByLabel('Nuevo email').fill('managed@example.com')
  let mutations = 0
  await admin.route('**/api/admin/users/*/email', async route => {
    mutations++
    const response = await route.fetch()
    expect(response.status()).toBe(200)
    await route.abort('failed')
  })
  await admin.getByRole('button', { name: 'Guardar email' }).click()
  await expect(admin.getByRole('button', { name: 'Consultar estado actual' })).toBeVisible()
  expect(mutations).toBe(1)
  await admin.unrouteAll()
  await admin.getByRole('button', { name: 'Consultar estado actual' }).click()
  await expect(admin.getByText('Estado actual consultado. Esta consulta no confirma si la acción anterior se aplicó.')).toBeVisible()
  await expect(admin.getByText('managed@example.com', { exact: true })).toBeVisible()
  console.log('Administrative browser lifecycle passed')
  await targetContext.close()
  for (const context of Object.values(contexts)) await context.close()
} finally {
  await browser?.close()
  await server.close()
}
