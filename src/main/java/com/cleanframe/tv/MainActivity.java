package com.cleanframe.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {

    // {nom affiche, mot-cle de recherche, paquet1, paquet2...}
    private static final String[][] FAVS = {
        {"YouTube", "youtube", "com.google.android.youtube.tv"},
        {"Netflix", "netflix", "com.netflix.ninja"},
        {"TCL Channel", "tcl channel", "com.tcl.tclchannel"},
        {"Disney+", "disney", "com.disney.disneyplus"},
        {"Prime Video", "prime video", "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"},
        {"Play Store", "play store", "com.android.vending"},
    };

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private PackageManager pm;
    private SharedPreferences prefs;
    private Wallpaper wallpaper;
    private float density;
    private LinearLayout col;
    private View firstTile;
    private FrameLayout root;
    private ScrollView sv;
    private View overlay;
    private View allAppsTile;
    private List<ResolveInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        prefs = getSharedPreferences("cf", MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;

        root = new FrameLayout(this);
        wallpaper = new Wallpaper(this);
        wallpaper.setTheme(prefs.getInt("wp", 0));
        root.addView(wallpaper, new FrameLayout.LayoutParams(MATCH, MATCH));

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setFillViewport(true);

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(dp(20), 0, dp(20), dp(40));

        sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    public void onBackPressed() {
        // Ecran d'accueil : on ne quitte pas, on ferme seulement la liste des applis.
        if (overlay != null) closeAllApps();
    }

    private void build() {
        col.removeAllViews();
        firstTile = null;
        apps = allApps();

        int screenH = getResources().getDisplayMetrics().heightPixels;

        FrameLayout topFrame = new FrameLayout(this);
        topFrame.setClipChildren(false);

        // Date + grande heure, centrees en haut
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        top.setPadding(0, 0, 0, dp(16));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextSize(22);
        date.setTextColor(0xF2FFFFFF);
        date.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        date.setShadowLayer(dp(4), 0, dp(1), 0x66000000);
        top.addView(date);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(104);
        clock.setTextColor(0xE8FFFFFF);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(dp(8), 0, dp(2), 0x55000000);
        top.addView(clock);

        topFrame.addView(top, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Petit logo en haut a droite : change le fond d'ecran
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP | Gravity.END);
        blp.setMargins(0, dp(28), dp(12), 0);
        topFrame.addView(wallpaperButton(), blp);

        col.addView(topFrame, new LinearLayout.LayoutParams(MATCH, (int) (screenH * 0.46f)));

        // Rangee 1 : grosses tuiles
        LinearLayout r1 = centerRow();
        for (String[] f : FAVS) addFav(r1, f);

        // Rangee 2 : entrees HDMI + reglages
        LinearLayout r2 = centerRow();
        int n = 0;
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (TvInputInfo info : tim.getTvInputList()) {
                    if (info.getType() == TvInputInfo.TYPE_HDMI) {
                        final String id = info.getId();
                        CharSequence l = info.loadCustomLabel(this);
                        if (l == null || l.length() == 0) l = info.loadLabel(this);
                        String name = l == null ? "HDMI" : l.toString();
                        r2.addView(smallTextTile(name, "HDMI", () -> launch(new Intent(Intent.ACTION_VIEW,
                                TvContract.buildChannelUriForPassthroughInput(id))), false));
                        n++;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (n == 0) {
            r2.addView(smallTextTile("HDMI", "Aucune entrée",
                    () -> Toast.makeText(this, "Aucune entrée HDMI détectée", Toast.LENGTH_SHORT).show(), true));
        }
        r2.addView(smallTextTile("Réglages", "Réglages",
                () -> launch(new Intent(Settings.ACTION_SETTINGS)), false));

        // Rangee 3 : une seule tuile qui ouvre toutes les applis
        LinearLayout r3 = centerRow();
        allAppsTile = tile("Toutes les applis", new GridIcon(this), false, () -> openAllApps(), 124, 70, 13);
        r3.addView(allAppsTile);

        final View ft = firstTile;
        if (ft != null) ft.post(() -> ft.requestFocus());
    }

    // Bouton rond en haut a droite : passe au fond d'ecran suivant
    private View wallpaperButton() {
        final FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(buttonBg(false));
        b.addView(new Logo(this), new FrameLayout.LayoutParams(MATCH, MATCH));
        b.setOnClickListener(v -> {
            int next = (wallpaper.getTheme() + 1) % Wallpaper.count();
            wallpaper.setTheme(next);
            prefs.edit().putInt("wp", next).apply();
            Toast.makeText(this, "Fond d'écran : " + Wallpaper.name(next), Toast.LENGTH_SHORT).show();
        });
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(buttonBg(has));
            float s = has ? 1.15f : 1f;
            b.animate().scaleX(s).scaleY(s).setDuration(140).start();
        });
        return b;
    }

    private GradientDrawable buttonBg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(focused ? 0x66FFFFFF : 0x44FFFFFF);
        if (focused) g.setStroke(dp(3), Color.WHITE);
        return g;
    }

    private void addFav(LinearLayout row, String[] f) {
        Intent in = null;
        ActivityInfo ai = null;
        for (int i = 2; i < f.length && ai == null; i++) {
            Intent c = pm.getLeanbackLaunchIntentForPackage(f[i]);
            if (c == null) c = pm.getLaunchIntentForPackage(f[i]);
            if (c != null) {
                ActivityInfo a = c.resolveActivityInfo(pm, 0);
                if (a != null) {
                    ai = a;
                    in = c;
                }
            }
        }
        if (ai == null) {
            for (ResolveInfo r : apps) {
                if (r.loadLabel(pm).toString().toLowerCase(Locale.ROOT).contains(f[1])) {
                    ai = r.activityInfo;
                    in = intentFor(ai);
                    break;
                }
            }
        }
        if (ai != null) {
            final Intent fin = in;
            row.addView(iconTile(f[0], ai, false, () -> launch(fin), 124, 70, 13));
        } else {
            final String pkg = f[2];
            TextView tv = label(f[0], 16, Color.WHITE, true);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new FrameLayout.LayoutParams(MATCH, MATCH));
            row.addView(tile("Non installé", tv, true,
                    () -> launch(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))),
                    124, 70, 13));
        }
    }

    private List<ResolveInfo> allApps() {
        Map<String, ResolveInfo> m = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                String p = r.activityInfo.packageName;
                if (p.equals(getPackageName())) continue;
                if (!m.containsKey(p)) m.put(p, r);
            }
        }
        List<ResolveInfo> l = new ArrayList<>(m.values());
        Collections.sort(l, (a, c) -> a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString()));
        return l;
    }

    private Intent intentFor(ActivityInfo ai) {
        Intent in = new Intent(Intent.ACTION_MAIN);
        in.setComponent(new ComponentName(ai.packageName, ai.name));
        return in;
    }

    private void launch(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir", Toast.LENGTH_SHORT).show();
        }
    }

    // Rangee horizontale centree (defile si elle deborde)
    private LinearLayout centerRow() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFillViewport(true);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(0, dp(4), 0, dp(4));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(8), 0, dp(8), 0);
        hs.addView(row, new ViewGroup.LayoutParams(WRAP, WRAP));
        col.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
        return row;
    }

    private View iconTile(String name, ActivityInfo ai, boolean dim, Runnable action, int w, int h, int capSp) {
        Drawable d = ai.loadBanner(pm);
        boolean banner = d != null;
        if (d == null) d = ai.loadIcon(pm);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(d);
        FrameLayout.LayoutParams lp;
        if (banner) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            lp = new FrameLayout.LayoutParams(MATCH, MATCH);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            iv.setClipToOutline(true);
            iv.setOutlineProvider(rounded(dp(10)));
            iv.setAlpha(0.95f);
        } else {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lp = new FrameLayout.LayoutParams(dp(h * 6 / 10), dp(h * 6 / 10), Gravity.CENTER);
        }
        iv.setLayoutParams(lp);
        return tile(name, iv, dim, action, w, h, capSp);
    }

    private View smallTextTile(String big, String caption, Runnable action, boolean dim) {
        TextView tv = label(big, 20, Color.WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(8), 0, dp(8), 0);
        tv.setLayoutParams(new FrameLayout.LayoutParams(MATCH, MATCH));
        return tile(caption, tv, dim, action, 124, 58, 13);
    }

    private View tile(String caption, View content, boolean dim, final Runnable action, int wDp, int hDp, int capSp) {
        final LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setFocusable(true);
        wrap.setClickable(true);
        // Marge interne : la tuile grossit sans etre coupee
        wrap.setPadding(dp(10), dp(10), dp(10), dp(10));
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);

        final FrameLayout card = new FrameLayout(this);
        card.setBackground(cardBg(false));
        card.setClipToOutline(true);
        card.setOutlineProvider(rounded(dp(16)));
        card.addView(content);
        if (dim) card.setAlpha(0.5f);
        wrap.addView(card, new LinearLayout.LayoutParams(dp(wDp), dp(hDp)));

        final TextView cap = label(caption, capSp, 0xCCFFFFFF, false);
        cap.setGravity(Gravity.CENTER);
        cap.setSingleLine(true);
        cap.setEllipsize(TextUtils.TruncateAt.END);
        cap.setShadowLayer(dp(3), 0, dp(1), 0x77000000);
        cap.setPadding(0, dp(8), 0, 0);
        wrap.addView(cap, new LinearLayout.LayoutParams(dp(wDp), WRAP));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.setMargins(dp(2), 0, dp(2), 0);
        wrap.setLayoutParams(lp);

        wrap.setOnClickListener(v -> action.run());
        wrap.setOnFocusChangeListener((v, has) -> {
            card.setBackground(cardBg(has));
            cap.setTextColor(has ? Color.WHITE : 0xCCFFFFFF);
            float s = has ? 1.1f : 1f;
            wrap.animate().scaleX(s).scaleY(s).setDuration(140).start();
            wrap.setTranslationZ(has ? dp(12) : 0);
        });

        if (firstTile == null) firstTile = wrap;
        return wrap;
    }

    private GradientDrawable cardBg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(focused ? 0x59FFFFFF : 0x30FFFFFF);
        if (focused) g.setStroke(dp(3), Color.WHITE);
        else g.setStroke(dp(1), 0x55FFFFFF);
        return g;
    }

    private ViewOutlineProvider rounded(final int r) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        };
    }

    // Liste complete des applis, affichee par-dessus l'accueil
    private void openAllApps() {
        if (overlay != null) return;
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(0xEE070B14);
        ov.setClickable(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(24), dp(20), dp(8));
        box.setClipChildren(false);

        TextView title = label("Toutes les applis", 26, Color.WHITE, true);
        title.setPadding(dp(10), 0, 0, dp(8));
        box.addView(title);

        ScrollView s2 = new ScrollView(this);
        s2.setVerticalScrollBarEnabled(false);
        s2.setClipChildren(false);
        s2.setClipToPadding(false);
        final GridLayout g = new GridLayout(this);
        g.setColumnCount(6);
        g.setClipChildren(false);
        for (ResolveInfo r : apps) {
            final ActivityInfo ai = r.activityInfo;
            final Intent in = intentFor(ai);
            g.addView(iconTile(r.loadLabel(pm).toString(), ai, false, () -> {
                closeAllApps();
                launch(in);
            }, 124, 70, 12));
        }
        s2.addView(g, new ViewGroup.LayoutParams(MATCH, WRAP));
        box.addView(s2, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        ov.addView(box, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(ov, new FrameLayout.LayoutParams(MATCH, MATCH));
        overlay = ov;
        sv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        g.post(() -> {
            View c = g.getChildAt(0);
            if (c != null) c.requestFocus();
        });
    }

    private void closeAllApps() {
        if (overlay == null) return;
        root.removeView(overlay);
        overlay = null;
        sv.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (allAppsTile != null) allAppsTile.requestFocus();
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    // Icone "grille" (9 carres) pour la tuile Toutes les applis
    private static class GridIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        GridIcon(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float s = Math.min(w, h) * 0.17f;
            float gap = s * 0.35f;
            float total = 3 * s + 2 * gap;
            float x0 = (w - total) / 2f;
            float y0 = (h - total) / 2f;
            p.setColor(0xE6FFFFFF);
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    float x = x0 + i * (s + gap);
                    float y = y0 + j * (s + gap);
                    canvas.drawRoundRect(x, y, x + s, y + s, s * 0.25f, s * 0.25f, p);
                }
            }
        }
    }

    // Petit logo (montagnes + soleil) dessine par le code
    private static class Logo extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Logo(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            p.setColor(0xF2FFFFFF);
            canvas.drawCircle(0.70f * w, 0.30f * h, 0.085f * w, p);
            Path a = new Path();
            a.moveTo(0.18f * w, 0.74f * h);
            a.lineTo(0.42f * w, 0.36f * h);
            a.lineTo(0.64f * w, 0.74f * h);
            a.close();
            canvas.drawPath(a, p);
            p.setColor(0xCCFFFFFF);
            Path b = new Path();
            b.moveTo(0.48f * w, 0.74f * h);
            b.lineTo(0.68f * w, 0.50f * h);
            b.lineTo(0.84f * w, 0.74f * h);
            b.close();
            canvas.drawPath(b, p);
        }
    }

    // Fond : montagnes + lac avec reflet, dessine par le code, plusieurs ambiances
    private static class Wallpaper extends View {
        private static final String[] NAMES = {"Lac", "Coucher de soleil", "Nuit", "Brume rose", "Forêt"};

        // ciel haut, ciel bas, lointain, montagne, neige, droite, collines, brume, eau haut, eau bas, etoiles
        private static final int[][] T = {
            {0xFFA6D8DC, 0xFF6FB1B8, 0xFF9CCBD0, 0xFF5F9FA7, 0xAAEAF6F6, 0xFF78B0B6, 0xFF4A8A90, 0x99DDF1F2, 0x22FFFFFF, 0xDD06222A, 0},
            {0xFF3B2A6B, 0xFFF29A5B, 0xFFC9708A, 0xFF7A3F6B, 0xAAFFD9B5, 0xFF9A5476, 0xFF4A2A55, 0x99FFC9A0, 0x22FFFFFF, 0xDD1A0B24, 0},
            {0xFF050816, 0xFF1B2A55, 0xFF223566, 0xFF16224A, 0x88B8C8F0, 0xFF1C2B5A, 0xFF0C1330, 0x553A4F8A, 0x11FFFFFF, 0xEE02040C, 1},
            {0xFF6C7FD8, 0xFFF5B7C8, 0xFFC3A7D8, 0xFF8D86C9, 0xAAFFFFFF, 0xFFA897D2, 0xFF5E5AA0, 0x99FFE3EE, 0x22FFFFFF, 0xDD1A1738, 0},
            {0xFFBFE3C8, 0xFF7FB89A, 0xFF8FC1A3, 0xFF3F7F63, 0x88EFFFF4, 0xFF58977A, 0xFF1F4F3E, 0x99DFF5E7, 0x22FFFFFF, 0xDD052018, 0},
        };

        private static final float[] FAR = {0, .62f, .10f, .48f, .18f, .56f, .28f, .40f, .40f, .52f,
                .55f, .34f, .66f, .48f, .78f, .38f, .90f, .50f, 1, .46f, 1, 1, 0, 1};
        private static final float[] MAIN = {.10f, 1, .22f, .66f, .29f, .52f, .345f, .30f, .375f, .20f,
                .40f, .29f, .44f, .27f, .50f, .46f, .58f, .66f, .72f, 1};
        private static final float[] SNOW = {.29f, .52f, .345f, .30f, .375f, .20f, .40f, .29f, .44f, .27f,
                .50f, .46f, .43f, .42f, .385f, .36f, .345f, .44f, .32f, .48f};
        private static final float[] RIGHT = {.60f, 1, .72f, .56f, .78f, .38f, .835f, .56f, .96f, 1};
        private static final float[] HILLS = {0, .86f, .12f, .80f, .30f, .90f, .48f, .95f, .70f, .90f,
                .88f, .82f, 1, .86f, 1, 1, 0, 1};

        private int theme;
        private int vw, vh, horizon;
        private Bitmap scene;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint water = new Paint();

        Wallpaper(Context c) {
            super(c);
        }

        static int count() {
            return T.length;
        }

        static String name(int i) {
            return NAMES[i];
        }

        int getTheme() {
            return theme;
        }

        void setTheme(int t) {
            theme = ((t % T.length) + T.length) % T.length;
            render();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            vw = w;
            vh = h;
            horizon = h / 2;
            render();
        }

        private void render() {
            if (vw <= 0 || vh <= 0) return;
            int[] t = T[theme];
            scene = Bitmap.createBitmap(vw, horizon, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(scene);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setShader(new LinearGradient(0, 0, 0, horizon, t[0], t[1], Shader.TileMode.CLAMP));
            c.drawRect(0, 0, vw, horizon, sp);
            sp.setShader(null);
            if (t[10] == 1) {
                Random r = new Random(7);
                for (int i = 0; i < 110; i++) {
                    float x = r.nextFloat() * vw;
                    float y = r.nextFloat() * horizon * 0.7f;
                    sp.setColor(Color.argb(120 + r.nextInt(120), 255, 255, 255));
                    c.drawCircle(x, y, 1f + r.nextFloat() * 1.6f, sp);
                }
            }
            poly(c, sp, t[2], vw, horizon, FAR);
            poly(c, sp, t[3], vw, horizon, MAIN);
            poly(c, sp, t[4], vw, horizon, SNOW);
            poly(c, sp, t[5], vw, horizon, RIGHT);
            poly(c, sp, t[6], vw, horizon, HILLS);
            sp.setShader(new LinearGradient(0, horizon * 0.6f, 0, horizon, t[7] & 0x00FFFFFF, t[7],
                    Shader.TileMode.CLAMP));
            c.drawRect(0, horizon * 0.6f, vw, horizon, sp);

            water.setShader(new LinearGradient(0, horizon, 0, vh, t[8], t[9], Shader.TileMode.CLAMP));
        }

        private void poly(Canvas c, Paint sp, int color, int w, int h, float[] pts) {
            Path path = new Path();
            path.moveTo(pts[0] * w, pts[1] * h);
            for (int i = 2; i < pts.length; i += 2) path.lineTo(pts[i] * w, pts[i + 1] * h);
            path.close();
            sp.setColor(color);
            c.drawPath(path, sp);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (scene == null) return;
            canvas.drawBitmap(scene, 0, 0, null);
            canvas.save();
            canvas.translate(0, 2 * horizon);
            canvas.scale(1, -1);
            p.setAlpha(150);
            canvas.drawBitmap(scene, 0, 0, p);
            canvas.restore();
            p.setAlpha(255);
            canvas.drawRect(0, horizon, getWidth(), getHeight(), water);
        }
    }
}
