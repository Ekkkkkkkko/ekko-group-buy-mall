import assert from 'node:assert/strict'
import test from 'node:test'
import {
  CHAT_SESSIONS_STORAGE_KEY,
  createChatSession,
  createSessionTitle,
  loadChatSessions,
  saveChatSessions,
} from '../src/utils/chatSessions.js'

function memoryStorage(initialValue = null) {
  const values = new Map()
  if (initialValue !== null) {
    values.set(CHAT_SESSIONS_STORAGE_KEY, initialValue)
  }
  return {
    getItem(key) {
      return values.get(key) ?? null
    },
    setItem(key, value) {
      values.set(key, value)
    },
  }
}

test('creates an independent conversation with the customer service greeting', () => {
  const session = createChatSession({ id: 'conversation-1', now: 1_000 })

  assert.equal(session.id, 'conversation-1')
  assert.equal(session.conversationId, '')
  assert.equal(session.title, '新对话')
  assert.equal(session.messages.length, 1)
  assert.equal(session.messages[0].role, 'assistant')
  assert.match(session.messages[0].text, /联巢智能客服/)
})

test('builds a concise title from the first question', () => {
  assert.equal(createSessionTitle('  TL-7DR6560   怎么组网？  '), 'TL-7DR6560 怎么组网？')
  assert.equal(createSessionTitle('这是一个超过十个字符的非常非常长的问题', 10), '这是一个超过十个字符…')
})

test('loads no history when local data is damaged', () => {
  assert.deepEqual(loadChatSessions(memoryStorage('{broken')), [])
})

test('persists messages but removes expiring signed image urls', () => {
  const storage = memoryStorage()
  const session = createChatSession({ id: 'conversation-2', now: 2_000 })
  session.messages.push({
    id: 'message-2',
    role: 'assistant',
    text: '请查看接口示意图。',
    createdAt: 2_100,
    sources: [
      {
        documentId: 9,
        title: '安装说明',
        images: [{ imageId: 1, url: 'https://example.test/signed', description: '接口图' }],
      },
    ],
  })
  session.conversationId = 'server-conversation-2'

  assert.equal(saveChatSessions([session], storage), true)
  const restored = loadChatSessions(storage)

  assert.equal(restored.length, 1)
  assert.equal(restored[0].conversationId, 'server-conversation-2')
  assert.equal(restored[0].messages[1].sources[0].title, '安装说明')
  assert.deepEqual(restored[0].messages[1].sources[0].images, [])
})
