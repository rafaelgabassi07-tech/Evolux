package br.com.valorae.carteira.ui.market;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import br.com.valorae.carteira.R;
import br.com.valorae.carteira.ui.UiFactory;
import br.com.valorae.carteira.ui.base.BaseAsyncFragment;
import br.com.valorae.carteira.util.DateUtils;
import br.com.valorae.carteira.util.JsonUtils;
import br.com.valorae.carteira.util.MoneyUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class MarketFragment extends BaseAsyncFragment {
    private View root;
    private LinearLayout indicesContainer, walletContainer, newsContainer, statusContainer;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_market, container, false);
        initRepository();
        indicesContainer = root.findViewById(R.id.indicesContainer);
        walletContainer = root.findViewById(R.id.walletMarketContainer);
        newsContainer = root.findViewById(R.id.marketNewsContainer);
        statusContainer = root.findViewById(R.id.marketStatusContainer);
        root.findViewById(R.id.btnRefreshMarket).setOnClickListener(v -> loadData());
        renderLocalShell();
        loadData();
        return root;
    }

    private void loadData() {
        bindLoader(root, true);
        runAsync(() -> new MarketPayload(repository.indices(), repository.assets(), repository.newsForFirstTicker(), repository.sourceStatus()), value -> {
            bindLoader(root, false);
            bind((MarketPayload) value);
        }, error -> {
            bindLoader(root, false);
            renderOffline(error.getMessage());
        });
    }

    private void renderLocalShell() {
        indicesContainer.removeAllViews();
        walletContainer.removeAllViews();
        newsContainer.removeAllViews();
        statusContainer.removeAllViews();
        indicesContainer.addView(UiFactory.emptyState(requireContext(), "Aguardando índices", "O app vai consultar o Proxy e converter os indicadores em cartões."));
        walletContainer.addView(UiFactory.metricCard(requireContext(), "Carteira em foco", repository.positions().size() + " ativos", "Tickers usados para buscar cotações e notícias"));
        newsContainer.addView(UiFactory.emptyState(requireContext(), "Notícias em preparação", "As notícias serão exibidas como resumo legível para o investidor."));
    }

    private void bind(MarketPayload payload) {
        renderIndices(JsonUtils.parseObject(payload.indicesJson));
        renderWallet(JsonUtils.parseObject(payload.assetsJson));
        renderNews(JsonUtils.parseObject(payload.newsJson));
        renderStatus(JsonUtils.parseObject(payload.sourceStatusJson));
    }

    private void renderIndices(JSONObject rootObj) {
        indicesContainer.removeAllViews();
        JSONObject data = JsonUtils.unwrap(rootObj);
        JSONArray indices = firstArray(data, "indices", "items", "markets", "data");
        if (indices.length() == 0) {
            indicesContainer.addView(UiFactory.emptyState(requireContext(), "Índices não recebidos", "O Proxy respondeu, mas não trouxe lista de índices renderizável."));
            return;
        }
        for (int i = 0; i < Math.min(6, indices.length()); i++) {
            JSONObject row = indices.optJSONObject(i);
            if (row == null) continue;
            String name = firstText(JsonUtils.getString(row, "name", "ticker", "symbol", "indice", "index"), "Índice");
            double price = firstNonZero(JsonUtils.getDouble(row, "price", "last", "value", "precoAtual", "close"), 0);
            double variation = JsonUtils.getDouble(row, "variationPct", "changePercent", "changePct", "variacao", "change");
            String subtitle = price > 0 ? "Último valor: " + MoneyUtils.compact(price) : "Indicador de mercado recebido do Proxy";
            int color = variation >= 0 ? ContextCompat.getColor(requireContext(), R.color.valorae_positive) : ContextCompat.getColor(requireContext(), R.color.valorae_negative);
            String trailing = variation == 0 ? "Info" : MoneyUtils.signedPct(variation);
            indicesContainer.addView(UiFactory.rowCard(requireContext(), name, subtitle, trailing, color));
        }
    }

    private void renderWallet(JSONObject rootObj) {
        walletContainer.removeAllViews();
        JSONObject data = JsonUtils.unwrap(rootObj);
        JSONArray assets = firstArray(data, "assets", "items", "results", "tickers", "data");
        if (assets.length() == 0) {
            walletContainer.addView(UiFactory.emptyState(requireContext(), "Cotações da carteira não recebidas", "O app continua com a carteira local e tentará atualizar novamente pelo Proxy."));
            return;
        }
        for (int i = 0; i < Math.min(8, assets.length()); i++) {
            JSONObject asset = JsonUtils.safeObject(assets.opt(i));
            JSONObject quote = JsonUtils.getObject(asset, "quote", "cotacao", "snapshot", "appMobileSnapshot");
            String ticker = firstText(JsonUtils.getString(asset, "ticker", "symbol", "name"), JsonUtils.getString(quote, "ticker", "symbol"));
            double price = firstNonZero(
                    JsonUtils.getDouble(asset, "precoAtual", "price", "lastPrice", "last", "currentPrice"),
                    JsonUtils.getDouble(quote, "precoAtual", "price", "lastPrice", "last", "currentPrice")
            );
            double variation = firstNonZero(
                    JsonUtils.getDouble(asset, "variationPct", "changePercent", "changePct", "variacao"),
                    JsonUtils.getDouble(quote, "variationPct", "changePercent", "changePct", "variacao")
            );
            String type = firstText(JsonUtils.getString(asset, "assetType", "type", "classe", "category"), "Ativo da carteira");
            String subtitle = price > 0 ? type + " • preço atual " + MoneyUtils.brl(price) : type + " • aguardando preço atual";
            int color = variation >= 0 ? ContextCompat.getColor(requireContext(), R.color.valorae_positive) : ContextCompat.getColor(requireContext(), R.color.valorae_negative);
            walletContainer.addView(UiFactory.rowCard(requireContext(), ticker, subtitle, variation == 0 ? "Carteira" : MoneyUtils.signedPct(variation), color));
        }
    }

    private void renderNews(JSONObject rootObj) {
        newsContainer.removeAllViews();
        JSONObject data = JsonUtils.unwrap(rootObj);
        JSONArray news = firstArray(data, "items", "news", "articles", "results", "data");
        if (news.length() == 0) {
            newsContainer.addView(UiFactory.emptyState(requireContext(), "Sem notícias relevantes", "Nenhuma notícia recente foi retornada para o principal ativo da carteira."));
            return;
        }
        for (int i = 0; i < Math.min(5, news.length()); i++) {
            JSONObject item = news.optJSONObject(i);
            if (item == null) continue;
            String title = firstText(JsonUtils.getString(item, "title", "headline", "name"), "Notícia do mercado");
            String source = JsonUtils.getString(item, "source", "publisher", "site", "origin");
            String date = JsonUtils.getString(item, "publishedAt", "date", "pubDate", "time");
            String subtitle = (source.isEmpty() ? "Fonte não informada" : source) + (date.isEmpty() ? "" : " • " + DateUtils.formatBr(date));
            newsContainer.addView(UiFactory.rowCard(requireContext(), title, subtitle, "Notícia", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        }
    }

    private void renderStatus(JSONObject sourceStatus) {
        statusContainer.removeAllViews();
        JSONObject data = JsonUtils.unwrap(sourceStatus);
        JSONArray sources = firstArray(data, "sources", "providers", "items", "data");
        String status = firstText(JsonUtils.getString(data, "status", "state", "health"), sources.length() > 0 ? "fontes mapeadas" : "resposta recebida");
        statusContainer.addView(UiFactory.rowCard(requireContext(), "Chegada de dados do Proxy", "Índices, ativos da carteira e notícias foram consultados sem usar listas ranqueadas.", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        statusContainer.addView(UiFactory.rowCard(requireContext(), "Fontes", sources.length() > 0 ? sources.length() + " fonte(s) monitorada(s)" : status, "Status", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
    }

    private void renderOffline(String error) {
        indicesContainer.removeAllViews();
        walletContainer.removeAllViews();
        newsContainer.removeAllViews();
        statusContainer.removeAllViews();
        indicesContainer.addView(UiFactory.rowCard(requireContext(), "Mercado indisponível", error == null || error.isEmpty() ? "O Proxy não respondeu agora." : error, "Local", ContextCompat.getColor(requireContext(), R.color.valorae_warning)));
        walletContainer.addView(UiFactory.metricCard(requireContext(), "Carteira local", repository.positions().size() + " ativos", "Nenhum dado remoto foi perdido; o app mantém os ativos cadastrados."));
        newsContainer.addView(UiFactory.emptyState(requireContext(), "Notícias offline", "Sem resposta remota neste momento."));
        statusContainer.addView(UiFactory.rowCard(requireContext(), "Rotas de destaque desativadas", "Esta versão não chama nem exibe rotas de destaque no APK.", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
    }

    private JSONArray firstArray(JSONObject obj, String... keys) {
        JSONArray direct = JsonUtils.getArray(obj, keys);
        if (direct.length() > 0) return direct;
        JSONObject data = JsonUtils.unwrap(obj);
        JSONArray nested = JsonUtils.getArray(data, keys);
        if (nested.length() > 0) return nested;
        for (String key : keys) {
            Object value = data.opt(key);
            if (value instanceof JSONObject) {
                JSONArray inside = JsonUtils.getArray((JSONObject) value, "items", "assets", "indices", "news", "events");
                if (inside.length() > 0) return inside;
            }
        }
        return new JSONArray();
    }

    private double firstNonZero(double a, double b) { return a != 0d ? a : b; }
    private String firstText(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value.trim(); }

    private static class MarketPayload {
        final String indicesJson, assetsJson, newsJson, sourceStatusJson;
        MarketPayload(String indicesJson, String assetsJson, String newsJson, String sourceStatusJson) {
            this.indicesJson = indicesJson;
            this.assetsJson = assetsJson;
            this.newsJson = newsJson;
            this.sourceStatusJson = sourceStatusJson;
        }
    }
}
