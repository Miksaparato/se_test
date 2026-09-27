package com.wms.data.rack;

import com.wms.common.ApiResponse;
import com.wms.data.rack.dto.CreateRackRequest;
import com.wms.data.rack.dto.UpdateRackRequest;
import com.wms.data.rack.vo.RackVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 货架控制器，对应《接口文档》API-022 ~ API-024（均需 {@code warehouse:manage}）。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/racks")
@PreAuthorize("hasAuthority('warehouse:manage')")
public class RackController {

    private final RackService rackService;

    /**
     * 构造货架控制器。
     *
     * @param rackService 货架服务
     */
    public RackController(RackService rackService) {
        this.rackService = rackService;
    }

    /**
     * API-022 创建货架（可选同时批量生成库位）。
     *
     * @param request 创建请求
     * @return 新建货架（含已生成库位数）
     */
    @PostMapping
    public ApiResponse<RackVO> create(@RequestBody @Valid CreateRackRequest request) {
        return ApiResponse.ok(rackService.createRack(request));
    }

    /**
     * API-023 更新货架。
     *
     * @param id      货架 id
     * @param request 更新请求
     * @return 更新后的货架
     */
    @PutMapping("/{id}")
    public ApiResponse<RackVO> update(@PathVariable Long id,
                                      @RequestBody @Valid UpdateRackRequest request) {
        return ApiResponse.ok(rackService.updateRack(id, request));
    }

    /**
     * API-024 删除货架（存在库位时返回 42210）。
     *
     * @param id 货架 id
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        rackService.deleteRack(id);
        return ApiResponse.ok();
    }
}
