<script setup>
import { ArrowUpRight, Radio, UsersRound } from 'lucide-vue-next'
import { computed } from 'vue'

const SLOT_COUNT = 8

const props = defineProps({
  products: {
    type: Array,
    default: () => [],
  },
  loading: Boolean,
  errorMessage: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['select', 'retry'])

const productSlots = computed(() =>
  Array.from({ length: SLOT_COUNT }, (_, index) => ({
    slotNumber: String(index + 1).padStart(2, '0'),
    product: props.products[index] || null,
  })),
)

const inventoryMessage = computed(() => {
  if (props.errorMessage) return '商品暂时没有加载出来'
  if (!props.products.length) return '更多好物即将上架'
  return `为你找到 ${Math.min(props.products.length, SLOT_COUNT)} 款设备`
})

function priceOf(product) {
  return product.groupBuyMarket?.payPrice ?? product.basePrice
}

function displayName(product) {
  const originalName = String(product?.productName || '')
  const model = String(product?.productModel || '').trim()
  let name = originalName.replace(/^TP-LINK\s*/i, '')

  if (model && name.startsWith(model)) name = name.slice(model.length).trim()
  name = name.replace(/\s*[（(][^）)]*[）)]\s*$/, '').trim()
  return name || originalName
}

function handleImageError(event) {
  event.currentTarget.closest('.product-media')?.classList.add('image-missing')
}
</script>

<template>
  <div v-if="loading" class="product-loading-grid" aria-label="商品加载中">
    <article v-for="index in SLOT_COUNT" :key="index" class="product-skeleton">
      <div class="product-skeleton-media"></div>
      <div class="product-skeleton-copy"><i></i><i></i><i></i></div>
    </article>
  </div>

  <div v-else class="product-inventory">
    <div class="inventory-note" role="status">
      <span class="inventory-status" :class="{ error: errorMessage }" aria-hidden="true"></span>
      <span>{{ inventoryMessage }}</span>
      <button v-if="errorMessage" type="button" @click="emit('retry')">重新加载</button>
    </div>

    <div class="product-grid">
      <template v-for="slot in productSlots" :key="slot.slotNumber">
        <article
          v-if="slot.product"
          class="product-card"
          tabindex="0"
          role="button"
          :aria-label="`查看${slot.product.productName}`"
          @click="emit('select', slot.product)"
          @keydown.enter="emit('select', slot.product)"
          @keydown.space.prevent="emit('select', slot.product)"
        >
          <div class="product-media" :class="{ 'image-missing': !slot.product.imageUrl }">
            <span v-if="slot.product.groupBuyMarket" class="product-promo-badge">拼购</span>
            <img
              v-if="slot.product.imageUrl"
              :src="slot.product.imageUrl"
              :alt="slot.product.productName"
              @error="handleImageError"
            />
            <div class="product-image-empty" aria-hidden="true">
              <span class="empty-product-mark"><i></i><i></i><i></i><i></i></span>
              <small>暂无图片</small>
            </div>
          </div>

          <div class="product-card-content">
            <div class="product-topline">
              <span>{{ slot.product.productModel || '精选路由器' }}</span>
              <span v-if="slot.product.groupBuyMarket" class="team-state">
                <UsersRound :size="14" />
                多人拼
              </span>
              <span v-else class="team-state">
                <Radio :size="14" />
                单独购买
              </span>
            </div>
            <div class="product-copy">
              <h3>{{ displayName(slot.product) }}</h3>
              <p>{{ slot.product.productDesc || '查看详情，选择适合你家的设备' }}</p>
            </div>
            <div class="product-card-footer">
              <div class="price-lockup">
                <small>{{ slot.product.groupBuyMarket ? '活动价' : '日常价' }}</small>
                <strong><sup>¥</sup>{{ priceOf(slot.product) }}</strong>
                <del v-if="slot.product.groupBuyMarket">日常价 ¥{{ slot.product.basePrice }}</del>
              </div>
              <span class="product-arrow" aria-hidden="true">
                去拼购
                <ArrowUpRight :size="16" />
              </span>
            </div>
          </div>
        </article>

        <article v-else class="product-slot-empty" :aria-label="`空设备位 ${slot.slotNumber}`">
          <div class="empty-slot-head">
            <span>{{ slot.slotNumber }}</span>
            <i aria-hidden="true"></i>
          </div>
          <div class="empty-slot-center" aria-hidden="true">
            <span class="empty-product-mark"><i></i><i></i><i></i><i></i></span>
          </div>
          <div class="empty-slot-foot">
            <span>新品即将上架</span>
            <i aria-hidden="true"></i>
          </div>
        </article>
      </template>
    </div>
  </div>
</template>
