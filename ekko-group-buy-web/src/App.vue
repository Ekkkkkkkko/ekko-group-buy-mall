<script setup>
import { gsap } from 'gsap'
import {
  ArrowDown,
  ArrowRight,
  Check,
  FileText,
  PackageCheck,
  Router,
  ShieldCheck,
  Truck,
  UsersRound,
  Wifi,
  Zap,
} from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  createPayOrder,
  logoutMallAccount,
  queryProductDetail,
  queryProducts,
  queryUserOrders,
  refundOrder,
} from './api/mall'
import ChatAssistant from './components/ChatAssistant.vue'
import AdminLogin from './components/AdminLogin.vue'
import CustomerServicePage from './components/CustomerServicePage.vue'
import DocumentManager from './components/DocumentManager.vue'
import { renderPaymentForm } from './utils/paymentForm'
import { resolveOrderSubmissionError } from './utils/orderSubmission'
import FeedbackCarousel from './components/FeedbackCarousel.vue'
import LoginDialog from './components/LoginDialog.vue'
import OrdersDrawer from './components/OrdersDrawer.vue'
import ProductGrid from './components/ProductGrid.vue'
import PurchaseDrawer from './components/PurchaseDrawer.vue'
import SiteNav from './components/SiteNav.vue'
import heroImage from './assets/router-hero-cutout-v3.png'
import { ADMIN_TOKEN_KEY } from './api/knowledge'
import {
  clearStoredMallSession,
  isMallAuthenticationError,
  MALL_ACCOUNT_NAME_KEY,
  MALL_TOKEN_KEY,
  MALL_USER_ID_KEY,
  readStoredMallSession,
  resolveJwtSession,
  storeMallSession,
} from './utils/mallSession'

const root = ref(null)
const currentHash = ref(window.location.hash)
const adminToken = ref(window.sessionStorage.getItem(ADMIN_TOKEN_KEY) || '')
const products = ref([])
const productsLoading = ref(true)
const productsError = ref('')
const demoMode = ref(false)
const selectedProduct = ref(null)
const selectedDetail = ref(null)
const detailLoading = ref(false)
const purchaseOpen = ref(false)
const loginOpen = ref(false)
const ordersOpen = ref(false)
const submitting = ref(false)
const ordersLoading = ref(false)
const orders = ref([])
const refundingId = ref('')
const toast = ref('')
const token = ref('')
const userId = ref('')
const accountName = ref('')
const authType = ref('')
const sessionExpiresAt = ref(null)
const guestId =
  window.localStorage.getItem('mangetuan_guest_id') ||
  `guest-${window.crypto.randomUUID()}`
window.localStorage.setItem('mangetuan_guest_id', guestId)
let toastTimer = null
let sessionExpiryTimer = null
let gsapContext = null
let homeInitialized = false

const authenticated = computed(() => Boolean(token.value && userId.value))
const accountLabel = computed(() => {
  if (token.value === 'demo-session') return '体验账号'
  if (accountName.value) return accountName.value
  if (authType.value === 'weixin') return '微信用户'
  return '商城账号'
})
const isDocumentManager = computed(() => currentHash.value === '#/documents')
const isCustomerService = computed(() => currentHash.value === '#/assistant')
const isHome = computed(() => !isDocumentManager.value && !isCustomerService.value)

function handleAdminAuthenticated(value) {
  adminToken.value = value
  window.sessionStorage.setItem(ADMIN_TOKEN_KEY, value)
}

function logoutAdmin() {
  adminToken.value = ''
  window.sessionStorage.removeItem(ADMIN_TOKEN_KEY)
}

const marqueeItems = [
  'Wi-Fi 7',
  '按户型选设备',
  '2.5G 高速网口',
  '一起拼更划算',
  '支付宝安全支付',
  '订单进度随时看',
]

function clearMallSession() {
  token.value = ''
  userId.value = ''
  accountName.value = ''
  authType.value = ''
  sessionExpiresAt.value = null
  orders.value = []
  clearStoredMallSession()
  if (sessionExpiryTimer) window.clearTimeout(sessionExpiryTimer)
  sessionExpiryTimer = null
}

function scheduleSessionExpiry(expiresAt) {
  if (sessionExpiryTimer) window.clearTimeout(sessionExpiryTimer)
  sessionExpiryTimer = null
  sessionExpiresAt.value = expiresAt || null
  if (!expiresAt) return
  const remaining = expiresAt - Date.now()
  if (remaining <= 0) {
    clearMallSession()
    return
  }
  sessionExpiryTimer = window.setTimeout(() => {
    clearMallSession()
    ordersOpen.value = false
    showToast('登录已过期，请重新登录')
  }, remaining)
}

function restoreMallSession() {
  const session = readStoredMallSession()
  if (!session) {
    clearMallSession()
    return false
  }
  token.value = session.token
  userId.value = session.userId
  accountName.value = session.accountName
  authType.value = session.authType
  scheduleSessionExpiry(session.expiresAt)
  return true
}

function handleMallAuthenticationError(error) {
  if (!isMallAuthenticationError(error)) return false
  clearMallSession()
  purchaseOpen.value = false
  ordersOpen.value = false
  loginOpen.value = true
  showToast('登录已失效，请重新登录')
  return true
}

restoreMallSession()

function normalizeProducts(data) {
  return data.map((product) => ({
    ...product,
    basePrice: Number(product.basePrice || 0),
    imageUrl: product.imageUrl || '',
  }))
}

async function loadProducts() {
  productsLoading.value = true
  productsError.value = ''
  try {
    const data = await queryProducts()
    if (!Array.isArray(data)) throw new Error('设备数据格式不正确')
    products.value = normalizeProducts(data)
    demoMode.value = false
    if (token.value === 'demo-session') logoutDemoSession()
  } catch (error) {
    products.value = []
    productsError.value = '商品暂时没有加载出来'
    demoMode.value = true
  } finally {
    productsLoading.value = false
  }
}

function logoutDemoSession() {
  clearMallSession()
}

async function selectProduct(product) {
  selectedProduct.value = product
  selectedDetail.value = product
  purchaseOpen.value = true
  detailLoading.value = !demoMode.value

  if (demoMode.value) return

  try {
    selectedDetail.value = await queryProductDetail(
      product.productId,
      userId.value || guestId,
    )
    selectedDetail.value.imageUrl = selectedDetail.value.imageUrl || product.imageUrl || ''
  } catch (error) {
    showToast('拼购信息暂时没有加载出来，仍可单独购买')
  } finally {
    detailLoading.value = false
  }
}

function showToast(message) {
  toast.value = message
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toast.value = ''
  }, 3200)
}

function openLogin() {
  restoreMallSession()
  loginOpen.value = true
}

function openOrdersFromAccount() {
  loginOpen.value = false
  openOrders()
}

async function logoutMall() {
  const currentToken = token.value
  loginOpen.value = false
  let serverLogoutConfirmed = currentToken === 'demo-session'
  try {
    if (currentToken && currentToken !== 'demo-session') {
      await logoutMallAccount(currentToken)
      serverLogoutConfirmed = true
    }
  } catch (error) {
    serverLogoutConfirmed = handleMallAuthenticationError(error)
  } finally {
    clearMallSession()
    loginOpen.value = false
  }
  showToast(serverLogoutConfirmed ? '已安全退出商城账号' : '已清除本机登录，服务端退出暂未确认')
}

function handlePasswordChanged() {
  clearMallSession()
  loginOpen.value = false
  showToast('密码修改成功，请使用新密码重新登录')
}

function handleAuthenticated(payload) {
  const jwt = typeof payload === 'string' ? payload : payload?.token
  const session = resolveJwtSession(jwt)
  if (!session) {
    showToast('登录凭证无效或已过期，请重新登录')
    return
  }
  const stored = storeMallSession({
    token: jwt,
    accountName: typeof payload === 'object' ? payload?.username : '',
  })
  if (!stored) {
    showToast('登录凭证无效或已过期，请重新登录')
    return
  }
  token.value = stored.token
  userId.value = stored.userId
  accountName.value = stored.accountName
  authType.value = stored.authType
  scheduleSessionExpiry(stored.expiresAt)
  loginOpen.value = false
  showToast('登录成功，可以开始选择并下单')
}

function handleDemoLogin() {
  const stored = storeMallSession({ token: 'demo-session', accountName: '体验账号' })
  token.value = stored.token
  userId.value = stored.userId
  accountName.value = stored.accountName
  authType.value = stored.authType
  scheduleSessionExpiry(stored.expiresAt)
  loginOpen.value = false
  showToast('已进入体验账号')
}

async function submitOrder(selection) {
  if (!authenticated.value) {
    purchaseOpen.value = false
    loginOpen.value = true
    showToast('请先登录，再继续下单')
    return
  }

  submitting.value = true
  const order = {
    userId: userId.value,
    productId: selectedProduct.value.productId,
    marketType: selection.marketType,
    activityId: selection.activityId,
    teamId: selection.teamId,
  }

  if (demoMode.value) {
    await new Promise((resolve) => window.setTimeout(resolve, 650))
    const market = selectedDetail.value?.groupBuyMarket
    orders.value = [
      {
        id: Date.now(),
        productId: selectedProduct.value.productId,
        productName: selectedProduct.value.productName,
        orderId: `DEMO${Date.now()}`,
        orderTime: new Date().toISOString(),
        totalAmount: selectedProduct.value.basePrice,
        payAmount: selection.marketType === 1 ? market?.payPrice : selectedProduct.value.basePrice,
        marketType: selection.marketType,
        status: selection.marketType === 1 ? 'PAY_SUCCESS' : 'PAY_WAIT',
      },
      ...orders.value,
    ]
    submitting.value = false
    purchaseOpen.value = false
    showToast('体验订单已创建，不会产生实际支付')
    return
  }

  const paymentWindow = window.open('', 'mangetuan_payment')
  if (paymentWindow) {
    paymentWindow.document.write(
      '<title>正在前往支付宝</title><p style="font-family:sans-serif;padding:40px">订单创建中，请稍候…</p>',
    )
  }

  try {
    const paymentForm = await createPayOrder(order, token.value)
    purchaseOpen.value = false
    if (!paymentWindow) {
      showToast('订单已创建，请允许浏览器打开支付窗口')
      return
    }
    renderPaymentForm(paymentWindow, paymentForm)
    showToast('订单创建成功，正在前往支付宝')
  } catch (error) {
    paymentWindow?.close()
    if (!handleMallAuthenticationError(error)) showToast(resolveOrderSubmissionError(error))
  } finally {
    submitting.value = false
  }
}

async function openOrders() {
  if (!authenticated.value) {
    loginOpen.value = true
    showToast('登录后才能查看订单')
    return
  }

  ordersOpen.value = true
  ordersLoading.value = true
  if (demoMode.value) {
    ordersLoading.value = false
    return
  }

  try {
    const result = await queryUserOrders(userId.value, token.value)
    orders.value = result?.orderList || []
  } catch (error) {
    if (!handleMallAuthenticationError(error)) showToast('订单暂时没有加载出来，请稍后重试')
  } finally {
    ordersLoading.value = false
  }
}

async function handleRefund(order) {
  refundingId.value = order.orderId
  if (demoMode.value) {
    await new Promise((resolve) => window.setTimeout(resolve, 500))
    orders.value = orders.value.map((item) =>
      item.orderId === order.orderId ? { ...item, status: 'CLOSE' } : item,
    )
    refundingId.value = ''
    showToast('体验订单已退款')
    return
  }

  try {
    const result = await refundOrder(userId.value, order.orderId, token.value)
    orders.value = orders.value.map((item) =>
      item.orderId === order.orderId ? { ...item, status: result.status } : item,
    )
    showToast(result.info || '退款申请已提交')
  } catch (error) {
    if (!handleMallAuthenticationError(error)) showToast('退款申请暂时没有提交成功，请稍后重试')
  } finally {
    refundingId.value = ''
  }
}

function scrollToShop() {
  document.querySelector('#shop')?.scrollIntoView({ behavior: 'smooth' })
}

function openCustomerService() {
  window.location.hash = '#/assistant'
}

function startWithHeroProduct() {
  if (products.value.length) selectProduct(products.value[0])
  else scrollToShop()
}

function initMotion() {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  gsapContext = gsap.context(() => {
    gsap.from('.hero-reveal', {
      y: 38,
      opacity: 0,
      duration: 1,
      stagger: 0.1,
      ease: 'power3.out',
    })
    gsap.from('.hero-art', {
      x: 80,
      opacity: 0,
      scale: 0.92,
      duration: 1.3,
      ease: 'power3.out',
      delay: 0.15,
    })
  }, root.value)
}

async function initializeHome() {
  if (homeInitialized) return
  homeInitialized = true
  await loadProducts()
  await nextTick()
  initMotion()
}

function syncHashRoute() {
  currentHash.value = window.location.hash
  window.scrollTo({ top: 0 })
  if (isHome.value) initializeHome()
}

function handleMallSessionStorage(event) {
  if (
    event.key === null
    || [MALL_TOKEN_KEY, MALL_USER_ID_KEY, MALL_ACCOUNT_NAME_KEY].includes(event.key)
  ) {
    restoreMallSession()
  }
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') restoreMallSession()
}

watch(
  [purchaseOpen, loginOpen, ordersOpen],
  (states) => document.body.classList.toggle('overlay-open', states.some(Boolean)),
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('hashchange', syncHashRoute)
  window.addEventListener('storage', handleMallSessionStorage)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  if (isHome.value) initializeHome()
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncHashRoute)
  window.removeEventListener('storage', handleMallSessionStorage)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  gsapContext?.revert()
  if (toastTimer) window.clearTimeout(toastTimer)
  if (sessionExpiryTimer) window.clearTimeout(sessionExpiryTimer)
  document.body.classList.remove('overlay-open')
})
</script>

<template>
  <template v-if="isDocumentManager">
    <DocumentManager
      v-if="adminToken"
      :admin-token="adminToken"
      @logout="logoutAdmin"
      @unauthorized="logoutAdmin"
    />
    <AdminLogin v-else @authenticated="handleAdminAuthenticated" />
  </template>

  <CustomerServicePage v-else-if="isCustomerService" />

  <main v-else ref="root" class="page-shell mall-page">
    <SiteNav
      :authenticated="authenticated"
      :demo-mode="demoMode"
      :account-label="accountLabel"
      @login="openLogin"
      @orders="openOrders"
      @assistant="openCustomerService"
    />

    <section class="hero">
      <div class="hero-ambient" aria-hidden="true"></div>
      <div class="hero-copy">
        <p class="hero-kicker hero-reveal">路由器拼购专区 · 按需挑选</p>
        <h1 class="hero-reveal">好路由，<br />一起拼更省。</h1>
        <p class="hero-description hero-reveal">
          Wi-Fi 7、全屋 Mesh、2.5G 高速网口等设备集中选购。支持单独购买，也可以邀请朋友一起拼。
        </p>
        <div class="hero-actions hero-reveal">
          <button class="button button--primary" type="button" @click="scrollToShop">
            立即逛拼购
            <ArrowRight :size="19" />
          </button>
          <button class="button button--text" type="button" @click="startWithHeroProduct">
            查看拼购商品
            <ArrowDown :size="18" />
          </button>
        </div>
      </div>
      <figure class="hero-art">
        <img :src="heroImage" alt="Wi-Fi 7 路由器与 Mesh 设备组合" />
      </figure>
    </section>

    <section class="mall-category-strip" aria-label="热门选购方向">
      <div><strong>Wi-Fi 7</strong><span>新一代无线标准</span></div>
      <div><strong>全屋 Mesh</strong><span>大户型覆盖方案</span></div>
      <div><strong>2.5G 网口</strong><span>高速有线接入</span></div>
      <div><strong>移动 5G</strong><span>无固定宽带场景</span></div>
      <div><strong>智能客服</strong><span>参数与故障问答</span></div>
    </section>

    <div class="marquee" aria-label="产品能力">
      <div class="marquee-track">
        <template v-for="loop in 4" :key="loop">
          <span v-for="item in marqueeItems" :key="`${loop}-${item}`">
            {{ item }}
            <i></i>
          </span>
        </template>
      </div>
    </div>

    <section id="shop" class="section section--shop">
      <header class="commerce-section-heading">
        <div>
          <span>多人拼购</span>
          <h2>热门设备</h2>
        </div>
        <p>优惠和到手价以下单页面显示为准</p>
      </header>

      <ProductGrid
        :products="products"
        :loading="productsLoading"
        :error-message="productsError"
        @select="selectProduct"
        @retry="loadProducts"
      />

      <div id="service" class="assurance-strip">
        <div>
          <ShieldCheck :size="22" />
          <span><strong>付款更放心</strong>下单前看清商品和到手价</span>
        </div>
        <div>
          <Truck :size="22" />
          <span><strong>进度随时看</strong>订单和拼购进度一目了然</span>
        </div>
        <div>
          <PackageCheck :size="22" />
          <span><strong>售后更省心</strong>有需要可在订单中申请退款</span>
        </div>
      </div>
    </section>

    <section id="group-guide" class="section section--story">
      <div class="story-heading">
        <p>拼购攻略</p>
        <h2>三步选对设备，放心完成拼购</h2>
      </div>

      <div class="story-stack">
        <article class="story-card story-card--light">
          <div class="story-card-number">01</div>
          <div class="story-card-copy">
            <Wifi :size="31" />
            <h3>先看家有多大</h3>
            <p>小户型一台通常就够，大户型可以看看 Mesh 设备，让客厅、卧室都连得更稳。</p>
            <ul>
              <li><Check :size="16" /> 看看家里有几个房间</li>
              <li><Check :size="16" /> 手机、电视和电脑都要算上</li>
            </ul>
          </div>
        </article>

        <article class="story-card story-card--lime">
          <div class="story-card-number">02</div>
          <div class="story-card-copy">
            <UsersRound :size="31" />
            <h3>再决定怎么买</h3>
            <p>现在就要用可以直接买，想更划算就叫上朋友一起拼，或者加入已有拼单。</p>
            <ul>
              <li><Check :size="16" /> 一个人也能直接下单</li>
              <li><Check :size="16" /> 好友一起拼，进度随时看</li>
            </ul>
          </div>
        </article>

        <article class="story-card story-card--dark">
          <div class="story-card-number">03</div>
          <div class="story-card-copy">
            <Zap :size="31" />
            <h3>确认清楚再付款</h3>
            <p>选好商品和购买方式，看清优惠与到手价，再安心付款。</p>
            <ul>
              <li><Check :size="16" /> 付款前确认商品和到手价</li>
              <li><Check :size="16" /> 下单后到“我的订单”看进度</li>
            </ul>
          </div>
        </article>
      </div>
    </section>

    <section class="section section--feedback">
      <div class="feedback-heading">
        <span class="feedback-label"><Router :size="17" /> 常见选购场景</span>
        <h2>按户型挑选<span>更合适</span></h2>
        <p>不同户型、不同设备数量，可以采用不同的选购思路。</p>
      </div>
      <FeedbackCarousel />
    </section>

    <section class="section section--cta">
      <div class="cta-inner">
        <div class="cta-signal" aria-hidden="true">
          <i></i><i></i><i></i><i></i>
        </div>
        <h2>选好设备，<span>马上开拼</span></h2>
        <p>找到适合家的那台，单独购买或邀请朋友一起拼购。</p>
        <button class="button button--lime" type="button" @click="scrollToShop">
          返回热门拼购
          <ArrowRight :size="20" />
        </button>
      </div>
    </section>

    <footer class="site-footer">
      <div class="brand footer-brand">
        <span class="brand-name"><strong>LinkNest</strong><small>联巢</small></span>
      </div>
      <p>连接每个房间，也连接一起买的人。</p>
      <div>
        <button type="button" @click="scrollToShop">设备</button>
        <button type="button" @click="openOrders">订单</button>
        <button type="button" @click="openLogin">账号</button>
        <a href="#/documents"><FileText :size="14" />商家管理</a>
      </div>
      <small>© 2026 LinkNest 联巢 · 好路由，一起拼更省</small>
    </footer>

    <PurchaseDrawer
      :open="purchaseOpen"
      :product="selectedProduct"
      :detail="selectedDetail"
      :detail-loading="detailLoading"
      :authenticated="authenticated"
      :submitting="submitting"
      @close="purchaseOpen = false"
      @submit="submitOrder"
    />
    <LoginDialog
      :open="loginOpen"
      :demo-mode="demoMode"
      :authenticated="authenticated"
      :token="token"
      :account-label="accountLabel"
      :auth-type="authType"
      :expires-at="sessionExpiresAt"
      @close="loginOpen = false"
      @authenticated="handleAuthenticated"
      @demo-login="handleDemoLogin"
      @logout="logoutMall"
      @orders="openOrdersFromAccount"
      @password-changed="handlePasswordChanged"
    />
    <OrdersDrawer
      :open="ordersOpen"
      :orders="orders"
      :loading="ordersLoading"
      :refunding-id="refundingId"
      @close="ordersOpen = false"
      @refund="handleRefund"
    />
    <ChatAssistant />

    <Transition name="toast">
      <div v-if="toast" class="toast-message" role="status">{{ toast }}</div>
    </Transition>
  </main>
</template>
