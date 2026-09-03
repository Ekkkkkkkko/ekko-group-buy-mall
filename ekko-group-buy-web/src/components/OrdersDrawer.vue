<script setup>
import { LoaderCircle, RotateCcw, X } from 'lucide-vue-next'

defineProps({
  open: Boolean,
  orders: {
    type: Array,
    default: () => [],
  },
  loading: Boolean,
  refundingId: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['close', 'refund'])

const statusMap = {
  CREATE: '订单处理中',
  PAY_WAIT: '待付款',
  PAY_SUCCESS: '等待拼成',
  MARKET: '拼购成功',
  DEAL_DONE: '已完成',
  WAIT_REFUND: '退款处理中',
  CLOSE: '已关闭',
}

function formatDate(value) {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function canRefund(status) {
  return ['CREATE', 'PAY_WAIT', 'PAY_SUCCESS', 'MARKET', 'DEAL_DONE'].includes(status)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="open" class="drawer-overlay" @click.self="emit('close')">
        <aside class="orders-drawer" role="dialog" aria-modal="true" aria-label="我的订单">
          <header class="drawer-header">
            <div>
              <span>LinkNest 联巢</span>
              <strong>我的订单</strong>
            </div>
            <button type="button" aria-label="关闭订单面板" @click="emit('close')">
              <X :size="21" />
            </button>
          </header>

          <div v-if="loading" class="orders-loading">
            <LoaderCircle :size="28" class="spin" />
            <span>正在加载订单</span>
          </div>

          <div v-else-if="!orders.length" class="orders-empty">
            <span class="brand-mark brand-mark--large" aria-hidden="true">
              <i></i><i></i><i></i><i></i>
            </span>
            <strong>还没有购买记录</strong>
            <p>选好适合家的设备后，可以在这里查看订单和拼购进度。</p>
          </div>

          <div v-else class="order-list">
            <article v-for="order in orders" :key="order.orderId" class="order-item">
              <div class="order-item-head">
                <span :class="`order-status order-status--${order.status?.toLowerCase()}`">
                  {{ statusMap[order.status] || order.status }}
                </span>
                <small>{{ formatDate(order.orderTime) }}</small>
              </div>
              <h3>{{ order.productName }}</h3>
              <p>订单号 {{ order.orderId }}</p>
              <div class="order-item-footer">
                <div>
                  <small>{{ order.marketType === 1 ? '拼团实付' : '本单实付' }}</small>
                  <strong>¥{{ Number(order.payAmount ?? order.totalAmount).toFixed(0) }}</strong>
                </div>
                <button
                  v-if="canRefund(order.status)"
                  type="button"
                  :disabled="refundingId === order.orderId"
                  @click="emit('refund', order)"
                >
                  <RotateCcw :size="15" />
                  {{ refundingId === order.orderId ? '正在申请' : '申请退款' }}
                </button>
              </div>
            </article>
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>
