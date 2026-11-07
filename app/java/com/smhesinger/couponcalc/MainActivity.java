package com.smhesinger.couponcalc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 消费券计算器
 * v1.1：多张券各自一张清单、商品名联想+历史单价、点行编辑、凑单建议
 */
public class MainActivity extends Activity {

    private Store store;

    private EditText etCoupon, etPrice, etQty;
    private AutoCompleteTextView etName;
    private TextView tvTotal, tvRemain, tvRemainLabel, tvProgress, tvSuggest, tvCount, tvEmpty, btnClear, btnSwitch;
    private ProgressBar pb;
    private ListView listView;
    private ItemAdapter adapter;

    private NameAdapter nameAdapter;

    private int cPrimary, cSuccess, cDanger, cSub;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = new Store(this);
        cPrimary = getResources().getColor(R.color.primary);
        cSuccess = getResources().getColor(R.color.success);
        cDanger = getResources().getColor(R.color.danger);
        cSub = getResources().getColor(R.color.textSecondary);

        etCoupon = (EditText) findViewById(R.id.etCoupon);
        etName = (AutoCompleteTextView) findViewById(R.id.etName);
        etPrice = (EditText) findViewById(R.id.etPrice);
        etQty = (EditText) findViewById(R.id.etQty);
        tvTotal = (TextView) findViewById(R.id.tvTotal);
        tvRemain = (TextView) findViewById(R.id.tvRemain);
        tvRemainLabel = (TextView) findViewById(R.id.tvRemainLabel);
        tvProgress = (TextView) findViewById(R.id.tvProgress);
        tvSuggest = (TextView) findViewById(R.id.tvSuggest);
        tvCount = (TextView) findViewById(R.id.tvCount);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);
        btnClear = (TextView) findViewById(R.id.btnClear);
        btnSwitch = (TextView) findViewById(R.id.btnSwitchCoupon);
        pb = (ProgressBar) findViewById(R.id.pb);
        listView = (ListView) findViewById(R.id.listView);

        adapter = new ItemAdapter(this, new ItemAdapter.Listener() {
            @Override
            public void onEdit(int position) {
                showEditDialog(position);
            }

            @Override
            public void onDelete(int position) {
                deleteItem(position);
            }
        });
        listView.setAdapter(adapter);
        listView.setEmptyView(tvEmpty);

        // 商品名联想
        nameAdapter = new NameAdapter(this);
        etName.setAdapter(nameAdapter);
        etName.setThreshold(1);
        etName.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String name = String.valueOf(parent.getItemAtPosition(position));
                double p = store.priceOf(name);
                if (p > 0) etPrice.setText(Fmt.qty(p));
                etQty.requestFocus();
                etQty.setSelection(etQty.getText().length());
            }
        });

        findViewById(R.id.btnAdd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addItem();
            }
        });
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });
        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCouponPicker();
            }
        });

        etCoupon.addTextChangedListener(new Watcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (loading) return;
                double v = Fmt.parse(s.toString());
                store.cur().amount = (Double.isNaN(v) || v < 0) ? 0 : v;
                refresh();
                store.save();
            }
        });

        etQty.setText("1");
        refreshHistorySuggestions();
        bindCoupon();
    }

    @Override
    protected void onPause() {
        super.onPause();
        store.save();
    }

    // ------------------------------------------------------------ 券

    /** 把当前券绑定到界面（切换券 / 初始化时调用） */
    private void bindCoupon() {
        Coupon c = store.cur();
        loading = true;
        etCoupon.setText(c.amount > 0 ? Fmt.qty(c.amount) : "");
        etCoupon.setSelection(etCoupon.getText().length());
        loading = false;

        btnSwitch.setText(c.name + " ▾");
        adapter.setItems(c.items);
        refresh();
    }

    private void showCouponPicker() {
        final String[] labels = new String[store.coupons.size()];
        for (int i = 0; i < store.coupons.size(); i++) {
            Coupon c = store.coupons.get(i);
            String amt = c.amount > 0 ? Fmt.money(c.amount) : "未填面额";
            labels[i] = c.name + "\n面额 " + amt + "  ·  已购 " + Fmt.money(c.total());
        }

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("切换消费券（长按可重命名）")
                .setSingleChoiceItems(labels, store.current, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        store.current = which;
                        store.save();
                        bindCoupon();
                        d.dismiss();
                    }
                })
                .setPositiveButton("新建", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        showNewCouponDialog();
                    }
                })
                .setNeutralButton("删除当前", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        deleteCurrentCoupon();
                    }
                })
                .setNegativeButton("取消", null)
                .show();

        dlg.getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                dlg.dismiss();
                showRenameDialog(position);
                return true;
            }
        });
    }

    private void showNewCouponDialog() {
        final EditText input = new EditText(this);
        input.setHint("券名称，例如 中秋消费券");
        input.setText("消费券 " + (store.coupons.size() + 1));
        input.setSelection(input.getText().length());

        FrameLayout box = new FrameLayout(this);
        int pad = dp(20);
        box.setPadding(pad, dp(6), pad, 0);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("新建消费券")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String nm = input.getText().toString().trim();
                        if (nm.isEmpty()) nm = "消费券 " + (store.coupons.size() + 1);
                        store.coupons.add(new Coupon(nm, 0));
                        store.current = store.coupons.size() - 1;
                        store.save();
                        bindCoupon();
                        etCoupon.requestFocus();
                    }
                })
                .show();
    }

    private void showRenameDialog(final int index) {
        if (index < 0 || index >= store.coupons.size()) return;
        final Coupon target = store.coupons.get(index);

        final EditText input = new EditText(this);
        input.setText(target.name);
        input.setSelection(input.getText().length());

        FrameLayout box = new FrameLayout(this);
        int pad = dp(20);
        box.setPadding(pad, dp(6), pad, 0);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("重命名消费券")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String nm = input.getText().toString().trim();
                        if (nm.isEmpty()) {
                            toast("名称不能为空");
                            return;
                        }
                        target.name = nm;
                        store.save();
                        bindCoupon();
                    }
                })
                .show();
    }

    private void deleteCurrentCoupon() {
        if (store.coupons.size() <= 1) {
            toast("至少保留一张消费券");
            return;
        }
        final Coupon c = store.cur();
        new AlertDialog.Builder(this)
                .setTitle("删除消费券")
                .setMessage("删除「" + c.name + "」及其 " + c.items.size() + " 条商品记录？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        store.coupons.remove(c);
                        store.current = 0;
                        store.save();
                        bindCoupon();
                        toast("已删除");
                    }
                })
                .show();
    }

    // ------------------------------------------------------------ 商品

    private void addItem() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            toast("请先填写商品名称");
            etName.requestFocus();
            return;
        }
        double price = Fmt.parse(etPrice.getText().toString());
        if (Double.isNaN(price) || price < 0) {
            toast("请填写正确的单价");
            etPrice.requestFocus();
            return;
        }
        double qty = Fmt.parse(etQty.getText().toString());
        if (Double.isNaN(qty) || qty <= 0) {
            toast("请填写正确的数量");
            etQty.requestFocus();
            return;
        }

        Coupon c = store.cur();
        c.items.add(new Item(name, price, qty));
        store.remember(name, price);
        store.save();

        refreshHistorySuggestions();
        adapter.notifyDataSetChanged();
        refresh();

        etName.setText("");
        etPrice.setText("");
        etName.requestFocus();
        hideKeyboard();

        final int last = c.items.size() - 1;
        listView.post(new Runnable() {
            @Override
            public void run() {
                listView.smoothScrollToPosition(last);
            }
        });
    }

    /** 点清单行弹出编辑框 */
    private void showEditDialog(final int position) {
        final Coupon c = store.cur();
        if (position < 0 || position >= c.items.size()) return;
        final Item it = c.items.get(position);

        View v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_item, null);
        final EditText dName = (EditText) v.findViewById(R.id.dlgName);
        final EditText dPrice = (EditText) v.findViewById(R.id.dlgPrice);
        final EditText dQty = (EditText) v.findViewById(R.id.dlgQty);

        dName.setText(it.name);
        dName.setSelection(dName.getText().length());
        dPrice.setText(Fmt.qty(it.price));
        dQty.setText(Fmt.qty(it.qty));

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("修改商品")
                .setView(v)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .setNeutralButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        deleteItem(position);
                    }
                })
                .create();
        dlg.show();

        // 自定义保存：校验不通过时不关闭对话框
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = dName.getText().toString().trim();
                double price = Fmt.parse(dPrice.getText().toString());
                double qty = Fmt.parse(dQty.getText().toString());
                if (name.isEmpty()) {
                    toast("商品名称不能为空");
                    return;
                }
                if (Double.isNaN(price) || price < 0) {
                    toast("单价不合法");
                    return;
                }
                if (Double.isNaN(qty) || qty <= 0) {
                    toast("数量不合法");
                    return;
                }
                it.name = name;
                it.price = price;
                it.qty = qty;
                store.remember(name, price);
                store.save();
                refreshHistorySuggestions();
                adapter.notifyDataSetChanged();
                refresh();
                dlg.dismiss();
            }
        });
    }

    private void deleteItem(int position) {
        Coupon c = store.cur();
        if (position < 0 || position >= c.items.size()) return;
        String n = c.items.get(position).name;
        c.items.remove(position);
        adapter.notifyDataSetChanged();
        refresh();
        store.save();
        toast("已删除「" + n + "」");
    }

    private void confirmClear() {
        final Coupon c = store.cur();
        if (c.items.isEmpty()) {
            toast("购物清单已经是空的");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空清单")
                .setMessage("删除「" + c.name + "」下全部 " + c.items.size() + " 条商品记录？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        c.items.clear();
                        adapter.notifyDataSetChanged();
                        refresh();
                        store.save();
                        toast("已清空");
                    }
                })
                .show();
    }

    // ------------------------------------------------------------ 界面刷新

    private void refresh() {
        Coupon c = store.cur();
        double coupon = c.amount;
        double total = c.total();

        tvTotal.setText(Fmt.money(total));
        tvCount.setText(c.items.size() + " 件");

        if (coupon <= 0) {
            tvRemainLabel.setText("还差");
            tvRemain.setText("--");
            tvRemain.setTextColor(cSub);
            pb.setProgress(0);
            pb.setProgressTintList(ColorStateList.valueOf(cPrimary));
            tvProgress.setText("填写消费券面额后自动计算");
            tvSuggest.setVisibility(View.GONE);
            return;
        }

        double remain = coupon - total;
        if (remain > 0.004) {                                   // 还没花完
            int percent = (int) Math.round(total / coupon * 100);
            tvRemainLabel.setText("还差");
            tvRemain.setText(Fmt.money(remain));
            tvRemain.setTextColor(cPrimary);
            pb.setProgress(Math.min(percent, 100));
            pb.setProgressTintList(ColorStateList.valueOf(cPrimary));
            tvProgress.setText("已消费 " + percent + "% ，还需买 " + Fmt.money(remain));
            showSuggest(remain);
        } else if (remain < -0.004) {                           // 超额
            tvRemainLabel.setText("已超出");
            tvRemain.setText(Fmt.money(-remain));
            tvRemain.setTextColor(cDanger);
            pb.setProgress(100);
            pb.setProgressTintList(ColorStateList.valueOf(cDanger));
            tvProgress.setText("超出面额 " + Fmt.money(-remain) + " ，需自付补差");
            tvSuggest.setVisibility(View.GONE);
        } else {                                                // 刚好
            tvRemainLabel.setText("还差");
            tvRemain.setText("¥0.00");
            tvRemain.setTextColor(cSuccess);
            pb.setProgress(100);
            pb.setProgressTintList(ColorStateList.valueOf(cSuccess));
            tvProgress.setText("刚好用完消费券，可以结账了");
            tvSuggest.setVisibility(View.GONE);
        }
    }

    /** 凑单建议：差额均摊到 1~3 件，并优先推荐历史里单价接近的商品 */
    private void showSuggest(double remain) {
        StringBuilder sb = new StringBuilder();
        sb.append("凑单参考：1 件 ").append(Fmt.money(remain))
                .append(" / 2 件 ").append(Fmt.money(remain / 2))
                .append(" / 3 件 ").append(Fmt.money(remain / 3));

        History h = store.closestTo(remain);
        if (h != null) {
            sb.append("\n上次买过「").append(h.name).append("」").append(Fmt.money(h.price));
        }
        tvSuggest.setText(sb.toString());
        tvSuggest.setVisibility(View.VISIBLE);
    }

    private void refreshHistorySuggestions() {
        nameAdapter.setData(store.historyNames());
    }

    // ------------------------------------------------------------ 工具

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** 简化版 TextWatcher */
    private abstract static class Watcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
