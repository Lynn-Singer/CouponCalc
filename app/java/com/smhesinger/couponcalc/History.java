package com.smhesinger.couponcalc;

/** 历史商品（用于名称联想与凑单推荐） */
public class History {

    public String name;
    public double price;
    public long ts;

    public History(String name, double price, long ts) {
        this.name = name;
        this.price = price;
        this.ts = ts;
    }
}
