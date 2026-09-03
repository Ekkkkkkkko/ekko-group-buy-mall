export const CHAT_SESSIONS_STORAGE_KEY = 'linknest_customer_service_sessions_v1'
export const MAX_CHAT_SESSIONS = 24
export const MAX_MESSAGES_PER_SESSION = 80

export const CUSTOMER_SERVICE_GREETING =
  '你好，我是联巢智能客服小安。你可以问我路由器参数、无线设置、易展组网和常见故障。'

function createId(prefix = 'chat') {
  const randomId = globalThis.crypto?.randomUUID?.()
  return `${prefix}-${randomId || `${Date.now()}-${Math.random().toString(16).slice(2)}`}`
}

export function createChatSession({ id, now = Date.now() } = {}) {
  return {
    id: id || createId('conversation'),
    conversationId: '',
    title: '新对话',
    createdAt: now,
    updatedAt: now,
    messages: [
      {
        id: createId('message'),
        role: 'assistant',
        text: CUSTOMER_SERVICE_GREETING,
        sources: [],
        createdAt: now,
      },
    ],
  }
}

export function createSessionTitle(question, maxLength = 20) {
  const normalized = String(question || '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!normalized) return '新对话'
  return normalized.length > maxLength
    ? `${normalized.slice(0, maxLength)}…`
    : normalized
}

function normalizeSource(source) {
  if (!source || typeof source !== 'object') return null
  return {
    documentId: source.documentId ?? null,
    chunkId: source.chunkId || '',
    title: source.title || '',
    headingPath: source.headingPath || '',
    images: Array.isArray(source.images)
      ? source.images
          .filter((image) => image && typeof image === 'object')
          .map((image) => ({
            imageId: image.imageId ?? null,
            description: image.description || '',
            url: image.url || '',
          }))
      : [],
  }
}

function normalizeMessage(message) {
  if (!message || typeof message !== 'object') return null
  if (message.role !== 'user' && message.role !== 'assistant') return null
  const text = String(message.text || '').trim()
  if (!text) return null
  return {
    id: String(message.id || createId('message')),
    role: message.role,
    text,
    sources: Array.isArray(message.sources)
      ? message.sources.map(normalizeSource).filter(Boolean)
      : [],
    error: Boolean(message.error),
    createdAt: Number(message.createdAt) || Date.now(),
  }
}

function normalizeSession(session) {
  if (!session || typeof session !== 'object' || !session.id) return null
  const messages = Array.isArray(session.messages)
    ? session.messages.map(normalizeMessage).filter(Boolean).slice(-MAX_MESSAGES_PER_SESSION)
    : []
  if (!messages.length) return null
  const createdAt = Number(session.createdAt) || Date.now()
  return {
    id: String(session.id),
    conversationId: String(session.conversationId || ''),
    title: createSessionTitle(session.title || '新对话'),
    createdAt,
    updatedAt: Number(session.updatedAt) || createdAt,
    messages,
  }
}

export function normalizeChatSessions(sessions) {
  if (!Array.isArray(sessions)) return []
  return sessions
    .map(normalizeSession)
    .filter(Boolean)
    .sort((left, right) => right.updatedAt - left.updatedAt)
    .slice(0, MAX_CHAT_SESSIONS)
}

export function loadChatSessions(storage = globalThis.localStorage) {
  if (!storage?.getItem) return []
  try {
    return normalizeChatSessions(
      JSON.parse(storage.getItem(CHAT_SESSIONS_STORAGE_KEY) || '[]'),
    )
  } catch {
    return []
  }
}

export function saveChatSessions(sessions, storage = globalThis.localStorage) {
  if (!storage?.setItem) return false
  try {
    const normalized = normalizeChatSessions(sessions)
    // 图片地址是短期 OSS 签名 URL，不写入长期浏览器历史，避免刷新后展示失效链接。
    const serializable = normalized.map((session) => ({
      ...session,
      messages: session.messages.map((message) => ({
        ...message,
        sources: message.sources.map((source) => ({ ...source, images: [] })),
      })),
    }))
    storage.setItem(CHAT_SESSIONS_STORAGE_KEY, JSON.stringify(serializable))
    return true
  } catch {
    return false
  }
}

export function formatSessionTime(timestamp, now = Date.now()) {
  const value = Number(timestamp)
  if (!value) return ''
  const date = new Date(value)
  const current = new Date(now)
  if (date.toDateString() === current.toDateString()) {
    return new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(date)
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}
