const API_BASE = import.meta.env?.VITE_GROUP_CHAT_API_BASE || '/chat-api'
export const ADMIN_TOKEN_KEY = 'linknest_knowledge_admin_token'
export const CHAT_CLIENT_ID_KEY = 'linknest_customer_service_client_id'

const STREAM_IDLE_TIMEOUT_MS = 180_000

export class KnowledgeApiError extends Error {
  constructor(message, status = 0) {
    super(message)
    this.name = 'KnowledgeApiError'
    this.status = status
  }
}

async function readErrorMessage(response) {
  try {
    const payload = await response.json()
    return payload.message || payload.error || `服务响应异常（${response.status}）`
  } catch {
    return `服务响应异常（${response.status}）`
  }
}

async function request(path, options = {}, timeoutMs = 60_000) {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs)
  const isFormData = options.body instanceof FormData

  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        ...(!isFormData && options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
    })

    if (!response.ok) {
      throw new KnowledgeApiError(await readErrorMessage(response), response.status)
    }
    if (response.status === 204) return null
    return response.json()
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new KnowledgeApiError('知识库服务处理超时，请稍后重试')
    }
    if (error instanceof KnowledgeApiError) throw error
    throw new KnowledgeApiError('暂时无法连接知识库服务')
  } finally {
    window.clearTimeout(timeout)
  }
}

export function askKnowledgeBase(question) {
  return request('/api/v1/chat', {
    method: 'POST',
    body: JSON.stringify({ question }),
  })
}

export function getOrCreateChatClientId(storage = globalThis.localStorage) {
  try {
    const existing = storage?.getItem?.(CHAT_CLIENT_ID_KEY)
    if (existing) return existing
  } catch {
    // 浏览器禁用存储时继续使用本次页面生成的标识。
  }

  const randomId = globalThis.crypto?.randomUUID?.()
  const clientId = `web-${randomId || `${Date.now()}-${Math.random().toString(16).slice(2)}`}`
  try {
    storage?.setItem?.(CHAT_CLIENT_ID_KEY, clientId)
  } catch {
    // localStorage 不可用不应阻断客服对话。
  }
  return clientId
}

function dispatchSseBlock(block, onPayload) {
  if (!block.trim()) return
  let eventName = ''
  const dataLines = []

  for (const line of block.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue
    const colon = line.indexOf(':')
    const field = colon < 0 ? line : line.slice(0, colon)
    let value = colon < 0 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') eventName = value
    if (field === 'data') dataLines.push(value)
  }

  if (!dataLines.length) return
  const rawData = dataLines.join('\n')
  let payload
  try {
    payload = JSON.parse(rawData)
  } catch {
    if (!eventName) {
      throw new KnowledgeApiError('客服流式响应格式错误')
    }
    payload = { type: eventName, data: rawData }
  }
  onPayload(payload, eventName)
}

function drainSseBuffer(buffer, onPayload, flush = false) {
  let remaining = buffer
  let boundary = remaining.match(/\r?\n\r?\n/)
  while (boundary?.index != null) {
    const block = remaining.slice(0, boundary.index)
    remaining = remaining.slice(boundary.index + boundary[0].length)
    dispatchSseBlock(block, onPayload)
    boundary = remaining.match(/\r?\n\r?\n/)
  }
  if (flush && remaining.trim()) {
    dispatchSseBlock(remaining, onPayload)
    return ''
  }
  return remaining
}

/**
 * POST SSE 客服流。后端事件顺序为 PROGRESS → REFERENCE → ANSWER... → COMPLETE，
 * 不能使用只支持 GET 的原生 EventSource，因此通过 fetch + ReadableStream 增量解析。
 */
export async function askKnowledgeBaseStream(
  question,
  {
    conversationId,
    clientId = getOrCreateChatClientId(),
    onProgress,
    onReference,
    onAnswer,
    onComplete,
    signal,
  } = {},
) {
  const controller = new AbortController()
  let idleTimer
  let timedOut = false
  const abortFromCaller = () => controller.abort(signal?.reason)
  const resetIdleTimer = () => {
    globalThis.clearTimeout(idleTimer)
    idleTimer = globalThis.setTimeout(() => {
      timedOut = true
      controller.abort()
    }, STREAM_IDLE_TIMEOUT_MS)
  }

  if (signal?.aborted) abortFromCaller()
  else signal?.addEventListener?.('abort', abortFromCaller, { once: true })
  resetIdleTimer()

  let answer = ''
  let sources = []
  let completedResponse = null

  const handlePayload = (payload, eventName) => {
    const type = String(payload?.type || eventName || '').toUpperCase()
    const data = payload?.data
    if (type === 'PROGRESS') {
      onProgress?.(String(data || ''))
      return
    }
    if (type === 'REFERENCE') {
      sources = Array.isArray(data) ? data : []
      onReference?.(sources)
      return
    }
    if (type === 'ANSWER') {
      const delta = String(data || '')
      answer += delta
      onAnswer?.(delta)
      return
    }
    if (type === 'COMPLETE') {
      completedResponse = data && typeof data === 'object'
        ? data
        : { conversationId: '', answer, sources }
      onComplete?.(completedResponse)
      return
    }
    if (type === 'ERROR') {
      throw new KnowledgeApiError(String(data || '客服回答生成失败'))
    }
  }

  try {
    const response = await fetch(`${API_BASE}/api/v1/chat/stream`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        question,
        clientId,
        ...(conversationId ? { conversationId } : {}),
      }),
    })

    if (!response.ok) {
      throw new KnowledgeApiError(await readErrorMessage(response), response.status)
    }
    if (!response.body?.getReader) {
      throw new KnowledgeApiError('当前浏览器不支持客服流式响应')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      resetIdleTimer()
      buffer += decoder.decode(value, { stream: true })
      buffer = drainSseBuffer(buffer, handlePayload)
    }
    buffer += decoder.decode()
    drainSseBuffer(buffer, handlePayload, true)

    if (completedResponse) return completedResponse
    if (answer) return { conversationId: conversationId || '', answer, sources }
    throw new KnowledgeApiError('客服流式响应提前结束')
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new KnowledgeApiError(timedOut ? '客服流式响应超时，请稍后重试' : '客服请求已取消')
    }
    if (error instanceof KnowledgeApiError) throw error
    throw new KnowledgeApiError('暂时无法连接知识库服务')
  } finally {
    globalThis.clearTimeout(idleTimer)
    signal?.removeEventListener?.('abort', abortFromCaller)
  }
}

export function loginKnowledgeAdmin({ username, password }) {
  return request('/api/v1/admin/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function uploadKnowledgeDocument(
  { file, title, productModel, chunkStrategy = 'SMART' },
  adminToken,
) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', title)
  if (productModel) formData.append('productModel', productModel)
  if (chunkStrategy) formData.append('chunkStrategy', chunkStrategy)

  return request(
    '/api/v1/documents',
    {
      method: 'POST',
      body: formData,
      headers: {
        Authorization: `Bearer ${adminToken}`,
      },
    },
    60_000,
  )
}

export function queryKnowledgeDocument(documentId, adminToken) {
  return request(`/api/v1/documents/${encodeURIComponent(documentId)}`, {
    headers: {
      Authorization: `Bearer ${adminToken}`,
    },
  })
}

export function deleteKnowledgeDocument(documentId, adminToken) {
  return request(`/api/v1/documents/${encodeURIComponent(documentId)}`, {
    method: 'DELETE',
    headers: {
      Authorization: `Bearer ${adminToken}`,
    },
  })
}
