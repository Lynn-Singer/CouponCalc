package com.smhesinger.couponcalc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 购物清单列表适配器 */
public class ItemAdapter extends BaseAdapter {

    public interface Listener {
        void onEdit(int position);

        void onDelete(int position);
    }

    private final Context ctx;
    private final Listener listener;
    private List<Item> data = new ArrayList<Item>();

    public ItemAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    /** 切换消费券时替换数据源 */
    public void setItems(List<Item> items) {
        this.data = items == null ? new ArrayList<Item>() : items;
        notifyDataSetChanged();
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

        Item it = data.get(position);
        h.name.setText(it.name);
        h.detail.setText(Fmt.money(it.price) + " × " + Fmt.qty(it.qty));
        h.subtotal.setText(Fmt.money(it.subtotal()));

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onEdit(position);
            }
        });
        h.del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onDelete(position);
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
