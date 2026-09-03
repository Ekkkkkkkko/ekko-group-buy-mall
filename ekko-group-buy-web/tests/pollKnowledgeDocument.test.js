import assert from 'node:assert/strict'
import test from 'node:test'

import { pollKnowledgeDocument } from '../src/utils/pollKnowledgeDocument.js'

test('polls until the document is published', async () => {
  const responses = [
    { id: 7, status: 'PARSING' },
    { id: 7, status: 'PARSING' },
    { id: 7, status: 'INDEXING' },
    { id: 7, status: 'PUBLISHED' },
  ]
  const updates = []

  const result = await pollKnowledgeDocument({
    documentId: 7,
    query: async () => responses.shift(),
    onUpdate: document => updates.push(document.status),
    intervalMs: 0,
  })

  assert.equal(result.status, 'PUBLISHED')
  assert.deepEqual(updates, ['PARSING', 'PARSING', 'INDEXING', 'PUBLISHED'])
})

test('stops immediately when the document failed', async () => {
  let calls = 0

  const result = await pollKnowledgeDocument({
    documentId: 7,
    query: async () => {
      calls += 1
      return { id: 7, status: 'FAILED' }
    },
    onUpdate: () => {},
    intervalMs: 0,
  })

  assert.equal(result.status, 'FAILED')
  assert.equal(calls, 1)
})

test('aborting after an update prevents another query', async () => {
  const controller = new AbortController()
  let calls = 0

  await assert.rejects(
    pollKnowledgeDocument({
      documentId: 7,
      query: async () => {
        calls += 1
        return { id: 7, status: 'PARSING' }
      },
      onUpdate: () => controller.abort(),
      signal: controller.signal,
      intervalMs: 0,
    }),
    error => error.name === 'AbortError',
  )

  assert.equal(calls, 1)
})
