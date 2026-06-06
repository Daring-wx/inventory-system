package com.inventory.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inventory.common.exception.BusinessException;
import com.inventory.inventory.entity.Inventory;
import com.inventory.inventory.mapper.InventoryMapper;
import com.inventory.product.entity.Product;
import com.inventory.product.entity.ProductCategory;
import com.inventory.product.mapper.ProductCategoryMapper;
import com.inventory.product.mapper.ProductMapper;
import com.inventory.purchase.entity.PurchaseOrderItem;
import com.inventory.purchase.mapper.PurchaseOrderItemMapper;
import com.inventory.sales.entity.SalesOrderItem;
import com.inventory.sales.mapper.SalesOrderItemMapper;
import com.inventory.stocktake.entity.StockTakeItem;
import com.inventory.stocktake.mapper.StockTakeItemMapper;
import com.inventory.transfer.entity.InventoryTransferItem;
import com.inventory.transfer.mapper.InventoryTransferItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.inventory.product.entity.ProductImportVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import cn.hutool.core.date.DateUtil;
import java.util.Date;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final InventoryMapper inventoryMapper;
    private final ProductCategoryMapper categoryMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final SalesOrderItemMapper salesOrderItemMapper;
    private final InventoryTransferItemMapper transferItemMapper;
    private final StockTakeItemMapper stockTakeItemMapper;

    public ProductService(ProductMapper productMapper, InventoryMapper inventoryMapper,
                          ProductCategoryMapper categoryMapper,
                          PurchaseOrderItemMapper purchaseOrderItemMapper,
                          SalesOrderItemMapper salesOrderItemMapper,
                          InventoryTransferItemMapper transferItemMapper,
                          StockTakeItemMapper stockTakeItemMapper) {
        this.productMapper = productMapper;
        this.inventoryMapper = inventoryMapper;
        this.categoryMapper = categoryMapper;
        this.purchaseOrderItemMapper = purchaseOrderItemMapper;
        this.salesOrderItemMapper = salesOrderItemMapper;
        this.transferItemMapper = transferItemMapper;
        this.stockTakeItemMapper = stockTakeItemMapper;
    }

    public Page<Product> page(Page<Product> page, String name, String code, Integer status, Boolean alertOnly, Long warehouseId, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal minSalePrice, BigDecimal maxSalePrice, String startDate, String endDate) {
        // 日期参数先判空再转换，避免 MyBatis-Plus 条件参数提前求值导致 NPE
        LocalDateTime startDateTime = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime endDateTime = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate).atTime(LocalTime.MAX) : null;
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(name != null, Product::getName, name)
                .like(code != null, Product::getCode, code)
                .eq(status != null, Product::getStatus, status)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .ge(minPrice != null, Product::getPurchasePrice, minPrice)
                .le(maxPrice != null, Product::getPurchasePrice, maxPrice)
                .ge(minSalePrice != null, Product::getSalePrice, minSalePrice)
                .le(maxSalePrice != null, Product::getSalePrice, maxSalePrice)
                .ge(startDateTime != null, Product::getCreateTime, startDateTime)
                .le(endDateTime != null, Product::getCreateTime, endDateTime);
        // 仓库筛选：先查商品ID再加IN条件，确保分页正常
        if (warehouseId != null) {
            List<Long> ids = inventoryMapper.selectList(
                    new LambdaQueryWrapper<Inventory>().eq(Inventory::getWarehouseId, warehouseId))
                    .stream().map(Inventory::getProductId).distinct().collect(Collectors.toList());
            if (!ids.isEmpty()) {
                wrapper.in(Product::getId, ids);
            } else {
                page.setRecords(new ArrayList<>());
                page.setTotal(0);
                return page;
            }
        }
        wrapper.orderByDesc(Product::getId);
        Page<Product> result = productMapper.selectPage(page, wrapper);
        for (Product p : result.getRecords()) {
            enrichInventory(p, warehouseId);
        }
        enrichCategoryNames(result.getRecords());
        if (Boolean.TRUE.equals(alertOnly)) {
            List<Product> filtered = result.getRecords().stream()
                    .filter(p -> "warning".equals(p.getAlertStatus()))
                    .collect(Collectors.toList());
            result.setRecords(filtered);
            result.setTotal(filtered.size());
        } else if (Boolean.FALSE.equals(alertOnly)) {
            List<Product> filtered = result.getRecords().stream()
                    .filter(p -> "normal".equals(p.getAlertStatus()))
                    .collect(Collectors.toList());
            result.setRecords(filtered);
            result.setTotal(filtered.size());
        }
        return result;
    }

    public List<Product> list() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1).orderByDesc(Product::getId));
    }

    public List<Product> listAll() {
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>().orderByDesc(Product::getId));
        for (Product p : list) enrichInventory(p);
        enrichCategoryNames(list);
        return list;
    }

    public List<Product> listFiltered(String name, String code, Integer status, Boolean alertOnly,
            Long warehouseId, Long categoryId, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
            java.math.BigDecimal minSalePrice, java.math.BigDecimal maxSalePrice, String startDate, String endDate) {
        LocalDateTime startDateTime = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime endDateTime = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate).atTime(LocalTime.MAX) : null;
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(name != null, Product::getName, name)
                .like(code != null, Product::getCode, code)
                .eq(status != null, Product::getStatus, status)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .ge(minPrice != null, Product::getPurchasePrice, minPrice)
                .le(maxPrice != null, Product::getPurchasePrice, maxPrice)
                .ge(minSalePrice != null, Product::getSalePrice, minSalePrice)
                .le(maxSalePrice != null, Product::getSalePrice, maxSalePrice)
                .ge(startDateTime != null, Product::getCreateTime, startDateTime)
                .le(endDateTime != null, Product::getCreateTime, endDateTime);
        if (warehouseId != null) {
            List<Long> ids = inventoryMapper.selectList(
                    new LambdaQueryWrapper<Inventory>().eq(Inventory::getWarehouseId, warehouseId))
                    .stream().map(Inventory::getProductId).distinct().collect(Collectors.toList());
            if (!ids.isEmpty()) wrapper.in(Product::getId, ids);
            else return new ArrayList<>();
        }
        wrapper.orderByDesc(Product::getId);
        List<Product> list = productMapper.selectList(wrapper);
        for (Product p : list) enrichInventory(p, warehouseId);
        enrichCategoryNames(list);
        // 预警筛选
        if (alertOnly != null) {
            list = list.stream().filter(p -> Boolean.TRUE.equals(alertOnly) ? "warning".equals(p.getAlertStatus()) : "normal".equals(p.getAlertStatus())).collect(Collectors.toList());
        }
        return list;
    }

    public Product getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product != null && product.getCategoryId() != null) {
            ProductCategory cat = categoryMapper.selectById(product.getCategoryId());
            if (cat != null) product.setCategoryName(cat.getName());
        }
        return product;
    }

    private void enrichInventory(Product p) {
        enrichInventory(p, null);
    }

    private void enrichInventory(Product p, Long warehouseId) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getProductId, p.getId());
        if (warehouseId != null) {
            wrapper.eq(Inventory::getWarehouseId, warehouseId);
        }
        List<Inventory> invs = inventoryMapper.selectList(wrapper);
        int total = invs.stream().mapToInt(Inventory::getQuantity).sum();
        p.setInventoryQuantity(total);
        p.setAlertStatus(p.getMinStock() != null && total < p.getMinStock() ? "warning" : "normal");
    }

    private void enrichCategoryNames(List<Product> products) {
        Set<Long> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) return;
        List<ProductCategory> categories = categoryMapper.selectBatchIds(categoryIds);
        Map<Long, String> nameMap = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
        for (Product p : products) {
            if (p.getCategoryId() != null) {
                p.setCategoryName(nameMap.get(p.getCategoryId()));
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(Product product) {
        product.setCode(generateProductCode());
        productMapper.insert(product);
    }

    @Transactional(rollbackFor = Exception.class)
    public int importExcel(List<ProductImportVO> rows) {
        Map<String, Long> catCache = new HashMap<>();
        // 加载已有分类到缓存
        categoryMapper.selectList(null).forEach(c ->
                catCache.put(c.getName() + ":" + (c.getParentId() == null ? 0 : c.getParentId()), c.getId()));

        // 加载已有商品：按名称和编码建立映射
        List<Product> allProducts = productMapper.selectList(null);
        Map<String, Product> byCode = new HashMap<>();
        Map<String, Product> byName = new HashMap<>();
        for (Product p : allProducts) {
            if (p.getCode() != null) byCode.put(p.getCode(), p);
            if (p.getName() != null) byName.put(p.getName(), p);
        }

        int count = 0;
        for (ProductImportVO row : rows) {
            if (row.getName() == null || row.getName().trim().isEmpty()) continue;
            String name = row.getName().trim();

            // 按编码或名称匹配已有商品，存在则更新，否则新建
            Product existing = null;
            if (row.getCode() != null && !row.getCode().trim().isEmpty()) {
                existing = byCode.get(row.getCode().trim());
            }
            if (existing == null) existing = byName.get(name);

            // 解析分类路径
            Long categoryId = null;
            if (row.getCategoryPath() != null && !row.getCategoryPath().trim().isEmpty()) {
                String[] parts = row.getCategoryPath().split("/");
                Long parentId = null;
                for (String part : parts) {
                    String catName = part.trim();
                    if (catName.isEmpty()) continue;
                    String cacheKey = catName + ":" + (parentId == null ? 0 : parentId);
                    Long existed = catCache.get(cacheKey);
                    if (existed != null) {
                        parentId = existed;
                        continue;
                    }
                    ProductCategory cat = new ProductCategory();
                    cat.setName(catName);
                    cat.setParentId(parentId);
                    cat.setStatus(1);
                    categoryMapper.insert(cat);
                    catCache.put(cacheKey, cat.getId());
                    parentId = cat.getId();
                }
                categoryId = parentId;
            }

            if (existing != null) {
                // 更新已有商品
                existing.setCategoryId(categoryId);
                if (row.getSpec() != null) existing.setSpec(row.getSpec());
                if (row.getUnit() != null) existing.setUnit(row.getUnit());
                if (row.getPurchasePrice() != null) existing.setPurchasePrice(row.getPurchasePrice());
                if (row.getSalePrice() != null) existing.setSalePrice(row.getSalePrice());
                if (row.getMinStock() != null) existing.setMinStock(row.getMinStock());
                if (row.getMaxStock() != null) existing.setMaxStock(row.getMaxStock());
                if (row.getStatus() != null) existing.setStatus("停用".equals(row.getStatus()) ? 0 : 1);
                productMapper.updateById(existing);
            } else {
                // 新建商品
                Product p = new Product();
                p.setName(name);
                p.setCategoryId(categoryId);
                p.setSpec(row.getSpec());
                p.setUnit(row.getUnit());
                p.setPurchasePrice(row.getPurchasePrice());
                p.setSalePrice(row.getSalePrice());
                p.setMinStock(row.getMinStock());
                p.setMaxStock(row.getMaxStock());
                p.setStatus("停用".equals(row.getStatus()) ? 0 : 1);
                // 编码：Excel有填则用，否则自动生成
                if (row.getCode() != null && !row.getCode().trim().isEmpty()
                        && productMapper.countAllByCode(row.getCode().trim()) == 0) {
                    p.setCode(row.getCode().trim());
                } else {
                    p.setCode(generateProductCode());
                }
                productMapper.insert(p);
                byName.put(name, p);
                if (p.getCode() != null) byCode.put(p.getCode(), p);
            }
            count++;
        }
        return count;
    }

    private synchronized String generateProductCode() {
        String prefix = "GD";
        String dateStr = DateUtil.format(new Date(), "yyyyMMdd");
        String likePrefix = prefix + dateStr;

        String maxCode = productMapper.selectMaxCodeByPrefix(likePrefix);
        int seq = 1;
        if (maxCode != null) {
            seq = Integer.parseInt(maxCode.substring(maxCode.length() - 6)) + 1;
        }

        String code = likePrefix + String.format("%06d", seq);
        while (productMapper.countAllByCode(code) > 0) {
            seq++;
            code = likePrefix + String.format("%06d", seq);
        }
        return code;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Product product) { productMapper.updateById(product); }

    public List<ProductImportVO> getImportVOs() {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>().orderByDesc(Product::getId));
        // 加载所有分类
        List<ProductCategory> allCats = categoryMapper.selectList(null);
        java.util.Map<Long, ProductCategory> catMap = allCats.stream()
                .collect(java.util.stream.Collectors.toMap(ProductCategory::getId, c -> c));
        // 构建分类路径
        java.util.function.Function<Long, String> buildPath = (id) -> {
            StringBuilder sb = new StringBuilder();
            Long currentId = id;
            while (currentId != null) {
                ProductCategory cat = catMap.get(currentId);
                if (cat == null) break;
                if (sb.length() > 0) sb.insert(0, "/");
                sb.insert(0, cat.getName());
                currentId = cat.getParentId();
            }
            return sb.toString();
        };
        return products.stream().map(p -> {
            ProductImportVO vo = new ProductImportVO();
            vo.setName(p.getName());
            vo.setCode(p.getCode());
            if (p.getCategoryId() != null) vo.setCategoryPath(buildPath.apply(p.getCategoryId()));
            vo.setSpec(p.getSpec());
            vo.setUnit(p.getUnit());
            vo.setPurchasePrice(p.getPurchasePrice());
            vo.setSalePrice(p.getSalePrice());
            vo.setMinStock(p.getMinStock());
            vo.setMaxStock(p.getMaxStock());
            vo.setStatus(p.getStatus() != null && p.getStatus() == 1 ? "启用" : "停用");
            return vo;
        }).collect(java.util.stream.Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 检查库存
        long invCount = inventoryMapper.selectCount(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getProductId, id));
        if (invCount > 0) throw new BusinessException("该商品存在库存记录，无法删除，请先清空库存");

        // 检查采购入库引用
        long poCount = purchaseOrderItemMapper.selectCount(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getProductId, id));
        if (poCount > 0) throw new BusinessException("该商品已被采购入库单引用，无法删除");

        // 检查销售出库引用
        long soCount = salesOrderItemMapper.selectCount(
                new LambdaQueryWrapper<SalesOrderItem>().eq(SalesOrderItem::getProductId, id));
        if (soCount > 0) throw new BusinessException("该商品已被销售出库单引用，无法删除");

        // 检查调拨引用
        long trCount = transferItemMapper.selectCount(
                new LambdaQueryWrapper<InventoryTransferItem>().eq(InventoryTransferItem::getProductId, id));
        if (trCount > 0) throw new BusinessException("该商品已被调拨单引用，无法删除");

        // 检查盘点引用
        long stCount = stockTakeItemMapper.selectCount(
                new LambdaQueryWrapper<StockTakeItem>().eq(StockTakeItem::getProductId, id));
        if (stCount > 0) throw new BusinessException("该商品已被盘点单引用，无法删除");

        productMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new com.inventory.common.exception.BusinessException("商品不存在");
        p.setDeleted(0);
        productMapper.updateById(p);
    }

}
