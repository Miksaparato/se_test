package com.wms.data.location;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.domain.entity.Rack;
import org.springframework.util.StringUtils;

/**
 * 库位业务规则（纯函数，无状态、无依赖）。
 *
 * <p>抽出为独立类的原因：
 * <ol>
 *   <li>这些规则是 b 的推荐引擎与 c 的约束校验共同关心的口径（状态取值、层号语义），
 *       集中一处便于冻结与评审；</li>
 *   <li>纯函数可被单元测试直接覆盖，无需数据库与任何 mock（NFR-4）。</li>
 * </ol>
 *
 * <p>规则清单（对应《需求文档》5.4 约束与《数据库设计说明书》2.3 冻结契约）：
 * <ul>
 *   <li>{@code status} 只能是 free / occupied / disabled；</li>
 *   <li>{@code occupied} 与 {@code occupiedSkuId} 必须同时成立；</li>
 *   <li>层号不超过所属货架的层数；</li>
 *   <li>显式改成非 occupied 即视为释放货物（否则会残留「空闲但带货物」的脏数据）。</li>
 * </ul>
 *
 * @author a
 */
public final class LocationRules {

    private LocationRules() {
    }

    /**
     * 校验层号是否在货架层数范围内。
     *
     * @param layer 层号，可为 null（表示不修改）
     * @param rack  所属货架
     * @throws BizException 层号超出货架层数时抛 40001
     */
    public static void validateLayer(Integer layer, Rack rack) {
        if (layer == null || rack == null || rack.getLayerCount() == null) {
            return;
        }
        if (layer > rack.getLayerCount()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "层号 " + layer + " 超出货架 " + rack.getCode() + " 的层数上限 " + rack.getLayerCount());
        }
    }

    /**
     * 校验状态与占用货物的一致性。
     *
     * @param status        状态
     * @param occupiedSkuId 占用货物 id
     * @throws BizException 不一致时抛 40001
     */
    public static void validateStatusAndSku(String status, Long occupiedSkuId) {
        if (LocationService.STATUS_OCCUPIED.equals(status) && occupiedSkuId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "库位状态为 occupied 时必须提供 occupiedSkuId");
        }
        if (!LocationService.STATUS_OCCUPIED.equals(status) && occupiedSkuId != null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅 occupied 状态可设置 occupiedSkuId，请先调整库位状态");
        }
    }

    /**
     * 解析更新后的目标状态。
     *
     * @param requestedStatus 请求中的状态，可为空
     * @param currentStatus   当前状态
     * @return 目标状态
     */
    public static String resolveTargetStatus(String requestedStatus, String currentStatus) {
        return StringUtils.hasText(requestedStatus) ? requestedStatus : currentStatus;
    }

    /**
     * 解析更新后的目标占用货物。
     *
     * <p>语义：目标状态不是 occupied 时一律置为请求值（通常为 null）——即「改状态即释放货物」；
     * 目标状态是 occupied 时优先取请求值，未提供则沿用当前值。
     *
     * @param targetStatus     目标状态
     * @param requestedSkuId   请求中的占用货物 id，可为空
     * @param currentSkuId     当前占用货物 id，可为空
     * @return 目标占用货物 id
     */
    public static Long resolveTargetSkuId(String targetStatus, Long requestedSkuId, Long currentSkuId) {
        if (!LocationService.STATUS_OCCUPIED.equals(targetStatus)) {
            return requestedSkuId;
        }
        return requestedSkuId != null ? requestedSkuId : currentSkuId;
    }

    /**
     * 判断是否需要显式清空占用货物列。
     *
     * <p>MyBatis-Plus 的 {@code updateById} 会忽略 null 字段，因此把库位从占用改为空闲时，
     * 必须额外发一条条件更新把 {@code occupied_sku_id} 置空。
     *
     * @param targetStatus  目标状态
     * @param currentSkuId  当前占用货物 id
     * @return 需要清空返回 true
     */
    public static boolean needsClearOccupiedSku(String targetStatus, Long currentSkuId) {
        return !LocationService.STATUS_OCCUPIED.equals(targetStatus) && currentSkuId != null;
    }

    /**
     * 判断是否允许删除库位。
     *
     * @param status 库位状态
     * @return 允许删除返回 true
     */
    public static boolean deletable(String status) {
        return !LocationService.STATUS_OCCUPIED.equals(status);
    }
}
