<script setup>
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Database,
  FileSearch,
  FileText,
  LoaderCircle,
  LogOut,
  Search,
  Trash2,
  UploadCloud,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  deleteKnowledgeDocument,
  queryKnowledgeDocument,
  uploadKnowledgeDocument,
} from '../api/knowledge'
import { pollKnowledgeDocument } from '../utils/pollKnowledgeDocument'

const props = defineProps({
  adminToken: {
    type: String,
    required: true,
  },
})

const emit = defineEmits(['logout', 'unauthorized'])

const selectedFile = ref(null)
const fileInput = ref(null)
const title = ref('')
const productModel = ref('')
const chunkStrategy = ref('SMART')
const lookupId = ref('')
const currentDocument = ref(null)
const uploading = ref(false)
const querying = ref(false)
const deleting = ref(false)
const successMessage = ref('')
const errorMessage = ref('')
let pollingController = null

const statusLabels = {
  UPLOADING: '上传中',
  PARSING: '解析中',
  INDEXING: '建立索引中',
  PUBLISHED: '已发布',
  FAILED: '处理失败',
}

const chunkStrategies = [
  {
    value: 'SMART',
    label: '智能切分（推荐）',
    description: '自动识别 Markdown 标题；结构清晰时按标题和父子分片，否则按长度切分。',
  },
  {
    value: 'TITLE',
    label: '标题切分',
    description: '按照 Markdown 标题组织章节，适合说明书、教程和结构清晰的 FAQ。',
  },
  {
    value: 'LENGTH',
    label: '长度切分',
    description: '按照自然边界和长度切分，适合 OCR 文本、图片详情图或标题不稳定的资料。',
  },
  {
    value: 'SEPARATOR',
    label: '固定分隔符切分',
    description: '适合经过人工整理，并使用统一分隔符组织内容的 Markdown。',
  },
  {
    value: 'REGEX',
    label: '正则切分',
    description: '适合排版规则固定、可由后端正则稳定识别章节的批量文档。',
  },
]

const selectedChunkStrategy = computed(
  () => chunkStrategies.find(strategy => strategy.value === chunkStrategy.value) || chunkStrategies[0],
)

function chunkStrategyLabel(value) {
  return chunkStrategies.find(strategy => strategy.value === value)?.label || value || '未记录'
}

const fileName = computed(() => selectedFile.value?.name || '选择 PDF、PNG 或 JPG 文件')

function resetMessage() {
  successMessage.value = ''
  errorMessage.value = ''
}

function handleApiError(error) {
  if (error.status === 401) {
    emit('unauthorized')
    return
  }
  errorMessage.value = error.message
}

function stopPolling() {
  pollingController?.abort()
  pollingController = null
}

async function trackDocument(documentId) {
  stopPolling()
  const controller = new AbortController()
  pollingController = controller
  try {
    const result = await pollKnowledgeDocument({
      documentId,
      query: id => queryKnowledgeDocument(id, props.adminToken),
      onUpdate: document => {
        currentDocument.value = document
      },
      signal: controller.signal,
    })
    if (result.status === 'PUBLISHED') {
      successMessage.value = `文档 #${documentId} 已发布，可以用于智能客服检索`
      errorMessage.value = ''
    } else {
      successMessage.value = ''
      errorMessage.value = result.failureReason || `文档 #${documentId} 处理失败`
    }
  } catch (error) {
    if (error.name !== 'AbortError') handleApiError(error)
  } finally {
    if (pollingController === controller) pollingController = null
  }
}

function logout() {
  stopPolling()
  emit('logout')
}

function chooseFile(event) {
  const [file] = event.target.files || []
  selectedFile.value = file || null
  if (file && !title.value.trim()) {
    title.value = file.name.replace(/\.[^.]+$/, '')
  }
  resetMessage()
}

function validDocumentId() {
  const id = Number(lookupId.value)
  if (!Number.isSafeInteger(id) || id <= 0) {
    errorMessage.value = '请输入正确的文档 ID'
    return null
  }
  return id
}

async function upload() {
  resetMessage()
  if (!selectedFile.value) {
    errorMessage.value = '请先选择需要上传的文档'
    return
  }
  if (!title.value.trim()) {
    errorMessage.value = '请输入文档标题'
    return
  }

  stopPolling()
  uploading.value = true
  try {
    const result = await uploadKnowledgeDocument(
      {
        file: selectedFile.value,
        title: title.value.trim(),
        productModel: productModel.value.trim(),
        chunkStrategy: chunkStrategy.value,
      },
      props.adminToken,
    )
    currentDocument.value = result
    lookupId.value = String(result.id)
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
    successMessage.value = `文档 #${result.id} 已提交解析`
    void trackDocument(result.id)
  } catch (error) {
    handleApiError(error)
  } finally {
    uploading.value = false
  }
}

async function lookup() {
  resetMessage()
  stopPolling()
  const documentId = validDocumentId()
  if (!documentId) return

  querying.value = true
  try {
    currentDocument.value = await queryKnowledgeDocument(documentId, props.adminToken)
    if (['PARSING', 'INDEXING'].includes(currentDocument.value.status)) {
      successMessage.value = `文档 #${documentId} 仍在处理中，状态将自动刷新`
      void trackDocument(documentId)
    }
  } catch (error) {
    currentDocument.value = null
    handleApiError(error)
  } finally {
    querying.value = false
  }
}

async function removeDocument() {
  if (!currentDocument.value || deleting.value) return
  const documentId = currentDocument.value.id
  const confirmed = window.confirm(
    `确认删除文档 #${documentId} 吗？\n\n将删除 MySQL 文档/分片记录和 ES 向量，OSS 文件会继续保留。`,
  )
  if (!confirmed) return

  resetMessage()
  stopPolling()
  deleting.value = true
  try {
    await deleteKnowledgeDocument(documentId, props.adminToken)
    currentDocument.value = null
    lookupId.value = ''
    successMessage.value = `文档 #${documentId} 已从知识库删除，OSS 文件仍然保留`
  } catch (error) {
    handleApiError(error)
  } finally {
    deleting.value = false
  }
}

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

onBeforeUnmount(stopPolling)
</script>

<template>
  <main class="document-admin">
    <header class="document-admin__nav">
      <a href="#/" class="document-admin__back">
        <ArrowLeft :size="18" />
        返回商城
      </a>
      <a href="#/" class="document-admin__brand">LinkNest <span>联巢</span></a>
      <div class="document-admin__actions">
        <span class="document-admin__role">管理员已登录</span>
        <button type="button" @click="logout">
          <LogOut :size="15" />
          退出
        </button>
      </div>
    </header>

    <section class="document-admin__hero">
      <div>
        <p><Database :size="16" /> 管理员工具</p>
        <h1>知识库文档</h1>
        <span>上传产品资料，完成解析、切片和索引后即可供智能客服检索。</span>
      </div>
      <div class="document-admin__flow" aria-label="文档处理流程">
        <span>OSS</span><i></i><span>MinerU</span><i></i><span>MySQL</span><i></i><span>ES</span>
      </div>
    </section>

    <div class="document-admin__notice" role="status">
      <CheckCircle2 v-if="successMessage" :size="18" />
      <AlertCircle v-else-if="errorMessage" :size="18" />
      <FileText v-else :size="18" />
      <span v-if="successMessage">{{ successMessage }}</span>
      <span v-else-if="errorMessage">{{ errorMessage }}</span>
      <span v-else>上传时可自动或手动选择切分策略；删除会清理 MySQL 分片和 ES 向量。</span>
    </div>

    <section class="document-admin__grid">
      <article class="document-card">
        <div class="document-card__heading">
          <span><UploadCloud :size="22" /></span>
          <div>
            <small>NEW DOCUMENT</small>
            <h2>上传文档</h2>
          </div>
        </div>

        <form class="document-form" @submit.prevent="upload">
          <label class="document-file">
            <input
              ref="fileInput"
              type="file"
              accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
              @change="chooseFile"
            />
            <FileText :size="24" />
            <span>{{ fileName }}</span>
            <small>单个文件最大 50MB</small>
          </label>

          <label>
            <span>文档标题</span>
            <input v-model="title" maxlength="200" placeholder="例如：TL-7DR5130 产品介绍" />
          </label>

          <label>
            <span>产品型号 <small>选填</small></span>
            <input v-model="productModel" maxlength="100" placeholder="例如：TL-7DR5130" />
          </label>

          <label>
            <span>切分策略 <small>SMART 默认推荐</small></span>
            <div class="document-strategy">
              <select v-model="chunkStrategy" aria-label="文档切分策略">
                <option
                  v-for="strategy in chunkStrategies"
                  :key="strategy.value"
                  :value="strategy.value"
                >
                  {{ strategy.label }}
                </option>
              </select>
              <p>
                <strong>{{ selectedChunkStrategy.value }}</strong>
                {{ selectedChunkStrategy.description }}
              </p>
            </div>
          </label>

          <button class="document-primary" type="submit" :disabled="uploading">
            <LoaderCircle v-if="uploading" class="is-spinning" :size="18" />
            <UploadCloud v-else :size="18" />
            {{ uploading ? '正在上传并提交解析…' : '上传并提交解析' }}
          </button>
        </form>
      </article>

      <article class="document-card">
        <div class="document-card__heading">
          <span><FileSearch :size="22" /></span>
          <div>
            <small>DOCUMENT LOOKUP</small>
            <h2>查询与删除</h2>
          </div>
        </div>

        <form class="document-lookup" @submit.prevent="lookup">
          <label>
            <span>文档 ID</span>
            <input v-model="lookupId" inputmode="numeric" placeholder="输入上传后返回的 ID" />
          </label>
          <button type="submit" :disabled="querying">
            <LoaderCircle v-if="querying" class="is-spinning" :size="18" />
            <Search v-else :size="18" />
            查询
          </button>
        </form>

        <div v-if="currentDocument" class="document-result">
          <div class="document-result__top">
            <div>
              <small>DOCUMENT #{{ currentDocument.id }}</small>
              <h3>{{ currentDocument.title }}</h3>
            </div>
            <span class="document-status" :class="`is-${currentDocument.status?.toLowerCase()}`">
              {{ statusLabels[currentDocument.status] || currentDocument.status }}
            </span>
          </div>

          <dl>
            <div><dt>文件</dt><dd>{{ currentDocument.fileName }}</dd></div>
            <div><dt>型号</dt><dd>{{ currentDocument.productModel || '未填写' }}</dd></div>
            <div><dt>切分策略</dt><dd>{{ chunkStrategyLabel(currentDocument.chunkStrategy) }}</dd></div>
            <div><dt>分片</dt><dd>{{ currentDocument.chunkCount ?? 0 }} 个</dd></div>
            <div><dt>创建时间</dt><dd>{{ formatDate(currentDocument.createdAt) }}</dd></div>
          </dl>

          <p v-if="currentDocument.failureReason" class="document-result__failure">
            {{ currentDocument.failureReason }}
          </p>

          <button class="document-danger" type="button" :disabled="deleting" @click="removeDocument">
            <LoaderCircle v-if="deleting" class="is-spinning" :size="17" />
            <Trash2 v-else :size="17" />
            {{ deleting ? '正在删除…' : '删除文档' }}
          </button>
          <small class="document-result__hint">删除 MySQL 文档/分片和 ES 向量，OSS 文件保留。</small>
        </div>

        <div v-else class="document-empty">
          <FileSearch :size="34" />
          <strong>尚未选择文档</strong>
          <span>上传成功后会自动显示，也可以通过文档 ID 查询。</span>
        </div>
      </article>
    </section>

    <footer class="document-admin__footer">
      <span>LinkNest 联巢知识库</span>
      <small>文档上传、查询和删除接口仅允许管理员访问。</small>
    </footer>
  </main>
</template>

<style scoped>
.document-admin {
  min-height: 100vh;
  padding: 22px clamp(20px, 5vw, 72px) 36px;
  color: var(--ink);
  background:
    radial-gradient(circle at 86% 2%, rgba(201, 242, 85, 0.2), transparent 24rem),
    var(--cream);
}

.document-admin__nav {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  max-width: 1180px;
  min-height: 64px;
  margin: 0 auto;
}

.document-admin__back,
.document-admin__brand {
  color: inherit;
  text-decoration: none;
}

.document-admin__back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.document-admin__brand {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.document-admin__brand span {
  margin-left: 8px;
  color: #657052;
  font-size: 13px;
  letter-spacing: 0.08em;
}

.document-admin__role {
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  background: rgba(255, 255, 255, 0.62);
  font-size: 11px;
  font-weight: 700;
}

.document-admin__actions {
  display: flex;
  align-items: center;
  justify-self: end;
  gap: 8px;
}

.document-admin__actions button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 11px;
  border: 0;
  border-radius: 999px;
  color: #fff;
  background: var(--ink);
  cursor: pointer;
  font-size: 11px;
  font-weight: 750;
}

.document-admin__hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  max-width: 1180px;
  margin: 68px auto 34px;
}

.document-admin__hero p {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  color: #718326;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.document-admin__hero h1 {
  margin: 0;
  font-size: clamp(2.7rem, 6vw, 5.5rem);
  letter-spacing: -0.07em;
  line-height: 0.96;
}

.document-admin__hero > div:first-child > span {
  display: block;
  max-width: 560px;
  margin-top: 22px;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.7;
}

.document-admin__flow {
  display: flex;
  align-items: center;
  gap: 9px;
  padding-bottom: 8px;
}

.document-admin__flow span {
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.68);
  font-size: 10px;
  font-weight: 800;
}

.document-admin__flow i {
  width: 22px;
  height: 1px;
  background: rgba(22, 23, 19, 0.22);
}

.document-admin__notice {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 1180px;
  min-height: 48px;
  margin: 0 auto 18px;
  padding: 11px 15px;
  border: 1px solid var(--line);
  border-radius: 12px;
  color: var(--muted);
  background: rgba(255, 255, 255, 0.58);
  font-size: 12px;
}

.document-admin__grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 18px;
  max-width: 1180px;
  margin: 0 auto;
}

.document-card {
  padding: clamp(24px, 3vw, 38px);
  border: 1px solid var(--line);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--soft-shadow);
}

.document-card__heading {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 30px;
}

.document-card__heading > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: var(--lime);
}

.document-card__heading small,
.document-result__top small {
  color: #8d9088;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.13em;
}

.document-card__heading h2,
.document-result__top h3 {
  margin: 3px 0 0;
  letter-spacing: -0.04em;
}

.document-card__heading h2 {
  font-size: 21px;
}

.document-form,
.document-lookup label {
  display: flex;
  flex-direction: column;
}

.document-form {
  gap: 18px;
}

.document-form label > span,
.document-lookup label > span {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 750;
}

.document-form label > span small {
  color: var(--muted);
  font-weight: 500;
}

.document-form input:not([type='file']),
.document-form select,
.document-lookup input {
  width: 100%;
  height: 48px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 11px;
  outline: 0;
  background: var(--cream);
  font: inherit;
}

.document-form input:focus,
.document-form select:focus,
.document-lookup input:focus {
  border-color: #8baa29;
  box-shadow: 0 0 0 4px rgba(139, 170, 41, 0.1);
}

.document-strategy select {
  appearance: auto;
  color: var(--ink);
  cursor: pointer;
}

.document-strategy p {
  margin: 8px 2px 0;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.55;
}

.document-strategy p strong {
  margin-right: 5px;
  color: #63781c;
  font-weight: 800;
}

.document-file {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 132px;
  padding: 18px;
  border: 1px dashed rgba(22, 23, 19, 0.25);
  border-radius: 15px;
  color: var(--muted);
  background: var(--cream);
  cursor: pointer;
  text-align: center;
}

.document-file input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.document-file > span {
  margin: 9px 0 3px !important;
  color: var(--ink);
}

.document-file > small {
  font-size: 10px;
}

.document-primary,
.document-danger,
.document-lookup button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  cursor: pointer;
  font-weight: 800;
}

.document-primary {
  min-height: 52px;
  margin-top: 3px;
  border-radius: 13px;
  color: #fff;
  background: var(--ink);
}

.document-primary:disabled,
.document-danger:disabled,
.document-lookup button:disabled {
  cursor: wait;
  opacity: 0.58;
}

.document-lookup {
  display: grid;
  grid-template-columns: 1fr 96px;
  align-items: end;
  gap: 10px;
}

.document-lookup button {
  height: 48px;
  border-radius: 11px;
  background: var(--lime);
}

.document-result,
.document-empty {
  min-height: 285px;
  margin-top: 24px;
  padding: 24px;
  border-radius: 17px;
  background: var(--cream);
}

.document-result__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.document-result__top h3 {
  font-size: 19px;
}

.document-status {
  flex: 0 0 auto;
  padding: 6px 9px;
  border-radius: 999px;
  color: #516315;
  background: rgba(201, 242, 85, 0.58);
  font-size: 10px;
  font-weight: 800;
}

.document-status.is-failed {
  color: #a14135;
  background: #f7ded8;
}

.document-result dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 20px;
  margin: 25px 0;
}

.document-result dl div {
  min-width: 0;
}

.document-result dt {
  margin-bottom: 4px;
  color: #8b8e85;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.document-result dd {
  margin: 0;
  overflow: hidden;
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-result__failure {
  padding: 10px 12px;
  border-radius: 9px;
  color: #9b3f35;
  background: #f8e5e1;
  font-size: 11px;
  line-height: 1.5;
}

.document-danger {
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid rgba(165, 65, 51, 0.18);
  border-radius: 10px;
  color: #a14135;
  background: #fff;
}

.document-result__hint {
  display: block;
  margin-top: 10px;
  color: #90928b;
  font-size: 9px;
}

.document-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: #94978e;
  text-align: center;
}

.document-empty strong {
  margin: 12px 0 5px;
  color: #676a62;
  font-size: 13px;
}

.document-empty span {
  max-width: 260px;
  font-size: 11px;
  line-height: 1.6;
}

.document-admin__footer {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  max-width: 1180px;
  margin: 34px auto 0;
  padding-top: 20px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 11px;
}

.is-spinning {
  animation: document-spin 0.9s linear infinite;
}

@keyframes document-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 760px) {
  .document-admin__nav {
    grid-template-columns: 1fr auto;
  }

  .document-admin__brand {
    display: none;
  }

  .document-admin__hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 28px;
    margin-top: 44px;
  }

  .document-admin__flow {
    padding: 0;
  }

  .document-admin__grid {
    grid-template-columns: 1fr;
  }

  .document-admin__footer {
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .document-admin {
    padding-inline: 14px;
  }

  .document-card {
    padding: 22px 18px;
    border-radius: 18px;
  }

  .document-result dl {
    grid-template-columns: 1fr;
  }
}
</style>
