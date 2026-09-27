package com.wms.recommend.engine;

/**
 * 库位坐标键工具：以 "x,y,layer" 作为占用状态的唯一键，供空位连续性计算使用。
 * 与 {@code domain.repository.InMemoryWarehouseDataRepository#occupiedSnapshot} 的键格式一致。
 */
public final class CoordKey {

    private CoordKey() {
    }

    public static String key(int x, int y, int layer) {
        return x + "," + y + "," + layer;
    }
}
