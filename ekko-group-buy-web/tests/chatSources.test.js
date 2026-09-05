import assert from 'node:assert/strict'
import test from 'node:test'
import { groupChatSources, isUsefulSourcePreview } from '../src/utils/chatSources.js'

test('groups a document once while retaining original reference numbers and distinct sections', () => {
  const sources = [
    { documentId: 95, chunkId: 'c1', title: '复位说明', headingPath: '软件复位' },
    { documentId: 95, chunkId: 'c0', title: '复位说明', headingPath: '复位前须知' },
    { documentId: 40, chunkId: 'c3', title: '安装指南', headingPath: '易展按键' },
    { documentId: 95, chunkId: 'c2', title: '复位说明', headingPath: '软件复位' },
  ]
  const snapshot = JSON.stringify(sources)
  const documents = groupChatSources(sources)
  assert.equal(documents.length, 2)
  assert.deepEqual(documents[0].references, [1, 2, 4])
  assert.deepEqual(documents[0].sections, ['软件复位', '复位前须知'])
  assert.deepEqual(documents[1].references, [3])
  assert.equal(JSON.stringify(sources), snapshot)
})

test('does not merge distinct models by equal title or unknown document ids', () => {
  const documents = groupChatSources([
    { documentId: 1, title: '快速安装指南' },
    { documentId: 2, title: '快速安装指南' },
    { title: '快速安装指南' },
    { title: '快速安装指南' },
  ])
  assert.equal(documents.length, 4)
})

test('deduplicates identical images across documents by content instead of image id or signature', () => {
  const hash = 'a'.repeat(64)
  const documents = groupChatSources([
    { documentId: 1, images: [{ imageId: 1, sha256: hash, url: 'https://oss.test/one?signature=1' }] },
    { documentId: 2, images: [{ imageId: 2, sha256: hash, url: 'https://oss.test/two?signature=2' }] },
    { documentId: 3, images: [{ imageId: 3, url: `https://oss.test/legacy/${hash}.jpg?signature=3` }] },
  ])
  assert.equal(documents[0].images.length, 1)
  assert.equal(documents[1].images.length, 0)
  assert.equal(documents[2].images.length, 0)
})

test('keeps useful images without descriptions and deduplicates legacy signed urls', () => {
  const documents = groupChatSources([
    { images: [
      { url: 'https://oss.test/wiring.png?signature=old' },
      { url: 'https://oss.test/wiring.png?signature=new' },
      { imageId: 4, url: 'https://oss.test/ports.png' },
    ] },
  ])
  assert.equal(documents[0].images.length, 2)
})

test('ignores malformed sources and unsafe image links', () => {
  assert.deepEqual(groupChatSources(null), [])
  assert.deepEqual(groupChatSources([null]), [])
  const [document] = groupChatSources([{ images: [null, {}, { url: 'javascript:alert(1)' }, { url: 'data:image/png;base64,xxx' }] }])
  assert.deepEqual(document.images, [])
})

test('does not enlarge small extracted icons; valid diagrams remain visible', () => {
  assert.equal(isUsefulSourcePreview(45, 54), false)
  assert.equal(isUsefulSourcePreview(95, 95), false)
  assert.equal(isUsefulSourcePreview(0, 500), false)
  assert.equal(isUsefulSourcePreview(NaN, 500), false)
  assert.equal(isUsefulSourcePreview(640, 360), true)
  assert.equal(isUsefulSourcePreview(90, 500), true)
})
