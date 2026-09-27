package com.wms.data.warehouse;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.data.warehouse.dto.CreateWarehouseRequest;
import com.wms.data.warehouse.dto.UpdateWarehouseRequest;
import com.wms.data.warehouse.vo.WarehouseLayoutVO;
import com.wms.data.warehouse.vo.WarehouseVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仓库控制器，对应《接口文档》API-016 ~ API-021。
 *
 * <p>写操作需 {@code warehouse:manage}（仅 admin），读操作（含平面布局）需 {@code sim:view}
 * ——即四种角色都能查看仓库布局（FR-1.1 验收 2、界面需求 8.2）。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    /**
     * 构造仓库控制器。
     *
     * @param warehouseService 仓库服务
     */
    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /**
     * API-016 创建仓库。
     *
     * @param request 创建请求
     * @return 新建仓库
     */
    @PostMapping
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<WarehouseVO> create(@RequestBody @Valid CreateWarehouseRequest request) {
        return ApiResponse.ok(warehouseService.createWarehouse(request));
    }

    /**
     * API-017 仓库列表（分页）。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param keyword  编码/名称关键字
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<PageResult<WarehouseVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(warehouseService.listWarehouses(page, pageSize, keyword));
    }

    /**
     * API-018 仓库详情。
     *
     * @param id 仓库 id
     * @return 仓库详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<WarehouseVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(warehouseService.getWarehouse(id));
    }

    /**
     * API-019 更新仓库。
     *
     * @param id      仓库 id
     * @param request 更新请求
     * @return 更新后的仓库
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<WarehouseVO> update(@PathVariable Long id,
                                           @RequestBody @Valid UpdateWarehouseRequest request) {
        return ApiResponse.ok(warehouseService.updateWarehouse(id, request));
    }

    /**
     * API-020 删除仓库（存在货架时返回 42210）。
     *
     * @param id 仓库 id
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ApiResponse.ok();
    }

    /**
     * API-021 仓库平面布局（货架/库位坐标与状态，供可视化）。
     *
     * <p>消费方：c 的平面图组件（C-F1）、仓库总览页（C-F2）、b 的推荐高亮（B-F2）。
     *
     * @param id 仓库 id
     * @return 布局数据
     */
    @GetMapping("/{id}/layout")
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<WarehouseLayoutVO> layout(@PathVariable Long id) {
        return ApiResponse.ok(warehouseService.getLayout(id));
    }
}
