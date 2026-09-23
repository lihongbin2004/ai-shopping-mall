<template>
  <el-dialog v-model="visible" title="申请退款" width="540px" :close-on-click-modal="false">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="原订单号">
        <span class="order-no">{{ order?.orderNo }}</span>
      </el-form-item>

      <el-form-item label="商品">
        <div v-if="order" class="product-info">
          <div v-for="item in order.items" :key="item.id" class="product-row">
            <span>{{ item.productName }} × {{ item.quantity }}</span>
            <span class="price">¥{{ Number(item.price).toFixed(2) }}</span>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="退款金额" prop="refundAmount">
        <el-input-number
          v-model="form.refundAmount"
          :min="0.01"
          :max="Number(order?.totalAmount || 0)"
          :precision="2"
          :step="0.01"
          style="width: 200px;"
        />
        <span class="hint">（最高可退 ¥{{ Number(order?.totalAmount || 0).toFixed(2) }}）</span>
      </el-form-item>

      <el-form-item label="退款原因" prop="reason">
        <el-select v-model="form.reason" placeholder="请选择退款原因" style="width: 100%;">
          <el-option label="商品质量问题" value="商品质量问题" />
          <el-option label="商品与描述不符" value="商品与描述不符" />
          <el-option label="收到错误商品" value="收到错误商品" />
          <el-option label="商品损坏/破损" value="商品损坏/破损" />
          <el-option label="不想要了/多拍" value="不想要了/多拍" />
          <el-option label="商品过期" value="商品过期" />
          <el-option label="其他原因" value="其他原因" />
        </el-select>
      </el-form-item>

      <el-form-item label="详细说明" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          placeholder="请详细描述问题，方便我们更快处理（选填）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 8px;"
      >
        <template #title>退款说明</template>
        提交后我们会在1-2个工作日内审核，审核通过后金额将原路退回。
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createRefund } from '@/api/refund'

const props = defineProps({
  modelValue: Boolean,
  order: Object
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({
  refundAmount: 0,
  reason: '',
  description: ''
})

const rules = {
  refundAmount: [
    { required: true, message: '请输入退款金额', trigger: 'blur' }
  ],
  reason: [
    { required: true, message: '请选择退款原因', trigger: 'change' }
  ]
}

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.order) {
    form.refundAmount = Number(props.order.totalAmount) || 0
    form.reason = ''
    form.description = ''
  }
})

watch(visible, (val) => emit('update:modelValue', val))

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    await createRefund({
      orderId: props.order.id,
      productId: props.order.items?.[0]?.productId,
      reason: form.reason,
      description: form.description,
      refundAmount: form.refundAmount
    })
    ElMessage.success('退款申请提交成功！请等待审核')
    visible.value = false
    emit('success')
  } catch (e) {
    console.error('提交退款失败', e)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.order-no { font-family: monospace; color: #409eff; }
.product-info { width: 100%; }
.product-row { display: flex; justify-content: space-between; padding: 4px 0; font-size: 14px; }
.price { color: #f56c6c; font-weight: 500; }
.hint { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
