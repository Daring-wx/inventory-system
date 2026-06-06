package com.inventory.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.inventory.common.result.PageResult;
import com.inventory.common.result.R;
import com.inventory.common.util.ExcelUtil;
import com.inventory.product.entity.Product;
import com.inventory.product.entity.ProductExportVO;
import com.inventory.product.entity.ProductImportVO;
import com.inventory.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "商品管理")
@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "分页查询商品")
    @GetMapping("/page")
    public R<PageResult<Product>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Boolean alertOnly,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minSalePrice,
            @RequestParam(required = false) BigDecimal maxSalePrice,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(PageResult.of(productService.page(new Page<>(page, size), name, code, status, alertOnly, warehouseId, categoryId, minPrice, maxPrice, minSalePrice, maxSalePrice, startDate, endDate)));
    }

    @Operation(summary = "查询所有启用的商品")
    @GetMapping("/list")
    public R<List<Product>> list() {
        return R.ok(productService.list());
    }

    @Operation(summary = "根据ID获取商品")
    @GetMapping("/{id}")
    public R<Product> getById(@PathVariable Long id) {
        Product product = productService.getById(id);
        return R.ok(product);
    }

    @SaCheckRole("role_1")
    @Operation(summary = "新增商品")
    @PostMapping
    public R<Long> create(@RequestBody Product product) {
        productService.save(product);
        return R.ok(product.getId());
    }

    @SaCheckRole("role_1")
    @Operation(summary = "更新商品")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        productService.update(product);
        return R.ok();
    }

    @SaCheckRole("role_1")
    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }

    @SaCheckRole("role_1")
    @Operation(summary = "恢复已删除商品")
    @PutMapping("/{id}/restore")
    public R<Void> restore(@PathVariable Long id) {
        productService.restore(id);
        return R.ok();
    }

    @Operation(summary = "下载导入模板")
    @GetMapping("/import/template")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil.export(response, java.util.Collections.emptyList(), "商品导入模板", ProductImportVO.class);
    }

    @SaCheckRole("role_1")
    @Operation(summary = "导入商品")
    @PostMapping("/import")
    public R<String> importExcel(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            List<ProductImportVO> rows = com.alibaba.excel.EasyExcel.read(file.getInputStream())
                    .head(ProductImportVO.class).sheet().doReadSync();
            int count = productService.importExcel(rows);
            return R.ok("导入成功，共创建 " + count + " 个商品");
        } catch (Exception e) {
            throw new com.inventory.common.exception.BusinessException("导入失败: " + e.getMessage());
        }
    }

    @Operation(summary = "导出为导入格式")
    @GetMapping("/export-for-import")
    public void exportForImport(HttpServletResponse response) {
        List<ProductImportVO> voList = productService.getImportVOs();
        ExcelUtil.export(response, voList, "商品导入格式", ProductImportVO.class);
    }

    @Operation(summary = "导出商品（支持筛选条件）")
    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String ids,
                       @RequestParam(required = false) String name,
                       @RequestParam(required = false) String code,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false) Boolean alertOnly,
                       @RequestParam(required = false) Long warehouseId,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) java.math.BigDecimal minPrice,
                       @RequestParam(required = false) java.math.BigDecimal maxPrice,
                       @RequestParam(required = false) java.math.BigDecimal minSalePrice,
                       @RequestParam(required = false) java.math.BigDecimal maxSalePrice,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate) {
        List<Product> list = productService.listFiltered(name, code, status, alertOnly, warehouseId, categoryId,
                minPrice, maxPrice, minSalePrice, maxSalePrice, startDate, endDate);
        if (ids != null && !ids.isEmpty()) {
            List<Long> idList = Arrays.stream(ids.split(",")).map(Long::parseLong).collect(Collectors.toList());
            list = list.stream().filter(p -> idList.contains(p.getId())).collect(Collectors.toList());
        }
        List<ProductExportVO> voList = list.stream().map(p -> {
            ProductExportVO vo = new ProductExportVO();
            vo.setCode(p.getCode());
            vo.setName(p.getName());
            vo.setSpec(p.getSpec());
            vo.setUnit(p.getUnit());
            vo.setPurchasePrice(p.getPurchasePrice());
            vo.setSalePrice(p.getSalePrice());
            vo.setMinStock(p.getMinStock());
            vo.setInventoryQuantity(p.getInventoryQuantity());
            vo.setAlertStatus(p.getAlertStatus());
            vo.setCategoryName(p.getCategoryName());
            vo.setCreateTime(p.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
        ExcelUtil.export(response, voList, "商品列表", ProductExportVO.class);
    }

}
