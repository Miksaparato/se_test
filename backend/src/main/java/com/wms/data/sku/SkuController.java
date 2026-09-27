package com.wms.data.sku;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.data.sku.dto.CreateSkuRequest;
import com.wms.data.sku.dto.UpdateSkuRequest;
import com.wms.data.sku.vo.SkuVO;
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
 * 货物控制器，对应《接口文档》API-029 ~ API-032。
 *
 * <p>读需 {@code sim:view}（四种角色均可），写需 {@code sku:manage}（admin、operator）。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/skus")
public class SkuController {

    private final SkuService skuService;

    /**
     * 构造货物控制器。
     *
     * @param skuService 货物服务
     */
    public SkuController(SkuService skuService) {
        this.skuService = skuService;
    }

    /**
     * API-029 货物列表（分页、可按品类过滤）。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param category 品类过滤
     * @param keyword  编码/名称关键字
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<PageResult<SkuVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(skuService.listSkus(page, pageSize, category, keyword));
    }

    /**
     * API-030 创建货物。
     *
     * @param request 创建请求
     * @return 新建货物
     */
    @PostMapping
    @PreAuthorize("hasAuthority('sku:manage')")
    public ApiResponse<SkuVO> create(@RequestBody @Valid CreateSkuRequest request) {
        return ApiResponse.ok(skuService.createSku(request));
    }

    /**
     * API-031 更新货物。
     *
     * @param id      货物 id
     * @param request 更新请求
     * @return 更新后的货物
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sku:manage')")
    public ApiResponse<SkuVO> update(@PathVariable Long id,
                                     @RequestBody @Valid UpdateSkuRequest request) {
        return ApiResponse.ok(skuService.updateSku(id, request));
    }

    /**
     * API-032 删除货物（被库位占用或被订单引用时返回 42210）。
     *
     * @param id 货物 id
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sku:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        skuService.deleteSku(id);
        return ApiResponse.ok();
    }
}
