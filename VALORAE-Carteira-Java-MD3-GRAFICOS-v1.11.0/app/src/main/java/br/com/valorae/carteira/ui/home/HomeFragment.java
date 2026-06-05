package br.com.valorae.carteira.ui.home;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import br.com.valorae.carteira.R;
import br.com.valorae.carteira.data.StartupSyncStore;
import br.com.valorae.carteira.model.PortfolioTemporalSummary;
import br.com.valorae.carteira.model.Position;
import br.com.valorae.carteira.ui.UiFactory;
import br.com.valorae.carteira.ui.base.BaseAsyncFragment;
import br.com.valorae.carteira.util.DateUtils;
import br.com.valorae.carteira.util.JsonUtils;
import br.com.valorae.carteira.util.MoneyUtils;

public class HomeFragment extends BaseAsyncFragment {
    private View root;
    private TextView homeHeaderText;
    private TextView totalValueText;
    private TextView summaryText;
    private TextView profitText;
    private LinearLayout metricsContainer;
    private LinearLayout timelineContainer;
    private LinearLayout dividendsContainer;
    private LinearLayout pastDividendsContainer;
    private LinearLayout allocationPreviewContainer;
    private LinearLayout insightsContainer;
    private TextView proxyStatusText;
    private TextView proxyDetailsText;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_home, container, false);
        initRepository();
        homeHeaderText = root.findViewById(R.id.homeHeaderText);
        totalValueText = root.findViewById(R.id.totalValueText);
        summaryText = root.findViewById(R.id.summaryText);
        profitText = root.findViewById(R.id.profitText);
        metricsContainer = root.findViewById(R.id.metricsContainer);
        timelineContainer = root.findViewById(R.id.timelineContainer);
        dividendsContainer = root.findViewById(R.id.dividendsContainer);
        pastDividendsContainer = root.findViewById(R.id.pastDividendsContainer);
        allocationPreviewContainer = root.findViewById(R.id.allocationPreviewContainer);
        insightsContainer = root.findViewById(R.id.insightsContainer);
        proxyStatusText = root.findViewById(R.id.proxyStatusText);
        proxyDetailsText = root.findViewById(R.id.proxyDetailsText);
        root.findViewById(R.id.btnRefreshHome).setOnClickListener(v -> loadData());
        renderLocalShell();
        loadData();
        return root;
    }

    private void loadData() {
        bindLoader(root, true);
        runAsync(() -> new HomePayload(repository.ready(), repository.summary(), repository.nextDividends(), repository.dividends(), repository.allocation(), repository.income()), value -> {
            bindLoader(root, false);
            renderRemote((HomePayload) value);
        }, error -> {
            bindLoader(root, false);
            renderLocalFallback(error.getMessage());
        });
    }

    private void renderLocalShell() {
        PortfolioTemporalSummary temporal = repository.temporalSummary();
        totalValueText.setText(MoneyUtils.brl(repository.investedTotal()));
        summaryText.setText(repository.positions().size() + " ativos cadastrados • carteira iniciada em " + DateUtils.formatBr(temporal.startDate));
        profitText.setText("Atualizando dados do Proxy");
        profitText.setTextColor(Color.WHITE);
        renderTimeline(temporal);
    }

    private void renderRemote(HomePayload payload) {
        JSONObject summaryRoot = JsonUtils.unwrap(JsonUtils.parseObject(payload.summaryPayload));
        JSONObject summary = JsonUtils.getObject(summaryRoot, "summary", "totals", "portfolioSummary");
        if (summary.length() == 0) summary = summaryRoot;
        JSONObject incomeRoot = JsonUtils.unwrap(JsonUtils.parseObject(payload.incomePayload));
        JSONObject income = JsonUtils.getObject(incomeRoot, "income", "summary", "totals");
        if (income.length() == 0) income = incomeRoot;

        double invested = firstNonZero(repository.investedTotal(), JsonUtils.getDouble(summary, "totalInvestedValue", "investedValue", "totalInvested", "cost", "costValue"));
        double current = firstNonZero(JsonUtils.getDouble(summary, "totalCurrentValue", "currentValue", "marketValue", "totalValue", "portfolioValue"), invested);
        double pnl = JsonUtils.getDouble(summary, "unrealizedPnL", "unrealizedPnl", "pnl", "profit", "gain");
        if (pnl == 0 && current > 0 && invested > 0) pnl = current - invested;
        double pnlPct = JsonUtils.getDouble(summary, "unrealizedPnLPercent", "unrealizedPnlPercent", "pnlPercent", "profitability", "returnPercent");
        if (pnlPct == 0 && invested > 0) pnlPct = (pnl / invested) * 100d;
        int count = (int) firstNonZero(repository.positions().size(), JsonUtils.getDouble(summary, "positionsCount", "tickersCount", "assetsCount"));

        totalValueText.setText(MoneyUtils.brl(current));
        summaryText.setText(count + " ativos • custo " + MoneyUtils.brl(invested));
        profitText.setText((pnl >= 0 ? "Ganho " : "Perda ") + MoneyUtils.signedBrl(pnl) + " (" + MoneyUtils.signedPct(pnlPct) + ")");
        profitText.setBackgroundResource(pnl >= 0 ? R.drawable.bg_chip_success : R.drawable.bg_chip_danger);
        profitText.setTextColor(Color.WHITE);
        homeHeaderText.setText("Patrimônio atualizado");

        metricsContainer.removeAllViews();
        metricsContainer.addView(UiFactory.metricCard(requireContext(), "Patrimônio atual", MoneyUtils.brl(current), "Valor estimado da carteira hoje"));
        metricsContainer.addView(UiFactory.metricCard(requireContext(), "Valor investido", MoneyUtils.brl(invested), "Total aportado pela carteira"));
        metricsContainer.addView(UiFactory.metricCard(requireContext(), "Renda mensal", MoneyUtils.brl(JsonUtils.getDouble(income, "monthlyIncomeEstimated", "monthlyIncome", "incomeMonth")), "Projeção de proventos"));
        metricsContainer.addView(UiFactory.metricCard(requireContext(), "Yield anual", MoneyUtils.pct(JsonUtils.getDouble(income, "annualYieldOnCurrentValue", "annualYield", "dividendYield", "dy")), "Renda estimada sobre patrimônio"));

        renderTimeline(repository.temporalSummary());
        renderDividends(payload.nextDividendsPayload);
        renderPastDividends(payload.pastDividendsPayload);
        renderAllocation(payload.allocationPayload);
        renderProxyStatus(payload.readyPayload);
        renderInsights(summaryRoot, pnlPct, current, invested);
    }

    private void renderLocalFallback(String error) {
        PortfolioTemporalSummary temporal = repository.temporalSummary();
        double invested = repository.investedTotal();
        totalValueText.setText(MoneyUtils.brl(invested));
        summaryText.setText(repository.positions().size() + " ativos • modo local sem cotação remota");
        profitText.setText("Dados remotos indisponíveis");
        profitText.setBackgroundResource(R.drawable.bg_chip_warning);
        profitText.setTextColor(Color.WHITE);

        metricsContainer.removeAllViews();
        metricsContainer.addView(UiFactory.metricCard(requireContext(), "Valor investido", MoneyUtils.brl(invested), "Base local cadastrada"));
        metricsContainer.addView(UiFactory.metricCard(requireContext(), "Ações", String.valueOf(repository.countByType("ACAO")), "Quantidade de posições"));
        metricsContainer.addView(UiFactory.metricCard(requireContext(), "FIIs", String.valueOf(repository.countByType("FII")), "Quantidade de posições"));
        metricsContainer.addView(UiFactory.metricCard(requireContext(), "ETFs", String.valueOf(repository.countByType("ETF")), "Quantidade de posições"));

        renderTimeline(temporal);
        dividendsContainer.removeAllViews();
        dividendsContainer.addView(UiFactory.emptyState(requireContext(), "Proventos futuros não atualizados", "O app não recebeu a agenda de pagamentos futuros do Proxy. A carteira local continua disponível."));
        pastDividendsContainer.removeAllViews();
        pastDividendsContainer.addView(UiFactory.emptyState(requireContext(), "Histórico de proventos não atualizado", "O app não recebeu os proventos passados do Proxy nesta tentativa."));
        renderLocalAllocation();
        proxyStatusText.setText("Modo local");
        proxyStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_warning));
        proxyDetailsText.setText(error == null || error.isEmpty() ? "Sem resposta do Proxy no momento." : "Sem resposta do Proxy: " + error);
        insightsContainer.removeAllViews();
        insightsContainer.addView(UiFactory.rowCard(requireContext(), "Carteira preservada no dispositivo", "As posições continuam disponíveis mesmo sem conexão.", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
    }

    private void renderTimeline(PortfolioTemporalSummary temporal) {
        timelineContainer.removeAllViews();
        timelineContainer.addView(UiFactory.metricCard(requireContext(), "Tempo de existência", DateUtils.humanDuration(temporal.ageDays), "Desde " + DateUtils.formatBr(temporal.startDate) + " • ativo mais antigo: " + safe(temporal.oldestTicker, "—")));
        timelineContainer.addView(UiFactory.metricCard(requireContext(), "Tempo médio ponderado", DateUtils.humanDuration(Math.round(temporal.weightedHoldingDays)), "Ponderado pelo valor investido"));
    }

    private void renderDividends(String payload) {
        dividendsContainer.removeAllViews();
        JSONObject rootObj = JsonUtils.unwrap(JsonUtils.parseObject(payload));
        JSONArray items = JsonUtils.getArray(rootObj, "items", "upcomingEvents", "events", "nextDividends", "dividends");
        if (items.length() == 0) {
            dividendsContainer.addView(UiFactory.emptyState(requireContext(), "Sem proventos próximos", "Nenhum pagamento futuro foi identificado para as posições cadastradas."));
            return;
        }
        for (int i = 0; i < Math.min(5, items.length()); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            JSONObject next = JsonUtils.safeObject(item.opt("nextDividend"));
            if (next.length() == 0) next = item;
            String ticker = firstText(JsonUtils.getString(item, "ticker", "symbol", "asset"), JsonUtils.getString(next, "ticker", "symbol", "asset"), "Ativo");
            String date = JsonUtils.getString(next, "paymentDate", "date", "dataPagamento", "dataPagto", "payDate");
            double value = JsonUtils.getDouble(next, "valuePerShare", "valor", "valorPorCota", "amount", "value");
            String subtitle = date.isEmpty() ? "Pagamento previsto" : "Pagamento em " + DateUtils.formatBr(date);
            dividendsContainer.addView(UiFactory.rowCard(requireContext(), ticker, subtitle, value > 0 ? MoneyUtils.brl(value) : "—", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        }
    }

    private void renderPastDividends(String payload) {
        pastDividendsContainer.removeAllViews();
        JSONObject rootObj = JsonUtils.unwrap(JsonUtils.parseObject(payload));
        JSONArray items = JsonUtils.getArray(rootObj, "items", "received", "pastDividends", "history", "events", "dividends", "payments", "records");
        if (items.length() == 0) {
            pastDividendsContainer.addView(UiFactory.emptyState(requireContext(), "Sem proventos passados no retorno", "A rota de proventos históricos respondeu, mas não trouxe pagamentos anteriores para exibir."));
            return;
        }
        int rendered = 0;
        for (int i = 0; i < items.length() && rendered < 5; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String ticker = firstText(JsonUtils.getString(item, "ticker", "symbol", "asset"), "Ativo");
            String date = JsonUtils.getString(item, "paymentDate", "date", "dataPagamento", "dataPagto", "payDate", "exDate", "dataCom");
            double value = JsonUtils.getDouble(item, "valuePerShare", "valor", "valorPorCota", "amount", "value", "grossAmount", "netAmount", "total");
            String type = firstText(JsonUtils.getString(item, "type", "eventType", "kind"), "Provento recebido");
            String subtitle = date.isEmpty() ? type : type + " • " + DateUtils.formatBr(date);
            pastDividendsContainer.addView(UiFactory.rowCard(requireContext(), ticker, subtitle, value > 0 ? MoneyUtils.brl(value) : "—", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
            rendered++;
        }
        if (rendered == 0) {
            pastDividendsContainer.addView(UiFactory.emptyState(requireContext(), "Histórico recebido sem itens legíveis", "O Proxy retornou dados, mas nenhum item continha ticker/data/valor reconhecíveis."));
        }
    }

    private void renderAllocation(String payload) {
        allocationPreviewContainer.removeAllViews();
        JSONObject rootObj = JsonUtils.unwrap(JsonUtils.parseObject(payload));
        JSONArray byType = JsonUtils.getArray(JsonUtils.getObject(rootObj, "allocation", "byClass"), "byType", "items");
        if (byType.length() == 0) byType = JsonUtils.getArray(rootObj, "byType", "allocationByType", "classes");
        if (byType.length() == 0) {
            renderLocalAllocation();
            return;
        }
        for (int i = 0; i < Math.min(4, byType.length()); i++) {
            JSONObject row = byType.optJSONObject(i);
            if (row == null) continue;
            String label = firstText(JsonUtils.getString(row, "label", "key", "type", "assetType", "name"), "Classe");
            double pct = JsonUtils.getDouble(row, "percent", "weight", "allocationPercent");
            double value = JsonUtils.getDouble(row, "value", "currentValue", "totalValue");
            allocationPreviewContainer.addView(UiFactory.rowCard(requireContext(), label, value > 0 ? MoneyUtils.brl(value) : "Alocação da carteira", MoneyUtils.pct(pct), ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        }
    }

    private void renderLocalAllocation() {
        allocationPreviewContainer.removeAllViews();
        double total = repository.investedTotal();
        addLocalClass("Ações", "ACAO", total);
        addLocalClass("FIIs", "FII", total);
        addLocalClass("ETFs", "ETF", total);
    }

    private void addLocalClass(String label, String type, double total) {
        double value = 0;
        for (Position p : repository.positions()) if (type.equalsIgnoreCase(p.assetType)) value += p.investedValue();
        if (value <= 0) return;
        double pct = total > 0 ? value / total * 100d : 0d;
        allocationPreviewContainer.addView(UiFactory.rowCard(requireContext(), label, MoneyUtils.brl(value), MoneyUtils.pct(pct), ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
    }

    private void renderProxyStatus(String payload) {
        JSONObject ready = JsonUtils.unwrap(JsonUtils.parseObject(payload));
        String status = firstText(JsonUtils.getString(ready, "status", "ok", "state"), "online");
        String version = JsonUtils.getString(ready, "version", "engineVersion", "release");
        proxyStatusText.setText(status.toUpperCase());
        proxyStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.valorae_positive));
        StartupSyncStore startup = new StartupSyncStore(requireContext());
        proxyDetailsText.setText("Dados convertidos em cards finais" + (version.isEmpty() ? "." : " • motor " + version) + "\nConsulta automática: " + startup.compactStatus());
    }

    private void renderInsights(JSONObject summaryRoot, double pnlPct, double current, double invested) {
        insightsContainer.removeAllViews();
        JSONArray insights = JsonUtils.getArray(summaryRoot, "insights", "messages", "notes");
        if (insights.length() > 0) {
            for (String text : JsonUtils.arrayStrings(insights, 4)) insightsContainer.addView(UiFactory.rowCard(requireContext(), cleanTechnicalText(text), "Leitura do motor VALORAE", "", 0));
            return;
        }
        if (current <= 0 || invested <= 0) {
            insightsContainer.addView(UiFactory.rowCard(requireContext(), "A carteira está cadastrada e pronta para análise", "Atualize a integração para obter preço atual, renda e risco.", "", 0));
        } else if (pnlPct >= 0) {
            insightsContainer.addView(UiFactory.rowCard(requireContext(), "Carteira positiva no período", "O patrimônio atual está acima do custo informado.", MoneyUtils.signedPct(pnlPct), ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        } else {
            insightsContainer.addView(UiFactory.rowCard(requireContext(), "Carteira negativa no período", "O patrimônio atual está abaixo do custo informado. Avalie concentração e fundamentos antes de agir.", MoneyUtils.signedPct(pnlPct), ContextCompat.getColor(requireContext(), R.color.valorae_negative)));
        }
        insightsContainer.addView(UiFactory.rowCard(requireContext(), "Tempo também entra na análise", "O app considera a data de compra para mostrar idade da carteira e retorno anualizado.", "Novo", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
    }

    private String cleanTechnicalText(String text) {
        if (text == null) return "";
        return text.replace("{", "").replace("}", "").replace("\"", "").trim();
    }

    private double firstNonZero(double a, double b) { return a != 0d ? a : b; }
    private String firstText(String a, String fallback) { return a == null || a.trim().isEmpty() ? fallback : a.trim(); }
    private String firstText(String a, String b, String fallback) { return firstText(a, firstText(b, fallback)); }
    private String safe(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s; }

    private static class HomePayload {
        final String readyPayload, summaryPayload, nextDividendsPayload, pastDividendsPayload, allocationPayload, incomePayload;
        HomePayload(String readyPayload, String summaryPayload, String nextDividendsPayload, String pastDividendsPayload, String allocationPayload, String incomePayload) {
            this.readyPayload = readyPayload;
            this.summaryPayload = summaryPayload;
            this.nextDividendsPayload = nextDividendsPayload;
            this.pastDividendsPayload = pastDividendsPayload;
            this.allocationPayload = allocationPayload;
            this.incomePayload = incomePayload;
        }
    }
}
