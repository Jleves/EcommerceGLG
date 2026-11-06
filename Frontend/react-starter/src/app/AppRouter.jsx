import { Navigate, Route, Routes } from 'react-router-dom'
import { CreateUserPage } from '../admin/pages/CreateUserPage.jsx'
import { UserDetailPage } from '../admin/pages/UserDetailPage.jsx'
import { UserListPage } from '../admin/pages/UserListPage.jsx'
import { SubscriptionListPage } from '../admin/pages/SubscriptionListPage.jsx'
import { ForgotPasswordPage } from '../auth/pages/ForgotPasswordPage.jsx'
import { LoginPage } from '../auth/pages/LoginPage.jsx'
import { MfaLoginPage } from '../auth/pages/MfaLoginPage.jsx'
import { ResetPasswordPage } from '../auth/pages/ResetPasswordPage.jsx'
import { CategoryManagementPage } from '../catalog/categories/pages/CategoryManagementPage.jsx'
import { CharacteristicManagementPage } from '../catalog/characteristics/pages/CharacteristicManagementPage.jsx'
import { ProductManagementPage } from '../catalog/products/pages/ProductManagementPage.jsx'
import { AppLayout } from './AppLayout.jsx'
import { ProfilePage } from './ProfilePage.jsx'
import { ProtectedRoute } from './ProtectedRoute.jsx'
import { PublicOnlyRoute } from './PublicOnlyRoute.jsx'
import { PublicLayout } from '../public/PublicLayout.jsx'
import { HomePage } from '../public/HomePage.jsx'
import { ContactPage } from '../public/ContactPage.jsx'
import { CatalogPage } from '../public/CatalogPage.jsx'
import { ProductDetailPage } from '../public/ProductDetailPage.jsx'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/contacto" element={<ContactPage />} />
        <Route path="/catalog" element={<CatalogPage />} />
        <Route path="/products/:id" element={<ProductDetailPage />} />
      </Route>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/login/mfa" element={<MfaLoginPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<AppLayout />}>
          <Route index element={<Navigate to="profile" replace />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="catalog/categories" element={<CategoryManagementPage />} />
          <Route path="catalog/characteristics" element={<CharacteristicManagementPage />} />
          <Route path="catalog/products" element={<ProductManagementPage />} />
          <Route element={<ProtectedRoute allowedRoles={['ADMIN', 'SUPER_ADMIN']} />}>
            <Route path="admin/subscriptions/email" element={<SubscriptionListPage channel="EMAIL" />} />
            <Route path="admin/subscriptions/whatsapp" element={<SubscriptionListPage channel="WHATSAPP" />} />
          </Route>
          <Route element={<ProtectedRoute requiredRole="SUPER_ADMIN" />}>
            <Route path="admin/users" element={<UserListPage />} />
            <Route path="admin/users/new" element={<CreateUserPage />} />
            <Route path="admin/users/:id" element={<UserDetailPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
