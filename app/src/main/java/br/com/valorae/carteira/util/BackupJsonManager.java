package br.com.valorae.carteira.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import br.com.valorae.carteira.model.ImportResult;
import br.com.valorae.carteira.model.Position;

public final class BackupJsonManager {
    private BackupJsonManager() {}

    public static String exportPositions(List<Position> positions, String appVersion) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", "VALORAE_PORTFOLIO_BACKUP");
        root.put("schemaVersion", 1);
        root.put("appVersion", appVersion == null ? "" : appVersion);
        root.put("exportedAt", DateUtils.todayIso());
        JSONArray arr = new JSONArray();
        if (positions != null) {
            for (Position p : positions) {
                JSONObject o = new JSONObject();
                o.put("ticker", p.ticker);
                o.put("assetType", p.assetType);
                o.put("quantity", p.quantity);
                o.put("averagePrice", p.averagePrice);
                o.put("targetPercent", p.targetPercent);
                o.put("purchaseDate", p.purchaseDate);
                arr.put(o);
            }
        }
        root.put("positions", arr);
        return root.toString(2);
    }

    public static ParsedBackup parsePositions(String json) throws JSONException {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        JSONArray arr = root.optJSONArray("positions");
        if (arr == null) arr = root.optJSONArray("items");
        if (arr == null) arr = root.optJSONArray("data");
        if (arr == null) arr = new JSONArray();
        List<Position> positions = new ArrayList<>();
        ImportResult result = new ImportResult();
        result.rowsRead = arr.length();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) { result.skippedRows++; continue; }
            String ticker = first(o, "ticker", "symbol", "codigo", "asset");
            double quantity = firstDouble(o, "quantity", "qty", "quantidade");
            double avg = firstDouble(o, "averagePrice", "avgPrice", "precoMedio", "price");
            String type = first(o, "assetType", "type", "classe");
            String date = first(o, "purchaseDate", "acquisitionDate", "buyDate", "dataCompra", "firstPurchaseDate");
            double target = firstDouble(o, "targetPercent", "targetWeight", "meta");
            if (ticker.trim().isEmpty() || quantity <= 0 || avg <= 0) {
                result.skippedRows++;
                continue;
            }
            positions.add(new Position(0, ticker, type.isEmpty() ? inferType(ticker) : type, quantity, avg, target, DateUtils.normalizeIsoDate(date, DateUtils.todayIso())));
            result.validTrades++;
        }
        result.positionsCreated = positions.size();
        if (positions.isEmpty()) result.messages.add("O backup foi lido, mas não continha posições válidas para restaurar.");
        return new ParsedBackup(positions, result);
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v.trim())) return v.trim();
        }
        return "";
    }

    private static double firstDouble(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            double d = parseNumber(v == null ? "" : String.valueOf(v));
            if (d > 0) return d;
        }
        return 0;
    }

    private static double parseNumber(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().replace("R$", "").replace("%", "").replace(" ", "");
        if (s.contains(",")) s = s.replace(".", "").replace(",", ".");
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private static String inferType(String ticker) {
        String t = ticker == null ? "" : ticker.toUpperCase();
        if (t.endsWith("11")) {
            if (t.startsWith("BOVA") || t.startsWith("IVVB") || t.startsWith("SMAL") || t.startsWith("XFIX") || t.startsWith("HASH") || t.startsWith("GOLD")) return "ETF";
            return "FII";
        }
        return "ACAO";
    }

    public static class ParsedBackup {
        public final List<Position> positions;
        public final ImportResult result;
        ParsedBackup(List<Position> positions, ImportResult result) {
            this.positions = positions;
            this.result = result;
        }
    }
}
