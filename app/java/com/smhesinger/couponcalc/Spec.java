package com.smhesinger.couponcalc;

/** 一条比价项：某个规格的商品卖多少钱 */
public class Spec {

    public String name;
    public double amount;   // 规格数值，如 10
    public String unit;     // 规格单位，如 kg
    public double price;    // 售价

    public Spec(String name, double amount, String unit, double price) {
        this.name = name;
        this.amount = amount;
        this.unit = unit;
        this.price = price;
    }

    public int dim() {
        return Unit.dimOf(unit);
    }

    /** 折算到基准量：g / ml / 个 */
    public double baseAmount() {
        return amount * Unit.factorOf(unit);
    }

    /** 折算到展示量：斤 / L / 个 */
    public double displayAmount() {
        double s = Unit.baseSize(dim());
        return s > 0 ? baseAmount() / s : 0;
    }

    /** 单价：元/斤、元/L、元/个 */
    public double unitPrice() {
        double d = displayAmount();
        return d > 0 ? price / d : 0;
    }

    /** 「10 kg」 */
    public String specText() {
        return Fmt.qty(amount) + " " + unit;
    }

    /** 展示名，没填名称就用规格兜底 */
    public String title() {
        if (name != null && !name.trim().isEmpty()) return name.trim();
        return specText();
    }

    /** 买够 target 个「展示单位」需要几件 */
    public int packsFor(double target) {
        double d = displayAmount();
        if (d <= 0 || target <= 0) return 1;
        return (int) Math.ceil(target / d - 1e-9);
    }

    /** 买够 target 个「展示单位」要花多少钱（含超买的部分） */
    public double costFor(double target) {
        if (target <= 0) return price;
        return packsFor(target) * price;
    }
}
