<script setup lang="ts">
import { ref, onMounted, reactive, watch } from 'vue'
import request, { downloadFile } from '../../api/request'
import type { Product, PageResult, PageParams } from '../../types/api'
import { ElMessage, ElMessageBox, ElForm } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const products = ref<Product[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const warehouseTree = ref<any[]>([])

const query = reactive<PageParams & {
  name?: string; code?: string; status?: number; alertOnly?: boolean;
  warehouseId?: number; categoryId?: number;
  minPrice?: number; maxPrice?: number;
  minSalePrice?: number; maxSalePrice?: number;
  startDate?: string; endDate?: string;
}>({
  page: 1, size: 10, name: '', code: '', status: undefined, alertOnly: undefined,
  warehouseId: undefined, categoryId: undefined,
  minPrice: undefined, maxPrice: undefined,
  minSalePrice: undefined, maxSalePrice: undefined,
  startDate: '', endDate: '',
})

const dialogVisible = ref(false)
const formType = ref<'create' | 'edit'>('create')
const formRef = ref<InstanceType<typeof ElForm>>()
const form = reactive<Partial<Product>>({
  code: '',
  name: '',
  spec: '',
  unit: '',
  categoryId: undefined,
  purchasePrice: null,
  salePrice: null,
  minStock: 0,
  maxStock: 0,
  status: 1,
  imageUrl: '',
  remark: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await request.get<{ data: PageResult<Product> }>('/product/page', { params: query })
    products.value = res.data.data.records
    total.value = res.data.data.total
    loadPageBarcodes()
  } catch {
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchData()
}

const categories = ref<any[]>([])

async function fetchCategories() {
  try {
    const res = await request.get('/category/tree')
    categories.value = res.data.data || []
  } catch { /* ignore */ }
}

const whPath = ref<any>(undefined)
function onWhChange(val: any) { query.warehouseId = val ?? undefined; handleSearch() }

function handleReset() {
  query.name = ''; query.code = ''; query.status = undefined;
  query.alertOnly = undefined; query.warehouseId = undefined; whPath.value = undefined;
  query.categoryId = undefined;
  query.minPrice = undefined; query.maxPrice = undefined;
  query.minSalePrice = undefined; query.maxSalePrice = undefined;
  query.startDate = ''; query.endDate = '';
  handleSearch()
}

function handlePageChange(page: number) {
  query.page = page
  fetchData()
}

function openCreate() {
  formType.value = 'create'
  Object.assign(form, { id: undefined, code: '', name: '', spec: '', unit: '', categoryId: undefined, purchasePrice: null, salePrice: null, minStock: 0, maxStock: 0, status: 1, imageUrl: '', remark: '' })
  dialogVisible.value = true
}

function openEdit(row: Product) {
  formType.value = 'edit'
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    if (formType.value === 'create') {
      await request.post('/product', form)
      ElMessage.success('新增成功')
    } else {
      await request.put(`/product/${form.id}`, form)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch { /* handled */ }
}

async function handleToggleStatus(row: Product) {
  const newStatus = row.status === 1 ? 0 : 1
  try { await ElMessageBox.confirm(`确定${newStatus === 0 ? '停用' : '启用'}商品「${row.name}」？`, '确认操作', { type: 'warning' }) } catch { return }
  await request.put(`/product/${row.id}`, { ...row, status: newStatus })
  ElMessage.success(newStatus === 1 ? '已启用' : '已停用')
  fetchData()
}

function handleExport(selected = false) {
  if (selected && selectedIds.value.length) {
    downloadFile(`/product/export?ids=${selectedIds.value.join(',')}`, '商品管理.xlsx')
    return
  }
  const params = new URLSearchParams()
  if (query.name) params.append('name', query.name)
  if (query.code) params.append('code', query.code)
  if (query.status !== undefined) params.append('status', String(query.status))
  if (query.warehouseId) params.append('warehouseId', String(query.warehouseId))
  if (query.categoryId) params.append('categoryId', String(query.categoryId))
  if (query.alertOnly !== undefined) params.append('alertOnly', String(query.alertOnly))
  if (query.minPrice) params.append('minPrice', String(query.minPrice))
  if (query.maxPrice) params.append('maxPrice', String(query.maxPrice))
  if (query.minSalePrice) params.append('minSalePrice', String(query.minSalePrice))
  if (query.maxSalePrice) params.append('maxSalePrice', String(query.maxSalePrice))
  if (query.startDate) params.append('startDate', query.startDate)
  if (query.endDate) params.append('endDate', query.endDate)
  const qs = params.toString()
  downloadFile(`/product/export${qs ? '?' + qs : ''}`, '商品管理.xlsx')
}

const fileInput = ref<HTMLInputElement>()
function handleImport() { fileInput.value?.click() }
async function onFileChange(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files?.length) return
  const formData = new FormData()
  formData.append('file', files[0])
  try {
    const res = await request.post('/product/import', formData)
    ElMessage.success(res.data.message)
    fetchData()
  } catch { /* handled */ }
  (e.target as HTMLInputElement).value = ''
}

async function handleDelete(row: Product) {
  try {
    await ElMessageBox.confirm(`确定作废商品「${row.name}」？`, '确认作废', { type: 'warning' })
    await request.delete(`/product/${row.id}`)
    ElMessage.success('已作废'); fetchData()
  } catch {} // cancelled or error
}
async function handleBatchToggle(status: number) {
  if (!selectedIds.value.length) { ElMessage.warning('请先选择商品'); return }
  for (const id of selectedIds.value) {
    await request.put(`/product/${id}`, { id, status })
  }
  ElMessage.success(`已${status === 1 ? '启用' : '停用'} ${selectedIds.value.length} 个商品`)
  selectedIds.value = []
  fetchData()
}

async function handlePrintBarcode() {
  if (!selectedIds.value.length) { ElMessage.warning('请先选择商品'); return }
  try {
    const res = await request.post('/product/barcode/download', selectedIds.value, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = '商品条码.zip'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已生成条码，请下载打印')
  } catch { ElMessage.error('生成条码失败') }
}

const barcodePreviews = ref<Record<number, string>>({})
const previewDialogVisible = ref(false)
const previewBarcodeUrl = ref('')
const previewProductName = ref('')

async function loadBarcodePreview(productId: number) {
  if (barcodePreviews.value[productId]) return
  try {
    const res = await request.get(`/product/barcode/${productId}`, { responseType: 'blob' })
    barcodePreviews.value[productId] = URL.createObjectURL(res.data as Blob)
  } catch { /* ignore */ }
}

function showBarcodePreview(row: Product) {
  if (barcodePreviews.value[row.id]) {
    previewBarcodeUrl.value = barcodePreviews.value[row.id]
    previewProductName.value = row.name || ''
    previewDialogVisible.value = true
  }
}

function loadPageBarcodes() {
  for (const p of products.value) {
    loadBarcodePreview(p.id)
  }
}

async function handlePrintSingleBarcode(row: Product) {
  try {
    const res = await request.get(`/product/barcode/${row.id}`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.code}_${row.name || ''}${row.spec ? '_'+row.spec : ''}.png`
    a.click()
    URL.revokeObjectURL(url)
  } catch { ElMessage.error('获取条码失败') }
}

onMounted(async () => { const r = await request.get('/warehouse/tree'); warehouseTree.value = r.data.data || []; fetchCategories(); fetchData() })
</script>

<template>
  <div>
    <div class="page-header">
      <h2>商品管理</h2>
      <div>
        <el-button type="primary" @click="openCreate" v-if="userStore.isAdmin">新增商品</el-button>
        <el-button @click="handleImport" v-if="userStore.isAdmin">导入Excel</el-button>
        <el-button @click="downloadFile('/product/import/template', '商品导入模板.xlsx')">下载模板</el-button>
        <el-tooltip content="表头与导入模板一致，修改后可直接导入" placement="top">
          <el-button @click="downloadFile('/product/export-for-import', '商品数据(可回导).xlsx')">导出规范Excel</el-button>
        </el-tooltip>
        <el-tooltip content="包含库存数量、预警状态等详细信息" placement="top">
          <el-button @click="handleExport">导出Excel</el-button>
        </el-tooltip>
        <input ref="fileInput" type="file" accept=".xlsx,.xls" hidden @change="onFileChange" />
      </div>
    </div>

    <!-- 搜索 -->
    <div class="search-bar">
      <el-input v-model="query.name" placeholder="商品名称" clearable style="width: 200px" @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-input v-model="query.code" placeholder="商品编码" clearable style="width: 160px" @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-input v-model="query.minPrice" placeholder="最低采购价" type="number" style="width:120px" @keyup.enter="handleSearch" />
      <span style="color:#999">-</span>
      <el-input v-model="query.maxPrice" placeholder="最高采购价" type="number" style="width:120px" @keyup.enter="handleSearch" />
      <el-input v-model="query.minSalePrice" placeholder="最低销售价" type="number" style="width:120px" @keyup.enter="handleSearch" />
      <span style="color:#999">-</span>
      <el-input v-model="query.maxSalePrice" placeholder="最高销售价" type="number" style="width:120px" @keyup.enter="handleSearch" />
      <el-date-picker v-model="query.startDate" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width:140px" @change="handleSearch" />
      <el-date-picker v-model="query.endDate" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width:140px" @change="handleSearch" />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 筛选（选择即自动刷新） -->
    <div class="filter-bar" style="margin-bottom: 10px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="handleSearch">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-cascader v-model="whPath" :options="warehouseTree" :props="{ label: 'name', children: 'children', value: 'id', emitPath: false, checkStrictly: true }" placeholder="全部仓库" clearable filterable style="width: 160px" @change="onWhChange">
        <template #default="{ data }">
          <span>{{ data.name }}</span>
          <el-tag v-if="data.children?.length" size="small" type="info" effect="plain" style="margin-left:4px;">虚拟</el-tag>
          <span v-else style="font-size:11px;color:#2e7d32;margin-left:4px;">库存{{ data.productCount || 0 }}</span>
        </template>
      </el-cascader>
      <el-tree-select
        v-model="query.categoryId"
        :data="categories"
        :props="{ label: 'name', value: 'id', children: 'children' }"
        placeholder="商品分类"
        clearable
        style="width: 140px"
        @change="handleSearch"
      />
      <el-select v-model="query.alertOnly" placeholder="库存状态" clearable style="width: 130px" @change="handleSearch">
        <el-option label="仅看预警" :value="true" />
        <el-option label="正常" :value="false" />
      </el-select>
      <el-button type="success" @click="handleExport()">导出筛选结果</el-button>
    </div>

    <!-- 批量操作 -->
    <div style="margin-bottom:10px;display:flex;align-items:center;gap:8px;">
      <span style="font-size:13px;color:#666;">{{ selectedIds.length ? '已选 ' + selectedIds.length + ' 项' : '批量操作' }}</span>
      <el-button size="small" type="success" :disabled="!selectedIds.length" @click="handleBatchToggle(1)">批量启用</el-button>
      <el-button size="small" type="warning" :disabled="!selectedIds.length" @click="handleBatchToggle(0)">批量停用</el-button>
      <el-button size="small" :disabled="!selectedIds.length" @click="handlePrintBarcode">打印条码</el-button>
	      <el-button size="small" :disabled="!selectedIds.length" @click="handleExport(true)">批量导出</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table :data="products" v-loading="loading" stripe border @selection-change="(rows: any[]) => selectedIds = rows.map(r => r.id)">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="code" label="编码" width="150" />
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-image v-if="row.imageUrl" :src="row.imageUrl" style="width:50px;height:50px;border-radius:4px;cursor:pointer" fit="cover" :preview-src-list="[row.imageUrl]" preview-teleported />
            <span v-else style="color:#ccc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="spec" label="规格" width="80" />
        <el-table-column label="条码" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="barcodePreviews[row.id]"
              :src="barcodePreviews[row.id]"
              style="width:72px;height:22px;cursor:pointer"
              fit="contain"
              @click="showBarcodePreview(row)"
            />
            <el-button v-else size="small" link type="primary" @click="loadBarcodePreview(row.id)">加载</el-button>
          </template>
        </el-table-column>
        <el-table-column label="打印" width="80" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handlePrintSingleBarcode(row)">
              <template #icon><svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M19 8h-1V3H6v5H5c-1.66 0-3 1.34-3 3v6h4v4h12v-4h4v-6c0-1.66-1.34-3-3-3zM8 5h8v3H8V5zm8 14H8v-4h8v4zm2-4v-2H6v2H4v-4c0-.55.45-1 1-1h14c.55 0 1 .45 1 1v4h-2z"/></svg></template>
              打印
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="categoryName" label="分类" width="80" />
        <el-table-column prop="purchasePrice" label="采购价" sortable width="80">
          <template #default="{ row }">¥{{ row.purchasePrice }}</template>
        </el-table-column>
        <el-table-column prop="salePrice" label="销售价" sortable width="80">
          <template #default="{ row }">¥{{ row.salePrice }}</template>
        </el-table-column>
        <el-table-column prop="inventoryQuantity" label="当前库存" sortable width="80">
          <template #default="{ row }">
            <span :style="{ color: row.inventoryQuantity != null && row.inventoryQuantity <= (row.minStock || 0) ? '#c62828' : '#2e7d32', fontWeight: 600 }">{{ row.inventoryQuantity ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="minStock" label="最低库存" sortable width="80" />
        <el-table-column label="库存状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.alertStatus === 'warning'" type="danger" size="small">预警</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="新增时间" sortable width="130">
          <template #default="{ row }">{{ row.createTime ? row.createTime.slice(0,16) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" sortable width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="handleToggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            <el-button v-if="userStore.isAdmin" size="small" type="danger" @click="handleDelete(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 16px; display: flex; justify-content: flex-end">
        <el-pagination
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="query.page = 1; fetchData()"
        />
      </div>
    </div>

    <!-- 表单弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="formType === 'create' ? '新增商品' : '编辑商品'"
      width="600px"
    >
      <el-form ref="formRef" :model="form" label-width="100px" :rules="{
        name: [{ required: true, message: '请输入商品名称' }],
        unit: [{ required: true, message: '请输入单位' }],
      }">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="商品编码">
              <span style="color:#999;line-height:32px;">{{ form.code || '自动生成' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规格型号" prop="spec">
              <el-input v-model="form.spec" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品分类">
              <el-tree-select
                v-model="form.categoryId"
                :data="categories"
                :props="{ label: 'name', value: 'id', children: 'children' }"
                placeholder="选择分类"
                clearable
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" prop="unit">
              <el-input v-model="form.unit" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="采购参考价" prop="purchasePrice">
              <el-input-number v-model="form.purchasePrice" :precision="2" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售参考价" prop="salePrice">
              <el-input-number v-model="form.salePrice" :precision="2" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="最低库存" prop="minStock">
              <el-input-number v-model="form.minStock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最高库存" prop="maxStock">
              <el-input-number v-model="form.maxStock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="商品图片">
          <el-upload
            :action="'/api/v1/file/upload'"
            :headers="{ Authorization: 'Bearer ' + (userStore.token || '') }"
            :on-success="(res: any) => { if (res.code === 200) { form.imageUrl = res.data; ElMessage.success('上传成功') } else { ElMessage.error(res.msg || '上传失败') } }"
            :on-error="() => ElMessage.error('上传失败，请重试')"
            :on-remove="() => { form.imageUrl = ''; ElMessage.info('已移除图片') }"
            :file-list="form.imageUrl ? [{ name: '商品图片', url: form.imageUrl }] : []"
            list-type="picture"
            :limit="1"
          >
            <el-button size="small" type="primary">点击上传</el-button>
            <template #tip><div style="font-size:12px;color:#999">建议尺寸 200x200，不超过 10MB</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 条码预览弹窗 -->
    <el-dialog v-model="previewDialogVisible" :title="previewProductName + ' - 条码'" width="500px" align-center>
      <div style="text-align:center;">
        <img v-if="previewBarcodeUrl" :src="previewBarcodeUrl" style="max-width:100%;height:auto;" />
      </div>
    </el-dialog>

  </div>
</template>

<script lang="ts">
</script>
