import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { ensureCsrfToken } from '../../services/apiClient.js'
import { subscribeToSessionExpired } from '../../services/sessionEvents.js'
import * as authService from '../services/authService.js'
import { AuthContext } from './AuthContext.js'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState('loading')
  const [authNotice, setAuthNotice] = useState(null)
  const [mfa, setMfa] = useState(null)
  const [mfaBusy, setMfaBusy] = useState(false)
  const [mfaUncertain, setMfaUncertain] = useState(false)
  const generation = useRef(0)
  const operation = useRef(false)
  const location = useLocation()

  const clearMfa = useCallback(() => {
    generation.current += 1
    setMfa(null)
    setMfaUncertain(false)
  }, [])

  useEffect(() => subscribeToSessionExpired(() => {
    clearMfa()
    setUser(null)
    setStatus('anonymous')
  }), [clearMfa])

  useEffect(() => {
    let active = true
    const attempt = generation.current
    async function restoreSession() {
      try {
        await ensureCsrfToken()
        const currentUser = await authService.getCurrentUser()
        if (active && attempt === generation.current) {
          setUser(currentUser)
          setStatus('authenticated')
        }
      } catch {
        if (active && attempt === generation.current) {
          setUser(null)
          setStatus('anonymous')
        }
      }
    }
    restoreSession()
    return () => { active = false }
  }, [])

  const login = useCallback(async (credentials, destination = '/app/profile') => {
    const attempt = ++generation.current
    const result = await authService.login(credentials)
    if (attempt !== generation.current) return null
    setAuthNotice(null)
    setMfaUncertain(false)
    if (result.status === 'MFA_REQUIRED') {
      setUser(null)
      setMfa({ ...result, destination })
      setStatus('mfa-required')
    } else {
      setMfa(null)
      setUser(result.user)
      setStatus('authenticated')
    }
    return result
  }, [])

  const cancelMfa = useCallback(async () => {
    clearMfa()
    setUser(null)
    setStatus('anonymous')
    const attempt = generation.current
    try {
      await authService.cancelMfa()
    } catch {
      if (attempt === generation.current) setAuthNotice('mfa-cancel-unconfirmed')
    }
  }, [clearMfa])

  // Do not cancel in unmount cleanup: StrictMode and success also unmount pages.
  useEffect(() => {
    if (status === 'mfa-required' && !['/login', '/login/mfa'].includes(location.pathname)
        && !location.pathname.startsWith('/app')) void cancelMfa()
  }, [location.pathname, status, cancelMfa])

  const runMfa = useCallback(async (action, code) => {
    if (operation.current || !mfa || (mfaUncertain && action !== 'recover')) return
    operation.current = true
    setMfaBusy(true)
    const attempt = generation.current
    const authenticate = (currentUser) => {
      if (attempt !== generation.current) return
      clearMfa()
      setUser(currentUser)
      setStatus('authenticated')
    }
    const recover = async () => {
      try {
        authenticate(await authService.getCurrentUser({ retryAuth: false }))
      } catch (error) {
        if (attempt !== generation.current) return
        if (error.status === 401) {
          clearMfa()
          setStatus('anonymous')
          setAuthNotice('mfa-restart')
        } else {
          setMfaUncertain(true)
          throw error
        }
      }
    }
    try {
      if (action === 'recover') return await recover()
      if (action === 'resend') {
        const timing = await authService.resendMfa()
        if (attempt === generation.current) setMfa((pending) => ({ ...pending, ...timing }))
      } else {
        try {
          const result = await authService.verifyMfa(code)
          if (result.status !== 'AUTHENTICATED') throw new Error('Respuesta inesperada')
          authenticate(result.user)
        } catch (error) {
          if (attempt !== generation.current) return
          if (!error.status || error.status >= 500) {
            setMfaUncertain(true)
            await recover()
          } else throw error
        }
      }
    } finally {
      operation.current = false
      setMfaBusy(false)
    }
  }, [mfa, mfaUncertain, clearMfa])

  const logout = useCallback(async () => {
    clearMfa()
    await authService.logout()
    setUser(null)
    setStatus('anonymous')
  }, [clearMfa])

  const changePassword = useCallback(async (passwords) => {
    await authService.changePassword(passwords)
    clearMfa()
    setAuthNotice('password-changed')
    setUser(null)
    setStatus('anonymous')
  }, [clearMfa])

  // The settings confirmation already revoked sessions and deleted cookies in the backend.
  const endSession = useCallback((notice) => {
    clearMfa()
    setAuthNotice(notice)
    setUser(null)
    setStatus('anonymous')
  }, [clearMfa])

  const value = useMemo(() => ({
    user, status, authNotice, mfa, mfaBusy, mfaUncertain,
    isAuthenticated: status === 'authenticated',
    login, logout, changePassword, runMfa, cancelMfa, endSession,
  }), [user, status, authNotice, mfa, mfaBusy, mfaUncertain, login, logout, changePassword, runMfa, cancelMfa, endSession])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
