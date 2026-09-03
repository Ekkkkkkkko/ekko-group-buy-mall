import assert from 'node:assert/strict'
import test from 'node:test'

import {
  changeMallAccountPassword,
  loginMallAccount,
  logoutMallAccount,
  registerMallAccount,
} from '../src/api/mall.js'

test('calls mall account login and registration endpoints', async t => {
  const originalWindow = globalThis.window
  const originalFetch = globalThis.fetch
  globalThis.window = { setTimeout, clearTimeout }

  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return {
      ok: true,
      status: 200,
      json: async () => ({ code: '0000', info: '成功', data: 'jwt-token' }),
    }
  }
  t.after(() => {
    globalThis.window = originalWindow
    globalThis.fetch = originalFetch
  })

  await loginMallAccount({ username: 'ekko_01', password: 'ExamplePass123' })
  await registerMallAccount({ username: 'ekko_02', password: 'ExamplePass123' })

  assert.equal(requests[0].url, '/mall-api/api/v1/login/account/login')
  assert.equal(requests[0].options.method, 'POST')
  assert.deepEqual(JSON.parse(requests[0].options.body), {
    username: 'ekko_01',
    password: 'ExamplePass123',
  })
  assert.equal(requests[1].url, '/mall-api/api/v1/login/account/register')
})

test('sends bearer token for password change and logout', async t => {
  const originalWindow = globalThis.window
  const originalFetch = globalThis.fetch
  globalThis.window = { setTimeout, clearTimeout }

  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return {
      ok: true,
      status: 200,
      json: async () => ({ code: '0000', info: '成功' }),
    }
  }
  t.after(() => {
    globalThis.window = originalWindow
    globalThis.fetch = originalFetch
  })

  await changeMallAccountPassword(
    { currentPassword: 'ExamplePass123', newPassword: 'ChangedPass456' },
    'jwt-token',
  )
  await logoutMallAccount('jwt-token')

  assert.equal(requests[0].url, '/mall-api/api/v1/login/account/password')
  assert.equal(requests[0].options.method, 'PUT')
  assert.equal(requests[0].options.headers.Authorization, 'Bearer jwt-token')
  assert.equal(requests[1].url, '/mall-api/api/v1/login/account/logout')
  assert.equal(requests[1].options.headers.Authorization, 'Bearer jwt-token')
})
