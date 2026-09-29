const authBase = '/api/auth'

export const ENDPOINTS = Object.freeze({
  ADMIN: Object.freeze({
    USERS: '/api/admin/users',
    USER: (id) => `/api/admin/users/${encodeURIComponent(id)}`,
    USER_EMAIL: (id) => `/api/admin/users/${encodeURIComponent(id)}/email`,
    USER_DEACTIVATE: (id) => `/api/admin/users/${encodeURIComponent(id)}/deactivate`,
    USER_REACTIVATE: (id) => `/api/admin/users/${encodeURIComponent(id)}/reactivate`,
  }),
  AUTH: Object.freeze({
    CHANGE_PASSWORD: `${authBase}/change-password`,
    CSRF: `${authBase}/csrf`,
    LOGIN: `${authBase}/login`,
    REFRESH: `${authBase}/refresh`,
    LOGOUT: `${authBase}/logout`,
    MFA_CANCEL: `${authBase}/mfa/cancel`,
    MFA_VERIFY: `${authBase}/mfa/login/verify`,
    MFA_RESEND: `${authBase}/mfa/login/resend`,
    MFA_STATUS: `${authBase}/mfa/status`,
    MFA_ENABLE_START: `${authBase}/mfa/enable/start`,
    MFA_ENABLE_CONFIRM: `${authBase}/mfa/enable/confirm`,
    MFA_DISABLE_START: `${authBase}/mfa/disable/start`,
    MFA_DISABLE_CONFIRM: `${authBase}/mfa/disable/confirm`,
    MFA_SETTINGS_RESEND: `${authBase}/mfa/settings/resend`,
    ME: `${authBase}/me`,
    FORGOT_PASSWORD: `${authBase}/forgot-password`,
    RESET_PASSWORD: `${authBase}/reset-password`,
    VALIDATE_RESET_TOKEN: (token) =>
      `${authBase}/reset-password/validate?${new URLSearchParams({ token })}`,
  }),
})
