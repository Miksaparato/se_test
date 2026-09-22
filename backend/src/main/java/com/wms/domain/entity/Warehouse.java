package com.wms.domain.entity;

/**
 * 仓库——对应《接口文档》API-021 冻结字段。
 * 出库口坐标 (exitX, exitY) 是距离/路程计算的原点（COM-6，曼哈顿距离，成员 c 牵头）。
 */
public class Warehouse {

    private Long id;
    private String name;
    private int exitX;
    private int exitY;

    public Warehouse() {
    }

    public Warehouse(Long id, String name, int exitX, int exitY) {
        this.id = id;
        this.name = name;
        this.exitX = exitX;
        this.exitY = exitY;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getExitX() {
        return exitX;
    }

    public void setExitX(int exitX) {
        this.exitX = exitX;
    }

    public int getExitY() {
        return exitY;
    }

    public void setExitY(int exitY) {
        this.exitY = exitY;
    }
}
