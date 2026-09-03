const TERMINAL_STATUSES = new Set(['PUBLISHED', 'FAILED'])

function abortError() {
  return new DOMException('Aborted', 'AbortError')
}

function wait(intervalMs, signal) {
  return new Promise((resolve, reject) => {
    let timer
    const onAbort = () => {
      clearTimeout(timer)
      reject(abortError())
    }
    if (signal?.aborted) {
      onAbort()
      return
    }
    timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, intervalMs)
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

export async function pollKnowledgeDocument({
  documentId,
  query,
  onUpdate,
  signal,
  intervalMs = 3000,
}) {
  while (!signal?.aborted) {
    const document = await query(documentId)
    onUpdate(document)
    if (TERMINAL_STATUSES.has(document.status)) return document
    await wait(intervalMs, signal)
  }
  throw abortError()
}
