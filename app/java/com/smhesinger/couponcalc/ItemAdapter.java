package com.smhesinger.couponcalc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 购物清单列表适配器，支持跨券平铺（合并结算）和长按移动归属 */
public class ItemAdapter extends BaseAdapter {

    public interface Listener {
        void onEdit(Row row);

        void onDelete(Row row);

        /** 长按：把这条商品移到别的券下 */
        void onMove(Row row);
    }

    private final Context ctx;
    private final Listener listener;
    private List<Row> data = new ArrayList<Row>();
    private boolean moveEnabled = false;

    public ItemAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    /** 切换消费券 / 切换结算方式时替换数据源 */
    public void setRows(List<Row> rows) {
        this.data = rows == null ? new ArrayList<Row>() : rows;
        notifyDataSetChanged();
    }

    /** 只有逐张核销且有多张券时，长按移动才有意义 */
    public void setMoveEnabled(boolean enabled) {
        this.moveEnabled = enabled;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_row, parent, false);
            h = new ViewHolder();
            h.name = (TextView) convertView.findViewById(R.id.tvName);
            h.detail = (TextView) convertView.findViewById(R.id.tvDetail);
            h.subtotal = (TextView) convertView.findViewById(R.id.tvSubtotal);
            h.del = (TextView) convertView.findViewById(R.id.tvDel);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        final Row row = data.get(position);
        Item it = row.item;
        h.name.setText(it.name);
        h.detail.setText(Fmt.money(it.price) + " × " + Fmt.qty(it.qty));
        h.subtotal.setText(Fmt.money(it.subtotal()));

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onEdit(row);
            }
        });
        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!moveEnabled || listener == null) return false;
                listener.onMove(row);
                return true;
            }
        });
        h.del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onDelete(row);
            }
        });
        return convertView;
    }

    private static class ViewHolder {
        TextView name;
        TextView detail;
        TextView subtotal;
        TextView del;
    }
}
