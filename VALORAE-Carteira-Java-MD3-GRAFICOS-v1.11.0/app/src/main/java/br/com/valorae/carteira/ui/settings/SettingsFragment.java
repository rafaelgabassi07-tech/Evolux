package br.com.valorae.carteira.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import br.com.valorae.carteira.BuildConfig;
import br.com.valorae.carteira.data.StartupSyncStore;
import br.com.valorae.carteira.R;
import br.com.valorae.carteira.model.PortfolioTemporalSummary;
import br.com.valorae.carteira.model.Position;
import br.com.valorae.carteira.model.ImportResult;
import br.com.valorae.carteira.model.ProxyAuditItem;
import br.com.valorae.carteira.ui.UiFactory;
import br.com.valorae.carteira.ui.base.BaseAsyncFragment;
import br.com.valorae.carteira.util.DateUtils;
import br.com.valorae.carteira.util.JsonUtils;
import br.com.valorae.carteira.util.MoneyUtils;

public class SettingsFragment extends BaseAsyncFragment {
    private View root;
    private TextView baseUrlText;
    private TextView versionText;
    private LinearLayout statusContainer;
    private LinearLayout compatibilityContainer;
    private LinearLayout proxyAuditContainer;
    private LinearLayout graphicsAuditContainer;
    private LinearLayout portfolioInfoContainer;
    private LinearLayout dataOpsContainer;
    private LinearLayout syncContainer;
    private ActivityResultLauncher<String> createBackupLauncher;
    private ActivityResultLauncher<String[]> importBackupLauncher;
    private ActivityResultLauncher<String[]> importB3Launcher;
    private boolean replaceOnImport = false;
    private StartupSyncStore startupSyncStore;

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createBackupLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri != null) exportBackup(uri);
        });
        importBackupLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) importBackup(uri, replaceOnImport);
        });
        importB3Launcher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) importB3(uri, replaceOnImport);
        });
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_settings, container, false);
        initRepository();
        baseUrlText = root.findViewById(R.id.baseUrlText);
        versionText = root.findViewById(R.id.versionText);
        statusContainer = root.findViewById(R.id.statusContainer);
        compatibilityContainer = root.findViewById(R.id.compatibilityContainer);
        proxyAuditContainer = root.findViewById(R.id.proxyAuditContainer);
        graphicsAuditContainer = root.findViewById(R.id.graphicsAuditContainer);
        portfolioInfoContainer = root.findViewById(R.id.portfolioInfoContainer);
        dataOpsContainer = root.findViewById(R.id.dataOpsContainer);
        syncContainer = root.findViewById(R.id.syncContainer);
        startupSyncStore = new StartupSyncStore(requireContext());
        baseUrlText.setText(getString(R.string.proxy_base_url));
        versionText.setText("App v" + BuildConfig.VERSION_NAME + " • Java/XML • Sync automático");
        root.findViewById(R.id.btnRefreshSettings).setOnClickListener(v -> loadData());
        root.findViewById(R.id.btnPortfolioSummary).setOnClickListener(v -> showReadablePortfolioSummary());
        root.findViewById(R.id.btnRunProxyTests).setOnClickListener(v -> runProxyAudit());
        root.findViewById(R.id.btnRunGraphicsTests).setOnClickListener(v -> runGraphicsAudit());
        root.findViewById(R.id.btnSyncProxyNow).setOnClickListener(v -> runProxySync());
        root.findViewById(R.id.btnExportBackup).setOnClickListener(v -> createBackupLauncher.launch("valorae-backup-" + DateUtils.todayIso() + ".json"));
        root.findViewById(R.id.btnImportBackup).setOnClickListener(v -> chooseImportMode(false));
        root.findViewById(R.id.btnImportB3).setOnClickListener(v -> chooseImportMode(true));
        root.findViewById(R.id.btnClearProxyCache).setOnClickListener(v -> clearProxyCache());
        renderLocalInfo();
        renderDataOpsWaiting();
        renderSyncWaiting();
        renderAuditWaiting();
        loadData();
        return root;
    }

    private void loadData() {
        bindLoader(root, true);
        runAsync(() -> new SettingsPayload(repository.ready(), repository.manifest(), repository.sourceStatus()), value -> {
            bindLoader(root, false);
            renderRemote((SettingsPayload) value);
        }, error -> {
            bindLoader(root, false);
            renderOffline(error.getMessage());
        });
    }

    private void renderRemote(SettingsPayload payload) {
        JSONObject ready = JsonUtils.unwrap(JsonUtils.parseObject(payload.readyPayload));
        JSONObject manifest = JsonUtils.unwrap(JsonUtils.parseObject(payload.manifestPayload));
        JSONObject sources = JsonUtils.unwrap(JsonUtils.parseObject(payload.sourceStatusPayload));

        String status = firstText(JsonUtils.getString(ready, "status", "state", "ok"), "online");
        String version = JsonUtils.getString(ready, "version", "engineVersion", "release");
        JSONArray routes = JsonUtils.getArray(manifest, "routes", "availableRoutes", "endpoints");
        int routesCount = routes.length();
        if (routesCount == 0) routesCount = estimateRoutesFromManifest(manifest);

        statusContainer.removeAllViews();
        statusContainer.addView(UiFactory.metricCard(requireContext(), "Estado do Proxy", status.toUpperCase(), "Conectividade com servidor VALORAE"));
        statusContainer.addView(UiFactory.metricCard(requireContext(), "Motor do Proxy", version.isEmpty() ? "Detectado" : version, "Versão informada pelo /ready"));
        statusContainer.addView(UiFactory.metricCard(requireContext(), "Fontes de dados", summarizeSources(sources), "Leitura consolidada para o investidor"));

        compatibilityContainer.removeAllViews();
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Carteira", "summary, allocation, income, dividends, next-dividends, risk, history, events e rebalance", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Ativos", "assets, asset, indicators, fundamentals, dividends e history", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Mercado", "índices, ativos da carteira, watchlist e notícias — sem rotas de destaque", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Rotas no manifesto", routesCount > 0 ? routesCount + " rotas detectadas" : "Manifesto lido com fallback", "Compat", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Envelope flexível", "Parser aceita data, result, results, payload e resposta direta", "Novo", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Cache local", repository.proxyCacheStatus(), repository.proxyCacheCount() > 0 ? "Ativo" : "Pronto", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Datas de compra", "Payload envia purchaseDate, acquisitionDate, buyDate, firstPurchaseDate e holdingStartDate", "Novo", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        renderLocalInfo();
    }

    private void renderOffline(String error) {
        statusContainer.removeAllViews();
        statusContainer.addView(UiFactory.rowCard(requireContext(), "Proxy sem resposta", error == null || error.isEmpty() ? "Não foi possível atualizar o diagnóstico." : error, "Local", ContextCompat.getColor(requireContext(), R.color.valorae_warning)));
        compatibilityContainer.removeAllViews();
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Compatibilidade preservada", "Mesmo offline, o app mantém parsers flexíveis e payload temporal para quando o Proxy responder.", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Rotas de destaque desativadas", "O APK consulta apenas rotas de carteira, ativos, índices, notícias e watchlist nesta versão.", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        compatibilityContainer.addView(UiFactory.rowCard(requireContext(), "Cache local", repository.proxyCacheStatus(), repository.proxyCacheCount() > 0 ? "Ativo" : "Vazio", ContextCompat.getColor(requireContext(), repository.proxyCacheCount() > 0 ? R.color.valorae_positive : R.color.valorae_warning)));
        renderLocalInfo();
    }


    private void renderSyncWaiting() {
        if (syncContainer == null) return;
        syncContainer.removeAllViews();
        syncContainer.addView(UiFactory.rowCard(requireContext(), "Consulta automática na abertura", startupSyncStore == null ? "Executada ao abrir o aplicativo." : startupSyncStore.readableStatus(), startupSyncStore == null ? "Auto" : startupSyncStore.compactStatus(), ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        syncContainer.addView(UiFactory.rowCard(requireContext(), "Pronto para sincronizar", "Além da consulta automática, este botão força nova atualização completa do Proxy e renova o cache local.", "Manual", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        syncContainer.addView(UiFactory.rowCard(requireContext(), "Estado do cache", repository.proxyCacheDetailedStatus(), repository.proxyCacheCount() > 0 ? "Ativo" : "Vazio", ContextCompat.getColor(requireContext(), repository.proxyCacheCount() > 0 ? R.color.valorae_positive : R.color.valorae_warning)));
    }

    private void runProxySync() {
        bindLoader(root, true);
        syncContainer.removeAllViews();
        syncContainer.addView(UiFactory.emptyState(requireContext(), "Sincronizando com o Proxy", "Buscando dados de carteira, ativos, renda, risco, histórico, eventos, índices, notícias e watchlist."));
        runAsync(() -> repository.syncNow(), value -> {
            bindLoader(root, false);
            @SuppressWarnings("unchecked")
            List<ProxyAuditItem> items = (List<ProxyAuditItem>) value;
            renderProxySync(items);
            renderDataOpsWaiting();
        }, error -> {
            bindLoader(root, false);
            syncContainer.removeAllViews();
            syncContainer.addView(UiFactory.rowCard(requireContext(), "Sincronização interrompida", error.getMessage(), "Falha", ContextCompat.getColor(requireContext(), R.color.valorae_negative)));
        });
    }

    private void renderProxySync(List<ProxyAuditItem> items) {
        syncContainer.removeAllViews();
        int okCount = 0;
        for (ProxyAuditItem item : items) if (item.ok) okCount++;
        int color = okCount == items.size() ? ContextCompat.getColor(requireContext(), R.color.valorae_positive) : ContextCompat.getColor(requireContext(), R.color.valorae_warning);
        syncContainer.addView(UiFactory.metricCard(requireContext(), "Sincronização concluída", okCount + "/" + items.size(), "Respostas úteis recebidas e cache local atualizado"));
        syncContainer.addView(UiFactory.rowCard(requireContext(), "Cache após sincronização", repository.proxyCacheDetailedStatus(), okCount == items.size() ? "OK" : "Parcial", color));
        for (ProxyAuditItem item : items) {
            int itemColor = item.ok ? ContextCompat.getColor(requireContext(), R.color.valorae_positive) : ContextCompat.getColor(requireContext(), R.color.valorae_negative);
            String trailing = item.ok ? (item.recordsCount > 0 ? String.valueOf(item.recordsCount) : "OK") : "Falha";
            syncContainer.addView(UiFactory.rowCard(requireContext(), item.title, item.route + " • " + item.detail, trailing, itemColor));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sincronização VALORAE")
                .setMessage(repository.syncSummaryText(items))
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void renderAuditWaiting() {
        proxyAuditContainer.removeAllViews();
        proxyAuditContainer.addView(UiFactory.emptyState(requireContext(), "Auditoria pronta para executar", "Toque em ‘Testar chegada das informações’ para validar ready, manifesto, fontes, carteira, ativos, índices, notícias e watchlist."));
    }

    private void runProxyAudit() {
        bindLoader(root, true);
        proxyAuditContainer.removeAllViews();
        proxyAuditContainer.addView(UiFactory.emptyState(requireContext(), "Testando Proxy", "Validando as rotas usadas pelo app e convertendo cada resultado em status legível."));
        runAsync(() -> repository.proxyAudit(), value -> {
            bindLoader(root, false);
            @SuppressWarnings("unchecked")
            List<ProxyAuditItem> items = (List<ProxyAuditItem>) value;
            renderProxyAudit(items);
        }, error -> {
            bindLoader(root, false);
            proxyAuditContainer.removeAllViews();
            proxyAuditContainer.addView(UiFactory.rowCard(requireContext(), "Auditoria interrompida", error.getMessage(), "Falha", ContextCompat.getColor(requireContext(), R.color.valorae_negative)));
        });
    }

    private void renderProxyAudit(List<ProxyAuditItem> items) {
        proxyAuditContainer.removeAllViews();
        int okCount = 0;
        for (ProxyAuditItem item : items) if (item.ok) okCount++;
        proxyAuditContainer.addView(UiFactory.metricCard(requireContext(), "Rotas válidas", okCount + "/" + items.size(), "Resultado da auditoria executada dentro do APK"));
        proxyAuditContainer.addView(UiFactory.rowCard(requireContext(), "Rotas de destaque fora do APK", "Nenhum teste usa rota de lista ranqueada; a tela Mercado usa somente índices, ativos, notícias e watchlist.", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        for (ProxyAuditItem item : items) {
            int itemColor = item.ok ? ContextCompat.getColor(requireContext(), R.color.valorae_positive) : ContextCompat.getColor(requireContext(), R.color.valorae_negative);
            String trailing = item.ok ? (item.recordsCount > 0 ? String.valueOf(item.recordsCount) : "OK") : "Falha";
            proxyAuditContainer.addView(UiFactory.rowCard(requireContext(), item.title, item.route + " • " + item.detail, trailing, itemColor));
        }
    }

    private void renderLocalInfo() {
        PortfolioTemporalSummary temporal = repository.temporalSummary();
        portfolioInfoContainer.removeAllViews();
        portfolioInfoContainer.addView(UiFactory.metricCard(requireContext(), "Posições cadastradas", String.valueOf(repository.positions().size()), "Ativos salvos localmente"));
        portfolioInfoContainer.addView(UiFactory.metricCard(requireContext(), "Valor investido", MoneyUtils.brl(repository.investedTotal()), "Custo total local"));
        portfolioInfoContainer.addView(UiFactory.metricCard(requireContext(), "Início da carteira", DateUtils.formatBr(temporal.startDate), "Ativo mais antigo: " + (temporal.oldestTicker == null || temporal.oldestTicker.isEmpty() ? "—" : temporal.oldestTicker)));
        portfolioInfoContainer.addView(UiFactory.metricCard(requireContext(), "Idade da carteira", DateUtils.humanDuration(temporal.ageDays), "Tempo desde a primeira compra"));
    }

    private void showReadablePortfolioSummary() {
        List<Position> positions = repository.positions();
        PortfolioTemporalSummary temporal = repository.temporalSummary();
        StringBuilder builder = new StringBuilder();
        builder.append("Valor investido: ").append(MoneyUtils.brl(repository.investedTotal())).append("\n");
        builder.append("Posições: ").append(positions.size()).append("\n");
        builder.append("Início: ").append(DateUtils.formatBr(temporal.startDate)).append("\n");
        builder.append("Tempo de carteira: ").append(DateUtils.humanDuration(temporal.ageDays)).append("\n\n");
        builder.append("Ativos cadastrados:\n");
        for (int i = 0; i < Math.min(positions.size(), 10); i++) {
            Position p = positions.get(i);
            builder.append("• ").append(p.ticker)
                    .append(" — ").append(p.assetType)
                    .append(" — ").append(MoneyUtils.brl(p.investedValue()))
                    .append(" — compra em ").append(DateUtils.formatBr(p.purchaseDate))
                    .append("\n");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Resumo da carteira")
                .setMessage(builder.toString())
                .setPositiveButton("Fechar", null)
                .show();
    }


    private void renderDataOpsWaiting() {
        if (dataOpsContainer == null) return;
        dataOpsContainer.removeAllViews();
        dataOpsContainer.addView(UiFactory.rowCard(requireContext(), "Backup seguro", "Gera um arquivo JSON VALORAE com ticker, classe, quantidade, preço médio, meta e data de compra.", "Local", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        dataOpsContainer.addView(UiFactory.rowCard(requireContext(), "Excel da B3", "Lê .xlsx de negociações, detecta compras/vendas e consolida posições finais para o app e para o Proxy.", "Novo", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
        dataOpsContainer.addView(UiFactory.rowCard(requireContext(), "Cache inteligente do Proxy", repository.proxyCacheStatus(), repository.proxyCacheCount() > 0 ? "Ativo" : "Vazio", ContextCompat.getColor(requireContext(), repository.proxyCacheCount() > 0 ? R.color.valorae_positive : R.color.valorae_warning)));
    }

    private void chooseImportMode(boolean b3) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(b3 ? "Importar Excel da B3" : "Restaurar backup")
                .setMessage(b3
                        ? "Como deseja aplicar as negociações encontradas na planilha?"
                        : "Como deseja aplicar as posições do backup?")
                .setPositiveButton("Anexar / atualizar", (dialog, which) -> {
                    replaceOnImport = false;
                    if (b3) importB3Launcher.launch(new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/octet-stream"});
                    else importBackupLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
                })
                .setNegativeButton("Substituir carteira", (dialog, which) -> {
                    replaceOnImport = true;
                    if (b3) importB3Launcher.launch(new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/octet-stream"});
                    else importBackupLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
                })
                .setNeutralButton("Cancelar", null)
                .show();
    }

    private void exportBackup(Uri uri) {
        bindLoader(root, true);
        runAsync(() -> {
            String json = repository.exportBackupJson(BuildConfig.VERSION_NAME);
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IllegalStateException("Não foi possível abrir o destino do backup.");
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            ImportResult result = new ImportResult();
            result.positionsCreated = repository.positions().size();
            result.messages.add("Backup criado com sucesso no arquivo escolhido.");
            return result;
        }, value -> {
            bindLoader(root, false);
            renderDataResult("Backup da carteira", (ImportResult) value);
        }, error -> {
            bindLoader(root, false);
            showDataError("Falha ao criar backup", error.getMessage());
        });
    }

    private void importBackup(Uri uri, boolean replaceAll) {
        bindLoader(root, true);
        runAsync(() -> {
            String json = readText(uri);
            return repository.importBackupJson(json, replaceAll);
        }, value -> {
            bindLoader(root, false);
            renderDataResult("Backup restaurado", (ImportResult) value);
            renderLocalInfo();
            renderSyncWaiting();
        }, error -> {
            bindLoader(root, false);
            showDataError("Falha ao restaurar backup", error.getMessage());
        });
    }

    private void importB3(Uri uri, boolean replaceAll) {
        bindLoader(root, true);
        runAsync(() -> {
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("Não foi possível abrir o Excel selecionado.");
                return repository.importB3Xlsx(in, replaceAll);
            }
        }, value -> {
            bindLoader(root, false);
            renderDataResult("Importação B3", (ImportResult) value);
            renderLocalInfo();
            renderSyncWaiting();
        }, error -> {
            bindLoader(root, false);
            showDataError("Falha ao importar Excel da B3", error.getMessage());
        });
    }

    private void clearProxyCache() {
        repository.clearProxyCache();
        renderDataOpsWaiting();
        renderSyncWaiting();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cache do Proxy limpo")
                .setMessage("As próximas telas vão buscar novas respostas no Proxy. A carteira local e os backups não foram alterados.")
                .setPositiveButton("Fechar", null)
                .show();
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Não foi possível abrir o arquivo selecionado.");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) baos.write(buffer, 0, n);
            return baos.toString("UTF-8");
        }
    }

    private void renderDataResult(String title, ImportResult result) {
        dataOpsContainer.removeAllViews();
        int color = result != null && result.hasUsefulData() ? ContextCompat.getColor(requireContext(), R.color.valorae_positive) : ContextCompat.getColor(requireContext(), R.color.valorae_warning);
        dataOpsContainer.addView(UiFactory.rowCard(requireContext(), title, result == null ? "Operação finalizada." : result.readableSummary(), result == null ? "OK" : result.shortStatus(), color));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(result == null ? "Operação finalizada." : result.readableSummary())
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void showDataError(String title, String message) {
        dataOpsContainer.removeAllViews();
        dataOpsContainer.addView(UiFactory.rowCard(requireContext(), title, message == null || message.trim().isEmpty() ? "Não foi possível concluir a operação." : message, "Falha", ContextCompat.getColor(requireContext(), R.color.valorae_negative)));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message == null || message.trim().isEmpty() ? "Não foi possível concluir a operação." : message)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private String summarizeSources(JSONObject sources) {
        if (sources == null || sources.length() == 0) return "Status recebido";
        JSONArray arr = JsonUtils.getArray(sources, "sources", "items", "providers");
        if (arr.length() > 0) return arr.length() + " fontes mapeadas";
        String status = JsonUtils.getString(sources, "status", "state", "health");
        return status.isEmpty() ? "Fontes verificadas" : status;
    }

    private int estimateRoutesFromManifest(JSONObject manifest) {
        if (manifest == null) return 0;
        JSONObject endpoints = JsonUtils.getObject(manifest, "endpoints", "paths");
        return endpoints.length();
    }

    private void runGraphicsAudit() {
        graphicsAuditContainer.removeAllViews();
        graphicsAuditContainer.addView(UiFactory.rowCard(requireContext(), "Iniciando testes", "Testando renderização e gráficos...", "Aguarde", ContextCompat.getColor(requireContext(), R.color.valorae_primary)));
        runAsync(() -> {
            List<Position> pos = repository.positions();
            if (pos.isEmpty()) return "Nenhuma posição para testar";
            Position p = pos.get(0);
            return "Testando ativo: " + p.ticker + "\n\nPayload: " + JsonUtils.parseObject(repository.payload(p.ticker)).length() + " campos raiz." +
                   "\nApp Payload/Contratos: " + JsonUtils.parseObject(repository.symbol(p.ticker)).length() + " campos raiz." +
                   "\nHistórico (1Y): " + JsonUtils.parseObject(repository.assetHistory(p.ticker, "1Y")).length() + " campos raiz." +
                   "\nFundamentos/DRE: " + JsonUtils.parseObject(repository.assetStatements(p.ticker)).length() + " campos raiz.";
        }, result -> {
            graphicsAuditContainer.removeAllViews();
            graphicsAuditContainer.addView(UiFactory.rowCard(requireContext(), "Diagnóstico Completo", "A comunicação com as chaves gráficas funciona e está formatada como JSON.", "OK", ContextCompat.getColor(requireContext(), R.color.valorae_positive)));
            graphicsAuditContainer.addView(UiFactory.emptyState(requireContext(), "Detalhes do Log", (String) result));
        }, error -> {
            graphicsAuditContainer.removeAllViews();
            graphicsAuditContainer.addView(UiFactory.rowCard(requireContext(), "Falha no diagnóstico", error.getMessage(), "Erro", ContextCompat.getColor(requireContext(), R.color.valorae_negative)));
        });
    }

    private String firstText(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value.trim(); }

    private static class SettingsPayload {
        final String readyPayload, manifestPayload, sourceStatusPayload;
        SettingsPayload(String readyPayload, String manifestPayload, String sourceStatusPayload) {
            this.readyPayload = readyPayload;
            this.manifestPayload = manifestPayload;
            this.sourceStatusPayload = sourceStatusPayload;
        }
    }
}
