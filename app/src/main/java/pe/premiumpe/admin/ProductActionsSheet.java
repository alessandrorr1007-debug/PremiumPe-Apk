package pe.premiumpe.admin;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import org.json.JSONObject;
import java.util.Locale;
import static pe.premiumpe.admin.Catalog.*;
import static pe.premiumpe.admin.UiActivity.*;

final class ProductActionsSheet {
    private ProductActionsSheet() {}

    static void show(MainActivity app, JSONObject product) {
        String name = text(product, "name");
        boolean isAvailable = product.optBoolean("available");
        boolean isVisible = product.optBoolean("visible");
        boolean isFeatured = product.optBoolean("featured");

        Dialog dialog = new Dialog(app, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout backdrop = new FrameLayout(app);
        backdrop.setBackgroundColor(0x00000000);

        LinearLayout sheet = app.column();
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(BG);
        float topR = app.dp(24);
        sheetBg.setCornerRadii(new float[]{topR, topR, topR, topR, 0f, 0f, 0f, 0f});
        sheet.setBackground(sheetBg);
        sheet.setElevation(app.dp(16));
        sheet.setClickable(true);
        sheet.setFocusable(true);

        // Constrain width on tablets while keeping full width on phones
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(-1, -2);
        sheetLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sheet.setLayoutParams(sheetLp);
        app.constrainMaxWidth(sheet, 540);

        // Apply safe-area bottom/side insets to sheet without clipping backdrop
        if (Build.VERSION.SDK_INT >= 30) {
            backdrop.setOnApplyWindowInsetsListener((v, insets) -> {
                var sys = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                sheet.setPadding(0, 0, 0, sys.bottom);
                backdrop.setPadding(sys.left, sys.top, sys.right, 0);
                return insets;
            });
        }

        final boolean[] closing = {false};
        Runnable animateOut = () -> {
            if (closing[0]) return;
            closing[0] = true;
            float dist = sheet.getHeight() > 0 ? sheet.getHeight() : app.dp(420);
            backdrop.animate().alpha(0f).setDuration(190).start();
            sheet.animate()
                .translationY(dist)
                .setDuration(210)
                .setInterpolator(new AccelerateInterpolator(1.3f))
                .withEndAction(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                })
                .start();
        };

        backdrop.setOnClickListener(v -> animateOut.run());
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                animateOut.run();
                return true;
            }
            return false;
        });

        // Drag handle + Header container (supports swipe down)
        LinearLayout topArea = app.column();
        app.pad(topArea, 16, 10, 16, 8);

        View handle = new View(app);
        handle.setBackground(app.rounded(0xFFC2B5AC, 3, 0));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(app.dp(40), app.dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        topArea.addView(handle, handleLp);
        app.gap(topArea, 12);

        // Header row: Thumbnail + Name/Subtitle + Close X button
        LinearLayout header = app.row();
        String letter = name.isEmpty() ? "P" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        FrameLayout thumbBox = new FrameLayout(app);
        thumbBox.setBackground(app.rounded(SOFT, 12, LINE));
        thumbBox.setClipToOutline(true);
        thumbBox.setLayoutParams(new LinearLayout.LayoutParams(app.dp(48), app.dp(48)));
        TextView monogram = app.monogram(letter, 48, SOFT, TERRACOTTA);
        thumbBox.addView(monogram, new FrameLayout.LayoutParams(-1, -1));
        ImageView thumbImg = new ImageView(app);
        thumbImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbImg.setContentDescription("Imagen de " + name);
        thumbBox.addView(thumbImg, new FrameLayout.LayoutParams(-1, -1));
        app.loadRemoteImage(thumbImg, monogram, text(product, "image_url"));
        header.addView(thumbBox);
        app.gap(header, 12);

        LinearLayout titleCol = app.column();
        TextView nameTv = app.text(name, 17, INK, true);
        nameTv.setMaxLines(2);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        app.add(titleCol, nameTv);
        app.gap(titleCol, 2);
        app.add(titleCol, app.text("Administrar producto", 12, MUTED, false));
        app.weight(header, titleCol);
        app.gap(header, 8);

        ImageButton closeBtn = new ImageButton(app);
        closeBtn.setContentDescription("Cerrar menú");
        closeBtn.setImageDrawable(app.tintedIcon(R.drawable.ic_close, INK));
        closeBtn.setBackground(app.ripple(0xFFEAE0D7, 18, 0, 0x229B3B24));
        closeBtn.setScaleType(ImageView.ScaleType.CENTER);
        closeBtn.setMinimumWidth(app.dp(44));
        closeBtn.setMinimumHeight(app.dp(44));
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(app.dp(38), app.dp(38)));
        closeBtn.setOnClickListener(v -> animateOut.run());
        header.addView(closeBtn);

        topArea.addView(header, -1, -2);
        sheet.addView(topArea, -1, -2);

        final int touchSlop = ViewConfiguration.get(app).getScaledTouchSlop();
        final int dismissThreshold = app.dp(88);

        // Scrollable action list capped cleanly at 68% of screen height with swipe-down at top
        ScrollView scroll = new ScrollView(app) {
            private float downRawY;
            private boolean draggingSheet;

            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxH = Math.round(getResources().getDisplayMetrics().heightPixels * 0.68f);
                int cappedSpec = MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, cappedSpec);
            }

            @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        downRawY = ev.getRawY();
                        draggingSheet = false;
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        float dy = ev.getRawY() - downRawY;
                        if (getScrollY() == 0 && dy > touchSlop) {
                            draggingSheet = true;
                            downRawY = ev.getRawY();
                            return true;
                        }
                    }
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override public boolean onTouchEvent(MotionEvent ev) {
                if (!draggingSheet) return super.onTouchEvent(ev);
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_MOVE -> {
                        sheet.setTranslationY(Math.max(0f, ev.getRawY() - downRawY));
                        return true;
                    }
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (sheet.getTranslationY() > dismissThreshold) animateOut.run();
                        else sheet.animate().translationY(0f).setDuration(180).start();
                        draggingSheet = false;
                        return true;
                    }
                    default -> { return true; }
                }
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout list = app.column();
        app.pad(list, 16, 4, 16, 14);

        // Category 1: EDICIÓN
        sectionHeader(app, list, R.drawable.ic_edit, "EDICIÓN");
        actionItem(app, list, R.drawable.ic_edit_box, "Editar ficha completa", false, () ->
            closeThen(dialog, sheet, backdrop, closing, () -> app.productEditor(product)));
        actionItem(app, list, R.drawable.ic_tag_outline, "Cambiar precio", false, () ->
            closeThen(dialog, sheet, backdrop, closing, () -> app.quickPrice(product)));

        app.gap(list, 6);

        // Category 2: VISIBILIDAD Y ESTADO
        sectionHeader(app, list, R.drawable.ic_layers, "VISIBILIDAD Y ESTADO");
        actionItem(app, list, R.drawable.ic_box_outline,
            isAvailable ? "Marcar agotado" : "Marcar disponible", false, () ->
                closeThen(dialog, sheet, backdrop, closing,
                    () -> app.patchProduct(product, "available", !isAvailable)));
        actionItem(app, list, isVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye,
            isVisible ? "Ocultar de la tienda" : "Mostrar en la tienda", false, () ->
                closeThen(dialog, sheet, backdrop, closing,
                    () -> app.patchProduct(product, "visible", !isVisible)));
        actionItem(app, list, R.drawable.ic_star_outline,
            isFeatured ? "Quitar destacado" : "Agregar destacado", false, () ->
                closeThen(dialog, sheet, backdrop, closing,
                    () -> app.patchProduct(product, "featured", !isFeatured)));

        app.gap(list, 6);

        // Category 3: OTRAS ACCIONES
        sectionHeader(app, list, R.drawable.ic_apps_grid, "OTRAS ACCIONES");
        actionItem(app, list, R.drawable.ic_copy, "Duplicar como oculto", false, () ->
            closeThen(dialog, sheet, backdrop, closing,
                () -> app.mutate(() -> app.api.change("POST", PRODUCTS, copyProduct(product), 1),
                    "Copia creada como oculta.")));
        actionItem(app, list, R.drawable.ic_trash_outline, "Eliminar producto", true, () ->
            closeThen(dialog, sheet, backdrop, closing,
                () -> app.confirm(
                    "¿Eliminar «" + name + "»? Puede afectar a sus promociones.",
                    () -> app.mutate(() -> app.api.change("DELETE", where(PRODUCTS, id(product)), null, 1),
                        "Producto eliminado."))));

        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        sheet.addView(scroll, -1, -2);

        // Swipe down to dismiss on topArea
        attachSwipeToDismiss(app, topArea, sheet, animateOut);

        backdrop.addView(sheet);
        dialog.setContentView(backdrop, new ViewGroup.LayoutParams(-1, -1));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            window.setDimAmount(0f);
        }

        // Start off-screen and animate smoothly in
        backdrop.setBackgroundColor(0x662E1610);
        backdrop.setAlpha(0f);
        sheet.setVisibility(View.INVISIBLE);
        dialog.show();

        sheet.post(() -> {
            float startY = sheet.getHeight() > 0 ? sheet.getHeight() : app.dp(420);
            sheet.setTranslationY(startY);
            sheet.setVisibility(View.VISIBLE);
            backdrop.animate().alpha(1f).setDuration(220).start();
            sheet.animate()
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
        });
    }

    private static void closeThen(Dialog dialog, View sheet, View backdrop, boolean[] closing, Runnable action) {
        if (closing[0]) return;
        closing[0] = true;
        float dist = sheet.getHeight() > 0 ? sheet.getHeight() : sheet.getContext().getResources().getDisplayMetrics().density * 420f;
        backdrop.animate().alpha(0f).setDuration(150).start();
        sheet.animate()
            .translationY(dist)
            .setDuration(170)
            .setInterpolator(new AccelerateInterpolator(1.2f))
            .withEndAction(() -> {
                if (dialog.isShowing()) dialog.dismiss();
                action.run();
            })
            .start();
    }

    private static void sectionHeader(MainActivity app, LinearLayout parent, int iconRes, String title) {
        LinearLayout row = app.row();
        app.pad(row, 2, 4, 2, 6);
        row.addView(app.iconView(iconRes, 15, TERRACOTTA));
        app.gap(row, 7);
        TextView label = app.text(title, 11, TERRACOTTA, true);
        label.setLetterSpacing(0.06f);
        row.addView(label);
        app.gap(row, 10);

        View divider = new View(app);
        divider.setBackgroundColor(0xFFE0D4CA);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(0, app.dp(1), 1f);
        divLp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(divider, divLp);

        app.add(parent, row);
    }

    private static void actionItem(MainActivity app, LinearLayout parent, int iconRes, String label,
                                   boolean danger, Runnable onClick) {
        int bg = danger ? ROCOTO_SOFT : PAPER;
        int stroke = danger ? 0x44B33022 : LINE;
        int rippleColor = danger ? 0x22B33022 : 0x189B3B24;
        int iconTint = danger ? ROCOTO : TERRACOTTA;
        int textColor = danger ? ROCOTO : INK;
        int arrowTint = danger ? ROCOTO : MUTED;

        LinearLayout row = app.row();
        row.setBackground(app.ripple(bg, 13, stroke, rippleColor));
        row.setElevation(danger ? 0f : app.dp(1));
        row.setMinimumHeight(app.dp(46));
        app.pad(row, 12, 9, 10, 9);

        row.addView(app.iconView(iconRes, 19, iconTint));
        app.gap(row, 12);
        app.weight(row, app.text(label, 14, textColor, danger));
        row.addView(app.iconView(R.drawable.ic_chevron_right, 18, arrowTint));

        row.setOnClickListener(v -> onClick.run());
        app.add(parent, row);
        app.gap(parent, 6);
    }

    private static void attachSwipeToDismiss(MainActivity app, View handleArea, View sheet, Runnable dismiss) {
        final float[] startRawY = {0f};
        final float[] lastDelta = {0f};
        final int dismissThreshold = app.dp(88);

        handleArea.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    startRawY[0] = event.getRawY();
                    lastDelta[0] = 0f;
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    float dy = Math.max(0f, event.getRawY() - startRawY[0]);
                    lastDelta[0] = dy;
                    sheet.setTranslationY(dy);
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (lastDelta[0] > dismissThreshold) {
                        dismiss.run();
                    } else {
                        sheet.animate().translationY(0f).setDuration(180).start();
                    }
                    return true;
                }
                default -> { return false; }
            }
        });
    }
}
