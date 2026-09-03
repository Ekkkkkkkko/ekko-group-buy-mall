import assert from 'node:assert/strict'
import test from 'node:test'

import {
  clearStoredMallSession,
  isMallAuthenticationError,
  MALL_ACCOUNT_NAME_KEY,
  MALL_TOKEN_KEY,
  MALL_USER_ID_KEY,
  readStoredMallSession,
  resolveJwtSession,
  storeMallSession,
} from '../src/utils/mallSession.js'

function memoryStorage(entries = []) {
  const values = new Map(entries)
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
    has: key => values.has(key),
  }
}

function jwt(payload) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.test-signature`
}

test('restores a non-expired jwt subject, login method and expiration time', () => {
  globalThis.window = { atob: value => Buffer.from(value, 'base64').toString('binary') }
  const expiresAt = Date.now() + 60_000

  const session = resolveJwtSession(jwt({
    sub: 'openid-1001',
    exp: expiresAt / 1000,
    authType: 'weixin',
  }))

  assert.equal(session.userId, 'openid-1001')
  assert.equal(session.expiresAt, expiresAt)
  assert.equal(session.authType, 'weixin')
})

test('stores and rereads the account session as one consistent state', () => {
  globalThis.window = { atob: value => Buffer.from(value, 'base64').toString('binary') }
  const storage = memoryStorage()
  const expiresAt = Date.now() + 60_000
  const token = jwt({ sub: 'USR_1001', exp: expiresAt / 1000, authType: 'account' })

  const stored = storeMallSession({ token, accountName: 'ekko_01' }, storage)
  const restored = readStoredMallSession(storage)

  assert.equal(stored.accountName, 'ekko_01')
  assert.equal(restored.token, token)
  assert.equal(restored.userId, 'USR_1001')
  assert.equal(restored.authType, 'account')
  assert.equal(restored.accountName, 'ekko_01')
})

test('rejects expired or malformed stored tokens', () => {
  globalThis.window = { atob: value => Buffer.from(value, 'base64').toString('binary') }

  assert.equal(resolveJwtSession(jwt({ sub: 'openid-1001', exp: 1 })), null)
  assert.equal(resolveJwtSession('not-a-jwt'), null)
})

test('recognizes backend business and http authentication errors', () => {
  assert.equal(isMallAuthenticationError({ code: '1006' }), true)
  assert.equal(isMallAuthenticationError({ code: '401' }), true)
  assert.equal(isMallAuthenticationError({ code: '1007' }), false)
})

test('clears token, user id and account label together', () => {
  const values = new Map([
    [MALL_TOKEN_KEY, 'token'],
    [MALL_USER_ID_KEY, 'openid'],
    [MALL_ACCOUNT_NAME_KEY, 'ekko_01'],
  ])
  const storage = {
    removeItem: key => values.delete(key),
  }

  clearStoredMallSession(storage)

  assert.equal(values.has(MALL_TOKEN_KEY), false)
  assert.equal(values.has(MALL_USER_ID_KEY), false)
  assert.equal(values.has(MALL_ACCOUNT_NAME_KEY), false)
})
