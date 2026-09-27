package com.wms.data.location;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.data.location.dto.CreateLocationRequest;
import com.wms.data.location.dto.UpdateLocationRequest;
import com.wms.data.location.vo.LocationVO;
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
 * 库位控制器，对应《接口文档》API-025 ~ API-028。
 *
 * <p>写操作需 {@code warehouse:manage}，列表查询需 {@code sim:view}。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    /**
     * 构造库位控制器。
     *
     * @param locationService 库位服务
     */
    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * API-025 创建库位。
     *
     * @param request 创建请求
     * @return 新建库位
     */
    @PostMapping
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<LocationVO> create(@RequestBody @Valid CreateLocationRequest request) {
        return ApiResponse.ok(locationService.createLocation(request));
    }

    /**
     * API-026 更新库位。
     *
     * @param id      库位 id
     * @param request 更新请求
     * @return 更新后的库位
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<LocationVO> update(@PathVariable Long id,
                                          @RequestBody @Valid UpdateLocationRequest request) {
        return ApiResponse.ok(locationService.updateLocation(id, request));
    }

    /**
     * API-027 库位列表（可按状态/货架过滤），供 b 的推荐与 c 的平面图取候选库位。
     *
     * @param page        页码
     * @param pageSize    每页条数
     * @param warehouseId 仓库过滤
     * @param rackId      货架过滤
     * @param status      状态过滤：free / occupied / disabled
     * @param layer       层号过滤
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<PageResult<LocationVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "warehouse_id", required = false) Long warehouseId,
            @RequestParam(name = "rack_id", required = false) Long rackId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer layer) {
        return ApiResponse.ok(locationService.listLocations(page, pageSize, warehouseId, rackId,
                status, layer));
    }

    /**
     * API-028 删除库位（占用状态返回 42210）。
     *
     * @param id 库位 id
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        locationService.deleteLocation(id);
        return ApiResponse.ok();
    }
}
