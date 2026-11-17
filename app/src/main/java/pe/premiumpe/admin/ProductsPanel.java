package pe.premiumpe.admin;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import static pe.premiumpe.admin.Catalog.*;
import static pe.premiumpe.admin.UiActivity.*;

final class ProductsPanel {
    private ProductsPanel() {}

    private static String deliveryLabel(JSONObject product) {
        String raw = text(product, "delivery_type");
        for (int i = 0; i < DELIVERY.length; i++) {
            if (DELIVERY[i].equals(raw)) return DELIVERY_LABELS[i];
        }
        return raw.isEmpty() ? "Perfil" : raw;
    }

    static void render(MainActivity app) {
        LinearLayout body = app.body;

        // Sub-header row matching mockup
        LinearLayout headRow = app.row();
        TextView headingTv = app.text(
            app.showPromotions ? "Gestión de Promociones" : "Gestión de Productos",
            19, INK, true);
        headingTv.setMaxLines(2);
        app.weight(headRow, headingTv);
        app.gap(headRow, 8);
        headRow.addView(app.storePillButton());
        app.gap(headRow, 6);
        headRow.addView(app.miniIconSquareButton(R.drawable.ic_refresh, "Actualizar datos", () -> app.refresh(true)));
        app.add(body, headRow);
        app.gap(body, 10);

        if (!app.showPromotions) {
            // Search + Filter icon row
            LinearLayout searchRow = app.row();
            LinearLayout searchBox = app.row();
            searchBox.setBackground(app.rounded(PAPER, 13, LINE));
            searchBox.setMinimumHeight(app.dp(44));
            app.pad(searchBox, 12, 2, 6, 2);
            searchBox.addView(app.iconView(R.drawable.ic_search, 18, MUTED));
            app.gap(searchBox, 8);

            EditText query = new EditText(app);
            query.setBackground(null);
            query.setSingleLine(true);
            query.setTextSize(13);
            query.setTextColor(INK);
            query.setHintTextColor(0xFF9C877E);
            query.setHint("Buscar por nombre o descripción...");
            query.setInputType(InputType.TYPE_CLASS_TEXT);
            query.setText(app.search);
            query.setSelection(app.search.length());
            query.setMinHeight(app.dp(42));
            app.weight(searchBox, query);

            ImageButton clearBtn = new ImageButton(app);
            clearBtn.setContentDescription("Limpiar búsqueda");
            clearBtn.setImageDrawable(app.tintedIcon(R.drawable.ic_close, MUTED));
            clearBtn.setBackground(app.ripple(0x00000000, 18, 0, 0x229B3B24));
            clearBtn.setVisibility(app.search.isEmpty() ? View.GONE : View.VISIBLE);
            clearBtn.setLayoutParams(new LinearLayout.LayoutParams(app.dp(36), app.dp(36)));
            clearBtn.setOnClickListener(v -> {
                query.setText("");
                app.search = "";
                clearBtn.setVisibility(View.GONE);
                renderResults(app);
            });
            searchBox.addView(clearBtn);
            app.weight(searchRow, searchBox);
            app.gap(searchRow, 8);

            boolean hasExtraFilter = app.category > 0 || app.sort > 0 || app.status > 0;
            FrameLayout filterBtnWrap = new FrameLayout(app);
            filterBtnWrap.setBackground(app.ripple(hasExtraFilter ? SOFT : PAPER, 13, hasExtraFilter ? TERRACOTTA : LINE, 0x229B3B24));
            filterBtnWrap.setLayoutParams(new LinearLayout.LayoutParams(app.dp(44), app.dp(44)));
            filterBtnWrap.setContentDescription("Filtros y orden");
            ImageView filterIcon = app.iconView(R.drawable.ic_filter, 20, hasExtraFilter ? TERRACOTTA : INK);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(app.dp(20), app.dp(20));
            iconLp.gravity = Gravity.CENTER;
            filterBtnWrap.addView(filterIcon, iconLp);
            filterBtnWrap.setOnClickListener(v -> showFilterDialog(app));
            searchRow.addView(filterBtnWrap);
            app.add(body, searchRow);
            app.gap(body, 8);

            app.watch(query, () -> {
                app.search = query.getText().toString();
                clearBtn.setVisibility(app.search.isEmpty() ? View.GONE : View.VISIBLE);
                renderResults(app);
            });

            // Inline compact filter dropdowns matching mockup
            LinearLayout filterRow1 = app.row();
            Spinner catSpin = compactSpinner(app, new String[]{"Todas las categorías", "Streaming", "Herramientas", "IA"}, app.category, pos -> {
                if (app.category != pos) { app.category = pos; renderResults(app); }
            });
            Spinner statSpin = compactSpinner(app, new String[]{
                "Todos los estados", "Disponibles", "Agotados", "Ocultos", "Visibles", "Destacados", "Sin portada", "Sin beneficios"
            }, app.status, pos -> {
                if (app.status != pos) { app.status = pos; renderResults(app); }
            });
            app.weight(filterRow1, catSpin);
            app.gap(filterRow1, 6);
            app.weight(filterRow1, statSpin);
            app.add(body, filterRow1);
            app.gap(body, 6);

            LinearLayout filterRow2 = app.row();
            LinearLayout sortWrap = app.row();
            sortWrap.setBackground(app.rounded(PAPER, 11, LINE));
            sortWrap.setMinimumHeight(app.dp(40));
            app.pad(sortWrap, 8, 0, 4, 0);
            sortWrap.addView(app.iconView(R.drawable.ic_sort, 16, MUTED));
            Spinner sortSpin = bareSpinner(app, new String[]{
                "Orden manual", "Nombre A-Z", "Precio menor", "Precio mayor", "Más recientes"
            }, app.sort, pos -> {
                if (app.sort != pos) {
                    app.sort = pos;
                    if (app.sort != 0) app.reorderMode = false;
                    renderResults(app);
                }
            });
            app.weight(sortWrap, sortSpin);
            app.weight(filterRow2, sortWrap);
            app.gap(filterRow2, 6);

            // Promociones pill chip
            TextView promoChip = app.text("Promociones (" + app.offers.size() + ")", 12, TERRACOTTA, true);
            promoChip.setBackground(app.ripple(SOFT, 11, LINE, 0x229B3B24));
            promoChip.setGravity(Gravity.CENTER);
            promoChip.setMinimumHeight(app.dp(40));
            app.pad(promoChip, 12, 6, 12, 6);
            promoChip.setOnClickListener(v -> {
                app.showPromotions = true;
                app.showHome();
            });
            filterRow2.addView(promoChip);
            app.add(body, filterRow2);
            app.gap(body, 10);

            app.add(body, app.iconButton("Nuevo producto", R.drawable.ic_plus, () -> app.productEditor(null), true));
            app.gap(body, 10);

            app.results = app.column();
            app.add(body, app.results);
            renderResults(app);
        } else {
            // Mode switch back to Productos + compact count label
            LinearLayout modeRow = app.row();
            TextView backToProducts = app.text("← Ver todos los productos", 12, TERRACOTTA, true);
            backToProducts.setBackground(app.ripple(PAPER, 11, LINE, 0x229B3B24));
            backToProducts.setMinimumHeight(app.dp(36));
            backToProducts.setGravity(Gravity.CENTER_VERTICAL);
            app.pad(backToProducts, 12, 6, 12, 6);
            backToProducts.setOnClickListener(v -> {
                app.showPromotions = false;
                app.showHome();
            });
            modeRow.addView(backToProducts);
            TextView countNote = app.text(
                app.offers.size() == 1 ? "1 promoción" : app.offers.size() + " promociones",
                12, MUTED, false);
            countNote.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            app.weight(modeRow, countNote);
            app.add(body, modeRow);
            app.gap(body, 10);

            app.add(body, app.iconButton("Nueva promoción", R.drawable.ic_plus, () -> app.promotionEditor(null), true));
            app.gap(body, 10);
            renderPromotions(app, body);
        }
    }

    private static Spinner compactSpinner(MainActivity app, String[] items, int selected,
                                          java.util.function.IntConsumer onSelect) {
        Spinner spinner = bareSpinner(app, items, selected, onSelect);
        spinner.setBackground(app.rounded(PAPER, 11, LINE));
        spinner.setMinimumHeight(app.dp(40));
        app.pad(spinner, 8, 0, 4, 0);
        return spinner;
    }

    private static Spinner bareSpinner(MainActivity app, String[] items, int selected,
                                       java.util.function.IntConsumer onSelect) {
        Spinner spinner = new Spinner(app);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(app, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(selected, items.length - 1)), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (v instanceof TextView tv) {
                    tv.setTextSize(12);
                    tv.setTextColor(INK);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                }
                onSelect.accept(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        return spinner;
    }

    private static void showFilterDialog(MainActivity app) {
        LinearLayout form = app.column();
        app.pad(form, 18, 12, 18, 4);
        Spinner categoryPicker = app.choices(form, "Categoría", new String[]{"Todas", "Streaming", "Herramientas", "IA"}, app.category, null);
        Spinner statusPicker = app.choices(form, "Estado", SUMMARY_FILTERS, app.status, null);
        Spinner sortPicker = app.choices(form, "Ordenar por", new String[]{
            "Orden manual", "Nombre", "Precio ascendente", "Precio descendente", "Más recientes"
        }, app.sort, null);

        new AlertDialog.Builder(app)
            .setTitle("Filtrar y ordenar")
            .setView(form)
            .setNeutralButton("Restablecer", (d, w) -> {
                app.category = 0;
                app.status = 0;
                app.sort = 0;
                app.reorderMode = false;
                app.showHome();
            })
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar", (d, w) -> {
                app.category = categoryPicker.getSelectedItemPosition();
                app.status = statusPicker.getSelectedItemPosition();
                app.sort = sortPicker.getSelectedItemPosition();
                if (app.sort != 0) app.reorderMode = false;
                app.showHome();
            })
            .show();
    }

    static void renderResults(MainActivity app) {
        if (app.tab != 1 || app.showPromotions || app.editor != null || app.results == null) return;
        app.results.removeAllViews();
        List<JSONObject> visible = app.filteredProducts();

        // Count + selection/reorder bar
        LinearLayout infoBar = app.row();
        app.weight(infoBar, app.text(
            "Mostrando " + visible.size() + " de " + app.products.size() + " productos",
            12, MUTED, false));
        if (!visible.isEmpty()) {
            boolean allSelected = visible.stream().allMatch(p -> app.selected.contains(id(p)));
            TextView selectToggle = app.text(allSelected ? "Deseleccionar" : "Seleccionar todos", 12, TERRACOTTA, true);
            app.pad(selectToggle, 6, 4, 4, 4);
            selectToggle.setOnClickListener(v -> {
                if (allSelected) visible.forEach(p -> app.selected.remove(id(p)));
                else visible.forEach(p -> app.selected.add(id(p)));
                renderResults(app);
            });
            infoBar.addView(selectToggle);
        }
        app.add(app.results, infoBar);
        app.gap(app.results, 6);

        if (app.sort == 0 && visible.size() > 1) {
            LinearLayout orderBar = app.row();
            orderBar.setGravity(Gravity.END);
            TextView reorderBtn = app.text(
                app.reorderMode ? "Finalizar orden" : "Reordenar ↑↓",
                11, app.reorderMode ? PAPER : TERRACOTTA, true);
            reorderBtn.setBackground(app.ripple(app.reorderMode ? TERRACOTTA : SOFT, 10, LINE, 0x229B3B24));
            app.pad(reorderBtn, 10, 5, 10, 5);
            reorderBtn.setOnClickListener(v -> {
                app.reorderMode = !app.reorderMode;
                renderResults(app);
            });
            orderBar.addView(reorderBtn);
            app.add(app.results, orderBar);
            app.gap(app.results, 6);
        }

        if (visible.isEmpty()) {
            LinearLayout empty = app.card(app.results);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            app.pad(empty, 20, 24, 20, 24);
            empty.addView(app.iconBadge(R.drawable.ic_products, 48, 24, SOFT, TERRACOTTA, 14));
            app.gap(empty, 10);
            TextView msg = app.text(
                app.catalogLoaded ? "No se encontraron productos con estos filtros." : "Cargando catálogo de productos…",
                14, MUTED, false);
            msg.setGravity(Gravity.CENTER);
            app.add(empty, msg);
            return;
        }

        // Bulk bar when items selected
        if (!app.selected.isEmpty()) {
            LinearLayout bulkBar = app.row();
            bulkBar.setBackground(app.rounded(SOFT, 14, TERRACOTTA));
            app.pad(bulkBar, 12, 8, 10, 8);
            app.weight(bulkBar, app.text(app.selected.size() + " seleccionados", 13, INK, true));

            TextView actBtn = app.text("Acciones", 12, PAPER, true);
            actBtn.setBackground(app.ripple(TERRACOTTA, 10, 0, 0x33FFFFFF));
            app.pad(actBtn, 12, 7, 12, 7);
            actBtn.setOnClickListener(v -> app.bulkActions());
            bulkBar.addView(actBtn);
            app.gap(bulkBar, 6);

            TextView clearSel = app.text("Limpiar", 12, MUTED, true);
            app.pad(clearSel, 8, 7, 8, 7);
            clearSel.setOnClickListener(v -> {
                app.selected.clear();
                renderResults(app);
            });
            bulkBar.addView(clearSel);
            app.add(app.results, bulkBar);
            app.gap(app.results, 10);
        }

        // Rich compact product cards matching reference mockup
        for (int index = 0; index < visible.size(); index++) {
            JSONObject product = visible.get(index);
            int position = index;
            String name = text(product, "name");
            String desc = text(product, "description").trim();
            boolean isAvailable = product.optBoolean("available");
            boolean isVisible = product.optBoolean("visible");
            boolean isFeatured = product.optBoolean("featured");

            LinearLayout card = app.card(app.results);
            app.pad(card, 10, 10, 10, 10);

            // Top row: CheckBox + Thumbnail + Name/Category/Delivery + 3-dot Menu
            LinearLayout topRow = app.row();

            CheckBox check = new CheckBox(app);
            check.setMinimumWidth(app.dp(34));
            check.setMinimumHeight(app.dp(40));
            check.setContentDescription("Seleccionar " + name);
            check.setButtonTintList(ColorStateList.valueOf(TERRACOTTA));
            check.setChecked(app.selected.contains(id(product)));
            check.setOnCheckedChangeListener((v, checked) -> {
                if (checked) app.selected.add(id(product));
                else app.selected.remove(id(product));
                renderResults(app);
            });
            topRow.addView(check);
            app.gap(topRow, 4);

            String letter = name.isEmpty() ? "P" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            FrameLayout thumbBox = new FrameLayout(app);
            thumbBox.setBackground(app.rounded(SOFT, 12, LINE));
            thumbBox.setClipToOutline(true);
            thumbBox.setLayoutParams(new LinearLayout.LayoutParams(app.dp(50), app.dp(50)));
            TextView monogram = app.monogram(letter, 50, SOFT, TERRACOTTA);
            thumbBox.addView(monogram, new FrameLayout.LayoutParams(-1, -1));
            ImageView thumbImg = new ImageView(app);
            thumbImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbImg.setContentDescription("Portada de " + name);
            thumbBox.addView(thumbImg, new FrameLayout.LayoutParams(-1, -1));
            app.loadRemoteImage(thumbImg, monogram, text(product, "image_url"));
            thumbBox.setOnClickListener(v -> app.productEditor(product));
            topRow.addView(thumbBox);
            app.gap(topRow, 10);

            LinearLayout infoCol = app.column();
            TextView nameView = app.text(name, 15, INK, true);
            nameView.setMaxLines(2);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            nameView.setOnClickListener(v -> app.productEditor(product));
            app.add(infoCol, nameView);
            app.gap(infoCol, 4);

            HorizontalScrollView tagsScroll = new HorizontalScrollView(app);
            tagsScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout tagsRow = app.row();
            tagsRow.addView(app.badge(text(product, "category"), NEUTRAL_SOFT, INK, 0));
            app.gap(tagsRow, 4);
            tagsRow.addView(app.badge(deliveryLabel(product), SOFT, TERRACOTTA, 0));
            if (!isVisible) {
                app.gap(tagsRow, 4);
                tagsRow.addView(app.badge("Oculto", ROCOTO_SOFT, ROCOTO, 0));
            }
            tagsScroll.addView(tagsRow);
            app.add(infoCol, tagsScroll);
            app.weight(topRow, infoCol);

            ImageButton moreBtn = new ImageButton(app);
            moreBtn.setContentDescription("Opciones de " + name);
            moreBtn.setImageDrawable(app.tintedIcon(R.drawable.ic_more_vert, MUTED));
            moreBtn.setBackground(app.ripple(0x00000000, 20, 0, 0x229B3B24));
            moreBtn.setMinimumWidth(app.dp(40));
            moreBtn.setMinimumHeight(app.dp(44));
            moreBtn.setLayoutParams(new LinearLayout.LayoutParams(app.dp(38), app.dp(44)));
            moreBtn.setOnClickListener(v -> app.productActions(product));
            topRow.addView(moreBtn);
            app.add(card, topRow);

            // Description preview
            if (!desc.isEmpty()) {
                app.gap(card, 6);
                TextView descView = app.text(desc, 12, MUTED, false);
                descView.setMaxLines(2);
                descView.setEllipsize(TextUtils.TruncateAt.END);
                app.pad(descView, 4, 0, 4, 0);
                app.add(card, descView);
            }

            // Middle row: PRECIO + Disp. switch + Vis. switch + Featured star
            app.gap(card, 8);
            LinearLayout ctrlRow = app.row();
            app.pad(ctrlRow, 4, 2, 2, 2);

            LinearLayout priceCol = app.column();
            TextView priceLbl = app.text("PRECIO", 10, MUTED, true);
            priceLbl.setLetterSpacing(0.05f);
            app.add(priceCol, priceLbl);
            app.add(priceCol, app.text(money(amount(product)), 16, INK, true));
            priceCol.setOnClickListener(v -> app.quickPrice(product));
            app.weight(ctrlRow, priceCol);

            // Disp. toggle
            LinearLayout dispCol = app.column();
            dispCol.setGravity(Gravity.CENTER_HORIZONTAL);
            dispCol.addView(app.text("Disp.", 10, MUTED, true));
            Switch dispSw = compactSwitch(app, isAvailable, GREEN, checked ->
                app.patchProduct(product, "available", checked));
            dispSw.setContentDescription("Disponible " + name);
            dispCol.addView(dispSw);
            ctrlRow.addView(dispCol);
            app.gap(ctrlRow, 8);

            // Vis. toggle
            LinearLayout visCol = app.column();
            visCol.setGravity(Gravity.CENTER_HORIZONTAL);
            visCol.addView(app.text("Vis.", 10, MUTED, true));
            Switch visSw = compactSwitch(app, isVisible, TERRACOTTA, checked ->
                app.patchProduct(product, "visible", checked));
            visSw.setContentDescription("Visible " + name);
            visCol.addView(visSw);
            ctrlRow.addView(visCol);
            app.gap(ctrlRow, 8);

            // Featured star button
            ImageButton starBtn = new ImageButton(app);
            starBtn.setContentDescription(isFeatured ? "Quitar destacado" : "Destacar " + name);
            starBtn.setImageDrawable(app.tintedIcon(
                isFeatured ? R.drawable.ic_star : R.drawable.ic_star_outline,
                isFeatured ? TERRACOTTA : MUTED));
            starBtn.setBackground(app.ripple(isFeatured ? SOFT : BG, 12, LINE, 0x229B3B24));
            starBtn.setLayoutParams(new LinearLayout.LayoutParams(app.dp(40), app.dp(40)));
            starBtn.setOnClickListener(v -> app.patchProduct(product, "featured", !isFeatured));
            ctrlRow.addView(starBtn);
            app.add(card, ctrlRow);

            // Bottom action row: Editar · Duplicar · Eliminar
            app.gap(card, 8);
            View divider = new View(app);
            divider.setBackgroundColor(0xFFEDE3DA);
            card.addView(divider, -1, app.dp(1));
            app.gap(card, 8);

            LinearLayout bottomBtns = app.row();
            app.weight(bottomBtns, compactCardAction(app, "Editar", R.drawable.ic_edit, false,
                () -> app.productEditor(product)));
            app.gap(bottomBtns, 6);
            app.weight(bottomBtns, compactCardAction(app, "Duplicar", R.drawable.ic_copy, false,
                () -> app.mutate(() -> app.api.change("POST", PRODUCTS, copyProduct(product), 1),
                    "Copia creada como oculta.")));
            app.gap(bottomBtns, 6);
            app.weight(bottomBtns, compactCardAction(app, "Eliminar", R.drawable.ic_trash_outline, true,
                () -> app.confirm("¿Eliminar «" + name + "»? Puede afectar a sus promociones.",
                    () -> app.mutate(() -> app.api.change("DELETE", where(PRODUCTS, id(product)), null, 1),
                        "Producto eliminado."))));
            app.add(card, bottomBtns);

            // Reorder arrows row when reorderMode active
            if (app.reorderMode && app.sort == 0 && visible.size() > 1) {
                app.gap(card, 6);
                LinearLayout orderRow = app.row();
                Button up = app.iconButton("Subir", R.drawable.ic_arrow_up, () -> app.move(visible, position, -1), false);
                Button down = app.iconButton("Bajar", R.drawable.ic_arrow_down, () -> app.move(visible, position, 1), false);
                up.setEnabled(index > 0);
                down.setEnabled(index < visible.size() - 1);
                up.setMinHeight(app.dp(38));
                up.setMinimumHeight(app.dp(38));
                down.setMinHeight(app.dp(38));
                down.setMinimumHeight(app.dp(38));
                app.weight(orderRow, up);
                app.gap(orderRow, 6);
                app.weight(orderRow, down);
                app.add(card, orderRow);
            }
        }
    }

    private static Switch compactSwitch(MainActivity app, boolean checked, int activeColor,
                                        java.util.function.Consumer<Boolean> onToggle) {
        Switch sw = new Switch(app);
        sw.setChecked(checked);
        sw.setThumbTintList(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{activeColor, 0xFFCCC0B8}
        ));
        sw.setTrackTintList(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{(activeColor & 0x00FFFFFF) | 0x66000000, 0x33715950}
        ));
        sw.setOnCheckedChangeListener((v, isChecked) -> onToggle.accept(isChecked));
        return sw;
    }

    private static LinearLayout compactCardAction(MainActivity app, String label, int iconRes,
                                                  boolean danger, Runnable onClick) {
        LinearLayout btn = app.row();
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight(app.dp(36));
        btn.setBackground(app.ripple(
            danger ? ROCOTO_SOFT : BG,
            10,
            danger ? 0x33B33022 : LINE,
            danger ? 0x22B33022 : 0x189B3B24
        ));
        app.pad(btn, 6, 6, 6, 6);
        btn.addView(app.iconView(iconRes, 15, danger ? ROCOTO : TERRACOTTA));
        app.gap(btn, 5);
        TextView tv = app.text(label, 12, danger ? ROCOTO : INK, true);
        tv.setMaxLines(1);
        btn.addView(tv);
        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }

    private static void renderPromotions(MainActivity app, LinearLayout container) {
        if (app.promotionsError != null) {
            LinearLayout err = app.card(container);
            app.pad(err, 12, 12, 12, 12);
            app.add(err, app.text(app.promotionsError, 13, ROCOTO, false));
            return;
        }
        if (app.offers.isEmpty()) {
            LinearLayout empty = app.card(container);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            app.pad(empty, 20, 22, 20, 22);
            empty.addView(app.iconBadge(R.drawable.ic_promo, 48, 24, SOFT, TERRACOTTA, 14));
            app.gap(empty, 10);
            app.add(empty, app.text("Todavía no hay promociones creadas.", 14, MUTED, false));
            return;
        }

        for (JSONObject offer : app.offers) {
            LinearLayout card = app.card(container);
            app.pad(card, 12, 11, 12, 11);

            List<JSONObject> inc = app.included(Editors.ids(offer));
            String st = promotionStatus(offer, inc);
            int stBg = st.equals("Publicada") ? GREEN_SOFT
                : (st.equals("Programada") ? AMBER_SOFT
                : (st.equals("Inactiva") ? NEUTRAL_SOFT : ROCOTO_SOFT));
            int stText = st.equals("Publicada") ? GREEN
                : (st.equals("Programada") ? AMBER
                : (st.equals("Inactiva") ? MUTED : ROCOTO));

            String promoName = text(offer, "name");
            String desc = text(offer, "description").trim();
            var promoPrice = amount(offer);
            var individualTotal = total(inc);

            // Compact Header: Icon/Cover + Title & Status + Price
            LinearLayout topRow = app.row();
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout thumbBox = new FrameLayout(app);
            thumbBox.setBackground(app.rounded(SOFT, 12, LINE));
            thumbBox.setClipToOutline(true);
            thumbBox.setLayoutParams(new LinearLayout.LayoutParams(app.dp(44), app.dp(44)));
            ImageView fallbackIcon = app.iconView(R.drawable.ic_promo, 22, TERRACOTTA);
            FrameLayout.LayoutParams fbLp = new FrameLayout.LayoutParams(app.dp(22), app.dp(22));
            fbLp.gravity = Gravity.CENTER;
            thumbBox.addView(fallbackIcon, fbLp);
            ImageView promoImg = new ImageView(app);
            promoImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            promoImg.setContentDescription("Imagen de " + promoName);
            thumbBox.addView(promoImg, new FrameLayout.LayoutParams(-1, -1));
            app.loadRemoteImage(promoImg, fallbackIcon, text(offer, "image_url"));
            topRow.addView(thumbBox);
            app.gap(topRow, 10);

            LinearLayout titleCol = app.column();
            TextView nameTv = app.text(promoName.isEmpty() ? "Promoción sin título" : promoName, 15, INK, true);
            nameTv.setMaxLines(2);
            nameTv.setEllipsize(TextUtils.TruncateAt.END);
            app.add(titleCol, nameTv);
            app.gap(titleCol, 4);

            LinearLayout badgeRow = app.row();
            badgeRow.addView(app.badge(st, stBg, stText, 0));
            if (!inc.isEmpty()) {
                app.gap(badgeRow, 5);
                badgeRow.addView(app.badge(
                    inc.size() == 1 ? "1 producto" : inc.size() + " productos",
                    SOFT, TERRACOTTA, 0));
            }
            app.add(titleCol, badgeRow);
            app.weight(topRow, titleCol);
            app.gap(topRow, 8);

            LinearLayout priceCol = app.column();
            priceCol.setGravity(Gravity.END);
            TextView priceTv = app.text(money(promoPrice), 16, TERRACOTTA, true);
            priceTv.setSingleLine(true);
            priceCol.addView(priceTv);
            if (!inc.isEmpty() && individualTotal.compareTo(promoPrice) > 0) {
                TextView saveTv = app.text("Ahorra " + money(individualTotal.subtract(promoPrice)), 10, GREEN, true);
                saveTv.setSingleLine(true);
                priceCol.addView(saveTv);
            }
            topRow.addView(priceCol);
            topRow.setOnClickListener(v -> app.promotionEditor(offer));
            app.add(card, topRow);

            // Optional concise description
            if (!desc.isEmpty()) {
                app.gap(card, 7);
                TextView descTv = app.text(desc, 12, MUTED, false);
                descTv.setMaxLines(2);
                descTv.setEllipsize(TextUtils.TruncateAt.END);
                app.add(card, descTv);
            }

            // Compact Cream strip with included product names & individual total
            int totalIds = Editors.ids(offer).size();
            if (!inc.isEmpty() || totalIds > 0) {
                app.gap(card, 7);
                LinearLayout strip = app.column();
                strip.setBackground(app.rounded(BG, 10, LINE));
                app.pad(strip, 10, 7, 10, 7);

                if (!inc.isEmpty()) {
                    StringBuilder names = new StringBuilder();
                    for (int i = 0; i < inc.size(); i++) {
                        if (i > 0) names.append(" • ");
                        names.append(text(inc.get(i), "name"));
                    }
                    TextView incNames = app.text("Incluye: " + names, 12, INK, false);
                    incNames.setMaxLines(2);
                    incNames.setEllipsize(TextUtils.TruncateAt.END);
                    app.add(strip, incNames);

                    app.gap(strip, 2);
                    TextView incTotal = app.text("Valor individual: " + money(individualTotal), 11, GREEN, true);
                    app.add(strip, incTotal);
                }

                if (inc.size() < totalIds) {
                    if (!inc.isEmpty()) app.gap(strip, 2);
                    app.add(strip, app.text("Contiene productos eliminados del catálogo", 11, ROCOTO, true));
                } else if (st.equals("No disponible") && !inc.isEmpty()) {
                    app.gap(strip, 2);
                    app.add(strip, app.text("Revisa disponibilidad de productos o precio", 11, ROCOTO, false));
                }
                app.add(card, strip);
            }

            // Optional active window summary
            String startStr = text(offer, "starts_at").trim();
            String endStr = text(offer, "ends_at").trim();
            if (!startStr.isEmpty() || !endStr.isEmpty()) {
                app.gap(card, 6);
                StringBuilder win = new StringBuilder("Vigencia: ");
                if (!startStr.isEmpty()) win.append("desde ").append(formatCompactDate(startStr));
                if (!startStr.isEmpty() && !endStr.isEmpty()) win.append(" · ");
                if (!endStr.isEmpty()) win.append("hasta ").append(formatCompactDate(endStr));
                TextView winTv = app.text(win.toString(), 11, MUTED, false);
                winTv.setSingleLine(true);
                winTv.setEllipsize(TextUtils.TruncateAt.END);
                app.add(card, winTv);
            }

            // Compact bottom divider + 3 balanced actions
            app.gap(card, 8);
            View divider = new View(app);
            divider.setBackgroundColor(0xFFEDE3DA);
            card.addView(divider, -1, app.dp(1));
            app.gap(card, 8);

            boolean isActive = offer.optBoolean("active");
            LinearLayout actions = app.row();
            app.weight(actions, compactCardAction(app, "Editar", R.drawable.ic_edit, false,
                () -> app.promotionEditor(offer)));
            app.gap(actions, 6);
            app.weight(actions, promotionStateAction(app, isActive,
                () -> app.mutate(
                    () -> app.api.change("PATCH", where(PROMOTIONS, id(offer)), Supabase.object("active", !isActive), 1),
                    isActive ? "Promoción pausada." : "Promoción activada.")));
            app.gap(actions, 6);
            app.weight(actions, compactCardAction(app, "Eliminar", R.drawable.ic_trash_outline, true,
                () -> app.confirm(
                    "¿Eliminar «" + promoName + "»?",
                    () -> app.mutate(
                        () -> app.api.change("DELETE", where(PROMOTIONS, id(offer)), null, 1),
                        "Promoción eliminada."))));
            app.add(card, actions);
        }
    }

    private static String formatCompactDate(String iso) {
        try {
            String full = localDate(iso);
            return full.length() >= 16 ? full.substring(0, 16) : full;
        } catch (Exception ignored) {
            return iso;
        }
    }

    private static LinearLayout promotionStateAction(MainActivity app, boolean isActive, Runnable onClick) {
        LinearLayout btn = app.row();
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight(app.dp(36));
        int bg = isActive ? SOFT : GREEN_SOFT;
        int stroke = isActive ? LINE : 0x331A7F45;
        int rippleColor = isActive ? 0x229B3B24 : 0x221A7F45;
        int tint = isActive ? TERRACOTTA : GREEN;
        btn.setBackground(app.ripple(bg, 10, stroke, rippleColor));
        app.pad(btn, 6, 6, 6, 6);
        btn.addView(app.iconView(isActive ? R.drawable.ic_clock : R.drawable.ic_check_circle, 15, tint));
        app.gap(btn, 5);
        TextView tv = app.text(isActive ? "Pausar" : "Activar", 12, isActive ? TERRACOTTA : GREEN, true);
        tv.setMaxLines(1);
        btn.addView(tv);
        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }
}
