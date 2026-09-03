<script setup>
import { Menu, Package, UserRound, X } from 'lucide-vue-next'
import { ref } from 'vue'

const props = defineProps({
  authenticated: Boolean,
  demoMode: Boolean,
  accountLabel: String,
})

const emit = defineEmits(['login', 'orders', 'assistant'])
const mobileOpen = ref(false)

function jumpTo(target) {
  mobileOpen.value = false
  document.querySelector(target)?.scrollIntoView({ behavior: 'smooth' })
}

function openAssistant() {
  mobileOpen.value = false
  emit('assistant')
}
</script>

<template>
  <header class="site-nav">
    <a class="brand" href="#" aria-label="LinkNest 联巢首页">
      <span class="brand-name"><strong>LinkNest</strong><small>联巢</small></span>
      <span class="brand-market-label">拼购商城</span>
    </a>

    <nav class="desktop-links" aria-label="主导航">
      <button type="button" @click="jumpTo('#shop')">
        <small>01</small>
        <span>拼购首页</span>
      </button>
      <button type="button" @click="jumpTo('#group-guide')">
        <small>02</small>
        <span>拼购攻略</span>
      </button>
      <button type="button" @click="openAssistant">
        <small>03</small>
        <span>智能客服</span>
      </button>
    </nav>

    <div class="nav-actions">
      <span v-if="demoMode" class="demo-indicator">体验模式</span>
      <button class="icon-button desktop-action" type="button" aria-label="查看我的订单" @click="emit('orders')">
        <Package :size="18" />
      </button>
      <button
        class="nav-login desktop-action"
        :class="{ 'nav-login--authenticated': authenticated }"
        type="button"
        :aria-label="authenticated ? `已登录：${props.accountLabel || '商城账号'}，打开账号中心` : '登录商城账号'"
        @click="emit('login')"
      >
        <UserRound :size="17" />
        <i v-if="authenticated" class="nav-login-status" aria-hidden="true"></i>
        {{ authenticated ? '已登录' : '登录' }}
      </button>
      <button
        class="icon-button mobile-menu-button"
        type="button"
        :aria-label="mobileOpen ? '关闭导航' : '打开导航'"
        @click="mobileOpen = !mobileOpen"
      >
        <X v-if="mobileOpen" :size="20" />
        <Menu v-else :size="20" />
      </button>
    </div>

    <div v-if="mobileOpen" class="mobile-menu">
      <button type="button" @click="jumpTo('#shop')">拼购首页</button>
      <button type="button" @click="jumpTo('#group-guide')">拼购攻略</button>
      <button type="button" @click="openAssistant">智能客服</button>
      <button type="button" @click="mobileOpen = false; emit('orders')">我的订单</button>
      <button type="button" @click="mobileOpen = false; emit('login')">
        {{ authenticated ? `已登录 · ${props.accountLabel || '商城账号'}` : '账号登录' }}
      </button>
    </div>
  </header>
</template>
