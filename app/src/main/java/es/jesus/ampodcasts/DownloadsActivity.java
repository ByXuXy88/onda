package es.jesus.ampodcasts;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.widget.*;
import java.util.*;

public final class DownloadsActivity extends Activity {
    private Repository repository;
    private LinearLayout content;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private List<Repository.StoredDownload> downloads = new ArrayList<>();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); UiPreferences.apply(this); repository = new Repository(this); getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.BLACK); scroll.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(16), dp(20), dp(20)); scroll.addView(content); setContentView(scroll); refresh();
    }
    private void refresh() { worker.execute(() -> { List<Repository.StoredDownload> result = repository.storedDownloads(); runOnUiThread(() -> { if (!isDestroyed()) { downloads = result; render(); } }); }); }
    private void render() {
        content.removeAllViews(); ImageButton back = new ImageButton(this); back.setImageDrawable(new ControlIcon("back", 0xffa8c7fa, dp(24))); back.setBackgroundColor(Color.TRANSPARENT); back.setContentDescription("Volver a ajustes"); back.setOnClickListener(v -> finish()); content.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        label("Tus descargas", 28); long total = 0; for (Repository.StoredDownload d : downloads) total += d.bytes; label(downloads.size() + " descargas · " + size(total), 16);
        Button cleanup = new Button(this); cleanup.setText("Eliminar descargas escuchadas"); cleanup.setAllCaps(false); content.addView(cleanup);
        List<Repository.StoredDownload> played = new ArrayList<>(); for (Repository.StoredDownload d : downloads) if (d.played && d.status == DownloadManager.STATUS_SUCCESSFUL) played.add(d); cleanup.setEnabled(!played.isEmpty());
        cleanup.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Eliminar descargas escuchadas").setMessage("Se eliminarán " + played.size() + " archivos. Tus favoritos y programas se conservan.").setNegativeButton("Conservar", null).setPositiveButton("Eliminar", (dialog, which) -> { for (Repository.StoredDownload d : played) repository.removeStoredDownload(d.id); refresh(); }).show());
        if (downloads.isEmpty()) label("Todavía no hay episodios descargados.", 16);
        for (Repository.StoredDownload d : downloads) {
            label(d.title == null ? "Episodio" : d.title, 16); label(size(d.bytes) + (d.played ? " · Escuchado" : "") + (d.status == DownloadManager.STATUS_SUCCESSFUL ? " · Disponible" : d.status == DownloadManager.STATUS_FAILED ? " · Fallida" : " · En curso"), 13);
            ImageButton delete = new ImageButton(this); delete.setImageDrawable(new ControlIcon("close", 0xffa8c7fa, dp(24))); delete.setBackgroundColor(Color.TRANSPARENT); delete.setContentDescription("Eliminar descarga de " + d.title); delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("¿Eliminar esta descarga?").setMessage(d.title).setNegativeButton("Conservar", null).setPositiveButton("Eliminar", (dialog, which) -> { repository.removeStoredDownload(d.id); refresh(); }).show()); content.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
    }
    private void label(String text, int size) { TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(0xfff2f2f2); t.setPadding(0, dp(12), 0, dp(6)); content.addView(t); }
    static String size(long bytes) { return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000d); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
