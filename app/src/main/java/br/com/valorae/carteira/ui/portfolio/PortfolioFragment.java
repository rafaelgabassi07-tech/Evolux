package br.com.valorae.carteira.ui.portfolio;

import android.os.Bundle;
import android.text.InputType;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import br.com.valorae.carteira.R;
import br.com.valorae.carteira.model.Position;
import br.com.valorae.carteira.ui.UiFactory;
import br.com.valorae.carteira.ui.base.BaseAsyncFragment;
import br.com.valorae.carteira.ui.widgets.DonutChartView;
import br.com.valorae.carteira.ui.widgets.LineChartView;
import br.com.valorae.carteira.util.DateUtils;
import br.com.valorae.carteira.util.JsonUtils;
import br.com.valorae.carteira.util.MoneyUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class PortfolioFragment extends BaseAsyncFragment {
    private View root;
    private TextView portfolioValueText;
    private TextView portfolioSubtitleText;
    private LinearLayout legendContainer;
    private DonutChartView chartView;
    private RecyclerView recyclerView;
    private PortfolioAdapter adapter;
    private final Map<String, QuoteInfo> quotes = new HashMap<>();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_portfolio, container, false);
        initRepository();
        portfolioValueText = root.findViewById(R.id.portfolioValueText);
        portfolioSubtitleText = root.findViewById(R.id.portfolioSubtitleText);
        legendContainer = root.findViewById(R.id.legendContainer);
        chartView = root.findViewById(R.id.allocationChart);
        recyclerView = root.findViewById(R.id.portfolioRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setNestedScrollingEnabled(false);
        adapter = new PortfolioAdapter();
        recyclerView.setAdapter(adapter);
        root.findViewById(R.id.btnSyncPortfolio).setOnClickListener(v -> syncQuotes());
        root.findViewById(R.id.fabAdd).setOnClickListener(v -> showAddDialog());
        refreshLocal();
        syncQuotes();
        return root;
    }

    private void refreshLocal() {
        List<Position> positions = repository.positions();
        double invested = repository.investedTotal();
        portfolioValueText.setText(MoneyUtils.brl(invested));
        portfolioSubtitleText.setText(positions.size() + " posições • datas de compra consideradas");
        adapter.submit(positions, quotes);
        renderAllocation(positions);
    }

    private void syncQuotes() {
        bindLoader(root, true);
        runAsync(() -> repository.assets(), value -> {
            bindLoader(root, false);
            parseQuotes(String.valueOf(value));
            refreshLocal();
            Snackbar.make(root, "Cotações sincronizadas pelo Proxy.", Snackbar.LENGTH_SHORT).show();
        }, error -> {
            bindLoader(root, false);
            refreshLocal();
            Snackbar.make(root, "Sem cotação remota. Exibindo dados locais.", Snackbar.LENGTH_SHORT).show();
        });
    }

    private void parseQuotes(String raw) {
        quotes.clear();
        JSONObject rootObj = JsonUtils.parseObject(raw);
        JSONObject data = JsonUtils.unwrap(rootObj);
        JSONArray assets = JsonUtils.getArray(data, "assets", "items", "results", "data");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String ticker = firstText(firstDeepString(asset, "ticker", "symbol", "code"), "");
            if (ticker.isEmpty()) continue;
            JSONObject quote = firstObject(asset, "appMobileSnapshot", "quote", "cotacao", "snapshot", "results");
            if (quote.length() == 0) quote = asset;
            double price = firstDeepDouble(quote, "precoAtual", "price", "regularMarketPrice", "lastPrice", "currentPrice");
            double dy = firstDeepDouble(asset, "dividendYield", "dy", "annualYield", "yield12m");
            double quality = firstDeepDouble(asset, "score", "qualityScore", "confidence");
            quotes.put(ticker.toUpperCase(Locale.ROOT), new QuoteInfo(price, dy, quality));
        }
    }

    private void renderAllocation(List<Position> positions) {
        legendContainer.removeAllViews();
        ArrayList<DonutChartView.Segment> segments = new ArrayList<>();
        int[] colors = new int[]{0xFF1565C0, 0xFF22C55E, 0xFFF59E0B, 0xFF8B5CF6, 0xFFEC4899, 0xFF0EA5E9};
        double total = 0;
        for (Position p : positions) total += p.investedValue();
        for (int i = 0; i < positions.size(); i++) {
            Position p = positions.get(i);
            float percent = total > 0 ? (float) ((p.investedValue() / total) * 100f) : 0f;
            int color = colors[i % colors.length];
            segments.add(new DonutChartView.Segment(p.ticker, percent, color));
            legendContainer.addView(UiFactory.rowCard(requireContext(), p.ticker, p.assetType + " • " + MoneyUtils.brl(p.investedValue()), MoneyUtils.pct(percent), color));
        }
        chartView.setSegments(segments);
    }

    private void showAddDialog() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = UiFactory.dp(requireContext(), 20);
        container.setPadding(pad, pad, pad, 0);

        EditText ticker = input("Ticker", InputType.TYPE_CLASS_TEXT);
        EditText type = input("Tipo (ACAO/FII/ETF)", InputType.TYPE_CLASS_TEXT);
        EditText quantity = input("Quantidade", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText avg = input("Preço médio", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText target = input("Meta %", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText purchaseDate = input("Data de compra (AAAA-MM-DD)", InputType.TYPE_CLASS_DATETIME);

        type.setText("ACAO");
        purchaseDate.setText(DateUtils.todayIso());
        container.addView(ticker); container.addView(type); container.addView(quantity); container.addView(avg); container.addView(target); container.addView(purchaseDate);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Nova posição")
                .setView(container)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    try {
                        repository.savePosition(
                                ticker.getText().toString().trim().toUpperCase(Locale.ROOT),
                                type.getText().toString().trim().toUpperCase(Locale.ROOT),
                                Double.parseDouble(quantity.getText().toString().replace(',', '.')),
                                Double.parseDouble(avg.getText().toString().replace(',', '.')),
                                target.getText().toString().isEmpty() ? 0 : Double.parseDouble(target.getText().toString().replace(',', '.')),
                                DateUtils.normalizeIsoDate(purchaseDate.getText().toString(), DateUtils.todayIso())
                        );
                        refreshLocal();
                    } catch (Exception e) {
                        Snackbar.make(root, "Preencha todos os campos corretamente.", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAssetDetails(Position position) {
        if (position == null || position.ticker == null || position.ticker.trim().isEmpty()) return;
        bindLoader(root, true);
        runAsync(() -> {
            boolean isFii = "FII".equalsIgnoreCase(position.assetType) || "FIIS".equalsIgnoreCase(position.assetType);
            AssetPayload payload = new AssetPayload();
            payload.cardJson = repository.assetCard(position.ticker);
            payload.indicatorsJson = isFii ? repository.fiiIndicators(position.ticker) : repository.assetIndicators(position.ticker);
            payload.profileJson = isFii ? repository.fiiProfile(position.ticker) : repository.assetProfile(position.ticker);
            payload.dividendsJson = repository.assetDividends(position.ticker);
            payload.historyJson = repository.assetHistory(position.ticker, "1Y");
            payload.sourceMapJson = repository.assetSourceMap(position.ticker);
            if (isFii) {
                payload.incomeJson = repository.fiiIncome(position.ticker);
                payload.patrimonialJson = repository.fiiPatrimonial(position.ticker);
                payload.portfolioJson = repository.fiiPortfolio(position.ticker);
                payload.vacancyJson = repository.fiiVacancy(position.ticker);
                payload.communicationsJson = repository.fiiCommunications(position.ticker);
                payload.checklistJson = repository.fiiChecklist(position.ticker);
            } else {
                payload.fundamentalsJson = repository.assetFundamentals(position.ticker);
                payload.valuationJson = repository.assetValuation(position.ticker);
                payload.profitabilityJson = repository.assetProfitability(position.ticker);
                payload.debtJson = repository.assetDebt(position.ticker);
                payload.statementsJson = repository.assetStatements(position.ticker);
                payload.peersJson = repository.assetPeers(position.ticker);
            }
            return payload;
        }, value -> {
            bindLoader(root, false);
            renderAssetDialog(position, (AssetPayload) value, false);
        }, error -> {
            bindLoader(root, false);
            renderAssetDialog(position, null, true);
        });
    }

    private void renderAssetDialog(Position position, AssetPayload payload, boolean offline) {
        AssetDetail detail = buildAssetDetail(position, payload, offline);
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = UiFactory.dp(requireContext(), 18);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);

        TextView title = new TextView(requireContext());
        title.setText(detail.displayName);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_text_primary));
        content.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText((detail.sector.isEmpty() ? position.assetType : detail.sector) + " • " + DateUtils.humanDuration(DateUtils.holdingDays(position.purchaseDate)) + " em carteira");
        subtitle.setTextSize(13);
        subtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_text_secondary));
        subtitle.setPadding(0, UiFactory.dp(requireContext(), 4), 0, UiFactory.dp(requireContext(), 10));
        content.addView(subtitle);

        if (offline) {
            content.addView(UiFactory.emptyState(requireContext(), "Modo offline/cache", "Não foi possível hidratar todos os detalhes agora. O app manteve a posição local e tentará usar o cache salvo do Proxy."));
        }

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        content.addView(grid);
        grid.addView(UiFactory.metricCard(requireContext(), "Preço atual", MoneyUtils.brl(detail.price), detail.change == 0d ? "Cotação do Proxy/cache" : MoneyUtils.signedPct(detail.change)));
        grid.addView(UiFactory.metricCard(requireContext(), "Valor da posição", MoneyUtils.brl(detail.currentValue), MoneyUtils.signedBrl(detail.pnl) + " · " + MoneyUtils.signedPct(detail.pnlPct)));
        grid.addView(UiFactory.metricCard(requireContext(), "Investido", MoneyUtils.brl(position.investedValue()), "PM " + MoneyUtils.brl(position.averagePrice) + " · compra " + DateUtils.formatBr(position.purchaseDate)));

        content.addView(section("Dados da posição"));
        content.addView(metricGroup(new String[][]{
                {"Classe", position.assetType},
                {"Quantidade", compactQuantity(position.quantity)},
                {"Preço médio", MoneyUtils.brl(position.averagePrice)},
                {"Data inicial", DateUtils.formatBr(position.purchaseDate)},
                {"Tempo de posse", DateUtils.humanDuration(DateUtils.holdingDays(position.purchaseDate))}
        }));

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
            content.addView(section("Indicadores e fundamentos"));
            content.addView(metricGroup(fundamentals));
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
                content.addView(section("Contrato FII"));
                content.addView(metricGroup(fii));
            }
        } else {
            ArrayList<String[]> company = new ArrayList<>();
            addMetric(company, "Receita líquida", detail.netRevenue, "brl");
            addMetric(company, "Lucro líquido", detail.netIncome, "brl");
            addMetric(company, "Ativos", detail.assetsValue, "brl");
            addMetric(company, "Patrimônio líquido", detail.equityValue, "brl");
            if (!detail.listingSegment.isEmpty()) company.add(new String[]{"Segmento", detail.listingSegment});
            if (!company.isEmpty()) {
                content.addView(section("Empresa e balanço"));
                content.addView(metricGroup(company));
            }
        }

        if (detail.historyPoints.size() >= 2) {
            content.addView(section("Gráfico do Proxy"));
            LineChartView chart = new LineChartView(requireContext());
            chart.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiFactory.dp(requireContext(), 210)));
            chart.setPoints(detail.historyPoints);
            content.addView(chart);
            content.addView(smallText("Fonte preferencial: appMobileSnapshot.charts, appPayload.charts ou chartSeries.series. Pontos carregados: " + detail.historyPoints.size() + "."));
        }

        if (detail.revenueRegion != null) {
            content.addView(section("Faturamento por região"));
            addBreakdownChart(content, detail.revenueRegion);
        }
        if (detail.revenueBusiness != null) {
            content.addView(section("Faturamento por negócio"));
            addBreakdownChart(content, detail.revenueBusiness);
        }

        ArrayList<String[]> dividends = new ArrayList<>();
        if (detail.dividendsCount > 0) dividends.add(new String[]{"Eventos localizados", String.valueOf(detail.dividendsCount)});
        if (!detail.lastDividend.isEmpty()) dividends.add(new String[]{"Último provento", detail.lastDividend});
        if (!detail.nextDividend.isEmpty()) dividends.add(new String[]{"Próximo provento", detail.nextDividend});
        if (!dividends.isEmpty()) {
            content.addView(section("Proventos"));
            content.addView(metricGroup(dividends));
        }

        ArrayList<String[]> contract = new ArrayList<>();
        if (!detail.contractScore.isEmpty()) contract.add(new String[]{"Score contrato", detail.contractScore});
        if (!detail.renderState.isEmpty()) contract.add(new String[]{"Estado render", detail.renderState});
        if (!detail.sourceTrace.isEmpty()) contract.add(new String[]{"Fonte", detail.sourceTrace});
        if (!contract.isEmpty()) {
            content.addView(section("Rastreabilidade do Proxy"));
            content.addView(metricGroup(contract));
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(position.ticker + " — detalhe do ativo")
                .setView(scroll)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private AssetDetail buildAssetDetail(Position position, AssetPayload payload, boolean offline) {
        QuoteInfo localQuote = quotes.get(position.ticker);
        AssetDetail d = new AssetDetail();
        d.isFii = "FII".equalsIgnoreCase(position.assetType) || "FIIS".equalsIgnoreCase(position.assetType);
        d.displayName = position.ticker;
        d.dy = localQuote == null ? 0d : localQuote.dividendYield;

        JSONObject full = payload == null ? new JSONObject() : JsonUtils.parseObject(payload.cardJson);
        JSONObject root = JsonUtils.unwrap(full);
        JSONObject mobile = firstObject(full, "appMobileSnapshot", "snapshot");
        JSONObject appPayload = firstObject(full, "appPayload");
        JSONObject assetClass = firstObject(full, "assetClassContract");
        JSONObject quote = firstObject(mobile, "quote");
        if (quote.length() == 0) quote = firstObject(root, "quote", "cotacao");
        if (quote.length() == 0) quote = root;

        d.displayName = firstText(firstDeepString(full, "name", "companyName", "shortName", "longName", "razaoSocial", "fundName"), position.ticker);
        d.sector = firstText(firstDeepString(full, "sector", "segment", "industry", "category", "setor", "segmento"), "");
        d.price = firstNonZero(firstDeepDouble(quote, "price", "lastPrice", "currentPrice", "precoAtual", "regularMarketPrice"), localQuote == null ? 0d : localQuote.price);
        d.change = firstDeepDouble(quote, "variationPct", "changePercent", "changePct", "variacao", "regularMarketChangePercent");

        mergeFundamentals(d, payload == null ? null : payload.indicatorsJson);
        mergeFundamentals(d, payload == null ? null : payload.fundamentalsJson);
        mergeFundamentals(d, payload == null ? null : payload.valuationJson);
        mergeFundamentals(d, payload == null ? null : payload.profitabilityJson);
        mergeFundamentals(d, payload == null ? null : payload.debtJson);
        mergeFundamentals(d, payload == null ? null : payload.statementsJson);
        mergeFundamentals(d, payload == null ? null : payload.profileJson);
        mergeFundamentals(d, payload == null ? null : payload.incomeJson);
        mergeFundamentals(d, payload == null ? null : payload.patrimonialJson);
        mergeFundamentals(d, payload == null ? null : payload.portfolioJson);
        mergeFundamentals(d, payload == null ? null : payload.vacancyJson);
        mergeFundamentals(d, full.toString());

        d.historyPoints = firstNonEmptySeries(full, payload == null ? null : payload.historyJson, payload == null ? null : payload.dividendsJson, payload == null ? null : payload.statementsJson);
        d.revenueRegion = firstBreakdown(full, "revenueGeography", "regioesReceita", "revenueByRegion", "geografiaReceita");
        d.revenueBusiness = firstBreakdown(full, "revenueByBusiness", "revenueSegment", "negociosReceita", "segmentosReceita");
        if (d.revenueRegion == null && payload != null) d.revenueRegion = firstBreakdown(JsonUtils.parseObject(payload.statementsJson), "revenueGeography", "regioesReceita", "revenueByRegion", "geografiaReceita");
        if (d.revenueBusiness == null && payload != null) d.revenueBusiness = firstBreakdown(JsonUtils.parseObject(payload.statementsJson), "revenueByBusiness", "revenueSegment", "negociosReceita", "segmentosReceita");

        extractDividendSummary(d, payload == null ? null : payload.dividendsJson);
        d.contractScore = firstText(firstDeepString(assetClass, "score", "completeness", "coverage"), "");
        d.renderState = firstText(firstDeepString(firstObject(full, "appRenderContract"), "state", "status", "renderSafe"), "");
        d.sourceTrace = firstText(firstDeepString(full, "source", "provider", "sourceTrace", "sourceModel"), "");
        if (d.sourceTrace.isEmpty()) d.sourceTrace = firstDeepString(assetClass, "sourceModel");

        d.currentValue = d.price > 0 ? d.price * position.quantity : position.investedValue();
        d.pnl = d.currentValue - position.investedValue();
        d.pnlPct = position.investedValue() > 0 ? (d.pnl / position.investedValue()) * 100d : 0d;
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
        JSONObject root = JsonUtils.parseObject(json);
        JSONArray arr = firstArray(root, "items", "dividends", "events", "results", "data", "payments", "records");
        d.dividendsCount = arr.length();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.optJSONObject(i);
            if (row == null) continue;
            double amount = firstDeepDouble(row, "amount", "value", "valor", "cashAmount", "valuePerShare", "valorPorCota");
            String date = firstDeepString(row, "paymentDate", "date", "dataPagamento", "payDate", "exDate");
            String label = (amount > 0 ? MoneyUtils.brl(amount) : "valor não informado") + (date.isEmpty() ? "" : " em " + DateUtils.formatBr(date));
            if (d.lastDividend.isEmpty()) d.lastDividend = label;
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

    private View metricGroup(List<String[]> rows) {
        return metricGroup(rows.toArray(new String[0][0]));
    }

    private View metricGroup(String[][] rows) {
        LinearLayout wrap = new LinearLayout(requireContext());
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, 0, 0, UiFactory.dp(requireContext(), 8));
        for (String[] row : rows) {
            if (row == null || row.length < 2 || row[1] == null || row[1].trim().isEmpty() || "—".equals(row[1])) continue;
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
        left.setTextSize(13);
        left.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_text_secondary));
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView right = new TextView(requireContext());
        right.setText(value);
        right.setTextSize(13);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        right.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_text_primary));
        right.setGravity(Gravity.END);
        row.addView(left);
        row.addView(right);
        return row;
    }

    private void addMetric(ArrayList<String[]> rows, String label, double value, String kind) {
        if (value == 0d) return;
        String formatted;
        if ("pct".equals(kind)) formatted = MoneyUtils.pct(value);
        else if ("brl".equals(kind)) formatted = MoneyUtils.brl(value);
        else formatted = MoneyUtils.compact(value);
        rows.add(new String[]{label, formatted});
    }

    private void addBreakdownChart(LinearLayout content, Breakdown breakdown) {
        if (breakdown == null || breakdown.values.isEmpty()) return;
        DonutChartView donut = new DonutChartView(requireContext());
        donut.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiFactory.dp(requireContext(), 220)));
        int[] colors = new int[]{0xFF1565C0, 0xFF22C55E, 0xFFF59E0B, 0xFF8B5CF6, 0xFFEC4899, 0xFF0EA5E9};
        ArrayList<DonutChartView.Segment> segments = new ArrayList<>();
        for (int i = 0; i < breakdown.values.size(); i++) {
            String label = i < breakdown.labels.size() ? breakdown.labels.get(i) : "Item " + (i + 1);
            segments.add(new DonutChartView.Segment(label, breakdown.values.get(i), colors[i % colors.length]));
        }
        donut.setSegments(segments);
        content.addView(donut);
        for (int i = 0; i < breakdown.values.size(); i++) {
            String label = i < breakdown.labels.size() ? breakdown.labels.get(i) : "Item " + (i + 1);
            int color = colors[i % colors.length];
            content.addView(UiFactory.rowCard(requireContext(), label, "Participação no faturamento", MoneyUtils.pct(breakdown.values.get(i)), color));
        }
    }

    private List<Float> firstNonEmptySeries(JSONObject full, String... payloads) {
        List<Float> out = extractChartSeries(full);
        if (out.size() >= 2) return out;
        if (payloads != null) {
            for (String payload : payloads) {
                if (payload == null) continue;
                out = extractChartSeries(JsonUtils.parseObject(payload));
                if (out.size() >= 2) return out;
            }
        }
        return out;
    }

    private List<Float> extractChartSeries(JSONObject root) {
        ArrayList<Float> out = new ArrayList<>();
        JSONArray series = firstArray(root, "series", "chartSeries", "charts", "history", "items", "data", "results");
        if (series.length() == 0) return out;
        JSONArray data = null;
        for (int i = 0; i < series.length(); i++) {
            Object item = series.opt(i);
            if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                data = firstArray(obj, "data", "points", "values", "items", "series");
                if (data.length() > 0) break;
                Double d = firstNumber(obj, "value", "close", "price", "preco", "y", "total", "amount");
                if (d != null) out.add(d.floatValue());
            } else if (item instanceof JSONArray || item instanceof Number || item instanceof String) {
                data = series;
                break;
            }
        }
        if (data != null && data.length() > 0) {
            out.clear();
            for (int i = 0; i < data.length() && out.size() < 80; i++) {
                Object point = data.opt(i);
                Double value = null;
                if (point instanceof Number || point instanceof String) value = JsonUtils.toDouble(point);
                else if (point instanceof JSONArray) {
                    JSONArray arr = (JSONArray) point;
                    // OHLC [timestamp, open, high, low, close] or [x, y]
                    value = JsonUtils.toDouble(arr.opt(arr.length() >= 5 ? 4 : arr.length() - 1));
                } else if (point instanceof JSONObject) {
                    value = firstNumber((JSONObject) point, "value", "close", "price", "preco", "y", "total", "amount", "valor", "rendimento", "dy", "pvp", "pl");
                }
                if (value != null) out.add(value.floatValue());
            }
        }
        return out;
    }

    private Breakdown firstBreakdown(JSONObject root, String... keys) {
        for (String key : keys) {
            JSONObject obj = findObjectByKey(root, key);
            Breakdown b = parseBreakdown(obj);
            if (b != null) return b;
        }
        return null;
    }

    private Breakdown parseBreakdown(JSONObject obj) {
        if (obj == null || obj.length() == 0) return null;
        JSONArray labels = firstArray(obj, "labels", "categories", "names");
        JSONArray values = firstArray(obj, "series", "values", "data", "percentages");
        ArrayList<String> outLabels = new ArrayList<>();
        ArrayList<Float> outValues = new ArrayList<>();
        for (int i = 0; i < labels.length(); i++) {
            String label = String.valueOf(labels.opt(i)).trim();
            Double value = JsonUtils.toDouble(values.opt(i));
            if (value != null && !label.isEmpty()) {
                outLabels.add(label);
                outValues.add(value.floatValue());
            }
        }
        if (outValues.isEmpty()) {
            JSONArray rows = firstArray(obj, "items", "rows", "data");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String label = firstDeepString(row, "label", "name", "segment", "region", "title");
                Double value = firstNumber(row, "percent", "percentage", "value", "valor", "share");
                if (value != null && !label.isEmpty()) {
                    outLabels.add(label);
                    outValues.add(value.floatValue());
                }
            }
        }
        if (outValues.isEmpty()) return null;
        Breakdown b = new Breakdown();
        b.labels = outLabels;
        b.values = outValues;
        return b;
    }

    private JSONObject firstObject(JSONObject obj, String... keys) {
        if (obj == null) return new JSONObject();
        for (String key : keys) {
            JSONObject found = findObjectByKey(obj, key);
            if (found != null && found.length() > 0) return found;
        }
        return new JSONObject();
    }

    private JSONArray firstArray(JSONObject obj, String... keys) {
        if (obj == null) return new JSONArray();
        for (String key : keys) {
            JSONArray direct = obj.optJSONArray(key);
            if (direct != null) return direct;
            JSONObject nested = obj.optJSONObject(key);
            if (nested != null) {
                JSONArray items = nested.optJSONArray("items");
                if (items != null) return items;
                JSONArray series = nested.optJSONArray("series");
                if (series != null) return series;
                JSONArray data = nested.optJSONArray("data");
                if (data != null) return data;
            }
        }
        for (String key : keys) {
            JSONArray found = findArrayByKey(obj, key);
            if (found != null && found.length() > 0) return found;
        }
        return new JSONArray();
    }

    private JSONObject findObjectByKey(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            Object direct = obj.opt(key);
            if (direct instanceof JSONObject) return (JSONObject) direct;
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                Object child = obj.opt(it.next());
                JSONObject found = findObjectByKey(child, key);
                if (found != null && found.length() > 0) return found;
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject found = findObjectByKey(arr.opt(i), key);
                if (found != null && found.length() > 0) return found;
            }
        }
        return new JSONObject();
    }

    private JSONArray findArrayByKey(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            Object direct = obj.opt(key);
            if (direct instanceof JSONArray) return (JSONArray) direct;
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                JSONArray found = findArrayByKey(obj.opt(it.next()), key);
                if (found != null && found.length() > 0) return found;
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                JSONArray found = findArrayByKey(arr.opt(i), key);
                if (found != null && found.length() > 0) return found;
            }
        }
        return new JSONArray();
    }

    private double firstDeepDouble(Object node, String... keys) {
        Double v = firstNumber(node, keys);
        return v == null ? 0d : v;
    }

    private Double firstNumber(Object node, String... keys) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            for (String key : keys) {
                Double d = JsonUtils.toDouble(obj.opt(key));
                if (d != null) return d;
                Object wrapped = obj.opt(key);
                if (wrapped instanceof JSONObject) {
                    Double wd = JsonUtils.toDouble(((JSONObject) wrapped).opt("value"));
                    if (wd != null) return wd;
                }
            }
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                Double d = firstNumber(obj.opt(it.next()), keys);
                if (d != null) return d;
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                Double d = firstNumber(arr.opt(i), keys);
                if (d != null) return d;
            }
        }
        return null;
    }

    private String firstDeepString(Object node, String... keys) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            for (String key : keys) {
                Object value = obj.opt(key);
                if (value == null || value == JSONObject.NULL) continue;
                if (value instanceof JSONObject) {
                    String display = JsonUtils.getString((JSONObject) value, "display", "value", "text", "label");
                    if (!display.isEmpty()) return display;
                } else {
                    String s = String.valueOf(value).trim();
                    if (!s.isEmpty()) return s;
                }
            }
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String s = firstDeepString(obj.opt(it.next()), keys);
                if (!s.isEmpty()) return s;
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                String s = firstDeepString(arr.opt(i), keys);
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private EditText input(String hint, int type) {
        EditText edit = new EditText(requireContext());
        edit.setHint(hint);
        edit.setInputType(type);
        edit.setPadding(0, UiFactory.dp(requireContext(), 12), 0, UiFactory.dp(requireContext(), 12));
        return edit;
    }

    private String compactQuantity(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001d) return String.valueOf(Math.round(value));
        return MoneyUtils.compact(value);
    }

    private double firstNonZero(double a, double b) { return a != 0d ? a : b; }
    private String firstText(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value.trim(); }

    private static class Breakdown {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Float> values = new ArrayList<>();
    }

    private static class AssetDetail {
        boolean isFii;
        String displayName = "";
        String sector = "";
        String fundType = "";
        String management = "";
        String listingSegment = "";
        String lastDividend = "";
        String nextDividend = "";
        String contractScore = "";
        String renderState = "";
        String sourceTrace = "";
        int dividendsCount;
        double price, change, currentValue, pnl, pnlPct;
        double dy, pvp, pl, roe, roic, netMargin, payout, netDebtEbitda, liquidity;
        double netRevenue, netIncome, assetsValue, equityValue;
        double vpPerShare, physicalVacancy, financialVacancy;
        List<Float> historyPoints = new ArrayList<>();
        Breakdown revenueRegion, revenueBusiness;
    }

    private static class AssetPayload {
        String cardJson = "";
        String indicatorsJson = "";
        String profileJson = "";
        String dividendsJson = "";
        String historyJson = "";
        String fundamentalsJson = "";
        String valuationJson = "";
        String profitabilityJson = "";
        String debtJson = "";
        String statementsJson = "";
        String peersJson = "";
        String sourceMapJson = "";
        String incomeJson = "";
        String patrimonialJson = "";
        String portfolioJson = "";
        String vacancyJson = "";
        String communicationsJson = "";
        String checklistJson = "";
    }

    private static class QuoteInfo {
        final double price; final double dividendYield; final double quality;
        QuoteInfo(double price, double dividendYield, double quality) { this.price = price; this.dividendYield = dividendYield; this.quality = quality; }
    }

    private class PortfolioAdapter extends RecyclerView.Adapter<PortfolioAdapter.Holder> {
        private final List<Position> items = new ArrayList<>();
        private Map<String, QuoteInfo> quotes = Collections.emptyMap();
        private double totalCurrent = 0;

        void submit(List<Position> data, Map<String, QuoteInfo> quoteMap) {
            items.clear();
            items.addAll(data);
            quotes = new HashMap<>(quoteMap);
            totalCurrent = 0;
            for (Position p : items) {
                QuoteInfo q = quotes.get(p.ticker);
                totalCurrent += (q != null && q.price > 0 ? q.price * p.quantity : p.investedValue());
            }
            notifyDataSetChanged();
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_position, parent, false);
            return new Holder(view);
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            Position p = items.get(position);
            QuoteInfo q = quotes.get(p.ticker);
            double currentValue = q != null && q.price > 0 ? q.price * p.quantity : p.investedValue();
            double pnl = currentValue - p.investedValue();
            double pnlPct = p.investedValue() > 0 ? (pnl / p.investedValue()) * 100d : 0d;
            double allocationPct = totalCurrent > 0 ? (currentValue / totalCurrent) * 100d : 0d;
            h.iconText.setText(p.ticker.length() >= 2 ? p.ticker.substring(0, 2) : p.ticker);
            h.tickerText.setText(p.ticker);
            h.typeText.setText(p.assetType + " • " + p.quantity + " cotas");
            h.investedText.setText("Investido: " + MoneyUtils.brl(p.investedValue()) + " • PM: " + MoneyUtils.brl(p.averagePrice) + " • compra: " + DateUtils.formatBr(p.purchaseDate));
            h.currentValueText.setText(MoneyUtils.brl(currentValue));
            h.variationText.setText(MoneyUtils.signedPct(pnlPct));
            h.variationText.setTextColor(ContextCompat.getColor(requireContext(), pnl >= 0 ? R.color.valorae_positive : R.color.valorae_negative));
            String footer = "Peso " + MoneyUtils.pct(allocationPct) + " • posse " + DateUtils.humanDuration(DateUtils.holdingDays(p.purchaseDate));
            if (q != null && q.dividendYield > 0) footer += " • DY " + MoneyUtils.pct(q.dividendYield);
            h.footerText.setText(footer);
            h.progressText.setText("Meta " + MoneyUtils.pct(p.targetPercent));
            h.progressBar.setProgress((int) Math.min(100, Math.round(allocationPct)));
            h.itemView.setOnClickListener(v -> showAssetDetails(p));
            h.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Remover posição")
                        .setMessage("Deseja remover " + p.ticker + " da carteira?")
                        .setPositiveButton("Remover", (dialog, which) -> {
                            repository.deletePosition(p.id);
                            refreshLocal();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return true;
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            TextView iconText, tickerText, typeText, investedText, currentValueText, variationText, footerText, progressText;
            com.google.android.material.progressindicator.LinearProgressIndicator progressBar;
            Holder(View itemView) {
                super(itemView);
                iconText = itemView.findViewById(R.id.positionIconText);
                tickerText = itemView.findViewById(R.id.positionTickerText);
                typeText = itemView.findViewById(R.id.positionTypeText);
                investedText = itemView.findViewById(R.id.positionInvestedText);
                currentValueText = itemView.findViewById(R.id.positionCurrentValueText);
                variationText = itemView.findViewById(R.id.positionVariationText);
                footerText = itemView.findViewById(R.id.positionFooterText);
                progressText = itemView.findViewById(R.id.positionProgressText);
                progressBar = itemView.findViewById(R.id.positionProgressBar);
            }
        }
    }
}
