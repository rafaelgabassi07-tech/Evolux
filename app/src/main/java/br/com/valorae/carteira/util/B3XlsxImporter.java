package br.com.valorae.carteira.util;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import br.com.valorae.carteira.model.ImportResult;
import br.com.valorae.carteira.model.Position;

public final class B3XlsxImporter {
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final Pattern TICKER_PATTERN = Pattern.compile("\\b[A-Z]{4}[0-9]{1,2}[A-Z]?\\b");
    private B3XlsxImporter() {}

    public static ParsedB3Import parse(InputStream input) throws Exception {
        Map<String, byte[]> entries = unzip(input);
        List<String> shared = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        List<List<String>> allRows = new ArrayList<>();
        List<String> sheetNames = new ArrayList<>();
        for (String name : entries.keySet()) {
            if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) sheetNames.add(name);
        }
        Collections.sort(sheetNames);
        for (String sheet : sheetNames) allRows.addAll(parseSheet(entries.get(sheet), shared));
        return parseRows(allRows);
    }

    private static Map<String, byte[]> unzip(InputStream input) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buffer)) >= 0) baos.write(buffer, 0, n);
                out.put(entry.getName(), baos.toByteArray());
            }
        }
        return out;
    }

    private static List<String> parseSharedStrings(byte[] xmlBytes) throws Exception {
        List<String> out = new ArrayList<>();
        if (xmlBytes == null) return out;
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xmlBytes), "UTF-8");
        StringBuilder current = null;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "si".equals(parser.getName())) current = new StringBuilder();
            else if (event == XmlPullParser.TEXT && current != null) current.append(parser.getText());
            else if (event == XmlPullParser.END_TAG && "si".equals(parser.getName())) {
                out.add(current == null ? "" : current.toString());
                current = null;
            }
        }
        return out;
    }

    private static List<List<String>> parseSheet(byte[] xmlBytes, List<String> shared) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        if (xmlBytes == null) return rows;
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xmlBytes), "UTF-8");
        List<String> currentRow = null;
        String cellType = "";
        int cellIndex = -1;
        String cellValue = "";
        boolean capture = false;
        String captureTag = "";
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("row".equals(tag)) currentRow = new ArrayList<>();
                else if ("c".equals(tag)) {
                    cellType = attr(parser, "t");
                    cellIndex = columnIndex(attr(parser, "r"));
                    if (cellIndex < 0 && currentRow != null) cellIndex = currentRow.size();
                    cellValue = "";
                } else if (("v".equals(tag) || "t".equals(tag)) && currentRow != null) {
                    capture = true;
                    captureTag = tag;
                }
            } else if (event == XmlPullParser.TEXT && capture) {
                cellValue += parser.getText();
            } else if (event == XmlPullParser.END_TAG) {
                String tag = parser.getName();
                if (capture && tag.equals(captureTag)) {
                    capture = false;
                    captureTag = "";
                } else if ("c".equals(tag) && currentRow != null) {
                    String value = cellValue == null ? "" : cellValue.trim();
                    if ("s".equals(cellType)) {
                        int idx = (int) parseNumber(value);
                        value = idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
                    }
                    while (currentRow.size() <= cellIndex) currentRow.add("");
                    currentRow.set(cellIndex, value);
                } else if ("row".equals(tag) && currentRow != null) {
                    rows.add(currentRow);
                    currentRow = null;
                }
            }
        }
        return rows;
    }

    private static String attr(XmlPullParser parser, String name) {
        String v = parser.getAttributeValue(null, name);
        return v == null ? "" : v;
    }

    private static int columnIndex(String cellRef) {
        if (cellRef == null || cellRef.isEmpty()) return -1;
        int idx = 0;
        boolean found = false;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = Character.toUpperCase(cellRef.charAt(i));
            if (c >= 'A' && c <= 'Z') {
                idx = idx * 26 + (c - 'A' + 1);
                found = true;
            } else break;
        }
        return found ? idx - 1 : -1;
    }

    private static ParsedB3Import parseRows(List<List<String>> rows) {
        ImportResult result = new ImportResult();
        result.rowsRead = rows == null ? 0 : rows.size();
        List<Trade> trades = new ArrayList<>();
        Header header = null;
        int headerRowIndex = -1;
        if (rows == null) rows = new ArrayList<>();
        for (int i = 0; i < Math.min(rows.size(), 80); i++) {
            Header h = detectHeader(rows.get(i));
            if (h.score >= 4) {
                header = h;
                headerRowIndex = i;
                break;
            }
        }
        if (header == null) {
            result.messages.add("Não encontrei uma linha de cabeçalho reconhecível. Use a planilha de negociações da B3 com colunas de data, ativo, quantidade e preço/valor.");
            return new ParsedB3Import(new ArrayList<>(), result);
        }
        for (int i = headerRowIndex + 1; i < rows.size(); i++) {
            Trade t = parseTrade(rows.get(i), header);
            if (t == null) { result.skippedRows++; continue; }
            trades.add(t);
            result.validTrades++;
            if (t.quantitySigned > 0) result.buyTrades++; else result.sellTrades++;
            result.grossValue += Math.abs(t.quantitySigned * t.price);
        }
        Collections.sort(trades, Comparator.comparing(a -> a.dateIso));
        Map<String, Agg> byTicker = new LinkedHashMap<>();
        for (Trade t : trades) {
            Agg agg = byTicker.get(t.ticker);
            if (agg == null) {
                agg = new Agg(t.ticker, inferType(t.ticker, t.product));
                byTicker.put(t.ticker, agg);
            }
            agg.apply(t);
        }
        List<Position> positions = new ArrayList<>();
        for (Agg agg : byTicker.values()) {
            Position p = agg.toPosition();
            if (p != null) positions.add(p);
        }
        result.positionsCreated = positions.size();
        if (result.validTrades > 0 && positions.isEmpty()) result.messages.add("As negociações foram lidas, mas todas resultaram em posição zerada após compras e vendas.");
        result.messages.add("Preço médio calculado por custo remanescente: vendas reduzem quantidade e abatem custo pelo preço médio anterior.");
        result.messages.add("A data de compra enviada ao Proxy passa a ser a primeira compra remanescente conhecida para cada ativo.");
        return new ParsedB3Import(positions, result);
    }

    private static Header detectHeader(List<String> row) {
        Header h = new Header();
        for (int i = 0; row != null && i < row.size(); i++) {
            String n = normalize(row.get(i));
            if (h.date < 0 && (n.equals("data") || n.contains("data do negocio") || n.contains("data pregão") || n.contains("data pregao"))) h.date = i;
            if (h.operation < 0 && (n.contains("compra venda") || n.contains("compra/venda") || n.contains("movimentacao") || n.contains("operacao") || n.equals("c/v"))) h.operation = i;
            if (h.ticker < 0 && (n.contains("codigo de negociacao") || n.contains("codigo negociacao") || n.equals("ticker") || n.equals("ativo") || n.contains("cod negociacao"))) h.ticker = i;
            if (h.product < 0 && (n.contains("produto") || n.contains("especificacao") || n.contains("nome do ativo"))) h.product = i;
            if (h.quantity < 0 && (n.contains("quantidade") || n.equals("qtd") || n.contains("qtd."))) h.quantity = i;
            if (h.price < 0 && (n.contains("preco") || n.contains("preço") || n.contains("valor unitario") || n.contains("cotacao") || n.contains("cotação"))) h.price = i;
            if (h.totalValue < 0 && (n.equals("valor") || n.contains("valor total") || n.contains("volume") || n.contains("total"))) h.totalValue = i;
        }
        h.score = 0;
        if (h.date >= 0) h.score++;
        if (h.operation >= 0) h.score++;
        if (h.ticker >= 0 || h.product >= 0) h.score += 2;
        if (h.quantity >= 0) h.score++;
        if (h.price >= 0 || h.totalValue >= 0) h.score++;
        return h;
    }

    private static Trade parseTrade(List<String> row, Header h) {
        if (row == null || row.isEmpty()) return null;
        String product = get(row, h.product);
        String ticker = get(row, h.ticker);
        if (ticker.isEmpty()) ticker = extractTicker(product);
        else ticker = extractTicker(ticker + " " + product);
        if (ticker.isEmpty()) return null;
        double qty = parseNumber(get(row, h.quantity));
        if (qty <= 0) return null;
        double price = parseNumber(get(row, h.price));
        double total = parseNumber(get(row, h.totalValue));
        if (price <= 0 && total > 0) price = total / qty;
        if (price <= 0) return null;
        int sign = operationSign(get(row, h.operation), join(row));
        if (sign == 0) return null;
        String dateIso = parseDate(get(row, h.date));
        if (dateIso.isEmpty()) dateIso = DateUtils.todayIso();
        Trade t = new Trade();
        t.ticker = ticker;
        t.product = product;
        t.quantitySigned = qty * sign;
        t.price = price;
        t.dateIso = dateIso;
        return t;
    }

    private static String get(List<String> row, int idx) {
        return idx >= 0 && idx < row.size() && row.get(idx) != null ? row.get(idx).trim() : "";
    }

    private static String join(List<String> row) {
        StringBuilder sb = new StringBuilder();
        if (row != null) for (String s : row) sb.append(' ').append(s == null ? "" : s);
        return sb.toString();
    }

    private static int operationSign(String op, String fullRow) {
        String n = normalize((op == null || op.trim().isEmpty()) ? fullRow : op);
        if (n.equals("c") || n.contains("compra") || n.contains("credito") || n.contains("creditado")) return 1;
        if (n.equals("v") || n.contains("venda") || n.contains("debito") || n.contains("debitado")) return -1;
        return 0;
    }

    private static String extractTicker(String raw) {
        if (raw == null) return "";
        Matcher m = TICKER_PATTERN.matcher(raw.toUpperCase(Locale.ROOT));
        return m.find() ? m.group() : "";
    }

    private static String inferType(String ticker, String product) {
        String t = ticker == null ? "" : ticker.toUpperCase(Locale.ROOT);
        String p = normalize(product);
        if (p.contains("fii") || p.contains("fundo imobiliario") || p.contains("fundo de investimento imobiliario")) return "FII";
        if (p.contains("etf") || p.contains("indice") || p.contains("índice")) return "ETF";
        if (t.endsWith("11")) {
            if (t.startsWith("BOVA") || t.startsWith("IVVB") || t.startsWith("SMAL") || t.startsWith("HASH") || t.startsWith("GOLD") || t.startsWith("XFIX")) return "ETF";
            return "FII";
        }
        return "ACAO";
    }

    private static String parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String s = raw.trim();
        Date d = DateUtils.parseFlexible(s);
        if (d != null) return DateUtils.formatIso(d);
        double serial = parseNumber(s);
        if (serial > 20000 && serial < 90000) {
            long millis = Math.round((serial - 25569d) * 86400000d);
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
        }
        return "";
    }

    private static double parseNumber(String raw) {
        if (raw == null) return 0;
        String s = raw.trim();
        if (s.isEmpty()) return 0;
        s = s.replace("R$", "").replace(" ", "").replace("\u00A0", "");
        s = s.replaceAll("[^0-9,.-]", "");
        if (s.isEmpty() || s.equals("-") || s.equals(",")) return 0;
        if (s.contains(",")) s = s.replace(".", "").replace(",", ".");
        try { return Math.abs(Double.parseDouble(s)); } catch (Exception e) { return 0; }
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String n = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static class Header {
        int date = -1, operation = -1, ticker = -1, product = -1, quantity = -1, price = -1, totalValue = -1, score = 0;
    }

    private static class Trade {
        String ticker;
        String product;
        double quantitySigned;
        double price;
        String dateIso;
    }

    private static class Agg {
        final String ticker;
        final String type;
        double quantity;
        double cost;
        String firstDate = "";
        Agg(String ticker, String type) { this.ticker = ticker; this.type = type; }
        void apply(Trade t) {
            if (t.quantitySigned > 0) {
                quantity += t.quantitySigned;
                cost += t.quantitySigned * t.price;
                if (firstDate.isEmpty() || t.dateIso.compareTo(firstDate) < 0) firstDate = t.dateIso;
            } else if (quantity > 0) {
                double sellQty = Math.min(quantity, Math.abs(t.quantitySigned));
                double avg = cost / quantity;
                quantity -= sellQty;
                cost -= avg * sellQty;
                if (quantity <= 0.000001) { quantity = 0; cost = 0; firstDate = ""; }
            }
        }
        Position toPosition() {
            if (quantity <= 0.000001 || cost <= 0) return null;
            return new Position(0, ticker, type, quantity, cost / quantity, 0, firstDate.isEmpty() ? DateUtils.todayIso() : firstDate);
        }
    }

    public static class ParsedB3Import {
        public final List<Position> positions;
        public final ImportResult result;
        ParsedB3Import(List<Position> positions, ImportResult result) {
            this.positions = positions;
            this.result = result;
        }
    }
}
