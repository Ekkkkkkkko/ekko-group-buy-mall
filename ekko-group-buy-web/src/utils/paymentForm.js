export function renderPaymentForm(paymentWindow, paymentForm) {
  if (!paymentWindow) return false

  paymentWindow.document.open()
  paymentWindow.document.write(paymentForm)
  paymentWindow.document.close()
  return true
}
