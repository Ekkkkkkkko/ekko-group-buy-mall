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

test('removes inline source markers because sources are rendered as separate cards', () => {
  const html = renderMarkdown([
    '1. 登录管理界面 [资料1]。',
    '2. 打开无线设置[资料 12]，保存配置 [资料1]。',
  ].join('\n'))

  assert.doesNotMatch(html, /资料\s*\d+/)
  assert.match(html, /登录管理界面。/)
  assert.match(html, /打开无线设置，保存配置。/)
})
