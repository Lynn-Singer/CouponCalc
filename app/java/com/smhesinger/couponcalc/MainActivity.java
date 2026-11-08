package com.smhesinger.couponcalc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 消费券计算器
 * v1.1：多张券各自一张清单、商品名联想+历史单价、点行编辑、凑单建议
 */
public class MainActivity extends Activity {

    /** 从比价页带回一条商品 */
    private static final int REQ_COMPARE = 101;

    private Store store;

    private EditText etCoupon, etPrice, etQty;
    private AutoCompleteTextView etName;
    private TextView tvTotal, tvRemain, tvRemainLabel, tvProgress, tvSuggest, tvCount, tvEmpty, btnClear, btnSwitch;
    private TextView tvCouponTitle, tvCouponHint, tvCouponStatus, btnOwner;
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
        tvCouponTitle = (TextView) findViewById(R.id.tvCouponTitle);
        tvCouponHint = (TextView) findViewById(R.id.tvCouponHint);
        tvCouponStatus = (TextView) findViewById(R.id.tvCouponStatus);
        btnOwner = (TextView) findViewById(R.id.btnOwner);
        pb = (ProgressBar) findViewById(R.id.pb);
        listView = (ListView) findViewById(R.id.listView);

        adapter = new ItemAdapter(this, new ItemAdapter.Listener() {
            @Override
            public void onEdit(Row row) {
                showEditDialog(row);
            }

            @Override
            public void onDelete(Row row) {
                deleteItem(row);
            }

            @Override
            public void onMove(Row row) {
                showMoveDialog(row);
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
        btnOwner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCouponPicker();
            }
        });
        // 合并结算时面额是各券合计，不可直接编辑，点一下进券管理逐张改
        etCoupon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (store.combined()) showCouponPicker();
            }
        });
        findViewById(R.id.btnCompare).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(
                        new Intent(MainActivity.this, CompareActivity.class), REQ_COMPARE);
            }
        });

        etCoupon.addTextChangedListener(new Watcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (loading || store.combined()) return;
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

    /** 比价页选中某项后「加入购物车」会把名称和单价带回来 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_COMPARE || resultCode != RESULT_OK || data == null) return;

        String name = data.getStringExtra(CompareActivity.EXTRA_ADD_NAME);
        double price = data.getDoubleExtra(CompareActivity.EXTRA_ADD_PRICE, 0);
        if (name == null || name.trim().isEmpty() || price <= 0) return;

        store.cur().items.add(new Item(name, price, 1));
        store.remember(name, price);
        store.save();

        reloadList();
        refreshHistorySuggestions();
        refresh();
        Toast.makeText(this, "已加入：" + name + " " + Fmt.money(price),
                Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------ 券

    /** 把当前结算状态绑定到界面（切换券 / 切换结算方式 / 初始化时调用） */
    private void bindCoupon() {
        Coupon c = store.cur();
        boolean combined = store.combined();

        loading = true;
        etCoupon.setText(combined
                ? (store.sumAmount() > 0 ? Fmt.qty(store.sumAmount()) : "")
                : (c.amount > 0 ? Fmt.qty(c.amount) : ""));
        etCoupon.setSelection(etCoupon.getText().length());
        loading = false;

        // 合并结算时大字是各券合计，不能直接改，点一下进券管理逐张改
        etCoupon.setFocusable(!combined);
        etCoupon.setFocusableInTouchMode(!combined);
        etCoupon.setClickable(combined);
        etCoupon.setLongClickable(!combined);
        etCoupon.setCursorVisible(!combined);

        tvCouponTitle.setText(combined ? "消费券合计面额" : "消费券面额");
        if (combined) {
            btnSwitch.setText(store.coupons.size() + " 张券 ▾");
            tvCouponHint.setText(amountBreakdown());
        } else {
            btnSwitch.setText(c.name + " ▾");
            tvCouponHint.setText(store.multi()
                    ? "逐张核销：每张券都要单独凑够面额"
                    : "面额可随时修改，已录入金额自动重算");
        }

        // 录入归属：只有逐张核销且不止一张券时才需要指定
        boolean showOwner = store.multi() && !combined;
        btnOwner.setVisibility(showOwner ? View.VISIBLE : View.GONE);
        if (showOwner) btnOwner.setText("记入 " + c.name + " ▾");

        adapter.setMoveEnabled(showOwner);
        adapter.setRows(combined ? store.flatRows() : store.rowsOf(store.current));
        refresh();
    }

    /** 「券1 ¥100.00 + 券2 ¥50.00 = ¥150.00」 */
    private String amountBreakdown() {
        StringBuilder sb = new StringBuilder();
        for (Coupon c : store.coupons) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(c.name).append(" ").append(c.amount > 0 ? Fmt.money(c.amount) : "未填");
        }
        sb.append(" = ").append(Fmt.money(store.sumAmount()));
        return sb.toString();
    }

    private void showCouponPicker() {
        final boolean showShort = store.multi() && !store.combined();
        final String[] labels = new String[store.coupons.size()];
        for (int i = 0; i < store.coupons.size(); i++) {
            Coupon c = store.coupons.get(i);
            String amt = c.amount > 0 ? Fmt.money(c.amount) : "未填面额";
            String line2 = "面额 " + amt + "  ·  已购 " + Fmt.money(c.total());
            if (showShort) {
                double need = c.amount - c.total();
                if (c.amount <= 0) line2 += "  ·  未设面额";
                else if (need > 0.004) line2 += "  ·  还差 " + Fmt.money(need);
                else line2 += "  ·  已够";
            }
            labels[i] = c.name + "\n" + line2;
        }

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("消费券（长按改名或删除）")
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
                .setNeutralButton("结算方式", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        showSettleModeDialog();
                    }
                })
                .setNegativeButton("取消", null)
                .show();

        dlg.getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                dlg.dismiss();
                showCouponMenu(position);
                return true;
            }
        });
    }

    /** 长按券条目的菜单：改名 / 删除 */
    private void showCouponMenu(final int index) {
        if (index < 0 || index >= store.coupons.size()) return;
        final Coupon c = store.coupons.get(index);
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(new String[]{"重命名", "删除这张券"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) showRenameDialog(index);
                        else confirmDeleteCoupon(index);
                    }
                })
                .show();
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
                        // 第一张券刚变成两张：问清楚这两张券怎么结算
                        if (store.coupons.size() == 2) showSettleModeDialog();
                        else etCoupon.requestFocus();
                    }
                })
                .show();
    }

    /** 多张券的结算方式：合并结算 / 逐张核销 */
    private void showSettleModeDialog() {
        if (!store.multi()) {
            toast("只有一张券，两种结算方式结果相同");
            return;
        }
        final int[] picked = {store.mode};
        final String[] items = {
                "合并结算：所有商品一起算，总价 ≥ 各券面额合计即可",
                "逐张核销：每张券单独凑够面额，商品要分到各张券下"
        };
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(store.coupons.size() + " 张券怎么结算？")
                .setSingleChoiceItems(items, picked[0], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        picked[0] = which;
                    }
                })
                .setNegativeButton("稍后决定", null)
                .setPositiveButton("确定", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applySettleMode(picked[0]);
                dlg.dismiss();
            }
        });
    }

    private void applySettleMode(int mode) {
        store.mode = mode;
        store.save();
        bindCoupon();
        toast(mode == Store.MODE_COMBINED
                ? "合并结算：" + store.coupons.size() + " 张券合计 " + Fmt.money(store.sumAmount())
                : "逐张核销：每张券都要单独凑够面额");
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

    private void confirmDeleteCoupon(final int index) {
        if (store.coupons.size() <= 1) {
            toast("至少保留一张消费券");
            return;
        }
        if (index < 0 || index >= store.coupons.size()) return;
        final Coupon c = store.coupons.get(index);
        new AlertDialog.Builder(this)
                .setTitle("删除消费券")
                .setMessage("删除「" + c.name + "」及其 " + c.items.size() + " 条商品记录？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        store.coupons.remove(index);
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
        reloadList();
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
    private void showEditDialog(final Row row) {
        final Item it = row.item;

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
                        deleteItem(row);
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
                reloadList();
                refresh();
                dlg.dismiss();
            }
        });
    }

    private void deleteItem(Row row) {
        if (row.coupon < 0 || row.coupon >= store.coupons.size()) return;
        Coupon c = store.coupons.get(row.coupon);
        if (!c.items.remove(row.item)) return;
        reloadList();
        refresh();
        store.save();
        toast("已删除「" + row.item.name + "」");
    }

    /** 清单数据源：合并结算取跨券平铺，逐张核销取当前券 */
    private void reloadList() {
        adapter.setRows(store.combined() ? store.flatRows() : store.rowsOf(store.current));
    }

    /** 长按清单行：把这条商品移到别的券下（逐张核销时用来纠正归属） */
    private void showMoveDialog(final Row row) {
        if (store.combined() || store.coupons.size() < 2) return;
        if (row.coupon < 0 || row.coupon >= store.coupons.size()) return;

        final List<Integer> targets = new ArrayList<Integer>();
        final List<String> names = new ArrayList<String>();
        for (int i = 0; i < store.coupons.size(); i++) {
            if (i == row.coupon) continue;
            targets.add(i);
            names.add(store.coupons.get(i).name + "  ·  已购 "
                    + Fmt.money(store.coupons.get(i).total()));
        }
        if (targets.isEmpty()) return;

        new AlertDialog.Builder(this)
                .setTitle("把「" + row.item.name + "」移到")
                .setItems(names.toArray(new String[names.size()]),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                Coupon from = store.coupons.get(row.coupon);
                                Coupon dest = store.coupons.get(targets.get(which));
                                from.items.remove(row.item);
                                dest.items.add(row.item);
                                store.save();
                                bindCoupon();
                                toast("已移到「" + dest.name + "」");
                            }
                        })
                .show();
    }

    private void confirmClear() {
        final boolean combined = store.combined();
        final int count = combined ? store.sumItemCount() : store.cur().items.size();
        if (count == 0) {
            toast("购物清单已经是空的");
            return;
        }
        String msg = combined
                ? "删除 " + store.coupons.size() + " 张券下全部 " + count + " 条商品记录？"
                : "删除「" + store.cur().name + "」下全部 " + count + " 条商品记录？";
        new AlertDialog.Builder(this)
                .setTitle("清空清单")
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (combined) {
                            for (Coupon c : store.coupons) c.items.clear();
                        } else {
                            store.cur().items.clear();
                        }
                        reloadList();
                        refresh();
                        store.save();
                        toast("已清空");
                    }
                })
                .show();
    }

    // ------------------------------------------------------------ 界面刷新

    private void refresh() {
        boolean combined = store.combined();
        double coupon = combined ? store.sumAmount() : store.cur().amount;
        double total = combined ? store.sumTotal() : store.cur().total();
        int count = combined ? store.sumItemCount() : store.cur().items.size();

        tvTotal.setText(Fmt.money(total));
        tvCount.setText(count + " 件");

        updateCouponStatus();

        if (coupon <= 0) {
            tvRemainLabel.setText("还差");
            tvRemain.setText("--");
            tvRemain.setTextColor(cSub);
            pb.setProgress(0);
            pb.setProgressTintList(ColorStateList.valueOf(cPrimary));
            tvProgress.setText(combined ? "各张券填好面额后自动计算" : "填写消费券面额后自动计算");
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
            tvProgress.setText(combined
                    ? "超出合计面额 " + Fmt.money(-remain) + " ，需自付补差"
                    : "超出面额 " + Fmt.money(-remain) + " ，需自付补差");
            tvSuggest.setVisibility(View.GONE);
        } else {                                                // 刚好
            tvRemainLabel.setText("还差");
            tvRemain.setText("¥0.00");
            tvRemain.setTextColor(cSuccess);
            pb.setProgress(100);
            pb.setProgressTintList(ColorStateList.valueOf(cSuccess));
            tvProgress.setText(combined
                    ? "刚好达到 " + store.coupons.size() + " 张券合计面额，可以结账了"
                    : "刚好用完消费券，可以结账了");
            tvSuggest.setVisibility(View.GONE);
        }
    }

    /** 逐张核销：提示每张券各自的达标情况；合并结算或单券时隐藏 */
    private void updateCouponStatus() {
        if (!store.multi() || store.combined()) {
            tvCouponStatus.setVisibility(View.GONE);
            return;
        }
        int shortN = store.shortCount();
        int total = store.coupons.size();
        int noAmount = 0;
        for (Coupon c : store.coupons) {
            if (c.amount <= 0) noAmount++;
        }
        int okN = total - noAmount - shortN;

        if (noAmount > 0) {
            // 有券没填面额时优先提示，否则"都已够面额"会误导
            String head = shortN > 0
                    ? okN + " 张已够，" + shortN + " 张还差"
                    : okN + " 张已够";
            tvCouponStatus.setBackgroundResource(R.drawable.bg_status_warn);
            tvCouponStatus.setTextColor(getResources().getColor(R.color.tieText));
            tvCouponStatus.setText("逐张核销：" + head + "，还有 " + noAmount + " 张券没填面额");
        } else if (shortN == 0) {
            tvCouponStatus.setBackgroundResource(R.drawable.bg_status_ok);
            tvCouponStatus.setTextColor(getResources().getColor(R.color.bestText));
            tvCouponStatus.setText(total + " 张券都已够面额，可以结账了");
        } else {
            Coupon first = store.firstShort();
            double need = first.amount - first.total();
            String head = shortN == total
                    ? "逐张核销：" + total + " 张券都还没凑够"
                    : "逐张核销：" + okN + " 张已够，" + shortN + " 张还差";
            tvCouponStatus.setBackgroundResource(R.drawable.bg_status_warn);
            tvCouponStatus.setTextColor(getResources().getColor(R.color.tieText));
            tvCouponStatus.setText(head + "\n" + first.name + " 还差 " + Fmt.money(need)
                    + " · 长按商品可改归属");
        }
        tvCouponStatus.setVisibility(View.VISIBLE);
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
