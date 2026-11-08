package com.smhesinger.couponcalc;

/**
 * 比价核心算法的离线自测，不依赖 Android 运行时。
 * 跑法（项目根目录）：
 *   javac -encoding UTF-8 -d build\testout app\java\com\smhesinger\couponcalc\Unit.java ^
 *         app\java\com\smhesinger\couponcalc\Spec.java app\java\com\smhesinger\couponcalc\Fmt.java ^
 *         tools\test\com\smhesinger\couponcalc\TestCalc.java
 *   java -cp build\testout com.smhesinger.couponcalc.TestCalc
 */
public class TestCalc {

    private static int pass = 0;
    private static int fail = 0;

    private static void eq(String name, double got, double want, double eps) {
        boolean ok = Math.abs(got - want) <= eps;
        System.out.println((ok ? "PASS  " : "FAIL  ") + name
                + "   got=" + got + "  want=" + want);
        if (ok) pass++;
        else fail++;
    }

    private static void eqs(String name, String got, String want) {
        boolean ok = want.equals(got);
        System.out.println((ok ? "PASS  " : "FAIL  ") + name
                + "   got=" + got + "  want=" + want);
        if (ok) pass++;
        else fail++;
    }

    public static void main(String[] args) {
        System.out.println("--- 单位折算 ---");
        Spec r10 = new Spec("东北大米", 10, "kg", 89.90);
        Spec r5 = new Spec("东北大米", 5, "kg", 49.90);
        Spec r2_5 = new Spec("东北大米", 2.5, "kg", 26.90);
        Spec r1 = new Spec("东北大米", 1, "kg", 12.90);

        eq("10kg 折斤", r10.displayAmount(), 20, 1e-9);
        eq("10kg 元/斤", r10.unitPrice(), 4.495, 1e-9);
        eqs("10kg 单价显示", Fmt.two(r10.unitPrice()), "4.50");
        eqs("5kg  单价显示", Fmt.two(r5.unitPrice()), "4.99");
        eqs("2.5kg 单价显示", Fmt.two(r2_5.unitPrice()), "5.38");

        eq("500g 与 0.5kg 等价",
                new Spec("", 500, "g", 12.9).unitPrice(),
                new Spec("", 0.5, "kg", 12.9).unitPrice(), 1e-9);
        eq("1斤 = 500g",
                new Spec("", 1, "斤", 10).baseAmount(),
                new Spec("", 500, "g", 10).baseAmount(), 1e-9);
        eq("2两 = 100g",
                new Spec("", 2, "两", 10).baseAmount(),
                new Spec("", 100, "g", 10).baseAmount(), 1e-9);

        System.out.println("--- 容量折算 ---");
        Spec m250 = new Spec("伊利", 250, "ml", 3.50);
        Spec m1L = new Spec("伊利", 1, "L", 13.90);
        Spec m3000 = new Spec("伊利箱装", 3000, "ml", 45.90);
        eq("250ml 元/L", m250.unitPrice(), 14.00, 1e-9);
        eq("1L 装元/L", m1L.unitPrice(), 13.90, 1e-9);
        eq("3000ml 元/L", m3000.unitPrice(), 15.30, 1e-9);

        System.out.println("--- 维度隔离 ---");
        eq("重量维度", r10.dim(), Unit.DIM_MASS, 0);
        eq("容量维度", m1L.dim(), Unit.DIM_VOL, 0);
        eq("抽纸维度", new Spec("抽纸", 120, "抽", 19.9).dim(), Unit.DIM_COUNT, 0);
        eqs("重量单价标签", Unit.priceLabel(Unit.DIM_MASS), "元/斤");
        eqs("容量单价标签", Unit.priceLabel(Unit.DIM_VOL), "元/L");
        eqs("个数单价标签", Unit.priceLabel(Unit.DIM_COUNT), "元/个");

        System.out.println("--- 按需购买（目标 2kg = 4 斤）---");
        double target = 4;
        eq("10kg 要买 1 件", r10.packsFor(target), 1, 1e-9);
        eq("1kg  要买 2 件", r1.packsFor(target), 2, 1e-9);
        eq("10kg 实际支出", r10.costFor(target), 89.90, 1e-9);
        eq("1kg  实际支出", r1.costFor(target), 25.80, 1e-9);
        eq("2.5kg 实际支出", r2_5.costFor(target), 26.90, 1e-9);
        eq("无目标时只买 1 件", r1.costFor(0), 12.90, 1e-9);

        System.out.println("--- 排名（结果核对）---");
        System.out.println("  按单价排序：1kg 25.80/2斤=" + Fmt.two(r1.unitPrice())
                + "  10kg " + Fmt.two(r10.unitPrice())
                + "  5kg " + Fmt.two(r5.unitPrice())
                + "  2.5kg " + Fmt.two(r2_5.unitPrice()));
        System.out.println("  最划算(单价) = " + (r10.unitPrice() < r5.unitPrice()
                && r10.unitPrice() < r2_5.unitPrice() && r10.unitPrice() < r1.unitPrice()
                ? "10kg ✔" : "算错了 ✘"));
        System.out.println("  按需 4 斤最省钱 = " + (r1.costFor(target) < r2_5.costFor(target)
                && r1.costFor(target) < r5.costFor(target) && r1.costFor(target) < r10.costFor(target)
                ? "1kg 装 (¥25.80) ✔ —— 大包装单价低但用不完反而更贵"
                : "算错了 ✘"));

        System.out.println();
        System.out.println("结果：" + pass + " 通过, " + fail + " 失败");
        if (fail > 0) System.exit(1);
    }
}
