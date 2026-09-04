import assert from 'node:assert/strict'
import test from 'node:test'

import { queryProducts } from '../src/api/mall.js'

test('enriches homepage products with activity prices from product details', async t => {
  const originalWindow = globalThis.window
  const originalFetch = globalThis.fetch
  globalThis.window = { setTimeout, clearTimeout }

  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    if (url === '/mall-api/api/v1/products') {
      return response([
        { productId: 'router-active', basePrice: 299 },
        { productId: 'router-regular', basePrice: 199 },
      ])
    }
    if (url.includes('router-active')) {
      return response({
        productId: 'router-active',
        basePrice: 299,
        groupBuyMarket: {
          activityId: 1001,
          originalPrice: 299,
          deductionPrice: 50,
          payPrice: 249,
        },
      })
    }
    return response({ productId: 'router-regular', basePrice: 199, groupBuyMarket: null })
  }
  t.after(() => {
    globalThis.window = originalWindow
    globalThis.fetch = originalFetch
  })

  const products = await queryProducts('guest user')

  assert.equal(requests.length, 3)
  assert.equal(
    requests[1].url,
    '/mall-api/api/v1/products/router-active?userId=guest%20user',
  )
  assert.equal(products[0].groupBuyMarket.payPrice, 249)
  assert.equal(products[1].groupBuyMarket, null)
})

test('keeps the base product when one activity-price request fails', async t => {
  const originalWindow = globalThis.window
  const originalFetch = globalThis.fetch
  globalThis.window = { setTimeout, clearTimeout }

  globalThis.fetch = async url => {
    if (url === '/mall-api/api/v1/products') {
      return response([{ productId: 'router-fallback', basePrice: 399 }])
    }
    return {
      ok: false,
      status: 503,
      json: async () => ({}),
    }
  }
  t.after(() => {
    globalThis.window = originalWindow
    globalThis.fetch = originalFetch
  })

  const products = await queryProducts('guest-1')

  assert.deepEqual(products, [{ productId: 'router-fallback', basePrice: 399 }])
})

function response(data) {
  return {
    ok: true,
    status: 200,
    json: async () => ({ code: '0000', info: '成功', data }),
  }
}
