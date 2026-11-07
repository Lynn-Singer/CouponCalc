package com.smhesinger.couponcalc;

import java.text.DecimalFormat;

/** 金额 / 数量格式化 */
public final class Fmt {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DecimalFormat QTY = new DecimalFormat("0.##");

    private Fmt() {
    }

    /** ¥1,234.50 */
    public static String money(double v) {
        return "¥" + MONEY.format(v);
    }

    /** 12 / 0.5 —— 整数不显示小数位 */
    public static String qty(double v) {
        return QTY.format(v);
    }

    /** 解析用户输入，非法返回 NaN */
    public static double parse(String s) {
        if (s == null) return Double.NaN;
        s = s.trim().replace("，", "").replace(",", "");
        if (s.isEmpty() || s.equals(".")) return Double.NaN;
        try {
            double v = Double.parseDouble(s);
            if (Double.isNaN(v) || Double.isInfinite(v)) return Double.NaN;
            return v;
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
