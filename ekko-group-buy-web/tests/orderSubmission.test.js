import assert from 'node:assert/strict'
import test from 'node:test'
import { resolveOrderSubmissionError } from '../src/utils/orderSubmission.js'

test('shows a clear message when payment is not configured', () => {
  assert.equal(
    resolveOrderSubmissionError({ message: '支付功能尚未配置' }),
    '支付功能暂未开通，请稍后再试',
  )
})

test('does not expose unexpected backend errors in the order toast', () => {
  assert.equal(
    resolveOrderSubmissionError({ message: 'SQL internal error' }),
    '订单暂时没有提交成功，请稍后重试',
  )
})
