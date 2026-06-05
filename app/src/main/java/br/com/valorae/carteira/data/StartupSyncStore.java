package br.com.valorae.carteira.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import br.com.valorae.carteira.model.ProxyAuditItem;

public class StartupSyncStore {
    private static final String PREFS = "valorae_startup_sync";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_FINISHED_AT = "finished_at";
    private static final String KEY_OK = "ok_count";
    private static final String KEY_TOTAL = "total_count";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final long MIN_INTERVAL_MS = 3L * 60L * 1000L;
    private final SharedPreferences prefs;

    public StartupSyncStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean shouldRunOnAppOpen() {
        long now = System.currentTimeMillis();
        long started = prefs.getLong(KEY_STARTED_AT, 0L);
        if (prefs.getBoolean(KEY_RUNNING, false) && started > 0L && now - started < MIN_INTERVAL_MS) return false;
        long finished = prefs.getLong(KEY_FINISHED_AT, 0L);
        long last = Math.max(started, finished);
        return last <= 0L || now - last >= MIN_INTERVAL_MS;
    }

    public void markStarted() {
        prefs.edit()
                .putBoolean(KEY_RUNNING, true)
                .putLong(KEY_STARTED_AT, System.currentTimeMillis())
                .putString(KEY_MESSAGE, "Sincronização automática iniciada ao abrir o app.")
                .remove(KEY_LAST_ERROR)
                .apply();
    }

    public void markFinished(List<ProxyAuditItem> items) {
        int ok = 0;
        int total = items == null ? 0 : items.size();
        if (items != null) for (ProxyAuditItem item : items) if (item.ok) ok++;
        prefs.edit()
                .putBoolean(KEY_RUNNING, false)
                .putLong(KEY_FINISHED_AT, System.currentTimeMillis())
                .putInt(KEY_OK, ok)
                .putInt(KEY_TOTAL, total)
                .putString(KEY_MESSAGE, ok + "/" + total + " integrações atualizadas automaticamente na abertura.")
                .remove(KEY_LAST_ERROR)
                .apply();
    }

    public void markFailed(Exception error) {
        String message = error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? "Falha sem mensagem detalhada."
                : error.getMessage();
        prefs.edit()
                .putBoolean(KEY_RUNNING, false)
                .putLong(KEY_FINISHED_AT, System.currentTimeMillis())
                .putString(KEY_LAST_ERROR, message)
                .putString(KEY_MESSAGE, "Sincronização automática não concluiu; dados em cache foram preservados.")
                .apply();
    }

    public String readableStatus() {
        if (prefs.getBoolean(KEY_RUNNING, false)) return "Sincronização automática em andamento agora.";
        long finished = prefs.getLong(KEY_FINISHED_AT, 0L);
        if (finished <= 0L) return "A consulta automática será executada na próxima abertura do app.";
        String msg = prefs.getString(KEY_MESSAGE, "Sincronização automática executada.");
        String error = prefs.getString(KEY_LAST_ERROR, "");
        String time = format(finished);
        if (error != null && !error.trim().isEmpty()) return msg + "\nÚltima tentativa: " + time + "\nDetalhe: " + error;
        return msg + "\nÚltima execução: " + time;
    }

    public String compactStatus() {
        if (prefs.getBoolean(KEY_RUNNING, false)) return "Em andamento";
        int ok = prefs.getInt(KEY_OK, 0);
        int total = prefs.getInt(KEY_TOTAL, 0);
        if (total <= 0) return "Pronta";
        return ok + "/" + total;
    }

    private String format(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}
