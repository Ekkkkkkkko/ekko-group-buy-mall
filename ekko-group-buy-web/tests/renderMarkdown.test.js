import assert from 'node:assert/strict'
import test from 'node:test'
import { renderMarkdown } from '../src/utils/renderMarkdown.js'

test('renders headings, emphasis, and lists instead of exposing Markdown markers', () => {
  const html = renderMarkdown('### 排查步骤\n\n1. **检查电源**\n2. 重启路由器')

  assert.match(html, /<h3>排查步骤<\/h3>/)
  assert.match(html, /<strong>检查电源<\/strong>/)
  assert.match(html, /<ol>/)
  assert.doesNotMatch(html, /\*\*检查电源\*\*/)
})

test('does not execute raw HTML or unsafe links returned by the model', () => {
  const html = renderMarkdown('<script>alert(1)</script>\n\n[危险链接](javascript:alert(1))')

  assert.doesNotMatch(html, /<script>/)
  assert.doesNotMatch(html, /href="javascript:/)
  assert.match(html, /&lt;script&gt;/)
})

test('opens safe links in a separate tab without opener access', () => {
  const html = renderMarkdown('[产品资料](https://example.com/manual)')

  assert.match(html, /target="_blank"/)
  assert.match(html, /rel="noopener noreferrer"/)
})
