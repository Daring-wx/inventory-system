<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import request, { downloadFile } from '../../api/request'
import type { Inventory } from '../../types/api'
import { ElMessage } from 'element-plus'

const router = useRouter()

const loading = ref(false)
const renderKey = ref(0)
const allList = ref<Inventory[]>([])
const rootNodes = ref<any[]>([])
const selectedWarehouseNode = ref<any[] | null>(null)
const childrenCache = reactive(new Map<number, any[]>())

const query = ref({ productName: '', warehouseId: undefined as number | undefined, warehouseName: '' })
const codeSearchInput = ref('')

// 批量选择仓库
const selectedWarehouseIds = reactive(new Set<number>())
const selectedCount = computed(() => selectedWarehouseIds.size)

// 仓库搜索选择器（含上级路径）
const whSearchLoading = ref(false)
const whSearchResults = ref<any[]>([])
const whCache = reactive(new Map<number, any>())
let searchTimer: any
async function onWhSearch(queryStr: string) {
  whSearchResults.value = []
  if (!queryStr) return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    whSearchLoading.value = true
    try {
      const res = await request.get('/warehouse/search', { params: { keyword: queryStr } })
      whSearchResults.value = res.data.data || []
      // 异步加载所有上级名称（缓存避免重复请求）
      async function loadWh(id: number): Promise<any> {
        if (whCache.has(id)) return whCache.get(id)
        try {
          const r = await request.get(`/warehouse/${id}`)
          if (r.data.data) whCache.set(id, r.data.data)
          return r.data.data
        } catch { return null }
      }
      const pendingIds = new Set<number>()
      for (const w of whSearchResults.value) {
        let pid = w.parentId
        while (pid) { pendingIds.add(pid); pid = null }
      }
      for (const pid of pendingIds) {
        const node = await loadWh(pid)
        if (node?.parentId) await loadWh(node.parentId) // 再上一级
      }
    } finally { whSearchLoading.value = false }
  }, 300)
}
function getParentPath(w: any): string {
  const parts: string[] = []
  const seen = new Set<number>()
  let pid = w.parentId
  while (pid && whCache.has(pid) && !seen.has(pid)) {
    seen.add(pid)
    parts.unshift(whCache.get(pid).name)
    pid = whCache.get(pid).parentId
  }
  return parts.length ? ' · ' + parts.join(' / ') : ''
}
async function onWarehouseSelect(val: any) {
  query.value.warehouseId = val ?? undefined
  loading.value = true
  try {
    if (val) {
      expanded.clear()
      const [, treeResult] = await Promise.all([
        fetchData(false),
        (async () => {
          const ancestorIds: number[] = []
          const res = await request.get(`/warehouse/${val}`)
          const node = res.data.data
          if (!node) return null
          async function loadParents(n: any): Promise<any> {
            if (!n.parentId) return n
            ancestorIds.push(n.parentId)
            const pr = await request.get(`/warehouse/${n.parentId}`)
            const pn = pr.data.data
            if (!pn) return n
            pn.children = [n]
            return loadParents(pn)
          }
          const tree = await loadParents(node)
          ancestorIds.forEach(id => expanded.add(id))
          return tree
        })(),
      ])
      selectedWarehouseNode.value = treeResult ? [treeResult] : []
      renderKey.value++
    } else {
      selectedWarehouseNode.value = null
      renderKey.value++
      await fetchData()
    }
  } finally { loading.value = false }
}

// 收集某个节点下的所有叶子仓库ID（仅已加载的子节点）
function collectLeafIds(node: any): number[] {
  if (!node.children || node.children.length === 0) return [node.id]
  return node.children.flatMap((c: any) => collectLeafIds(c))
}
function isNodeChecked(node: any): boolean {
  const leafIds = collectLeafIds(node)
  return leafIds.length > 0 && leafIds.every((id: number) => selectedWarehouseIds.has(id))
}
function isNodeIndeterminate(node: any): boolean {
  const leafIds = collectLeafIds(node)
  const some = leafIds.some((id: number) => selectedWarehouseIds.has(id))
  const all = leafIds.every((id: number) => selectedWarehouseIds.has(id))
  return some && !all
}
function toggleWarehouseSelect(node: any) {
  const leafIds = collectLeafIds(node)
  if (isNodeChecked(node)) {
    leafIds.forEach(id => selectedWarehouseIds.delete(id))
  } else {
    leafIds.forEach(id => selectedWarehouseIds.add(id))
  }
}

async function fetchData(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const params: Record<string, any> = { page: 1, size: 999 }
    if (query.value.productName) params.productName = query.value.productName
    if (query.value.warehouseId !== undefined) params.warehouseId = query.value.warehouseId
    const res = await request.get('/inventory/page', { params })
    allList.value = res.data.data.records || []
  } finally { if (showLoading) loading.value = false }
}

async function fetchWarehouseRoots() {
  try {
    const res = await request.get('/warehouse/roots')
    rootNodes.value = res.data.data || []
  } catch { /* handled */ }
}

// 展开/收起（懒加载子节点）
const expanded = reactive(new Set<number>())
async function toggle(id: number) {
  if (expanded.has(id)) {
    expanded.delete(id)
  } else {
    if (!childrenCache.has(id)) {
      const res = await request.get(`/warehouse/children-all/${id}`)
      childrenCache.set(id, res.data.data || [])
    }
    expanded.add(id)
  }
}
function isExpanded(id: number) { return expanded.has(id) }

async function handleSearch() {
  selectedWarehouseIds.clear()
  if (codeSearchInput.value?.trim()) {
    await handleCodeSearch()
  } else {
    await fetchData()
  }
}
async function handleReset() {
  query.value = { productName: '', warehouseId: undefined, warehouseName: '' }
  codeSearchInput.value = ''
  selectedWarehouseNode.value = null
  rootNodes.value = []
  childrenCache.clear()
  expanded.clear()
  selectedWarehouseIds.clear()
  await fetchWarehouseRoots()
  await fetchData()
}
async function handleCodeSearch() {
  const code = codeSearchInput.value?.trim()
  if (!code) return
  try {
    const res = await request.get('/warehouse/search', { params: { keyword: code } })
    const match = (res.data.data || []).find((w: any) => w.code === code)
    if (match) {
      query.value.warehouseId = match.id
      await onWarehouseSelect(match.id)
    } else {
      ElMessage.info('未找到该编码的仓库')
    }
  } catch { /* handled */ }
}
function handleExport() {
  selectedWarehouseIds.clear()
  const warehouseId = query.value.warehouseId
  const url = warehouseId ? `/inventory/export?warehouseId=${warehouseId}` : '/inventory/export'
  downloadFile(url, '库存查询.xlsx')
}
function handleBatchExport() {
  const ids = Array.from(selectedWarehouseIds).join(',')
  downloadFile(`/inventory/export?warehouseIds=${ids}`, '库存查询.xlsx')
  selectedWarehouseIds.clear()
}

// 从 inventory 数据构建商品名→库存的映射
const invByWarehouse = computed(() => {
  const map = new Map<number, Inventory[]>()
  for (const item of allList.value) {
    const wid = item.warehouseId
    if (!map.has(wid)) map.set(wid, [])
    map.get(wid)!.push(item)
  }
  return map
})

// 给仓库树注入库存数据（使用 childrenCache 替代直接 children 属性）
function buildTreeWithStock(nodes: any[]): any[] {
  return nodes.map(n => {
    const invs = invByWarehouse.value.get(n.id) || []
    const qty = invs.reduce((s: number, i: any) => s + (i.quantity || 0), 0)
    const amt = invs.reduce((s: number, i: any) => s + ((i.costPrice || 0) * (i.quantity || 0)), 0)
    const kids = childrenCache.get(n.id)?.length ? childrenCache.get(n.id)! : (n.children || [])
    const node: any = { ...n, _pc: invs.length, _qty: qty, _amt: amt }
    if (kids.length) {
      node.children = buildTreeWithStock(kids)
    }
    return node
  })
}

// 展平树为列表，depth 用于缩进；跳过收起分支
function flattenTree(nodes: any[], depth = 0): any[] {
  const result: any[] = []
  for (const n of nodes) {
    result.push({ ...n, depth })
    if (n.children?.length && isExpanded(n.id)) {
      result.push(...flattenTree(n.children, depth + 1))
    }
  }
  return result
}

const flatList = computed(() => {
  const source = selectedWarehouseNode.value || rootNodes.value
  return flattenTree(buildTreeWithStock(source))
})

// 合计
const grandTotal = computed(() => {
  const qty = allList.value.reduce((s: number, i: any) => s + (i.quantity || 0), 0)
  const amt = allList.value.reduce((s: number, i: any) => s + ((i.costPrice || 0) * (i.quantity || 0)), 0)
  return { qty, amt }
})

onMounted(() => { fetchWarehouseRoots(); fetchData() })
</script>

<template>
  <div>
    <div class="page-header">
      <h2>库存查询</h2>
      <div>
        <el-button @click="router.push('/inventory/log')">库存流水</el-button>
        <el-button v-if="selectedCount > 0" type="warning" @click="handleBatchExport">批量导出 ({{ selectedCount }})</el-button>
        <el-button @click="handleExport">导出Excel</el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input v-model="query.productName" placeholder="商品名称/编码" clearable style="width:200px" @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-input v-model="codeSearchInput" placeholder="仓库编码快速定位" clearable style="width:200px" @keyup.enter="handleCodeSearch" @clear="handleCodeSearch" />
      <el-select
        v-model="query.warehouseId"
        :remote-method="onWhSearch"
        :loading="whSearchLoading"
        filterable
        remote
        clearable
        placeholder="搜索仓库名称"
        style="width:220px"
        @change="onWarehouseSelect"
      >
        <el-option v-for="w in whSearchResults" :key="w.id" :label="w.name" :value="w.id">
          <span style="font-size:12px;color:#999;margin-right:4px;">{{ w.code }}</span>
          {{ w.name }}
          <span style="font-size:11px;color:#aaa;">{{ getParentPath(w) }}</span>
        </el-option>
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <div v-loading="loading" :key="renderKey">
      <div v-if="!flatList.length && !loading" style="text-align:center;padding:60px 0;color:#999;">暂无库存数据</div>

      <div v-for="(node, idx) in flatList" :key="'n' + node.id + '_' + idx" class="tree-row" :style="{ paddingLeft: (node.depth * 24 + 16) + 'px' }">
        <div class="tree-node" :class="'level-' + node.level">
          <span v-if="node.children?.length || node.hasChildren" class="toggle-icon" @click="toggle(node.id)">{{ isExpanded(node.id) ? '▼' : '▶' }}</span>
          <span v-else class="toggle-icon" style="visibility:hidden;">▶</span>
          <el-checkbox
            :model-value="isNodeChecked(node)"
            :indeterminate="isNodeIndeterminate(node)"
            size="small"
            @click.stop
            @change="toggleWarehouseSelect(node)"
          />
          <span class="node-name" @click="toggle(node.id)">{{ node.name }}</span>
          <span class="node-code-index">{{ node.code }}</span>
          <el-tag size="small" :type="['primary','success','warning','info'][node.level-1] || 'info'">{{ node.level }}级</el-tag>
          <el-tag v-if="node.hasChildren" size="small" type="info" effect="plain" style="margin-left:4px;">虚拟节点</el-tag>
          <span class="node-stats" v-if="node._pc">
            <span class="stats-badge">{{ node._pc }} 种商品</span>
            <span class="stats-badge">{{ node._qty }} 件</span>
            <span class="stats-badge amount">¥{{ node._amt.toFixed(2) }}</span>
          </span>
        </div>
        <!-- 叶子节点：库存汇总条 -->
        <div v-if="!node.hasChildren && node._pc" class="leaf-summary">
          📦 {{ node.name }} · {{ node._pc }} 种商品 · 共 {{ node._qty }} 件 · 金额 ¥{{ node._amt.toFixed(2) }}
        </div>
        <!-- 叶子节点：展示库存表格 -->
        <div v-if="!node.hasChildren && node._pc" class="leaf-inventory">
          <el-table :data="invByWarehouse.get(node.id) || []" stripe border size="small">
            <el-table-column prop="productCode" label="编码" width="100" />
            <el-table-column prop="productName" label="商品" min-width="130" />
            <el-table-column prop="quantity" label="数量" width="70" align="center">
              <template #default="{ row }"><span style="font-weight:600;">{{ row.quantity }}</span></template>
            </el-table-column>
            <el-table-column label="均价" width="100" align="right">
              <template #default="{ row }">¥{{ (row.costPrice || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">¥{{ ((row.costPrice || 0) * (row.quantity || 0)).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="grand-total" v-if="flatList.length && !selectedWarehouseNode">
        <span class="grand-total-label">📊 全部仓库合计</span>
        <span>{{ grandTotal.qty }} 件 · 金额 ¥{{ grandTotal.amt.toFixed(2) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tree-row { border-bottom: 1px solid #f0f0f0; }
.tree-row:last-child { border-bottom: none; }
.tree-row:hover { background: #fafafa; }
.tree-node {
  display: flex; align-items: center; gap: 8px; padding: 10px 0;
  cursor: pointer; user-select: none;
}
.tree-node.level-1 { background: #ecf5ff; margin: -1px -16px; padding: 10px 16px; border-radius: 6px 6px 0 0; font-size: 15px; font-weight: 700; }
.tree-node.level-1:hover { background: #d9ecff; }
.tree-node.level-2 { font-size: 14px; }
.tree-node.level-3 { font-size: 13px; }
.tree-node.level-4 { cursor: default; font-size: 13px; }
.toggle-icon { font-size: 10px; color: #909399; width: 12px; text-align: center; flex-shrink: 0; }
.node-name { color: #303133; font-weight: 600; white-space: nowrap; }
.node-code-index { font-size: 11px; color: #909399; background: #f5f5f5; padding: 0 6px; border-radius: 3px; line-height: 20px; flex-shrink: 0; }
.node-stats { font-size: 13px; font-weight: 600; margin-left: auto; white-space: nowrap; display: flex; gap: 6px; align-items: center; }
.stats-badge {
  background: #ecf5ff; color: #409eff; padding: 2px 8px; border-radius: 4px; font-size: 12px;
}
.stats-badge.amount {
  background: #fdf6ec; color: #e6a23c;
}
.leaf-summary {
  font-size: 13px; font-weight: 600; color: #303133;
  padding: 6px 12px; margin: 0 0 4px 0;
  background: #f0f9eb; border-left: 3px solid #67c23a; border-radius: 0 4px 4px 0;
}
.grand-total {
  display: flex; align-items: center; gap: 16px;
  text-align: right; padding: 14px 20px; margin-top: 8px;
  font-size: 15px; font-weight: 600; color: #303133;
  background: #f5f7fa; border-radius: 6px;
}
.grand-total-label {
  background: #409eff; color: #fff; padding: 4px 12px; border-radius: 4px; font-size: 13px;
}
</style>
