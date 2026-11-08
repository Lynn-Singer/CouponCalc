package com.smhesinger.couponcalc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 比价页：不同规格的商品，折算到统一单价后比出哪个更划算。
 *
 * 关键约束：只有同一计量维度（重量 / 容量 / 个数）才能互相比，
 * 所以先分组、组内按单价（或按需总支出）排名，组间不排名。
 */
public class CompareActivity extends Activity {

    public static final String EXTRA_ADD_NAME = "add_name";
    public static final String EXTRA_ADD_PRICE = "add_price";

    private static final String SP_NAME = "coupon_calc";
    private static final String K_COMPARE = "compare_v1";
    /** 低于这个百分比差异，认为「基本持平」，不值得为它换规格 */
    private static final double TIE_PCT = 3.0;

    private final List<Spec> specs = new ArrayList<Spec>();
    /** 每个维度的目标量：0 表示不按需计算 */
    private final double[] targets = new double[3];

    private String curUnit = "kg";

    private EditText etName, etAmount, etPrice;
    private TextView btnUnit, tvEmpty;
    private SpecAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare);

        etName = (EditText) findViewById(R.id.etCName);
        etAmount = (EditText) findViewById(R.id.etCAmount);
        etPrice = (EditText) findViewById(R.id.etCPrice);
        btnUnit = (TextView) findViewById(R.id.btnCUnit);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);

        ListView listView = (ListView) findViewById(R.id.listSpecs);
        adapter = new SpecAdapter(this, new SpecAdapter.Listener() {
            @Override
            public void onTarget(int dim) {
                askTarget(dim);
            }

            @Override
            public void onItem(SpecAdapter.Row row) {
                showItemMenu(row.spec);
            }
        });
        listView.setAdapter(adapter);
        listView.setEmptyView(tvEmpty);

        load();

        btnUnit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickUnit();
            }
        });
        findViewById(R.id.btnAddSpec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSpec();
            }
        });
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnClearList).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });

        refreshUnitButton();
        rebuild();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    // ------------------------------------------------------------ 录入

    private void refreshUnitButton() {
        btnUnit.setText(curUnit + " ▾");
    }

    private void pickUnit() {
        new AlertDialog.Builder(this)
                .setTitle("规格单位")
                .setItems(Unit.CODES, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        curUnit = Unit.CODES[which];
                        refreshUnitButton();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addSpec() {
        double amount = Fmt.parse(etAmount.getText().toString());
        double price = Fmt.parse(etPrice.getText().toString());

        if (Double.isNaN(amount) || amount <= 0) {
            toast("请填写规格数值，例如 500 或 1.5");
            etAmount.requestFocus();
            return;
        }
        if (Double.isNaN(price) || price <= 0) {
            toast("请填写售价");
            etPrice.requestFocus();
            return;
        }

        specs.add(new Spec(etName.getText().toString().trim(), amount, curUnit, price));
        save();
        rebuild();

        // 保留名称，清掉数值 —— 比价大多是同一商品的不同规格，接着录更顺手
        etAmount.setText("");
        etPrice.setText("");
        etAmount.requestFocus();
    }

    private void confirmClear() {
        if (specs.isEmpty()) {
            toast("列表已经是空的");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空全部比价项？")
                .setMessage("当前 " + specs.size() + " 项，清空后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        specs.clear();
                        targets[0] = targets[1] = targets[2] = 0;
                        save();
                        rebuild();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------ 排名

    private void rebuild() {
        List<SpecAdapter.Row> rows = new ArrayList<SpecAdapter.Row>();

        for (int dim = 0; dim < 3; dim++) {
            final List<Spec> group = new ArrayList<Spec>();
            for (Spec s : specs) {
                if (s.dim() == dim) group.add(s);
            }
            if (group.isEmpty()) continue;

            final double target = targets[dim];
            final boolean byCost = target > 0;

            Collections.sort(group, new Comparator<Spec>() {
                @Override
                public int compare(Spec a, Spec b) {
                    double va = byCost ? a.costFor(target) : a.unitPrice();
                    double vb = byCost ? b.costFor(target) : b.unitPrice();
                    return Double.compare(va, vb);
                }
            });

            double bestVal = byCost ? group.get(0).costFor(target) : group.get(0).unitPrice();

            SpecAdapter.Row head = new SpecAdapter.Row();
            head.type = SpecAdapter.TYPE_GROUP;
            head.dim = dim;
            head.groupTitle = Unit.dimName(dim) + " · " + group.size() + " 项";
            head.groupHint = Unit.hintOf(dim);
            head.targetText = byCost
                    ? "需 " + Fmt.qty(target) + Unit.baseUnit(dim)
                    : "按需购买";
            head.showCost = byCost;
            rows.add(head);

            for (Spec s : group) {
                SpecAdapter.Row r = new SpecAdapter.Row();
                r.dim = dim;
                r.spec = s;
                r.unitPrice = s.unitPrice();
                r.showCost = byCost;
                if (byCost) {
                    r.packs = s.packsFor(target);
                    r.cost = s.costFor(target);
                }
                double v = byCost ? r.cost : r.unitPrice;
                r.best = Math.abs(v - bestVal) < 1e-9;
                if (bestVal > 0 && v > bestVal) {
                    r.diffPct = (v - bestVal) / bestVal * 100.0;
                    r.tie = r.diffPct < TIE_PCT;
                }
                rows.add(r);
            }
        }

        adapter.setRows(rows);
        boolean empty = specs.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------ 按需购买

    private void askTarget(final int dim) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint("例如 5，留空表示不按需");
        et.setSingleLine(true);
        if (targets[dim] > 0) {
            et.setText(Fmt.qty(targets[dim]));
            et.setSelection(et.getText().length());
        }

        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(22), dp(8), dp(22), dp(0));
        box.addView(et);

        new AlertDialog.Builder(this)
                .setTitle("按需购买 · " + Unit.dimName(dim))
                .setMessage("你大概要买多少" + Unit.baseUnit(dim) + "？\n"
                        + "填了之后改按「实际总支出」排名 —— 大包装单价低但用不完时，"
                        + "小包装可能才是真省钱。")
                .setView(box)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double v = Fmt.parse(et.getText().toString());
                        targets[dim] = (Double.isNaN(v) || v <= 0) ? 0 : v;
                        save();
                        rebuild();
                    }
                })
                .setNeutralButton("不按需", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        targets[dim] = 0;
                        save();
                        rebuild();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ------------------------------------------------------------ 单项操作

    private void showItemMenu(final Spec spec) {
        final String[] actions = {"加入凑单购物车", "删除这一项"};
        new AlertDialog.Builder(this)
                .setTitle(spec.title() + "   " + Unit.priceLabel(spec.dim())
                        + Fmt.two(spec.unitPrice()))
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            sendToCart(spec);
                        } else {
                            specs.remove(spec);
                            save();
                            rebuild();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void sendToCart(Spec spec) {
        Intent data = new Intent();
        data.putExtra(EXTRA_ADD_NAME, spec.title());
        data.putExtra(EXTRA_ADD_PRICE, spec.price);
        setResult(RESULT_OK, data);
        toast("已加入凑单清单");
        finish();
    }

    // ------------------------------------------------------------ 持久化

    private void save() {
        JSONObject root = new JSONObject();
        try {
            JSONArray arr = new JSONArray();
            for (Spec s : specs) {
                JSONObject o = new JSONObject();
                o.put("n", s.name);
                o.put("a", s.amount);
                o.put("u", s.unit);
                o.put("p", s.price);
                arr.put(o);
            }
            root.put("specs", arr);
            JSONArray t = new JSONArray();
            for (int i = 0; i < 3; i++) t.put(targets[i]);
            root.put("targets", t);
        } catch (Exception ignored) {
        }
        getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putString(K_COMPARE, root.toString()).apply();
    }

    private void load() {
        String raw = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getString(K_COMPARE, null);
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray arr = root.optJSONArray("specs");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String u = o.optString("u", "kg");
                    if (Unit.indexOf(u) < 0) u = "kg";
                    specs.add(new Spec(o.optString("n", ""),
                            o.optDouble("a", 0), u, o.optDouble("p", 0)));
                }
            }
            JSONArray t = root.optJSONArray("targets");
            if (t != null) {
                for (int i = 0; i < 3 && i < t.length(); i++) targets[i] = t.optDouble(i, 0);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------ 工具

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
