import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const mallTarget = env.VITE_PAY_MALL_TARGET || 'http://127.0.0.1:8092'
  const groupChatTarget = env.VITE_GROUP_CHAT_TARGET || 'http://127.0.0.1:8095'

  return {
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/mall-api': {
          target: mallTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/mall-api/, ''),
        },
        '/chat-api': {
          target: groupChatTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/chat-api/, ''),
        },
      },
    },
  }
})
