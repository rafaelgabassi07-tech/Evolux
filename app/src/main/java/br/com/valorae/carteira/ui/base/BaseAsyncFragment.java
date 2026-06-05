package br.com.valorae.carteira.ui.base;

import android.os.*;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.concurrent.*;
import br.com.valorae.carteira.data.PortfolioRepository;

public abstract class BaseAsyncFragment extends Fragment {
    protected PortfolioRepository repository;
    private ExecutorService executor;
    private final Handler main = new Handler(Looper.getMainLooper());

    protected void initRepository() {
        if (repository == null && getContext() != null) repository = new PortfolioRepository(requireContext().getApplicationContext());
        if (executor == null || executor.isShutdown()) executor = Executors.newSingleThreadExecutor();
    }

    protected void runAsync(Task task, Success success, @Nullable Failure failure) {
        initRepository();
        executor.execute(() -> {
            try {
                Object value = task.run();
                main.post(() -> {
                    if (isAdded()) success.onSuccess(value);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (isAdded() && failure != null) failure.onFailure(e);
                });
            }
        });
    }

    protected void bindLoader(View root, boolean loading) {
        if (root == null) return;
        LinearProgressIndicator indicator = root.findViewById(br.com.valorae.carteira.R.id.loadingIndicator);
        if (indicator != null) indicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override public void onDestroyView() {
        if (executor != null) executor.shutdownNow();
        super.onDestroyView();
    }

    protected interface Task { Object run() throws Exception; }
    protected interface Success { void onSuccess(Object value); }
    protected interface Failure { void onFailure(Exception error); }
}
