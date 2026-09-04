<script setup>
import { ArrowLeft, KeyRound, LoaderCircle, LockKeyhole } from 'lucide-vue-next'
import { ref } from 'vue'
import { loginKnowledgeAdmin } from '../api/knowledge'

const emit = defineEmits(['authenticated'])

const username = ref('Ekko')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

async function login() {
  errorMessage.value = ''
  submitting.value = true
  try {
    const session = await loginKnowledgeAdmin({
      username: username.value.trim(),
      password: password.value,
    })
    emit('authenticated', session.token)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="admin-login">
    <header class="admin-login__nav">
      <a href="#/" class="admin-login__back">
        <ArrowLeft :size="18" />
        返回商城
      </a>
      <a href="#/" class="admin-login__brand">LinkNest <span>联巢</span></a>
      <span>知识库管理</span>
    </header>

    <section class="admin-login__panel" aria-labelledby="admin-login-title">
      <form class="admin-login__form" @submit.prevent="login">
        <div class="admin-login__form-header">
          <span class="admin-login__mark" aria-hidden="true">
            <LockKeyhole :size="22" :stroke-width="1.8" />
          </span>
          <p>LinkNest Admin</p>
          <h1 id="admin-login-title">登录知识库</h1>
          <span>验证管理员身份后，即可管理知识库文档。</span>
        </div>

        <label>
          <span>管理员账号</span>
          <input
            v-model="username"
            autocomplete="username"
            maxlength="64"
            placeholder="请输入管理员账号"
            required
          />
        </label>
        <label>
          <span>管理员密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            maxlength="128"
            placeholder="请输入管理员密码"
            required
          />
        </label>

        <p v-if="errorMessage" class="admin-login__error" aria-live="polite">
          {{ errorMessage }}
        </p>

        <button type="submit" :disabled="submitting">
          <LoaderCircle v-if="submitting" class="is-spinning" :size="18" />
          <KeyRound v-else :size="18" />
          {{ submitting ? '正在登录…' : '登录知识库' }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.admin-login {
  position: relative;
  min-height: 100dvh;
  padding: 0;
  color: var(--ink);
  background: #f7f7f8;
}

.admin-login__nav {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  width: 100%;
  min-height: 58px;
  margin: 0 auto;
  padding: 0 clamp(18px, 3vw, 34px);
  border-bottom: 1px solid #ececec;
  background: rgba(255, 255, 255, 0.96);
}

.admin-login__back,
.admin-login__brand {
  color: inherit;
  text-decoration: none;
}

.admin-login__back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  min-height: 36px;
  padding: 0 11px;
  border-radius: 8px;
  color: #4b4b4b;
  font-size: 13px;
  font-weight: 600;
  transition: background 180ms ease, transform 180ms ease;
}

.admin-login__back:hover {
  background: #f2f2f2;
  transform: translateX(-2px);
}

.admin-login__brand {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.admin-login__brand span {
  margin-left: 8px;
  color: #6d6d6d;
  font-size: 13px;
  letter-spacing: 0.08em;
}

.admin-login__nav > span {
  justify-self: end;
  color: #555;
  font-size: 12px;
  font-weight: 600;
}

.admin-login__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100dvh - 58px);
  margin: 0 auto;
  padding: 48px 20px 72px;
}

.admin-login__form {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: min(100%, 480px);
  padding: clamp(30px, 4vw, 42px);
  border: 1px solid #e2e2e2;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 18px 54px rgba(0, 0, 0, 0.07);
}

.admin-login__form-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 4px;
  text-align: center;
}

.admin-login__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin-bottom: 18px;
  border-radius: 11px;
  color: #fff;
  background: #202123;
}

.admin-login__form-header p {
  margin: 0 0 9px;
  color: #777;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.admin-login__form-header h1 {
  margin: 0;
  font-size: clamp(2rem, 4vw, 2.45rem);
  letter-spacing: -0.045em;
  line-height: 1.08;
  text-wrap: balance;
}

.admin-login__form-header > span:last-child {
  max-width: 320px;
  margin-top: 12px;
  color: #777;
  font-size: 13px;
  line-height: 1.65;
}

.admin-login__form label {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.admin-login__form label span {
  color: #34362f;
  font-size: 13px;
  font-weight: 700;
}

.admin-login__form input {
  width: 100%;
  height: 54px;
  padding: 0 16px;
  border: 1px solid #dcdcdc;
  border-radius: 12px;
  outline: 0;
  color: var(--ink);
  background: #f7f7f8;
  transition: border-color 180ms ease, box-shadow 180ms ease, background 180ms ease;
}

.admin-login__form input::placeholder {
  color: #95978f;
}

.admin-login__form input:focus {
  border-color: #8d8d8d;
  background: #fff;
  box-shadow: 0 0 0 4px rgba(32, 33, 35, 0.07);
}

.admin-login__form button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 54px;
  margin-top: 4px;
  border: 0;
  border-radius: 12px;
  color: #fff;
  background: #202123;
  box-shadow: 0 10px 22px rgba(32, 33, 35, 0.16);
  cursor: pointer;
  font-weight: 800;
  transition: background 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.admin-login__form button:not(:disabled):hover {
  background: #343541;
  box-shadow: 0 13px 26px rgba(32, 33, 35, 0.2);
  transform: translateY(-1px);
}

.admin-login__form button:not(:disabled):active {
  box-shadow: 0 8px 16px rgba(27, 29, 24, 0.14);
  transform: translateY(1px) scale(0.995);
}

.admin-login__form button:disabled {
  cursor: wait;
  opacity: 0.58;
}

.admin-login__error {
  margin: -4px 0 0;
  padding: 10px 12px;
  border-radius: 9px;
  color: #9b3f35;
  background: #f8e5e1;
  font-size: 11px;
}

.is-spinning {
  animation: admin-login-spin 0.9s linear infinite;
}

@keyframes admin-login-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 760px) {
  .admin-login {
    padding: 0;
  }

  .admin-login__nav {
    grid-template-columns: 1fr auto;
    min-height: 58px;
  }

  .admin-login__brand {
    display: none;
  }

  .admin-login__panel {
    min-height: calc(100dvh - 58px);
    padding: 30px 18px 52px;
  }

  .admin-login__form {
    padding: 30px 24px 34px;
    border-radius: 16px;
  }
}
</style>
