<script setup>
import {
  ArrowRight,
  Check,
  Clock3,
  Minus,
  Plus,
  ShieldCheck,
  UsersRound,
  X,
} from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

const props = defineProps({
  open: Boolean,
  product: {
    type: Object,
    default: null,
  },
  detail: {
    type: Object,
    default: null,
  },
  detailLoading: Boolean,
  authenticated: Boolean,
  submitting: Boolean,
})

const emit = defineEmits(['close', 'submit'])
const purchaseMode = ref('normal')
const selectedTeamId = ref('')

const activeProduct = computed(() => props.detail || props.product)
const market = computed(() => activeProduct.value?.groupBuyMarket || null)
const teams = computed(() => market.value?.teamList || [])
const normalPrice = computed(() => Number(activeProduct.value?.basePrice || 0))
const groupPrice = computed(() => Number(market.value?.payPrice || normalPrice.value))
const currentPrice = computed(() =>
  purchaseMode.value === 'group' ? groupPrice.value : normalPrice.value,
)

watch(
  () => props.product?.productId,
  () => {
    purchaseMode.value = 'normal'
    selectedTeamId.value = ''
  },
)

watch(
  market,
  (value) => {
    if (value) purchaseMode.value = 'group'
  },
  { immediate: true },
)

function formatPrice(value) {
  return Number(value || 0).toFixed(0)
}

function submit() {
  emit('submit', {
    marketType: purchaseMode.value === 'group' ? 1 : 0,
    activityId: purchaseMode.value === 'group' ? market.value?.activityId : null,
    teamId:
      purchaseMode.value === 'group' && selectedTeamId.value
        ? selectedTeamId.value
        : null,
  })
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="open && activeProduct" class="drawer-overlay" @click.self="emit('close')">
        <aside class="purchase-drawer" role="dialog" aria-modal="true" aria-label="选择下单方式">
          <header class="drawer-header">
            <div>
              <span>确认订单</span>
              <strong>{{ activeProduct.productName }}</strong>
            </div>
            <button type="button" aria-label="关闭购买面板" @click="emit('close')">
              <X :size="21" />
            </button>
          </header>

          <div class="drawer-content">
            <div class="drawer-product">
              <img
                v-if="activeProduct.imageUrl"
                :src="activeProduct.imageUrl"
                :alt="activeProduct.productName"
              />
              <div v-else class="drawer-product-image-empty" aria-label="设备图片待上传">
                <span class="empty-product-mark" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
              </div>
              <div>
                <span>{{ activeProduct.productModel }}</span>
                <p>{{ activeProduct.productSpecs }}</p>
              </div>
            </div>

            <section class="purchase-mode-section">
              <div class="drawer-section-title">
                <strong>购买方式</strong>
                <span v-if="detailLoading">正在查找可拼的队伍</span>
              </div>
              <div class="purchase-mode-grid">
                <button
                  type="button"
                  :class="{ active: purchaseMode === 'normal' }"
                  @click="purchaseMode = 'normal'; selectedTeamId = ''"
                >
                  <span class="mode-check"><Check :size="15" /></span>
                  <small>单独购买</small>
                  <strong>¥{{ formatPrice(normalPrice) }}</strong>
                  <em>即买即用，无需成团</em>
                </button>
                <button
                  type="button"
                  :disabled="!market"
                  :class="{ active: purchaseMode === 'group' }"
                  @click="purchaseMode = 'group'"
                >
                  <span class="mode-check"><Check :size="15" /></span>
                  <small>一起拼团</small>
                  <strong>{{ market ? `¥${formatPrice(groupPrice)}` : '暂无拼购' }}</strong>
                  <em v-if="market">拼成后安排发货</em>
                  <em v-else>这款商品暂时不能拼</em>
                </button>
              </div>
            </section>

            <section v-if="purchaseMode === 'group' && market" class="team-section">
              <div class="drawer-section-title">
                <strong>选择拼团队伍</strong>
                <span>也可以自己开团</span>
              </div>

              <label class="team-option team-option--new" :class="{ active: !selectedTeamId }">
                <input v-model="selectedTeamId" type="radio" value="" />
                <span class="team-avatar"><Plus :size="19" /></span>
                <span>
                  <strong>自己开一团</strong>
                  <small>邀请朋友一起凑齐人数</small>
                </span>
                <i><Check :size="15" /></i>
              </label>

              <label
                v-for="team in teams"
                :key="team.teamId"
                class="team-option"
                :class="{ active: selectedTeamId === team.teamId }"
              >
                <input v-model="selectedTeamId" type="radio" :value="team.teamId" />
                <span class="team-avatar"><UsersRound :size="18" /></span>
                <span class="team-info">
                  <span>
                    <strong>还差 {{ Math.max(team.targetCount - team.completeCount, 0) }} 人</strong>
                    <small><Clock3 :size="13" /> {{ team.validTimeCountdown }}</small>
                  </span>
                  <span class="team-progress">
                    <i
                      :style="{
                        width: `${Math.min((team.completeCount / team.targetCount) * 100, 100)}%`,
                      }"
                    ></i>
                  </span>
                </span>
                <i><Check :size="15" /></i>
              </label>

              <div v-if="!teams.length" class="empty-team">
                暂时没有进行中的队伍，你可以先开一团。
              </div>
            </section>

            <div class="quantity-row">
              <span>
                <strong>购买数量</strong>
                <small>拼团设备每次限购 1 件</small>
              </span>
              <div>
                <button type="button" disabled aria-label="减少数量"><Minus :size="16" /></button>
                <strong>1</strong>
                <button type="button" disabled aria-label="增加数量"><Plus :size="16" /></button>
              </div>
            </div>

            <div class="purchase-assurance">
              <ShieldCheck :size="18" />
              <span>付款前请确认商品和到手价，随后前往支付宝完成支付。</span>
            </div>
          </div>

          <footer class="drawer-footer">
            <div>
              <small>{{ purchaseMode === 'group' ? '拼团应付' : '本单应付' }}</small>
              <strong><sup>¥</sup>{{ formatPrice(currentPrice) }}</strong>
            </div>
            <button type="button" :disabled="submitting" @click="submit">
              {{ submitting ? '正在提交' : authenticated ? '确认并去支付宝' : '登录后下单' }}
              <ArrowRight :size="19" />
            </button>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>
