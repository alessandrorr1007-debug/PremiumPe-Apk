package pe.premiumpe.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.*;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class UiActivity extends Activity {
    static final int BG = 0xFFF8F5EF;
    static final int PAPER = 0xFFFFFFFF;
    static final int INK = 0xFF2E1610;
    static final int MUTED = 0xFF715950;
    static final int TERRACOTTA = 0xFF9B3B24;
    static final int TERRACOTTA_DARK = 0xFF7E2C18;
    static final int AJI = TERRACOTTA;
    static final int RED = TERRACOTTA;
    static final int LINE = 0xFFE6DAD0;
    static final int SOFT = 0xFFF2E5DC;
    static final int GREEN = 0xFF1A7F45;
    static final int GREEN_SOFT = 0xFFDCF2E4;
    static final int AMBER = 0xFF9E6300;
    static final int AMBER_SOFT = 0xFFFCEBC4;
    static final int ROCOTO = 0xFFB33022;
    static final int ROCOTO_SOFT = 0xFFFBE2DF;
    static final int NEUTRAL_SOFT = 0xFFECE5DF;
    static final int GOLD = 0xFFFFD379;

    private static final ExecutorService IMAGE_POOL = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> IMAGE_CACHE = new LruCache<>(32);

    FrameLayout screenFrame;
    LinearLayout root;
    LinearLayout body;
    LinearLayout contentWrap;
    ScrollView scroll;
    private FrameLayout loadingOverlay;
    private TextView loadingLabel;

    int dp(float amount) { return Math.round(amount * getResources().getDisplayMetrics().density); }

    LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) background.setStroke(dp(1), strokeColor);
        return background;
    }

    Drawable ripple(int color, int radiusDp, int strokeColor, int rippleColor) {
        GradientDrawable content = rounded(color, radiusDp, strokeColor);
        GradientDrawable mask = rounded(0xFFFFFFFF, radiusDp, 0);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    Drawable tintedIcon(int resId, int color) {
        Drawable d = getDrawable(resId);
        if (d == null) return null;
        Drawable copy = d.mutate();
        copy.setTint(color);
        return copy;
    }

    ImageView iconView(int resId, int sizeDp, int tintColor) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(tintedIcon(resId, tintColor));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        iv.setLayoutParams(lp);
        return iv;
    }

    FrameLayout iconBadge(int resId, int boxDp, int iconDp, int bgColor, int iconColor, int radiusDp) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(rounded(bgColor, radiusDp, 0));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(boxDp), dp(boxDp)));
        ImageView iv = iconView(resId, iconDp, iconColor);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp));
        lp.gravity = Gravity.CENTER;
        box.addView(iv, lp);
        return box;
    }

    ImageButton topIconButton(int resId, String description, Runnable action) {
        ImageButton btn = new ImageButton(this);
        btn.setContentDescription(description);
        btn.setImageDrawable(tintedIcon(resId, PAPER));
        btn.setBackground(ripple(0x00000000, 24, 0, 0x33FFFFFF));
        btn.setScaleType(ImageView.ScaleType.CENTER);
        btn.setMinimumWidth(dp(44));
        btn.setMinimumHeight(dp(44));
        btn.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    void pad(View view, int left, int top, int right, int bottom) {
        view.setPadding(dp(left), dp(top), dp(right), dp(bottom));
    }

    void gap(LinearLayout into, int sizeDp) {
        int horizontal = into.getOrientation() == LinearLayout.HORIZONTAL ? dp(sizeDp) : 1;
        int vertical = into.getOrientation() == LinearLayout.HORIZONTAL ? 1 : dp(sizeDp);
        into.addView(new View(this), horizontal, vertical);
    }

    void add(LinearLayout into, View child) {
        if (into.getOrientation() == LinearLayout.HORIZONTAL) into.addView(child, -2, -2);
        else into.addView(child, -1, -2);
    }

    void weight(LinearLayout into, View child) {
        into.addView(child, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    TextView text(String label, int sizeSp, int color, boolean strong) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        if (strong) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    void heading(LinearLayout into, String label) {
        add(into, text(label, 16, INK, true));
        gap(into, 8);
    }

    void note(LinearLayout into, String label) { add(into, text(label, 13, MUTED, false)); }

    TextView badge(String label, int bgColor, int textColor, int borderColor) {
        TextView view = text(label, 11, textColor, true);
        view.setBackground(rounded(bgColor, 8, borderColor));
        pad(view, 8, 3, 8, 3);
        return view;
    }

    TextView monogram(String letter, int sizeDp, int bgColor, int textColor) {
        TextView view = text(letter, Math.max(12, sizeDp / 2), textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(bgColor, Math.max(10, sizeDp / 4), 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        view.setLayoutParams(lp);
        return view;
    }

    LinearLayout card(LinearLayout parent) {
        LinearLayout view = column();
        view.setBackground(rounded(PAPER, 18, LINE));
        view.setElevation(dp(2));
        pad(view, 14, 14, 14, 14);
        add(parent, view);
        gap(parent, 10);
        return view;
    }

    Button button(String label, Runnable action, boolean primary) {
        return iconButton(label, 0, action, primary);
    }

    Button iconButton(String label, int iconRes, Runnable action, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? PAPER : INK);
        button.setStateListAnimator(null);
        button.setElevation(primary ? dp(2) : 0);
        button.setBackground(ripple(
            primary ? TERRACOTTA : SOFT,
            13,
            primary ? 0 : LINE,
            primary ? 0x33FFFFFF : 0x229B3B24
        ));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        pad(button, 14, 8, 14, 8);
        if (iconRes != 0) {
            Drawable d = tintedIcon(iconRes, primary ? PAPER : TERRACOTTA);
            if (d != null) {
                d.setBounds(0, 0, dp(18), dp(18));
                button.setCompoundDrawables(d, null, null, null);
                button.setCompoundDrawablePadding(dp(8));
            }
        }
        button.setOnClickListener(view -> action.run());
        return button;
    }

    Button dangerButton(String label, int iconRes, Runnable action) {
        Button button = iconButton(label, iconRes, action, false);
        button.setTextColor(ROCOTO);
        button.setBackground(ripple(ROCOTO_SOFT, 13, 0x33B33022, 0x22B33022));
        if (iconRes != 0) {
            Drawable d = tintedIcon(iconRes, ROCOTO);
            if (d != null) {
                d.setBounds(0, 0, dp(18), dp(18));
                button.setCompoundDrawables(d, null, null, null);
                button.setCompoundDrawablePadding(dp(8));
            }
        }
        return button;
    }

    EditText input(LinearLayout into, String label, String value, int type) {
        return iconInput(into, label, 0, value, "", type, false);
    }

    EditText iconInput(LinearLayout into, String label, int iconRes, String value, String hint, int type, boolean password) {
        if (label != null && !label.isEmpty()) {
            TextView title = text(label, 13, INK, true);
            add(into, title);
            gap(into, 5);
        }
        LinearLayout box = row();
        box.setBackground(rounded(PAPER, 13, LINE));
        box.setMinimumHeight(dp(48));
        pad(box, 12, 4, password ? 4 : 12, 4);

        if (iconRes != 0) {
            ImageView icon = iconView(iconRes, 18, MUTED);
            box.addView(icon);
            gap(box, 10);
        }

        EditText field = new EditText(this);
        field.setId(View.generateViewId());
        field.setBackground(null);
        field.setTextSize(15);
        field.setTextColor(INK);
        field.setHintTextColor(0xFF9C877E);
        if (hint != null && !hint.isEmpty()) field.setHint(hint);
        field.setInputType(type);
        boolean isMultiline = (type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (!isMultiline) {
            field.setSingleLine(true);
        } else {
            field.setMinLines(2);
            field.setGravity(Gravity.TOP | Gravity.START);
            pad(field, 0, 8, 0, 8);
        }
        field.setText(value);
        field.setMinHeight(dp(44));
        weight(box, field);

        if (password) {
            final boolean[] visible = {false};
            ImageButton toggle = new ImageButton(this);
            toggle.setContentDescription("Mostrar u ocultar contraseña");
            toggle.setImageDrawable(tintedIcon(R.drawable.ic_eye, MUTED));
            toggle.setBackground(ripple(0x00000000, 20, 0, 0x229B3B24));
            toggle.setMinimumWidth(dp(44));
            toggle.setMinimumHeight(dp(44));
            toggle.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
            toggle.setOnClickListener(v -> {
                visible[0] = !visible[0];
                int sel = field.getSelectionEnd();
                if (visible[0]) {
                    field.setTransformationMethod(null);
                    toggle.setImageDrawable(tintedIcon(R.drawable.ic_eye_off, TERRACOTTA));
                } else {
                    field.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    toggle.setImageDrawable(tintedIcon(R.drawable.ic_eye, MUTED));
                }
                if (sel >= 0) field.setSelection(Math.min(sel, field.length()));
            });
            box.addView(toggle);
        }

        add(into, box);
        gap(into, 10);
        return field;
    }

    Spinner choices(LinearLayout into, String label, String[] options, int selected, Runnable changed) {
        TextView title = text(label, 13, INK, true);
        add(into, title);
        gap(into, 5);
        LinearLayout wrap = row();
        wrap.setBackground(rounded(PAPER, 13, LINE));
        wrap.setMinimumHeight(dp(48));
        pad(wrap, 8, 0, 8, 0);
        Spinner spinner = new Spinner(this);
        spinner.setId(View.generateViewId());
        title.setLabelFor(spinner.getId());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, selected));
        spinner.setMinimumHeight(dp(48));
        weight(wrap, spinner);
        add(into, wrap);
        gap(into, 10);
        if (changed != null) spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) { changed.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        return spinner;
    }

    Switch toggle(LinearLayout into, String label, boolean value) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextSize(15);
        view.setTextColor(INK);
        view.setMinHeight(dp(48));
        view.setChecked(value);
        view.setThumbTintList(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{TERRACOTTA, 0xFFCCC0B8}
        ));
        view.setTrackTintList(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{0x669B3B24, 0x33715950}
        ));
        add(into, view);
        return view;
    }

    void watch(EditText input, Runnable action) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int s, int c, int a) {}
            @Override public void onTextChanged(CharSequence value, int s, int before, int count) { action.run(); }
            @Override public void afterTextChanged(Editable value) {}
        });
    }

    void loadRemoteImage(ImageView target, View fallback, String path) {
        if (path == null || path.trim().isEmpty()) {
            target.setTag(null);
            target.setVisibility(View.GONE);
            if (fallback != null) fallback.setVisibility(View.VISIBLE);
            return;
        }
        Bitmap cached = IMAGE_CACHE.get(path);
        if (cached != null) {
            target.setTag(path);
            target.setImageBitmap(cached);
            target.setVisibility(View.VISIBLE);
            if (fallback != null) fallback.setVisibility(View.GONE);
            return;
        }
        target.setTag(path);
        target.setVisibility(View.GONE);
        if (fallback != null) fallback.setVisibility(View.VISIBLE);
        IMAGE_POOL.execute(() -> {
            try {
                URL url = new URL(new URL(BuildConfig.STORE_URL), path);
                if (!url.getProtocol().equals("https") && !BuildConfig.DEBUG) return;
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);
                try (InputStream input = connection.getInputStream()) {
                    byte[] bytes = Supabase.read(input, 4 * 1024 * 1024);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    options.inSampleSize = Math.max(1, Math.max(options.outHeight, options.outWidth) / 480);
                    options.inJustDecodeBounds = false;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    if (bitmap != null) {
                        IMAGE_CACHE.put(path, bitmap);
                        runOnUiThread(() -> {
                            if (!isDestroyed() && path.equals(target.getTag())) {
                                target.setImageBitmap(bitmap);
                                target.setVisibility(View.VISIBLE);
                                if (fallback != null) fallback.setVisibility(View.GONE);
                            }
                        });
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) { /* Mantiene monograma o portada previa. */ }
        });
    }

    void constrainMaxWidth(View target, int maxWidthDp) {
        target.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
            View parent = (View) v.getParent();
            if (parent == null) return;
            int avail = parent.getWidth();
            int cap = dp(maxWidthDp);
            if (avail <= 0) return;
            int desired = avail > cap ? cap : ViewGroup.LayoutParams.MATCH_PARENT;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.width != desired) {
                lp.width = desired;
                if (lp instanceof LinearLayout.LayoutParams llp) llp.gravity = Gravity.CENTER_HORIZONTAL;
                v.post(() -> v.setLayoutParams(lp));
            }
        });
    }

    void pageShell(View topBar, boolean includeScroll) {
        pageShell(topBar, includeScroll, true);
    }

    void pageShell(View topBar, boolean includeScroll, boolean constrainBody) {
        screenFrame = new FrameLayout(this);
        screenFrame.setBackgroundColor(BG);

        root = column();
        root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);

        if (topBar != null) {
            root.addView(topBar, -1, -2);
        }

        if (includeScroll) {
            scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            contentWrap = column();
            contentWrap.setGravity(Gravity.CENTER_HORIZONTAL);
            body = column();
            pad(body, 16, 12, 16, 20);
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
            contentWrap.addView(body, bodyLp);
            if (constrainBody) constrainMaxWidth(body, 620);
            scroll.addView(contentWrap, new FrameLayout.LayoutParams(-1, -2));
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            body = column();
            root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        if (Build.VERSION.SDK_INT >= 30) {
            screenFrame.setOnApplyWindowInsetsListener((view, insets) -> {
                var sys = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                var ime = insets.getInsets(WindowInsets.Type.ime());
                int bottom = Math.max(sys.bottom, ime.bottom);
                if (topBar != null) {
                    topBar.setPadding(sys.left, sys.top, sys.right, 0);
                } else {
                    root.setPadding(sys.left, sys.top, sys.right, 0);
                }
                root.setPadding(
                    topBar != null ? sys.left : root.getPaddingLeft(),
                    topBar != null ? 0 : sys.top,
                    topBar != null ? sys.right : root.getPaddingRight(),
                    bottom
                );
                return insets;
            });
        }

        screenFrame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        // Loading overlay
        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(0x552E1610);
        loadingOverlay.setClickable(true);
        loadingOverlay.setFocusable(true);
        loadingOverlay.setVisibility(View.GONE);

        LinearLayout pill = row();
        pill.setBackground(rounded(PAPER, 18, LINE));
        pill.setElevation(dp(8));
        pad(pill, 20, 14, 22, 14);
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(TERRACOTTA));
        pill.addView(spinner, dp(24), dp(24));
        gap(pill, 12);
        loadingLabel = text("Cargando…", 14, INK, true);
        pill.addView(loadingLabel);
        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(-2, -2);
        pillLp.gravity = Gravity.CENTER;
        loadingOverlay.addView(pill, pillLp);
        screenFrame.addView(loadingOverlay, new FrameLayout.LayoutParams(-1, -1));

        setContentView(screenFrame);
    }

    void page(String title, String label) {
        LinearLayout topBar = editorTopBar(title, null);
        pageShell(topBar, true);
    }

    LinearLayout editorTopBar(String title, Runnable onBack) {
        LinearLayout bar = column();
        bar.setBackgroundColor(TERRACOTTA);
        LinearLayout inner = row();
        inner.setMinimumHeight(dp(56));
        pad(inner, 8, 6, 16, 6);
        if (onBack != null) {
            inner.addView(topIconButton(R.drawable.ic_back, "Volver", onBack));
            gap(inner, 4);
        } else {
            gap(inner, 8);
        }
        TextView titleView = text(title, 18, PAPER, true);
        weight(inner, titleView);
        bar.addView(inner, -1, -2);
        return bar;
    }

    void showLoading(String message) {
        if (loadingOverlay == null) return;
        loadingLabel.setText(message == null || message.isEmpty() ? "Procesando…" : message);
        loadingOverlay.setVisibility(View.VISIBLE);
    }

    void hideLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    void showSuccessModal(String title, String subtitle, Runnable onClose) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(rounded(PAPER, 24, 0));
        pad(box, 24, 26, 24, 22);

        FrameLayout checkBadge = iconBadge(R.drawable.ic_check_circle, 68, 40, GREEN_SOFT, GREEN, 34);
        box.addView(checkBadge);
        gap(box, 16);

        TextView titleView = text(title, 20, INK, true);
        titleView.setGravity(Gravity.CENTER);
        add(box, titleView);
        gap(box, 6);

        TextView subView = text(subtitle, 13, MUTED, false);
        subView.setGravity(Gravity.CENTER);
        add(box, subView);
        gap(box, 20);

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        Button closeBtn = button("Cerrar", () -> {
            dialog.dismiss();
            if (onClose != null) onClose.run();
        }, true);
        add(box, closeBtn);

        dialog.setView(box);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    View loginWaveHeader() {
        FrameLayout header = new FrameLayout(this);
        View waveBg = new View(this) {
            private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();

            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth(), h = getHeight();
                fillPaint.setShader(new LinearGradient(0, 0, w, h, TERRACOTTA_DARK, TERRACOTTA, Shader.TileMode.CLAMP));

                // Soft decorative cream wave at bottom
                accentPaint.setColor(0x28F8F5EF);
                path.reset();
                path.moveTo(0, 0);
                path.lineTo(w, 0);
                path.lineTo(w, h * 0.88f);
                path.cubicTo(w * 0.68f, h * 0.72f, w * 0.32f, h * 1.05f, 0, h * 0.82f);
                path.close();
                canvas.drawPath(path, accentPaint);

                path.reset();
                path.moveTo(0, 0);
                path.lineTo(w, 0);
                path.lineTo(w, h * 0.78f);
                path.cubicTo(w * 0.66f, h * 0.96f, w * 0.34f, h * 0.66f, 0, h * 0.90f);
                path.close();
                canvas.drawPath(path, fillPaint);
            }
        };
        header.addView(waveBg, new FrameLayout.LayoutParams(-1, dp(220)));

        LinearLayout center = column();
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        pad(center, 20, 28, 20, 36);

        FrameLayout logoBadge = iconBadge(R.drawable.ic_cart, 58, 30, 0x29FFFFFF, PAPER, 16);
        center.addView(logoBadge);
        gap(center, 12);

        TextView brand = text("PremiumPe", 26, PAPER, true);
        brand.setGravity(Gravity.CENTER);
        center.addView(brand);
        gap(center, 3);

        TextView sub = text("Panel de Administración", 13, 0xFFF3DDD4, false);
        sub.setGravity(Gravity.CENTER);
        center.addView(sub);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(-1, -2);
        centerLp.gravity = Gravity.CENTER;
        header.addView(center, centerLp);
        return header;
    }
}