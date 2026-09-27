package com.wms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 库位分区（品类 ↔ 巷道）映射配置，绑定 {@code application.yml} 的 {@code wms.zone.*}。
 *
 * <p><b>为什么需要它</b>：冻结表结构 {@code locations} 没有分区列（《数据库设计说明书》2.3），
 * 但算法需要「库位属于哪个品类分区」这一信息：
 * <ul>
 *   <li>b 的推荐引擎 {@code S_other} 分项要做品类匹配（FR-2.1「其他（品类/尺寸/空位连续性）」）；</li>
 *   <li>c 的「分区存储」入库策略要按品类/频次分区、区内就近（FR-3.1）。</li>
 * </ul>
 * 因此分区由库位所属货架的 {@code racks.aisle}（巷道 A/B/C…）经本映射**派生**，
 * 既不动冻结表结构，又天然满足 FR-2.3「权重与规则允许管理员调整」。
 *
 * <p>未配置映射时 {@link #categoryOfAisle(String)} 返回 {@code null}，推荐引擎的品类匹配
 * 按「无分区约束」的中性分处理，不会因为缺配置而报错。
 *
 * @author a
 */
@Component
@ConfigurationProperties(prefix = "wms.zone")
public class ZoneProperties {

    /** 品类 ↔ 巷道映射：key = 巷道标识（如 A），value = 品类名（如 电子）。 */
    private Map<String, String> aisleCategory = new LinkedHashMap<>();

    /**
     * 获取品类 ↔ 巷道映射。
     *
     * @return 映射表（可修改，便于配置刷新）
     */
    public Map<String, String> getAisleCategory() {
        return aisleCategory;
    }

    /**
     * 设置品类 ↔ 巷道映射。
     *
     * @param aisleCategory 映射表
     */
    public void setAisleCategory(Map<String, String> aisleCategory) {
        this.aisleCategory = aisleCategory == null ? new LinkedHashMap<>() : aisleCategory;
    }

    /**
     * 按巷道查询所属品类分区（大小写不敏感，两侧空白忽略）。
     *
     * @param aisle 巷道标识，如 {@code A}
     * @return 品类名；未配置或无巷道时返回 null
     */
    public String categoryOfAisle(String aisle) {
        if (aisle == null || aisle.isBlank()) {
            return null;
        }
        String key = aisle.trim();
        String direct = aisleCategory.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : aisleCategory.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 反向查询：该品类对应的巷道集合（c 的分区存储策略按品类挑分区时使用）。
     *
     * @param category 品类名
     * @return 巷道集合；未配置时为空集合
     */
    public java.util.Set<String> aislesOfCategory(String category) {
        java.util.Set<String> aisles = new java.util.LinkedHashSet<>();
        if (category == null) {
            return aisles;
        }
        aisleCategory.forEach((aisle, cat) -> {
            if (category.equals(cat)) {
                aisles.add(aisle);
            }
        });
        return aisles;
    }
}
