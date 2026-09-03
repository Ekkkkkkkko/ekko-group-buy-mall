export const MALL_TOKEN_KEY = 'mangetuan_token'
export const MALL_USER_ID_KEY = 'mangetuan_user_id'
export const MALL_ACCOUNT_NAME_KEY = 'mangetuan_account_name'

export function decodeJwtPayload(jwt) {
  try {
    const payload = jwt.split('.')[1]
    if (!payload) return null
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    return JSON.parse(window.atob(padded))
  } catch {
    return null
  }
}

export function resolveJwtSession(jwt, now = Date.now()) {
  if (!jwt) return null
  if (jwt === 'demo-session') {
    return { userId: 'demo-user', expiresAt: null, authType: 'demo' }
  }
  const payload = decodeJwtPayload(jwt)
  const expiresAt = Number(payload?.exp) * 1000
  if (!payload?.sub || !Number.isFinite(expiresAt) || expiresAt <= now) {
    return null
  }
  return {
    userId: String(payload.sub),
    expiresAt,
    authType: String(payload.authType || ''),
  }
}

export function readStoredMallSession(storage = window.localStorage, now = Date.now()) {
  const token = storage?.getItem?.(MALL_TOKEN_KEY) || ''
  const session = resolveJwtSession(token, now)
  if (!session) return null
  return {
    ...session,
    token,
    accountName: String(storage?.getItem?.(MALL_ACCOUNT_NAME_KEY) || ''),
  }
}

export function storeMallSession(
  { token, accountName = '' },
  storage = window.localStorage,
  now = Date.now(),
) {
  const session = resolveJwtSession(token, now)
  if (!session) return null
  const normalizedAccountName = String(accountName || '').trim()
  storage.setItem(MALL_TOKEN_KEY, token)
  storage.setItem(MALL_USER_ID_KEY, session.userId)
  if (normalizedAccountName) storage.setItem(MALL_ACCOUNT_NAME_KEY, normalizedAccountName)
  else storage.removeItem(MALL_ACCOUNT_NAME_KEY)
  return { ...session, token, accountName: normalizedAccountName }
}

export function isMallAuthenticationError(error) {
  return ['1006', '401'].includes(String(error?.code || ''))
}

export function clearStoredMallSession(storage = window.localStorage) {
  storage.removeItem(MALL_TOKEN_KEY)
  storage.removeItem(MALL_USER_ID_KEY)
  storage.removeItem(MALL_ACCOUNT_NAME_KEY)
}
