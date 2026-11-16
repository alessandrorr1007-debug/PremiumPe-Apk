package pe.premiumpe.admin;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import static pe.premiumpe.admin.Supabase.object;

final class Catalog {
    static final String[] CATEGORIES = {"Streaming", "Herramientas", "IA"};
    static final String[] DELIVERY = {"perfil", "activacion_tu_cuenta", "cuenta_entregada"};
    static final String[] DELIVERY_LABELS = {"Perfil", "Activación en tu cuenta", "Cuenta entregada"};
    static final String[] ACCENTS = {"rocoto", "lime", "aji"};
    static final String[] ACCENT_LABELS = {"Rocoto", "Lima", "Ají"};
    static final String[] SUMMARY_FILTERS = {"Todos", "Disponibles", "Agotados", "Ocultos", "Visibles",
        "Destacados", "Sin portada", "Sin beneficios"};
    static final String PRODUCTS = "products";
    static final String PROMOTIONS = "promotions";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    static List<JSONObject> list(JSONArray json) {
        List<JSONObject> rows = new ArrayList<>();
        if (json != null) for (int i = 0; i < json.length(); i++) {
            JSONObject row = json.optJSONObject(i);
            if (row != null) rows.add(row);
        }
        return rows;
    }
    static JSONArray array(JSONObject row, String key) {
        JSONArray array = row.optJSONArray(key);
        if (array != null) return array;
        try { return new JSONArray(text(row, key)); }
        catch (Exception invalid) { return new JSONArray(); }
    }
    static String text(JSONObject row, String key) {
        return row.isNull(key) ? "" : row.optString(key);
    }
    static String id(JSONObject row) { return row.optString("id"); }
    static String where(String table, String id) { return table + "?id=eq." + id; }
    static boolean canBuy(JSONObject product) {
        return product.optBoolean("visible") && product.optBoolean("available");
    }
    static BigDecimal amount(JSONObject item) {
        try { return new BigDecimal(text(item, "price")); }
        catch (NumberFormatException invalid) { return BigDecimal.ZERO; }
    }
    static BigDecimal price(String text) throws IOException {
        String value = text.trim().replace(',', '.');
        if (value.matches("[0-9]{1,8}(\\.[0-9]{1,2})?")) return new BigDecimal(value);
        throw new IOException("Ingresa un precio de 0 a 99 999 999,99 con hasta dos decimales.");
    }
    static String name(String value) throws IOException {
        if (value.trim().isEmpty()) throw new IOException("Ingresa un nombre.");
        return value.trim();
    }
    static BigDecimal total(List<JSONObject> products) {
        return products.stream().map(Catalog::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    static String money(BigDecimal value) { return "S/ " + value.setScale(2, java.math.RoundingMode.HALF_UP); }
    static String localDate(String value) {
        return value.isEmpty() ? "" : DATE.format(Instant.parse(value).atZone(LIMA));
    }
    static Instant timestamp(String value) throws IOException {
        String text = value.trim().replace(' ', 'T');
        if (text.isEmpty()) return null;
        try { return LocalDateTime.parse(text).atZone(LIMA).toInstant(); }
        catch (Exception invalid) { throw new IOException("Usa AAAA-MM-DD HH:MM en hora de Lima."); }
    }
    static boolean https(String value) { return value.matches("https://[^\\s/]+[^\\s]*"); }
    static String promotionStatus(JSONObject row, List<JSONObject> included) {
        Instant now = Instant.now();
        if (!row.optBoolean("active")) return "Inactiva";
        if (!row.isNull("ends_at") && !Instant.parse(text(row, "ends_at")).isAfter(now)) return "Finalizada";
        if (included.size() != array(row, "product_ids").length() || !included.stream().allMatch(Catalog::canBuy)
            || total(included).compareTo(amount(row)) <= 0) return "No disponible";
        if (!row.isNull("starts_at") && Instant.parse(text(row, "starts_at")).isAfter(now)) return "Programada";
        return "Publicada";
    }
    static JSONObject copyProduct(JSONObject row) throws Exception {
        return object("name", text(row, "name") + " (Copia)", "description", text(row, "description"),
            "category", text(row, "category"), "delivery_type", row.optString("delivery_type", "perfil"),
            "includes", array(row, "includes"), "accent", row.optString("accent", "aji"),
            "price", amount(row), "image_url", row.opt("image_url"), "available", row.optBoolean("available"),
            "visible", false, "featured", false, "sort_order", Math.min(Integer.MAX_VALUE, row.optInt("sort_order") + 1L));
    }
}