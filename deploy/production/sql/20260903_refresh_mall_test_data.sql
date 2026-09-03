-- 仅用于当前非生产 RDS 测试数据整理；执行前必须先做目标表备份。
START TRANSACTION;

-- 保留已支付订单作为订单查询样本，删除失败、关闭和未完成的历史测试订单。
DELETE FROM pay_order
WHERE status <> 'PAY_SUCCESS';

-- 现有 8 个带 OSS 图片的路由器商品均可继续测试，统一恢复上架状态。
UPDATE product
SET status = 1
WHERE product_id IN (
    'TL-7DR3630', 'TL-7DR3650', 'TL-7DR5130', 'TL-7DR6430',
    'TL-7DR6560', 'TL-7DR7270', 'TL-7DR7290', 'TL-TR970G'
);

-- 删除未纳入本次测试目录的下架商品；当前盘点没有命中项。
DELETE FROM product
WHERE status = 0
  AND product_id NOT IN (
      'TL-7DR3630', 'TL-7DR3650', 'TL-7DR5130', 'TL-7DR6430',
      'TL-7DR6560', 'TL-7DR7270', 'TL-7DR7290', 'TL-TR970G'
  );

COMMIT;
