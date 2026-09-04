<script setup>
import {
  ArrowLeft,
  FileText,
  Headset,
  Menu,
  MessageSquareText,
  Plus,
  Send,
  Trash2,
  X,
} from 'lucide-vue-next'
import { computed, nextTick, onMounted, ref } from 'vue'
import { askKnowledgeBaseStream } from '../api/knowledge'
import MarkdownMessage from './MarkdownMessage.vue'
import {
  createChatSession,
  createSessionTitle,
  formatSessionTime,
  loadChatSessions,
  MAX_CHAT_SESSIONS,
  MAX_MESSAGES_PER_SESSION,
  saveChatSessions,
} from '../utils/chatSessions'

const suggestions = [
  'TL-7DR6560 有几个 2.5G 网口？',
  '忘记 Wi-Fi 密码怎么办？',
  '两台易展路由器怎么组网？',
  '路由器怎么恢复出厂设置？',
]

const sessions = ref([])
const activeSessionId = ref('')
const draft = ref('')
const pendingSessionIds = ref(new Set())
const chatBody = ref(null)
const sidebarOpen = ref(false)

const activeSession = computed(() =>
  sessions.value.find((session) => session.id === activeSessionId.value),
)
const activeMessages = computed(() => activeSession.value?.messages || [])
const activeBusy = computed(() => pendingSessionIds.value.has(activeSessionId.value))
const hasOnlyGreeting = computed(() => activeMessages.value.length === 1)

function createMessageId() {
  return `message-${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`}`
}

function sortAndSave() {
  sessions.value.sort((left, right) => right.updatedAt - left.updatedAt)
  if (sessions.value.length > MAX_CHAT_SESSIONS) {
    sessions.value.splice(MAX_CHAT_SESSIONS)
  }
  saveChatSessions(sessions.value)
}

function startNewConversation() {
  const session = createChatSession()
  sessions.value.unshift(session)
  activeSessionId.value = session.id
  draft.value = ''
  sidebarOpen.value = false
  sortAndSave()
  scrollToLatest()
}

function selectSession(sessionId) {
  activeSessionId.value = sessionId
  sidebarOpen.value = false
  scrollToLatest()
}

function deleteSession(sessionId) {
  sessions.value = sessions.value.filter((session) => session.id !== sessionId)
  const nextSession = sessions.value[0]
  if (!nextSession) {
    startNewConversation()
    return
  }
  if (activeSessionId.value === sessionId) {
    activeSessionId.value = nextSession.id
  }
  sortAndSave()
}

function setPending(sessionId, pending) {
  const next = new Set(pendingSessionIds.value)
  if (pending) next.add(sessionId)
  else next.delete(sessionId)
  pendingSessionIds.value = next
}

async function scrollToLatest() {
  await nextTick()
  if (chatBody.value) {
    chatBody.value.scrollTop = chatBody.value.scrollHeight
  }
}

function appendMessage(sessionId, message) {
  const session = sessions.value.find((item) => item.id === sessionId)
  if (!session) return null
  session.messages.push(message)
  if (session.messages.length > MAX_MESSAGES_PER_SESSION) {
    session.messages.splice(0, session.messages.length - MAX_MESSAGES_PER_SESSION)
  }
  session.updatedAt = Date.now()
  return session
}

function findMessage(sessionId, messageId) {
  return sessions.value
    .find((session) => session.id === sessionId)
    ?.messages.find((message) => message.id === messageId)
}

async function submitQuestion(value = draft.value) {
  const question = String(value || '').trim()
  const session = activeSession.value
  if (!question || !session || pendingSessionIds.value.has(session.id)) return

  const sessionId = session.id
  const isFirstQuestion = session.messages.every((message) => message.role !== 'user')
  appendMessage(sessionId, {
    id: createMessageId(),
    role: 'user',
    text: question,
    sources: [],
    createdAt: Date.now(),
  })
  if (isFirstQuestion) session.title = createSessionTitle(question)
  draft.value = ''
  setPending(sessionId, true)
  const assistantId = createMessageId()
  appendMessage(sessionId, {
    id: assistantId,
    role: 'assistant',
    text: '',
    progress: '正在理解您的问题',
    sources: [],
    createdAt: Date.now(),
  })
  sortAndSave()
  await scrollToLatest()

  try {
    const result = await askKnowledgeBaseStream(question, {
      conversationId: session.conversationId || undefined,
      onProgress(progress) {
        const message = findMessage(sessionId, assistantId)
        if (!message) return
        message.progress = progress || '正在生成回答'
        if (activeSessionId.value === sessionId) scrollToLatest()
      },
      onReference(sources) {
        const message = findMessage(sessionId, assistantId)
        if (!message) return
        message.sources = Array.isArray(sources) ? sources : []
        if (activeSessionId.value === sessionId) scrollToLatest()
      },
      onAnswer(delta) {
        const message = findMessage(sessionId, assistantId)
        if (!message) return
        message.progress = ''
        message.text += delta
        if (activeSessionId.value === sessionId) scrollToLatest()
      },
      onComplete(response) {
        const currentSession = sessions.value.find((item) => item.id === sessionId)
        const message = findMessage(sessionId, assistantId)
        if (currentSession && response?.conversationId) {
          currentSession.conversationId = response.conversationId
        }
        if (!message) return
        if (!message.text && response?.answer) message.text = response.answer
        if (Array.isArray(response?.sources)) message.sources = response.sources
      },
    })
    const currentSession = sessions.value.find((item) => item.id === sessionId)
    const message = findMessage(sessionId, assistantId)
    if (currentSession && result?.conversationId) currentSession.conversationId = result.conversationId
    if (message) {
      message.progress = ''
      message.text = result?.answer || message.text || '暂时没找到可靠答案，你可以换个问法。'
      if (Array.isArray(result?.sources)) message.sources = result.sources
    }
  } catch {
    const message = findMessage(sessionId, assistantId)
    if (message) {
      message.progress = ''
      message.text = message.text
        ? `${message.text}\n\n（回答中断，请稍后重试。）`
        : '小安暂时无法回答，请稍后再试。'
      message.error = true
    }
  } finally {
    setPending(sessionId, false)
    sortAndSave()
    if (activeSessionId.value === sessionId) await scrollToLatest()
  }
}

function handleComposerKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submitQuestion()
  }
}

onMounted(() => {
  sessions.value = loadChatSessions()
  if (!sessions.value.length) {
    startNewConversation()
    return
  }
  activeSessionId.value = sessions.value[0].id
  scrollToLatest()
})
</script>

<template>
  <main class="support-page">
    <header class="support-topbar">
      <a class="brand" href="#" aria-label="返回 LinkNest 联巢商城首页">
        <span class="brand-name"><strong>LinkNest</strong><small>联巢</small></span>
      </a>
      <div class="support-topbar-title">
        <Headset :size="18" />
        <span>智能客服</span>
      </div>
      <a class="support-back-link" href="#">
        <ArrowLeft :size="17" />
        返回商城
      </a>
    </header>

    <section class="support-workspace" aria-label="智能客服工作台">
      <button
        v-if="sidebarOpen"
        class="support-sidebar-scrim"
        type="button"
        aria-label="关闭会话列表"
        @click="sidebarOpen = false"
      ></button>

      <aside class="support-sidebar" :class="{ 'support-sidebar--open': sidebarOpen }">
        <div class="support-sidebar-heading">
          <div>
            <span>对话记录</span>
            <small>只保存在这台设备</small>
          </div>
          <button
            class="support-mobile-close"
            type="button"
            aria-label="关闭会话列表"
            @click="sidebarOpen = false"
          >
            <X :size="19" />
          </button>
        </div>

        <button class="support-new-chat" type="button" @click="startNewConversation">
          <Plus :size="18" />
          新建对话
        </button>

        <div class="support-session-list" aria-label="历史对话">
          <article
            v-for="session in sessions"
            :key="session.id"
            class="support-session-item"
            :class="{ 'support-session-item--active': session.id === activeSessionId }"
          >
            <button type="button" class="support-session-select" @click="selectSession(session.id)">
              <MessageSquareText :size="17" />
              <span>
                <strong>{{ session.title }}</strong>
                <small>{{ formatSessionTime(session.updatedAt) }}</small>
              </span>
            </button>
            <button
              class="support-session-delete"
              type="button"
              :aria-label="`删除对话：${session.title}`"
              @click="deleteSession(session.id)"
            >
              <Trash2 :size="15" />
            </button>
          </article>
        </div>

        <p class="support-storage-note">
          每次提问都会查找相关产品资料；对话只保存在这台设备，不会出现在其他设备上。
        </p>
      </aside>

      <section class="support-chat-area">
        <header class="support-chat-header">
          <button
            class="support-sidebar-toggle"
            type="button"
            aria-label="打开会话列表"
            @click="sidebarOpen = true"
          >
            <Menu :size="20" />
          </button>
          <span class="support-agent-mark"><Headset :size="21" /></span>
          <div>
            <strong>{{ activeSession?.title || '新对话' }}</strong>
            <small><i aria-hidden="true"></i> 小安 · 路由器选购助手</small>
          </div>
        </header>

        <div
          ref="chatBody"
          class="support-messages"
          tabindex="0"
          aria-label="对话内容"
          aria-live="polite"
        >
          <div class="support-conversation-date"><span>本次对话</span></div>

          <template v-for="message in activeMessages" :key="message.id">
            <article v-if="message.role === 'user'" class="support-message support-message--user">
              <div>
                <small>你</small>
                <p>{{ message.text }}</p>
              </div>
            </article>

            <article
              v-else
              class="support-message support-message--assistant"
              :class="{ 'support-message--error': message.error }"
            >
              <span class="support-message-avatar"><Headset :size="15" /></span>
              <div>
                <small>小安</small>
                <MarkdownMessage v-if="message.text" :content="message.text" />
                <p v-else class="support-thinking">
                  <i></i><i></i><i></i>
                  <span>{{ message.progress || '正在生成回答' }}</span>
                </p>
                <ol v-if="message.sources?.length" class="support-source-list">
                  <li v-for="(source, index) in message.sources" :key="`${message.id}-${index}`">
                    <span class="support-source-index">资料 {{ index + 1 }}</span>
                    <div>
                      <strong>{{ source.title || '相关产品资料' }}</strong>
                      <small v-if="source.headingPath">{{ source.headingPath }}</small>
                    </div>
                    <FileText :size="16" />
                    <div v-if="source.images?.length" class="support-source-images">
                      <a
                        v-for="image in source.images"
                        :key="image.imageId || image.url"
                        :href="image.url"
                        target="_blank"
                        rel="noreferrer"
                        :title="image.description || '查看资料图片'"
                      >
                        <img :src="image.url" :alt="image.description || '相关资料图片'" loading="lazy" />
                      </a>
                    </div>
                  </li>
                </ol>
              </div>
            </article>
          </template>

          <div v-if="hasOnlyGreeting" class="support-suggestions" aria-label="常见问题">
            <p>可以从这些问题开始</p>
            <div>
              <button
                v-for="suggestion in suggestions"
                :key="suggestion"
                type="button"
                @click="submitQuestion(suggestion)"
              >
                {{ suggestion }}
              </button>
            </div>
          </div>

        </div>

        <form class="support-composer" @submit.prevent="submitQuestion()">
          <div class="support-composer-box">
            <textarea
              v-model="draft"
              rows="1"
              maxlength="500"
              :disabled="activeBusy"
              placeholder="输入路由器参数、设置或故障问题"
              aria-label="输入客服问题"
              @keydown="handleComposerKeydown"
            ></textarea>
            <button
              type="submit"
              :disabled="activeBusy || !draft.trim()"
              aria-label="发送消息"
            >
              <Send :size="19" />
            </button>
          </div>
          <p>Enter 发送，Shift + Enter 换行 · 回答参考已收录的产品资料</p>
        </form>
      </section>
    </section>
  </main>
</template>
