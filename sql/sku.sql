-- ============================================================
-- 商品 SKU 多规格支持
-- ============================================================

-- 1. SKU 表（每个商品可以有多个 SKU）
CREATE TABLE IF NOT EXISTS product_sku (
  id INT PRIMARY KEY AUTO_INCREMENT,
  product_id INT NOT NULL COMMENT '所属商品',
  sku_code VARCHAR(64) DEFAULT NULL COMMENT 'SKU 编码',
  specs VARCHAR(500) NOT NULL COMMENT '规格组合 JSON，如 {"颜色":"黑","容量":"256G"}',
  price DECIMAL(10,2) NOT NULL COMMENT 'SKU 价格',
  stock INT NOT NULL DEFAULT 0 COMMENT 'SKU 库存',
  image VARCHAR(500) DEFAULT NULL COMMENT 'SKU 图片（覆盖商品主图）',
  sales INT DEFAULT 0 COMMENT '销量',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 给 cart 表加 sku_id
ALTER TABLE cart ADD COLUMN sku_id INT DEFAULT NULL COMMENT 'SKU ID';
ALTER TABLE cart ADD INDEX idx_user_product_sku (user_id, product_id, sku_id);

-- 3. 给 order_item 表加 sku_id 和 spec_desc
ALTER TABLE order_item ADD COLUMN sku_id INT DEFAULT NULL COMMENT 'SKU ID';
ALTER TABLE order_item ADD COLUMN spec_desc VARCHAR(200) DEFAULT NULL COMMENT '规格描述快照';

-- ============================================================
-- 给现有商品生成 SKU 数据
-- 电脑/手机类：颜色+容量双规格；服装：颜色+尺码双规格；食品：单规格
-- ============================================================

-- product_id=1 华为MateBook X Pro：颜色 × 容量
INSERT INTO product_sku (product_id, sku_code, specs, price, stock, image, sales) VALUES
(1, 'SKU-HWMB-1', '{"颜色":"深空灰","容量":"16G+512G"}', 12999.00, 30, 'https://picsum.photos/seed/p1-1/400/400', 80),
(1, 'SKU-HWMB-2', '{"颜色":"深空灰","容量":"16G+1T"}', 14999.00, 20, 'https://picsum.photos/seed/p1-2/400/400', 56),
(1, 'SKU-HWMB-3', '{"颜色":"皓月银","容量":"16G+512G"}', 12999.00, 25, 'https://picsum.photos/seed/p1-3/400/400', 60),
(1, 'SKU-HWMB-4', '{"颜色":"皓月银","容量":"16G+1T"}', 14999.00, 15, 'https://picsum.photos/seed/p1-4/400/400', 60);

-- product_id=2 iPhone 15 Pro Max：颜色 × 容量
INSERT INTO product_sku (product_id, sku_code, specs, price, stock, image, sales) VALUES
(2, 'SKU-IP15-1', '{"颜色":"原色钛金属","容量":"256G"}', 9999.00, 50, 'https://picsum.photos/seed/p2-1/400/400', 180),
(2, 'SKU-IP15-2', '{"颜色":"原色钛金属","容量":"512G"}', 10999.00, 40, 'https://picsum.photos/seed/p2-2/400/400', 150),
(2, 'SKU-IP15-3', '{"颜色":"原色钛金属","容量":"1T"}', 12999.00, 30, 'https://picsum.photos/seed/p2-3/400/400', 100),
(2, 'SKU-IP15-4', '{"颜色":"蓝色钛金属","容量":"256G"}', 9999.00, 50, 'https://picsum.photos/seed/p2-4/400/400', 82),
(2, 'SKU-IP15-5', '{"颜色":"蓝色钛金属","容量":"512G"}', 10999.00, 40, 'https://picsum.photos/seed/p2-5/400/400', 0),
(2, 'SKU-IP15-6', '{"颜色":"黑色钛金属","容量":"256G"}', 9999.00, 45, 'https://picsum.photos/seed/p2-6/400/400', 0),
(2, 'SKU-IP15-7', '{"颜色":"白色钛金属","容量":"512G"}', 10999.00, 20, 'https://picsum.photos/seed/p2-7/400/400', 0);

-- product_id=3 男士休闲夹克：颜色 × 尺码
INSERT INTO product_sku (product_id, sku_code, specs, price, stock, image, sales) VALUES
(3, 'SKU-JK-1', '{"颜色":"卡其","尺码":"M"}', 399.00, 80, 'https://picsum.photos/seed/p3-1/400/400', 300),
(3, 'SKU-JK-2', '{"颜色":"卡其","尺码":"L"}', 399.00, 80, 'https://picsum.photos/seed/p3-2/400/400', 400),
(3, 'SKU-JK-3', '{"颜色":"卡其","尺码":"XL"}', 399.00, 70, 'https://picsum.photos/seed/p3-3/400/400', 300),
(3, 'SKU-JK-4', '{"颜色":"深蓝","尺码":"M"}', 399.00, 40, 'https://picsum.photos/seed/p3-4/400/400', 200),
(3, 'SKU-JK-5', '{"颜色":"深蓝","尺码":"L"}', 399.00, 27, 'https://picsum.photos/seed/p3-5/400/400', 280);

-- product_id=4 女士连衣裙：颜色 × 尺码
INSERT INTO product_sku (product_id, sku_code, specs, price, stock, image, sales) VALUES
(4, 'SKU-LS-1', '{"颜色":"米白","尺码":"S"}', 259.00, 40, 'https://picsum.photos/seed/p4-1/400/400', 200),
(4, 'SKU-LS-2', '{"颜色":"米白","尺码":"M"}', 259.00, 50, 'https://picsum.photos/seed/p4-2/400/400', 356),
(4, 'SKU-LS-3', '{"颜色":"米白","尺码":"L"}', 259.00, 60, 'https://picsum.photos/seed/p4-3/400/400', 300),
(4, 'SKU-LS-4', '{"颜色":"粉色","尺码":"M"}', 259.00, 0, 'https://picsum.photos/seed/p4-4/400/400', 0);

-- product_id=11 IQOO11：颜色 × 容量
INSERT INTO product_sku (product_id, sku_code, specs, price, stock, image, sales) VALUES
(11, 'SKU-IQ11-1', '{"颜色":"传奇版","容量":"12G+256G"}', 2000.00, 3, 'https://picsum.photos/seed/p11-1/400/400', 2),
(11, 'SKU-IQ11-2', '{"颜色":"传奇版","容量":"12G+512G"}', 2399.00, 7, 'https://picsum.photos/seed/p11-2/400/400', 0),
(11, 'SKU-IQ11-3', '{"颜色":"赛道版","容量":"12G+256G"}', 2000.00, 0, 'https://picsum.photos/seed/p11-3/400/400', 0);

-- product_id=5,6,7,8,9,10 食品/家居/书籍：单规格，只有一个 SKU
INSERT INTO product_sku (product_id, sku_code, specs, price, stock, image, sales) VALUES
(5, 'SKU-PEARL', '{"规格":"357g/饼"}', 168.00, 500, NULL, 2340),
(6, 'SKU-NUTS',   '{"规格":"500g礼盒装"}', 89.00, 798, NULL, 3600),
(7, 'SKU-PILLOW', '{"规格":"标准款"}', 129.00, 400, NULL, 1890),
(8, 'SKU-LAMP',   '{"规格":"标准版"}', 199.00, 250, NULL, 1200),
(9, 'SKU-JVM',    '{"规格":"第三版"}', 129.00, 600, NULL, 4500),
(10, 'SKU-SPRING','{"规格":"第六版"}', 89.00, 400, NULL, 3200);
