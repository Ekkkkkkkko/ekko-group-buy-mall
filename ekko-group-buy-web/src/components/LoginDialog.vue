<script setup>
import { KeyRound, LoaderCircle, LogOut, UserPlus, X } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import {
  changeMallAccountPassword,
  loginMallAccount,
  registerMallAccount,
} from '../api/mall'

const props = defineProps({
  open: Boolean,
  demoMode: Boolean,
  authenticated: Boolean,
  token: String,
  accountLabel: String,
  authType: String,
  expiresAt: Number,
})

const emit = defineEmits([
  'close',
  'authenticated',
  'demo-login',
  'logout',
  'orders',
  'password-changed',
])

const mode = ref('login')
const accountView = ref('overview')
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const confirmNewPassword = ref('')
const busy = ref(false)
const message = ref('')

const accountSessionMeta = computed(() => {
  if (props.token === 'demo-session') return '体验数据只保存在当前浏览器'
  const loginMethod = props.authType === 'weixin' ? '微信授权登录' : '账号密码登录'
  if (!props.expiresAt) return loginMethod
  const expiresAt = new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(props.expiresAt))
  return `${loginMethod} · 登录有效至 ${expiresAt}`
})

function resetForm() {
  mode.value = 'login'
  accountView.value = 'overview'
  username.value = ''
  password.value = ''
  confirmPassword.value = ''
  currentPassword.value = ''
  newPassword.value = ''
  confirmNewPassword.value = ''
  busy.value = false
  message.value = ''
}

function switchMode(nextMode) {
  mode.value = nextMode
  password.value = ''
  confirmPassword.value = ''
  message.value = ''
}

async function submitAccount() {
  if (busy.value) return
  message.value = ''
  if (mode.value === 'register' && password.value !== confirmPassword.value) {
    message.value = '两次输入的密码不一致'
    return
  }

  busy.value = true
  try {
    const credentials = {
      username: username.value.trim(),
      password: password.value,
    }
    const jwt = mode.value === 'register'
      ? await registerMallAccount(credentials)
      : await loginMallAccount(credentials)
    emit('authenticated', {
      token: jwt,
      username: username.value.trim(),
    })
  } catch (error) {
    message.value = error.message || '账号服务暂时不可用'
  } finally {
    busy.value = false
  }
}

async function submitPasswordChange() {
  if (busy.value) return
  message.value = ''
  if (newPassword.value !== confirmNewPassword.value) {
    message.value = '两次输入的新密码不一致'
    return
  }

  busy.value = true
  try {
    await changeMallAccountPassword(
      {
        currentPassword: currentPassword.value,
        newPassword: newPassword.value,
      },
      props.token,
    )
    emit('password-changed')
  } catch (error) {
    message.value = error.message || '密码修改失败，请稍后重试'
  } finally {
    busy.value = false
  }
}

watch(
  () => props.open,
  open => {
    if (open) resetForm()
  },
)
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="open" class="dialog-overlay" @click.self="emit('close')">
        <section class="login-dialog" role="dialog" aria-modal="true" aria-label="商城账号">
          <button class="dialog-close" type="button" aria-label="关闭账号窗口" @click="emit('close')">
            <X :size="20" />
          </button>

          <div class="login-heading">
            <span class="login-icon">
              <KeyRound v-if="authenticated" :size="23" />
              <UserPlus v-else :size="23" />
            </span>
            <h2>{{ authenticated ? '当前已登录' : '登录 LinkNest 联巢' }}</h2>
            <p v-if="authenticated">你可以继续选购、查看订单或管理账号安全。</p>
            <p v-else>使用商城账号登录后，可以下单、查询订单和申请退款。</p>
          </div>

          <template v-if="authenticated">
            <div class="account-session-card">
              <span class="account-session-status"><i aria-hidden="true"></i> 登录状态正常</span>
              <strong>{{ accountLabel || '商城账号' }}</strong>
              <small>{{ accountSessionMeta }}</small>
            </div>

            <div v-if="accountView === 'overview'" class="account-overview-actions">
              <button class="account-submit" type="button" @click="emit('orders')">
                查看我的订单
              </button>
              <button
                v-if="token !== 'demo-session'"
                class="account-secondary"
                type="button"
                @click="accountView = 'password'"
              >
                修改密码
              </button>
              <button class="account-logout" type="button" @click="emit('logout')">
                <LogOut :size="17" />
                {{ token === 'demo-session' ? '退出体验账号' : '退出当前账号' }}
              </button>
            </div>

            <form v-else class="account-form account-password-form" @submit.prevent="submitPasswordChange">
              <button class="account-back" type="button" @click="accountView = 'overview'">
                ← 返回账号概览
              </button>
              <label class="account-field">
                <span>当前密码</span>
                <input
                  v-model="currentPassword"
                  type="password"
                  autocomplete="current-password"
                  placeholder="请输入当前密码"
                  required
                />
              </label>
              <label class="account-field">
                <span>新密码</span>
                <input
                  v-model="newPassword"
                  type="password"
                  autocomplete="new-password"
                  placeholder="8—64位，必须包含字母和数字"
                  required
                />
              </label>
              <label class="account-field">
                <span>确认新密码</span>
                <input
                  v-model="confirmNewPassword"
                  type="password"
                  autocomplete="new-password"
                  placeholder="请再次输入新密码"
                  required
                />
              </label>

              <p v-if="message" class="account-message" role="alert">{{ message }}</p>

              <button class="account-submit" type="submit" :disabled="busy">
                <LoaderCircle v-if="busy" :size="18" class="spin" />
                {{ busy ? '正在修改' : '修改密码' }}
              </button>
            </form>
          </template>

          <template v-else-if="demoMode">
            <div class="account-demo">
              <strong>当前为体验模式</strong>
              <span>商城服务暂时不可用，可以先使用体验账号浏览页面。</span>
              <button class="account-submit" type="button" @click="emit('demo-login')">
                使用体验账号
              </button>
            </div>
          </template>

          <template v-else>
            <div class="account-tabs" role="tablist" aria-label="账号操作">
              <button
                type="button"
                :class="{ active: mode === 'login' }"
                @click="switchMode('login')"
              >
                登录
              </button>
              <button
                type="button"
                :class="{ active: mode === 'register' }"
                @click="switchMode('register')"
              >
                注册
              </button>
            </div>

            <form class="account-form" @submit.prevent="submitAccount">
              <label class="account-field">
                <span>用户名</span>
                <input
                  v-model="username"
                  autocomplete="username"
                  placeholder="4—32位小写字母、数字或下划线"
                  required
                />
              </label>
              <label class="account-field">
                <span>密码</span>
                <input
                  v-model="password"
                  type="password"
                  :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
                  placeholder="8—64位，必须包含字母和数字"
                  required
                />
              </label>
              <label v-if="mode === 'register'" class="account-field">
                <span>确认密码</span>
                <input
                  v-model="confirmPassword"
                  type="password"
                  autocomplete="new-password"
                  placeholder="请再次输入密码"
                  required
                />
              </label>

              <p v-if="message" class="account-message" role="alert">{{ message }}</p>

              <button class="account-submit" type="submit" :disabled="busy">
                <LoaderCircle v-if="busy" :size="18" class="spin" />
                {{ busy ? '正在提交' : mode === 'register' ? '注册并登录' : '登录' }}
              </button>
            </form>
          </template>

          <small v-if="!authenticated || accountView === 'password'">密码仅以 BCrypt 哈希保存，页面不会保存明文密码。</small>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
