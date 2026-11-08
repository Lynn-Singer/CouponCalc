package com.smhesinger.couponcalc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 比价结果列表。
 * 行有两种：分组标题（重量/容量/个数）+ 组内按单价升序的比价项。
 */
public class SpecAdapter extends BaseAdapter {

    public static final int TYPE_GROUP = 0;
    public static final int TYPE_ITEM = 1;

    /** 一行数据，分组标题和比价项共用，靠 type 区分 */
    public static class Row {
        public int type = TYPE_ITEM;
        public String groupTitle = "";
        public String groupHint = "";
        public String targetText = "";
        public int dim;
        public Spec spec;
        public double unitPrice;
        public double diffPct;
        public boolean best;
        public boolean tie;
        public int packs = 1;
        public double cost;
        public boolean showCost;
    }

    public interface Listener {
        void onTarget(int dim);

        void onItem(Row row);
    }

    private static final DecimalFormat PCT1 = new DecimalFormat("0.0");

    private final Context ctx;
    private final List<Row> rows = new ArrayList<Row>();
    private final Listener listener;

    private final int cBest, cBestText, cPrimary, cSecondary, cDivider;

    public SpecAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        cBest = ctx.getResources().getColor(R.color.success);
        cBestText = ctx.getResources().getColor(R.color.bestText);
        cPrimary = ctx.getResources().getColor(R.color.textPrimary);
        cSecondary = ctx.getResources().getColor(R.color.textSecondary);
        cDivider = ctx.getResources().getColor(R.color.divider);
    }

    public void setRows(List<Row> data) {
        rows.clear();
        if (data != null) rows.addAll(data);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public boolean isEnabled(int position) {
        return rows.get(position).type == TYPE_ITEM;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Row r = rows.get(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx)
                    .inflate(R.layout.item_spec, parent, false);
        }

        View groupBar = convertView.findViewById(R.id.groupBar);
        View card = convertView.findViewById(R.id.card);

        if (r.type == TYPE_GROUP) {
            groupBar.setVisibility(View.VISIBLE);
            card.setVisibility(View.GONE);

            ((TextView) convertView.findViewById(R.id.tvGroupTitle)).setText(r.groupTitle);
            ((TextView) convertView.findViewById(R.id.tvGroupHint)).setText(r.groupHint);

            TextView btnTarget = (TextView) convertView.findViewById(R.id.btnTarget);
            btnTarget.setText(r.targetText);
            btnTarget.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onTarget(r.dim);
                }
            });
            return convertView;
        }

        groupBar.setVisibility(View.GONE);
        card.setVisibility(View.VISIBLE);

        TextView tvName = (TextView) convertView.findViewById(R.id.tvName);
        TextView tvSpec = (TextView) convertView.findViewById(R.id.tvSpec);
        TextView tvBadge = (TextView) convertView.findViewById(R.id.tvBadge);
        TextView tvTie = (TextView) convertView.findViewById(R.id.tvTie);
        TextView tvUnitPrice = (TextView) convertView.findViewById(R.id.tvUnitPrice);
        TextView tvUnitLabel = (TextView) convertView.findViewById(R.id.tvUnitLabel);
        TextView tvCost = (TextView) convertView.findViewById(R.id.tvCost);
        View bar = convertView.findViewById(R.id.bar);

        tvName.setText(r.spec.title());
        tvSpec.setText(r.spec.specText() + " · " + Fmt.money(r.spec.price));

        tvUnitPrice.setText(Fmt.two(r.unitPrice));
        tvUnitLabel.setText(Unit.priceLabel(r.spec.dim()));

        if (r.best) {
            card.setBackgroundResource(R.drawable.bg_spec_best);
            bar.setBackgroundColor(cBest);
            tvUnitPrice.setTextColor(cBestText);
        } else {
            card.setBackgroundResource(R.drawable.bg_spec_normal);
            bar.setBackgroundColor(cDivider);
            tvUnitPrice.setTextColor(cPrimary);
        }

        if (r.best) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setBackgroundResource(R.drawable.bg_badge_best);
            tvBadge.setTextColor(cBestText);
            tvBadge.setText(r.showCost ? "最省钱" : "最划算");
        } else if (r.tie) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setBackgroundResource(R.drawable.bg_badge_tie);
            tvBadge.setTextColor(ctx.getResources().getColor(R.color.tieText));
            tvBadge.setText("基本持平");
        } else if (r.diffPct > 0) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setBackgroundResource(R.drawable.bg_badge_bad);
            tvBadge.setTextColor(ctx.getResources().getColor(R.color.badText));
            tvBadge.setText((r.showCost ? "多花 " : "贵 ") + pct(r.diffPct) + "%");
        } else {
            tvBadge.setVisibility(View.GONE);
        }

        if (r.tie && !r.best) {
            tvTie.setVisibility(View.VISIBLE);
            tvTie.setText("差不到 3%");
        } else {
            tvTie.setVisibility(View.GONE);
        }

        if (r.showCost) {
            tvCost.setVisibility(View.VISIBLE);
            tvCost.setText("买 " + r.packs + " 件 " + Fmt.money(r.cost));
        } else {
            tvCost.setVisibility(View.GONE);
        }

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onItem(r);
            }
        });
        return convertView;
    }

    private String pct(double v) {
        if (v >= 10) return String.valueOf(Math.round(v));
        return PCT1.format(v);
    }
}
