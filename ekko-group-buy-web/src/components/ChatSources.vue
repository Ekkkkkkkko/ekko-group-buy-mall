<script setup>
import { computed, ref } from 'vue'
import { ChevronDown, FileText } from 'lucide-vue-next'
import { groupChatSources } from '../utils/chatSources'
import SourceImage from './SourceImage.vue'

const props = defineProps({ sources: { type: Array, default: () => [] } })
const documents = computed(() => groupChatSources(props.sources))
const expanded = ref(false)
</script>

<template>
  <details v-if="documents.length" class="answer-sources" @toggle="expanded = $event.target.open">
    <summary>
      <FileText :size="15" aria-hidden="true" />
      <span>参考资料 <span class="answer-sources-count">· {{ documents.length }} 篇</span></span>
      <span class="answer-sources-action">{{ expanded ? '收起' : '展开' }}</span>
      <ChevronDown class="answer-sources-chevron" :size="15" aria-hidden="true" />
    </summary>
    <ol v-if="expanded" class="answer-source-documents">
      <li v-for="document in documents" :key="document.key" class="answer-source-document">
        <div class="answer-source-heading">
          <strong>{{ document.title }}</strong>
          <span>资料 {{ document.references.join('、') }}</span>
        </div>
        <ul v-if="document.sections.length" class="answer-source-sections">
          <li v-for="section in document.sections" :key="section">{{ section }}</li>
        </ul>
        <div v-if="document.images.length" class="answer-source-previews">
          <SourceImage v-for="image in document.images" :key="image.url" :image="image" />
        </div>
      </li>
    </ol>
  </details>
</template>

<style scoped>
.answer-sources {
  margin-top: 20px;
  border-top: 1px solid #e8e8e8;
  color: #626262;
  font-size: 13px;
  line-height: 1.6;
}
.answer-sources > summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 2px;
  cursor: pointer;
  list-style: none;
  border-radius: 4px;
  transition: color 160ms ease, background-color 160ms ease;
}
.answer-sources > summary::-webkit-details-marker { display: none; }
.answer-sources > summary:hover { color: #222; background: #fafafa; }
.answer-sources > summary:focus-visible { outline: 2px solid #666; outline-offset: 3px; }
.answer-sources-count { color: #888; font-variant-numeric: tabular-nums; }
.answer-sources-action { margin-left: auto; font-size: 12px; }
.answer-sources-chevron { flex-shrink: 0; transition: transform 160ms ease; }
.answer-sources[open] .answer-sources-chevron { transform: rotate(180deg); }
.answer-source-documents { margin: 0; padding: 0; list-style: none; }
.answer-source-document { padding: 12px 0; border-top: 1px solid #eee; }
.answer-source-heading { display: flex; align-items: baseline; gap: 8px 16px; }
.answer-source-heading strong { min-width: 0; color: #444; font-weight: 600; overflow-wrap: anywhere; }
.answer-source-heading > span { flex-shrink: 0; margin-left: auto; color: #888; font-size: 11px; }
.answer-source-sections { margin: 5px 0 0; padding-left: 16px; color: #777; font-size: 12px; }
.answer-source-sections li { padding: 1px 0; overflow-wrap: anywhere; }
.answer-source-previews { display: flex; flex-wrap: wrap; gap: 0 10px; }
@media (max-width: 560px) {
  .answer-source-heading { flex-direction: column; gap: 2px; }
  .answer-source-heading > span { margin-left: 0; }
}
@media (prefers-reduced-motion: reduce) {
  .answer-sources > summary, .answer-sources-chevron { transition: none; }
}
</style>
