import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
  typographer: false,
})

const defaultLinkOpen = markdown.renderer.rules.link_open

markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer')

  if (defaultLinkOpen) {
    return defaultLinkOpen(tokens, index, options, env, self)
  }
  return self.renderToken(tokens, index, options)
}

export function renderMarkdown(content) {
  const normalized = String(content || '').replace(/[ \t]*\[资料\s*\d+\][ \t]*/g, '')
  return markdown.render(normalized)
}
