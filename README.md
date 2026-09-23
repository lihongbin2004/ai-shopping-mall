# AI 智能购物商城系统

基于 Spring Boot + Vue 3 的前后端分离 B2C 商城，内置**个性化商品推荐**、**多规格 SKU**、**AI 智能客服（DeepSeek + RAG）**、在线支付与完整后台管理。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 2.7、Spring MVC、MyBatis（XML）、MySQL 8、Druid |
| 缓存 | Redis（String / Hash / ZSet：热门缓存、用户兴趣画像、浏览历史） |
| 前端 | Vue 3（Composition API）、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts |
| 安全 | JWT 登录鉴权、Spring 声明式事务、统一异常处理 |
| AI | DeepSeek（OpenAI 兼容协议，SSE 流式输出）+ RAG 商城知识库；可切换通义千问 / 本地 Ollama |
| 其他 | WebSocket 在线客服、MinIO 对象存储（不可用时自动降级本地存储）、支付宝沙箱 |

## 核心亮点

### 1. 个性化推荐（召回 + 多因子加权排序）
- **行为画像**：对浏览、收藏、加购、购买四类行为按 **1∶2∶3∶5** 权重累加，构建用户分类兴趣画像（Redis Hash 存储）。
- **召回**：取兴趣度 Top 3 分类作为候选集，排除已买/已藏商品。
- **排序**：候选商品按三因子 min-max 归一化后加权打分：

```
finalScore = 0.4 × 销量热度 + 0.4 × 兴趣匹配度 + 0.2 × 上新新鲜度
```

- **冷启动**：新用户无行为数据时自动回退全局热门（销量榜，Redis 缓存 30 分钟）。
- 浏览历史使用 ZSet 按时间打分维护，推荐结果缓存 30 分钟。

### 2. 多规格 SKU 与数据一致性
- 支持最多 3 组规格（如颜色 × 容量）笛卡尔积自动生成 SKU，每个 SKU 独立定价、库存、图片。
- 商品保存采用「**先删后插 + @Transactional 声明式事务**」全量重建 SKU，并在同一事务内同步主表价格（取最低 SKU 价）与库存（SKU 库存求和），保证主表与 SKU 表数据一致。
- 级联删除：删除商品时同事务清理全部 SKU。

### 3. AI 智能客服（RAG）
- 基于 DeepSeek 流式输出（SSE + 打字机效果），结合商城知识库（商品咨询、售后规则、购物须知）做检索增强问答。
- 支持多会话创建/切换/删除，商品详情页可直接发起 AI 问答。

### 4. 工程实践
- 统一响应封装与全局异常处理；JWT 拦截器实现管理员 / 普通用户双角色权限。
- 敏感配置全部走环境变量占位，**真实密钥不入库**；MinIO 不可用时上传自动降级本地目录。
- 后台数据看板：ECharts 销售额趋势、分类占比、销量排行。

## 环境要求

- JDK 1.8+、Maven 3.3+
- MySQL 8.0、Redis（可选，不影响基础功能）
- Node.js 16+
- MinIO（可选，不可用时自动降级）

## 快速启动

### 1. 初始化数据库

```bash
mysql -u root -p < sql/init.sql
```

然后修改 `backend/src/main/resources/application.yml` 中的数据库密码（或设置环境变量 `DB_PASSWORD`）。

### 2.（可选）配置 AI Key

在 `application.yml` 填入你的 DeepSeek API Key（申请：https://platform.deepseek.com/api_keys ），
或设置环境变量 `DEEPSEEK_API_KEY`。未配置时商城核心功能不受影响，仅 AI 客服不可用。

### 3. 启动后端（端口 8081）

```bash
cd backend
mvn spring-boot:run
```

### 4. 启动前端（端口 3000）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:3000

## 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 普通用户 | testuser | 123456 |

## 功能模块

**前台**：注册登录、商品首页（轮播图 / 分类导航 / 个性化推荐）、商品列表（分类筛选 / 搜索 / 排序 / 分页）、
商品详情（多规格选择 / 评价 / 收藏）、购物车、订单结算（地址 + 优惠券 + 支付）、订单管理、地址管理、商品收藏、个人中心

**后台**：用户管理、商品管理（含多规格 SKU）、分类树形管理（多级分类）、订单管理、退款审核、优惠券管理、
数据看板、知识库管理、客服工作台、系统配置

## 项目结构

```
ai-shopping-mall/
├── backend/
│   └── src/main/
│       ├── java/com/mall/
│       │   ├── ai/            # AI 模块（DeepSeek / RAG / 多模型）
│       │   ├── common/        # 统一响应、全局异常、配置
│       │   ├── controller/    # 控制层
│       │   ├── entity/        # 实体类
│       │   ├── mapper/        # MyBatis Mapper
│       │   ├── security/      # JWT 鉴权
│       │   ├── service/       # 业务层（含 RecommendService 推荐服务）
│       │   └── websocket/     # WebSocket 客服
│       └── resources/
│           ├── application.yml
│           └── mapper/        # MyBatis XML
├── frontend/
│   └── src/
│       ├── api/               # Axios 接口层
│       ├── components/
│       ├── router/
│       ├── stores/            # Pinia
│       └── views/
│           ├── front/         # 前台页面
│           ├── admin/         # 后台管理页面
│           └── chat/          # 在线客服
├── sql/                       # 数据库脚本
└── docs/
```

## 说明

- 支付宝沙箱支付需自行申请沙箱账号并替换配置（默认关闭，不影响其他功能）。
- 本项目为个人学习项目，欢迎交流 Star ⭐
