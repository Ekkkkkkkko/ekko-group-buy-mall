<script setup>
import { computed } from 'vue'
import { renderMarkdown } from '../utils/renderMarkdown'

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
})

const renderedContent = computed(() => renderMarkdown(props.content))
</script>

<template>
  <!-- MarkdownIt 禁止原始 HTML，并过滤 javascript: 等危险链接。 -->
  <div class="markdown-message" v-html="renderedContent"></div>
</template>
