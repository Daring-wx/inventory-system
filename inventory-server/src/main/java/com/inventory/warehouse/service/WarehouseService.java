package com.inventory.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inventory.common.exception.BusinessException;
import com.inventory.inventory.entity.Inventory;
import com.inventory.inventory.mapper.InventoryMapper;
import com.inventory.product.entity.Product;
import com.inventory.product.mapper.ProductMapper;
import com.inventory.purchase.entity.PurchaseOrder;
import com.inventory.purchase.mapper.PurchaseOrderMapper;
import com.inventory.sales.entity.SalesOrder;
import com.inventory.sales.mapper.SalesOrderMapper;
import com.inventory.transfer.entity.InventoryTransfer;
import com.inventory.transfer.mapper.InventoryTransferMapper;
import com.inventory.warehouse.entity.Warehouse;
import com.inventory.warehouse.mapper.WarehouseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.inventory.warehouse.entity.WarehouseImportVO;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import cn.hutool.core.date.DateUtil;
import java.util.Date;

@Service
public class WarehouseService {

    private final WarehouseMapper warehouseMapper;
    private final InventoryMapper inventoryMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final InventoryTransferMapper transferMapper;
    private final ProductMapper productMapper;

    public WarehouseService(WarehouseMapper warehouseMapper, InventoryMapper inventoryMapper,
                            PurchaseOrderMapper purchaseOrderMapper, SalesOrderMapper salesOrderMapper,
                            InventoryTransferMapper transferMapper,
                            ProductMapper productMapper) {
        this.warehouseMapper = warehouseMapper;
        this.inventoryMapper = inventoryMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.transferMapper = transferMapper;
        this.productMapper = productMapper;
    }

    public Warehouse getById(Long id) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w != null) w.setHasChildren(hasChildren(w.getId()));
        return w;
    }

    public List<Warehouse> tree() {
        List<Warehouse> all = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .orderByAsc(Warehouse::getId));
        for (Warehouse w : all) enrichStats(w);
        Map<Long, Warehouse> map = all.stream().collect(Collectors.toMap(Warehouse::getId, w -> w));
        List<Warehouse> roots = new java.util.ArrayList<>();
        for (Warehouse w : all) {
            if (w.getParentId() == null) {
                roots.add(w);
            } else {
                Warehouse parent = map.get(w.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) parent.setChildren(new java.util.ArrayList<>());
                    parent.getChildren().add(w);
                }
            }
        }
        return roots;
    }

    public List<Warehouse> children(Long parentId) {
        return warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getParentId, parentId)
                .eq(Warehouse::getStatus, 1)
                .orderByAsc(Warehouse::getId));
    }

    public List<Warehouse> search(String keyword, Integer level) {
        LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getStatus, 1)
                .eq(level != null, Warehouse::getLevel, level);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Warehouse::getName, keyword).or().like(Warehouse::getCode, keyword));
        }
        List<Warehouse> list = warehouseMapper.selectList(wrapper.orderByAsc(Warehouse::getId));
        for (Warehouse w : list) {
            enrichStats(w);
            w.setHasChildren(hasChildren(w.getId()));
        }
        return list;
    }

    public List<Warehouse> listAll() {
        // 返回所有叶子节点（无子级的仓库）用于单据选择
        List<Long> parentIds = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>()
                        .isNotNull(Warehouse::getParentId)
                        .select(Warehouse::getParentId))
                .stream().map(Warehouse::getParentId).distinct().collect(Collectors.toList());
        List<Warehouse> list = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getStatus, 1)
                .notIn(!parentIds.isEmpty(), Warehouse::getId, parentIds)
                .orderByAsc(Warehouse::getId));
        for (Warehouse w : list) enrichStats(w);
        return list;
    }

    public List<Warehouse> exportAll() {
        // 导出全部仓库（按层级显示）
        return warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .orderByAsc(Warehouse::getLevel, Warehouse::getId));
    }

    public List<Warehouse> roots() {
        List<Warehouse> list = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getLevel, 1)
                .eq(Warehouse::getDeleted, 0)
                .orderByAsc(Warehouse::getId));
        for (Warehouse w : list) {
            w.setHasChildren(hasChildren(w.getId()));
            enrichStats(w);
        }
        return list;
    }

    public List<Warehouse> childrenAll(Long parentId) {
        List<Warehouse> list = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getParentId, parentId)
                .eq(Warehouse::getDeleted, 0)
                .orderByAsc(Warehouse::getId));
        for (Warehouse w : list) {
            w.setHasChildren(hasChildren(w.getId()));
            enrichStats(w);
        }
        return list;
    }

    public Page<Warehouse> page(Page<Warehouse> page, String name, String contact, String phone,
                                String address, Integer status, Integer level, Long parentId) {
        LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<Warehouse>()
                .like(name != null, Warehouse::getName, name)
                .like(contact != null, Warehouse::getContact, contact)
                .like(phone != null, Warehouse::getPhone, phone)
                .like(address != null, Warehouse::getAddress, address)
                .eq(status != null, Warehouse::getStatus, status)
                .eq(level != null, Warehouse::getLevel, level)
                .eq(parentId != null, Warehouse::getParentId, parentId)
                .orderByDesc(Warehouse::getId);
        Page<Warehouse> result = warehouseMapper.selectPage(page, wrapper);
        for (Warehouse w : result.getRecords()) {
            enrichStats(w);
            if (w.getParentId() != null) {
                Warehouse parent = warehouseMapper.selectById(w.getParentId());
                if (parent != null) w.setParentName(parent.getName());
            }
        }
        return result;
    }

    private boolean hasChildren(Long id) {
        return warehouseMapper.selectCount(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getParentId, id)) > 0;
    }

    private void enrichStats(Warehouse w) {
        if (hasChildren(w.getId())) {
            // 父节点：从所有叶子节点汇总
            List<Long> leafIds = collectLeafIds(w.getId());
            if (leafIds.isEmpty()) {
                w.setProductCount(0);
                w.setTotalAmount(BigDecimal.ZERO);
                return;
            }
            List<Inventory> invs = inventoryMapper.selectList(
                    new LambdaQueryWrapper<Inventory>().in(Inventory::getWarehouseId, leafIds));
            int count = invs.stream().mapToInt(Inventory::getQuantity).sum();
            w.setProductCount(count);
            BigDecimal amount = BigDecimal.ZERO;
            for (Inventory inv : invs) {
                if (inv.getCostPrice() != null) {
                    amount = amount.add(inv.getCostPrice().multiply(BigDecimal.valueOf(inv.getQuantity())));
                }
            }
            w.setTotalAmount(amount);
        } else {
            // 叶子节点：直接查库存
            List<Inventory> invs = inventoryMapper.selectList(
                    new LambdaQueryWrapper<Inventory>().eq(Inventory::getWarehouseId, w.getId()));
            int count = invs.stream().mapToInt(Inventory::getQuantity).sum();
            w.setProductCount(count);
            BigDecimal amount = BigDecimal.ZERO;
            for (Inventory inv : invs) {
                if (inv.getCostPrice() != null) {
                    amount = amount.add(inv.getCostPrice().multiply(BigDecimal.valueOf(inv.getQuantity())));
                }
            }
            w.setTotalAmount(amount);
        }
    }

    private List<Long> collectLeafIds(Long parentId) {
        List<Warehouse> children = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getParentId, parentId));
        List<Long> ids = new java.util.ArrayList<>();
        for (Warehouse child : children) {
            if (hasChildren(child.getId())) {
                ids.addAll(collectLeafIds(child.getId()));
            } else {
                ids.add(child.getId());
            }
        }
        return ids;
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(Warehouse warehouse) {
        warehouse.setCode(generateWarehouseCode());
        // 校验层级连续性：子级层级 = 父级层级 + 1
        if (warehouse.getParentId() != null) {
            Warehouse parent = warehouseMapper.selectById(warehouse.getParentId());
            if (parent != null && warehouse.getLevel() != null) {
                if (parent.getLevel() == null || warehouse.getLevel() != parent.getLevel() + 1) {
                    throw new BusinessException("仓库层级不连续，子级层级必须为父级层级+1");
                }
                // 有库存的仓库不可再建子级（库存归属会混乱）
                long invCount = inventoryMapper.selectCount(
                        new LambdaQueryWrapper<Inventory>().eq(Inventory::getWarehouseId, warehouse.getParentId()));
                if (invCount > 0) {
                    throw new BusinessException("该仓库存在库存记录，不可新建子级仓库，请先清空库存");
                }
            }
        }
        warehouseMapper.insert(warehouse);
    }

    private synchronized String generateWarehouseCode() {
        String prefix = "WH";
        String dateStr = DateUtil.format(new Date(), "yyyyMMdd");
        String likePrefix = prefix + dateStr;

        String maxCode = warehouseMapper.selectMaxCodeByPrefix(likePrefix);
        int seq = 1;
        if (maxCode != null) {
            seq = Integer.parseInt(maxCode.substring(maxCode.length() - 6)) + 1;
        }

        String code = likePrefix + String.format("%06d", seq);
        while (warehouseMapper.countAllByCode(code) > 0) {
            seq++;
            code = likePrefix + String.format("%06d", seq);
        }
        return code;
    }

    @Transactional
    public void update(Warehouse warehouse) {
        Warehouse existing = warehouseMapper.selectById(warehouse.getId());
        if (existing == null) throw new BusinessException("仓库不存在");

        // 禁止修改层级
        if (warehouse.getLevel() != null && !warehouse.getLevel().equals(existing.getLevel())) {
            throw new BusinessException("层级不可修改");
        }

        // 禁止修改有库存仓库的上级（防止库存归属混乱）
        if (warehouse.getParentId() != null && !warehouse.getParentId().equals(existing.getParentId())) {
            long invCount = inventoryMapper.selectCount(
                    new LambdaQueryWrapper<Inventory>().eq(Inventory::getWarehouseId, warehouse.getId()));
            if (invCount > 0) {
                throw new BusinessException("该仓库存在库存记录，无法调整上级");
            }
            long childCount = warehouseMapper.selectCount(
                    new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getParentId, warehouse.getId()));
            if (childCount > 0) {
                throw new BusinessException("该仓库存在子级仓库，无法调整上级");
            }
        }

        warehouseMapper.updateById(warehouse);
    }

    @Transactional
    public void delete(Long id) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w == null) throw new BusinessException("仓库不存在");

        // 父节点：检查是否有子仓库
        if (hasChildren(id)) {
            throw new BusinessException("该仓库存在子级仓库，请先删除子级仓库");
        }

        // 检查库存
        long invCount = inventoryMapper.selectCount(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getWarehouseId, id));
        if (invCount > 0) throw new BusinessException("该仓库存在库存记录，无法删除，请先清空库存");

        // 检查采购入库单引用（包含所有状态）
        long purchaseCount = purchaseOrderMapper.selectCount(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getWarehouseId, id));
        if (purchaseCount > 0) throw new BusinessException("该仓库已被采购入库单引用，无法删除");

        // 检查销售出库单引用（包含所有状态）
        long salesCount = salesOrderMapper.selectCount(
                new LambdaQueryWrapper<SalesOrder>().eq(SalesOrder::getWarehouseId, id));
        if (salesCount > 0) throw new BusinessException("该仓库已被销售出库单引用，无法删除");

        // 检查调拨单引用（包含所有状态）
        long transferCount = transferMapper.selectCount(
                new LambdaQueryWrapper<InventoryTransfer>()
                        .eq(InventoryTransfer::getFromWarehouseId, id)
                        .or()
                        .eq(InventoryTransfer::getToWarehouseId, id));
        if (transferCount > 0) throw new BusinessException("该仓库已被调拨单引用，无法删除");

        warehouseMapper.deleteById(id);
    }

    @Transactional
    public void restore(Long id) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w == null) throw new BusinessException("仓库不存在");
        w.setDeleted(0);
        warehouseMapper.updateById(w);
    }

    @Transactional(rollbackFor = Exception.class)
    public int importExcel(List<WarehouseImportVO> rows) {
        // 缓存 key=名称:父级ID → warehouseId，避免重复查库
        Map<String, Long> cache = new HashMap<>();
        int created = 0;

        // 先加载已有仓库到缓存
        List<Warehouse> existing = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getDeleted, 0));
        for (Warehouse w : existing) {
            String key = w.getName() + ":" + (w.getParentId() == null ? 0 : w.getParentId());
            cache.putIfAbsent(key, w.getId());
        }

        for (WarehouseImportVO row : rows) {
            // 逐级处理 一级→二级→三级→四级
            Long parentId = null;
            int level = 0;
            String[] names = { row.getLevel1Name(), row.getLevel2Name(),
                               row.getLevel3Name(), row.getLevel4Name() };

            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                if (name == null || name.trim().isEmpty()) continue;
                name = name.trim();
                level = i + 1;

                String cacheKey = name + ":" + (parentId == null ? 0 : parentId);
                Long existedId = cache.get(cacheKey);

                if (existedId != null) {
                    parentId = existedId;
                    continue;
                }

                // 创建新仓库
                Warehouse w = new Warehouse();
                w.setName(name);
                w.setParentId(parentId);
                w.setLevel(level);
                // 最后一级（叶子）才设置状态/联系人/电话/地址
                boolean isLast = (i == names.length - 1) || (i + 1 < names.length &&
                        (names[i + 1] == null || names[i + 1].trim().isEmpty()));
                if (isLast) {
                    // 状态：空/启用/停用 → 1/1/0
                    String statusStr = row.getStatus();
                    w.setStatus("停用".equals(statusStr) ? 0 : 1);
                    w.setContact(row.getContact());
                    w.setPhone(row.getPhone());
                    w.setAddress(row.getAddress());
                } else {
                    w.setStatus(1);
                }
                // 编码：仅叶子节点使用客户自定义编码，父节点始终自动生成
                String customCode = row.getCode();
                if (isLast && customCode != null && !customCode.trim().isEmpty()) {
                    w.setCode(customCode.trim());
                } else {
                    w.setCode(generateWarehouseCode());
                }
                warehouseMapper.insert(w);

                cache.put(cacheKey, w.getId());
                parentId = w.getId();
                created++;
            }
        }
        return created;
    }
}
