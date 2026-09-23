-- =============================================
-- RAG 知识库管理表
-- 把硬编码的商城规则段落搬到数据库，让管理员可在后台 CRUD
-- =============================================
USE mall;

DROP TABLE IF EXISTS `knowledge_section`;
CREATE TABLE `knowledge_section` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(100) NOT NULL                COMMENT '段落名（例：商城简介、购物须知）',
  `type`        VARCHAR(20)  NOT NULL                COMMENT '内容类型：text=纯文本 / list=列表(JSON数组) / kv=自由JSON对象',
  `content`     LONGTEXT     NOT NULL                COMMENT '内容：纯文本 / JSON数组字符串 / JSON对象字符串',
  `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '排序，越小越靠前',
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否启用：1=启用 0=禁用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识库段落';

-- 初始数据：把原 RAGService 硬编码的 6 段搬过来
INSERT INTO `knowledge_section` (`name`, `type`, `content`, `sort_order`, `enabled`) VALUES
('商城简介', 'text',
 'AI 智能购物商城是一家综合性在线购物平台，提供各类商品，包括电子产品、服装、食品、家居用品等。',
 10, 1),

('购物须知', 'list',
 '["用户注册后可以浏览商品、加入购物车、下单购买。", "下单前请确认收货地址准确无误。", "支付方式支持多种在线支付方式。", "下单后可在\\"我的订单\\"中查看订单状态。"]',
 20, 1),

('售后规则', 'list',
 '["商品签收后 7 天内，如出现质量问题，可申请退货退款。", "未发货订单可随时取消；已发货订单需联系客服处理。", "退款将在审核通过后 3-7 个工作日原路返回。", "如有售后问题，请通过在线客服或售后电话联系。"]',
 30, 1),

('优惠券说明', 'list',
 '["用户可在优惠券中心领取优惠券。", "每张优惠券有使用门槛和有效期，请在有效期内使用。", "每个订单只能使用一张优惠券。"]',
 40, 1),

('常见问题', 'list',
 '["Q: 如何修改收货地址？A: 在确认订单页可以添加、编辑或删除收货地址。", "Q: 如何取消订单？A: 在\\"我的订单\\"中，待付款状态的订单可以直接取消。", "Q: 忘记密码怎么办？A: 请联系客服重置密码。", "Q: 如何查看物流信息？A: 在\\"我的订单\\"中点击订单详情查看物流状态。"]',
 50, 1),

('AI 助手具备的工具', 'list',
 '["addCart — 加购商品（支持商品名 + 数量）", "searchProduct — 按关键词/价格区间搜索商品", "clearCart — 一键清空购物车", "getFavorites — 查看我的收藏夹", "addFavoritesToCart — 把收藏里的商品加入购物车"]',
 60, 1);
