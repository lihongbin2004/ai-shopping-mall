<template>
  <div class="admin-layout">
    <AdminSidebar />
    <div class="admin-main">
      <AdminHeader title="分类管理" />
      <div class="admin-content">
        <el-card>
          <div class="toolbar">
            <div class="toolbar-left">
              <el-tag type="info">共 {{ categories.length }} 个分类节点</el-tag>
              <el-input v-model="filterKeyword" placeholder="搜索分类名" clearable size="small" style="width:180px" @input="onFilter" />
            </div>
            <el-button type="primary" size="small" @click="openDialog(null)">+ 新增顶级分类</el-button>
          </div>

          <el-table
            :data="displayList"
            row-key="id"
            :tree-props="{ children: 'children' }"
            default-expand-all
            v-loading="loading"
            stripe
            border
          >
            <el-table-column prop="name" label="分类名称" min-width="260">
              <template #default="{ row }">
                <el-icon v-if="!row.parentId" color="#409eff"><Folder /></el-icon>
                <el-icon v-else color="#67c23a"><Document /></el-icon>
                <span style="margin-left:6px">{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="80" align="center">
              <template #default="{ row }">{{ row.sort ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="层级" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="!row.parentId" size="small">顶级</el-tag>
                <el-tag v-else size="small" type="info">子级</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="openDialog(null, row)">+ 子分类</el-button>
                <el-button size="small" link @click="openDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 新增/编辑弹窗 -->
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" :close-on-click-modal="false">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="上级分类">
              <el-tree-select
                v-model="form.parentId"
                :data="treeSelectData"
                :props="{ label: 'name', value: 'id', children: 'children' }"
                check-strictly
                clearable
                placeholder="不选则为顶级分类"
                style="width:100%"
              />
            </el-form-item>
            <el-form-item label="分类名称" prop="name">
              <el-input v-model="form.name" placeholder="如：电子产品 / 运动户外 / 宠物用品" />
            </el-form-item>
            <el-form-item label="排序值">
              <el-input-number v-model="form.sort" :min="0" :max="999" style="width:100%" />
              <div class="field-hint">数值越小越靠前</div>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveCategory">保存</el-button>
          </template>
        </el-dialog>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Folder, Document } from '@element-plus/icons-vue'
import AdminSidebar from './components/AdminSidebar.vue'
import AdminHeader from './components/AdminHeader.vue'
import { getCategoryTree, getCategoryList, createCategory, updateCategory, deleteCategory } from '@/api/category'
import request from '@/utils/request'

const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const filterKeyword = ref('')
const dialogVisible = ref(false)
const editingId = ref(null)
const parentIdForNew = ref(null)
const formRef = ref(null)

const form = reactive({
  name: '',
  parentId: null,
  sort: 0
})

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const dialogTitle = computed(() => {
  if (editingId.value) return '编辑分类'
  return parentIdForNew.value ? '新增子分类' : '新增顶级分类'
})

// 平铺列表（带 children）供默认渲染 + 搜索用
const flatList = computed(() => {
  const out = []
  function walk(nodes, depth = 0) {
    for (const n of nodes) {
      out.push(n)
      if (n.children && n.children.length) walk(n.children, depth + 1)
    }
  }
  walk(categories.value)
  return out
})

// 搜索过滤：自底向上保留匹配节点 + 所有祖先 + 匹配节点的全部子孙
const displayList = computed(() => {
  const kw = filterKeyword.value.trim().toLowerCase()
  if (!kw) return categories.value
  // 标记命中节点 id（自身或子孙匹配）
  const hitIds = new Set()
  // 映射：id → parentId
  const parentMap = new Map()
  function walkParent(nodes, parentId) {
    for (const n of nodes) {
      parentMap.set(n.id, parentId)
      walkParent(n.children || [], n.id)
    }
  }
  walkParent(categories.value, null)
  // markMatches：返回当前节点子树是否命中，同时记录命中的节点 id
  function markMatches(nodes) {
    let hit = false
    for (const n of nodes) {
      const selfHit = n.name.toLowerCase().includes(kw)
      const descendantHit = markMatches(n.children || [])
      if (selfHit || descendantHit) {
        hit = true
        hitIds.add(n.id)
        // 命中节点同时向上保留所有祖先
        let pid = parentMap.get(n.id)
        while (pid != null) { hitIds.add(pid); pid = parentMap.get(pid) }
      }
    }
    return hit
  }
  markMatches(categories.value)

  // 过滤树：命中节点或其任何子节点含关键字 → 整个子树全保留；
  // 仅被祖先保留（自身无命中）→ 只递归保留命中的 children
  function hasHitDescendant(node) {
    if (node.name.toLowerCase().includes(kw)) return true
    return (node.children || []).some(c => hasHitDescendant(c))
  }
  function filterTree(nodes) {
    return nodes
      .filter(n => hitIds.has(n.id))
      .map(n => {
        if (hasHitDescendant(n)) {
          // 整个子树保留（命中节点的全部子孙）
          return { ...n }
        } else {
          // 仅被祖先带出来的节点，继续过滤 children
          return { ...n, children: filterTree(n.children || []) }
        }
      })
  }
  return filterTree(categories.value)
})

// 下拉选项（树选择时不含"自己"，避免父子关系设置错误）
const treeSelectData = computed(() => {
  if (!editingId.value) return categories.value
  function prune(nodes) {
    return nodes
      .filter(n => n.id !== editingId.value)
      .map(n => ({ ...n, children: n.children ? prune(n.children) : [] }))
  }
  return prune(categories.value)
})

function onFilter() { /* 触发 computed */ }

function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ts
  return d.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

async function fetchCategories() {
  loading.value = true
  try {
    const res = await getCategoryTree()
    categories.value = res.data || []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.parentId = null
  form.sort = 0
  editingId.value = null
  parentIdForNew.value = null
}

function openDialog(existingParent, editingRow) {
  resetForm()
  if (editingRow) {
    editingId.value = editingRow.id
    form.name = editingRow.name
    form.parentId = editingRow.parentId ?? null
    form.sort = editingRow.sort ?? 0
  } else if (existingParent) {
    parentIdForNew.value = existingParent.id
    form.parentId = existingParent.id
  }
  dialogVisible.value = true
}

async function saveCategory() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (editingId.value) {
    // 编辑时，不能把自己 parentId 改成自己或自己的子孙
    if (form.parentId) {
      if (form.parentId === editingId.value) {
        ElMessage.warning('上级分类不能是自己')
        return
      }
      const descendants = []
      function walk(nodes) {
        for (const n of nodes) {
          descendants.push(n.id)
          if (n.children) walk(n.children)
        }
      }
      const selfNode = findById(categories.value, editingId.value)
      if (selfNode && selfNode.children) walk(selfNode.children)
      if (descendants.includes(form.parentId)) {
        ElMessage.warning('不能把分类移动到它的子分类下')
        return
      }
    }
  }

  saving.value = true
  try {
    if (editingId.value) {
      await updateCategory({ id: editingId.value, ...form })
      ElMessage.success('分类已更新')
    } else {
      await createCategory({ ...form, parentId: form.parentId || null })
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    fetchCategories()
  } finally {
    saving.value = false
  }
}

function findById(nodes, id) {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children) {
      const hit = findById(n.children, id)
      if (hit) return hit
    }
  }
  return null
}

async function handleDelete(row) {
  // 1. 有子分类 → 阻止
  if (row.children && row.children.length > 0) {
    ElMessage.warning(`分类「${row.name}」下还有 ${row.children.length} 个子分类，请先删除或迁移子分类`)
    return
  }
  // 2. 该分类下是否还有商品
  try {
    const res = await request({
      url: '/product/page',
      method: 'get',
      params: { categoryId: row.id, page: 1, pageSize: 1 }
    })
    const total = res.data?.total ?? res.data?.records?.length ?? 0
    if (total > 0) {
      ElMessage.warning(`该分类下还有 ${total} 件商品，请先迁移商品再删除分类`)
      return
    }
  } catch {}

  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？此操作不可恢复。`, '删除确认', { type: 'warning' })
    await deleteCategory(row.id)
    ElMessage.success('分类已删除')
    fetchCategories()
  } catch {}
}

onMounted(fetchCategories)
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; background: #f0f2f5; }
.admin-main { flex: 1; display: flex; flex-direction: column; }
.admin-content { padding: 20px; flex: 1; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.toolbar-left { display: flex; gap: 12px; align-items: center; }
.field-hint { font-size: 12px; color: #909399; }
</style>
