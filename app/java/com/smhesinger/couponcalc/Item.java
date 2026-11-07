package com.smhesinger.couponcalc;

/** 购物车中的一条商品记录 */
public class Item {
    public String name;
    public double price;   // 单价
    public double qty;     // 数量（支持小数，如 0.5 kg）

    public Item(String name, double price, double qty) {
        this.name = name;
        this.price = price;
        this.qty = qty;
    }

    /** 本条小计 */
    public double subtotal() {
        return price * qty;
    }
}
