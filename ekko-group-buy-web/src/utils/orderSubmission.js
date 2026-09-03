const DEFAULT_ORDER_ERROR = '订单暂时没有提交成功，请稍后重试'

export function resolveOrderSubmissionError(error) {
  if (error?.message === '支付功能尚未配置') {
    return '支付功能暂未开通，请稍后再试'
  }
  return DEFAULT_ORDER_ERROR
}
