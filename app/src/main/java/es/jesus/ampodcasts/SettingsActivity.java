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
        super.onCreate(state); UiPreferences.apply(this); repository = new Repository(this);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        if (android.os.Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.BLACK);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(16), dp(20), dp(24)); scroll.addView(content); setContentView(scroll);
        ImageButton back = new ImageButton(this); back.setImageDrawable(new ControlIcon("back", 0xffa8c7fa, dp(24))); back.setBackgroundColor(Color.TRANSPARENT); back.setContentDescription("Volver a la biblioteca"); back.setOnClickListener(v -> finish()); content.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        title("Ajustes", 28); title("Calidad de audio y vídeo", 18);
        text("Calidad original · Máxima disponible", 16);
        text("Onda no recomprime los archivos. Si el RSS indica varias versiones, elige la de mayor bitrate; la calidad final depende del archivo publicado y de los formatos que admite tu dispositivo. El vídeo aparece solo cuando el podcast lo ofrece en su RSS.", 13);
        title("Reproducción", 18);
        text("Velocidad de reproducción", 15);
        Spinner speed = new Spinner(this); String[] speeds = {"0,75×", "1× · Normal", "1,25×", "1,5×", "1,75×", "2×"}; float[] values = {.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, speeds); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); speed.setAdapter(adapter);
        float saved = repository.prefs.getFloat("speed", 1f); int selected = 1; for (int i = 0; i < values.length; i++) if (values[i] == saved) selected = i; speed.setSelection(selected); speed.setContentDescription("Velocidad de reproducción"); content.addView(speed, new LinearLayout.LayoutParams(-1, dp(48)));
        speed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { public void onNothingSelected(AdapterView<?> p) {} public void onItemSelected(AdapterView<?> p, View v, int index, long id) { repository.prefs.edit().putFloat("speed", values[index]).apply(); } });
        option("skipSilence", "Omitir silencios", "Acorta las pausas del audio durante la reproducción.", false);
        option("resumePlayback", "Recordar dónde lo dejaste", "Reanuda cada episodio desde su última posición.", true);
        action("Saltos de avance y retroceso", () -> new AlertDialog.Builder(this).setTitle("Elige el control").setItems(new String[]{"Retroceder", "Avanzar"}, (d, side) -> new AlertDialog.Builder(this).setTitle("Segundos por pulsación").setItems(new String[]{"10 segundos", "15 segundos", "30 segundos", "60 segundos"}, (dialog, index) -> repository.prefs.edit().putInt(side==0 ? "jumpBack" : "jumpForward", new int[]{10,15,30,60}[index]).apply()).show()).show());
        title("Apariencia", 18);
        action("Saltar anuncios · Gemini Beta", () -> startActivity(new Intent(this, GeminiAdsActivity.class)));
        option("dynamicColors", "Colores del sistema", "En Android 12 o posterior, adapta el acento al fondo del móvil. El fondo sigue siendo negro OLED.", true);
        title("Descargas y biblioteca", 18);
        option("wifiOnly", "Descargar solo por Wi‑Fi", "Se aplica a las nuevas descargas. Las que ya están en curso conservan su configuración.", false);
        option("autoRefresh", "Actualizar al abrir un programa", "Busca nuevos episodios cuando abres Onda o cambias de podcast.", true);
        option("oldestFirst", "Episodios antiguos primero", "Invierte el orden del RSS para escuchar desde el principio.", false);
        option("hideListened", "Ocultar episodios escuchados", "Se aplica a la lista de episodios del programa. Tus favoritos se conservan.", false);
        action("Gestionar espacio de descargas", () -> startActivity(new Intent(this, DownloadsActivity.class)));
        option("deletePlayedDownloads", "Borrar descargas ya escuchadas", "Elimina periódicamente archivos terminados; conserva el episodio activo. Configura descargas automáticas y avisos desde las opciones de cada programa.", false);
        action("Opciones de cada programa", () -> { java.util.List<Podcast> programs = repository.podcasts(); String[] labels = new String[programs.size()]; for (int i = 0; i < labels.length; i++) labels[i] = programs.get(i).title; if (labels.length == 0) Toast.makeText(this, "Añade primero un podcast", Toast.LENGTH_SHORT).show(); else new AlertDialog.Builder(this).setTitle("Elige un programa").setItems(labels, (d, w) -> ProgramOptions.show(this, programs.get(w).feed)).show(); });
        title("Vídeo y accesibilidad", 18);
        option("videoPip", "Vídeo en ventana flotante", "Al salir con el botón Inicio, mantiene el vídeo visible si tu dispositivo permite esta función.", true);
        action("Tamaño del texto", () -> new AlertDialog.Builder(this).setTitle("Tamaño del texto").setItems(new String[]{"Sistema", "Grande · +15 %", "Más grande · +30 %"}, (d, w) -> { repository.prefs.edit().putFloat("textScale", new float[]{1f, 1.15f, 1.3f}[w]).apply(); recreate(); }).show());
        text("Onda respeta el tamaño de letra del sistema. Sus controles mantienen áreas táctiles de al menos 48 dp y etiquetas para el lector de pantalla.", 13);
        title("Tu biblioteca", 18);
        action("Guardar copia de seguridad completa", () -> { Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "Onda-copia.json"); startActivityForResult(intent, 44); });
        action("Restaurar copia de seguridad", () -> new AlertDialog.Builder(this).setTitle("Restaurar biblioteca").setMessage("Sustituye programas, favoritos, cola, progreso y ajustes por los de la copia. Los archivos descargados de este dispositivo se conservan. La reproducción se detendrá al restaurar.").setNegativeButton("Cancelar", null).setPositiveButton("Elegir copia", (d, w) -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"), 45)).show());
        text("La copia incluye programas, favoritos, cola, progreso y ajustes. No contiene audio, vídeo, contraseñas ni archivos descargados.", 13);
        action("Exportar biblioteca OPML", () -> { Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/xml").putExtra(Intent.EXTRA_TITLE, "Onda-biblioteca.opml"); startActivityForResult(intent, 43); });
        text("Guarda tus programas y sus RSS para importarlos en Onda u otra aplicación. El archivo no incluye descargas ni posiciones de escucha.", 13);
        title("Apple Podcasts", 18);
        text("Puedes buscar en su catálogo y añadir enlaces de programas desde el botón +. Para consultar tu biblioteca personal de Apple, abre su web e inicia sesión allí. La cuenta y la biblioteca de Apple no se sincronizan con Onda.", 14);
        action("Abrir mi biblioteca en Apple Podcasts", () -> { try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://podcasts.apple.com/"))); } catch (ActivityNotFoundException e) { Toast.makeText(this, "No hay un navegador disponible", Toast.LENGTH_LONG).show(); } });
        title("Onda " + BuildConfig.VERSION_NAME, 18); text("Tu biblioteca de podcasts, a tu manera. Tema negro OLED y portadas guardadas en el dispositivo.", 13);
    }
    private void option(String key, String label, String explanation, boolean fallback) {
        Switch toggle = new Switch(this); toggle.setText(label); toggle.setTextColor(0xfff2f2f2); toggle.setTextSize(16); toggle.setMinHeight(dp(48)); toggle.setChecked(repository.prefs.getBoolean(key, fallback)); content.addView(toggle);
        toggle.setOnCheckedChangeListener((button, checked) -> { repository.prefs.edit().putBoolean(key, checked).apply(); BackgroundSync.schedule(this); }); text(explanation, 13);
    }
    private void action(String label, Runnable run) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setMinHeight(dp(48)); button.setOnClickListener(v -> run.run()); content.addView(button); }
    private void title(String value, int size) { TextView t = text(value, size); t.setTextColor(0xffa8c7fa); t.setTypeface(null, android.graphics.Typeface.BOLD); t.setPadding(0, dp(18), 0, dp(8)); }
    private TextView text(String value, int size) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xffa6abb3); t.setPadding(0, 0, 0, dp(10)); content.addView(t); return t; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if ((request != 43 && request != 44 && request != 45) || result != RESULT_OK || data == null || data.getData() == null) return;
        worker.execute(() -> {
            try {
                if (request == 45) {
                    java.util.Map<String, Object> values; try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) { values = BackupStore.validate(in); }
                    runOnUiThread(() -> { if (isDestroyed()) return;
                        new AlertDialog.Builder(this).setTitle("Copia comprobada").setMessage("¿Sustituir la biblioteca actual por esta copia?").setNegativeButton("Cancelar", null).setPositiveButton("Restaurar", (d, w) -> restoreBackup(values)).show(); });
                } else { try (OutputStream out = getContentResolver().openOutputStream(data.getData())) { if (request == 44) BackupStore.export(this, out); else repository.exportOpml(out); } runOnUiThread(() -> { if (!isDestroyed()) Toast.makeText(this, "Biblioteca guardada", Toast.LENGTH_LONG).show(); }); }
            } catch (Exception e) { showError(request == 45 ? "El archivo no es una copia válida de Onda" : "No se pudo guardar la biblioteca"); }
        });
    }
    private void restoreBackup(java.util.Map<String, Object> values) {
        com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController> future = new androidx.media3.session.MediaController.Builder(this, new androidx.media3.session.SessionToken(this, new ComponentName(this, PlaybackService.class))).buildAsync();
        future.addListener(() -> {
            try {
                androidx.media3.session.MediaController controller = future.get();
                com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> cleared = controller.sendCustomCommand(new androidx.media3.session.SessionCommand(PlaybackService.PREPARE_RESTORE, Bundle.EMPTY), Bundle.EMPTY);
                cleared.addListener(() -> {
                    try { if (cleared.get().resultCode != androidx.media3.session.SessionResult.RESULT_SUCCESS) throw new java.io.IOException();
                        worker.execute(() -> { try { BackupStore.restore(this, values); runOnUiThread(() -> { androidx.media3.session.MediaController.releaseFuture(future); if (!isDestroyed()) { Toast.makeText(this, "Copia restaurada", Toast.LENGTH_LONG).show(); startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)); finish(); } }); } catch (Exception e) { getMainExecutor().execute(() -> androidx.media3.session.MediaController.releaseFuture(future)); showError("No se pudo restaurar la copia"); } });
                    } catch (Exception e) { androidx.media3.session.MediaController.releaseFuture(future); showError("No se pudo detener el reproductor. Inténtalo de nuevo."); }
                }, getMainExecutor());
            } catch (Exception e) { androidx.media3.session.MediaController.releaseFuture(future); showError("No se pudo conectar al reproductor. Inténtalo de nuevo."); }
        }, getMainExecutor());
    }
    private void showError(String message) { runOnUiThread(() -> { if (!isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }); }

    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
