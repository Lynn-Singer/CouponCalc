package com.smhesinger.couponcalc;

import java.util.ArrayList;
import java.util.List;

/** 一张消费券 = 面额 + 自己的购物清单 */
public class Coupon {

    public String name;
    public double amount;
    public final List<Item> items = new ArrayList<Item>();

    public Coupon(String name, double amount) {
        this.name = name;
        this.amount = amount;
    }

    public double total() {
        double t = 0;
        for (Item it : items) t += it.subtotal();
        return t;
    }
}
