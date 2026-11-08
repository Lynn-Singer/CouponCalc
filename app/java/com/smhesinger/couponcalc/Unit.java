package com.smhesinger.couponcalc;

/**
 * 计量单位与折算。
 *
 * 比价的前提是「同一个维度才能比」：500g 大米和 1.5L 牛奶都折算成单价后
 * 一个是元/斤、一个是元/L，两个数字不可比。所以先按维度分组，组内才排名。
 */
public class Unit {

    public static final int DIM_MASS = 0;   // 重量
    public static final int DIM_VOL = 1;    // 容量
    public static final int DIM_COUNT = 2;  // 个数

    public static final String[] CODES = {"g", "kg", "斤", "两", "ml", "L",
            "个", "片", "包", "抽", "卷"};

    private static final int[] DIMS = {
            DIM_MASS, DIM_MASS, DIM_MASS, DIM_MASS,
            DIM_VOL, DIM_VOL,
            DIM_COUNT, DIM_COUNT, DIM_COUNT, DIM_COUNT, DIM_COUNT};

    /** 1 个该单位 = 多少基准单位（重量基准 g，容量基准 ml，个数基准 个） */
    private static final double[] TO_BASE = {
            1, 1000, 500, 50,
            1, 1000,
            1, 1, 1, 1, 1};

    public static int indexOf(String code) {
        if (code == null) return -1;
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) return i;
        }
        return -1;
    }

    public static int dimOf(String code) {
        int i = indexOf(code);
        return i < 0 ? DIM_COUNT : DIMS[i];
    }

    public static double factorOf(String code) {
        int i = indexOf(code);
        return i < 0 ? 1 : TO_BASE[i];
    }

    public static String dimName(int dim) {
        switch (dim) {
            case DIM_MASS:
                return "重量";
            case DIM_VOL:
                return "容量";
            default:
                return "个数";
        }
    }

    /** 展示基准单位，如「斤」「L」「个」 */
    public static String baseUnit(int dim) {
        switch (dim) {
            case DIM_MASS:
                return "斤";
            case DIM_VOL:
                return "L";
            default:
                return "个";
        }
    }

    /** 单价标签，如「元/斤」 */
    public static String priceLabel(int dim) {
        return "元/" + baseUnit(dim);
    }

    /** 1 个展示基准 = 多少基准单位：斤 500g、L 1000ml、个 1 */
    public static double baseSize(int dim) {
        switch (dim) {
            case DIM_MASS:
                return 500;
            case DIM_VOL:
                return 1000;
            default:
                return 1;
        }
    }

    /** 该维度常用单位（用于分组说明） */
    public static String hintOf(int dim) {
        switch (dim) {
            case DIM_MASS:
                return "统一折算成每斤（500g）单价";
            case DIM_VOL:
                return "统一折算成每升（1000ml）单价";
            default:
                return "按单个计价（抽纸选「抽」更准）";
        }
    }
}
