package pe.premiumpe.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static pe.premiumpe.admin.Catalog.*;
import static pe.premiumpe.admin.MainActivity.*;
import static pe.premiumpe.admin.Supabase.object;
import static pe.premiumpe.admin.UiActivity.*;

final class Editors {
    private Editors() {}

    enum SettingsSection {
        STORE_INFO("Datos de la tienda", "Datos de la tienda", "Nombre, WhatsApp, moneda y pagos", R.drawable.ic_store),
        HERO("Portada (Hero)", "Portada", "Textos del banner principal (Hero)", R.drawable.ic_image),
        SUPPORT_PAYMENTS("Atención y soporte", "Atención y Soporte", "Horarios, respuesta y garantía", R.drawable.ic_shield),
        FAQ("Preguntas frecuentes", "Preguntas Frecuentes (FAQ)", "Gestiona las preguntas y respuestas", R.drawable.ic_help),
        SOCIAL("Redes sociales", "Redes Sociales", "Enlaces de Instagram, TikTok y Facebook", R.drawable.ic_share);

        final String menuTitle;
        final String pageTitle;
        final String subtitle;
        final int icon;

        SettingsSection(String menuTitle, String pageTitle, String subtitle, int icon) {
            this.menuTitle = menuTitle;
            this.pageTitle = pageTitle;
            this.subtitle = subtitle;
            this.icon = icon;
        }
    }

    static Set<String> ids(JSONObject row) {
        Set<String> ids = new LinkedHashSet<>();
        JSONArray values = array(row, "product_ids");
        for (int i = 0; i < values.length(); i++) ids.add(values.optString(i));
        return ids;
    }

    abstract static class Editor {
        final MainActivity app;
        final JSONObject original;
        LinearLayout form;
        TextView error;
        String initial;
        final Map<String, EditText> fields = new LinkedHashMap<>();

        Editor(MainActivity app, JSONObject source) {
            this.app = app;
            original = source == null ? new JSONObject() : source;
        }

        abstract String title();
        abstract void build();
        abstract Object snapshot();
        abstract MainActivity.Operation prepareSave() throws Exception;

        String saveButtonLabel() { return "Guardar"; }

        void show() {
            app.pageShell(app.editorTopBar(title(), this::cancel), true);
            form = app.body;
            build();
            initial = snapshot().toString();

            LinearLayout footer = app.column();
            footer.setBackgroundColor(PAPER);
            footer.setElevation(app.dp(6));
            View hairline = new View(app);
            hairline.setBackgroundColor(LINE);
            footer.addView(hairline, -1, app.dp(1));

            LinearLayout innerFooter = app.column();
            app.pad(innerFooter, 14, 10, 14, 12);

            error = app.text("", 13, ROCOTO, true);
            error.setBackground(app.rounded(ROCOTO_SOFT, 10, 0x44B33022));
            app.pad(error, 12, 8, 12, 8);
            error.setVisibility(View.GONE);
            error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            app.add(innerFooter, error);

            LinearLayout buttons = app.row();
            app.weight(buttons, app.button("Cancelar", this::cancel, false));
            app.gap(buttons, 10);
            app.weight(buttons, app.iconButton(saveButtonLabel(), R.drawable.ic_check, () -> {
                app.hideKeyboard();
                showError("");
                try {
                    app.perform("Guardando cambios…", prepareSave(), () -> app.saved("Cambios guardados."), error);
                } catch (Exception invalid) {
                    showError(invalid.getMessage());
                }
            }, true));
            app.add(innerFooter, buttons);
            footer.addView(innerFooter, -1, -2);
            app.add(app.root, footer);
        }

        void showError(String message) {
            if (error == null) return;
            if (message == null || message.trim().isEmpty()) {
                error.setText("");
                error.setVisibility(View.GONE);
            } else {
                error.setText(message);
                error.setVisibility(View.VISIBLE);
            }
        }

        EditText field(LinearLayout parent, String key, String label, String value, int type, int length) {
            return iconField(parent, key, label, 0, value, "", type, length);
        }

        EditText iconField(LinearLayout parent, String key, String label, int iconRes, String value, String hint, int type, int length) {
            EditText field = app.iconInput(parent, label, iconRes, value, hint, type, false);
            if (length > 0) field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
            app.watch(field, app::recordActivity);
            fields.put(key, field);
            return field;
        }

        String value(String key) {
            EditText f = fields.get(key);
            return f == null ? "" : f.getText().toString().trim();
        }

        Map<String, String> values() {
            Map<String, String> values = new LinkedHashMap<>();
            fields.keySet().forEach(key -> values.put(key, value(key)));
            return values;
        }

        void cancel() {
            if (initial.equals(snapshot().toString())) { app.closeEditor(); return; }
            new AlertDialog.Builder(app)
                .setTitle("¿Descartar cambios?")
                .setMessage("Los cambios pendientes se perderán al salir del editor.")
                .setNegativeButton("Seguir editando", null)
                .setPositiveButton("Descartar", (d, w) -> app.closeEditor())
                .show();
        }

        void preview(String title, String detail, String url, Bitmap bitmap, String price, List<String> lines, int accent) {
            LinearLayout view = app.column();
            app.pad(view, 18, 12, 18, 18);
            if (bitmap != null || !url.isEmpty()) {
                ImageView cover = cover(view);
                if (bitmap != null) cover.setImageBitmap(bitmap);
                else app.loadRemoteImage(cover, null, url);
            }
            app.heading(view, title.isEmpty() ? "Sin nombre" : title);
            if (!detail.isEmpty()) app.note(view, detail);
            app.gap(view, 10);
            app.add(view, app.text(price, 21, accent, true));
            app.gap(view, 6);
            for (String line : lines) {
                app.note(view, line);
                app.gap(view, 2);
            }
            ScrollView content = new ScrollView(app);
            content.addView(view);
            new AlertDialog.Builder(app).setTitle("Vista previa").setView(content).setPositiveButton("Cerrar", null).show();
        }

        ImageView cover(LinearLayout panel) {
            ImageView view = new ImageView(app);
            view.setContentDescription("Portada");
            view.setBackground(app.rounded(SOFT, 14, LINE));
            view.setClipToOutline(true);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            panel.addView(view, -1, app.dp(156));
            app.gap(panel, 10);
            return view;
        }

        int position(String[] options, String value) { return Math.max(0, Arrays.asList(options).indexOf(value)); }

        String formatted(String amount) {
            try { return money(price(amount)); }
            catch (IOException invalid) { return "Precio pendiente"; }
        }
    }

    static final class Product extends Editor {
        private static final int GALLERY = 300, CAMERA = 301;
        private static final int MAX_BYTES = 2 * 1024 * 1024;
        private final String newId = UUID.randomUUID().toString();
        private Spinner category, delivery, accent;
        private Switch available, visible, featured;
        private Bitmap pendingImage;
        private String remoteImage, uploadName;
        private ImageView cover;

        Product(MainActivity app, JSONObject source) {
            super(app, source);
            remoteImage = text(original, "image_url");
        }

        @Override String title() { return original.has("id") ? "Editar producto" : "Nuevo producto"; }

        @Override void build() {
            LinearLayout details = app.card(form);
            app.heading(details, "Información del producto");
            field(details, "name", "Nombre", text(original, "name"), LINE_TEXT, 0);
            field(details, "description", "Descripción", text(original, "description"), MULTILINE, 0);
            category = app.choices(details, "Categoría", CATEGORIES, position(CATEGORIES, text(original, "category")), null);
            delivery = app.choices(details, "Modalidad de entrega", DELIVERY_LABELS, position(DELIVERY, text(original, "delivery_type")), null);
            field(details, "price", "Precio en soles (S/)", text(original, "price"), MONEY, 0);

            LinearLayout benefits = app.card(form);
            app.heading(benefits, "Qué incluye");
            JSONArray includes = array(original, "includes");
            for (int i = 0; i < 3; i++) {
                field(benefits, "benefit" + i, "Beneficio " + (i + 1),
                    includes.isNull(i) ? "" : includes.optString(i), LINE_TEXT, 0);
            }

            LinearLayout art = app.card(form);
            app.heading(art, "Portada y color");
            cover = cover(art);
            renderCover();
            LinearLayout photos = app.row();
            app.weight(photos, app.iconButton("Galería", R.drawable.ic_image, this::gallery, false));
            app.gap(photos, 8);
            app.weight(photos, app.iconButton("Cámara", R.drawable.ic_camera, this::camera, false));
            app.add(art, photos);
            app.gap(art, 8);
            app.add(art, app.dangerButton("Quitar portada", R.drawable.ic_trash, () -> {
                remoteImage = "";
                pendingImage = null;
                uploadName = null;
                renderCover();
            }));
            app.gap(art, 10);
            accent = app.choices(art, "Acento visual", ACCENT_LABELS, position(ACCENTS, text(original, "accent")), null);

            LinearLayout publication = app.card(form);
            app.heading(publication, "Estado y visibilidad");
            available = app.toggle(publication, "Disponible para venta", original.optBoolean("available", true));
            visible = app.toggle(publication, "Visible en tienda", original.optBoolean("visible", true));
            featured = app.toggle(publication, "Destacado en portada", original.optBoolean("featured", false));
            app.gap(publication, 6);
            int next = app.products.stream().mapToInt(p -> p.optInt("sort_order")).max().orElse(0);
            field(publication, "sort_order", "Posición", String.valueOf(original.optInt("sort_order",
                next == Integer.MAX_VALUE ? next : next + 1)), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED, 11);

            app.add(form, app.button("Ver vista previa", () -> {
                List<String> lines = new ArrayList<>();
                lines.add(CATEGORIES[category.getSelectedItemPosition()] + " · " + delivery.getSelectedItem());
                lines.add((available.isChecked() ? "Disponible" : "Agotado") + " · " +
                    (visible.isChecked() ? "Visible" : "Oculto") + (featured.isChecked() ? " · Destacado" : ""));
                for (String benefit : benefits()) lines.add("• " + benefit);
                int[] colors = {TERRACOTTA, GREEN, AMBER};
                preview(value("name"), value("description"), remoteImage, pendingImage, formatted(value("price")), lines,
                    colors[accent.getSelectedItemPosition()]);
            }, false));
        }

        private List<String> benefits() {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < 3; i++) if (!value("benefit" + i).isEmpty()) result.add(value("benefit" + i));
            return result;
        }

        private void renderCover() {
            cover.setTag(null);
            cover.setImageDrawable(null);
            if (pendingImage != null) {
                cover.setImageBitmap(pendingImage);
                cover.setVisibility(View.VISIBLE);
            } else if (!remoteImage.isEmpty()) {
                app.loadRemoteImage(cover, null, remoteImage);
            } else {
                cover.setVisibility(View.GONE);
            }
        }

        private void gallery() {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
                app.startActivityForResult(intent, GALLERY);
            } catch (Exception error) { app.alert("La galería no está disponible."); }
        }

        private void camera() {
            try {
                File output = new File(app.getCacheDir(), "cover.jpg");
                try (var clear = new java.io.FileOutputStream(output)) { clear.flush(); }
                Uri uri = Uri.parse("content://" + app.getPackageName() + ".capture/cover.jpg");
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("Portada", uri));
                app.startActivityForResult(intent, CAMERA);
            } catch (Exception error) { app.alert("La cámara no está disponible."); }
        }

        void photoResult(int request, int result, Intent data) {
            if (result != Activity.RESULT_OK || (request != GALLERY && request != CAMERA)) return;
            File capture = new File(app.getCacheDir(), "cover.jpg");
            Uri uri = data == null ? null : data.getData();
            if (request == GALLERY && uri == null) return;
            Bitmap[] decoded = new Bitmap[1];
            app.perform("Procesando imagen…", () -> {
                try {
                    ImageDecoder.Source source = request == CAMERA ? ImageDecoder.createSource(capture)
                        : ImageDecoder.createSource(app.getContentResolver(), uri);
                    decoded[0] = ImageDecoder.decodeBitmap(source, (decoder, info, image) -> {
                        int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                        double scale = Math.min(1.0, 1400.0 / Math.max(width, height));
                        decoder.setTargetSize(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
                } finally {
                    if (request == CAMERA) capture.delete();
                }
            }, () -> {
                pendingImage = decoded[0];
                uploadName = UUID.randomUUID() + ".jpg";
                renderCover();
            }, error);
        }

        @Override Object snapshot() {
            return Arrays.asList(values(), category.getSelectedItemPosition(), delivery.getSelectedItemPosition(),
                accent.getSelectedItemPosition(), available.isChecked(), visible.isChecked(), featured.isChecked(),
                remoteImage, uploadName);
        }

        @Override MainActivity.Operation prepareSave() throws Exception {
            String productName = name(value("name"));
            BigDecimal amount = price(value("price"));
            int order;
            try { order = Integer.parseInt(value("sort_order")); }
            catch (NumberFormatException invalid) { throw new IOException("Ingresa una posición entera válida."); }
            JSONObject row = object("name", productName, "description", value("description"), "price", amount,
                "category", CATEGORIES[category.getSelectedItemPosition()], "delivery_type", DELIVERY[delivery.getSelectedItemPosition()],
                "accent", ACCENTS[accent.getSelectedItemPosition()], "includes", new JSONArray(benefits()),
                "available", available.isChecked(), "visible", visible.isChecked(), "featured", featured.isChecked(),
                "sort_order", order, "image_url", remoteImage.isEmpty() ? null : remoteImage);
            return () -> {
                if (pendingImage != null) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    if (!pendingImage.compress(Bitmap.CompressFormat.JPEG, 86, buffer) || buffer.size() > MAX_BYTES) {
                        throw new IOException("Elige una portada de hasta 2 MB.");
                    }
                    remoteImage = app.api.upload(uploadName, "image/jpeg", buffer.toByteArray());
                    pendingImage = null;
                    row.put("image_url", remoteImage);
                }
                if (original.has("id")) app.api.change("PATCH", where(PRODUCTS, id(original)), row, 1);
                else app.api.change("POST", PRODUCTS + "?on_conflict=id", row.put("id", newId), 1);
            };
        }
    }

    static final class Promotion extends Editor {
        private final Set<String> selected;
        private Switch active;
        private TextView totals;

        Promotion(MainActivity app, JSONObject source) {
            super(app, source);
            selected = ids(original);
        }

        @Override String title() { return original.has("id") ? "Editar promoción" : "Nueva promoción"; }

        @Override void build() {
            LinearLayout details = app.card(form);
            app.heading(details, "Datos del combo");
            field(details, "name", "Nombre", text(original, "name"), LINE_TEXT, 120);
            field(details, "description", "Descripción (opcional)", text(original, "description"), MULTILINE, 1000);
            EditText price = field(details, "price", "Precio promocional en soles (S/)", text(original, "price"), MONEY, 0);
            field(details, "image_url", "URL de imagen HTTPS (opcional)", text(original, "image_url"), URL, 2048);

            LinearLayout schedule = app.card(form);
            app.heading(schedule, "Fechas en hora de Lima");
            dateField(schedule, "starts_at", "Inicio (opcional)");
            dateField(schedule, "ends_at", "Finalización (opcional)");

            LinearLayout items = app.card(form);
            app.heading(items, "Productos del combo");
            app.note(items, "Selecciona al menos dos productos.");
            app.gap(items, 6);
            totals = app.text("", 14, GREEN, true);
            totals.setBackground(app.rounded(GREEN_SOFT, 12, 0));
            app.pad(totals, 12, 10, 12, 10);
            app.add(items, totals);
            app.gap(items, 8);

            if (app.included(selected).size() != selected.size()) {
                app.add(items, app.dangerButton("Quitar productos eliminados", R.drawable.ic_trash, () -> {
                    selected.retainAll(app.products.stream().map(Catalog::id).collect(java.util.stream.Collectors.toSet()));
                    updateTotals();
                }));
                app.gap(items, 8);
            }
            for (JSONObject product : app.products) {
                CheckBox box = new CheckBox(app);
                box.setMinHeight(app.dp(48));
                box.setTextColor(INK);
                box.setTextSize(14);
                box.setButtonTintList(ColorStateList.valueOf(TERRACOTTA));
                box.setText(text(product, "name") + " · " + money(amount(product)) + (canBuy(product) ? "" : " · No disponible"));
                box.setChecked(selected.contains(id(product)));
                box.setOnCheckedChangeListener((view, checked) -> {
                    if (checked) selected.add(id(product)); else selected.remove(id(product));
                    updateTotals();
                });
                app.add(items, box);
            }

            LinearLayout pubCard = app.card(form);
            active = app.toggle(pubCard, "Activar promoción al guardar", original.optBoolean("active"));

            app.add(form, app.button("Ver vista previa", () -> {
                List<String> lines = new ArrayList<>();
                lines.add(totals.getText().toString());
                app.included(selected).forEach(product -> lines.add("• " + text(product, "name")));
                preview(value("name"), value("description"), https(value("image_url")) ? value("image_url") : "",
                    null, formatted(value("price")), lines, TERRACOTTA);
            }, false));
            app.watch(price, this::updateTotals);
            updateTotals();
        }

        private void dateField(LinearLayout schedule, String key, String label) {
            EditText value = iconField(schedule, key, label, R.drawable.ic_calendar,
                localDate(text(original, key)), "AAAA-MM-DD HH:MM", InputType.TYPE_CLASS_TEXT, 19);
            LinearLayout controls = app.row();
            app.weight(controls, app.iconButton("Elegir fecha", R.drawable.ic_calendar, () -> {
                java.time.LocalDateTime initial = java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima"));
                try {
                    Instant saved = timestamp(value.getText().toString());
                    if (saved != null) initial = saved.atZone(java.time.ZoneId.of("America/Lima")).toLocalDateTime();
                } catch (IOException ignored) {}
                int hour = initial.getHour(), minute = initial.getMinute();
                new android.app.DatePickerDialog(app, (picker, year, month, day) ->
                    new android.app.TimePickerDialog(app, (time, h, m) ->
                        value.setText(String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d",
                            year, month + 1, day, h, m)), hour, minute, true).show(),
                    initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
            }, false));
            app.gap(controls, 8);
            app.weight(controls, app.button("Quitar", () -> value.setText(""), false));
            app.add(schedule, controls);
            app.gap(schedule, 10);
        }

        private void updateTotals() {
            BigDecimal originalTotal = total(app.included(selected));
            String savings;
            try { savings = money(originalTotal.subtract(price(value("price"))).max(BigDecimal.ZERO)); }
            catch (IOException invalid) { savings = "—"; }
            totals.setText("Total individual: " + money(originalTotal) + "  ·  Ahorro: " + savings);
        }

        @Override Object snapshot() { return Arrays.asList(values(), active.isChecked(), new ArrayList<>(selected)); }

        @Override MainActivity.Operation prepareSave() throws Exception {
            List<JSONObject> products = app.included(selected);
            if (selected.size() < 2 || products.size() != selected.size()) {
                throw new IOException("Selecciona al menos dos productos existentes.");
            }
            String name = name(value("name"));
            BigDecimal amount = price(value("price"));
            Instant start = timestamp(value("starts_at")), end = timestamp(value("ends_at"));
            if (start != null && end != null && !end.isAfter(start)) {
                throw new IOException("La finalización debe ser posterior al inicio.");
            }
            String image = value("image_url");
            if (!image.isEmpty() && !https(image)) throw new IOException("La imagen debe usar una dirección HTTPS.");
            if (active.isChecked() && (amount.compareTo(total(products)) >= 0 || !products.stream().allMatch(Catalog::canBuy))) {
                throw new IOException("Para publicar, elige productos visibles con disponibilidad y un precio inferior al total individual.");
            }
            JSONObject details = object("name", name, "description", value("description"), "image_url", image,
                "price", amount, "active", active.isChecked(), "starts_at", start == null ? null : start.toString(),
                "ends_at", end == null ? null : end.toString());
            JSONObject args = object("p_id", original.opt("id"),
                "p_details", details, "p_products", new JSONArray(selected));
            return () -> app.api.write("POST", "rpc/save_promotion", args);
        }
    }

    static final class Settings extends Editor {
        private final SettingsSection section;
        private final boolean whatsappOnly;
        private final List<Faq> faq = new ArrayList<>();
        private LinearLayout faqRows;

        private static final class Faq {
            final LinearLayout card;
            final TextView numberLabel;
            final TextView previewTitle;
            final ImageView chevron;
            final LinearLayout details;
            final EditText question;
            final EditText answer;
            boolean expanded;

            Faq(LinearLayout card, TextView numberLabel, TextView previewTitle, ImageView chevron,
                LinearLayout details, EditText question, EditText answer, boolean expanded) {
                this.card = card;
                this.numberLabel = numberLabel;
                this.previewTitle = previewTitle;
                this.chevron = chevron;
                this.details = details;
                this.question = question;
                this.answer = answer;
                this.expanded = expanded;
            }
        }

        Settings(MainActivity app, JSONObject source, SettingsSection section, boolean whatsappOnly) {
            super(app, source);
            this.section = section;
            this.whatsappOnly = whatsappOnly;
        }

        @Override String title() { return whatsappOnly ? "Editar WhatsApp" : section.pageTitle; }
        @Override String saveButtonLabel() { return "Guardar Cambios"; }

        private String phone() {
            String digits = text(original, "whatsapp_number").replaceAll("[^0-9]", "");
            return digits.length() == 11 && digits.startsWith("51") ? digits.substring(2) : digits;
        }

        @Override void build() {
            switch (section) {
                case STORE_INFO -> buildStoreInfo();
                case HERO -> buildHero();
                case SUPPORT_PAYMENTS -> buildSupportPayments();
                case FAQ -> buildFaq();
                case SOCIAL -> buildSocial();
            }
        }

        private void buildStoreInfo() {
            LinearLayout card = app.card(form);
            if (!whatsappOnly) {
                field(card, "store_name", "Nombre de la Tienda *", text(original, "store_name"), LINE_TEXT, 0);
            }

            // WhatsApp (+51 prefix pill + phone input)
            app.add(card, app.text("Número de WhatsApp (Perú) *", 13, INK, true));
            app.gap(card, 5);
            LinearLayout phoneRow = app.row();
            TextView prefix = app.text("+51", 14, TERRACOTTA, true);
            prefix.setBackground(app.rounded(SOFT, 12, LINE));
            prefix.setGravity(Gravity.CENTER);
            prefix.setMinimumHeight(app.dp(48));
            app.pad(prefix, 14, 0, 14, 0);
            phoneRow.addView(prefix);
            app.gap(phoneRow, 8);

            LinearLayout phoneInputWrap = app.column();
            EditText phoneEdit = app.iconInput(phoneInputWrap, "", 0, phone(), "942990279", InputType.TYPE_CLASS_PHONE, false);
            app.watch(phoneEdit, app::recordActivity);
            fields.put("whatsapp_number", phoneEdit);
            // Remove trailing default gap inside wrapper
            if (phoneInputWrap.getChildCount() > 1) phoneInputWrap.removeViewAt(phoneInputWrap.getChildCount() - 1);
            app.weight(phoneRow, phoneInputWrap);
            app.add(card, phoneRow);
            app.gap(card, 10);

            app.add(card, app.iconButton("Abrir WhatsApp", R.drawable.ic_external, () -> {
                try {
                    String number = normalizedPhone(value("whatsapp_number"));
                    app.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number)));
                } catch (Exception error) {
                    app.alert("Ingresa un número válido de 9 dígitos y verifica WhatsApp.");
                }
            }, false));

            if (!whatsappOnly) {
                app.gap(card, 12);
                String savedCurrency = text(original, "currency").isEmpty() ? "S/" : text(original, "currency");
                field(card, "currency", "Símbolo de Moneda", savedCurrency, LINE_TEXT, 12);

                EditText payField = field(card, "payment_methods", "Métodos de Pago",
                    text(original, "payment_methods").isEmpty() ? "Yape y Plin" : text(original, "payment_methods"),
                    LINE_TEXT, 0);
                LinearLayout quickPay = app.row();
                for (String preset : new String[]{"Yape y Plin", "Yape, Plin y Transferencia"}) {
                    TextView chip = app.text(preset, 11, TERRACOTTA, true);
                    chip.setBackground(app.ripple(SOFT, 10, LINE, 0x229B3B24));
                    app.pad(chip, 10, 6, 10, 6);
                    chip.setOnClickListener(v -> payField.setText(preset));
                    quickPay.addView(chip);
                    app.gap(quickPay, 6);
                }
                app.add(card, quickPay);
            }
        }

        private void buildHero() {
            LinearLayout card = app.card(form);
            field(card, "hero_badge", "Etiqueta / Badge del Hero", text(original, "hero_badge"), LINE_TEXT, 0);
            field(card, "hero_title", "Título Principal del Hero *", text(original, "hero_title"), LINE_TEXT, 0);
            field(card, "hero_subtitle", "Subtítulo del Hero", text(original, "hero_subtitle"), MULTILINE, 0);
        }

        private void buildSupportPayments() {
            LinearLayout card = app.card(form);
            labeledIconRow(card, R.drawable.ic_clock, AMBER, "Horario de Soporte (Opcional)");
            field(card, "support_hours", "", text(original, "support_hours"), LINE_TEXT, 0);

            labeledIconRow(card, R.drawable.ic_bolt, GREEN, "Tiempo de Respuesta (Opcional)");
            field(card, "response_time", "", text(original, "response_time"), LINE_TEXT, 0);

            field(card, "warranty_text", "Texto de Garantía y Reposición (Opcional)",
                text(original, "warranty_text"), MULTILINE, 0);
        }

        private void labeledIconRow(LinearLayout parent, int iconRes, int tint, String label) {
            LinearLayout row = app.row();
            row.addView(app.iconView(iconRes, 17, tint));
            app.gap(row, 6);
            row.addView(app.text(label, 13, INK, true));
            app.add(parent, row);
            app.gap(parent, 5);
        }

        private void buildFaq() {
            LinearLayout topRow = app.row();
            app.weight(topRow, app.text("Preguntas frecuentes", 14, MUTED, true));
            Button addBtn = app.iconButton("Agregar pregunta", R.drawable.ic_plus, () -> addFaq("", "", true), true);
            addBtn.setTextSize(13);
            addBtn.setMinHeight(app.dp(42));
            addBtn.setMinimumHeight(app.dp(42));
            app.pad(addBtn, 12, 6, 14, 6);
            topRow.addView(addBtn);
            app.add(form, topRow);
            app.gap(form, 12);

            faqRows = app.column();
            app.add(form, faqRows);
            List<JSONObject> list = list(array(original, "faq"));
            for (int i = 0; i < list.size(); i++) {
                JSONObject item = list.get(i);
                addFaq(text(item, "question"), text(item, "answer"), false);
            }
        }

        private void addFaq(String question, String answer, boolean startExpanded) {
            LinearLayout card = app.card(faqRows);

            LinearLayout headerTop = app.row();
            TextView numLabel = app.text("Pregunta #" + (faq.size() + 1), 12, TERRACOTTA, true);
            app.weight(headerTop, numLabel);
            ImageButton delIcon = new ImageButton(app);
            delIcon.setContentDescription("Eliminar pregunta");
            delIcon.setImageDrawable(app.tintedIcon(R.drawable.ic_trash_outline, MUTED));
            delIcon.setBackground(app.ripple(0x00000000, 18, 0, 0x22B33022));
            delIcon.setLayoutParams(new LinearLayout.LayoutParams(app.dp(36), app.dp(36)));
            headerTop.addView(delIcon);
            app.add(card, headerTop);
            app.gap(card, 4);

            LinearLayout previewRow = app.row();
            previewRow.setBackground(app.rounded(BG, 11, LINE));
            previewRow.setMinimumHeight(app.dp(44));
            app.pad(previewRow, 12, 8, 10, 8);
            TextView previewTitle = app.text(question.isEmpty() ? "Nueva pregunta…" : question, 14, INK, false);
            previewTitle.setMaxLines(2);
            previewTitle.setEllipsize(TextUtils.TruncateAt.END);
            app.weight(previewRow, previewTitle);
            app.gap(previewRow, 8);
            ImageView chevron = app.iconView(startExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down, 20, MUTED);
            previewRow.addView(chevron);
            app.add(card, previewRow);

            LinearLayout details = app.column();
            details.setVisibility(startExpanded ? View.VISIBLE : View.GONE);
            app.gap(details, 10);

            EditText qField = app.input(details, "Pregunta", question, LINE_TEXT);
            EditText aField = app.input(details, "Respuesta", answer, MULTILINE);
            app.watch(qField, () -> {
                app.recordActivity();
                String t = qField.getText().toString().trim();
                previewTitle.setText(t.isEmpty() ? "Nueva pregunta…" : t);
            });
            app.watch(aField, app::recordActivity);

            Faq row = new Faq(card, numLabel, previewTitle, chevron, details, qField, aField, startExpanded);
            app.add(card, details);

            previewRow.setOnClickListener(v -> toggleFaq(row));
            delIcon.setOnClickListener(v -> {
                faq.remove(row);
                faqRows.removeView(card);
                renumberFaqs();
            });
            faq.add(row);
        }

        private void renumberFaqs() {
            for (int i = 0; i < faq.size(); i++) {
                faq.get(i).numberLabel.setText("Pregunta #" + (i + 1));
            }
        }

        private void toggleFaq(Faq row) {
            row.expanded = !row.expanded;
            row.details.setVisibility(row.expanded ? View.VISIBLE : View.GONE);
            row.chevron.setImageDrawable(app.tintedIcon(
                row.expanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down, MUTED
            ));
        }

        private void buildSocial() {
            LinearLayout card = app.card(form);
            JSONObject social = original.optJSONObject("social_links");
            if (social == null) social = new JSONObject();

            socialField(card, "instagram", "Instagram URL", R.drawable.ic_instagram, 0xFFE1306C,
                text(social, "instagram"), "https://instagram.com/tu_cuenta");
            socialField(card, "tiktok", "TikTok URL", R.drawable.ic_tiktok, 0xFF1F1B1A,
                text(social, "tiktok"), "https://www.tiktok.com/@premiumpe");
            socialField(card, "facebook", "Facebook URL", R.drawable.ic_facebook, 0xFF1877F2,
                text(social, "facebook"), "https://facebook.com/tu_pagina");
        }

        private void socialField(LinearLayout card, String key, String label, int iconRes, int iconTint,
                                 String current, String hint) {
            LinearLayout row = app.row();
            row.addView(app.iconView(iconRes, 22, iconTint));
            app.gap(row, 12);
            LinearLayout col = app.column();
            EditText edit = app.iconInput(col, label, 0, current, hint, URL, false);
            app.watch(edit, app::recordActivity);
            fields.put(key, edit);
            app.weight(row, col);
            app.add(card, row);
        }

        @Override Object snapshot() {
            List<List<String>> entries = new ArrayList<>();
            for (Faq row : faq) {
                entries.add(List.of(row.question.getText().toString(), row.answer.getText().toString()));
            }
            return Arrays.asList(section.name(), values(), entries);
        }

        private String normalizedPhone(String raw) throws IOException {
            String digits = raw.replaceAll("[^0-9]", "");
            if (digits.matches("51[0-9]{9}")) return digits;
            if (digits.matches("[0-9]{9}")) return "51" + digits;
            throw new IOException("WhatsApp necesita nueve dígitos de Perú.");
        }

        @Override MainActivity.Operation prepareSave() throws Exception {
            JSONObject payload = new JSONObject();
            boolean isNew = !original.has("id");

            if (isNew) {
                payload.put("id", 1);
                String existingPhone = text(original, "whatsapp_number");
                if (!existingPhone.isEmpty()) payload.put("whatsapp_number", existingPhone);
                payload.put("store_name", text(original, "store_name").isEmpty() ? "PremiumPe" : text(original, "store_name"));
                payload.put("hero_title", text(original, "hero_title").isEmpty() ? "PremiumPe" : text(original, "hero_title"));
                payload.put("currency", text(original, "currency").isEmpty() ? "S/" : text(original, "currency"));
                payload.put("payment_methods", text(original, "payment_methods").isEmpty() ? "Yape y Plin" : text(original, "payment_methods"));
            }

            switch (section) {
                case STORE_INFO -> {
                    payload.put("whatsapp_number", normalizedPhone(value("whatsapp_number")));
                    if (!whatsappOnly) {
                        name(value("store_name"));
                        payload.put("store_name", value("store_name"));
                        payload.put("currency", value("currency").isEmpty() ? "S/" : value("currency"));
                        payload.put("payment_methods", value("payment_methods").isEmpty() ? "Yape y Plin" : value("payment_methods"));
                    }
                }
                case HERO -> {
                    if (value("hero_title").isEmpty()) throw new IOException("Ingresa el título de portada.");
                    payload.put("hero_badge", value("hero_badge"));
                    payload.put("hero_title", value("hero_title"));
                    payload.put("hero_subtitle", value("hero_subtitle"));
                }
                case SUPPORT_PAYMENTS -> {
                    payload.put("support_hours", value("support_hours"));
                    payload.put("response_time", value("response_time"));
                    payload.put("warranty_text", value("warranty_text"));
                }
                case FAQ -> {
                    JSONArray entries = new JSONArray();
                    for (Faq row : faq) {
                        String question = row.question.getText().toString().trim();
                        String answer = row.answer.getText().toString().trim();
                        if (question.isEmpty() && answer.isEmpty()) continue;
                        if (question.isEmpty() || answer.isEmpty()) {
                            throw new IOException("Completa la pregunta y la respuesta de cada entrada.");
                        }
                        entries.put(object("question", question, "answer", answer));
                    }
                    payload.put("faq", entries);
                }
                case SOCIAL -> {
                    JSONObject social = original.optJSONObject("social_links");
                    social = social == null ? new JSONObject() : new JSONObject(social.toString());
                    for (String key : new String[]{"instagram", "tiktok", "facebook"}) {
                        String link = value(key);
                        if (!link.isEmpty() && !https(link)) {
                            throw new IOException("Usa enlaces HTTPS para las redes sociales.");
                        }
                        social.put(key, link);
                    }
                    payload.put("social_links", social);
                }
            }

            return () -> {
                if (isNew) app.api.change("POST", "settings", payload, 1);
                else app.api.change("PATCH", "settings?id=eq.1", payload, 1);
            };
        }
    }
}