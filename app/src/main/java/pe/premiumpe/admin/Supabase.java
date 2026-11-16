package pe.premiumpe.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Supabase {
    static final String ADMIN = "admin@premiumpe.com";
    static final long IDLE = 30 * 60_000L;
    private static final String ALIAS = "premiumpe-admin-session";
    private final String endpoint = BuildConfig.SUPABASE_URL.replaceAll("/+$", "");
    private final SharedPreferences preferences;
    private volatile JSONObject session;
    private volatile long activity;

    Supabase(Context context) {
        String profile = Integer.toHexString(endpoint.hashCode());
        preferences = context.getSharedPreferences("secure_session_" + profile, Context.MODE_PRIVATE);
        try {
            String encrypted = preferences.getString("session", null);
            if (encrypted != null) {
                String[] pieces = encrypted.split(":");
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, decode(pieces[0])));
                session = new JSONObject(new String(cipher.doFinal(decode(pieces[1])), StandardCharsets.UTF_8));
                activity = preferences.getLong("activity", 0);
            }
        } catch (Exception invalid) {
            clear();
        }
    }

    boolean configured() {
        return !BuildConfig.SUPABASE_KEY.trim().isEmpty() &&
            (endpoint.startsWith("https://") || (BuildConfig.DEBUG && endpoint.startsWith("http://")));
    }

    boolean hasSession() { return session != null; }
    boolean idle() { return System.currentTimeMillis() - activity >= IDLE; }
    long remainingSeconds() { return (IDLE - (System.currentTimeMillis() - activity) + 999) / 1000; }

    void touch() {
        if (!hasSession()) return;
        activity = System.currentTimeMillis();
        preferences.edit().putLong("activity", activity).apply();
    }

    void clear() {
        session = null;
        activity = 0;
        preferences.edit().clear().commit();
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private static byte[] decode(String text) { return Base64.decode(text, Base64.NO_WRAP); }
    private static String encode(byte[] bytes) { return Base64.encodeToString(bytes, Base64.NO_WRAP); }

    private void saveSession(JSONObject result) throws Exception {
        if (!ADMIN.equalsIgnoreCase(result.getJSONObject("user").getString("email"))) {
            clear();
            throw new IOException("Esta cuenta no tiene acceso administrativo.");
        }
        JSONObject tokens = object("access_token", result.getString("access_token"),
            "refresh_token", result.getString("refresh_token"),
            "expires_at", System.currentTimeMillis() / 1000 + result.getLong("expires_in"));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String encrypted = encode(cipher.getIV()) + ":" +
            encode(cipher.doFinal(tokens.toString().getBytes(StandardCharsets.UTF_8)));
        if (!preferences.edit().putString("session", encrypted).putLong("activity", activity).commit()) {
            clear();
            throw new IOException("No se pudo guardar la sesión de forma segura.");
        }
        session = tokens;
    }

    void login(String user, String password) throws Exception {
        if (!configured()) throw new IOException("Configura la URL y la clave publicable de Supabase.");
        String email = user.trim();
        if (email.equalsIgnoreCase("cheridecheros")) email = ADMIN;
        if (!email.equalsIgnoreCase(ADMIN)) throw new IOException("Usa la cuenta administradora de PremiumPe.");
        JSONObject result = new JSONObject(json("POST", "/auth/v1/token?grant_type=password",
            object("email", email.toLowerCase(java.util.Locale.ROOT), "password", password), null, null));
        activity = System.currentTimeMillis();
        saveSession(result);
    }

    void logout() {
        JSONObject previous = session;
        clear();
        if (previous == null) return;
        try {
            json("POST", "/auth/v1/logout?scope=local", null, previous.optString("access_token"), null);
        } catch (Exception ignored) { /* La sesión local ya se eliminó. */ }
    }

    private String token(boolean forceRefresh) throws Exception {
        if (!hasSession() || idle()) {
            clear();
            throw new IOException("La sesión terminó. Vuelve a iniciar sesión.");
        }
        if (forceRefresh || session.getLong("expires_at") <= System.currentTimeMillis() / 1000 + 60) {
            try {
                saveSession(new JSONObject(json("POST", "/auth/v1/token?grant_type=refresh_token",
                    object("refresh_token", session.getString("refresh_token")), null, null)));
            } catch (ApiError error) {
                if (error.status == 400 || error.status == 401 || error.status == 403) {
                    clear();
                    throw new IOException("La sesión venció. Vuelve a iniciar sesión.");
                }
                throw error;
            }
        }
        return session.getString("access_token");
    }

    JSONArray rows(String query) throws Exception {
        return new JSONArray(write("GET", query, null));
    }

    String write(String method, String query, Object payload) throws Exception {
        String prefer = method.equals("GET") || query.startsWith("rpc/") ? null : "return=representation";
        if (query.contains("on_conflict=")) prefer += ",resolution=merge-duplicates";
        try {
            return json(method, "/rest/v1/" + query, payload, token(false), prefer);
        } catch (ApiError error) {
            if (error.status != 401) throw error;
            return json(method, "/rest/v1/" + query, payload, token(true), prefer);
        }
    }

    void change(String method, String query, Object payload, int expected) throws Exception {
        if (new JSONArray(write(method, query, payload)).length() != expected) {
            throw new IOException("La selección cambió o el servidor rechazó el acceso. Actualiza la lista.");
        }
    }

    String upload(String filename, String mime, byte[] contents) throws Exception {
        String path = "/storage/v1/object/product-images/" + filename;
        try {
            request("POST", path, contents, mime, token(false), null);
        } catch (ApiError error) {
            if (error.status != 401) throw error;
            request("POST", path, contents, mime, token(true), null);
        }
        return endpoint + "/storage/v1/object/public/product-images/" + filename;
    }

    private String json(String method, String path, Object payload, String bearer, String prefer) throws Exception {
        return request(method, path, payload == null ? null : payload.toString().getBytes(StandardCharsets.UTF_8),
            "application/json", bearer, prefer);
    }

    private String request(String method, String path, byte[] payload, String mime, String bearer, String prefer)
        throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY);
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
            if (prefer != null) connection.setRequestProperty("Prefer", prefer);
            if (method.equals("POST") && path.startsWith("/storage/v1/object/")) {
                connection.setRequestProperty("x-upsert", "true");
            }
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", mime);
                connection.setFixedLengthStreamingMode(payload.length);
                try (var output = connection.getOutputStream()) { output.write(payload); }
            }
            int status = connection.getResponseCode();
            boolean ok = status / 100 == 2;
            try (InputStream input = ok ? connection.getInputStream() : connection.getErrorStream()) {
                String body = input == null ? "" : new String(read(input, 8 * 1024 * 1024), StandardCharsets.UTF_8);
                if (!ok) throw new ApiError(status, body);
                return body;
            }
        } finally {
            connection.disconnect();
        }
    }

    static byte[] read(InputStream input, int limit) throws IOException {
        var content = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count; (count = input.read(buffer)) != -1; ) {
            if (content.size() + count > limit) throw new IOException("La respuesta excede el tamaño permitido.");
            content.write(buffer, 0, count);
        }
        return content.toByteArray();
    }
    static JSONObject object(Object... pairs) throws org.json.JSONException {
        JSONObject result = new JSONObject();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1] == null ? JSONObject.NULL : pairs[index + 1]);
        }
        return result;
    }

    static final class ApiError extends IOException {
        final int status;
        ApiError(int status, String body) {
            super(message(status, body));
            this.status = status;
        }
        private static String message(int status, String body) {
            try {
                JSONObject error = new JSONObject(body);
                return switch (error.optString("code")) {
                    case "23505" -> "Ya existe un registro con esta combinación.";
                    case "42501" -> "El servidor rechazó el acceso.";
                    case "PGRST202", "PGRST205" -> "Falta la tabla o la operación requerida en Supabase.";
                    default -> error.optString("message", error.optString("msg", "Error de Supabase (" + status + ")."));
                };
            } catch (Exception invalid) {
                return "Error de Supabase (" + status + ").";
            }
        }
    }
}