package pe.premiumpe.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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

public final class MainActivity extends UiActivity {
    static final int LINE_TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    static final int MULTILINE = LINE_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
    static final int MONEY = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    static final int URL = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
    final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler clock = new Handler(Looper.getMainLooper());
    Supabase api;
    Editors.Editor editor;
    List<JSONObject> products = new ArrayList<>();
    private List<JSONObject> offers = new ArrayList<>();
    JSONObject settings;
    private boolean catalogLoaded;
    private String catalogError, promotionsError, settingsError;
    private boolean busy, foreground;
    private boolean warned;
    private AlertDialog warning;
    private int tab, category, status, sort;
    private String search = "";
    private final Set<String> selected = new LinkedHashSet<>();
    private LinearLayout results;
    private TextView loginError;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!BuildConfig.DEBUG) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        api = new Supabase(this);
        if (api.hasSession() && !api.idle()) {
            showHome();
            refresh();
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
                warning = new AlertDialog.Builder(MainActivity.this).setTitle("Inactividad detectada")
                    .setMessage("Tu sesión se cerrará al llegar a 30 minutos de inactividad.")
                    .setNegativeButton("Cerrar sesión", (d, w) -> endSession(""))
                    .setPositiveButton("Continuar", (d, w) -> recordActivity()).setCancelable(false).show();
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
        page("PremiumPe", "Panel de Administración");

        LinearLayout topNav = row();
        Button backToStore = button("← Volver a la tienda", this::openStore, false);
        backToStore.setTextSize(12);
        topNav.addView(backToStore);
        body.addView(topNav, 0);
        gap(body, 12);

        LinearLayout panel = card(body);

        LinearLayout logoContainer = row();
        logoContainer.setGravity(Gravity.CENTER);
        TextView logo = monogram("P", 48, AJI, PAPER);
        logo.setTextSize(24);
        logoContainer.addView(logo);
        add(panel, logoContainer);
        gap(panel, 12);

        TextView title = text("Panel de Administración", 22, INK, true);
        title.setGravity(Gravity.CENTER);
        add(panel, title);
        gap(panel, 4);

        TextView subtitle = text("Gestión del catálogo y precios de PremiumPe", 12, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        add(panel, subtitle);
        gap(panel, 16);

        LinearLayout securityBox = row();
        securityBox.setBackground(rounded(SOFT, 12, LINE));
        pad(securityBox, 12, 10, 12, 10);
        TextView shieldIcon = text("✓ ", 14, AJI, true);
        securityBox.addView(shieldIcon);
        TextView shieldText = text("Ingresa con tus credenciales de administrador autorizadas.", 12, MUTED, false);
        weight(securityBox, shieldText);
        add(panel, securityBox);
        gap(panel, 14);

        if (!api.configured()) {
            note(panel, "Configura Supabase en el archivo .env para habilitar el acceso.");
            gap(panel, 8);
        }

        EditText user = input(panel, "Usuario o correo administrativo", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        user.setHint("Ingresa tu usuario o correo");

        EditText pass = input(panel, "Contraseña", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setHint("••••••••••••");

        loginError = text(message == null ? "" : message, 13, ROCOTO, true);
        loginError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (message != null && !message.isEmpty()) {
            loginError.setBackground(rounded(0x20a13020, 10, 0x40a13020));
            pad(loginError, 10, 8, 10, 8);
        }
        add(panel, loginError);
        gap(panel, 8);

        Button enter = button("Iniciar Sesión en Admin", () -> {
            if (pass.length() == 0) {
                loginError.setText("Ingresa tu contraseña.");
                loginError.setBackground(rounded(0x20a13020, 10, 0x40a13020));
                pad(loginError, 10, 8, 10, 8);
                return;
            }
            String account = user.getText().toString(), secret = pass.getText().toString();
            hideKeyboard();
            perform(() -> api.login(account, secret), () -> {
                tab = 0;
                showHome();
                refresh();
            }, loginError);
        }, true);
        enter.setContentDescription("Entrar");
        enter.setEnabled(api.configured());
        add(panel, enter);
        gap(panel, 16);

        TextView storeLink = text("Ver tienda pública ↗", 12, MUTED, true);
        storeLink.setGravity(Gravity.CENTER);
        storeLink.setMinHeight(dp(44));
        storeLink.setOnClickListener(v -> openStore());
        add(panel, storeLink);
    }

    private void endSession(String reason) {
        if (warning != null) warning.dismiss();
        warned = false;
        perform(api::logout, () -> {
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
        if (busy) return;
        busy = true;
        if (errorView != null) errorView.setText("");
        List<View> disabled = new ArrayList<>();
        disableControls(root, disabled);
        io.execute(() -> {
            String problem = null;
            try { operation.run(); }
            catch (Exception error) { problem = error.getLocalizedMessage() == null ? "No se pudo completar la operación." : error.getLocalizedMessage(); }
            String result = problem == null ? null : (problem.isEmpty() ? "No se pudo completar la operación." : problem);
            runOnUiThread(() -> {
                busy = false;
                if (isDestroyed()) return;
                disabled.forEach(view -> view.setEnabled(true));
                if (result == null) done.run();
                else if (!api.hasSession() && (errorView == null || loginError != errorView)) {
                    products.clear();
                    offers.clear();
                    catalogLoaded = false;
                    settings = null;
                    selected.clear();
                    if (warning != null) warning.dismiss();
                    showLogin(result);
                } else if (errorView != null) errorView.setText(result);
                else alert(result);
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
    private void confirm(String message, Runnable action) {
        new AlertDialog.Builder(this).setTitle("Confirmar eliminación").setMessage(message)
            .setNegativeButton("Cancelar", null).setPositiveButton("Eliminar", (d, w) -> action.run()).show();
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

    private void refresh() {
        perform(() -> {
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
        notice(message);
        refresh();
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

    private static final String[] TABS = {"Resumen", "Productos", "Promociones", "Ajustes"};
    private static final String[] TAB_SUBTITLES = {
        "Resumen General", "Gestión de Productos", "Gestión de Promociones", "Configuración de la Tienda"
    };
    private static final int[] ICONS = {android.R.drawable.ic_menu_view, android.R.drawable.ic_menu_agenda,
        android.R.drawable.ic_menu_today, android.R.drawable.ic_menu_preferences};

    private void showHome() {
        page(TABS[tab], "PremiumPe Admin");

        LinearLayout topHeader = row();
        topHeader.setBackground(rounded(PAPER, 16, LINE));
        pad(topHeader, 12, 10, 12, 10);

        TextView monogram = monogram("P", 36, AJI, PAPER);
        topHeader.addView(monogram);
        gap(topHeader, 10);

        LinearLayout titleCol = column();
        LinearLayout brandRow = row();
        brandRow.addView(text("PremiumPe", 15, INK, true));
        gap(brandRow, 6);
        brandRow.addView(badge("ADMIN", SOFT, AJI, LINE));
        add(titleCol, brandRow);
        add(titleCol, text(TAB_SUBTITLES[tab], 11, MUTED, false));
        weight(topHeader, titleCol);

        Button viewStoreBtn = button("Tienda ↗", this::openStore, false);
        viewStoreBtn.setTextSize(11);
        viewStoreBtn.setMinHeight(dp(36));
        viewStoreBtn.setMinimumHeight(dp(36));
        pad(viewStoreBtn, 10, 4, 10, 4);
        topHeader.addView(viewStoreBtn);
        gap(topHeader, 6);

        Button logoutBtn = button("Salir", () -> endSession(""), false);
        logoutBtn.setTextSize(11);
        logoutBtn.setMinHeight(dp(36));
        logoutBtn.setMinimumHeight(dp(36));
        logoutBtn.setTextColor(ROCOTO);
        logoutBtn.setBackground(rounded(0x18a13020, 10, 0x30a13020));
        pad(logoutBtn, 10, 4, 10, 4);
        topHeader.addView(logoutBtn);

        body.addView(topHeader, 0);
        gap(body, 12);

        LinearLayout nav = row();
        nav.setBackgroundColor(BG);
        pad(nav, 6, 6, 6, 6);
        for (int index = 0; index < TABS.length; index++) {
            int destination = index;
            TextView link = text(TABS[index], 11, index == tab ? AJI : MUTED, index == tab);
            link.setGravity(Gravity.CENTER);
            link.setCompoundDrawablesWithIntrinsicBounds(0, ICONS[index], 0, 0);
            link.setSelected(index == tab);
            link.setMinHeight(dp(54));
            if (index == tab) link.setBackground(rounded(SOFT, 12, 0));
            link.setOnClickListener(v -> { hideKeyboard(); tab = destination; showHome(); });
            weight(nav, link);
        }
        add(root, nav);
        if (catalogError != null) note(body, "Productos · " + catalogError);
        switch (tab) {
            case 0 -> summary();
            case 1 -> productList();
            case 2 -> promotionList();
            case 3 -> settingsList();
            default -> throw new IllegalStateException();
        }
        gap(body, 8);
        add(body, button("Actualizar datos", this::refresh, false));
    }

    private void openStore() {
        try {
            if (!BuildConfig.STORE_URL.startsWith("https://") && !BuildConfig.DEBUG) throw new IllegalArgumentException();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.STORE_URL)));
        } catch (Exception error) { alert("No se pudo abrir la tienda. Revisa la dirección configurada."); }
    }
    private void goProducts(int filter) {
        tab = 1;
        status = filter;
        category = sort = 0;
        search = "";
        showHome();
    }
    private void productEditor(JSONObject product) {
        start(new Editors.Product(this, product));
    }
    private void promotionEditor(JSONObject promotion) {
        if (!catalogLoaded || promotionsError != null) {
            alert("Actualiza productos y promociones antes de abrir el editor.");
            return;
        }
        start(new Editors.Promotion(this, promotion));
    }
    private void settingsEditor(boolean whatsapp) {
        if (settingsError != null || (!catalogLoaded && settings == null)) {
            alert("Actualiza los datos de la tienda antes de editar.");
            return;
        }
        start(new Editors.Settings(this, settings, whatsapp));
    }

    private void summary() {
        if (!catalogLoaded && catalogError == null) note(body, "Cargando catálogo…");

        if (catalogLoaded) {
            long covers = products.stream().filter(p -> Catalog.text(p, "image_url").trim().isEmpty()).count();
            long benefits = products.stream().filter(p -> !hasBenefits(p)).count();
            boolean missingPhone = settingsError == null && (settings == null ||
                Catalog.text(settings, "whatsapp_number").replaceAll("[^0-9]", "").length() < 9);
            if (missingPhone || benefits > 0 || covers > 0) {
                LinearLayout alerts = card(body);
                heading(alerts, "Alertas de configuración");
                if (missingPhone) {
                    add(alerts, button("⚠️ Configurar WhatsApp de atención", () -> settingsEditor(true), false));
                    gap(alerts, 6);
                }
                if (benefits > 0) {
                    add(alerts, button("⚠️ " + benefits + " productos sin beneficios", () -> goProducts(7), false));
                    gap(alerts, 6);
                }
                if (covers > 0) {
                    add(alerts, button("⚠️ " + covers + " productos sin portada", () -> goProducts(6), false));
                }
            }
        }

        if (catalogLoaded) {
            long total = products.size();
            long available = count("available", true);
            long outOfStock = count("available", false);
            long featured = count("featured", true);
            long visible = count("visible", true);

            LinearLayout first = row();
            statCard(first, "Productos Totales", total, visible + " visibles en la tienda", INK, 0);
            gap(first, 8);
            statCard(first, "Disponibles", available, "Listos para entrega", GREEN, 1);
            add(body, first);
            gap(body, 8);

            LinearLayout second = row();
            statCard(second, "Agotados", outOfStock, outOfStock == 0 ? "Sin faltantes" : "Requieren reposición", ROCOTO, 2);
            gap(second, 8);
            statCard(second, "Destacados", featured, "En el mazo de portada", AJI, 5);
            add(body, second);
            gap(body, 12);
        }

        LinearLayout quick = card(body);
        heading(quick, "Acciones rápidas");
        add(quick, button("＋ Nuevo producto", () -> productEditor(null), true));
        gap(quick, 6);
        add(quick, button("🎁 Nueva promoción", () -> promotionEditor(null), false));
        gap(quick, 6);
        LinearLayout links = row();
        weight(links, button("💬 Editar WhatsApp", () -> settingsEditor(true), false));
        gap(links, 6);
        weight(links, button("🌐 Ver tienda", this::openStore, false));
        add(quick, links);

        if (catalogLoaded) {
            LinearLayout restock = card(body);
            heading(restock, "Reposición de agotados");
            List<JSONObject> sold = products.stream().filter(p -> !p.optBoolean("available")).collect(java.util.stream.Collectors.toList());
            if (sold.isEmpty()) note(restock, "Todos los productos tienen disponibilidad.");
            for (JSONObject product : sold) {
                LinearLayout line = row();
                String name = Catalog.text(product, "name");
                String letter = name.isEmpty() ? "P" : name.substring(0, 1).toUpperCase(Locale.ROOT);
                line.addView(monogram(letter, 32, SOFT, AJI));
                gap(line, 8);
                weight(line, text(name, 14, INK, true));
                Button repBtn = button("Reponer", () -> patchProduct(product, "available", true), false);
                repBtn.setTextSize(12);
                repBtn.setTextColor(GREEN);
                repBtn.setMinHeight(dp(36));
                repBtn.setMinimumHeight(dp(36));
                pad(repBtn, 12, 4, 12, 4);
                line.addView(repBtn);
                add(restock, line);
                gap(restock, 6);
            }
        }
    }

    private void statCard(LinearLayout row, String title, long count, String subtitle, int numColor, int filter) {
        LinearLayout cell = column();
        cell.setBackground(rounded(PAPER, 18, LINE));
        pad(cell, 14, 12, 14, 12);
        add(cell, text(title, 11, MUTED, true));
        gap(cell, 4);
        add(cell, text(Long.toString(count), 24, numColor, true));
        gap(cell, 4);
        add(cell, text(subtitle, 10, numColor == INK ? MUTED : numColor, false));
        cell.setMinimumHeight(dp(64));
        cell.setContentDescription(title + ", " + count);
        cell.setOnClickListener(v -> goProducts(filter));
        weight(row, cell);
    }

    private boolean hasBenefits(JSONObject product) {
        JSONArray benefits = array(product, "includes");
        for (int i = 0; i < benefits.length(); i++) if (!benefits.isNull(i) && !benefits.optString(i).trim().isEmpty()) return true;
        return false;
    }
    private long count(String key, boolean value) {
        return products.stream().filter(p -> p.optBoolean(key) == value).count();
    }
    private void metric(LinearLayout row, String label, long count, int filter) {
        LinearLayout cell = column();
        add(cell, text(Long.toString(count), 25, INK, true));
        add(cell, text(label, 12, MUTED, false));
        cell.setMinimumHeight(dp(50));
        cell.setContentDescription(label + ", " + count);
        cell.setOnClickListener(v -> goProducts(filter));
        weight(row, cell);
    }

    private void productList() {
        add(body, button("Nuevo producto", () -> productEditor(null), true));
        gap(body, 6);
        LinearLayout filters = card(body);
        EditText query = input(filters, "Buscar productos", search, InputType.TYPE_CLASS_TEXT);
        LinearLayout first = row();
        LinearLayout categories = column();
        LinearLayout states = column();
        Spinner categoryPicker = choices(categories, "Categoría", new String[]{"Todas", "Streaming", "Herramientas", "IA"}, category, null);
        Spinner statusPicker = choices(states, "Estado", SUMMARY_FILTERS, status, null);
        weight(first, categories);
        weight(first, states);
        add(filters, first);
        Spinner sortPicker = choices(filters, "Ordenar por", new String[]{"Posición", "Nombre", "Precio ascendente",
            "Precio descendente", "Más recientes"}, sort, null);
        results = column();
        add(body, results);
        Runnable update = () -> {
            search = query.getText().toString();
            category = categoryPicker.getSelectedItemPosition();
            status = statusPicker.getSelectedItemPosition();
            sort = sortPicker.getSelectedItemPosition();
            renderProducts();
        };
        watch(query, update);
        AdapterView.OnItemSelectedListener change = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) { update.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        for (Spinner picker : new Spinner[]{categoryPicker, statusPicker, sortPicker}) picker.setOnItemSelectedListener(change);
        renderProducts();
    }

    private List<JSONObject> filteredProducts() {
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
            default -> { }
        }
        return visible;
    }

    private void renderProducts() {
        if (tab != 1 || editor != null || results == null) return;
        results.removeAllViews();
        List<JSONObject> visible = filteredProducts();
        if (visible.isEmpty()) note(results, catalogLoaded ? "No hay productos con estos filtros." : "Catálogo pendiente de cargar.");
        if (!visible.isEmpty()) add(results, button("Seleccionar resultados", () -> {
            visible.forEach(p -> selected.add(id(p)));
            renderProducts();
        }, false));
        gap(results, 8);
        if (!selected.isEmpty()) {
            add(results, button("Acciones en lote · " + selected.size(), this::bulkActions, true));
            gap(results, 8);
        }
        for (int index = 0; index < visible.size(); index++) {
            JSONObject product = visible.get(index);
            LinearLayout item = card(results);

            LinearLayout topRow = row();
            CheckBox check = new CheckBox(this);
            check.setMinimumWidth(dp(36));
            check.setContentDescription("Seleccionar " + Catalog.text(product, "name"));
            check.setChecked(selected.contains(id(product)));
            check.setOnCheckedChangeListener((v, checked) -> {
                if (checked) selected.add(id(product)); else selected.remove(id(product));
                renderProducts();
            });
            topRow.addView(check);
            gap(topRow, 6);

            String name = Catalog.text(product, "name");
            String letter = name.isEmpty() ? "P" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            topRow.addView(monogram(letter, 38, SOFT, AJI));
            gap(topRow, 8);

            LinearLayout infoCol = column();
            TextView label = text(name, 16, INK, true);
            label.setOnClickListener(v -> productEditor(product));
            add(infoCol, label);
            gap(infoCol, 3);

            LinearLayout badgesRow = row();
            badgesRow.addView(badge(Catalog.text(product, "category"), SOFT, INK, LINE));
            gap(badgesRow, 4);
            if (!product.optBoolean("available")) {
                badgesRow.addView(badge("Agotado", 0x22a13020, ROCOTO, 0x44a13020));
                gap(badgesRow, 4);
            }
            if (!product.optBoolean("visible")) {
                badgesRow.addView(badge("Oculto", 0x15391b15, MUTED, LINE));
                gap(badgesRow, 4);
            }
            if (product.optBoolean("featured")) {
                badgesRow.addView(badge("Destacado", 0x30ffd379, AJI, LINE));
            }
            add(infoCol, badgesRow);
            weight(topRow, infoCol);
            add(item, topRow);
            gap(item, 8);

            LinearLayout actions = row();
            weight(actions, button(money(amount(product)), () -> quickPrice(product), false));
            gap(actions, 4);
            weight(actions, button("Editar", () -> productEditor(product), false));
            gap(actions, 4);
            weight(actions, button("Más", () -> productActions(product), false));
            add(item, actions);
            if (sort == 0 && visible.size() > 1) {
                int position = index;
                gap(item, 6);
                LinearLayout order = row();
                Button up = button("Subir", () -> move(visible, position, -1), false);
                Button down = button("Bajar", () -> move(visible, position, 1), false);
                up.setEnabled(index > 0);
                down.setEnabled(index < visible.size() - 1);
                weight(order, up);
                gap(order, 4);
                weight(order, down);
                add(item, order);
            }
        }
    }

    private void quickPrice(JSONObject product) {
        LinearLayout form = column();
        pad(form, 20, 8, 20, 0);
        EditText priceField = input(form, "Precio en soles", amount(product).toPlainString(), MONEY);
        TextView error = text("", 14, RED, true);
        add(form, error);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(Catalog.text(product, "name")).setView(form)
            .setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                var value = price(priceField.getText().toString());
                dialog.dismiss();
                patchProduct(product, "price", value);
            } catch (Exception invalid) { error.setText(invalid.getMessage()); }
        }));
        dialog.show();
    }
    private void productActions(JSONObject product) {
        String[] labels = {product.optBoolean("available") ? "Marcar agotado" : "Marcar disponible",
            product.optBoolean("visible") ? "Ocultar" : "Mostrar",
            product.optBoolean("featured") ? "Quitar destacado" : "Destacar", "Duplicar", "Eliminar"};
        new AlertDialog.Builder(this).setTitle(Catalog.text(product, "name")).setItems(labels, (dialog, item) -> {
            switch (item) {
                case 0 -> patchProduct(product, "available", !product.optBoolean("available"));
                case 1 -> patchProduct(product, "visible", !product.optBoolean("visible"));
                case 2 -> patchProduct(product, "featured", !product.optBoolean("featured"));
                case 3 -> mutate(() -> api.change("POST", PRODUCTS, copyProduct(product), 1), "Copia creada como producto oculto.");
                case 4 -> confirm("¿Eliminar «" + Catalog.text(product, "name") + "»? Puede afectar a sus promociones.",
                    () -> mutate(() -> api.change("DELETE", where(PRODUCTS, id(product)), null, 1), "Producto eliminado."));
                default -> { }
            }
        }).show();
    }
    private void patchProduct(JSONObject product, String field, Object value) {
        mutate(() -> api.change("PATCH", where(PRODUCTS, id(product)), object(field, value), 1), "Producto actualizado.");
    }
    private void mutate(Operation op, String message) {
        perform(op, () -> { notice(message); refresh(); }, null);
    }
    private void bulkActions() {
        String[] labels = {"Marcar disponibles", "Marcar agotados", "Mostrar", "Ocultar", "Eliminar", "Limpiar selección"};
        new AlertDialog.Builder(this).setTitle(selected.size() + " productos seleccionados").setItems(labels, (dialog, index) -> {
            String path = "products?id=in.(" + String.join(",", selected) + ")";
            int size = selected.size();
            if (index == 5) { selected.clear(); renderProducts(); return; }
            if (index == 4) {
                confirm("¿Eliminar " + size + " productos? Puede afectar a sus promociones.",
                    () -> mutate(() -> api.change("DELETE", path, null, size), "Productos eliminados."));
                return;
            }
            mutate(() -> api.change("PATCH", path, object(index < 2 ? "available" : "visible", index % 2 == 0), size),
                "Productos actualizados.");
        }).show();
    }
    private void move(List<JSONObject> visible, int source, int direction) {
        List<JSONObject> ordered = new ArrayList<>(products);
        int a = ordered.indexOf(visible.get(source)), b = ordered.indexOf(visible.get(source + direction));
        Collections.swap(ordered, a, b);
        perform(() -> {
            for (int i = 0; i < ordered.size(); i++) {
                JSONObject p = ordered.get(i);
                if (p.optInt("sort_order") != i + 1) {
                    try {
                        api.change("PATCH", where(PRODUCTS, id(p)) + "&sort_order=eq." + p.optInt("sort_order"),
                            object("sort_order", i + 1), 1);
                    } catch (Exception failure) {
                        runOnUiThread(() -> clock.post(this::refresh));
                        throw new java.io.IOException("El orden se guardó parcialmente. Se volverá a cargar el catálogo.", failure);
                    }
                }
            }
        }, () -> { notice("Orden actualizado."); refresh(); }, null);
    }

    List<JSONObject> included(Set<String> ids) {
        return products.stream().filter(p -> ids.contains(id(p))).collect(java.util.stream.Collectors.toList());
    }
    private void promotionList() {
        add(body, button("＋ Nueva promoción", () -> promotionEditor(null), true));
        gap(body, 10);
        if (promotionsError != null) note(body, promotionsError);
        else if (offers.isEmpty()) note(body, "Todavía no hay promociones creadas.");
        for (JSONObject offer : offers) {
            LinearLayout card = card(body);
            String st = promotionStatus(offer, included(Editors.ids(offer)));
            int stBg = st.equals("Publicada") ? 0x2212783a : (st.equals("Programada") ? 0x30ffd379 : (st.equals("Inactiva") ? SOFT : 0x22a13020));
            int stText = st.equals("Publicada") ? GREEN : (st.equals("Programada") ? AJI : (st.equals("Inactiva") ? MUTED : ROCOTO));

            LinearLayout headerRow = row();
            headerRow.addView(badge(st, stBg, stText, LINE));
            weight(headerRow, new View(this));
            headerRow.addView(text(money(amount(offer)), 16, AJI, true));
            add(card, headerRow);
            gap(card, 8);

            heading(card, Catalog.text(offer, "name"));
            if (!Catalog.text(offer, "description").isEmpty()) {
                note(card, Catalog.text(offer, "description"));
                gap(card, 6);
            }

            List<JSONObject> inc = included(Editors.ids(offer));
            if (!inc.isEmpty()) {
                note(card, "Total individual " + money(total(inc)));
                gap(card, 8);
            }

            LinearLayout actions = row();
            weight(actions, button("Editar", () -> promotionEditor(offer), false));
            gap(actions, 4);
            if (offer.optBoolean("active")) {
                weight(actions, button("Pausar", () -> mutate(
                    () -> api.change("PATCH", where(PROMOTIONS, id(offer)), object("active", false), 1), "Promoción pausada."), false));
                gap(actions, 4);
            } else {
                weight(actions, button("Activar", () -> mutate(
                    () -> api.change("PATCH", where(PROMOTIONS, id(offer)), object("active", true), 1), "Promoción activada."), false));
                gap(actions, 4);
            }
            weight(actions, button("Eliminar", () -> confirm("¿Eliminar «" + Catalog.text(offer, "name") + "»?",
                () -> mutate(() -> api.change("DELETE", where(PROMOTIONS, id(offer)), null, 1), "Promoción eliminada.")), false));
            add(card, actions);
        }
    }
    private void settingsList() {
        LinearLayout panel = card(body);

        LinearLayout headerRow = row();
        headerRow.addView(monogram("P", 36, AJI, PAPER));
        gap(headerRow, 10);
        LinearLayout textCol = column();
        add(textCol, text(settings == null ? "PremiumPe" : Catalog.text(settings, "store_name"), 17, INK, true));
        add(textCol, text("Configuración de la tienda", 11, MUTED, false));
        weight(headerRow, textCol);
        add(panel, headerRow);
        gap(panel, 14);

        if (settingsError != null) {
            note(panel, settingsError);
            gap(panel, 8);
        }
        for (String section : Editors.Settings.SECTIONS) {
            add(panel, button(section, () -> {
                settingsEditor(false);
                if (editor instanceof Editors.Settings form) form.focusSection(section);
            }, false));
            gap(panel, 6);
        }
        gap(body, 6);
        add(body, button("🌐 Ver tienda pública", this::openStore, false));
        gap(body, 4);
        Button logoutBtn = button("Cerrar sesión", () -> endSession(""), false);
        logoutBtn.setTextColor(ROCOTO);
        add(body, logoutBtn);
    }
}
