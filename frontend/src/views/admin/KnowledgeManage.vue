<template>
  <div class="knowledge-manage">
    <header class="page-head">
      <div class="head-left">
        <el-icon :size="22" color="#6366f1"><Reading /></el-icon>
        <h2>知识库管理</h2>
        <el-tag size="small" type="info" effect="plain">RAG</el-tag>
      </div>
      <div class="head-right">
        <el-button :icon="Refresh" @click="load" :loading="loading">刷新</el-button>
        <el-button :icon="Reading" @click="$router.push('/knowledge')" type="info" plain>查看前台展示</el-button>
        <el-button type="primary" :icon="Plus" @click="onCreate">新增段落</el-button>
      </div>
    </header>

    <el-alert
      type="info" show-icon :closable="false" style="margin-bottom: 12px"
      title="编辑后立即生效，AI 客服下次回复时会使用最新内容。type=text 是纯文本，type=list 是 JSON 数组，type=kv 是 JSON 对象。"
    />

    <el-table :data="list" v-loading="loading" border stripe size="default" style="width: 100%">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="sortOrder" label="排序" width="80" sortable />
      <el-table-column prop="name" label="段落名" min-width="160" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容预览" min-width="280">
        <template #default="{ row }">
          <span class="content-preview">{{ previewContent(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(v) => onToggle(row, v)" />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="170">
        <template #default="{ row }">
          <span class="muted">{{ formatTime(row.updatedAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="onEdit(row)">编辑</el-button>
          <el-popconfirm
            :title="`确认删除「${row.name}」？`"
            @confirm="onDelete(row)"
            confirm-button-text="删除"
            cancel-button-text="取消"
          >
            <template #reference>
              <el-button link type="danger" :icon="Delete">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑知识库段落' : '新增知识库段落'"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="段落名" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="例：售后规则 / 会员政策 / 配送说明" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button value="text">text（纯文本）</el-radio-button>
            <el-radio-button value="list">list（列表/JSON数组）</el-radio-button>
            <el-radio-button value="kv">kv（自由KV/JSON对象）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          <span class="muted" style="margin-left: 8px">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabledBool" />
          <span class="muted" style="margin-left: 8px">禁用后前台和 AI 都不展示</span>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="12"
            :placeholder="contentPlaceholder"
            spellcheck="false"
            style="font-family: ui-monospace, Consolas, monospace"
          />
          <div class="muted" style="margin-top: 4px">
            <span v-if="form.type === 'list'">格式示例：["条目1","条目2","条目3"]</span>
            <span v-else-if="form.type === 'kv'">格式示例：{"键1":"值1","键2":"值2"}</span>
            <span v-else>纯文本直接填写即可</span>
            <span v-if="form.type !== 'text'" style="margin-left: 12px">
              <el-button link :icon="MagicStick" @click="onFormat" size="small">美化 JSON</el-button>
            </span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Reading, Refresh, Plus, Edit, Delete, MagicStick
} from '@element-plus/icons-vue'
import {
  getKnowledgeList,
  createKnowledge,
  updateKnowledge,
  deleteKnowledge,
  toggleKnowledge
} from '@/api/admin'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  name: '',
  type: 'text',
  content: '',
  sortOrder: 0,
  enabled: 1,
  enabledBool: true
})

const rules = {
  name: [{ required: true, message: '请输入段落名', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const contentPlaceholder = computed(() => {
  if (form.type === 'list') return '["条目1","条目2","条目3"]'
  if (form.type === 'kv') return '{"键1":"值1","键2":"值2"}'
  return '请输入正文...'
})

function typeLabel(t) {
  if (t === 'text') return 'text'
  if (t === 'list') return 'list'
  if (t === 'kv') return 'kv'
  return t || 'text'
}
function typeTagType(t) {
  if (t === 'text') return ''
  if (t === 'list') return 'success'
  if (t === 'kv') return 'warning'
  return 'info'
}
function previewContent(row) {
  const c = row.content || ''
  if (row.type === 'text') {
    return c.length > 80 ? c.slice(0, 80) + '…' : c
  }
  if (row.type === 'list') {
    try {
      const arr = JSON.parse(c)
      if (Array.isArray(arr)) return '[' + arr.length + ' 条] ' + (arr[0] || '')
    } catch (e) { /* ignore */ }
  }
  if (row.type === 'kv') {
    try {
      const obj = JSON.parse(c)
      return '{' + Object.keys(obj || {}).length + ' 项}'
    } catch (e) { /* ignore */ }
  }
  return c.length > 80 ? c.slice(0, 80) + '…' : c
}
function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return '-'
  const pad = n => (n < 10 ? '0' + n : n)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const res = await getKnowledgeList()
    list.value = res.data || []
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message || ''))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = null
  form.name = ''
  form.type = 'text'
  form.content = ''
  form.sortOrder = 100
  form.enabled = 1
  form.enabledBool = true
}

function onCreate() {
  resetForm()
  dialogVisible.value = true
}

function onEdit(row) {
  form.id = row.id
  form.name = row.name
  form.type = row.type || 'text'
  form.content = row.content || ''
  form.sortOrder = row.sortOrder == null ? 0 : row.sortOrder
  form.enabled = row.enabled
  form.enabledBool = row.enabled === 1
  dialogVisible.value = true
}

async function onSubmit() {
  // list / kv 校验 JSON 格式
  if (form.type === 'list' || form.type === 'kv') {
    try {
      JSON.parse(form.content)
    } catch (e) {
      ElMessage.error(`${form.type.toUpperCase()} 类型必须是合法 JSON：${e.message}`)
      return
    }
  }
  saving.value = true
  try {
    const data = {
      name: form.name,
      type: form.type,
      content: form.content,
      sortOrder: form.sortOrder,
      enabled: form.enabledBool ? 1 : 0
    }
    if (form.id) {
      data.id = form.id
      await updateKnowledge(data)
      ElMessage.success('已更新')
    } else {
      await createKnowledge(data)
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || ''))
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await deleteKnowledge(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || ''))
  }
}

async function onToggle(row, v) {
  try {
    await toggleKnowledge({ id: row.id, enabled: v ? 1 : 0 })
    row.enabled = v ? 1 : 0
    ElMessage.success(v ? '已启用' : '已禁用')
  } catch (e) {
    ElMessage.error('切换失败：' + (e.message || ''))
    await load()
  }
}

function onFormat() {
  if (!form.content || form.type === 'text') return
  try {
    form.content = JSON.stringify(JSON.parse(form.content), null, 2)
    ElMessage.success('已美化')
  } catch (e) {
    ElMessage.error('JSON 格式有误：' + e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.knowledge-manage { padding: 16px; }
.page-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px;
}
.head-left { display: flex; align-items: center; gap: 10px; }
.head-left h2 { margin: 0; font-size: 18px; }
.head-right { display: flex; gap: 8px; }
.content-preview {
  display: inline-block; max-width: 360px;
  color: #475569; font-size: 13px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  vertical-align: middle;
}
.muted { color: #94a3b8; font-size: 12px; }
</style>
