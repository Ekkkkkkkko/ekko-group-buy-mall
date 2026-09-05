<script setup>
import { ArrowUp, Headset, MessageCircleMore, X } from 'lucide-vue-next'
import { nextTick, ref } from 'vue'
import { askKnowledgeBaseStream } from '../api/knowledge'
import MarkdownMessage from './MarkdownMessage.vue'
import ChatSources from './ChatSources.vue'

const open = ref(false)
const draft = ref('')
const busy = ref(false)
const conversationId = ref('')
const chatBody = ref(null)
const suggestions = [
  '如何查看连接路由器的终端数量？',
  '如何设置两台易展路由器组网？',
  '怎么查看路由器默认无线信号？',
]
let messageSequence = 1

const messages = ref([
  {
    id: messageSequence++,
    role: 'assistant',
    text: '你好，我是产品客服小安。你可以问我路由器参数、无线设置和易展组网等问题。',
    sources: [],
  },
])

async function scrollToLatest() {
  await nextTick()
  if (chatBody.value) {
    chatBody.value.scrollTop = chatBody.value.scrollHeight
  }
}

async function submitQuestion(value = draft.value) {
  const question = value.trim()
  if (!question || busy.value) return

  messages.value.push({
    id: messageSequence++,
    role: 'user',
    text: question,
    sources: [],
  })
  draft.value = ''
  busy.value = true
  const assistantId = messageSequence++
  messages.value.push({
    id: assistantId,
    role: 'assistant',
    text: '',
    progress: '正在理解您的问题',
    sources: [],
  })
  const assistantMessage = messages.value.find((message) => message.id === assistantId)
  await scrollToLatest()

  try {
    const result = await askKnowledgeBaseStream(question, {
      conversationId: conversationId.value || undefined,
      onProgress(progress) {
        assistantMessage.progress = progress || '正在生成回答'
        scrollToLatest()
      },
      onReference(sources) {
        assistantMessage.sources = Array.isArray(sources) ? sources : []
        scrollToLatest()
      },
      onAnswer(delta) {
        assistantMessage.progress = ''
        assistantMessage.text += delta
        scrollToLatest()
      },
      onComplete(response) {
        conversationId.value = response?.conversationId || conversationId.value
        if (!assistantMessage.text && response?.answer) assistantMessage.text = response.answer
        if (Array.isArray(response?.sources)) assistantMessage.sources = response.sources
      },
    })
    conversationId.value = result?.conversationId || conversationId.value
    assistantMessage.progress = ''
    assistantMessage.text = result?.answer || assistantMessage.text || '暂时没找到可靠答案，你可以换个问法。'
    if (Array.isArray(result?.sources)) assistantMessage.sources = result.sources
  } catch {
    assistantMessage.progress = ''
    assistantMessage.text = assistantMessage.text
      ? `${assistantMessage.text}\n\n（回答中断，请稍后重试。）`
      : '小安暂时无法回答，请稍后再试。'
    assistantMessage.error = true
  } finally {
    busy.value = false
    await scrollToLatest()
  }
}

function openChat() {
  open.value = true
  scrollToLatest()
}

defineExpose({ openChat })
</script>

<template>
  <div class="chat-shell" :class="{ open }">
    <Transition name="chat-panel">
      <section v-if="open" class="chat-panel" aria-label="小安客服对话框">
        <header>
          <span class="chat-agent"><Headset :size="25" /></span>
          <div>
            <strong>小安</strong>
            <small><i aria-hidden="true"></i> 路由器选购助手 · 在线</small>
          </div>
          <button type="button" aria-label="关闭客服对话框" @click="open = false">
            <X :size="23" />
          </button>
        </header>

        <div
          ref="chatBody"
          class="chat-body"
          tabindex="0"
          aria-label="客服对话内容"
          aria-live="polite"
        >
          <div class="chat-date"><span>今天</span></div>

          <template v-for="message in messages" :key="message.id">
            <div v-if="message.role === 'user'" class="chat-preview-message chat-message">
              <span>你</span>
              <p>{{ message.text }}</p>
            </div>

            <div
              v-else
              class="chat-system-message chat-message"
              :class="{ 'chat-system-message--error': message.error }"
            >
              <span class="chat-message-avatar"><Headset :size="14" /></span>
              <div>
                <small>小安</small>
                <MarkdownMessage v-if="message.text" :content="message.text" />
                <p v-else class="chat-loading">
                  <i></i><i></i><i></i>
                  <span>{{ message.progress || '正在生成回答' }}</span>
                </p>
                <ChatSources :sources="message.sources" />
              </div>
            </div>
          </template>

          <div v-if="messages.length === 1" class="chat-suggestions" aria-label="快捷问题">
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

        <form @submit.prevent="submitQuestion()">
          <input
            v-model="draft"
            type="text"
            maxlength="500"
            :disabled="busy"
            placeholder="输入路由器参数、设置或故障问题"
          />
          <button type="submit" :disabled="busy || !draft.trim()" aria-label="发送消息">
            <ArrowUp :size="24" />
          </button>
        </form>
      </section>
    </Transition>

    <button
      class="chat-trigger"
      type="button"
      :aria-label="open ? '关闭智能客服' : '打开智能客服'"
      @click="open = !open"
    >
      <X v-if="open" :size="26" />
      <MessageCircleMore v-else :size="30" />
      <span v-if="!open">问一问</span>
    </button>
  </div>
</template>
