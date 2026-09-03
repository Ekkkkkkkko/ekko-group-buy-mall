const API_BASE = import.meta.env?.VITE_PAY_MALL_API_BASE || '/mall-api'
const SUCCESS_CODE = '0000'

export class MallApiError extends Error {
  constructor(message, code = 'NETWORK_ERROR') {
    super(message)
    this.name = 'MallApiError'
    this.code = code
  }
}

async function request(path, options = {}) {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 5000)

  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
    })

    if (!response.ok) {
      throw new MallApiError(`服务响应异常（${response.status}）`, String(response.status))
    }

    const payload = await response.json()
    if (payload.code !== SUCCESS_CODE) {
      throw new MallApiError(payload.info || '业务处理失败', payload.code)
    }

    return payload.data
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new MallApiError('服务连接超时，请稍后重试', 'TIMEOUT')
    }
    if (error instanceof MallApiError) throw error
    throw new MallApiError('暂时无法连接设备服务', 'NETWORK_ERROR')
  } finally {
    window.clearTimeout(timeout)
  }
}

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function queryProducts() {
  return request('/api/v1/products')
}

export function queryProductDetail(productId, userId) {
  const query = userId ? `?userId=${encodeURIComponent(userId)}` : ''
  return request(`/api/v1/products/${encodeURIComponent(productId)}${query}`)
}

export function createPayOrder(order, token) {
  return request('/api/v1/alipay/create_pay_order', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(order),
  })
}

export function queryUserOrders(userId, token, lastId = null, pageSize = 10) {
  return request('/api/v1/alipay/query_user_order_list', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ userId, lastId, pageSize }),
  })
}

export function refundOrder(userId, orderId, token) {
  return request('/api/v1/alipay/refund_order', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ userId, orderId }),
  })
}

export function loginMallAccount({ username, password }) {
  return request('/api/v1/login/account/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function registerMallAccount({ username, password }) {
  return request('/api/v1/login/account/register', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function logoutMallAccount(token) {
  return request('/api/v1/login/account/logout', {
    method: 'POST',
    headers: authHeaders(token),
  })
}

export function changeMallAccountPassword({ currentPassword, newPassword }, token) {
  return request('/api/v1/login/account/password', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

export function createLoginTicket(sceneStr) {
  return request(
    `/api/v1/login/weixin_qrcode_ticket_scene?sceneStr=${encodeURIComponent(sceneStr)}`,
  )
}

export function pollLoginTicket(ticket, sceneStr) {
  return request(
    `/api/v1/login/check_login_scene?ticket=${encodeURIComponent(ticket)}&sceneStr=${encodeURIComponent(sceneStr)}`,
  )
}

export function weixinQrUrl(ticket) {
  return `https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=${encodeURIComponent(ticket)}`
}
