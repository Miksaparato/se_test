package com.wms.domain.entity;

/**
 * 库位——对应《接口文档》API-021 / API-027 冻结字段。
 * 坐标 (x,y) 为二维平面坐标，layer 为层号（1 = 最底层）。
 */
public class Location {

    private Long id;
    /** 唯一编码，如 A-01-03-02。 */
    private String code;
    private int x;
    private int y;
    /** 层号，1 为最底层，越大越高。 */
    private int layer;
    /** 状态：free / occupied。 */
    private String status;
    /** 容量，预留尺寸校验。 */
    private double capacity;
    /** 分区品类（可空），用于「其他」分项中的品类匹配。 */
    private String category;
    private Long warehouseId;

    public Location() {
    }

    public Location(Long id, String code, int x, int y, int layer, Long warehouseId) {
        this.id = id;
        this.code = code;
        this.x = x;
        this.y = y;
        this.layer = layer;
        this.status = "free";
        this.warehouseId = warehouseId;
    }

    public boolean isFree() {
        return "free".equals(status);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getCapacity() {
        return capacity;
    }

    public void setCapacity(double capacity) {
        this.capacity = capacity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }
}
