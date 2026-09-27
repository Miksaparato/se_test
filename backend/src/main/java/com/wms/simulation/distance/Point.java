package com.wms.simulation.distance;

/**
 * 二维平面坐标点（仓库平面图的坐标系，与 {@code locations.x / locations.y} 同口径）。
 *
 * <p>《接口文档》API-021 的 {@code exit {x, y}} 与 API-061 的 {@code points[{x, y}]}
 * 都用本结构表达，保证前端平面图组件（C-F1）拿到的是同一套坐标。
 *
 * @param x 平面坐标 x
 * @param y 平面坐标 y
 * @author c
 */
public record Point(int x, int y) {
}
