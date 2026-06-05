package br.com.valorae.carteira.util;

import org.json.*;

import java.util.*;

public final class JsonUtils {
    private JsonUtils() {}

    public static JSONObject parseObject(String json) {
        if (json == null || json.trim().isEmpty()) return new JSONObject();
        String t = json.trim();
        try {
            if (t.startsWith("[")) {
                JSONObject o = new JSONObject();
                o.put("items", new JSONArray(t));
                return o;
            }
            return new JSONObject(t);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONArray getArray(JSONObject obj, String... keys) {
        if (obj == null) return new JSONArray();
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value instanceof JSONArray) return (JSONArray) value;
            if (value instanceof JSONObject) {
                JSONObject nested = (JSONObject) value;
                JSONArray inside = nested.optJSONArray("items");
                if (inside != null) return inside;
            }
        }
        return new JSONArray();
    }

    public static JSONObject getObject(JSONObject obj, String... keys) {
        if (obj == null) return new JSONObject();
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value instanceof JSONObject) return (JSONObject) value;
        }
        return new JSONObject();
    }

    public static String getString(JSONObject obj, String... keys) {
        if (obj == null) return "";
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value != null && value != JSONObject.NULL) {
                String s = String.valueOf(value).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    public static double getDouble(JSONObject obj, String... keys) {
        if (obj == null) return 0d;
        for (String key : keys) {
            Object value = obj.opt(key);
            Double num = toDouble(value);
            if (num != null) return num;
        }
        return 0d;
    }

    public static Double toDouble(Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return null;
        s = s.replace("R$", "").replace("%", "").replaceAll("\\s+", "");
        if (s.contains(",")) s = s.replace(".", "").replace(',', '.');
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }


    public static JSONObject unwrap(JSONObject obj) {
        if (obj == null) return new JSONObject();
        Object data = obj.opt("data");
        if (data instanceof JSONObject) return (JSONObject) data;
        Object result = obj.opt("result");
        if (result instanceof JSONObject) return (JSONObject) result;
        Object results = obj.opt("results");
        if (results instanceof JSONObject) return (JSONObject) results;
        Object payload = obj.opt("payload");
        if (payload instanceof JSONObject) return (JSONObject) payload;
        return obj;
    }

    public static double firstDouble(JSONObject root, String... keys) {
        double direct = getDouble(root, keys);
        if (direct != 0d) return direct;
        JSONObject data = unwrap(root);
        double unwrapped = getDouble(data, keys);
        if (unwrapped != 0d) return unwrapped;
        JSONObject summary = getObject(data, "summary", "totals", "portfolioSummary");
        double fromSummary = getDouble(summary, keys);
        if (fromSummary != 0d) return fromSummary;
        JSONObject income = getObject(data, "income", "dividends", "yield");
        return getDouble(income, keys);
    }

    public static String firstString(JSONObject root, String... keys) {
        String direct = getString(root, keys);
        if (!direct.isEmpty()) return direct;
        JSONObject data = unwrap(root);
        String unwrapped = getString(data, keys);
        if (!unwrapped.isEmpty()) return unwrapped;
        JSONObject summary = getObject(data, "summary", "totals", "portfolioSummary");
        return getString(summary, keys);
    }

    public static List<Float> extractSeries(JSONArray array, String... candidateKeys) {
        ArrayList<Float> out = new ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) continue;
            for (String key : candidateKeys) {
                Double d = toDouble(row.opt(key));
                if (d != null) {
                    out.add(d.floatValue());
                    break;
                }
            }
        }
        return out;
    }

    public static String labelize(String key) {
        if (key == null || key.isEmpty()) return "";
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace("_", " ");
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    public static JSONArray safeArray(Object any) {
        return any instanceof JSONArray ? (JSONArray) any : new JSONArray();
    }

    public static JSONObject safeObject(Object any) {
        return any instanceof JSONObject ? (JSONObject) any : new JSONObject();
    }

    public static List<String> arrayStrings(JSONArray array, int maxItems) {
        ArrayList<String> out = new ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length() && out.size() < maxItems; i++) {
            Object item = array.opt(i);
            if (item == null || item == JSONObject.NULL) continue;
            if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                String msg = getString(obj, "message", "title", "name", "ticker", "code");
                if (!msg.isEmpty()) out.add(msg);
            } else {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }
}
