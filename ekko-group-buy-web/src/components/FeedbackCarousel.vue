<script setup>
import { ArrowLeft, ArrowRight } from 'lucide-vue-next'
import { ref } from 'vue'

const stories = [
  {
    quote: '三室两厅或墙体较多，可以优先看看支持 Mesh 的设备。',
    name: '全屋覆盖',
    role: 'Mesh 组网建议',
    detail: '多房间也能稳定连接',
  },
  {
    quote: '家里手机、电脑和电视多，选设备时要把同时上网的数量算进去。',
    name: '多设备上网',
    role: '性能选择建议',
    detail: '办公、游戏和影音同时进行',
  },
  {
    quote: '想省一点可以邀请好友一起拼，人数和剩余时间都能随时查看。',
    name: '好友拼购',
    role: '拼购方式说明',
    detail: '价格和进度看得明白',
  },
]

const active = ref(0)

function move(direction) {
  active.value = (active.value + direction + stories.length) % stories.length
}
</script>

<template>
  <div class="feedback-carousel">
    <div class="feedback-avatar-stack" aria-hidden="true">
      <span v-for="(story, index) in stories" :key="story.name" :class="{ active: index === active }">
        {{ story.name.slice(0, 1) }}
      </span>
    </div>
    <div class="feedback-copy">
      <Transition name="quote" mode="out-in">
        <blockquote :key="active">“{{ stories[active].quote }}”</blockquote>
      </Transition>
      <div class="feedback-meta">
        <div>
          <strong>{{ stories[active].name }}</strong>
          <span>{{ stories[active].role }} · {{ stories[active].detail }}</span>
        </div>
        <div class="carousel-controls">
          <button type="button" aria-label="上一条用户反馈" @click="move(-1)">
            <ArrowLeft :size="20" />
          </button>
          <button type="button" aria-label="下一条用户反馈" @click="move(1)">
            <ArrowRight :size="20" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
