package pe.premiumpe.admin;

import android.app.AlertDialog;
import android.graphics.Color;
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

    static void render(MainActivity app) {
        LinearLayout body = app.body;

        // Header row
        LinearLayout headRow = app.row();
        LinearLayout titleCol = app.column();
        app.add(titleCol, app.text(app.showPromotions ? "Promociones" : "Productos", 22, INK, true));
        app.add(titleCol, app.text(
            app.showPromotions ? "Combos y ofertas de tu tienda" : "Administra los servicios de tu tienda",
            12, MUTED, false));
        app.weight(headRow, titleCol);

        int count = app.showPromotions ? app.offers.size() : app.products.size();
        headRow.addView(app.badge(count + (count == 1 ? " ítem" : " ítems"), SOFT, TERRACOTTA, LINE));
        app.add(body, headRow);
        app.gap(body, 10);

        // Search + Filter row (for products)
        if (!app.showPromotions) {
            LinearLayout searchRow = app.row();

            LinearLayout searchBox = app.row();
            searchBox.setBackground(app.rounded(PAPER, 13, LINE));
            searchBox.setMinimumHeight(app.dp(46));
            app.pad(searchBox, 12, 2, 6, 2);
            searchBox.addView(app.iconView(R.drawable.ic_search, 18, MUTED));
            app.gap(searchBox, 8);

            EditText query = new EditText(app);
            query.setBackground(null);
            query.setSingleLine(true);
            query.setTextSize(14);
            query.setTextColor(INK);
            query.setHintTextColor(0xFF9C877E);
            query.setHint("Buscar producto...");
            query.setInputType(InputType.TYPE_CLASS_TEXT);
            query.setText(app.search);
            query.setSelection(app.search.length());
            query.setMinHeight(app.dp(44));
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

            boolean hasExtraFilter = app.category > 0 || app.sort > 0 || app.status > 2;
            FrameLayout filterBtnWrap = new FrameLayout(app);
            filterBtnWrap.setBackground(app.ripple(hasExtraFilter ? SOFT : PAPER, 13, hasExtraFilter ? TERRACOTTA : LINE, 0x229B3B24));
            filterBtnWrap.setLayoutParams(new LinearLayout.LayoutParams(app.dp(46), app.dp(46)));
            filterBtnWrap.setContentDescription("Filtros y orden");
            ImageView filterIcon = app.iconView(R.drawable.ic_filter, 20, hasExtraFilter ? TERRACOTTA : INK);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(app.dp(20), app.dp(20));
            iconLp.gravity = Gravity.CENTER;
            filterBtnWrap.addView(filterIcon, iconLp);
            if (hasExtraFilter) {
                View dot = new View(app);
                dot.setBackground(app.rounded(TERRACOTTA, 4, 0));
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(app.dp(7), app.dp(7));
                dotLp.gravity = Gravity.TOP | Gravity.END;
                dotLp.setMargins(0, app.dp(7), app.dp(7), 0);
                filterBtnWrap.addView(dot, dotLp);
            }
            filterBtnWrap.setOnClickListener(v -> showFilterDialog(app));
            searchRow.addView(filterBtnWrap);
            app.add(body, searchRow);
            app.gap(body, 10);

            app.watch(query, () -> {
                app.search = query.getText().toString();
                clearBtn.setVisibility(app.search.isEmpty() ? View.GONE : View.VISIBLE);
                renderResults(app);
            });
        }

        // Horizontal Filter Pills (Todos, Disponibles, Agotados, Promociones, Ocultos, Destacados)
        HorizontalScrollView chipScroll = new HorizontalScrollView(app);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = app.row();
        addFilterChip(app, chipRow, "Todos", !app.showPromotions && app.status == 0, () -> {
            app.showPromotions = false; app.status = 0; app.showHome();
        });
        addFilterChip(app, chipRow, "Disponibles", !app.showPromotions && app.status == 1, () -> {
            app.showPromotions = false; app.status = 1; app.showHome();
        });
        addFilterChip(app, chipRow, "Agotados", !app.showPromotions && app.status == 2, () -> {
            app.showPromotions = false; app.status = 2; app.showHome();
        });
        addFilterChip(app, chipRow, "Promociones", app.showPromotions, () -> {
            app.showPromotions = true; app.showHome();
        });
        addFilterChip(app, chipRow, "Ocultos", !app.showPromotions && app.status == 3, () -> {
            app.showPromotions = false; app.status = 3; app.showHome();
        });
        addFilterChip(app, chipRow, "Destacados", !app.showPromotions && app.status == 5, () -> {
            app.showPromotions = false; app.status = 5; app.showHome();
        });
        chipScroll.addView(chipRow);
        app.add(body, chipScroll);
        app.gap(body, 10);

        if (app.showPromotions) {
            app.add(body, app.iconButton("Nueva promoción", R.drawable.ic_plus, () -> app.promotionEditor(null), true));
            app.gap(body, 12);
            renderPromotions(app, body);
        } else {
            app.add(body, app.iconButton("Agregar producto", R.drawable.ic_plus, () -> app.productEditor(null), true));
            app.gap(body, 10);
            app.results = app.column();
            app.add(body, app.results);
            renderResults(app);
        }
    }

    private static void addFilterChip(MainActivity app, LinearLayout row, String label, boolean active, Runnable onClick) {
        TextView chip = app.text(label, 12, active ? PAPER : INK, active);
        chip.setBackground(app.ripple(active ? TERRACOTTA : PAPER, 20, active ? 0 : LINE, active ? 0x33FFFFFF : 0x229B3B24));
        app.pad(chip, 14, 8, 14, 8);
        chip.setMinHeight(app.dp(36));
        chip.setGravity(Gravity.CENTER);
        chip.setOnClickListener(v -> onClick.run());
        row.addView(chip);
        app.gap(row, 6);
    }

    private static void showFilterDialog(MainActivity app) {
        LinearLayout form = app.column();
        app.pad(form, 18, 12, 18, 4);

        Spinner categoryPicker = app.choices(form, "Categoría", new String[]{"Todas", "Streaming", "Herramientas", "IA"}, app.category, null);
        Spinner statusPicker = app.choices(form, "Estado", SUMMARY_FILTERS, app.status, null);
        Spinner sortPicker = app.choices(form, "Ordenar por", new String[]{
            "Posición", "Nombre", "Precio ascendente", "Precio descendente", "Más recientes"
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

        if (visible.isEmpty()) {
            LinearLayout empty = app.card(app.results);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            app.pad(empty, 20, 24, 20, 24);
            empty.addView(app.iconBadge(R.drawable.ic_products, 48, 24, SOFT, TERRACOTTA, 14));
            app.gap(empty, 10);
            TextView msg = app.text(
                app.catalogLoaded ? "No se encontraron productos con estos filtros." : "Cargando catálogo de productos…",
                14, MUTED, false
            );
            msg.setGravity(Gravity.CENTER);
            app.add(empty, msg);
            return;
        }

        // Utility toolbar: Select all + Reorder toggle
        LinearLayout tools = app.row();
        boolean allSelected = visible.stream().allMatch(p -> app.selected.contains(id(p)));
        TextView selectToggle = app.text(allSelected ? "Deseleccionar todos" : "Seleccionar todos", 12, TERRACOTTA, true);
        selectToggle.setMinHeight(app.dp(36));
        selectToggle.setGravity(Gravity.CENTER_VERTICAL);
        selectToggle.setOnClickListener(v -> {
            if (allSelected) visible.forEach(p -> app.selected.remove(id(p)));
            else visible.forEach(p -> app.selected.add(id(p)));
            renderResults(app);
        });
        app.weight(tools, selectToggle);

        if (app.sort == 0 && visible.size() > 1) {
            TextView reorderBtn = app.text(
                app.reorderMode ? "Listo" : "Reordenar ↑↓",
                12, app.reorderMode ? PAPER : MUTED, true
            );
            reorderBtn.setBackground(app.ripple(
                app.reorderMode ? TERRACOTTA : PAPER, 12, app.reorderMode ? 0 : LINE, 0x229B3B24
            ));
            app.pad(reorderBtn, 10, 6, 10, 6);
            reorderBtn.setOnClickListener(v -> {
                app.reorderMode = !app.reorderMode;
                renderResults(app);
            });
            tools.addView(reorderBtn);
        }
        app.add(app.results, tools);
        app.gap(app.results, 6);

        // Bulk bar when items selected
        if (!app.selected.isEmpty()) {
            LinearLayout bulkBar = app.row();
            bulkBar.setBackground(app.rounded(SOFT, 14, TERRACOTTA));
            app.pad(bulkBar, 12, 8, 10, 8);
            TextView selCount = app.text(app.selected.size() + " seleccionados", 13, INK, true);
            app.weight(bulkBar, selCount);

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

        // Compact product cards
        for (int index = 0; index < visible.size(); index++) {
            JSONObject product = visible.get(index);
            int position = index;

            LinearLayout item = app.column();
            item.setBackground(app.ripple(PAPER, 16, LINE, 0x169B3B24));
            item.setElevation(app.dp(2));
            app.pad(item, 8, 10, 6, 10);
            item.setOnClickListener(v -> app.productEditor(product));

            LinearLayout contentRow = app.row();

            CheckBox check = new CheckBox(app);
            check.setMinimumWidth(app.dp(36));
            check.setMinimumHeight(app.dp(44));
            check.setContentDescription("Seleccionar " + text(product, "name"));
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(TERRACOTTA));
            check.setChecked(app.selected.contains(id(product)));
            check.setOnCheckedChangeListener((v, checked) -> {
                if (checked) app.selected.add(id(product));
                else app.selected.remove(id(product));
                renderResults(app);
            });
            contentRow.addView(check);
            app.gap(contentRow, 4);

            // Thumbnail (54dp) with real cover photo or monogram fallback
            String name = text(product, "name");
            String letter = name.isEmpty() ? "P" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            FrameLayout thumbBox = new FrameLayout(app);
            thumbBox.setBackground(app.rounded(SOFT, 12, LINE));
            thumbBox.setClipToOutline(true);
            thumbBox.setLayoutParams(new LinearLayout.LayoutParams(app.dp(54), app.dp(54)));

            TextView monogram = app.monogram(letter, 54, SOFT, TERRACOTTA);
            thumbBox.addView(monogram, new FrameLayout.LayoutParams(-1, -1));

            ImageView thumbImg = new ImageView(app);
            thumbImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbImg.setContentDescription("Portada de " + name);
            thumbBox.addView(thumbImg, new FrameLayout.LayoutParams(-1, -1));
            app.loadRemoteImage(thumbImg, monogram, text(product, "image_url"));

            contentRow.addView(thumbBox);
            app.gap(contentRow, 10);

            // Info column
            LinearLayout infoCol = app.column();
            TextView nameView = app.text(name, 15, INK, true);
            nameView.setMaxLines(2);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            app.add(infoCol, nameView);
            app.gap(infoCol, 2);

            LinearLayout priceLine = app.row();
            TextView priceView = app.text(money(amount(product)), 14, TERRACOTTA, true);
            priceView.setOnClickListener(v -> app.quickPrice(product));
            priceLine.addView(priceView);
            app.gap(priceLine, 8);
            priceLine.addView(app.text(text(product, "category"), 11, MUTED, false));
            app.add(infoCol, priceLine);
            app.gap(infoCol, 5);

            LinearLayout badgesRow = app.row();
            if (product.optBoolean("available")) {
                badgesRow.addView(app.badge("Disponible", GREEN_SOFT, GREEN, 0));
            } else {
                badgesRow.addView(app.badge("Agotado", ROCOTO_SOFT, ROCOTO, 0));
            }
            if (product.optBoolean("featured")) {
                app.gap(badgesRow, 4);
                badgesRow.addView(app.badge("Destacado", AMBER_SOFT, AMBER, 0));
            }
            if (!product.optBoolean("visible")) {
                app.gap(badgesRow, 4);
                badgesRow.addView(app.badge("Oculto", NEUTRAL_SOFT, MUTED, 0));
            }
            app.add(infoCol, badgesRow);

            app.weight(contentRow, infoCol);

            // Reorder arrows when reorderMode is active
            if (app.reorderMode && app.sort == 0 && visible.size() > 1) {
                LinearLayout arrows = app.column();
                ImageButton up = new ImageButton(app);
                up.setContentDescription("Subir " + name);
                up.setImageDrawable(app.tintedIcon(R.drawable.ic_arrow_up, index > 0 ? TERRACOTTA : 0xFFCCC0B8));
                up.setBackground(app.ripple(SOFT, 10, 0, 0x229B3B24));
                up.setEnabled(index > 0);
                up.setLayoutParams(new LinearLayout.LayoutParams(app.dp(34), app.dp(30)));
                up.setOnClickListener(v -> app.move(visible, position, -1));
                arrows.addView(up);
                app.gap(arrows, 4);
                ImageButton down = new ImageButton(app);
                down.setContentDescription("Bajar " + name);
                down.setImageDrawable(app.tintedIcon(R.drawable.ic_arrow_down, index < visible.size() - 1 ? TERRACOTTA : 0xFFCCC0B8));
                down.setBackground(app.ripple(SOFT, 10, 0, 0x229B3B24));
                down.setEnabled(index < visible.size() - 1);
                down.setLayoutParams(new LinearLayout.LayoutParams(app.dp(34), app.dp(30)));
                down.setOnClickListener(v -> app.move(visible, position, 1));
                arrows.addView(down);
                contentRow.addView(arrows);
            }

            // Overflow menu button (44dp)
            ImageButton moreBtn = new ImageButton(app);
            moreBtn.setContentDescription("Opciones de " + name);
            moreBtn.setImageDrawable(app.tintedIcon(R.drawable.ic_more_vert, MUTED));
            moreBtn.setBackground(app.ripple(0x00000000, 22, 0, 0x229B3B24));
            moreBtn.setMinimumWidth(app.dp(40));
            moreBtn.setMinimumHeight(app.dp(44));
            moreBtn.setLayoutParams(new LinearLayout.LayoutParams(app.dp(40), app.dp(44)));
            moreBtn.setOnClickListener(v -> app.productActions(product));
            contentRow.addView(moreBtn);

            app.add(item, contentRow);
            app.add(app.results, item);
            app.gap(app.results, 8);
        }
    }

    private static void renderPromotions(MainActivity app, LinearLayout container) {
        if (app.promotionsError != null) {
            LinearLayout err = app.card(container);
            app.add(err, app.text(app.promotionsError, 13, ROCOTO, false));
            return;
        }
        if (app.offers.isEmpty()) {
            LinearLayout empty = app.card(container);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            app.pad(empty, 20, 24, 20, 24);
            empty.addView(app.iconBadge(R.drawable.ic_promo, 48, 24, SOFT, TERRACOTTA, 14));
            app.gap(empty, 10);
            app.add(empty, app.text("Todavía no hay promociones creadas.", 14, MUTED, false));
            return;
        }

        for (JSONObject offer : app.offers) {
            LinearLayout card = app.card(container);
            String st = promotionStatus(offer, app.included(Editors.ids(offer)));
            int stBg = st.equals("Publicada") ? GREEN_SOFT
                : (st.equals("Programada") ? AMBER_SOFT
                : (st.equals("Inactiva") ? NEUTRAL_SOFT : ROCOTO_SOFT));
            int stText = st.equals("Publicada") ? GREEN
                : (st.equals("Programada") ? AMBER
                : (st.equals("Inactiva") ? MUTED : ROCOTO));

            LinearLayout headerRow = app.row();
            headerRow.addView(app.badge(st, stBg, stText, 0));
            app.weight(headerRow, new View(app));
            headerRow.addView(app.text(money(amount(offer)), 17, TERRACOTTA, true));
            app.add(card, headerRow);
            app.gap(card, 8);

            app.add(card, app.text(text(offer, "name"), 16, INK, true));
            if (!text(offer, "description").isEmpty()) {
                app.gap(card, 3);
                app.note(card, text(offer, "description"));
            }

            List<JSONObject> inc = app.included(Editors.ids(offer));
            if (!inc.isEmpty()) {
                app.gap(card, 6);
                app.add(card, app.text(
                    inc.size() + " productos · Total individual " + money(total(inc)),
                    12, GREEN, true
                ));
            }
            app.gap(card, 10);

            LinearLayout actions = app.row();
            app.weight(actions, app.iconButton("Editar", R.drawable.ic_edit, () -> app.promotionEditor(offer), false));
            app.gap(actions, 6);
            if (offer.optBoolean("active")) {
                app.weight(actions, app.button("Pausar", () -> app.mutate(
                    () -> app.api.change("PATCH", where(PROMOTIONS, id(offer)), Supabase.object("active", false), 1),
                    "Promoción pausada."), false));
            } else {
                app.weight(actions, app.button("Activar", () -> app.mutate(
                    () -> app.api.change("PATCH", where(PROMOTIONS, id(offer)), Supabase.object("active", true), 1),
                    "Promoción activada."), false));
            }
            app.gap(actions, 6);
            ImageButton delBtn = new ImageButton(app);
            delBtn.setContentDescription("Eliminar " + text(offer, "name"));
            delBtn.setImageDrawable(app.tintedIcon(R.drawable.ic_trash, ROCOTO));
            delBtn.setBackground(app.ripple(ROCOTO_SOFT, 13, 0x33B33022, 0x22B33022));
            delBtn.setLayoutParams(new LinearLayout.LayoutParams(app.dp(48), app.dp(48)));
            delBtn.setOnClickListener(v -> app.confirm(
                "¿Eliminar «" + text(offer, "name") + "»?",
                () -> app.mutate(
                    () -> app.api.change("DELETE", where(PROMOTIONS, id(offer)), null, 1),
                    "Promoción eliminada.")
            ));
            actions.addView(delBtn);
            app.add(card, actions);
        }
    }
}
