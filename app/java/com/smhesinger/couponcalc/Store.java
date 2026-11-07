package com.smhesinger.couponcalc;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地存储：多张消费券 + 历史商品
 * 兼容 v1.0.0 的单清单数据（自动迁移成第一张券）
 */
public class Store {

    private static final String SP_NAME = "coupon_calc";
    private static final String K_STATE = "state_v2";
    private static final String K_OLD_COUPON = "coupon";
    private static final String K_OLD_ITEMS = "items";
    private static final int MAX_HISTORY = 80;

    public final List<Coupon> coupons = new ArrayList<Coupon>();
    public final List<History> history = new ArrayList<History>();
    public int current = 0;

    private final SharedPreferences sp;

    public Store(Context ctx) {
        sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        load();
    }

    // ------------------------------------------------------------ 当前券

    public Coupon cur() {
        if (coupons.isEmpty()) coupons.add(new Coupon("消费券 1", 0));
        if (current < 0 || current >= coupons.size()) current = 0;
        return coupons.get(current);
    }

    // ------------------------------------------------------------ 历史商品

    public List<String> historyNames() {
        List<String> out = new ArrayList<String>();
        for (History h : history) out.add(h.name);
        return out;
    }

    public double priceOf(String name) {
        if (name == null) return -1;
        for (History h : history) {
            if (h.name.equals(name.trim())) return h.price;
        }
        return -1;
    }

    /** 记住商品名与单价，最近使用的排最前 */
    public void remember(String name, double price) {
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty()) return;

        History found = null;
        for (History h : history) {
            if (h.name.equals(name)) {
                found = h;
                break;
            }
        }
        if (found == null) {
            history.add(0, new History(name, price, System.currentTimeMillis()));
        } else {
            found.price = price;
            found.ts = System.currentTimeMillis();
            history.remove(found);
            history.add(0, found);
        }
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
    }

    /**
     * 找一个单价接近差额的历史商品，用于凑单推荐。
     * 偏差超过差额 40%+1 元则不推荐（免得太离谱）。
     */
    public History closestTo(double amount) {
        if (amount <= 0 || history.isEmpty()) return null;
        History best = null;
        double bestScore = Double.MAX_VALUE;
        for (History h : history) {
            if (h.price <= 0) continue;
            double diff = h.price - amount;
            // 略高于差额的比略低的更实用（能一次性凑够）
            double score = Math.abs(diff) + (diff < 0 ? amount * 0.12 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = h;
            }
        }
        if (best == null) return null;
        if (Math.abs(best.price - amount) > amount * 0.4 + 1) return null;
        return best;
    }

    // ------------------------------------------------------------ 持久化

    private void load() {
        String raw = sp.getString(K_STATE, null);
        boolean ok = false;
        if (raw != null && !raw.isEmpty()) ok = parse(raw);
        if (!ok) migrateLegacy();
        if (coupons.isEmpty()) coupons.add(new Coupon("消费券 1", 0));
        if (current < 0 || current >= coupons.size()) current = 0;
    }

    private boolean parse(String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            current = root.optInt("current", 0);

            JSONArray carr = root.optJSONArray("coupons");
            if (carr != null) {
                for (int i = 0; i < carr.length(); i++) {
                    JSONObject co = carr.optJSONObject(i);
                    if (co == null) continue;
                    Coupon c = new Coupon(co.optString("name", "消费券 " + (i + 1)),
                            co.optDouble("amount", 0));
                    JSONArray iarr = co.optJSONArray("items");
                    if (iarr != null) {
                        for (int j = 0; j < iarr.length(); j++) {
                            JSONObject io = iarr.optJSONObject(j);
                            if (io == null) continue;
                            c.items.add(new Item(io.optString("n", "商品"),
                                    io.optDouble("p", 0), io.optDouble("q", 1)));
                        }
                    }
                    coupons.add(c);
                }
            }

            JSONArray harr = root.optJSONArray("history");
            if (harr != null) {
                for (int i = 0; i < harr.length(); i++) {
                    JSONObject ho = harr.optJSONObject(i);
                    if (ho == null) continue;
                    history.add(new History(ho.optString("n", ""),
                            ho.optDouble("p", 0), ho.optLong("t", 0)));
                }
            }
            return !coupons.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 把 v1.0.0 的单张券 + 清单迁过来 */
    private void migrateLegacy() {
        String amount = sp.getString(K_OLD_COUPON, "");
        String itemsRaw = sp.getString(K_OLD_ITEMS, "");
        boolean hasOld = (amount != null && !amount.isEmpty())
                || (itemsRaw != null && !itemsRaw.isEmpty());
        if (!hasOld) return;

        double amt = Fmt.parse(amount);
        Coupon c = new Coupon("消费券 1", Double.isNaN(amt) ? 0 : amt);
        try {
            JSONArray arr = new JSONArray(itemsRaw == null ? "[]" : itemsRaw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String n = o.optString("n", "商品");
                double p = o.optDouble("p", 0);
                c.items.add(new Item(n, p, o.optDouble("q", 1)));
                remember(n, p);
            }
        } catch (Exception ignored) {
        }
        coupons.add(c);
        current = 0;
        save();
    }

    public void save() {
        JSONObject root = new JSONObject();
        try {
            root.put("current", current);

            JSONArray carr = new JSONArray();
            for (Coupon c : coupons) {
                JSONObject co = new JSONObject();
                co.put("name", c.name);
                co.put("amount", c.amount);
                JSONArray iarr = new JSONArray();
                for (Item it : c.items) {
                    JSONObject io = new JSONObject();
                    io.put("n", it.name);
                    io.put("p", it.price);
                    io.put("q", it.qty);
                    iarr.put(io);
                }
                co.put("items", iarr);
                carr.put(co);
            }
            root.put("coupons", carr);

            JSONArray harr = new JSONArray();
            for (History h : history) {
                JSONObject ho = new JSONObject();
                ho.put("n", h.name);
                ho.put("p", h.price);
                ho.put("t", h.ts);
                harr.put(ho);
            }
            root.put("history", harr);
        } catch (Exception ignored) {
        }
        sp.edit().putString(K_STATE, root.toString()).apply();
    }
}
