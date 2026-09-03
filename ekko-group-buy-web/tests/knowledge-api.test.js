import assert from 'node:assert/strict'
import test from 'node:test'

import {
  askKnowledgeBaseStream,
  KnowledgeApiError,
  uploadKnowledgeDocument,
} from '../src/api/knowledge.js'

function streamingResponse(parts) {
  const encoder = new TextEncoder()
  return {
    ok: true,
    status: 200,
    body: new ReadableStream({
      start(controller) {
        for (const part of parts) controller.enqueue(encoder.encode(part))
        controller.close()
      },
    }),
  }
}

test('uploads the selected chunk strategy as multipart data', async t => {
  const originalWindow = globalThis.window
  const originalFetch = globalThis.fetch
  globalThis.window = {
    setTimeout,
    clearTimeout,
  }

  let request
  globalThis.fetch = async (url, options) => {
    request = { url, options }
    return {
      ok: true,
      status: 201,
      json: async () => ({ id: 11, chunkStrategy: 'TITLE' }),
    }
  }
  t.after(() => {
    globalThis.window = originalWindow
    globalThis.fetch = originalFetch
  })

  const result = await uploadKnowledgeDocument(
    {
      file: new Blob(['pdf-content'], { type: 'application/pdf' }),
      title: '易展组网教程',
      productModel: 'TL-TEST',
      chunkStrategy: 'TITLE',
    },
    'admin-token',
  )

  assert.equal(request.url, '/chat-api/api/v1/documents')
  assert.equal(request.options.method, 'POST')
  assert.equal(request.options.headers.Authorization, 'Bearer admin-token')
  assert.equal(request.options.body.get('title'), '易展组网教程')
  assert.equal(request.options.body.get('productModel'), 'TL-TEST')
  assert.equal(request.options.body.get('chunkStrategy'), 'TITLE')
  assert.equal(result.chunkStrategy, 'TITLE')
})

test('parses fragmented SSE events and exposes answer chunks immediately', async t => {
  const originalFetch = globalThis.fetch
  let request
  globalThis.fetch = async (url, options) => {
    request = { url, options }
    return streamingResponse([
      'data: {"type":"PROGRESS","data":"\u6b63\u5728\u68c0',
      '\u7d22"}\n\ndata: {"type":"REFERENCE","data":[{"title":"\u5b89\u88c5\u8bf4\u660e"}]}\n\n',
      'data: {"type":"ANSWER","data":"\u7b2c\u4e00\u6bb5"}\n\ndata: {"type":"ANSWER","data":"\u7b2c\u4e8c\u6bb5"}\n\n',
      'data: {"type":"COMPLETE","data":{"conversationId":"server-1","answer":"\u7b2c\u4e00\u6bb5\u7b2c\u4e8c\u6bb5","sources":[{"title":"\u5b89\u88c5\u8bf4\u660e"}]}}\n\n',
    ])
  }
  t.after(() => {
    globalThis.fetch = originalFetch
  })

  const progress = []
  const references = []
  const chunks = []
  const completed = []
  const result = await askKnowledgeBaseStream('如何安装？', {
    clientId: 'client-1',
    conversationId: 'server-old',
    onProgress: value => progress.push(value),
    onReference: value => references.push(value),
    onAnswer: value => chunks.push(value),
    onComplete: value => completed.push(value),
  })

  assert.equal(request.url, '/chat-api/api/v1/chat/stream')
  assert.equal(request.options.method, 'POST')
  assert.equal(request.options.headers.Accept, 'text/event-stream')
  assert.deepEqual(JSON.parse(request.options.body), {
    question: '如何安装？',
    clientId: 'client-1',
    conversationId: 'server-old',
  })
  assert.deepEqual(progress, ['正在检索'])
  assert.deepEqual(references, [[{ title: '安装说明' }]])
  assert.deepEqual(chunks, ['第一段', '第二段'])
  assert.equal(completed.length, 1)
  assert.deepEqual(result, {
    conversationId: 'server-1',
    answer: '第一段第二段',
    sources: [{ title: '安装说明' }],
  })
})

test('turns a server ERROR event into a customer service error', async t => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => streamingResponse([
    'data: {"type":"ERROR","data":"\u6a21\u578b\u6682\u65f6不可用"}\n\n',
  ])
  t.after(() => {
    globalThis.fetch = originalFetch
  })

  await assert.rejects(
    askKnowledgeBaseStream('测试', { clientId: 'client-1' }),
    error => error instanceof KnowledgeApiError && error.message === '模型暂时不可用',
  )
})
