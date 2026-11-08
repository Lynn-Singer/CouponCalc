package com.smhesinger.couponcalc;

import java.util.ArrayList;
import java.util.List;

/**
 * 重新分配算法的离线自测（不依赖 Android 运行时）。
 * 跑法（项目根目录）：
 *   javac -encoding UTF-8 -d build\testout app\java\com\smhesinger\couponcalc\Item.java ^
 *         app\java\com\smhesinger\couponcalc\Coupon.java ^
 *         app\java\com\smhesinger\couponcalc\Optimizer.java ^
 *         tools\test\com\smhesinger\couponcalc\OptimizerTest.java
 *   java -cp build\testout com.smhesinger.couponcalc.OptimizerTest
 */
public class OptimizerTest {

    private static int pass = 0;
    private static int fail = 0;

    private static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + "   " + detail);
        if (ok) pass++;
        else fail++;
    }

    public static void main(String[] args) {
        threeCoupons();
        twoCoupons();
        noSplitMode();
        cannotCover();
        freeBin();
        bigCart();
        ignoreLast();
        giveUps();
        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
        if (fail > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- 用例

    /** 3 张 ¥103 的券：现状有一张差钱，重新分配应该让三张都够 */
    private static void threeCoupons() {
        System.out.println("--- 3 张 ¥103（商品合计 319.30 > 面额合计 309）---");
        List<Coupon> cs = coupons(103, 103, 103);
        add(cs.get(0), "洗衣液", 39.90, 1);
        add(cs.get(0), "抽纸", 15.90, 2);
        add(cs.get(0), "大米", 26.90, 1);
        add(cs.get(0), "排骨", 49.90, 1);
        add(cs.get(1), "牛奶", 12.90, 3);
        add(cs.get(1), "鸡蛋", 15.80, 1);
        add(cs.get(1), "面包", 8.50, 2);
        add(cs.get(1), "苹果", 5.50, 3);
        add(cs.get(2), "食用油", 59.90, 1);
        add(cs.get(2), "酱油", 9.90, 1);
        add(cs.get(2), "挂面", 6.50, 2);

        Optimizer.Plan p = Optimizer.solve(cs, true);
        check("三张都能凑够", p.allCovered(),
                "缺口=" + p.deficitAfter() + " 最大超额=" + p.maxExcessAfter());
        check("总金额守恒", sameTotal(cs, p), money(p));
        check("最大超额 ≤ 4 元", p.maxExcessAfter() <= 4.0,
                "最大超额=" + p.maxExcessAfter());
        report(cs, p);
    }

    /** 2 张 ¥50：总价略低于面额合计，怎么排都不可能都够，应尽量把缺口压到最小 */
    private static void twoCoupons() {
        System.out.println("\n--- 2 张 ¥50（总价不足） ---");
        List<Coupon> cs = coupons(50, 50);
        add(cs.get(0), "牛奶", 12.90, 2);
        add(cs.get(0), "面包", 8.50, 1);
        add(cs.get(1), "苹果", 5.50, 3);
        add(cs.get(1), "抽纸", 19.90, 1);

        Optimizer.Plan p = Optimizer.solve(cs, true);
        check("缺口不增加", p.deficitAfter() <= p.deficitBefore(),
                "现状缺口=" + p.deficitBefore() + " → " + p.deficitAfter());
        check("总金额守恒", sameTotal(cs, p), money(p));
        report(cs, p);

        // 总价 96 < 100：凑不满两张，但应该至少凑满一张（比两张各差一点更有用）
        System.out.println("\n--- 2 张 ¥50（总价 96，能凑满一张） ---");
        List<Coupon> cs2 = coupons(50, 50);
        add(cs2.get(0), "大米", 26.90, 2);
        add(cs2.get(0), "牛奶", 12.90, 1);
        add(cs2.get(1), "苹果", 5.50, 2);
        add(cs2.get(1), "抽纸", 19.90, 1);
        Optimizer.Plan p2 = Optimizer.solve(cs2, true);
        int covered = 0;
        for (int i = 0; i < p2.size(); i++) {
            if (p2.after(i) + 0.004 >= p2.target(i)) covered++;
        }
        check("至少凑满一张券", covered >= 1, "凑满 " + covered + " 张");
        check("总金额守恒", sameTotal(cs2, p2), money(p2));
        report(cs2, p2);
    }

    /** 关闭拆分时，同一条商品只能整条落在同一张券里 */
    private static void noSplitMode() {
        System.out.println("\n--- 关闭拆分 ---");
        List<Coupon> cs = coupons(100, 100);
        add(cs.get(0), "大米", 26.90, 2);
        add(cs.get(0), "洗衣液", 39.90, 1);
        add(cs.get(1), "牛奶", 12.90, 5);

        Optimizer.Plan p = Optimizer.solve(cs, false);
        boolean whole = true;
        for (Coupon c : cs) {
            for (Item it : c.items) {
                if (Math.abs(it.qty - Math.round(it.qty)) > 1e-6) whole = false;
            }
        }
        for (List<Item> list : p.items) {
            for (Item it : list) {
                if (Math.abs(it.qty - Math.round(it.qty)) > 1e-6) whole = false;
            }
        }
        check("不拆分时数量保持整数", whole, "");
        check("总金额守恒", sameTotal(cs, p), money(p));
        check("关闭拆分后 0 次拆分", p.splitRows == 0, "splitRows=" + p.splitRows);
        report(cs, p);
    }

    /** 单件商品就超过面额时不可能凑准，至少要保证不崩、不丢钱 */
    private static void cannotCover() {
        System.out.println("\n--- 无法凑够（单件过大） ---");
        List<Coupon> cs = coupons(100, 100);
        add(cs.get(0), "冰箱", 2999, 1);
        add(cs.get(1), "牛奶", 12.90, 1);

        Optimizer.Plan p = Optimizer.solve(cs, true);
        check("结果仍然可用", p.before(0) + p.before(1) > 0, money(p));
        check("总金额守恒", sameTotal(cs, p), money(p));
        report(cs, p);
    }

    /** 有没填面额的券时，多出来的商品可以被丢到那张券上 */
    private static void freeBin() {
        System.out.println("\n--- 含一张未填面额的券 ---");
        List<Coupon> cs = coupons(100, 100, 0);
        add(cs.get(0), "大米", 26.90, 2);
        add(cs.get(0), "洗衣液", 39.90, 1);
        add(cs.get(1), "牛奶", 12.90, 3);
        add(cs.get(1), "抽纸", 19.90, 1);
        add(cs.get(2), "食用油", 59.90, 1);

        Optimizer.Plan p = Optimizer.solve(cs, true);
        check("两张有面额的都够", p.after(0) >= 100 - 0.004 && p.after(1) >= 100 - 0.004,
                "券1=" + p.after(0) + " 券2=" + p.after(1));
        check("总金额守恒", sameTotal(cs, p), money(p));
        report(cs, p);
    }

    /** 大购物车：30 条商品、4 张券，看求解耗时 */
    private static void bigCart() {
        System.out.println("\n--- 30 条 / 4 张券 (性能) ---");
        List<Coupon> cs = coupons(80, 80, 80, 80);
        double[] prices = {3.50, 5.90, 8.80, 12.90, 19.90, 26.90, 6.50, 9.90, 15.80, 4.20};
        for (int i = 0; i < 30; i++) {
            add(cs.get(i % 4), "商品" + i, prices[i % prices.length], (i % 3) + 1);
        }
        long t0 = System.currentTimeMillis();
        Optimizer.Plan p = Optimizer.solve(cs, true);
        long cost = System.currentTimeMillis() - t0;
        check("耗时 < 400ms", cost < 400, cost + "ms");
        check("总金额守恒", sameTotal(cs, p), money(p));
        check("缺口不增加", p.deficitAfter() <= p.deficitBefore(),
                p.deficitBefore() + " → " + p.deficitAfter());
        System.out.println("      券合计 320，商品合计 " + total(cs));
    }

    /** 商品总额不够时：最后一张券不考核，前面几张要尽量都凑够 */
    private static void ignoreLast() {
        System.out.println("\n--- 商品 256.40 < 券 309：最后一张不考核 ---");
        List<Coupon> cs = coupons(103, 103, 103);
        add(cs.get(0), "洗衣液", 39.90, 1);
        add(cs.get(0), "抽纸", 15.90, 2);
        add(cs.get(0), "大米", 26.90, 1);
        add(cs.get(1), "牛奶", 12.90, 3);
        add(cs.get(1), "鸡蛋", 15.80, 1);
        add(cs.get(1), "面包", 8.50, 2);
        add(cs.get(1), "苹果", 5.50, 3);
        add(cs.get(2), "食用油", 59.90, 1);
        add(cs.get(2), "酱油", 9.90, 1);

        Optimizer.Plan strict = Optimizer.solve(cs, true, false);
        Optimizer.Plan relaxed = Optimizer.solve(cs, true, true);
        check("放宽后缺口更小或持平",
                relaxed.deficitAfter() <= strict.deficitAfter(),
                strict.deficitAfter() + " → " + relaxed.deficitAfter());
        check("前两张都凑够",
                relaxed.after(0) + 0.004 >= 103 && relaxed.after(1) + 0.004 >= 103,
                "券1=" + relaxed.after(0) + " 券2=" + relaxed.after(1));
        check("总金额守恒", sameTotal(cs, relaxed), money(relaxed));
        report(cs, relaxed);
    }

    /** 商品总额超过券面总额时：给出「放弃哪几件」的建议，放弃后每张券仍然够 */
    private static void giveUps() {
        System.out.println("\n--- 可放弃商品建议 ---");
        List<Coupon> cs = coupons(100, 100);
        add(cs.get(0), "大米", 49.90, 1);
        add(cs.get(0), "抽纸", 19.90, 2);
        add(cs.get(0), "面包", 8.50, 1);
        add(cs.get(0), "苹果", 5.50, 3);
        add(cs.get(1), "食用油", 59.90, 1);
        add(cs.get(1), "牛奶", 12.90, 3);
        add(cs.get(1), "挂面", 6.50, 2);
        add(cs.get(1), "酱油", 9.90, 1);

        Optimizer.Plan p = Optimizer.solve(cs, true);
        check("两张都凑够", p.allCovered(), "缺口=" + p.deficitAfter());
        check("有放弃建议", p.giveUpTotal() > 0, "可省 " + p.giveUpTotal());
        check("放弃后每张券仍然够面额", p.maxExcessAfterGiveUp() >= -0.004, "");
        boolean stillOk = true;
        for (int i = 0; i < p.size(); i++) {
            double left = p.after(i);
            for (Item it : p.giveUps.get(i)) left -= it.subtotal();
            if (left + 0.004 < p.target(i)) stillOk = false;
        }
        check("逐张核对放弃后金额", stillOk, "");
        report(cs, p);
        System.out.println("      可放弃合计 " + p.giveUpTotal()
                + "，放弃后最大超额 " + p.maxExcessAfterGiveUp());
        for (int i = 0; i < p.size(); i++) {
            StringBuilder all = new StringBuilder();
            for (Item it : p.items.get(i)) {
                all.append(it.name).append(" ").append(it.subtotal()).append(" | ");
            }
            System.out.println("      券" + (i + 1) + " 商品: " + all);
            for (Item it : p.giveUps.get(i)) {
                System.out.println("      券" + (i + 1) + " 放弃 " + it.name + " " + it.subtotal());
            }
        }
    }

    // ---------------------------------------------------------------- 工具
    private static List<Coupon> coupons(double... amounts) {
        List<Coupon> cs = new ArrayList<Coupon>();
        for (int i = 0; i < amounts.length; i++) {
            cs.add(new Coupon("券" + (i + 1), amounts[i]));
        }
        return cs;
    }

    private static void add(Coupon c, String name, double price, double qty) {
        c.items.add(new Item(name, price, qty));
    }

    private static double total(List<Coupon> cs) {
        double t = 0;
        for (Coupon c : cs) t += c.total();
        return t;
    }

    private static double planTotal(Optimizer.Plan p) {
        double t = 0;
        for (List<Item> list : p.items) {
            for (Item it : list) t += it.subtotal();
        }
        return t;
    }

    private static boolean sameTotal(List<Coupon> cs, Optimizer.Plan p) {
        return Math.abs(total(cs) - planTotal(p)) < 0.005;
    }

    private static String money(Optimizer.Plan p) {
        StringBuilder sb = new StringBuilder("合计 " + Math.round(planTotal(p) * 100) / 100.0 + "：");
        for (int i = 0; i < p.size(); i++) {
            sb.append(" 券").append(i + 1).append("=").append(p.after(i));
        }
        return sb.toString();
    }

    private static void report(List<Coupon> cs, Optimizer.Plan p) {
        System.out.println("      面额/现状/改后：");
        for (int i = 0; i < p.size(); i++) {
            System.out.println("      券" + (i + 1) + "  " + p.target(i)
                    + "  " + p.before(i) + " → " + p.after(i)
                    + "   (" + p.items.get(i).size() + " 条)");
        }
        System.out.println("      移动 " + p.movedRows + " 条，拆分 " + p.splitRows + " 条");
    }
}
