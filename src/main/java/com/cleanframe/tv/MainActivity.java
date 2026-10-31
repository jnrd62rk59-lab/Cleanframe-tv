package com.cleanframe.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
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
    private float density;
    private LinearLayout col;
    private View firstTile;
    private List<ResolveInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        density = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.addView(new Wallpaper(this), new FrameLayout.LayoutParams(MATCH, MATCH));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setFillViewport(true);

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(dp(40), 0, dp(40), dp(40));

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
        // Ecran d'accueil : on ne quitte pas.
    }

    private void build() {
        col.removeAllViews();
        firstTile = null;
        apps = allApps();

        int screenH = getResources().getDisplayMetrics().heightPixels;

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

        col.addView(top, new LinearLayout.LayoutParams(MATCH, (int) (screenH * 0.40f)));

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

        // Rangee 3 : toutes les applis
        TextView t = label("Toutes les applis", 18, 0xE6FFFFFF, true);
        t.setShadowLayer(dp(4), 0, dp(1), 0x66000000);
        t.setPadding(dp(8), dp(28), 0, 0);
        col.addView(t);

        LinearLayout r3 = centerRow();
        for (ResolveInfo r : apps) {
            final ActivityInfo ai = r.activityInfo;
            final Intent in = intentFor(ai);
            r3.addView(iconTile(r.loadLabel(pm).toString(), ai, false, () -> launch(in), 132, 74, 13));
        }

        final View ft = firstTile;
        if (ft != null) ft.post(() -> ft.requestFocus());
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
            row.addView(iconTile(f[0], ai, false, () -> launch(fin), 172, 98, 15));
        } else {
            final String pkg = f[2];
            TextView tv = label(f[0], 22, Color.WHITE, true);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new FrameLayout.LayoutParams(MATCH, MATCH));
            row.addView(tile("Non installé", tv, true,
                    () -> launch(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))),
                    172, 98, 15));
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
        hs.setPadding(0, dp(14), 0, dp(14));

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
        return tile(caption, tv, dim, action, 140, 64, 13);
    }

    private View tile(String caption, View content, boolean dim, final Runnable action, int wDp, int hDp, int capSp) {
        final LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setFocusable(true);
        wrap.setClickable(true);

        final FrameLayout card = new FrameLayout(this);
        card.setBackground(cardBg(false));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(16));
            }
        });
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
        lp.setMargins(dp(10), 0, dp(10), 0);
        wrap.setLayoutParams(lp);

        wrap.setOnClickListener(v -> action.run());
        wrap.setOnFocusChangeListener((v, has) -> {
            card.setBackground(cardBg(has));
            cap.setTextColor(has ? Color.WHITE : 0xCCFFFFFF);
            float s = has ? 1.12f : 1f;
            wrap.animate().scaleX(s).scaleY(s).setDuration(140).start();
            wrap.setTranslationZ(has ? dp(12) : 0);
        });

        if (firstTile == null) firstTile = wrap;
        return wrap;
    }

    private GradientDrawable cardBg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(focused ? 0x66FFFFFF : 0x44FFFFFF);
        if (focused) g.setStroke(dp(3), Color.WHITE);
        return g;
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

    // Fond : montagnes + lac avec reflet, dessine par le code
    private static class Wallpaper extends View {
        private static final float[] FAR = {0, .62f, .10f, .48f, .18f, .56f, .28f, .40f, .40f, .52f,
                .55f, .34f, .66f, .48f, .78f, .38f, .90f, .50f, 1, .46f, 1, 1, 0, 1};
        private static final float[] MAIN = {.10f, 1, .22f, .66f, .29f, .52f, .345f, .30f, .375f, .20f,
                .40f, .29f, .44f, .27f, .50f, .46f, .58f, .66f, .72f, 1};
        private static final float[] SNOW = {.29f, .52f, .345f, .30f, .375f, .20f, .40f, .29f, .44f, .27f,
                .50f, .46f, .43f, .42f, .385f, .36f, .345f, .44f, .32f, .48f};
        private static final float[] RIGHT = {.60f, 1, .72f, .56f, .78f, .38f, .835f, .56f, .96f, 1};
        private static final float[] HILLS = {0, .86f, .12f, .80f, .30f, .90f, .48f, .95f, .70f, .90f,
                .88f, .82f, 1, .86f, 1, 1, 0, 1};

        private Bitmap scene;
        private int horizon;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint water = new Paint();

        Wallpaper(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w <= 0 || h <= 0) return;
            horizon = h / 2;
            scene = Bitmap.createBitmap(w, horizon, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(scene);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setShader(new LinearGradient(0, 0, 0, horizon, 0xFFA6D8DC, 0xFF6FB1B8, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, horizon, sp);
            sp.setShader(null);
            poly(c, sp, 0xFF9CCBD0, w, horizon, FAR);
            poly(c, sp, 0xFF5F9FA7, w, horizon, MAIN);
            poly(c, sp, 0xAAEAF6F6, w, horizon, SNOW);
            poly(c, sp, 0xFF78B0B6, w, horizon, RIGHT);
            poly(c, sp, 0xFF4A8A90, w, horizon, HILLS);
            sp.setShader(new LinearGradient(0, horizon * 0.6f, 0, horizon, 0x00FFFFFF, 0x99DDF1F2,
                    Shader.TileMode.CLAMP));
            c.drawRect(0, horizon * 0.6f, w, horizon, sp);

            water.setShader(new LinearGradient(0, horizon, 0, h, 0x22FFFFFF, 0xDD06222A, Shader.TileMode.CLAMP));
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
