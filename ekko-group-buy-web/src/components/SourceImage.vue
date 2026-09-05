<script setup>
import { ref, watch } from 'vue'
import { isUsefulSourcePreview } from '../utils/chatSources'

const props = defineProps({ image: { type: Object, required: true } })
const state = ref('loading')
watch(() => props.image.url, () => { state.value = 'loading' })

function onLoad(event) {
  const { naturalWidth, naturalHeight } = event.target
  state.value = isUsefulSourcePreview(naturalWidth, naturalHeight) ? 'ready' : 'hidden'
}
</script>

<template>
  <a
    v-if="state !== 'hidden'"
    class="source-preview"
    :hidden="state !== 'ready'"
    :href="image.url"
    target="_blank"
    rel="noopener noreferrer"
    :title="image.description || '查看资料原图（新窗口）'"
  >
    <img
      :src="image.url"
      :alt="image.description || '资料插图，点击查看原图'"
      referrerpolicy="no-referrer"
      @load="onLoad"
      @error="state = 'hidden'"
    />
  </a>
</template>

<style scoped>
.source-preview {
  display: block;
  box-sizing: border-box;
  max-width: min(100%, 234px);
  margin-top: 10px;
  padding: 6px;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  background: #fff;
}
.source-preview[hidden] { display: none; }
.source-preview img {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 160px;
  object-fit: contain;
}
.source-preview:focus-visible { outline: 2px solid #555; outline-offset: 3px; }
.source-preview:hover { border-color: #aaa; }
</style>
