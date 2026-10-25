package com.natantec.tvspecs;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tela única: menu de painéis à esquerda e conteúdo à direita.
 * Navegação pelo controle remoto: ◀ ▶ troca entre menu e conteúdo, ▲ ▼ rola, OK seleciona.
 */
public class MainActivity extends Activity implements Pages.Host {

    private final SensorStore store = new SensorStore();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<Integer, List<Row>> cache = new HashMap<Integer, List<Row>>();
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private Pages pages;
    private Sampler sampler;
    private RowAdapter adapter;
    private ListView content;
    private LinearLayout menuBox;
    private TextView[] menuItems;
    private TextView clockView;
    private TextView uptimeView;
    private int current = 0;

    private final Runnable refreshRun = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        pages = new Pages(this, store, this);
        sampler = new Sampler(this, store, new Sampler.Listener() {
            @Override
            public void onSampled() {
                ui.post(refreshRun);
            }
        });

        setContentView(buildUi());
        showPage(0);
        menuItems[0].requestFocus();

        // Informações pesadas (getprop, GPU, codecs, DRM) carregam em segundo plano.
        new Thread(new Runnable() {
            @Override
            public void run() {
                Info.load(getApplicationContext());
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cache.clear();
                        adapter.set(getRows(current));
                    }
                });
            }
        }, "tvspecs-info").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sampler.start();
        ui.post(refreshRun);
    }

    @Override
    protected void onPause() {
        sampler.stop();
        super.onPause();
    }

    // ---------------------------------------------------------------------
    // Interface
    // ---------------------------------------------------------------------

    private TextView label(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View buildUi() {
        final int matchP = ViewGroup.LayoutParams.MATCH_PARENT;
        final int wrapC = ViewGroup.LayoutParams.WRAP_CONTENT;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 40), Ui.dp(this, 22), Ui.dp(this, 40), Ui.dp(this, 16));

        // ---- cabeçalho ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(28, Ui.ACCENT, true);
        title.setText("TV Specs");
        titleBox.addView(title);
        TextView sub = label(15, Ui.MUTED, false);
        sub.setText(Sys.cap(android.os.Build.MANUFACTURER) + " " + android.os.Build.MODEL
                + "   •   Android " + android.os.Build.VERSION.RELEASE);
        titleBox.addView(sub);
        head.addView(titleBox, new LinearLayout.LayoutParams(0, wrapC, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        clockView = label(24, Ui.TEXT, true);
        clockView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clockView.setGravity(Gravity.END);
        uptimeView = label(14, Ui.MUTED, false);
        uptimeView.setGravity(Gravity.END);
        right.addView(clockView);
        right.addView(uptimeView);
        head.addView(right, new LinearLayout.LayoutParams(wrapC, wrapC));

        root.addView(head, new LinearLayout.LayoutParams(matchP, wrapC));

        // ---- corpo: menu + conteúdo ----
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);

        ScrollView menuScroll = new ScrollView(this);
        menuScroll.setFocusable(false);
        menuScroll.setVerticalScrollBarEnabled(false);
        menuBox = new LinearLayout(this);
        menuBox.setOrientation(LinearLayout.VERTICAL);
        menuScroll.addView(menuBox, new ViewGroup.LayoutParams(matchP, wrapC));

        menuItems = new TextView[Pages.COUNT];
        for (int i = 0; i < Pages.COUNT; i++) {
            final int idx = i;
            TextView t = label(19, Ui.TEXT, false);
            t.setText(Pages.TITLES[i]);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 11), Ui.dp(this, 12), Ui.dp(this, 11));
            t.setFocusable(true);
            t.setClickable(true);
            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus && idx != current) showPage(idx);
                    else styleMenu();
                }
            });
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx != current) showPage(idx);
                    content.requestFocus();
                }
            });
            menuItems[i] = t;
            menuBox.addView(t, new LinearLayout.LayoutParams(matchP, wrapC));
        }
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.dp(this, 230), matchP);
        body.addView(menuScroll, mp);

        View divider = new View(this);
        divider.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 1), matchP);
        dp.leftMargin = Ui.dp(this, 12);
        dp.rightMargin = Ui.dp(this, 12);
        body.addView(divider, dp);

        content = new ListView(this);
        adapter = new RowAdapter(this);
        content.setAdapter(adapter);
        content.setSelector(new ColorDrawable(0x334FC3F7));
        content.setDivider(new ColorDrawable(0x12FFFFFF));
        content.setDividerHeight(1);
        content.setCacheColorHint(Color.TRANSPARENT);
        content.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content.setFocusable(true);
        content.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Row r = adapter.row(position);
                if (r.action != null) r.action.run();
            }
        });
        body.addView(content, new LinearLayout.LayoutParams(0, matchP, 1f));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(matchP, 0, 1f);
        bp.topMargin = Ui.dp(this, 14);
        root.addView(body, bp);

        // ---- rodapé ----
        TextView hint = label(13, Ui.MUTED, false);
        hint.setText("▲ ▼ rolar     ◀ ▶ trocar entre menu e conteúdo     OK selecionar     VOLTAR sair");
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(matchP, wrapC);
        hp.topMargin = Ui.dp(this, 8);
        root.addView(hint, hp);

        return root;
    }

    private void styleMenu() {
        for (int i = 0; i < menuItems.length; i++) {
            TextView t = menuItems[i];
            boolean sel = i == current;
            t.setTextColor(sel ? Ui.ACCENT : Ui.TEXT);
            t.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            int bg = t.hasFocus() ? 0x664FC3F7 : (sel ? 0x264FC3F7 : 0x00000000);
            t.setBackgroundColor(bg);
        }
    }

    // ---------------------------------------------------------------------
    // Painéis
    // ---------------------------------------------------------------------

    private List<Row> getRows(int idx) {
        if (Pages.isLive(idx)) return pages.build(idx);
        List<Row> c = cache.get(idx);
        if (c == null) {
            c = pages.build(idx);
            if (Info.ready || idx == Pages.REPORT) cache.put(idx, c);
        }
        return c;
    }

    private void showPage(int idx) {
        current = idx;
        styleMenu();
        adapter.set(getRows(idx));
        content.setSelection(0);
    }

    private void refresh() {
        clockView.setText(clockFmt.format(new Date()));
        uptimeView.setText("ligado há " + Sys.uptime());
        if (Pages.isLive(current)) adapter.set(pages.build(current));
    }

    // ---------------------------------------------------------------------
    // Controle remoto
    // ---------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            View f = getCurrentFocus();
            int k = e.getKeyCode();
            if (f != null && f.getParent() == menuBox && k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                content.requestFocus();
                return true;
            }
            if (f == content && k == KeyEvent.KEYCODE_DPAD_LEFT) {
                menuItems[current].requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (content.hasFocus()) {
            menuItems[current].requestFocus();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------------
    // Relatório
    // ---------------------------------------------------------------------

    @Override
    public void saveReport() {
        final String text = Report.text(pages);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    msg = "Salvo em: " + Report.save(getApplicationContext(), text);
                } catch (Throwable t) {
                    msg = "Falha ao salvar: " + t.getMessage();
                }
                final String result = msg;
                Pages.lastReport = result;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                        cache.remove(Pages.REPORT);
                        if (current == Pages.REPORT) adapter.set(getRows(Pages.REPORT));
                    }
                });
            }
        }, "tvspecs-report").start();
    }
}
