package br.com.valorae.carteira.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ProxyDataCache {
    private static final String PREFS = "valorae_proxy_cache";
    private static final String TS_SUFFIX = "__ts";
    private final SharedPreferences prefs;

    public ProxyDataCache(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(String key, String payload) {
        if (key == null || key.trim().isEmpty()) return;
        if (payload == null || payload.trim().isEmpty()) return;
        prefs.edit()
                .putString(key, payload)
                .putLong(key + TS_SUFFIX, System.currentTimeMillis())
                .apply();
    }

    public String read(String key) {
        if (key == null || key.trim().isEmpty()) return "";
        return prefs.getString(key, "");
    }

    public long timestamp(String key) {
        if (key == null || key.trim().isEmpty()) return 0L;
        return prefs.getLong(key + TS_SUFFIX, 0L);
    }

    public boolean has(String key) {
        String payload = read(key);
        return payload != null && !payload.trim().isEmpty();
    }

    public boolean isFresh(String key, long maxAgeMillis) {
        long ts = timestamp(key);
        return ts > 0L && System.currentTimeMillis() - ts <= maxAgeMillis;
    }

    public int payloadCount() {
        int count = 0;
        for (String key : prefs.getAll().keySet()) {
            if (!key.endsWith(TS_SUFFIX)) count++;
        }
        return count;
    }

    public long newestTimestamp() {
        long newest = 0L;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.endsWith(TS_SUFFIX)) continue;
            Object value = entry.getValue();
            if (value instanceof Long) newest = Math.max(newest, (Long) value);
        }
        return newest;
    }

    public long oldestTimestamp() {
        long oldest = 0L;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.endsWith(TS_SUFFIX)) continue;
            Object value = entry.getValue();
            if (!(value instanceof Long)) continue;
            long ts = (Long) value;
            if (ts <= 0L) continue;
            oldest = oldest == 0L ? ts : Math.min(oldest, ts);
        }
        return oldest;
    }

    public long approxBytes() {
        long total = 0L;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(TS_SUFFIX)) continue;
            Object value = entry.getValue();
            if (value instanceof String) total += ((String) value).length() * 2L;
        }
        return total;
    }

    public List<String> payloadKeys() {
        ArrayList<String> keys = new ArrayList<>();
        for (String key : prefs.getAll().keySet()) {
            if (!key.endsWith(TS_SUFFIX)) keys.add(key);
        }
        Collections.sort(keys);
        return keys;
    }

    public void removeByPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
            if (key.endsWith(TS_SUFFIX) && key.substring(0, key.length() - TS_SUFFIX.length()).startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }

    public void clear() { prefs.edit().clear().apply(); }

    public String readableStatus() {
        int count = payloadCount();
        if (count == 0) return "Nenhuma resposta em cache ainda";
        long newest = newestTimestamp();
        if (newest <= 0L) return count + " resposta(s) guardada(s)";
        return count + " resposta(s) guardada(s), última atualização " + humanAge(newest);
    }

    public String detailedStatus() {
        int count = payloadCount();
        if (count == 0) return "Cache vazio. As próximas telas buscarão novas respostas no Proxy.";
        long kb = Math.max(1L, approxBytes() / 1024L);
        long newest = newestTimestamp();
        long oldest = oldestTimestamp();
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(" resposta(s) em cache • aprox. ").append(kb).append(" KB");
        if (newest > 0L) sb.append("\nÚltima atualização: ").append(humanAge(newest));
        if (oldest > 0L && oldest != newest) sb.append("\nResposta mais antiga: ").append(humanAge(oldest));
        List<String> keys = payloadKeys();
        if (!keys.isEmpty()) {
            sb.append("\nRotas guardadas: ");
            for (int i = 0; i < Math.min(8, keys.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(prettyKey(keys.get(i)));
            }
            if (keys.size() > 8) sb.append(" +").append(keys.size() - 8);
        }
        return sb.toString();
    }

    private String prettyKey(String key) {
        if (key == null) return "—";
        return key.replace('_', '/');
    }

    private String humanAge(long timestamp) {
        long minutes = Math.max(0L, (System.currentTimeMillis() - timestamp) / 60000L);
        if (minutes < 1) return "agora há pouco";
        if (minutes < 60) return "há " + minutes + " min";
        if (minutes < 1440) return "há " + (minutes / 60L) + " h";
        return "há " + (minutes / 1440L) + " dia(s)";
    }
}
