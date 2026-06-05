package br.com.valorae.carteira.ui.analysis;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import br.com.valorae.carteira.R;
import br.com.valorae.carteira.ui.UiFactory;
import br.com.valorae.carteira.ui.base.BaseAsyncFragment;
import br.com.valorae.carteira.ui.widgets.DonutChartView;
import br.com.valorae.carteira.ui.widgets.LineChartView;
import br.com.valorae.carteira.util.DateUtils;
import br.com.valorae.carteira.util.JsonUtils;
import br.com.valorae.carteira.util.MoneyUtils;

public class AnalysisFragment extends BaseAsyncFragment {

    private View root;
    private EditText searchInput;
    private ImageButton btnSearch;
    private LinearLayout resultsContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_analysis, container, false);
        initRepository();
        
        searchInput = root.findViewById(R.id.searchInput);
        btnSearch = root.findViewById(R.id.btnSearch);
        resultsContainer = root.findViewById(R.id.resultsContainer);

        btnSearch.setOnClickListener(v -> performSearch());
        
        return root;
    }

    private void performSearch() {
        String ticker = searchInput.getText().toString().trim().toUpperCase();
        if (ticker.isEmpty()) {
            Toast.makeText(requireContext(), "Digite um ticker para pesquisar", Toast.LENGTH_SHORT).show();
            return;
        }

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }

        resultsContainer.removeAllViews();
        bindLoader(root, true);

        runAsync(() -> {
            boolean isFii = ticker.endsWith("11");
            AssetPayload payload = new AssetPayload();
            payload.cardJson = repository.symbol(ticker);
            payload.payloadJson = repository.payload(ticker);
            payload.dividendsJson = repository.assetDividends(ticker);
            payload.historyJson = repository.assetHistory(ticker, "1Y");
            payload.sourceMapJson = repository.assetSourceMap(ticker);
            
            if (isFii) {
                payload.incomeJson = repository.fiiIncome(ticker);
                payload.patrimonialJson = repository.fiiPatrimonial(ticker);
                payload.portfolioJson = repository.fiiPortfolio(ticker);
                payload.vacancyJson = repository.fiiVacancy(ticker);
                payload.communicationsJson = repository.fiiCommunications(ticker);
                payload.checklistJson = repository.fiiChecklist(ticker);
            } else {
                payload.fundamentalsJson = repository.assetFundamentals(ticker);
                payload.valuationJson = repository.assetValuation(ticker);
                payload.profitabilityJson = repository.assetProfitability(ticker);
                payload.debtJson = repository.assetDebt(ticker);
                payload.statementsJson = repository.assetStatements(ticker);
                payload.peersJson = repository.assetPeers(ticker);
            }
            return payload;
        }, value -> {
            bindLoader(root, false);
            AssetPayload p = (AssetPayload) value;
            renderResults(ticker, p, false);
        }, error -> {
            bindLoader(root, false);
            Toast.makeText(requireContext(), "Erro ao buscar do Proxy: " + error.getMessage(), Toast.LENGTH_LONG).show();
            renderResults(ticker, null, true);
        });
    }

    private void renderResults(String ticker, AssetPayload payload, boolean offline) {
        resultsContainer.removeAllViews();
        
        if (offline || payload == null || (isEmpty(payload.cardJson) && isEmpty(payload.payloadJson))) {
            resultsContainer.addView(UiFactory.emptyState(requireContext(), "Ativo não encontrado ou Proxy indisponível", "A busca por " + ticker + " não retornou dados consistentes."));
            return;
        }

        AssetDetail detail = buildAssetDetail(ticker, payload);

        TextView title = new TextView(requireContext());
        title.setText(detail.displayName);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_text_primary));
        resultsContainer.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText(detail.sector.isEmpty() ? (ticker.endsWith("11") ? "FII" : "Ação") : detail.sector);
        subtitle.setTextSize(14);
        subtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_text_secondary));
        subtitle.setPadding(0, UiFactory.dp(requireContext(), 4), 0, UiFactory.dp(requireContext(), 16));
        resultsContainer.addView(subtitle);

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.addView(grid);
        grid.addView(UiFactory.metricCard(requireContext(), "Preço atual", MoneyUtils.brl(detail.price), detail.change == 0d ? "Cotação do Proxy" : MoneyUtils.signedPct(detail.change)));

        ArrayList<String[]> fundamentals = new ArrayList<>();
        addMetric(fundamentals, "Dividend Yield", detail.dy, "pct");
        addMetric(fundamentals, "P/VP", detail.pvp, "num");
        addMetric(fundamentals, "P/L", detail.pl, "num");
        addMetric(fundamentals, "ROE", detail.roe, "pct");
        addMetric(fundamentals, "ROIC", detail.roic, "pct");
        addMetric(fundamentals, "Margem líquida", detail.netMargin, "pct");
        addMetric(fundamentals, "Payout", detail.payout, "pct");
        addMetric(fundamentals, "Dívida líquida/EBITDA", detail.netDebtEbitda, "num");
        addMetric(fundamentals, "Liquidez média", detail.liquidity, "num");

        if (!fundamentals.isEmpty()) {
            resultsContainer.addView(section("Indicadores e fundamentos"));
            resultsContainer.addView(metricGroup(fundamentals));
        }

        if (detail.isFii) {
            ArrayList<String[]> fii = new ArrayList<>();
            addMetric(fii, "VP por cota", detail.vpPerShare, "brl");
            addMetric(fii, "Patrimônio", detail.equityValue, "brl");
            addMetric(fii, "Vacância física", detail.physicalVacancy, "pct");
            addMetric(fii, "Vacância financeira", detail.financialVacancy, "pct");
            if (!detail.fundType.isEmpty()) fii.add(new String[]{"Tipo do fundo", detail.fundType});
            if (!detail.management.isEmpty()) fii.add(new String[]{"Gestão", detail.management});
            if (!fii.isEmpty()) {
                resultsContainer.addView(section("Contrato FII"));
                resultsContainer.addView(metricGroup(fii));
            }
        } else {
            ArrayList<String[]> company = new ArrayList<>();
            addMetric(company, "Receita líquida", detail.netRevenue, "brl");
            addMetric(company, "Lucro líquido", detail.netIncome, "brl");
            addMetric(company, "Ativos", detail.assetsValue, "brl");
            addMetric(company, "Patrimônio líquido", detail.equityValue, "brl");
            if (!detail.listingSegment.isEmpty()) company.add(new String[]{"Segmento", detail.listingSegment});
            if (!company.isEmpty()) {
                resultsContainer.addView(section("Empresa e balanço"));
                resultsContainer.addView(metricGroup(company));
            }
        }

        if (detail.historyPoints.size() >= 2) {
            resultsContainer.addView(section("Gráfico histórico"));
            LineChartView chart = new LineChartView(requireContext());
            chart.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiFactory.dp(requireContext(), 220)));
            chart.setPoints(detail.historyPoints);
            resultsContainer.addView(chart);
            resultsContainer.addView(smallText("Pontos renderizados: " + detail.historyPoints.size() + " ao longo de até 1 ano."));
        }

        if (detail.revenueRegion != null) {
            resultsContainer.addView(section("Faturamento por região"));
            addBreakdownChart(resultsContainer, detail.revenueRegion);
        }
        if (detail.revenueBusiness != null) {
            resultsContainer.addView(section("Faturamento por negócio"));
            addBreakdownChart(resultsContainer, detail.revenueBusiness);
        }

        ArrayList<String[]> dividends = new ArrayList<>();
        if (detail.dividendsCount > 0) dividends.add(new String[]{"Eventos de pagamento", String.valueOf(detail.dividendsCount)});
        if (!detail.lastDividend.isEmpty()) dividends.add(new String[]{"Último provento", detail.lastDividend});
        if (!detail.nextDividend.isEmpty()) dividends.add(new String[]{"Próximo provento", detail.nextDividend});
        if (!dividends.isEmpty()) {
            resultsContainer.addView(section("Proventos"));
            resultsContainer.addView(metricGroup(dividends));
        }
    }

    private boolean isEmpty(String str) { return str == null || str.trim().isEmpty() || "{}".equals(str.trim()); }

    private AssetDetail buildAssetDetail(String ticker, AssetPayload payload) {
        AssetDetail d = new AssetDetail();
        d.isFii = ticker.endsWith("11");
        d.displayName = ticker;

        JSONObject full = JsonUtils.parseObject(payload.cardJson);
        JSONObject root = JsonUtils.unwrap(full);
        JSONObject mobile = firstObject(full, "appMobileSnapshot", "snapshot");
        JSONObject quote = firstObject(mobile, "quote");
        if (quote.length() == 0) quote = firstObject(root, "quote", "cotacao");
        if (quote.length() == 0) quote = root;

        d.displayName = firstText(firstDeepString(full, "name", "companyName", "shortName", "longName", "razaoSocial", "fundName"), ticker);
        d.sector = firstText(firstDeepString(full, "sector", "segment", "industry", "category", "setor", "segmento"), "");
        d.price = firstDeepDouble(quote, "price", "lastPrice", "currentPrice", "precoAtual", "regularMarketPrice");
        d.change = firstDeepDouble(quote, "variationPct", "changePercent", "changePct", "variacao", "regularMarketChangePercent");

        mergeFundamentals(d, payload.indicatorsJson);
        mergeFundamentals(d, payload.fundamentalsJson);
        mergeFundamentals(d, payload.valuationJson);
        mergeFundamentals(d, payload.profitabilityJson);
        mergeFundamentals(d, payload.debtJson);
        mergeFundamentals(d, payload.statementsJson);
        mergeFundamentals(d, payload.profileJson);
        mergeFundamentals(d, payload.incomeJson);
        mergeFundamentals(d, payload.patrimonialJson);
        mergeFundamentals(d, payload.portfolioJson);
        mergeFundamentals(d, payload.vacancyJson);
        mergeFundamentals(d, full.toString());

        d.historyPoints = firstNonEmptySeries(full, payload.historyJson, payload.dividendsJson, payload.statementsJson);
        d.revenueRegion = firstBreakdown(full, "revenueGeography", "regioesReceita", "revenueByRegion", "geografiaReceita");
        d.revenueBusiness = firstBreakdown(full, "revenueByBusiness", "revenueSegment", "negociosReceita", "segmentosReceita");
        if (d.revenueRegion == null) d.revenueRegion = firstBreakdown(JsonUtils.parseObject(payload.statementsJson), "revenueGeography", "regioesReceita", "revenueByRegion", "geografiaReceita");
        if (d.revenueBusiness == null) d.revenueBusiness = firstBreakdown(JsonUtils.parseObject(payload.statementsJson), "revenueByBusiness", "revenueSegment", "negociosReceita", "segmentosReceita");

        extractDividendSummary(d, payload.dividendsJson);
        return d;
    }

    private void mergeFundamentals(AssetDetail d, String json) {
        if (json == null || json.trim().isEmpty()) return;
        JSONObject full = JsonUtils.parseObject(json);
        d.dy = firstNonZero(d.dy, firstDeepDouble(full, "dividendYield", "dy", "yield", "annualYield", "yield12m"));
        d.pvp = firstNonZero(d.pvp, firstDeepDouble(full, "pvp", "p_vp", "priceToBook", "vp", "pVp"));
        d.pl = firstNonZero(d.pl, firstDeepDouble(full, "pl", "p_l", "pe", "priceEarnings", "pL"));
        d.roe = firstNonZero(d.roe, firstDeepDouble(full, "roe", "ROE"));
        d.roic = firstNonZero(d.roic, firstDeepDouble(full, "roic", "ROIC"));
        d.netMargin = firstNonZero(d.netMargin, firstDeepDouble(full, "margemLiquida", "netMargin", "liquidMargin"));
        d.payout = firstNonZero(d.payout, firstDeepDouble(full, "payout", "payoutRatio"));
        d.netDebtEbitda = firstNonZero(d.netDebtEbitda, firstDeepDouble(full, "dividaLiquidaEbitda", "netDebtEbitda", "dlEbitda"));
        d.liquidity = firstNonZero(d.liquidity, firstDeepDouble(full, "liquidezMediaDiaria", "dailyLiquidity", "liquidity", "volume"));
        d.netRevenue = firstNonZero(d.netRevenue, firstDeepDouble(full, "receitaLiquida", "netRevenue", "revenue", "faturamento12m"));
        d.netIncome = firstNonZero(d.netIncome, firstDeepDouble(full, "lucroLiquido", "netIncome", "profit", "lucro12m"));
        d.assetsValue = firstNonZero(d.assetsValue, firstDeepDouble(full, "ativos", "totalAssets", "assetValue"));
        d.equityValue = firstNonZero(d.equityValue, firstDeepDouble(full, "patrimonioLiquido", "equity", "patrimonio", "patrimonioTotal"));
        d.vpPerShare = firstNonZero(d.vpPerShare, firstDeepDouble(full, "vpPorCota", "valorPatrimonialPorCota", "bookValuePerShare", "vpa"));
        d.physicalVacancy = firstNonZero(d.physicalVacancy, firstDeepDouble(full, "vacanciaFisica", "physicalVacancy"));
        d.financialVacancy = firstNonZero(d.financialVacancy, firstDeepDouble(full, "vacanciaFinanceira", "financialVacancy"));
        d.fundType = firstText(d.fundType, firstDeepString(full, "tipoFundo", "fundType", "type"));
        d.management = firstText(d.management, firstDeepString(full, "gestao", "management", "manager"));
        d.listingSegment = firstText(d.listingSegment, firstDeepString(full, "segmentoListagem", "listingSegment", "governance"));
        if (d.sector.isEmpty()) d.sector = firstText(firstDeepString(full, "sector", "segment", "industry", "category", "setor", "segmento"), "");
    }

    private void extractDividendSummary(AssetDetail d, String json) {
        if (json == null || json.trim().isEmpty()) return;
        JSONObject root = JsonUtils.parseObject(json);
        JSONArray arr = firstArray(root, "items", "dividends", "events", "results", "data", "payments", "records");
        d.dividendsCount = arr.length();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.optJSONObject(i);
            if (row == null) continue;
            double amount = firstDeepDouble(row, "amount", "value", "valor", "cashAmount", "valuePerShare", "valorPorCota");
            String date = firstDeepString(row, "paymentDate", "date", "dataPagamento", "payDate", "exDate");
            String label = (amount > 0 ? MoneyUtils.brl(amount) : "") + (date.isEmpty() ? "" : " em " + DateUtils.formatBr(date));
            if (d.lastDividend.isEmpty() && label.length() > 0) d.lastDividend = label;
            break;
        }
    }

    private TextView section(String text) {
        return UiFactory.sectionTitle(requireContext(), text);
    }

    private TextView smallText(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_text_secondary));
        tv.setPadding(0, UiFactory.dp(requireContext(), 4), 0, UiFactory.dp(requireContext(), 12));
        return tv;
    }

    private View metricGroup(ArrayList<String[]> rows) {
        LinearLayout wrap = new LinearLayout(requireContext());
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, 0, 0, UiFactory.dp(requireContext(), 8));
        for (String[] row : rows) {
            wrap.addView(metricRow(row[0], row[1]));
        }
        return wrap;
    }

    private View metricRow(String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiFactory.dp(requireContext(), 7), 0, UiFactory.dp(requireContext(), 7));
        TextView left = new TextView(requireContext());
        left.setText(label);
        left.setTextSize(14);
        left.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_text_secondary));
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView right = new TextView(requireContext());
        right.setText(value);
        right.setTextSize(14);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        right.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_text_primary));
        row.addView(left);
        row.addView(right);
        return row;
    }

    private void addMetric(ArrayList<String[]> list, String label, double value, String format) {
        if (value <= 0 && "pct".equals(format)) return;
        if (value == 0d) return;
        String vStr = value == 0d ? "—" : "brl".equals(format) ? MoneyUtils.brl(value) : "pct".equals(format) ? MoneyUtils.pct(value) : String.valueOf(value);
        list.add(new String[]{label, vStr});
    }

    private void addBreakdownChart(LinearLayout container, Breakdown breakdown) {
        if (breakdown.items.isEmpty()) return;
        DonutChartView chart = new DonutChartView(requireContext());
        chart.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiFactory.dp(requireContext(), 220)));
        chart.setSegments(breakdown.items);
        container.addView(chart);
        container.addView(smallText(breakdown.source + " • Mostrando apenas partes representativas"));
    }

    // Parsing helpers (flattened for speed & standalone)
    private double firstDeepDouble(JSONObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                try { return obj.getDouble(k); } catch (Exception ignored) {}
            }
        }
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            Object child = obj.opt(it.next());
            if (child instanceof JSONObject) {
                double val = firstDeepDouble((JSONObject) child, keys);
                if (val != 0d) return val;
            }
            if (child instanceof JSONArray) {
                JSONArray arr = (JSONArray) child;
                for (int i = 0; i < arr.length(); i++) {
                    if (arr.optJSONObject(i) != null) {
                        double val = firstDeepDouble(arr.optJSONObject(i), keys);
                        if (val != 0d) return val;
                    }
                }
            }
        }
        return 0d;
    }

    private String firstDeepString(JSONObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                String val = obj.optString(k, "");
                if (!val.trim().isEmpty()) return val.trim();
            }
        }
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            Object child = obj.opt(it.next());
            if (child instanceof JSONObject) {
                String val = firstDeepString((JSONObject) child, keys);
                if (!val.isEmpty()) return val;
            }
            if (child instanceof JSONArray) {
                JSONArray arr = (JSONArray) child;
                for (int i = 0; i < arr.length(); i++) {
                    if (arr.optJSONObject(i) != null) {
                        String val = firstDeepString(arr.optJSONObject(i), keys);
                        if (!val.isEmpty()) return val;
                    }
                }
            }
        }
        return "";
    }

    private JSONObject firstObject(JSONObject parent, String... possibleNames) {
        for (String name : possibleNames) {
            if (parent.has(name) && parent.optJSONObject(name) != null) return parent.optJSONObject(name);
        }
        return new JSONObject();
    }

    private JSONArray firstArray(JSONObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && obj.optJSONArray(k) != null) return obj.optJSONArray(k);
        }
        return new JSONArray();
    }

    private double firstNonZero(double a, double b) { return a != 0d ? a : b; }
    private String firstText(String a, String fallback) { return a == null || a.trim().isEmpty() ? fallback : a.trim(); }

    private List<Float> firstNonEmptySeries(JSONObject root, String... moreJsons) {
        List<Float> pts = extractChartSeries(root);
        if (!pts.isEmpty()) return pts;
        for (String js : moreJsons) {
            if (js == null || js.trim().isEmpty()) continue;
            pts = extractChartSeries(JsonUtils.parseObject(js));
            if (!pts.isEmpty()) return pts;
        }
        return new ArrayList<>();
    }

    private List<Float> extractChartSeries(JSONObject root) {
        List<Float> result = new ArrayList<>();
        JSONArray arr = firstArray(root, "chartSeries", "series", "history", "points", "historico", "cotacoes");
        if (arr.length() == 0) {
            JSONObject charts = firstObject(root, "charts", "chart");
            arr = firstArray(charts, "series", "points");
        }
        if (arr.length() == 0) return result;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject pt = arr.optJSONObject(i);
            if (pt != null) {
                float val = (float) firstDeepDouble(pt, "close", "price", "value", "fechamento", "valor");
                if (val > 0) result.add(val);
            }
        }
        return result;
    }

    private Breakdown firstBreakdown(JSONObject root, String... arrayKeys) {
        for (String key : arrayKeys) {
            JSONArray arr = firstArray(root, key, key + "s");
            if (arr.length() == 0) {
                JSONObject obj = firstObject(root, key, key + "s");
                arr = firstArray(obj, "items", "data", "segments", "regions");
            }
            if (arr.length() > 0) return parseBreakdown(arr, key);
        }
        return null;
    }

    private Breakdown parseBreakdown(JSONArray arr, String sourceName) {
        Breakdown b = new Breakdown();
        b.source = sourceName;
        int[] palette = {0xFF1565C0, 0xFF22C55E, 0xFFF59E0B, 0xFF8B5CF6, 0xFFEC4899, 0xFF0EA5E9};
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.optJSONObject(i);
            if (row == null) continue;
            String label = firstDeepString(row, "name", "region", "segment", "label", "nome", "setor");
            float percent = (float) firstDeepDouble(row, "percent", "weight", "percentual", "peso");
            if (percent > 0 && !label.isEmpty()) {
                b.items.add(new DonutChartView.Segment(label, percent, palette[i % palette.length]));
            }
        }
        return b.items.isEmpty() ? null : b;
    }

    private static class AssetPayload {
        String cardJson, payloadJson, fundJson, profileJson, indicatorsJson, fundamentalsJson;
        String valuationJson, profitabilityJson, debtJson, statementsJson, dividendsJson;
        String historyJson, peersJson, sourceMapJson;
        String incomeJson, patrimonialJson, portfolioJson, vacancyJson, communicationsJson, checklistJson;
    }

    private static class AssetDetail {
        boolean isFii;
        String displayName = "", sector = "";
        double price, change, dy, pvp, pl, roe, roic, netMargin, payout, netDebtEbitda, liquidity;
        double netRevenue, netIncome, assetsValue, equityValue, vpPerShare, physicalVacancy, financialVacancy;
        String fundType = "", management = "", listingSegment = "";
        int dividendsCount;
        String lastDividend = "", nextDividend = "";
        List<Float> historyPoints = new ArrayList<>();
        Breakdown revenueRegion, revenueBusiness;
    }

    private static class Breakdown {
        String source;
        List<DonutChartView.Segment> items = new ArrayList<>();
    }
}
