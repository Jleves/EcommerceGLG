import { chromium, expect } from '@playwright/test'
import { createServer } from 'vite'

// Started by MfaBrowserIT: real HTTP, MySQL and SMTP, no mocked API responses.
// Preserve Host so API requests remain same-origin, like a reverse proxy deployment.
const server = await createServer({ server: { host: 'localhost', port: 0, proxy: { '/api': { target: process.env.MFA_API_URL, changeOrigin: false } } } })
await server.listen()
const base = server.resolvedUrls.local[0]
let browser
try {
  browser = await chromium.launch({ headless: true, ...(process.env.MFA_BROWSER_CHANNEL ? { channel: process.env.MFA_BROWSER_CHANNEL } : {}) })
  for (const role of ['user', 'admin', 'super_admin']) {
    const context = await browser.newContext()
    const page = await context.newPage()
    const email = `${role}@example.com`
    const seen = new Set()
    async function messageCode() {
      let code
      await expect.poll(async () => {
        const list = await (await fetch(`${process.env.MFA_MAIL_URL}/api/v1/messages`)).json()
        for (const item of list.messages) {
          if (seen.has(item.ID) || !item.To.some(to => to.Address === email)) continue
          const message = await (await fetch(`${process.env.MFA_MAIL_URL}/api/v1/message/${item.ID}`)).json()
          seen.add(item.ID)
          const match = (message.Text + message.HTML).match(/\b\d{6}\b/)
          if (match) { code = match[0]; return true }
        }
        return false
      }, { timeout: 10000 }).toBe(true)
      return code
    }
    async function login(mfa) {
      await page.getByLabel('Email', { exact: true }).fill(email)
      await page.getByLabel('Contraseña', { exact: true }).fill('browser-test-password')
      await page.getByRole('button', { name: 'Ingresar', exact: true }).click()
      if (mfa) {
        await expect(page.getByLabel('Código de verificación')).toBeVisible()
        expect((await context.request.get(`${base}api/auth/me`)).status()).toBe(401)
        const cookies = await context.cookies(`${base}api/auth/mfa/login/verify`)
        const pending = cookies.find(cookie => cookie.name === 'MFA_CHALLENGE')
        expect(pending.httpOnly).toBe(true)
        expect(pending.path).toBe('/api/auth/mfa')
        expect(cookies.some(cookie => cookie.name === 'ACCESS_TOKEN')).toBe(false)
        await page.getByLabel('Código de verificación').fill(await messageCode())
        await page.getByRole('button', { name: 'Confirmar código', exact: true }).click()
      }
      await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible()
      expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
    }
    async function configure(enabled) {
      await page.goto(`${base}app/profile`)
      await page.getByLabel('Contraseña actual para verificación en dos pasos', { exact: true }).fill('browser-test-password')
      await page.getByRole('button', { name: enabled ? 'Activar verificación en dos pasos' : 'Desactivar verificación en dos pasos', exact: true }).click()
      await page.getByLabel('Código para cambiar la verificación en dos pasos').fill(await messageCode())
      await page.getByRole('button', { name: enabled ? 'Confirmar activación' : 'Confirmar desactivación', exact: true }).click()
      await expect(page.getByLabel('Contraseña', { exact: true })).toBeVisible()
      expect((await context.request.get(`${base}api/auth/me`)).status()).toBe(401)
      await expect.poll(async () => {
        const list = await (await fetch(`${process.env.MFA_MAIL_URL}/api/v1/messages`)).json()
        return list.messages.filter(item => item.To.some(to => to.Address === email)
          && item.Subject === 'Cambio en la verificación en dos pasos').length
      }).toBe(enabled ? 1 : 2)
    }
    await page.goto(`${base}login`)
    await login(false)
    await configure(true)
    await login(true)
    await page.getByRole('button', { name: 'Cerrar sesión' }).click()
    await expect(page.getByLabel('Contraseña', { exact: true })).toBeVisible()
    await login(true)
    await configure(false)
    await login(false)
    console.log(`MFA browser lifecycle passed: ${role}`)
    await context.close()
  }
} finally {
  await browser?.close()
  await server.close()
}
