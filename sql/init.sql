-- ============================================================
-- AI智能购物商城系统 - 数据库建表脚本
-- 数据库: mall
-- ============================================================

CREATE DATABASE IF NOT EXISTS mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mall;

-- 1. 用户表
DROP TABLE IF EXISTS user;
CREATE TABLE user (
  id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  phone VARCHAR(20) DEFAULT NULL,
  email VARCHAR(100) DEFAULT NULL,
  avatar VARCHAR(500) DEFAULT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 商品分类表
DROP TABLE IF EXISTS category;
CREATE TABLE category (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  parent_id INT DEFAULT 0,
  sort INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 商品表
DROP TABLE IF EXISTS product;
CREATE TABLE product (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  category_id INT DEFAULT NULL,
  image VARCHAR(500) DEFAULT NULL,
  sales INT DEFAULT 0,
  status TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 购物车表
DROP TABLE IF EXISTS cart;
CREATE TABLE cart (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  product_id INT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  checked TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 订单表
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
  id INT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(50) NOT NULL UNIQUE,
  user_id INT NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  pay_amount DECIMAL(10,2) DEFAULT NULL,
  status TINYINT DEFAULT 0,
  address_id INT DEFAULT NULL,
  payment VARCHAR(50) DEFAULT '余额',
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  pay_time DATETIME DEFAULT NULL,
  delivery_time DATETIME DEFAULT NULL,
  finish_time DATETIME DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 订单详情表
DROP TABLE IF EXISTS order_item;
CREATE TABLE order_item (
  id INT PRIMARY KEY AUTO_INCREMENT,
  order_id INT NOT NULL,
  product_id INT NOT NULL,
  product_name VARCHAR(200) DEFAULT NULL,
  product_image VARCHAR(500) DEFAULT NULL,
  quantity INT NOT NULL,
  price DECIMAL(10,2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 地址表
DROP TABLE IF EXISTS address;
CREATE TABLE address (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  receiver VARCHAR(50) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  province VARCHAR(50) DEFAULT NULL,
  city VARCHAR(50) DEFAULT NULL,
  district VARCHAR(50) DEFAULT NULL,
  detail VARCHAR(200) NOT NULL,
  is_default TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 评价表
DROP TABLE IF EXISTS review;
CREATE TABLE review (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  product_id INT NOT NULL,
  rating INT NOT NULL,
  content TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 退款申请表
DROP TABLE IF EXISTS refund_orders;
CREATE TABLE refund_orders (
  id INT PRIMARY KEY AUTO_INCREMENT,
  refund_no VARCHAR(50) NOT NULL UNIQUE,
  order_id INT NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  user_id INT NOT NULL,
  product_id INT,
  reason VARCHAR(500) NOT NULL,
  description TEXT,
  refund_amount DECIMAL(10,2) NOT NULL,
  status TINYINT DEFAULT 0,
  reject_reason VARCHAR(500),
  admin_reply VARCHAR(500),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  audit_time DATETIME NULL,
  refund_time DATETIME NULL,
  INDEX idx_user_id (user_id),
  INDEX idx_order_id (order_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 收藏表
DROP TABLE IF EXISTS favorite;
CREATE TABLE favorite (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  product_id INT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_product_fav (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. 优惠券表
DROP TABLE IF EXISTS coupon;
CREATE TABLE coupon (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  min_amount DECIMAL(10,2) DEFAULT 0,
  stock INT DEFAULT 0,
  start_time DATETIME DEFAULT NULL,
  end_time DATETIME DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  status TINYINT DEFAULT 1 COMMENT '1=启用 0=禁用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. 用户优惠券关联表
DROP TABLE IF EXISTS user_coupon;
CREATE TABLE user_coupon (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  coupon_id INT NOT NULL,
  status TINYINT DEFAULT 0,
  used_time DATETIME DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. AI会话表
DROP TABLE IF EXISTS ai_session;
CREATE TABLE ai_session (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  title VARCHAR(200) DEFAULT '新会话',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. AI消息表
DROP TABLE IF EXISTS ai_message;
CREATE TABLE ai_message (
  id INT PRIMARY KEY AUTO_INCREMENT,
  session_id INT NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. 系统配置表
DROP TABLE IF EXISTS system_config;
CREATE TABLE system_config (
  id INT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(100) NOT NULL UNIQUE,
  config_value TEXT,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 初始化数据
-- ============================================================

-- 管理员账号 (密码: admin123)
INSERT INTO user (username, password, role, phone, email, status) VALUES
('admin', '0192023a7bbd73250516f069df18b500', 'ADMIN', '13800000000', 'admin@mall.com', 1);

-- 测试用户 (密码: 123456)
INSERT INTO user (username, password, role, phone, email, status) VALUES
('testuser', 'e10adc3949ba59abbe56e057f20f883e', 'USER', '13900000001', 'test@test.com', 1);

-- 商品分类
INSERT INTO category (name, parent_id, sort) VALUES
('电子产品', 0, 1),
('服装鞋帽', 0, 2),
('食品饮料', 0, 3),
('家居用品', 0, 4),
('图书音像', 0, 5);

-- 系统配置默认值
INSERT INTO system_config (config_key, config_value) VALUES
('siteName', 'AI智能购物商城'),
('siteDescription', '基于AI技术的智能购物平台'),
('siteLogo', ''),
('servicePhone', '400-888-8888'),
('serviceEmail', 'service@ai-mall.com'),
('aiApiUrl', 'https://api.openai.com/v1/chat/completions'),
('aiApiKey', ''),
('aiModel', 'gpt-4o'),
('autoCancelMinutes', '30'),
('autoConfirmDays', '7');

-- 测试商品
INSERT INTO product (name, description, price, stock, category_id, image, sales, status) VALUES
('华为MateBook X Pro 2024', '华为旗舰轻薄笔记本，3K触控屏，超级终端', 12999.00, 100, 1, 'https://picsum.photos/seed/p1/400/400', 256, 1),
('iPhone 15 Pro Max', 'Apple A17 Pro芯片，钛金属设计', 9999.00, 200, 1, 'https://picsum.photos/seed/p2/400/400', 512, 1),
('男士休闲夹克', '春秋季轻薄款，商务休闲两穿', 399.00, 300, 2, 'https://picsum.photos/seed/p3/400/400', 1280, 1),
('女士连衣裙', '法式碎花连衣裙，优雅气质', 259.00, 150, 2, 'https://picsum.photos/seed/p4/400/400', 856, 1),
('云南普洱熟茶饼', '陈年普洱熟茶357g，回甘醇厚', 168.00, 500, 3, 'https://picsum.photos/seed/p5/400/400', 2340, 1),
('进口坚果礼盒', '每日坚果混合装500g', 89.00, 800, 3, 'https://picsum.photos/seed/p6/400/400', 3600, 1),
('记忆棉护颈枕头', '人体工学设计，缓解颈椎压力', 129.00, 400, 4, 'https://picsum.photos/seed/p7/400/400', 1890, 1),
('智能台灯Pro', 'LED护眼灯，智能感应调光', 199.00, 250, 4, 'https://picsum.photos/seed/p8/400/400', 1200, 1),
('深入理解Java虚拟机', '第三版，周志明著，Java开发者必读', 129.00, 600, 5, 'https://picsum.photos/seed/p9/400/400', 4500, 1),
('Spring实战第六版', 'Spring框架入门到精通经典书籍', 89.00, 400, 5, 'https://picsum.photos/seed/p10/400/400', 3200, 1);

-- 测试优惠券
INSERT INTO coupon (name, amount, min_amount, stock, start_time, end_time) VALUES
('新人专享5元券', 5.00, 50.00, 1000, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
('满100减10元券', 10.00, 100.00, 500, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
('满300减30元券', 30.00, 300.00, 200, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
('满500减50元券', 50.00, 500.00, 100, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY));
