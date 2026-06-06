package com.inventory.warehouse.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inventory.common.result.PageResult;
import com.inventory.common.result.R;
import com.inventory.common.util.ExcelUtil;
import com.inventory.warehouse.entity.Warehouse;
import com.inventory.warehouse.entity.WarehouseExportVO;
import com.inventory.warehouse.entity.WarehouseImportVO;
import com.inventory.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "仓库管理")
@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @Operation(summary = "获取仓库树形结构")
    @GetMapping("/tree")
    public R<List<Warehouse>> tree() {
        return R.ok(warehouseService.tree());
    }

    @Operation(summary = "获取根节点仓库（懒加载用）")
    @GetMapping("/roots")
    public R<List<Warehouse>> roots(@RequestParam(required = false) Integer status) {
        return R.ok(warehouseService.roots(status));
    }

    @Operation(summary = "获取子级仓库列表（含已停用，懒加载用）")
    @GetMapping("/children-all/{parentId}")
    public R<List<Warehouse>> childrenAll(@PathVariable Long parentId,
                                           @RequestParam(required = false) Integer status) {
        return R.ok(warehouseService.childrenAll(parentId, status));
    }

    @Operation(summary = "获取子级仓库列表（仅启用）")
    @GetMapping("/children/{parentId}")
    public R<List<Warehouse>> children(@PathVariable Long parentId) {
        return R.ok(warehouseService.children(parentId));
    }

    @Operation(summary = "搜索仓库")
    @GetMapping("/search")
    public R<List<Warehouse>> search(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer level,
                                      @RequestParam(required = false) Integer status) {
        return R.ok(warehouseService.search(keyword, level, status));
    }

    @Operation(summary = "查询所有启用的仓库")
    @GetMapping("/list")
    public R<List<Warehouse>> list() {
        return R.ok(warehouseService.listAll());
    }

    @Operation(summary = "分页查询仓库")
    @GetMapping("/page")
    public R<PageResult<Warehouse>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String contact,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Long parentId) {
        return R.ok(PageResult.of(warehouseService.page(new Page<>(page, size), name, contact, phone, address, status, level, parentId)));
    }

    @Operation(summary = "获取仓库详情")
    @GetMapping("/detail/{id}")
    public R<Warehouse> getById(@PathVariable Long id) {
        return R.ok(warehouseService.getById(id));
    }

    @Operation(summary = "根据ID获取仓库（小程序使用）")
    @GetMapping("/{id}")
    public R<Warehouse> getByIdShort(@PathVariable Long id) {
        return R.ok(warehouseService.getById(id));
    }

    @SaCheckRole("role_1")
    @Operation(summary = "新增仓库")
    @PostMapping
    public R<Void> create(@RequestBody Warehouse warehouse) {
        warehouseService.save(warehouse);
        return R.ok();
    }

    @SaCheckRole("role_1")
    @Operation(summary = "更新仓库")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Warehouse warehouse) {
        warehouse.setId(id);
        warehouseService.update(warehouse);
        return R.ok();
    }

    @SaCheckRole("role_1")
    @Operation(summary = "删除仓库")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return R.ok();
    }

    @Operation(summary = "下载导入模板")
    @GetMapping("/import/template")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil.export(response, java.util.Collections.emptyList(), "仓库导入模板", WarehouseImportVO.class);
    }

    @Operation(summary = "导出仓库")
    @GetMapping("/export")
    public void export(HttpServletResponse response) {
        List<Warehouse> all = warehouseService.exportAll();
        // 构建 ID → 仓库 映射，用于向上追溯父级
        Map<Long, Warehouse> map = all.stream().collect(Collectors.toMap(Warehouse::getId, w -> w));
        List<WarehouseExportVO> voList = all.stream().map(w -> {
            WarehouseExportVO vo = new WarehouseExportVO();
            vo.setContact(w.getContact());
            vo.setPhone(w.getPhone());
            vo.setAddress(w.getAddress());
            vo.setCode(w.getCode());
            vo.setStatus(w.getStatus() != null && w.getStatus() == 1 ? "启用" : "停用");
            // 按层级填充 一级~四级
            switch (w.getLevel() != null ? w.getLevel() : 1) {
                case 4: vo.setLevel4Name(w.getName());
                    Warehouse p3 = map.get(w.getParentId());
                    if (p3 != null) {
                        vo.setLevel3Name(p3.getName());
                        Warehouse p2 = map.get(p3.getParentId());
                        if (p2 != null) {
                            vo.setLevel2Name(p2.getName());
                            Warehouse p1 = map.get(p2.getParentId());
                            if (p1 != null) vo.setLevel1Name(p1.getName());
                        }
                    }
                    break;
                case 3: vo.setLevel3Name(w.getName());
                    Warehouse p2 = map.get(w.getParentId());
                    if (p2 != null) {
                        vo.setLevel2Name(p2.getName());
                        Warehouse p1 = map.get(p2.getParentId());
                        if (p1 != null) vo.setLevel1Name(p1.getName());
                    }
                    break;
                case 2: vo.setLevel2Name(w.getName());
                    Warehouse p1 = map.get(w.getParentId());
                    if (p1 != null) vo.setLevel1Name(p1.getName());
                    break;
                default: vo.setLevel1Name(w.getName()); break;
            }
            return vo;
        }).collect(Collectors.toList());
        ExcelUtil.export(response, voList, "仓库列表", WarehouseExportVO.class);
    }

    @SaCheckRole("role_1")
    @Operation(summary = "导入仓库")
    @PostMapping("/import")
    public R<String> importExcel(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            List<WarehouseImportVO> rows = com.alibaba.excel.EasyExcel.read(file.getInputStream())
                    .head(WarehouseImportVO.class).sheet().doReadSync();
            int count = warehouseService.importExcel(rows);
            return R.ok("导入成功，共创建 " + count + " 个仓库");
        } catch (Exception e) {
            throw new com.inventory.common.exception.BusinessException("导入失败: " + e.getMessage());
        }
    }

    @SaCheckRole("role_1")
    @Operation(summary = "恢复已删除仓库")
    @PutMapping("/{id}/restore")
    public R<Void> restore(@PathVariable Long id) {
        warehouseService.restore(id);
        return R.ok();
    }
}
