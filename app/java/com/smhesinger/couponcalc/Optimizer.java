package com.smhesinger.couponcalc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 多券商品重新分配求解器（v1.4.0）
 *
 * <p>用途：逐张核销模式下，把购物车里已有的商品重新分给各张券，
 * 让每张券的金额都 ≥ 自己的面额，同时「超额」尽量小（少自付）。</p>
 *
 * <p>目标按字典序比较：</p>
 * <ol>
 *   <li>缺口合计最小 —— 为 0 表示每张券都凑够</li>
 *   <li>单张券的最大超额最小 —— 别在某一张上凑过头太多</li>
 *   <li>超额平方和最小 —— 把多出来的钱摊匀，顺便让各券都不难看</li>
 * </ol>
 *
 * <p>做法：数量为整数的商品按件拆成「片」（3 瓶牛奶 = 3 片，可分到不同券），
 * 随机贪心构造初始解，再做局部搜索（单片移动 + 两片交换），多次重启取最优，
 * 带时间预算，保证调用方秒回。</p>
 */
public final class Optimizer {

    /** 单条商品最多拆成几片（数量 ≤ 8 时按件拆，超过就整条不拆） */
    private static final int MAX_PIECES = 8;
    /** 求解时间预算（毫秒）：超时立即返回当前最优解 */
    private static final long TIME_BUDGET_MS = 160;
    /** 随机重启次数上限 */
    private static final int RESTARTS = 60;
    /** 局部搜索最多迭代轮数 */
    private static final int MAX_PASS = 24;
    /** 「可以放弃哪些商品」的背包容量上限（分）：超额超过 ¥500 时不追求精确 */
    private static final int MAX_GIVEUP_CENTS = 50000;
    /** 只有把最大超额压低这么多（分）才值得搬动商品，否则宁可不折腾 */
    private static final long WORTH_MOVING_CENTS = 200;

    /** 指标比较顺序（钱优先）：缺口 → 凑够张数 → 最大超额 → 均衡度 → 挪动片数 */
    private static final int[] ORDER_MONEY = {0, 1, 3, 4, 2};
    /** 指标比较顺序（少搬优先）：缺口 → 凑够张数 → 挪动片数 → 最大超额 → 均衡度 */
    private static final int[] ORDER_CHURN = {0, 1, 2, 3, 4};

    private Optimizer() {
    }

    // ============================================================ 对外结果

    /** 一次求解的完整结果：每张券的新清单 + 前后对比指标 */
    public static final class Plan {

        /** 每张券分配后的商品（顺序尽量与原录入顺序一致） */
        public final List<List<Item>> items = new ArrayList<List<Item>>();

        /** 建议放弃的商品（每张券一份）：放弃后该券仍然够面额，能少自付 */
        public final List<List<Item>> giveUps = new ArrayList<List<Item>>();

        /** 是否允许拆分同一条商品的数量 */
        public boolean allowSplit;
        /** 最后一张券是否不参与考核（商品总额不够时用） */
        public boolean ignoreLast;

        /** 有商品换过券的条数 */
        public int movedRows;
        /** 数量被拆到多张券的条数 */
        public int splitRows;

        private final long[] target;        // 各券面额（分）
        private final long[] before;        // 现各券合计（分）
        private final long[] after;         // 方案各券合计（分）
        private final long[] metricBefore = new long[5];
        private final long[] metricAfter = new long[5];

        Plan(int k) {
            target = new long[k];
            before = new long[k];
            after = new long[k];
            for (int i = 0; i < k; i++) {
                items.add(new ArrayList<Item>());
                giveUps.add(new ArrayList<Item>());
            }
        }

        public int size() {
            return items.size();
        }

        public double target(int i) {
            return target[i] / 100.0;
        }

        public double before(int i) {
            return before[i] / 100.0;
        }

        public double after(int i) {
            return after[i] / 100.0;
        }

        /** 缺口合计（元） */
        public double deficitBefore() {
            return metricBefore[0] / 100.0;
        }

        public double deficitAfter() {
            return metricAfter[0] / 100.0;
        }

        /** 单张最大超额（元） */
        public double maxExcessBefore() {
            return metricBefore[3] / 100.0;
        }

        public double maxExcessAfter() {
            return metricAfter[3] / 100.0;
        }

        /**
         * 方案是否真的比现状好（相等返回 false）。
         * 只比钱：缺口 → 凑够的券数 → 最大超额 → 均衡度；挪动条数不参与，
         * 否则「为了把超额摊匀而搬空整个清单」也会被判成更好。
         */
        public boolean better() {
            if (metricAfter[0] != metricBefore[0]) return metricAfter[0] < metricBefore[0];
            if (metricAfter[1] != metricBefore[1]) return metricAfter[1] < metricBefore[1];
            if (metricAfter[3] != metricBefore[3]) return metricAfter[3] < metricBefore[3];
            return metricAfter[4] < metricBefore[4];
        }

        /** 方案是否让每张券都凑够 */
        public boolean allCovered() {
            return metricAfter[0] == 0;
        }

        /** 放弃建议合计能省多少（元） */
        public double giveUpTotal() {
            double t = 0;
            for (List<Item> ups : giveUps) {
                for (Item it : ups) t += it.subtotal();
            }
            return t;
        }

        /** 放弃这些商品后，单张券的最大超额（元） */
        public double maxExcessAfterGiveUp() {
            double worst = 0;
            for (int i = 0; i < after.length; i++) {
                if (target[i] <= 0) continue;
                double e = (after[i] - target[i]) / 100.0;
                for (Item it : giveUps.get(i)) e -= it.subtotal();
                if (e > worst) worst = e;
            }
            return worst;
        }
    }

    // ============================================================ 内部结构

    /** 一片商品：一条商品可能被拆成多片，分到不同券 */
    private static final class Piece {
        final Item src;
        final int origin;   // 原来属于哪张券
        final int row;      // 来源商品序号（用来保持清单顺序、统计拆分条数）
        final double qty;
        final long cents;

        Piece(Item src, int origin, int row, double qty, long cents) {
            this.src = src;
            this.origin = origin;
            this.row = row;
            this.qty = qty;
            this.cents = cents;
        }
    }

    // ============================================================ 入口

    /**
     * 求一个更贴合面额的分配方案（每张券都要考核）。
     *
     * @param coupons    全部消费券（含面额为 0 的券，它只当「兜底筐」用）
     * @param allowSplit 是否允许把同一条商品的数量拆到不同券
     */
    public static Plan solve(List<Coupon> coupons, boolean allowSplit) {
        return solve(coupons, allowSplit, false);
    }

    /**
     * @param ignoreLast true 时最后一张券不考核（商品总额不够时，让它先当兜底筐）
     */
    public static Plan solve(List<Coupon> coupons, boolean allowSplit, boolean ignoreLast) {
        // 算两遍：「尽量贴近面额」和「尽量少搬动」，再挑一个值得的
        Plan tight = solve(coupons, allowSplit, ignoreLast, ORDER_MONEY);
        Plan calm = solve(coupons, allowSplit, ignoreLast, ORDER_CHURN);

        if (tight.deficitAfter() + 0.004 < calm.deficitAfter()) return tight;
        // 挪动一堆商品只为了把超额压低不到 2 块钱，不划算，留着现在的顺序
        if (tight.maxExcessAfter() <= calm.maxExcessAfter() - WORTH_MOVING_CENTS / 100.0) {
            return tight;
        }
        return calm;
    }

    /** @param order 指标比较顺序，见 ORDER_MONEY / ORDER_CHURN */
    public static Plan solve(List<Coupon> coupons, boolean allowSplit, boolean ignoreLast, int[] order) {
        final int k = coupons.size();
        final Plan plan = new Plan(k);
        plan.allowSplit = allowSplit;
        plan.ignoreLast = ignoreLast;

        List<Piece> pieces = new ArrayList<Piece>();
        int row = 0;
        for (int ci = 0; ci < k; ci++) {
            Coupon c = coupons.get(ci);
            plan.target[ci] = Math.round(c.amount * 100);
            long sum = 0;
            for (Item it : c.items) {
                sum += Math.round(it.subtotal() * 100);
                addPieces(pieces, it, ci, row, allowSplit);
                row++;
            }
            plan.before[ci] = sum;
        }
        // 最后一张券先不考核：当成没有面额的兜底筐
        if (ignoreLast && k > 0) plan.target[k - 1] = 0;

        metrics(plan.before, plan.target, k, plan.metricBefore, 0);

        final int n = pieces.size();
        if (n == 0) {
            return plan;
        }

        int[] best = new int[n];
        int[] cur = new int[n];
        for (int i = 0; i < n; i++) {
            best[i] = pieces.get(i).origin;
            cur[i] = best[i];
        }
        long[] bestScore = new long[5];
        metrics(plan.before, plan.target, k, bestScore, 0);

        Random rnd = new Random(20261108L);
        int[] idxOrder = new int[n];
        long deadline = System.currentTimeMillis() + TIME_BUDGET_MS;

        for (int r = 0; r < RESTARTS; r++) {
            if (r == 0) {
                // 第一轮从「现状」出发做局部搜索：保证结果不劣于现状
                System.arraycopy(best, 0, cur, 0, n);
            } else {
                construct(pieces, plan.target, k, cur, idxOrder, rnd, order);
            }
            long[] sc = localSearch(pieces, plan.target, k, cur, order);
            if (betterThan(sc, bestScore, order)) {
                System.arraycopy(sc, 0, bestScore, 0, 5);
                System.arraycopy(cur, 0, best, 0, n);
            }
            if (System.currentTimeMillis() > deadline) break;
        }

        build(plan, pieces, best, k);
        metrics(plan.after, plan.target, k, plan.metricAfter, 0);
        if (plan.metricAfter[0] == 0) pickGiveUps(plan, k);
        return plan;
    }

    // ============================================================ 切片

    /** 把一条商品切成若干片；整数数量按件切，其余整条不可分 */
    private static void addPieces(List<Piece> out, Item it, int bin, int row, boolean allowSplit) {
        long total = Math.round(it.subtotal() * 100);
        int n = 1;
        if (allowSplit && it.qty > 1) {
            int qi = (int) Math.round(it.qty);
            if (qi > 1 && qi <= MAX_PIECES && Math.abs(it.qty - qi) < 1e-6) n = qi;
        }
        if (n == 1) {
            out.add(new Piece(it, bin, row, it.qty, total));
            return;
        }
        // 用累计取整，保证各片合计与整条金额完全一致
        long accCents = 0;
        double accQty = 0;
        for (int i = 1; i <= n; i++) {
            double cumQty = it.qty * i / n;
            long cumCents = Math.round(it.price * cumQty * 100);
            out.add(new Piece(it, bin, row, cumQty - accQty, cumCents - accCents));
            accCents = cumCents;
            accQty = cumQty;
        }
    }

    // ============================================================ 指标

    /**
     * 五个指标，下标固定：
     * 0 缺口合计 / 1 已凑够券数（取负，越负越好）/ 2 挪动片数 / 3 最大超额 / 4 超额平方和
     */
    private static void metrics(long[] totals, long[] target, int k, long[] out, int moves) {
        long deficit = 0;
        long covered = 0;
        long maxExcess = 0;
        long sumSq = 0;
        for (int i = 0; i < k; i++) {
            if (target[i] <= 0) continue;               // 没填面额的券不参与考核
            long diff = totals[i] - target[i];
            if (diff < 0) {
                deficit += -diff;
            } else {
                covered++;
                if (diff > maxExcess) maxExcess = diff;
                sumSq += diff * diff;
            }
        }
        out[0] = deficit;
        out[1] = -covered;                              // 越负说明凑够的券越多
        out[2] = moves;
        out[3] = maxExcess;
        out[4] = sumSq;
    }

    /** 按给定的指标顺序做字典序比较（越小的越好） */
    private static boolean betterThan(long[] a, long[] b, int[] order) {
        for (int idx : order) {
            if (a[idx] != b[idx]) return a[idx] < b[idx];
        }
        return false;
    }

    // ============================================================ 构造 + 搜索

    /** 随机贪心：乱序逐片放入「放完代价最小」的券（平分时留在原券，少挪动） */
    private static void construct(List<Piece> pieces, long[] target, int k,
                                  int[] assign, int[] idxOrder, Random rnd, int[] order) {
        int n = pieces.size();
        for (int i = 0; i < n; i++) idxOrder[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = idxOrder[i];
            idxOrder[i] = idxOrder[j];
            idxOrder[j] = t;
        }

        long[] bin = new long[k];
        long[] tmp = new long[5];
        for (int oi = 0; oi < n; oi++) {
            int idx = idxOrder[oi];
            Piece p = pieces.get(idx);
            long c = p.cents;
            long[] best = null;
            int bestBin = p.origin;
            for (int b = 0; b < k; b++) {
                bin[b] += c;
                metrics(bin, target, k, tmp, 0);
                bin[b] -= c;
                if (best == null || betterThan(tmp, best, order)) {
                    best = tmp.clone();
                    bestBin = b;
                }
            }
            assign[idx] = bestBin;
            bin[bestBin] += c;
        }
    }

    /** 局部搜索：反复尝试「移动一片」「交换两片」，只接受严格变好的改动 */
    private static long[] localSearch(List<Piece> pieces, long[] target, int k, int[] assign, int[] order) {
        final int n = pieces.size();
        long[] bin = new long[k];
        int moves = 0;
        for (int i = 0; i < n; i++) {
            bin[assign[i]] += pieces.get(i).cents;
            if (assign[i] != pieces.get(i).origin) moves++;
        }

        long[] sc = new long[5];
        long[] tmp = new long[5];
        metrics(bin, target, k, sc, moves);

        for (int pass = 0; pass < MAX_PASS; pass++) {
            boolean changed = false;

            // 1) 单片移动
            for (int i = 0; i < n; i++) {
                final Piece p = pieces.get(i);
                long c = p.cents;
                if (c <= 0) continue;
                int from = assign[i];
                for (int j = 0; j < k; j++) {
                    if (j == from) continue;
                    long a = bin[from];
                    long b = bin[j];
                    bin[from] = a - c;
                    bin[j] = b + c;
                    int nm = moves - (from == p.origin ? 0 : 1) + (j == p.origin ? 0 : 1);
                    metrics(bin, target, k, tmp, nm);
                    if (betterThan(tmp, sc, order)) {
                        System.arraycopy(tmp, 0, sc, 0, 5);
                        moves = nm;
                        assign[i] = j;
                        from = j;
                        changed = true;
                    } else {
                        bin[from] = a;
                        bin[j] = b;
                    }
                }
            }

            // 2) 两片交换（能解决「谁也搬不动」的僵持）
            for (int i = 0; i < n; i++) {
                final Piece pi = pieces.get(i);
                long ci = pi.cents;
                for (int j = i + 1; j < n; j++) {
                    int bi = assign[i];             // 交换过一次就会变，每轮重取
                    int bj = assign[j];
                    if (bi == bj) continue;
                    final Piece pj = pieces.get(j);
                    long cj = pj.cents;
                    if (ci == cj) continue;
                    long a = bin[bi];
                    long b = bin[bj];
                    bin[bi] = a - ci + cj;
                    bin[bj] = b - cj + ci;
                    int nm = moves
                            - (bi == pi.origin ? 0 : 1) - (bj == pj.origin ? 0 : 1)
                            + (bj == pi.origin ? 0 : 1) + (bi == pj.origin ? 0 : 1);
                    metrics(bin, target, k, tmp, nm);
                    if (betterThan(tmp, sc, order)) {
                        System.arraycopy(tmp, 0, sc, 0, 5);
                        moves = nm;
                        assign[i] = bj;
                        assign[j] = bi;
                        changed = true;
                    } else {
                        bin[bi] = a;
                        bin[bj] = b;
                    }
                }
            }

            if (!changed) break;
        }
        return sc;
    }

    // ============================================================ 组装结果

    /** 把最终归属还原成「每张券一份清单」 */
    private static void build(Plan plan, List<Piece> pieces, int[] assign, int k) {
        final int n = pieces.size();

        List<List<Integer>> bins = new ArrayList<List<Integer>>();
        for (int b = 0; b < k; b++) bins.add(new ArrayList<Integer>());
        for (int i = 0; i < n; i++) bins.get(assign[i]).add(i);

        // 统计：换了券的条数、被拆开的条数
        int[] rowBins = new int[n];
        int rows = 0;
        for (int i = 0; i < n; i++) {
            int r = pieces.get(i).row;
            if (r + 1 > rows) rows = r + 1;
        }
        int[] firstBin = new int[rows];
        for (int r = 0; r < rows; r++) firstBin[r] = -1;
        boolean[] moved = new boolean[rows];
        boolean[] split = new boolean[rows];
        for (int i = 0; i < n; i++) {
            Piece p = pieces.get(i);
            if (firstBin[p.row] < 0) firstBin[p.row] = assign[i];
            else if (firstBin[p.row] != assign[i]) split[p.row] = true;
            if (assign[i] != p.origin) moved[p.row] = true;
        }
        for (int r = 0; r < rows; r++) {
            if (moved[r]) plan.movedRows++;
            if (split[r]) plan.splitRows++;
        }

        for (int b = 0; b < k; b++) {
            final List<Integer> idx = bins.get(b);
            // 保持原录入顺序，读起来不会乱
            Collections.sort(idx, new Comparator<Integer>() {
                @Override
                public int compare(Integer x, Integer y) {
                    return pieces.get(x).row - pieces.get(y).row;
                }
            });

            List<Item> out = plan.items.get(b);
            long cents = 0;
            int i = 0;
            while (i < idx.size()) {
                Piece head = pieces.get(idx.get(i));
                double qty = head.qty;
                int j = i + 1;
                // 同一条商品的相邻片合并成一行
                while (j < idx.size() && pieces.get(idx.get(j)).row == head.row) {
                    qty += pieces.get(idx.get(j)).qty;
                    j++;
                }
                Item made = (Math.abs(qty - head.src.qty) < 1e-6)
                        ? head.src
                        : new Item(head.src.name, head.src.price, qty);
                out.add(made);
                cents += Math.round(made.subtotal() * 100);
                i = j;
            }
            plan.after[b] = cents;
        }
    }

    // ============================================================ 可以放弃哪些商品

    /**
     * 每张券的超额就是要「白付」的钱。在券内挑一组商品放弃，
     * 合计不超过该券超额且尽量贴近它 —— 放弃后这张券仍然够面额，自付就能少一点。
     */
    private static void pickGiveUps(Plan plan, int k) {
        for (int b = 0; b < k; b++) {
            long budget = plan.after[b] - plan.target[b];
            if (plan.target[b] <= 0 || budget <= 0) continue;
            plan.giveUps.set(b, bestSubset(plan.items.get(b), budget));
        }
    }

    /** 0/1 背包：在 items 里挑一组，合计不超过 budget 且尽量大（同额度取件数少的） */
    private static List<Item> bestSubset(List<Item> items, long budget) {
        List<Item> out = new ArrayList<Item>();
        final int n = items.size();
        if (n == 0) return out;
        final int cap = (int) Math.min(budget, MAX_GIVEUP_CENTS);
        if (cap <= 0) return out;

        long[] cents = new long[n];
        for (int i = 0; i < n; i++) cents[i] = Math.round(items.get(i).subtotal() * 100);

        boolean[] reach = new boolean[cap + 1];
        int[] cnt = new int[cap + 1];
        int[] last = new int[cap + 1];
        int[] prev = new int[cap + 1];
        reach[0] = true;

        for (int i = 0; i < n; i++) {
            int c = (int) cents[i];
            if (c <= 0 || c > cap) continue;
            for (int s = cap; s >= c; s--) {
                if (!reach[s - c]) continue;
                if (!reach[s] || cnt[s - c] + 1 < cnt[s]) {
                    reach[s] = true;
                    cnt[s] = cnt[s - c] + 1;
                    last[s] = i;
                    prev[s] = s - c;
                }
            }
        }

        int best = 0;
        for (int s = cap; s > 0; s--) {
            if (reach[s]) {
                best = s;
                break;
            }
        }
        while (best > 0) {
            out.add(0, items.get(last[best]));
            best = prev[best];
        }
        return out;
    }
}
