package pe.premiumpe.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import static pe.premiumpe.admin.Catalog.*;
import static pe.premiumpe.admin.MainActivity.*;
import static pe.premiumpe.admin.Supabase.object;

final class Editors {
    private Editors() {}

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

        void show() {
            app.page(title(), "PremiumPe Admin");
            form = app.body;
            build();
            initial = snapshot().toString();
            LinearLayout footer = app.column();
            app.pad(footer, 12, 4, 12, 5);
            footer.setBackgroundColor(UiActivity.PAPER);
            error = app.text("", 14, UiActivity.RED, true);
            error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            app.add(footer, error);
            LinearLayout buttons = app.row();
            app.weight(buttons, app.button("Cancelar", this::cancel, false));
            app.weight(buttons, app.button("Guardar", () -> {
                app.hideKeyboard();
                try {
                    app.perform(prepareSave(), () -> app.saved("Cambios guardados."), error);
                } catch (Exception invalid) {
                    error.setText(invalid.getMessage());
                }
            }, true));
            app.add(footer, buttons);
            app.add(app.root, footer);
        }
        EditText field(LinearLayout parent, String key, String label, String value, int type, int length) {
            EditText field = app.input(parent, label, value, type);
            if (length > 0) field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
            app.watch(field, app::recordActivity);
            fields.put(key, field);
            return field;
        }
        String value(String key) { return fields.get(key).getText().toString().trim(); }
        Map<String, String> values() {
            Map<String, String> values = new LinkedHashMap<>();
            fields.keySet().forEach(key -> values.put(key, value(key)));
            return values;
        }
        void cancel() {
            if (initial.equals(snapshot().toString())) { app.closeEditor(); return; }
            new AlertDialog.Builder(app).setTitle("¿Descartar cambios?")
                .setMessage("Los cambios pendientes se perderán al salir del editor.")
                .setNegativeButton("Seguir editando", null)
                .setPositiveButton("Descartar", (d, w) -> app.closeEditor()).show();
        }
        void preview(String title, String detail, String url, Bitmap bitmap, String price, List<String> lines, int accent) {
            LinearLayout view = app.column();
            app.pad(view, 18, 10, 18, 18);
            if (bitmap != null || !url.isEmpty()) {
                ImageView cover = cover(view);
                if (bitmap != null) cover.setImageBitmap(bitmap); else loadCover(cover, url);
            }
            app.heading(view, title.isEmpty() ? "Sin nombre" : title);
            if (!detail.isEmpty()) app.note(view, detail);
            app.gap(view, 10);
            app.add(view, app.text(price, 21, accent, true));
            for (String line : lines) app.note(view, line);
            ScrollView content = new ScrollView(app);
            content.addView(view);
            new AlertDialog.Builder(app).setTitle("Vista previa").setView(content).setPositiveButton("Cerrar", null).show();
        }
        ImageView cover(LinearLayout panel) {
            ImageView view = new ImageView(app);
            view.setContentDescription("Portada");
            view.setBackground(app.rounded(UiActivity.GOLD, 14, 0));
            view.setClipToOutline(true);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            panel.addView(view, -1, app.dp(150));
            app.gap(panel, 10);
            return view;
        }
        void loadCover(ImageView view, String path) {
            view.setTag(path);
            app.io.execute(() -> {
                try {
                    URL url = new URL(new URL(BuildConfig.STORE_URL), path);
                    if (!url.getProtocol().equals("https") && !BuildConfig.DEBUG) return;
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setInstanceFollowRedirects(false);
                    try (var input = connection.getInputStream()) {
                        byte[] bytes = Supabase.read(input, 4 * 1024 * 1024);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                        options.inSampleSize = Math.max(1, Math.max(options.outHeight, options.outWidth) / 600);
                        options.inJustDecodeBounds = false;
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                        app.runOnUiThread(() -> {
                            if (bitmap != null && path.equals(view.getTag())) view.setImageBitmap(bitmap);
                        });
                    } finally { connection.disconnect(); }
                } catch (Exception ignored) { /* La ficha conserva la portada guardada. */ }
            });
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
            app.heading(details, "Ficha");
            field(details, "name", "Nombre", text(original, "name"), LINE_TEXT, 0);
            field(details, "description", "Descripción", text(original, "description"), MULTILINE, 0);
            category = app.choices(details, "Categoría", CATEGORIES, position(CATEGORIES, text(original, "category")), null);
            delivery = app.choices(details, "Modalidad de entrega", DELIVERY_LABELS, position(DELIVERY, text(original, "delivery_type")), null);
            field(details, "price", "Precio en soles", text(original, "price"), MONEY, 0);

            LinearLayout benefits = app.card(form);
            app.heading(benefits, "Qué incluye");
            JSONArray includes = array(original, "includes");
            for (int i = 0; i < 3; i++) field(benefits, "benefit" + i, "Beneficio " + (i + 1),
                includes.isNull(i) ? "" : includes.optString(i), LINE_TEXT, 0);

            LinearLayout art = app.card(form);
            app.heading(art, "Portada");
            cover = cover(art);
            renderCover();
            LinearLayout photos = app.row();
            app.weight(photos, app.button("Galería", this::gallery, false));
            app.weight(photos, app.button("Cámara", this::camera, false));
            app.add(art, photos);
            app.add(art, app.button("Quitar portada", () -> {
                remoteImage = "";
                pendingImage = null;
                uploadName = null;
                renderCover();
            }, false));
            accent = app.choices(art, "Acento visual", ACCENT_LABELS, position(ACCENTS, text(original, "accent")), null);

            LinearLayout publication = app.card(form);
            app.heading(publication, "Catálogo");
            available = app.toggle(publication, "Disponible", original.optBoolean("available", true));
            visible = app.toggle(publication, "Visible", original.optBoolean("visible", true));
            featured = app.toggle(publication, "Destacado", original.optBoolean("featured", false));
            int next = app.products.stream().mapToInt(p -> p.optInt("sort_order")).max().orElse(0);
            field(publication, "sort_order", "Posición", String.valueOf(original.optInt("sort_order",
                next == Integer.MAX_VALUE ? next : next + 1)), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED, 11);
            app.add(form, app.button("Ver vista previa", () -> {
                List<String> lines = new ArrayList<>();
                lines.add(CATEGORIES[category.getSelectedItemPosition()] + " · " + delivery.getSelectedItem());
                lines.add((available.isChecked() ? "Disponible" : "Agotado") + " · " +
                    (visible.isChecked() ? "Visible" : "Oculto") + (featured.isChecked() ? " · Destacado" : ""));
                for (String benefit : benefits()) lines.add("• " + benefit);
                int[] colors = {UiActivity.RED, UiActivity.GREEN, 0xff855400};
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
            if (pendingImage != null) cover.setImageBitmap(pendingImage);
            else if (!remoteImage.isEmpty()) loadCover(cover, remoteImage);
            cover.setVisibility(pendingImage != null || !remoteImage.isEmpty() ? View.VISIBLE : View.GONE);
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
            app.perform(() -> {
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
            EditText price = field(details, "price", "Precio promocional en soles", text(original, "price"), MONEY, 0);
            field(details, "image_url", "URL de imagen HTTPS (opcional)", text(original, "image_url"), URL, 2048);
            LinearLayout schedule = app.card(form);
            app.heading(schedule, "Fechas en hora de Lima");
            dateField(schedule, "starts_at", "Inicio (opcional)");
            dateField(schedule, "ends_at", "Finalización (opcional)");
            LinearLayout items = app.card(form);
            app.heading(items, "Productos del combo");
            app.note(items, "Selecciona al menos dos.");
            totals = app.text("", 15, UiActivity.GREEN, true);
            app.add(items, totals);
            if (app.included(selected).size() != selected.size()) {
                app.add(items, app.button("Quitar productos eliminados", () -> {
                    selected.retainAll(app.products.stream().map(Catalog::id).collect(java.util.stream.Collectors.toSet()));
                    updateTotals();
                }, false));
            }
            for (JSONObject product : app.products) {
                CheckBox box = new CheckBox(app);
                box.setMinHeight(app.dp(48));
                box.setText(text(product, "name") + " · " + money(amount(product)) + (canBuy(product) ? "" : " · No disponible"));
                box.setChecked(selected.contains(id(product)));
                box.setOnCheckedChangeListener((view, checked) -> {
                    if (checked) selected.add(id(product)); else selected.remove(id(product));
                    updateTotals();
                });
                app.add(items, box);
            }
            active = app.toggle(form, "Activar promoción", original.optBoolean("active"));
            app.add(form, app.button("Ver vista previa", () -> {
                List<String> lines = new ArrayList<>();
                lines.add(totals.getText().toString());
                app.included(selected).forEach(product -> lines.add("• " + text(product, "name")));
                preview(value("name"), value("description"), https(value("image_url")) ? value("image_url") : "",
                    null, formatted(value("price")), lines, UiActivity.RED);
            }, false));
            app.watch(price, this::updateTotals);
            updateTotals();
        }

        private void dateField(LinearLayout schedule, String key, String label) {
            EditText value = field(schedule, key, label, localDate(text(original, key)), InputType.TYPE_CLASS_TEXT, 19);
            value.setHint("AAAA-MM-DD HH:MM");
            LinearLayout controls = app.row();
            app.weight(controls, app.button("Elegir fecha", () -> {
                java.time.LocalDateTime initial = java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima"));
                try {
                    Instant saved = timestamp(value.getText().toString());
                    if (saved != null) initial = saved.atZone(java.time.ZoneId.of("America/Lima")).toLocalDateTime();
                } catch (IOException ignored) { /* El selector parte de la hora actual. */ }
                int hour = initial.getHour(), minute = initial.getMinute();
                new android.app.DatePickerDialog(app, (picker, year, month, day) ->
                    new android.app.TimePickerDialog(app, (time, h, m) ->
                        value.setText(String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d",
                            year, month + 1, day, h, m)), hour, minute, true).show(),
                    initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
            }, false));
            app.weight(controls, app.button("Quitar fecha", () -> value.setText(""), false));
            app.add(schedule, controls);
            app.gap(schedule, 8);
        }

        private void updateTotals() {
            BigDecimal original = total(app.included(selected));
            String savings;
            try { savings = money(original.subtract(price(value("price"))).max(BigDecimal.ZERO)); }
            catch (IOException invalid) { savings = "—"; }
            totals.setText("Total individual " + money(original) + "\nAhorro " + savings);
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
        static final String[] SECTIONS = {"Datos de la tienda", "Portada", "Atención y pagos",
            "Preguntas frecuentes", "Redes sociales"};
        private final boolean whatsapp;
        private final List<Faq> faq = new ArrayList<>();
        private final Map<String, View> sections = new HashMap<>();
        private LinearLayout faqRows;

        private record Faq(EditText question, EditText answer) {}

        Settings(MainActivity app, JSONObject source, boolean whatsapp) {
            super(app, source);
            this.whatsapp = whatsapp;
        }

        @Override String title() { return whatsapp ? "Editar WhatsApp" : "Editar ajustes"; }
        private LinearLayout section(int number) {
            LinearLayout area = app.card(form);
            app.heading(area, SECTIONS[number]);
            sections.put(SECTIONS[number], area);
            return area;
        }
        void focusSection(String label) {
            View section = sections.get(label);
            if (section != null) app.scroll.post(() -> app.scroll.scrollTo(0, section.getTop()));
        }

        private String phone() {
            String digits = text(original, "whatsapp_number").replaceAll("[^0-9]", "");
            return digits.length() == 11 && digits.startsWith("51") ? digits.substring(2) : digits;
        }
        private void setting(LinearLayout parent, String key, String title, int type) {
            field(parent, key, title, text(original, key), type, 0);
        }

        @Override void build() {
            LinearLayout info = section(0);
            if (!whatsapp) setting(info, "store_name", "Nombre de la tienda", LINE_TEXT);
            field(info, "whatsapp_number", "WhatsApp de Perú (+51)", phone(), InputType.TYPE_CLASS_PHONE, 0);
            app.add(info, app.button("Abrir WhatsApp", () -> {
                try {
                    String number = normalizedPhone(value("whatsapp_number"));
                    app.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number)));
                } catch (Exception error) { app.alert("Ingresa un número válido y verifica que puedas abrir WhatsApp."); }
            }, false));
            if (whatsapp) return;
            setting(info, "support_hours", "Horarios", LINE_TEXT);
            setting(info, "response_time", "Tiempo de respuesta", LINE_TEXT);

            LinearLayout hero = section(1);
            setting(hero, "hero_badge", "Etiqueta", LINE_TEXT);
            setting(hero, "hero_title", "Título", LINE_TEXT);
            setting(hero, "hero_subtitle", "Subtítulo", MULTILINE);

            LinearLayout support = section(2);
            setting(support, "currency", "Moneda", LINE_TEXT);
            setting(support, "payment_methods", "Métodos de pago", LINE_TEXT);
            setting(support, "warranty_text", "Garantía o reposición", MULTILINE);

            LinearLayout frequentlyAsked = section(3);
            faqRows = app.column();
            app.add(frequentlyAsked, faqRows);
            for (JSONObject item : list(array(original, "faq"))) addFaq(text(item, "question"), text(item, "answer"));
            app.add(frequentlyAsked, app.button("Agregar pregunta", () -> addFaq("", ""), false));

            LinearLayout links = section(4);
            JSONObject social = original.optJSONObject("social_links");
            if (social == null) social = new JSONObject();
            for (String name : new String[]{"Instagram", "TikTok", "Facebook"}) {
                String key = name.toLowerCase(Locale.ROOT);
                field(links, key, name, text(social, key), URL, 0);
            }
        }

        private void addFaq(String question, String answer) {
            LinearLayout item = app.card(faqRows);
            Faq row = new Faq(app.input(item, "Pregunta", question, LINE_TEXT), app.input(item, "Respuesta", answer, MULTILINE));
            app.watch(row.question(), app::recordActivity);
            app.watch(row.answer(), app::recordActivity);
            faq.add(row);
            app.add(item, app.button("Quitar pregunta", () -> {
                faq.remove(row);
                faqRows.removeView(item);
            }, false));
        }

        @Override Object snapshot() {
            List<List<String>> entries = new ArrayList<>();
            for (Faq row : faq) entries.add(List.of(row.question().getText().toString(), row.answer().getText().toString()));
            return Arrays.asList(values(), entries);
        }

        private String normalizedPhone(String raw) throws IOException {
            String digits = raw.replaceAll("[^0-9]", "");
            if (digits.matches("51[0-9]{9}")) return digits;
            if (digits.matches("[0-9]{9}")) return "51" + digits;
            throw new IOException("WhatsApp necesita nueve dígitos de Perú.");
        }

        @Override MainActivity.Operation prepareSave() throws Exception {
            JSONObject payload = object("id", 1, "whatsapp_number", normalizedPhone(value("whatsapp_number")));
            if (!whatsapp) {
                name(value("store_name"));
                if (value("hero_title").isEmpty()) throw new IOException("Ingresa el título de portada.");
                for (String key : new String[]{"store_name", "hero_badge", "hero_title", "hero_subtitle", "support_hours",
                    "response_time", "warranty_text"}) payload.put(key, value(key));
                payload.put("currency", value("currency").isEmpty() ? "S/" : value("currency"));
                payload.put("payment_methods", value("payment_methods").isEmpty() ? "Yape y Plin" : value("payment_methods"));
                JSONObject social = original.optJSONObject("social_links");
                social = social == null ? new JSONObject() : new JSONObject(social.toString());
                for (String key : new String[]{"instagram", "tiktok", "facebook"}) {
                    String link = value(key);
                    if (!link.isEmpty() && !https(link)) throw new IOException("Usa enlaces HTTPS para las redes sociales.");
                    social.put(key, link);
                }
                payload.put("social_links", social);
                JSONArray entries = new JSONArray();
                for (Faq row : faq) {
                    String question = row.question().getText().toString().trim(), answer = row.answer().getText().toString().trim();
                    if (question.isEmpty() && answer.isEmpty()) continue;
                    if (question.isEmpty() || answer.isEmpty()) throw new IOException("Completa la pregunta y la respuesta de cada entrada.");
                    entries.put(object("question", question, "answer", answer));
                }
                payload.put("faq", entries);
            }
            return () -> {
                if (original.has("id")) app.api.change("PATCH", "settings?id=eq.1", payload, 1);
                else app.api.change("POST", "settings", payload, 1);
            };
        }
    }
}