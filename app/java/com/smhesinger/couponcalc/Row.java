package com.smhesinger.couponcalc;

/**
 * 清单行：一条商品 + 它所属的券索引。
 * 逐张核销模式下 coupon 就是当前券；合并结算模式下清单跨券平铺，靠 coupon 定位归属。
 */
public class Row {

    public final int coupon;
    public final Item item;

    public Row(int coupon, Item item) {
        this.coupon = coupon;
        this.item = item;
    }
}
