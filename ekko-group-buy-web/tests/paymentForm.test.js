import assert from 'node:assert/strict'
import test from 'node:test'

import { renderPaymentForm } from '../src/utils/paymentForm.js'

test('renders the SDK payment form without submitting it a second time', () => {
  const calls = []
  const document = {
    open: () => calls.push('open'),
    write: value => calls.push(['write', value]),
    close: () => calls.push('close'),
    get forms() {
      throw new Error('payment form must not be submitted manually')
    },
  }
  const paymentForm = '<form></form><script>document.forms[0].submit();</script>'

  assert.equal(renderPaymentForm({ document }, paymentForm), true)
  assert.deepEqual(calls, ['open', ['write', paymentForm], 'close'])
})

test('does nothing when the payment window was blocked', () => {
  assert.equal(renderPaymentForm(null, '<form></form>'), false)
})
