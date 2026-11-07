package com.smhesinger.couponcalc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品名联想适配器
 * 不用 ArrayAdapter：它首次过滤后会缓存快照，导致后续新增的历史商品不参与联想。
 * 这里自己实现 Filterable，并把「前缀命中」排在「包含命中」前面，
 * 这样输入「牛奶」既能匹配「牛奶」，也能匹配「伊利纯牛奶」。
 */
public class NameAdapter extends BaseAdapter implements Filterable {

    private final Context ctx;
    private List<String> all = new ArrayList<String>();
    private List<String> shown = new ArrayList<String>();

    public NameAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setData(List<String> data) {
        all = (data == null) ? new ArrayList<String>() : data;
        shown = all;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return shown.size();
    }

    @Override
    public Object getItem(int position) {
        return shown.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView tv;
        if (convertView == null) {
            tv = (TextView) LayoutInflater.from(ctx)
                    .inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
        } else {
            tv = (TextView) convertView;
        }
        tv.setText(shown.get(position));
        return tv;
    }

    private final Filter filter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<String> out = new ArrayList<String>();
            if (constraint == null || constraint.length() == 0) {
                out.addAll(all);
            } else {
                String q = constraint.toString().toLowerCase();
                List<String> contains = new ArrayList<String>();
                for (String s : all) {
                    String ls = s.toLowerCase();
                    if (ls.startsWith(q)) {
                        out.add(s);
                    } else if (ls.contains(q)) {
                        contains.add(s);
                    }
                }
                out.addAll(contains);
            }
            FilterResults r = new FilterResults();
            r.values = out;
            r.count = out.size();
            return r;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            shown = (results.values == null) ? new ArrayList<String>() : (List<String>) results.values;
            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }
    };

    @Override
    public Filter getFilter() {
        return filter;
    }
}
