-- ============================================================
-- 在线客服（人工客服）功能
-- 独立于 AI 聊天，客户侧浮动窗口，admin 后台有客服工作台
-- ============================================================

-- 1. 会话表
CREATE TABLE IF NOT EXISTS chat_session (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL COMMENT '客户 user_id',
  admin_id INT DEFAULT NULL COMMENT '分配的客服 admin_id，可为空',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=进行中 1=已关闭',
  last_message VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息（冗余）',
  last_time DATETIME DEFAULT NULL COMMENT '最后消息时间',
  unread_count INT DEFAULT 0 COMMENT '客户未读数（admin 发的）',
  admin_unread INT DEFAULT 0 COMMENT '客服未读数（客户发的）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_admin_id (admin_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 消息表
CREATE TABLE IF NOT EXISTS chat_message (
  id INT PRIMARY KEY AUTO_INCREMENT,
  session_id INT NOT NULL COMMENT '会话 ID',
  sender_type TINYINT NOT NULL COMMENT '0=客户(user) 1=客服(admin)',
  sender_id INT NOT NULL COMMENT '发送者 ID',
  content VARCHAR(2000) NOT NULL COMMENT '消息内容',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session_id (session_id),
  INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
