package pe.premiumpe.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static pe.premiumpe.admin.Catalog.*;
import static pe.premiumpe.admin.Supabase.object;
import static pe.premiumpe.admin.UiActivity.*;

public final class MainActivity extends UiActivity {
    static final int LINE_TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    static final int MULTILINE = LINE_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
    static final int MONEY = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    static final int URL = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;

    private static final String PREFS_UI = "premiumpe_ui_prefs";
    private static final String PREF_REMEMBER_USER = "remembered_user";

    final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler clock = new Handler(Looper.getMainLooper());
    Supabase api;
    Editors.Editor editor;
    List<JSONObject> products = new ArrayList<>();
    List<JSONObject> offers = new ArrayList<>();
    JSONObject settings;
    boolean catalogLoaded;
    String catalogError, promotionsError, settingsError;
    private boolean busy, foreground, warned;
    private AlertDialog warning;

    int tab, category, status, sort;
    boolean showPromotions, reorderMode;
    String search = "";
    final Set<String> selected = new LinkedHashSet<>();
    LinearLayout results;
    private TextView loginError;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!BuildConfig.DEBUG) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        api = new Supabase(this);
        if (api.hasSession() && !api.idle()) {
            showHome();
            refresh(true);
        } else {
            api.clear();
            showLogin("");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        tick.run();
    }

    @Override protected void onPause() {
        foreground = false;
        clock.removeCallbacks(tick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        clock.removeCallbacks(tick);
        io.shutdown();
        super.onDestroy();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!foreground || isFinishing()) return;
            if (api.hasSession() && api.idle() && !busy) endSession("Sesión cerrada por inactividad.");
            else if (api.hasSession() && api.remainingSeconds() <= 120 && !warned && !busy) {
                warned = true;
                warning = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Inactividad detectada")
                    .setMessage("Tu sesión se cerrará al llegar a 30 minutos de inactividad.")
                    .setNegativeButton("Cerrar sesión", (d, w) -> endSession(""))
                    .setPositiveButton("Continuar", (d, w) -> recordActivity())
                    .setCancelable(false)
                    .show();
            }
            clock.postDelayed(this, 1000);
        }
    };

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && api.hasSession() && !api.idle()) recordActivity();
        return super.dispatchTouchEvent(event);
    }

    void recordActivity() {
        if (!api.idle()) {
            api.touch();
            warned = false;
        }
    }

    private void showLogin(String message) {
        editor = null;
        LinearLayout statusStrip = column();
        statusStrip.setBackgroundColor(TERRACOTTA_DARK);
        pageShell(statusStrip, true, false);
        pad(body, 0, 0, 0, 24);

        add(body, loginWaveHeader());

        LinearLayout formBox = column();
        pad(formBox, 22, 4, 22, 12);
        constrainMaxWidth(formBox, 460);

        TextView welcome = text("Bienvenido de nuevo", 22, INK, true);
        welcome.setGravity(Gravity.CENTER);
        add(formBox, welcome);
        gap(formBox, 4);

        TextView subtitle = text("Accede a tu panel de administración", 13, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        add(formBox, subtitle);
        gap(formBox, 18);

        if (!api.configured()) {
            TextView cfgNote = text("Configura Supabase en el archivo .env para habilitar el acceso.", 12, ROCOTO, true);
            cfgNote.setBackground(rounded(ROCOTO_SOFT, 12, 0x44B33022));
            pad(cfgNote, 12, 10, 12, 10);
            add(formBox, cfgNote);
            gap(formBox, 12);
        }

        SharedPreferences uiPrefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE);
        String remembered = uiPrefs.getString(PREF_REMEMBER_USER, "");

        EditText user = iconInput(formBox, "", R.drawable.ic_mail, remembered,
            "Usuario o correo electrónico", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false);
        user.setContentDescription("Usuario o correo administrativo");

        EditText pass = iconInput(formBox, "", R.drawable.ic_lock, "",
            "Contraseña", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true);
        pass.setContentDescription("Contraseña");

        LinearLayout optionsRow = row();
        CheckBox rememberCheck = new CheckBox(this);
        rememberCheck.setText("Recordarme");
        rememberCheck.setTextSize(13);
        rememberCheck.setTextColor(INK);
        rememberCheck.setButtonTintList(ColorStateList.valueOf(TERRACOTTA));
        rememberCheck.setChecked(!remembered.isEmpty());
        rememberCheck.setMinHeight(dp(44));
        weight(optionsRow, rememberCheck);

        TextView storeLink = text("Ver tienda pública ↗", 12, TERRACOTTA, true);
        storeLink.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        storeLink.setMinHeight(dp(44));
        pad(storeLink, 8, 4, 4, 4);
        storeLink.setOnClickListener(v -> openStore());
        optionsRow.addView(storeLink);
        add(formBox, optionsRow);
        gap(formBox, 6);

        loginError = text(message == null ? "" : message, 13, ROCOTO, true);
        loginError.setBackground(rounded(ROCOTO_SOFT, 10, 0x44B33022));
        pad(loginError, 12, 8, 12, 8);
        loginError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        loginError.setVisibility(message != null && !message.isEmpty() ? View.VISIBLE : View.GONE);
        add(formBox, loginError);
        gap(formBox, 10);

        Button enter = iconButton("Iniciar sesión", R.drawable.ic_logout, () -> {
            if (pass.length() == 0) {
                showLoginError("Ingresa tu contraseña.");
                return;
            }
            String account = user.getText().toString().trim();
            String secret = pass.getText().toString();
            if (rememberCheck.isChecked()) {
                uiPrefs.edit().putString(PREF_REMEMBER_USER, account).apply();
            } else {
                uiPrefs.edit().remove(PREF_REMEMBER_USER).apply();
            }
            hideKeyboard();
            perform("Iniciando sesión…", () -> api.login(account, secret), () -> {
                tab = 0;
                showPromotions = false;
                showHome();
                refresh(true);
            }, loginError);
        }, true);
        enter.setContentDescription("Entrar");
        enter.setEnabled(api.configured());
        add(formBox, enter);

        add(body, formBox);
    }

    private void showLoginError(String msg) {
        if (loginError == null) return;
        loginError.setText(msg);
        loginError.setVisibility(msg == null || msg.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void endSession(String reason) {
        if (warning != null) warning.dismiss();
        warned = false;
        perform("Cerrando sesión…", api::logout, () -> {
            products.clear();
            offers.clear();
            settings = null;
            selected.clear();
            catalogLoaded = false;
            catalogError = promotionsError = settingsError = null;
            showLogin(reason);
        }, null);
    }

    @FunctionalInterface interface Operation { void run() throws Exception; }

    void perform(Operation operation, Runnable done, TextView errorView) {
        perform("Procesando…", operation, done, errorView);
    }

    void perform(String loadingText, Operation operation, Runnable done, TextView errorView) {
        if (busy) return;
        busy = true;
        if (errorView != null) {
            errorView.setText("");
            if (errorView == loginError || (editor != null && errorView == editor.error)) {
                errorView.setVisibility(View.GONE);
            }
        }
        if (loadingText != null) showLoading(loadingText);
        List<View> disabled = new ArrayList<>();
        disableControls(root, disabled);
        io.execute(() -> {
            String problem = null;
            try { operation.run(); }
            catch (Exception error) {
                problem = error.getLocalizedMessage() == null
                    ? "No se pudo completar la operación."
                    : error.getLocalizedMessage();
            }
            String result = problem == null ? null : (problem.isEmpty() ? "No se pudo completar la operación." : problem);
            runOnUiThread(() -> {
                busy = false;
                if (isDestroyed()) return;
                hideLoading();
                disabled.forEach(view -> view.setEnabled(true));
                if (result == null) {
                    done.run();
                } else if (!api.hasSession() && (errorView == null || loginError != errorView)) {
                    products.clear();
                    offers.clear();
                    catalogLoaded = false;
                    settings = null;
                    selected.clear();
                    if (warning != null) warning.dismiss();
                    showLogin(result);
                } else if (errorView != null) {
                    errorView.setText(result);
                    if (errorView == loginError || (editor != null && errorView == editor.error)) {
                        errorView.setVisibility(View.VISIBLE);
                    }
                } else {
                    alert(result);
                }
            });
        });
    }

    private void disableControls(View view, List<View> disabled) {
        if (view.isEnabled()) {
            view.setEnabled(false);
            disabled.add(view);
        }
        if (view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) disableControls(group.getChildAt(i), disabled);
        }
    }

    void alert(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Aceptar", null).show();
    }

    void confirm(String message, Runnable action) {
        new AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage(message)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (d, w) -> action.run())
            .show();
    }

    void hideKeyboard() {
        View field = getCurrentFocus();
        if (field != null) getSystemService(InputMethodManager.class).hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    void notice(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private List<JSONObject> allRows(String query) throws Exception {
        List<JSONObject> rows = new ArrayList<>();
        for (int offset = 0; ; offset += 200) {
            var page = list(api.rows(query + "&limit=200&offset=" + offset));
            rows.addAll(page);
            if (page.size() < 200) return rows;
        }
    }

    void refresh() { refresh(true); }

    void refresh(boolean showOverlay) {
        perform(showOverlay ? "Actualizando datos…" : null, () -> {
            try {
                products = allRows("products?select=*&order=sort_order.asc,created_at.asc,id.asc");
                catalogLoaded = true;
                catalogError = null;
            } catch (Exception error) {
                if (!api.hasSession()) throw error;
                catalogError = error.getLocalizedMessage();
            }
            try {
                var rows = list(api.rows("settings?id=eq.1&select=*"));
                settings = rows.isEmpty() ? null : rows.get(0);
                settingsError = null;
            } catch (Exception error) {
                if (!api.hasSession()) throw error;
                settingsError = error.getLocalizedMessage();
            }
            try {
                offers = allRows("promotions?select=*&order=created_at.desc,id.asc");
                promotionsError = null;
            } catch (Exception error) {
                if (!api.hasSession()) throw error;
                promotionsError = error.getLocalizedMessage();
            }
        }, () -> {
            selected.retainAll(products.stream().map(Catalog::id).collect(java.util.stream.Collectors.toSet()));
            if (editor == null) showHome();
        }, null);
    }

    void start(Editors.Editor next) {
        hideKeyboard();
        editor = next;
        next.show();
    }

    void closeEditor() {
        hideKeyboard();
        editor = null;
        showHome();
    }

    void saved(String message) {
        closeEditor();
        refresh(false);
        showSuccessModal("Cambios guardados", "La información se actualizó correctamente.", null);
    }

    @Override public void onBackPressed() {
        if (busy) return;
        if (editor != null) editor.cancel();
        else if (tab != 0 && api.hasSession()) { tab = 0; showHome(); }
        else super.onBackPressed();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (editor instanceof Editors.Product product) product.photoResult(request, result, data);
    }

    private static final String[] NAV_LABELS = {"Resumen", "Productos", "Ajustes"};
    private static final int[] NAV_ICONS = {R.drawable.ic_home, R.drawable.ic_products, R.drawable.ic_settings};

    void showHome() {
        pageShell(homeTopBar(), true);

        if (catalogError != null) {
            TextView err = text("Productos · " + catalogError, 12, ROCOTO, true);
            err.setBackground(rounded(ROCOTO_SOFT, 12, 0x44B33022));
            pad(err, 12, 8, 12, 8);
            add(body, err);
            gap(body, 10);
        }

        switch (tab) {
            case 0 -> summary();
            case 1 -> ProductsPanel.render(this);
            case 2 -> settingsList();
            default -> throw new IllegalStateException();
        }

        add(root, bottomNavBar());
    }

    private LinearLayout homeTopBar() {
        LinearLayout bar = column();
        bar.setBackgroundColor(TERRACOTTA);
        LinearLayout inner = row();
        inner.setMinimumHeight(dp(56));
        pad(inner, 16, 6, 8, 6);

        inner.addView(iconView(R.drawable.ic_cart, 22, PAPER));
        gap(inner, 10);
        weight(inner, text("PremiumPe", 19, PAPER, true));

        // Bell button with alert indicator dot
        FrameLayout bellWrap = new FrameLayout(this);
        ImageButton bellBtn = topIconButton(R.drawable.ic_bell, "Alertas de tienda", this::showAlertsDialog);
        bellWrap.addView(bellBtn);
        if (hasPendingAlerts()) {
            View dot = new View(this);
            dot.setBackground(rounded(GOLD, 4, TERRACOTTA));
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(9), dp(9));
            dotLp.gravity = Gravity.TOP | Gravity.END;
            dotLp.setMargins(0, dp(9), dp(9), 0);
            bellWrap.addView(dot, dotLp);
        }
        inner.addView(bellWrap);

        inner.addView(topIconButton(R.drawable.ic_logout, "Cerrar sesión", () ->
            new AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Deseas salir del panel administrativo?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salir", (d, w) -> endSession(""))
                .show()
        ));

        bar.addView(inner, -1, -2);
        return bar;
    }

    private LinearLayout bottomNavBar() {
        LinearLayout wrapper = column();
        wrapper.setBackgroundColor(PAPER);
        wrapper.setElevation(dp(8));

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        wrapper.addView(divider, -1, dp(1));

        LinearLayout nav = row();
        pad(nav, 10, 7, 10, 8);
        for (int index = 0; index < NAV_LABELS.length; index++) {
            int destination = index;
            boolean active = (index == tab);

            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(dp(48));
            item.setBackground(ripple(active ? TERRACOTTA : 0x00000000, 14, 0, active ? 0x33FFFFFF : 0x189B3B24));
            pad(item, 6, 7, 6, 7);

            item.addView(iconView(NAV_ICONS[index], 20, active ? PAPER : MUTED));
            gap(item, 2);
            TextView label = text(NAV_LABELS[index], 11, active ? PAPER : INK, active);
            label.setMaxLines(1);
            item.addView(label);

            item.setContentDescription(NAV_LABELS[index]);
            item.setSelected(active);
            item.setOnClickListener(v -> {
                hideKeyboard();
                if (tab == 1 && destination == 1 && showPromotions) {
                    showPromotions = false;
                }
                tab = destination;
                showHome();
            });
            weight(nav, item);
            if (index < NAV_LABELS.length - 1) gap(nav, 6);
        }
        wrapper.addView(nav, -1, -2);
        return wrapper;
    }

    private boolean hasPendingAlerts() {
        if (!catalogLoaded) return false;
        long covers = products.stream().filter(p -> Catalog.text(p, "image_url").trim().isEmpty()).count();
        long benefits = products.stream().filter(p -> !hasBenefits(p)).count();
        boolean missingPhone = settingsError == null && (settings == null ||
            Catalog.text(settings, "whatsapp_number").replaceAll("[^0-9]", "").length() < 9);
        return missingPhone || covers > 0 || benefits > 0;
    }

    private void showAlertsDialog() {
        if (!catalogLoaded) {
            alert("Cargando información del catálogo…");
            return;
        }
        long covers = products.stream().filter(p -> Catalog.text(p, "image_url").trim().isEmpty()).count();
        long benefits = products.stream().filter(p -> !hasBenefits(p)).count();
        long outOfStock = count("available", false);
        boolean missingPhone = settingsError == null && (settings == null ||
            Catalog.text(settings, "whatsapp_number").replaceAll("[^0-9]", "").length() < 9);

        LinearLayout box = column();
        pad(box, 18, 12, 18, 8);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Alertas de configuración")
            .setView(box).setPositiveButton("Cerrar", null).create();

        if (!missingPhone && covers == 0 && benefits == 0 && outOfStock == 0) {
            note(box, "Todo está en orden. Tu tienda tiene WhatsApp, portadas, beneficios y stock completo.");
        } else {
            if (missingPhone) {
                add(box, iconButton("Configurar WhatsApp", R.drawable.ic_whatsapp, () -> {
                    dialog.dismiss();
                    settingsEditor(Editors.SettingsSection.STORE_INFO, true);
                }, false));
                gap(box, 8);
            }
            if (outOfStock > 0) {
                add(box, iconButton(outOfStock + " productos agotados", R.drawable.ic_warning, () -> {
                    dialog.dismiss();
                    goProducts(2);
                }, false));
                gap(box, 8);
            }
            if (covers > 0) {
                add(box, iconButton(covers + " productos sin portada", R.drawable.ic_image, () -> {
                    dialog.dismiss();
                    goProducts(6);
                }, false));
                gap(box, 8);
            }
            if (benefits > 0) {
                add(box, iconButton(benefits + " productos sin beneficios", R.drawable.ic_text, () -> {
                    dialog.dismiss();
                    goProducts(7);
                }, false));
            }
        }
        dialog.show();
    }

    void openStore() {
        try {
            if (!BuildConfig.STORE_URL.startsWith("https://") && !BuildConfig.DEBUG) throw new IllegalArgumentException();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.STORE_URL)));
        } catch (Exception error) { alert("No se pudo abrir la tienda. Revisa la dirección configurada."); }
    }

    private void goProducts(int filter) {
        tab = 1;
        showPromotions = false;
        status = filter;
        category = sort = 0;
        reorderMode = false;
        search = "";
        showHome();
    }

    private void goPromotions() {
        tab = 1;
        showPromotions = true;
        showHome();
    }

    void productEditor(JSONObject product) {
        start(new Editors.Product(this, product));
    }

    void promotionEditor(JSONObject promotion) {
        if (!catalogLoaded || promotionsError != null) {
            alert("Actualiza productos y promociones antes de abrir el editor.");
            return;
        }
        start(new Editors.Promotion(this, promotion));
    }

    void settingsEditor(Editors.SettingsSection section, boolean whatsappOnly) {
        if (settingsError != null || (!catalogLoaded && settings == null)) {
            alert("Actualiza los datos de la tienda antes de editar.");
            return;
        }
        start(new Editors.Settings(this, settings, section, whatsappOnly));
    }

    private void summary() {
        // Greeting + refresh button
        LinearLayout greetRow = row();
        LinearLayout greetCol = column();
        add(greetCol, text("¡Hola, Administrador!", 21, INK, true));
        add(greetCol, text("Panel de control de tu tienda", 12, MUTED, false));
        weight(greetRow, greetCol);

        ImageButton refreshBtn = new ImageButton(this);
        refreshBtn.setContentDescription("Actualizar datos");
        refreshBtn.setImageDrawable(tintedIcon(R.drawable.ic_refresh, TERRACOTTA));
        refreshBtn.setBackground(ripple(PAPER, 22, LINE, 0x229B3B24));
        refreshBtn.setElevation(dp(1));
        refreshBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        refreshBtn.setOnClickListener(v -> refresh(true));
        greetRow.addView(refreshBtn);
        add(body, greetRow);
        gap(body, 12);

        if (!catalogLoaded && catalogError == null) {
            LinearLayout loadingCard = card(body);
            LinearLayout row = row();
            ProgressBar pb = new ProgressBar(this);
            pb.setIndeterminateTintList(ColorStateList.valueOf(TERRACOTTA));
            row.addView(pb, dp(22), dp(22));
            gap(row, 12);
            row.addView(text("Cargando catálogo de la tienda…", 13, MUTED, false));
            add(loadingCard, row);
        }

        if (catalogLoaded) {
            long total = products.size();
            long available = count("available", true);
            long outOfStock = count("available", false);
            long featured = count("featured", true);

            LinearLayout first = row();
            statCard(first, "Productos totales", total, R.drawable.ic_products, SOFT, TERRACOTTA, () -> goProducts(0));
            gap(first, 10);
            statCard(first, "Disponibles", available, R.drawable.ic_check_circle, GREEN_SOFT, GREEN, () -> goProducts(1));
            add(body, first);
            gap(body, 10);

            LinearLayout second = row();
            statCard(second, "Agotados", outOfStock, R.drawable.ic_warning, ROCOTO_SOFT, ROCOTO, () -> goProducts(2));
            gap(second, 10);
            statCard(second, "Destacados", featured, R.drawable.ic_star, AMBER_SOFT, AMBER, () -> goProducts(5));
            add(body, second);
            gap(body, 10);

            // Promotions wide stat card
            LinearLayout promoCard = row();
            promoCard.setBackground(ripple(PAPER, 16, LINE, 0x189B3B24));
            promoCard.setElevation(dp(2));
            pad(promoCard, 14, 12, 14, 12);
            promoCard.addView(iconBadge(R.drawable.ic_promo, 40, 20, SOFT, TERRACOTTA, 12));
            gap(promoCard, 12);
            LinearLayout promoText = column();
            add(promoText, text(Long.toString(offers.size()), 20, INK, true));
            add(promoText, text("Promociones existentes", 12, MUTED, false));
            weight(promoCard, promoText);
            promoCard.addView(iconView(R.drawable.ic_chevron_right, 20, MUTED));
            promoCard.setOnClickListener(v -> goPromotions());
            add(body, promoCard);
            gap(body, 14);
        }

        // Quick Actions (kept "Acciones rápidas" and "Nuevo producto" labels for QA compatibility)
        heading(body, "Acciones rápidas");
        LinearLayout quickRow = row();
        quickTile(quickRow, "Nuevo producto", R.drawable.ic_plus, () -> productEditor(null));
        gap(quickRow, 8);
        quickTile(quickRow, "Promoción", R.drawable.ic_promo, () -> promotionEditor(null));
        gap(quickRow, 8);
        quickTile(quickRow, "WhatsApp", R.drawable.ic_whatsapp, () -> settingsEditor(Editors.SettingsSection.STORE_INFO, true));
        gap(quickRow, 8);
        quickTile(quickRow, "Ver tienda", R.drawable.ic_store, this::openStore);
        add(body, quickRow);
        gap(body, 14);

        // Compact alert banner if needed
        if (hasPendingAlerts()) {
            LinearLayout alertBanner = row();
            alertBanner.setBackground(ripple(AMBER_SOFT, 14, 0x449E6300, 0x229E6300));
            pad(alertBanner, 12, 10, 12, 10);
            alertBanner.addView(iconView(R.drawable.ic_warning, 20, AMBER));
            gap(alertBanner, 10);
            weight(alertBanner, text("Tienes alertas de configuración pendientes", 12, INK, true));
            TextView review = text("Revisar", 12, TERRACOTTA, true);
            pad(review, 6, 4, 4, 4);
            alertBanner.addView(review);
            alertBanner.setOnClickListener(v -> showAlertsDialog());
            add(body, alertBanner);
            gap(body, 12);
        }

        // Out of stock restock card
        if (catalogLoaded) {
            List<JSONObject> sold = products.stream().filter(p -> !p.optBoolean("available"))
                .collect(java.util.stream.Collectors.toList());
            if (!sold.isEmpty()) {
                LinearLayout restock = card(body);
                heading(restock, "Reposición de agotados");
                for (int i = 0; i < sold.size(); i++) {
                    JSONObject product = sold.get(i);
                    LinearLayout line = row();
                    String pName = Catalog.text(product, "name");
                    String letter = pName.isEmpty() ? "P" : pName.substring(0, 1).toUpperCase(Locale.ROOT);
                    line.addView(monogram(letter, 36, ROCOTO_SOFT, ROCOTO));
                    gap(line, 10);
                    LinearLayout info = column();
                    TextView nameTv = text(pName, 14, INK, true);
                    nameTv.setMaxLines(1);
                    nameTv.setEllipsize(TextUtils.TruncateAt.END);
                    add(info, nameTv);
                    add(info, text(money(amount(product)), 12, MUTED, false));
                    weight(line, info);

                    Button repBtn = button("Reponer", () -> patchProduct(product, "available", true), false);
                    repBtn.setTextSize(12);
                    repBtn.setTextColor(GREEN);
                    repBtn.setBackground(ripple(GREEN_SOFT, 11, 0, 0x221A7F45));
                    repBtn.setMinHeight(dp(38));
                    repBtn.setMinimumHeight(dp(38));
                    pad(repBtn, 12, 4, 12, 4);
                    line.addView(repBtn);
                    add(restock, line);
                    if (i < sold.size() - 1) gap(restock, 8);
                }
            } else {
                LinearLayout okRow = row();
                okRow.setBackground(rounded(GREEN_SOFT, 14, 0));
                pad(okRow, 12, 10, 12, 10);
                okRow.addView(iconView(R.drawable.ic_check_circle, 18, GREEN));
                gap(okRow, 8);
                okRow.addView(text("Todos los productos tienen stock disponible.", 12, GREEN, true));
                add(body, okRow);
            }
        }
    }

    private void statCard(LinearLayout row, String title, long count, int iconRes, int iconBg, int iconTint, Runnable onClick) {
        LinearLayout cell = row();
        cell.setBackground(ripple(PAPER, 16, LINE, 0x189B3B24));
        cell.setElevation(dp(2));
        pad(cell, 12, 12, 12, 12);
        cell.setMinimumHeight(dp(74));

        cell.addView(iconBadge(iconRes, 38, 20, iconBg, iconTint, 12));
        gap(cell, 10);

        LinearLayout textCol = column();
        add(textCol, text(Long.toString(count), 21, INK, true));
        TextView label = text(title, 11, MUTED, false);
        label.setMaxLines(2);
        add(textCol, label);
        weight(cell, textCol);

        cell.setContentDescription(title + ", " + count);
        cell.setOnClickListener(v -> onClick.run());
        weight(row, cell);
    }

    private void quickTile(LinearLayout row, String label, int iconRes, Runnable action) {
        LinearLayout tile = column();
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setBackground(ripple(PAPER, 14, LINE, 0x189B3B24));
        tile.setElevation(dp(1));
        pad(tile, 4, 10, 4, 10);
        tile.setMinimumHeight(dp(76));

        tile.addView(iconBadge(iconRes, 38, 20, SOFT, TERRACOTTA, 19));
        gap(tile, 6);
        TextView tv = text(label, 11, INK, true);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        tile.addView(tv);

        tile.setOnClickListener(v -> action.run());
        weight(row, tile);
    }

    private boolean hasBenefits(JSONObject product) {
        JSONArray benefits = array(product, "includes");
        for (int i = 0; i < benefits.length(); i++) {
            if (!benefits.isNull(i) && !benefits.optString(i).trim().isEmpty()) return true;
        }
        return false;
    }

    private long count(String key, boolean value) {
        return products.stream().filter(p -> p.optBoolean(key) == value).count();
    }

    List<JSONObject> filteredProducts() {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<JSONObject> visible = new ArrayList<>();
        for (JSONObject p : products) {
            if (category > 0 && !CATEGORIES[category - 1].equals(Catalog.text(p, "category"))) continue;
            if (!(Catalog.text(p, "name") + " " + Catalog.text(p, "description")).toLowerCase(Locale.ROOT).contains(needle)) continue;
            boolean matches = switch (status) {
                case 1 -> p.optBoolean("available");
                case 2 -> !p.optBoolean("available");
                case 3 -> !p.optBoolean("visible");
                case 4 -> p.optBoolean("visible");
                case 5 -> p.optBoolean("featured");
                case 6 -> Catalog.text(p, "image_url").trim().isEmpty();
                case 7 -> !hasBenefits(p);
                default -> true;
            };
            if (matches) visible.add(p);
        }
        switch (sort) {
            case 1 -> visible.sort(Comparator.comparing(p -> Catalog.text(p, "name"), String.CASE_INSENSITIVE_ORDER));
            case 2 -> visible.sort(Comparator.comparing(Catalog::amount));
            case 3 -> visible.sort(Comparator.comparing(Catalog::amount).reversed());
            case 4 -> visible.sort(Comparator.comparing((JSONObject p) -> Catalog.text(p, "created_at")).reversed());
            default -> {}
        }
        return visible;
    }

    void quickPrice(JSONObject product) {
        LinearLayout form = column();
        pad(form, 20, 10, 20, 4);
        EditText priceField = input(form, "Precio en soles (S/)", amount(product).toPlainString(), MONEY);
        TextView error = text("", 13, ROCOTO, true);
        add(form, error);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(Catalog.text(product, "name"))
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                var value = price(priceField.getText().toString());
                dialog.dismiss();
                patchProduct(product, "price", value);
            } catch (Exception invalid) { error.setText(invalid.getMessage()); }
        }));
        dialog.show();
    }

    void productActions(JSONObject product) {
        List<String> labels = new ArrayList<>(List.of(
            "Editar ficha completa",
            "Cambiar precio",
            product.optBoolean("available") ? "Marcar agotado" : "Marcar disponible",
            product.optBoolean("visible") ? "Ocultar de la tienda" : "Mostrar en la tienda",
            product.optBoolean("featured") ? "Quitar destacado" : "Marcar como destacado",
            "Duplicar como oculto",
            "Eliminar producto"
        ));
        new AlertDialog.Builder(this)
            .setTitle(Catalog.text(product, "name"))
            .setItems(labels.toArray(new String[0]), (dialog, item) -> {
                switch (item) {
                    case 0 -> productEditor(product);
                    case 1 -> quickPrice(product);
                    case 2 -> patchProduct(product, "available", !product.optBoolean("available"));
                    case 3 -> patchProduct(product, "visible", !product.optBoolean("visible"));
                    case 4 -> patchProduct(product, "featured", !product.optBoolean("featured"));
                    case 5 -> mutate(() -> api.change("POST", PRODUCTS, copyProduct(product), 1), "Copia creada como oculta.");
                    case 6 -> confirm("¿Eliminar «" + Catalog.text(product, "name") + "»? Puede afectar a sus promociones.",
                        () -> mutate(() -> api.change("DELETE", where(PRODUCTS, id(product)), null, 1), "Producto eliminado."));
                    default -> {}
                }
            }).show();
    }

    private void patchProduct(JSONObject product, String field, Object value) {
        mutate(() -> api.change("PATCH", where(PRODUCTS, id(product)), object(field, value), 1), "Producto actualizado.");
    }

    void mutate(Operation op, String message) {
        perform("Actualizando…", op, () -> {
            notice(message);
            refresh(false);
        }, null);
    }

    void bulkActions() {
        String[] labels = {"Marcar disponibles", "Marcar agotados", "Mostrar en tienda", "Ocultar de tienda", "Eliminar seleccionados", "Limpiar selección"};
        new AlertDialog.Builder(this)
            .setTitle(selected.size() + " productos seleccionados")
            .setItems(labels, (dialog, index) -> {
                String path = "products?id=in.(" + String.join(",", selected) + ")";
                int size = selected.size();
                if (index == 5) {
                    selected.clear();
                    ProductsPanel.renderResults(this);
                    return;
                }
                if (index == 4) {
                    confirm("¿Eliminar " + size + " productos? Puede afectar a sus promociones.",
                        () -> mutate(() -> api.change("DELETE", path, null, size), "Productos eliminados."));
                    return;
                }
                mutate(() -> api.change("PATCH", path, object(index < 2 ? "available" : "visible", index % 2 == 0), size),
                    "Productos actualizados.");
            }).show();
    }

    void move(List<JSONObject> visible, int source, int direction) {
        List<JSONObject> ordered = new ArrayList<>(products);
        int a = ordered.indexOf(visible.get(source));
        int b = ordered.indexOf(visible.get(source + direction));
        if (a < 0 || b < 0) return;
        Collections.swap(ordered, a, b);
        perform("Guardando orden…", () -> {
            for (int i = 0; i < ordered.size(); i++) {
                JSONObject p = ordered.get(i);
                if (p.optInt("sort_order") != i + 1) {
                    try {
                        api.change("PATCH", where(PRODUCTS, id(p)) + "&sort_order=eq." + p.optInt("sort_order"),
                            object("sort_order", i + 1), 1);
                    } catch (Exception failure) {
                        runOnUiThread(() -> clock.post(() -> refresh(false)));
                        throw new java.io.IOException("El orden se guardó parcialmente. Se volverá a cargar el catálogo.", failure);
                    }
                }
            }
        }, () -> {
            notice("Orden actualizado.");
            refresh(false);
        }, null);
    }

    List<JSONObject> included(Set<String> ids) {
        return products.stream().filter(p -> ids.contains(id(p))).collect(java.util.stream.Collectors.toList());
    }

    private void settingsList() {
        LinearLayout header = row();
        header.addView(iconBadge(R.drawable.ic_settings, 46, 24, SOFT, TERRACOTTA, 14));
        gap(header, 12);
        LinearLayout headCol = column();
        add(headCol, text("Ajustes", 22, INK, true));
        add(headCol, text("Configura tu tienda y personaliza la información", 12, MUTED, false));
        weight(header, headCol);
        add(body, header);
        gap(body, 14);

        if (settingsError != null) {
            TextView err = text(settingsError, 12, ROCOTO, true);
            err.setBackground(rounded(ROCOTO_SOFT, 12, 0x44B33022));
            pad(err, 12, 8, 12, 8);
            add(body, err);
            gap(body, 10);
        }

        // Categorized menu items matching reference mockup
        addSettingsItem(Editors.SettingsSection.STORE_INFO, R.drawable.ic_mail, "Nombre, WhatsApp y horarios");
        addSettingsItem(Editors.SettingsSection.HERO, R.drawable.ic_image, "Título, subtítulo y contenido");
        addSettingsItem(Editors.SettingsSection.SUPPORT_PAYMENTS, R.drawable.ic_shield, "Métodos de pago, soporte y garantías");
        addSettingsItem(Editors.SettingsSection.FAQ, R.drawable.ic_help, "Respuestas para tus clientes");
        addSettingsItem(Editors.SettingsSection.SOCIAL, R.drawable.ic_share, "Enlaces y perfiles de PremiumPe");

        gap(body, 6);
        add(body, iconButton("Ver tienda pública", R.drawable.ic_external, this::openStore, false));
        gap(body, 8);
        add(body, dangerButton("Cerrar sesión", R.drawable.ic_logout, () -> endSession("")));
    }

    private void addSettingsItem(Editors.SettingsSection section, int menuIcon, String desc) {
        LinearLayout card = row();
        card.setBackground(ripple(PAPER, 16, LINE, 0x189B3B24));
        card.setElevation(dp(2));
        pad(card, 12, 12, 12, 12);
        card.setMinimumHeight(dp(64));

        card.addView(iconBadge(menuIcon, 42, 21, SOFT, TERRACOTTA, 12));
        gap(card, 12);

        LinearLayout info = column();
        add(info, text(section.title, 15, INK, true));
        gap(info, 2);
        add(info, text(desc, 12, MUTED, false));
        weight(card, info);

        card.addView(iconView(R.drawable.ic_chevron_right, 20, MUTED));
        card.setOnClickListener(v -> settingsEditor(section, false));
        add(body, card);
        gap(body, 10);
    }
}
