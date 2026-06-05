package br.com.valorae.carteira.data;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class ValoraeClient {
    private final String baseUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public ValoraeClient(String baseUrl) { this(baseUrl, 10000, 15000); }

    public ValoraeClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    private static String enc(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private HttpURLConnection open(String path, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "VALORAE-Carteira-AndroidJava/1.12.0");
        conn.setRequestProperty("x-valorae-app", "VALORAE-Carteira");
        conn.setRequestProperty("x-valorae-channel", "android-java");
        conn.setRequestProperty("x-valorae-app-version", "1.12.0");
        conn.setRequestProperty("x-valorae-build", "v1.12.0-proxy-21.12.58-graphs");
        return conn;
    }

    private String get(String path) throws IOException { return read(open(path, "GET")); }

    private String post(String path, String json) throws IOException {
        HttpURLConnection conn = open(path, "POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write((json == null ? "{}" : json).getBytes(StandardCharsets.UTF_8));
        }
        return read(conn);
    }

    private String read(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream raw = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (raw == null) raw = new ByteArrayInputStream(new byte[0]);
        String body;
        try (InputStream in = raw; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            body = out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
        if (code < 200 || code >= 300) {
            throw new IOException("VALORAE HTTP " + code + (body.isEmpty() ? "" : ": " + body.substring(0, Math.min(180, body.length()))));
        }
        return body;
    }

    public String readyJson() throws IOException { return get("/api/v1/ready"); }
    public String manifestJson() throws IOException { return get("/api/v1/manifest?lean=1"); }
    public String openApiJson() throws IOException { return get("/api/v1/openapi?lean=1"); }
    public String fieldsJson() throws IOException { return get("/api/v1/fields?lean=1"); }
    public String cacheStatsJson() throws IOException { return get("/api/v1/cache/stats?lean=1"); }
    public String sourceStatusJson() throws IOException { return get("/api/v1/source/status?lean=1"); }

    public String assetJson(String ticker) throws IOException {
        return get("/api/v1/asset?ticker=" + enc(ticker) + "&view=app&complete=1&profile=full&maxChartSeries=8&chartSeriesLimit=8&maxItems=120");
    }
    public String assetIndicatorsJson(String ticker) throws IOException { return get("/api/v1/asset/indicators?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String assetFundamentalsJson(String ticker) throws IOException { return get("/api/v1/asset/fundamentals?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String assetProfileJson(String ticker) throws IOException { return get("/api/v1/asset/profile?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String assetValuationJson(String ticker) throws IOException { return get("/api/v1/asset/valuation?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String assetProfitabilityJson(String ticker) throws IOException { return get("/api/v1/asset/profitability?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String assetDebtJson(String ticker) throws IOException { return get("/api/v1/asset/debt?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String assetStatementsJson(String ticker) throws IOException { return get("/api/v1/asset/statements?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String assetPeersJson(String ticker) throws IOException { return get("/api/v1/asset/peers?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String assetSourceMapJson(String ticker) throws IOException { return get("/api/v1/asset/source-map?ticker=" + enc(ticker) + "&view=app&lean=1"); }
    public String assetHistoryJson(String ticker, String range) throws IOException { return get("/api/v1/asset/history?ticker=" + enc(ticker) + "&range=" + enc(range == null ? "1Y" : range) + "&view=app&maxItems=180&lean=1"); }
    public String assetDividendsJson(String ticker) throws IOException { return get("/api/v1/asset/dividends?ticker=" + enc(ticker) + "&view=app&complete=1&maxItems=120&lean=1"); }

    public String fiiIndicatorsJson(String ticker) throws IOException { return get("/api/v1/fii/indicators?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String fiiProfileJson(String ticker) throws IOException { return get("/api/v1/fii/profile?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String fiiIncomeJson(String ticker) throws IOException { return get("/api/v1/fii/income?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String fiiPatrimonialJson(String ticker) throws IOException { return get("/api/v1/fii/patrimonial?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String fiiPortfolioJson(String ticker) throws IOException { return get("/api/v1/fii/portfolio?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String fiiVacancyJson(String ticker) throws IOException { return get("/api/v1/fii/vacancy?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }
    public String fiiCommunicationsJson(String ticker) throws IOException { return get("/api/v1/fii/communications?ticker=" + enc(ticker) + "&view=app&complete=1&maxItems=25&lean=1"); }
    public String fiiChecklistJson(String ticker) throws IOException { return get("/api/v1/fii/checklist?ticker=" + enc(ticker) + "&view=app&complete=1&lean=1"); }

    public String assetsJson(String tickersCsv) throws IOException { return get("/api/v1/assets?tickers=" + enc(tickersCsv) + "&view=wallet&profile=portfolio&lean=1"); }
    public String compareJson(String tickersCsv) throws IOException { return get("/api/v1/compare?tickers=" + enc(tickersCsv) + "&lean=1"); }
    public String indicesJson() throws IOException { return get("/api/v1/market/indices?lean=1"); }
    public String newsJson(String ticker) throws IOException { return get("/api/v1/news?ticker=" + enc(ticker) + "&maxItems=10&lean=1"); }
    public String watchlistAnalyzeJson(String tickersCsv) throws IOException { return get("/api/v1/watchlist/analyze?tickers=" + enc(tickersCsv) + "&view=compact&profile=portfolio&lean=1"); }

    public String portfolioAnalyzeJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/analyze", bodyJson); }
    public String portfolioSummaryJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/summary", bodyJson); }
    public String portfolioAllocationJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/allocation", bodyJson); }
    public String portfolioIncomeJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/income", bodyJson); }
    public String portfolioDividendsJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/dividends", bodyJson); }
    public String portfolioNextDividendsJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/next-dividends", bodyJson); }
    public String portfolioHistoryJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/history", bodyJson); }
    public String portfolioEventsJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/events", bodyJson); }
    public String portfolioRiskJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/risk", bodyJson); }
    public String portfolioRebalanceJson(String bodyJson) throws IOException { return post("/api/v1/portfolio/rebalance", bodyJson); }
}
