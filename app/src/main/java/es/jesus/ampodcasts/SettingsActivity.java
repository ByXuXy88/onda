package es.jesus.ampodcasts;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;

public final class SettingsActivity extends Activity {
    private Repository repository;
    private LinearLayout content;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repository = new Repository(this);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        if (android.os.Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.BLACK);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(16), dp(20), dp(24)); scroll.addView(content); setContentView(scroll);
        ImageButton back = new ImageButton(this); back.setImageDrawable(new ControlIcon("back", 0xffa8c7fa, dp(24))); back.setBackgroundColor(Color.TRANSPARENT); back.setContentDescription("Volver a la biblioteca"); back.setOnClickListener(v -> finish()); content.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        title("Ajustes", 28); title("Reproducción", 18);
        text("Velocidad de reproducción", 15);
        Spinner speed = new Spinner(this); String[] speeds = {"0,75×", "1× · Normal", "1,25×", "1,5×", "1,75×", "2×"}; float[] values = {.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, speeds); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); speed.setAdapter(adapter);
        float saved = repository.prefs.getFloat("speed", 1f); int selected = 1; for (int i = 0; i < values.length; i++) if (values[i] == saved) selected = i; speed.setSelection(selected); speed.setContentDescription("Velocidad de reproducción"); content.addView(speed, new LinearLayout.LayoutParams(-1, dp(48)));
        speed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { public void onNothingSelected(AdapterView<?> p) {} public void onItemSelected(AdapterView<?> p, View v, int index, long id) { repository.prefs.edit().putFloat("speed", values[index]).apply(); } });
        option("skipSilence", "Omitir silencios", "Acorta las pausas del audio durante la reproducción.", false);
        option("resumePlayback", "Recordar dónde lo dejaste", "Reanuda cada episodio desde su última posición.", true);
        title("Descargas y biblioteca", 18);
        option("wifiOnly", "Descargar solo por Wi‑Fi", "Se aplica a las nuevas descargas. Las que ya están en curso conservan su configuración.", false);
        option("autoRefresh", "Actualizar al abrir un programa", "Busca nuevos episodios cuando abres Onda o cambias de podcast.", true);
        option("oldestFirst", "Episodios antiguos primero", "Invierte el orden del RSS para escuchar desde el principio.", false);
        title("Tu biblioteca", 18);
        action("Exportar biblioteca OPML", () -> { Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/xml").putExtra(Intent.EXTRA_TITLE, "Onda-biblioteca.opml"); startActivityForResult(intent, 43); });
        text("Guarda tus programas y sus RSS para importarlos en Onda u otra aplicación. El archivo no incluye descargas ni posiciones de escucha.", 13);
        title("Apple Podcasts", 18);
        text("Puedes buscar en su catálogo y añadir enlaces de programas desde el botón +. Para consultar tu biblioteca personal de Apple, abre su web e inicia sesión allí. La cuenta y la biblioteca de Apple no se sincronizan con Onda.", 14);
        action("Abrir mi biblioteca en Apple Podcasts", () -> { try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://podcasts.apple.com/"))); } catch (ActivityNotFoundException e) { Toast.makeText(this, "No hay un navegador disponible", Toast.LENGTH_LONG).show(); } });
        title("Onda " + BuildConfig.VERSION_NAME, 18); text("Tu biblioteca de podcasts, a tu manera. Tema negro OLED y portadas guardadas en el dispositivo.", 13);
    }
    private void option(String key, String label, String explanation, boolean fallback) {
        Switch toggle = new Switch(this); toggle.setText(label); toggle.setTextColor(0xfff2f2f2); toggle.setTextSize(16); toggle.setMinHeight(dp(48)); toggle.setChecked(repository.prefs.getBoolean(key, fallback)); content.addView(toggle);
        toggle.setOnCheckedChangeListener((button, checked) -> repository.prefs.edit().putBoolean(key, checked).apply()); text(explanation, 13);
    }
    private void action(String label, Runnable run) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setMinHeight(dp(48)); button.setOnClickListener(v -> run.run()); content.addView(button); }
    private void title(String value, int size) { TextView t = text(value, size); t.setTextColor(0xffa8c7fa); t.setTypeface(null, android.graphics.Typeface.BOLD); t.setPadding(0, dp(18), 0, dp(8)); }
    private TextView text(String value, int size) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xffa6abb3); t.setPadding(0, 0, 0, dp(10)); content.addView(t); return t; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (request != 43 || result != RESULT_OK || data == null || data.getData() == null) return;
        worker.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) { repository.exportOpml(out); runOnUiThread(() -> { if (!isDestroyed()) Toast.makeText(this, "Biblioteca exportada", Toast.LENGTH_LONG).show(); }); }
            catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) Toast.makeText(this, "No se pudo exportar la biblioteca", Toast.LENGTH_LONG).show(); }); }
        });
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
