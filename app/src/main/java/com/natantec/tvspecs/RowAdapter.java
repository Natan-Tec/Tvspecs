package com.natantec.tvspecs;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adaptador da lista de informações; cada tipo de {@link Row} tem seu próprio layout. */
final class RowAdapter extends BaseAdapter {
    private final Context ctx;
    private List<Row> rows = new ArrayList<Row>();

    private static final class Hold {
        TextView a;
        TextView b;
        TextView c;
        TextView d;
        TextView e;
        BarView bar;
        SparkView spark;
    }

    RowAdapter(Context ctx) {
        this.ctx = ctx;
    }

    void set(List<Row> newRows) {
        rows = newRows == null ? new ArrayList<Row>() : newRows;
        notifyDataSetChanged();
    }

    Row row(int position) {
        return rows.get(position);
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return Row.TYPES;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        int t = rows.get(position).type;
        return t != Row.HEADER && t != Row.SENSOR_HEAD && t != Row.NOTE;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row r = rows.get(position);
        View v = convertView;
        if (v == null) v = create(r.type);
        bind((Hold) v.getTag(), r);
        return v;
    }

    // ---------- criação dos layouts ----------

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    private View create(int type) {
        Hold h = new Hold();
        int pad = Ui.dp(ctx, 14);
        int vpad = Ui.dp(ctx, 7);
        View root;

        switch (type) {
            case Row.HEADER: {
                TextView t = text(15, Ui.ACCENT, true);
                t.setPadding(pad, Ui.dp(ctx, 9), pad, Ui.dp(ctx, 9));
                t.setBackgroundColor(Ui.HEAD);
                h.a = t;
                root = t;
                break;
            }
            case Row.KV: {
                LinearLayout l = new LinearLayout(ctx);
                l.setOrientation(LinearLayout.HORIZONTAL);
                l.setPadding(pad, vpad, pad, vpad);
                h.a = text(17, Ui.MUTED, false);
                h.b = text(17, Ui.TEXT, false);
                l.addView(h.a, weight(4));
                l.addView(h.b, weight(6));
                root = l;
                break;
            }
            case Row.BAR: {
                LinearLayout l = new LinearLayout(ctx);
                l.setOrientation(LinearLayout.VERTICAL);
                l.setPadding(pad, vpad, pad, vpad);
                LinearLayout top = new LinearLayout(ctx);
                top.setOrientation(LinearLayout.HORIZONTAL);
                h.a = text(17, Ui.MUTED, false);
                h.b = text(17, Ui.TEXT, false);
                h.b.setGravity(Gravity.END);
                top.addView(h.a, weight(1));
                top.addView(h.b, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                l.addView(top, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                h.bar = new BarView(ctx);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 10));
                bp.topMargin = Ui.dp(ctx, 6);
                l.addView(h.bar, bp);
                root = l;
                break;
            }
            case Row.SENSOR:
            case Row.SENSOR_HEAD: {
                boolean head = type == Row.SENSOR_HEAD;
                LinearLayout l = new LinearLayout(ctx);
                l.setOrientation(LinearLayout.HORIZONTAL);
                l.setPadding(pad, head ? Ui.dp(ctx, 9) : Ui.dp(ctx, 6), pad, head ? Ui.dp(ctx, 9) : Ui.dp(ctx, 6));
                if (head) l.setBackgroundColor(Ui.HEAD2);
                int nameColor = head ? Ui.MUTED : Ui.TEXT;
                h.a = text(16, nameColor, head);
                h.b = number(head);
                h.c = number(head);
                h.d = number(head);
                h.e = number(head);
                l.addView(h.a, weight(5));
                l.addView(h.b, weight(2));
                l.addView(h.c, weight(2));
                l.addView(h.d, weight(2));
                l.addView(h.e, weight(2));
                root = l;
                break;
            }
            case Row.GRAPH: {
                LinearLayout l = new LinearLayout(ctx);
                l.setOrientation(LinearLayout.VERTICAL);
                l.setPadding(pad, vpad, pad, vpad);
                LinearLayout top = new LinearLayout(ctx);
                top.setOrientation(LinearLayout.HORIZONTAL);
                h.a = text(17, Ui.MUTED, false);
                h.b = text(17, Ui.TEXT, false);
                h.b.setGravity(Gravity.END);
                top.addView(h.a, weight(1));
                top.addView(h.b, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                l.addView(top, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                h.spark = new SparkView(ctx);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 56));
                sp.topMargin = Ui.dp(ctx, 5);
                l.addView(h.spark, sp);
                root = l;
                break;
            }
            case Row.ACTION: {
                FrameLayout f = new FrameLayout(ctx);
                f.setPadding(pad, Ui.dp(ctx, 8), pad, Ui.dp(ctx, 8));
                TextView t = text(18, Ui.ACCENT, true);
                t.setGravity(Gravity.CENTER);
                t.setPadding(pad, Ui.dp(ctx, 12), pad, Ui.dp(ctx, 12));
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(Ui.dp(ctx, 8));
                g.setStroke(Ui.dp(ctx, 2), Ui.ACCENT);
                g.setColor(0x1A4FC3F7);
                t.setBackgroundDrawable(g);
                f.addView(t, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                h.a = t;
                root = f;
                break;
            }
            default: {
                TextView t = text(14, Ui.MUTED, false);
                t.setPadding(pad, Ui.dp(ctx, 10), pad, Ui.dp(ctx, 10));
                h.a = t;
                root = t;
                break;
            }
        }
        root.setTag(h);
        return root;
    }

    private TextView number(boolean head) {
        TextView t = text(16, head ? Ui.MUTED : Ui.TEXT, head);
        t.setGravity(Gravity.END);
        t.setTypeface(head ? Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) : Typeface.MONOSPACE);
        return t;
    }

    // ---------- preenchimento ----------

    private void bind(Hold h, Row r) {
        switch (r.type) {
            case Row.HEADER:
                h.a.setText(r.label.toUpperCase(Locale.getDefault()));
                break;
            case Row.KV:
                h.a.setText(r.label);
                h.b.setText(r.value);
                h.b.setTextColor(Ui.textColor(r.level));
                break;
            case Row.BAR:
                h.a.setText(r.label);
                h.b.setText(r.value);
                h.b.setTextColor(Ui.textColor(r.level));
                h.bar.set(r.pct, Ui.fillColor(r.level));
                break;
            case Row.SENSOR:
                h.a.setText(r.label);
                h.b.setText(r.value);
                h.b.setTextColor(Ui.textColor(r.level));
                h.c.setText(r.min);
                h.d.setText(r.max);
                h.e.setText(r.avg);
                break;
            case Row.SENSOR_HEAD:
                h.a.setText(r.label);
                h.b.setText(r.value);
                h.c.setText(r.min);
                h.d.setText(r.max);
                h.e.setText(r.avg);
                break;
            case Row.GRAPH:
                h.a.setText(r.label);
                h.b.setText(r.value);
                h.b.setTextColor(Ui.textColor(r.level));
                h.spark.set(r.series, r.graphMax, Ui.fillColor(r.level));
                break;
            default:
                h.a.setText(r.label);
                break;
        }
    }
}
