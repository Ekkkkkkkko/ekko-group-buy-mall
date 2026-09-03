-- 仅用于当前非生产 RDS 测试数据整理；执行前必须先做目标表备份。
START TRANSACTION;

-- 清理历史测试交易，避免过期队伍、锁单明细和已完成通知继续干扰联调。
DELETE FROM notify_task;
DELETE FROM group_buy_order_list;
DELETE FROM group_buy_order;

-- 保留三档基础直减规则，便于覆盖不同价格带的拼团试算。
INSERT INTO group_buy_discount
    (discount_id, discount_name, discount_desc, discount_type, market_plan, market_expr, tag_id)
VALUES
    ('R20OFF01', '入门路由器拼团直减20元', '双人拼团，成团后每件直减20元', 0, 'ZJ', '20', NULL),
    ('R30OFF01', '主流路由器拼团直减30元', '双人拼团，成团后每件直减30元', 0, 'ZJ', '30', NULL),
    ('R50OFF01', '高端路由器拼团直减50元', '双人拼团，成团后每件直减50元', 0, 'ZJ', '50', NULL)
ON DUPLICATE KEY UPDATE
    discount_name = VALUES(discount_name),
    discount_desc = VALUES(discount_desc),
    discount_type = VALUES(discount_type),
    market_plan = VALUES(market_plan),
    market_expr = VALUES(market_expr),
    tag_id = VALUES(tag_id);

-- 8 个现有商品全部保留；活动统一延长到 2030 年底，同一测试账号最多可参与 100 次。
UPDATE group_buy_activity
SET activity_name = CASE activity_id
        WHEN 2026072901 THEN 'TL-7DR3630长期双人拼团'
        WHEN 2026072902 THEN 'TL-7DR3650长期双人拼团'
        WHEN 2026072903 THEN 'TL-7DR5130长期双人拼团'
        WHEN 2026072904 THEN 'TL-7DR6430长期双人拼团'
        WHEN 2026072905 THEN 'TL-7DR6560长期双人拼团'
        WHEN 2026072906 THEN 'TL-7DR7270长期双人拼团'
        WHEN 2026072907 THEN 'TL-7DR7290长期双人拼团'
        WHEN 2026072908 THEN 'TL-TR970G长期双人拼团'
    END,
    discount_id = CASE
        WHEN activity_id IN (2026072901, 2026072902) THEN 'R20OFF01'
        WHEN activity_id IN (2026072903, 2026072904) THEN 'R30OFF01'
        ELSE 'R50OFF01'
    END,
    group_type = 1,
    take_limit_count = 100,
    target = 2,
    valid_time = 180,
    status = 1,
    start_time = '2026-09-01 00:00:00',
    end_time = '2030-12-31 23:59:59',
    tag_id = NULL,
    tag_scope = NULL
WHERE activity_id IN (
    2026072901, 2026072902, 2026072903, 2026072904,
    2026072905, 2026072906, 2026072907, 2026072908
);

-- 清理不再使用的活动、商品映射、营销 SKU 和优惠规则。
DELETE FROM sc_sku_activity
WHERE goods_id NOT IN (
    'TL-7DR3630', 'TL-7DR3650', 'TL-7DR5130', 'TL-7DR6430',
    'TL-7DR6560', 'TL-7DR7270', 'TL-7DR7290', 'TL-TR970G'
);
DELETE FROM sku
WHERE goods_id NOT IN (
    'TL-7DR3630', 'TL-7DR3650', 'TL-7DR5130', 'TL-7DR6430',
    'TL-7DR6560', 'TL-7DR7270', 'TL-7DR7290', 'TL-TR970G'
);
DELETE FROM group_buy_activity
WHERE activity_id NOT IN (
    2026072901, 2026072902, 2026072903, 2026072904,
    2026072905, 2026072906, 2026072907, 2026072908
);
DELETE FROM group_buy_discount
WHERE discount_id NOT IN ('R20OFF01', 'R30OFF01', 'R50OFF01');

COMMIT;
