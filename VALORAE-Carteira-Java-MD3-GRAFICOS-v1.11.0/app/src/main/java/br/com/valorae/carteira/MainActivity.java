package br.com.valorae.carteira;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import br.com.valorae.carteira.data.PortfolioRepository;
import br.com.valorae.carteira.data.StartupSyncStore;
import br.com.valorae.carteira.model.ProxyAuditItem;
import br.com.valorae.carteira.ui.analysis.AnalysisFragment;
import br.com.valorae.carteira.ui.home.HomeFragment;
import br.com.valorae.carteira.ui.market.MarketFragment;
import br.com.valorae.carteira.ui.portfolio.PortfolioFragment;
import br.com.valorae.carteira.ui.settings.SettingsFragment;

public class MainActivity extends AppCompatActivity {
    private MaterialToolbar topAppBar;
    private ExecutorService startupExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        topAppBar = findViewById(R.id.topAppBar);
        BottomNavigationView nav = findViewById(R.id.bottomNavigation);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return open("Visão Geral", new HomeFragment());
            if (id == R.id.nav_portfolio) return open("Carteira", new PortfolioFragment());
            if (id == R.id.nav_analysis) return open("Análise", new AnalysisFragment());
            if (id == R.id.nav_market) return open("Mercado", new MarketFragment());
            if (id == R.id.nav_settings) return open("Mais", new SettingsFragment());
            return false;
        });
        if (savedInstanceState == null) nav.setSelectedItemId(R.id.nav_home);
    }

    private boolean open(String title, Fragment fragment) {
        topAppBar.setTitle(title);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, fragment).commit();
        return true;
    }

    private void runAutomaticProxySyncOnOpen() {
        StartupSyncStore syncStore = new StartupSyncStore(this);
        if (!syncStore.shouldRunOnAppOpen()) {
            topAppBar.setSubtitle(syncStore.compactStatus().equals("Pronta") ? "Proxy pronto para sincronizar" : "Proxy: " + syncStore.compactStatus());
            return;
        }
        startupExecutor = Executors.newSingleThreadExecutor();
        topAppBar.setSubtitle("Consultando Proxy automaticamente…");
        syncStore.markStarted();
        startupExecutor.execute(() -> {
            PortfolioRepository repository = new PortfolioRepository(getApplicationContext());
            try {
                List<ProxyAuditItem> items = repository.syncOnAppOpen();
                syncStore.markFinished(items);
                int ok = 0;
                for (ProxyAuditItem item : items) if (item.ok) ok++;
                final int okCount = ok;
                final int totalCount = items.size();
                final String subtitle = okCount == totalCount ? "Proxy atualizado automaticamente" : "Proxy atualizado parcialmente: " + okCount + "/" + totalCount;
                mainHandler.post(() -> {
                    topAppBar.setSubtitle(subtitle);
                    Toast.makeText(this, "VALORAE Proxy: " + okCount + "/" + totalCount + " informações atualizadas", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                syncStore.markFailed(e);
                mainHandler.post(() -> {
                    topAppBar.setSubtitle("Proxy offline • cache local preservado");
                    Toast.makeText(this, "Proxy não respondeu agora; usando cache/local", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        // Revalida automaticamente quando o usuário volta ao app.
        // StartupSyncStore aplica o intervalo mínimo para evitar chamadas repetidas ao Proxy.
        runAutomaticProxySyncOnOpen();
    }

    @Override protected void onDestroy() {
        if (startupExecutor != null) startupExecutor.shutdownNow();
        super.onDestroy();
    }
}
