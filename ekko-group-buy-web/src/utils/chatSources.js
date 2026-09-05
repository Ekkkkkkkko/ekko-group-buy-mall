// 只改变引用的展示结构，保留后端分片顺序和原始资料编号，便于追溯。
export function groupChatSources(sources = []) {
  const groups = new Map()
  const seenImages = new Set()
  if (!Array.isArray(sources)) return []

  sources.forEach((source, index) => {
    if (!source || typeof source !== 'object') return
    const key = source.documentId != null
      ? `document-${source.documentId}`
      : `chunk-${source.chunkId || index}`
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        title: source.title || '相关产品资料',
        references: [],
        sections: [],
        images: [],
      })
    }
    const group = groups.get(key)
    group.references.push(index + 1)
    if (source.headingPath && !group.sections.includes(source.headingPath)) {
      group.sections.push(source.headingPath)
    }
    for (const image of Array.isArray(source.images) ? source.images : []) {
      const identity = imageIdentity(image)
      if (!identity || seenImages.has(identity)) continue
      seenImages.add(identity)
      group.images.push({ ...image, key: identity })
    }
  })
  return [...groups.values()]
}

function imageIdentity(image) {
  if (!image || typeof image.url !== 'string') return null
  try {
    const url = new URL(image.url)
    if (!['https:', 'http:'].includes(url.protocol)) return null
    // 新接口使用内容指纹；兼容旧接口内容寻址的 OSS 文件名，不比较短期签名参数。
    const hash = image.sha256 || url.pathname.match(/\/([a-f0-9]{64})\.[a-z0-9]+$/i)?.[1]
    if (typeof hash === 'string' && /^[a-f0-9]{64}$/i.test(hash)) return `sha-${hash.toLowerCase()}`
    if (image.imageId != null) return `image-${image.imageId}`
    return `${url.origin}${url.pathname}`
  } catch {
    return null
  }
}

export function isUsefulSourcePreview(width, height) {
  // 小型按钮/品牌图标保留在原资料中，不放大为问答配图；截图、接线图等继续展示。
  return Number.isFinite(width) && Number.isFinite(height)
    && width > 0 && height > 0 && Math.max(width, height) >= 96
}
