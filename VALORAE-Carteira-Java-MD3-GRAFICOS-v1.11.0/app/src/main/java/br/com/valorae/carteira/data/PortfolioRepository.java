package br.com.valorae.carteira.data;

import android.content.Context;
import org.json.JSONArray;
import java.io.InputStream;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import br.com.valorae.carteira.R;
import br.com.valorae.carteira.model.Position;
import br.com.valorae.carteira.model.ImportResult;
import br.com.valorae.carteira.model.PortfolioTemporalSummary;
import br.com.valorae.carteira.model.ProxyAuditItem;
import br.com.valorae.carteira.util.BackupJsonManager;
import br.com.valorae.carteira.util.B3XlsxImporter;
import br.com.valorae.carteira.util.DateUtils;
import br.com.valorae.carteira.util.JsonUtils;
import br.com.valorae.carteira.util.PortfolioMath;

public class PortfolioRepository {
    private final LocalPortfolioStore store;
    private final ValoraeClient client;
    private final ProxyDataCache cache;

    public PortfolioRepository(Context context) {
        Context appContext = context.getApplicationContext();
        this.store = new LocalPortfolioStore(appContext);
        this.client = new ValoraeClient(appContext.getString(R.string.proxy_base_url));
        this.cache = new ProxyDataCache(appContext);
    }

    public List<Position> positions() { return store.listPositions(); }

    public void savePosition(String ticker, String type, double quantity, double averagePrice, double targetPercent) {
        store.upsertPosition(ticker, type, quantity, averagePrice, targetPercent);
        invalidatePortfolioCache();
    }

    public void savePosition(String ticker, String type, double quantity, double averagePrice, double targetPercent, String purchaseDate) {
        store.upsertPosition(ticker, type, quantity, averagePrice, targetPercent, purchaseDate);
        invalidatePortfolioCache();
    }

    public void deletePosition(long id) {
        store.deletePosition(id);
        invalidatePortfolioCache();
    }

    public double investedTotal() {
        double total = 0;
        for (Position p : positions()) total += p.investedValue();
        return total;
    }

    public int countByType(String type) {
        int count = 0;
        for (Position p : positions()) if (type.equalsIgnoreCase(p.assetType)) count++;
        return count;
    }

    public PortfolioTemporalSummary temporalSummary() { return PortfolioMath.portfolioSummary(positions()); }

    public String exportBackupJson(String appVersion) throws JSONException {
        return BackupJsonManager.exportPositions(positions(), appVersion);
    }

    public ImportResult importBackupJson(String json, boolean replaceAll) throws JSONException {
        BackupJsonManager.ParsedBackup parsed = BackupJsonManager.parsePositions(json);
        if (replaceAll) store.replacePositions(parsed.positions);
        else store.upsertPositions(parsed.positions);
        invalidatePortfolioCache();
        parsed.result.positionsReplaced = parsed.positions.size();
        parsed.result.messages.add(replaceAll ? "Backup restaurado substituindo a carteira local." : "Backup anexado à carteira local, atualizando tickers existentes.");
        return parsed.result;
    }

    public ImportResult importB3Xlsx(InputStream input, boolean replaceAll) throws Exception {
        B3XlsxImporter.ParsedB3Import parsed = B3XlsxImporter.parse(input);
        if (parsed.positions.isEmpty()) return parsed.result;
        if (replaceAll) store.replacePositions(parsed.positions);
        else store.upsertPositions(parsed.positions);
        invalidatePortfolioCache();
        parsed.result.positionsReplaced = parsed.positions.size();
        parsed.result.messages.add(replaceAll ? "Planilha B3 importada substituindo a carteira local." : "Planilha B3 anexada à carteira local, atualizando tickers existentes.");
        return parsed.result;
    }


    public String firstTickerOrDefault(String fallback) {
        List<Position> ps = positions();
        return ps.isEmpty() ? fallback : ps.get(0).ticker;
    }

    public String tickersCsv() {
        StringBuilder sb = new StringBuilder();
        for (Position p : positions()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p.ticker);
        }
        return sb.toString();
    }

    public String portfolioPayloadJson() throws JSONException {
        JSONArray arr = new JSONArray();
        for (Position p : positions()) {
            JSONObject o = new JSONObject();
            o.put("ticker", p.ticker);
            o.put("symbol", p.ticker);
            o.put("assetType", p.assetType);
            o.put("type", p.assetType);
            o.put("quantity", p.quantity);
            o.put("qty", p.quantity);
            o.put("averagePrice", p.averagePrice);
            o.put("avgPrice", p.averagePrice);
            o.put("purchasePrice", p.averagePrice);
            o.put("targetPercent", p.targetPercent);
            o.put("targetWeight", p.targetPercent);
            o.put("purchaseDate", p.purchaseDate);
            o.put("acquisitionDate", p.purchaseDate);
            o.put("buyDate", p.purchaseDate);
            o.put("firstPurchaseDate", p.purchaseDate);
            o.put("holdingStartDate", p.purchaseDate);
            arr.put(o);
        }
        JSONObject targetsByType = new JSONObject();
        targetsByType.put("ACOES", 45);
        targetsByType.put("ACAO", 45);
        targetsByType.put("FIIS", 35);
        targetsByType.put("FII", 35);
        targetsByType.put("ETFS", 20);
        targetsByType.put("ETF", 20);

        PortfolioTemporalSummary temporal = temporalSummary();
        JSONObject temporalObj = new JSONObject();
        temporalObj.put("portfolioStartDate", temporal.startDate);
        temporalObj.put("oldestTicker", temporal.oldestTicker);
        temporalObj.put("ageDays", temporal.ageDays);
        temporalObj.put("ageYears", temporal.ageYears);
        temporalObj.put("weightedHoldingDays", temporal.weightedHoldingDays);

        JSONObject root = new JSONObject();
        root.put("positions", arr);
        root.put("targetsByType", targetsByType);
        root.put("portfolioStartDate", temporal.startDate);
        root.put("asOfDate", DateUtils.todayIso());
        root.put("temporal", temporalObj);
        root.put("source", "android-java-md3-auto-integration");
        root.put("client", "VALORAE-Carteira-Java");
        root.put("appVersion", "1.11.0");
        root.put("view", "full");
        return root.toString();
    }

    public String ready() throws Exception { return remoteOrCache("ready", () -> client.readyJson()); }
    public String manifest() throws Exception { return remoteOrCache("manifest", () -> client.manifestJson()); }
    public String sourceStatus() throws Exception { return remoteOrCache("source_status", () -> client.sourceStatusJson()); }
    public String fields() throws Exception { return remoteOrCache("fields", () -> client.fieldsJson()); }
    public String assets() throws Exception {
        String csv = tickersCsv();
        String effective = csv.isEmpty() ? "PETR4,VALE3" : csv;
        return remoteOrCache("assets_" + normalizeCacheKey(effective), () -> client.assetsJson(effective));
    }
    public String compareWithIfix() throws Exception { return client.compareJson(tickersCsv().isEmpty() ? "IFIX" : tickersCsv() + ",IFIX"); }
    public String analyze() throws Exception { return remoteOrCache("portfolio_analyze", () -> client.portfolioAnalyzeJson(portfolioPayloadJson())); }
    public String summary() throws Exception { return remoteOrCache("portfolio_summary", () -> client.portfolioSummaryJson(portfolioPayloadJson())); }
    public String allocation() throws Exception { return remoteOrCache("portfolio_allocation", () -> client.portfolioAllocationJson(portfolioPayloadJson())); }
    public String income() throws Exception { return remoteOrCache("portfolio_income", () -> client.portfolioIncomeJson(portfolioPayloadJson())); }
    public String dividends() throws Exception { return remoteOrCache("portfolio_dividends", () -> client.portfolioDividendsJson(portfolioPayloadJson())); }
    public String nextDividends() throws Exception { return remoteOrCache("portfolio_next_dividends", () -> client.portfolioNextDividendsJson(portfolioPayloadJson())); }
    public String history() throws Exception { return remoteOrCache("portfolio_history", () -> client.portfolioHistoryJson(portfolioPayloadJson())); }
    public String events() throws Exception { return remoteOrCache("portfolio_events", () -> client.portfolioEventsJson(portfolioPayloadJson())); }
    public String risk() throws Exception { return remoteOrCache("portfolio_risk", () -> client.portfolioRiskJson(portfolioPayloadJson())); }
    public String rebalance() throws Exception { return remoteOrCache("portfolio_rebalance", () -> client.portfolioRebalanceJson(portfolioPayloadJson())); }
    public String indices() throws Exception { return remoteOrCache("market_indices", () -> client.indicesJson()); }
    public String newsForFirstTicker() throws Exception {
        String ticker = firstTickerOrDefault("PETR4");
        return remoteOrCache("news_" + normalizeCacheKey(ticker), () -> client.newsJson(ticker));
    }
    public String watchlist() throws Exception {
        String csv = tickersCsv().isEmpty() ? "PETR4,VALE3" : tickersCsv();
        return remoteOrCache("watchlist_" + normalizeCacheKey(csv), () -> client.watchlistAnalyzeJson(csv));
    }

    public String proxyCacheStatus() { return cache.readableStatus(); }
    public String proxyCacheDetailedStatus() { return cache.detailedStatus(); }
    public int proxyCacheCount() { return cache.payloadCount(); }
    public String assetCard(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_card_" + normalized, () -> client.assetJson(ticker));
    }

    public String assetIndicators(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_indicators_" + normalized, () -> client.assetIndicatorsJson(ticker));
    }

    public String assetFundamentals(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_fundamentals_" + normalized, () -> client.assetFundamentalsJson(ticker));
    }

    public String assetDividends(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_dividends_" + normalized, () -> client.assetDividendsJson(ticker));
    }

    public String fiiIndicators(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("fii_indicators_" + normalized, () -> client.fiiIndicatorsJson(ticker));
    }

    public String fiiProfile(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("fii_profile_" + normalized, () -> client.fiiProfileJson(ticker));
    }

    public String fiiIncome(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("fii_income_" + normalized, () -> client.fiiIncomeJson(ticker));
    }

    public String fiiPatrimonial(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("fii_patrimonial_" + normalized, () -> client.fiiPatrimonialJson(ticker));
    }

    public String fiiPortfolio(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("fii_portfolio_" + normalized, () -> client.fiiPortfolioJson(ticker));
    }

    public String fiiVacancy(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("fii_vacancy_" + normalized, () -> client.fiiVacancyJson(ticker));
    }

    public String fiiCommunications(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("fii_communications_" + normalized, () -> client.fiiCommunicationsJson(ticker));
    }

    public String fiiChecklist(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("fii_checklist_" + normalized, () -> client.fiiChecklistJson(ticker));
    }

    public String assetHistory(String ticker, String range) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        String rangeKey = range == null || range.trim().isEmpty() ? "1Y" : range.trim().toUpperCase(Locale.ROOT);
        return remoteOrCache("asset_history_" + normalized + "_" + rangeKey, () -> client.assetHistoryJson(ticker, rangeKey));
    }

    public String assetProfile(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_profile_" + normalized, () -> client.assetProfileJson(ticker));
    }

    public String assetValuation(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_valuation_" + normalized, () -> client.assetValuationJson(ticker));
    }

    public String assetProfitability(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_profitability_" + normalized, () -> client.assetProfitabilityJson(ticker));
    }

    public String assetDebt(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_debt_" + normalized, () -> client.assetDebtJson(ticker));
    }

    public String assetStatements(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_statements_" + normalized, () -> client.assetStatementsJson(ticker));
    }

    public String assetPeers(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_peers_" + normalized, () -> client.assetPeersJson(ticker));
    }

    public String assetSourceMap(String ticker) throws Exception {
        String normalized = normalizeCacheKey(ticker);
        return remoteOrCache("asset_source_map_" + normalized, () -> client.assetSourceMapJson(ticker));
    }

    public void clearProxyCache() { cache.clear(); }

    public List<ProxyAuditItem> syncNow() {
        ArrayList<ProxyAuditItem> out = new ArrayList<>();
        audit(out, "Conectividade", "/api/v1/ready", () -> ready(), "Servidor respondeu e cache foi atualizado");
        audit(out, "Contrato", "/api/v1/manifest", () -> manifest(), "Manifesto salvo para diagnóstico");
        audit(out, "Fontes", "/api/v1/source/status", () -> sourceStatus(), "Status das fontes salvo");
        audit(out, "Ativos", "/api/v1/assets", () -> assets(), "Snapshots dos ativos da carteira salvos");
        audit(out, "Resumo", "/api/v1/portfolio/summary", () -> summary(), "Resumo consolidado salvo");
        audit(out, "Alocação", "/api/v1/portfolio/allocation", () -> allocation(), "Distribuição salva");
        audit(out, "Renda", "/api/v1/portfolio/income", () -> income(), "Renda passiva salva");
        audit(out, "Proventos recebidos", "/api/v1/portfolio/dividends", () -> dividends(), "Histórico de proventos recebido salvo");
        audit(out, "Risco", "/api/v1/portfolio/risk", () -> risk(), "Risco salvo");
        audit(out, "Histórico", "/api/v1/portfolio/history", () -> history(), "Histórico salvo");
        audit(out, "Eventos", "/api/v1/portfolio/events", () -> events(), "Eventos salvos");
        audit(out, "Próximos proventos", "/api/v1/portfolio/next-dividends", () -> nextDividends(), "Agenda de proventos salva");
        audit(out, "Rebalanceamento", "/api/v1/portfolio/rebalance", () -> rebalance(), "Sugestões de ajuste salvas");
        audit(out, "Índices", "/api/v1/market/indices", () -> indices(), "Índices salvos");
        audit(out, "Notícias", "/api/v1/news", () -> newsForFirstTicker(), "Notícias do ativo principal salvas");
        audit(out, "Watchlist", "/api/v1/watchlist/analyze", () -> watchlist(), "Watchlist da carteira salva");
        String firstTicker = firstTickerOrDefault("PETR4");
        audit(out, "Contrato visual do ativo", "/api/v1/asset?view=app", () -> assetCard(firstTicker), "appMobileSnapshot, appPayload e gráficos do ativo salvos");
        audit(out, "Histórico gráfico do ativo", "/api/v1/asset/history", () -> assetHistory(firstTicker, "1Y"), "Série histórica do ativo salva");
        audit(out, "Mapa de fontes do ativo", "/api/v1/asset/source-map", () -> assetSourceMap(firstTicker), "Rastreabilidade por campo salva");
        return out;
    }

    public List<ProxyAuditItem> syncOnAppOpen() {
        ArrayList<ProxyAuditItem> out = new ArrayList<>();
        audit(out, "Abertura do app", "/api/v1/ready", () -> ready(), "Proxy consultado automaticamente ao entrar no aplicativo");
        audit(out, "Contrato do Proxy", "/api/v1/manifest", () -> manifest(), "Manifesto carregado para compatibilidade de telas");
        audit(out, "Fontes de dados", "/api/v1/source/status", () -> sourceStatus(), "Fontes verificadas em segundo plano");
        audit(out, "Ativos da carteira", "/api/v1/assets", () -> assets(), "Cotações e snapshots pré-carregados");
        audit(out, "Resumo da carteira", "/api/v1/portfolio/summary", () -> summary(), "Patrimônio consolidado atualizado");
        audit(out, "Alocação", "/api/v1/portfolio/allocation", () -> allocation(), "Distribuição de carteira atualizada");
        audit(out, "Renda", "/api/v1/portfolio/income", () -> income(), "Renda e yield atualizados");
        audit(out, "Proventos recebidos", "/api/v1/portfolio/dividends", () -> dividends(), "Histórico de pagamentos passados carregado");
        audit(out, "Risco", "/api/v1/portfolio/risk", () -> risk(), "Concentração e alertas atualizados");
        audit(out, "Histórico", "/api/v1/portfolio/history", () -> history(), "Série histórica disponível para gráficos");
        audit(out, "Eventos", "/api/v1/portfolio/events", () -> events(), "Eventos e agenda carregados");
        audit(out, "Próximos proventos", "/api/v1/portfolio/next-dividends", () -> nextDividends(), "Pagamentos futuros carregados");
        audit(out, "Rebalanceamento", "/api/v1/portfolio/rebalance", () -> rebalance(), "Sugestões de ajuste atualizadas");
        audit(out, "Índices de mercado", "/api/v1/market/indices", () -> indices(), "Índices pré-carregados para Mercado");
        audit(out, "Notícias", "/api/v1/news", () -> newsForFirstTicker(), "Notícias do ativo principal atualizadas");
        audit(out, "Watchlist", "/api/v1/watchlist/analyze", () -> watchlist(), "Leitura compacta dos ativos atualizada");
        audit(out, "Detalhes dos ativos", "pré-carga local", () -> preloadPortfolioAssetDetails(6), "Detalhes de ativos da carteira preparados para abertura rápida");
        return out;
    }

    public String preloadPortfolioAssetDetails(int limit) throws JSONException {
        List<Position> ps = positions();
        JSONArray items = new JSONArray();
        int total = Math.min(Math.max(limit, 0), ps.size());
        int useful = 0;
        for (int i = 0; i < total; i++) {
            Position p = ps.get(i);
            JSONObject item = new JSONObject();
            item.put("ticker", p.ticker);
            item.put("assetType", p.assetType);
            int ok = 0;
            ok += preloadOne(item, "asset", () -> assetCard(p.ticker));
            ok += preloadOne(item, "indicators", () -> assetIndicators(p.ticker));
            ok += preloadOne(item, "dividends", () -> assetDividends(p.ticker));
            if ("FII".equalsIgnoreCase(p.assetType) || "FIIS".equalsIgnoreCase(p.assetType)) {
                ok += preloadOne(item, "fiiProfile", () -> fiiProfile(p.ticker));
                ok += preloadOne(item, "fiiIndicators", () -> fiiIndicators(p.ticker));
                ok += preloadOne(item, "fiiIncome", () -> fiiIncome(p.ticker));
                ok += preloadOne(item, "fiiPatrimonial", () -> fiiPatrimonial(p.ticker));
                ok += preloadOne(item, "fiiPortfolio", () -> fiiPortfolio(p.ticker));
                ok += preloadOne(item, "fiiVacancy", () -> fiiVacancy(p.ticker));
            } else {
                ok += preloadOne(item, "fundamentals", () -> assetFundamentals(p.ticker));
                ok += preloadOne(item, "valuation", () -> assetValuation(p.ticker));
                ok += preloadOne(item, "profitability", () -> assetProfitability(p.ticker));
                ok += preloadOne(item, "debt", () -> assetDebt(p.ticker));
                ok += preloadOne(item, "statements", () -> assetStatements(p.ticker));
            }
            ok += preloadOne(item, "history", () -> assetHistory(p.ticker, "1Y"));
            item.put("okCount", ok);
            if (ok > 0) useful++;
            items.put(item);
        }
        JSONObject root = new JSONObject();
        root.put("items", items);
        root.put("requested", total);
        root.put("preloaded", useful);
        root.put("cacheStatus", proxyCacheStatus());
        root.put("source", "startup-preload");
        return root.toString();
    }

    private int preloadOne(JSONObject item, String key, RemoteCall call) throws JSONException {
        try {
            String payload = call.run();
            boolean ok = isUsableJson(payload);
            item.put(key, ok ? "ok" : "empty");
            return ok ? 1 : 0;
        } catch (Exception e) {
            item.put(key, "fallback_or_error");
            return 0;
        }
    }

    public String syncSummaryText(List<ProxyAuditItem> items) {
        if (items == null || items.isEmpty()) return "Nenhuma rota foi testada.";
        int ok = 0;
        StringBuilder failures = new StringBuilder();
        for (ProxyAuditItem item : items) {
            if (item.ok) ok++;
            else {
                if (failures.length() > 0) failures.append("\n");
                failures.append("• ").append(item.title).append(": ").append(item.detail);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ok).append("/").append(items.size()).append(" integrações responderam com dados úteis.");
        sb.append("\n").append(proxyCacheDetailedStatus());
        if (failures.length() > 0) sb.append("\n\nPendências:\n").append(failures);
        return sb.toString();
    }

    public List<ProxyAuditItem> proxyAudit() {
        ArrayList<ProxyAuditItem> out = new ArrayList<>();
        audit(out, "Servidor pronto", "/api/v1/ready", () -> ready(), "Conectividade básica com o Proxy");
        audit(out, "Manifesto de rotas", "/api/v1/manifest", () -> manifest(), "Contrato público carregado");
        audit(out, "Campos documentados", "/api/v1/fields", () -> fields(), "Mapa de campos disponível");
        audit(out, "Fontes do Proxy", "/api/v1/source/status", () -> sourceStatus(), "Fontes e provedores monitorados");
        audit(out, "Ativos da carteira", "/api/v1/assets", () -> assets(), "Cotações e snapshots dos tickers cadastrados");
        audit(out, "Resumo da carteira", "/api/v1/portfolio/summary", () -> summary(), "Patrimônio, custo e rentabilidade");
        audit(out, "Alocação", "/api/v1/portfolio/allocation", () -> allocation(), "Distribuição por ticker e classe");
        audit(out, "Renda passiva", "/api/v1/portfolio/income", () -> income(), "Yield, renda mensal e calendário estimado");
        audit(out, "Proventos recebidos", "/api/v1/portfolio/dividends", () -> dividends(), "Histórico de proventos passados interpretado");
        audit(out, "Risco", "/api/v1/portfolio/risk", () -> risk(), "Concentração e alertas de diversificação");
        audit(out, "Histórico", "/api/v1/portfolio/history", () -> history(), "Série histórica para gráficos");
        audit(out, "Eventos", "/api/v1/portfolio/events", () -> events(), "Agenda e movimentações relevantes");
        audit(out, "Próximos proventos", "/api/v1/portfolio/next-dividends", () -> nextDividends(), "Pagamentos futuros interpretados");
        audit(out, "Rebalanceamento", "/api/v1/portfolio/rebalance", () -> rebalance(), "Ações sugeridas por meta");
        audit(out, "Índices de mercado", "/api/v1/market/indices", () -> indices(), "Indicadores macro de mercado");
        audit(out, "Notícias do ativo principal", "/api/v1/news", () -> newsForFirstTicker(), "Notícias renderizáveis em cards");
        audit(out, "Watchlist da carteira", "/api/v1/watchlist/analyze", () -> watchlist(), "Leitura compacta dos ativos cadastrados");
        String firstTicker = firstTickerOrDefault("PETR4");
        audit(out, "Contrato visual do ativo", "/api/v1/asset?view=app", () -> assetCard(firstTicker), "appMobileSnapshot, appPayload, chartSeries e assetClassContract do ativo");
        audit(out, "Gráfico do ativo", "/api/v1/asset/history", () -> assetHistory(firstTicker, "1Y"), "Histórico pronto para LineChartView");
        audit(out, "Fonte por campo", "/api/v1/asset/source-map", () -> assetSourceMap(firstTicker), "Mapa de confiança/origem por campo");
        return out;
    }

    private interface RemoteCall { String run() throws Exception; }

    private String remoteOrCache(String key, RemoteCall call) throws Exception {
        try {
            String payload = call.run();
            if (isUsableJson(payload)) cache.save(key, payload);
            return payload;
        } catch (Exception remoteError) {
            String fallback = cache.read(key);
            if (fallback != null && !fallback.trim().isEmpty()) return fallback;
            throw remoteError;
        }
    }

    private boolean isUsableJson(String payload) {
        if (payload == null) return false;
        String trimmed = payload.trim();
        if (!trimmed.startsWith("{") || trimmed.length() < 2) return false;
        JSONObject raw = JsonUtils.parseObject(trimmed);
        return raw.length() > 0;
    }

    private String normalizeCacheKey(String value) {
        if (value == null || value.trim().isEmpty()) return "empty";
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
    }

    private void invalidatePortfolioCache() {
        // A carteira mudou. Preserva diagnósticos estáveis do Proxy e remove apenas dados dependentes da carteira.
        cache.removeByPrefix("portfolio_");
        cache.removeByPrefix("assets_");
        cache.removeByPrefix("watchlist_");
        cache.removeByPrefix("news_");
        cache.removeByPrefix("asset_");
        cache.removeByPrefix("fii_");
    }

    private interface AuditCall { String run() throws Exception; }

    private void audit(List<ProxyAuditItem> out, String title, String route, AuditCall call, String successLabel) {
        try {
            String payload = call.run();
            JSONObject raw = JsonUtils.parseObject(payload);
            JSONObject data = JsonUtils.unwrap(raw);
            int count = estimateCount(data);
            boolean ok = payload != null && payload.trim().startsWith("{") && (data.length() > 0 || raw.length() > 0);
            String detail = ok
                    ? successLabel + (count > 0 ? " • " + count + " item(ns) detectado(s)" : " • resposta válida")
                    : "Resposta recebida, mas sem conteúdo útil para renderização.";
            out.add(new ProxyAuditItem(title, route, ok, detail, count));
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.trim().isEmpty()) msg = "Falha sem mensagem detalhada.";
            out.add(new ProxyAuditItem(title, route, false, msg, 0));
        }
    }

    private int estimateCount(JSONObject obj) {
        if (obj == null) return 0;
        String[] arrays = {"items", "assets", "indices", "events", "dividends", "nextDividends", "pastDividends", "received", "payments", "records", "history", "positions", "actions", "series", "sources", "providers", "routes", "fields"};
        for (String key : arrays) {
            JSONArray arr = obj.optJSONArray(key);
            if (arr != null) return arr.length();
        }
        JSONObject nested = JsonUtils.getObject(obj, "summary", "totals", "income", "risk", "allocation", "portfolio", "data");
        if (nested.length() > 0) return nested.length();
        return obj.length();
    }
}

