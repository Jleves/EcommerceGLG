import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/context/useAuth.js'
import { LoadingScreen } from './LoadingScreen.jsx'

export function PublicOnlyRoute() {
  const { status, isAuthenticated } = useAuth()
  if (status === 'loading') return <LoadingScreen />
  if (status === 'mfa-required') return <Navigate to="/login/mfa" replace />
  return isAuthenticated ? <Navigate to="/app/profile" replace /> : <Outlet />
}
