package es.jesus.ampodcasts;

import android.app.*;
import android.widget.*;

final class ProgramOptions {
    static String key(String kind, String feed) { return kind + ":" + Repository.key(feed); }
    static void show(Activity activity, String feed) {
        Repository repository = new Repository(activity); LinearLayout box = new LinearLayout(activity); box.setOrientation(LinearLayout.VERTICAL); int pad = (int) (20 * activity.getResources().getDisplayMetrics().density); box.setPadding(pad, pad, pad, pad);
        Switch downloads = new Switch(activity); downloads.setText("Descargar nuevos episodios por Wi‑Fi"); downloads.setChecked(repository.prefs.getBoolean(key("auto", feed), false)); box.addView(downloads);
        Switch alerts = new Switch(activity); alerts.setText("Avisarme de nuevos episodios"); alerts.setChecked(repository.prefs.getBoolean(key("notify", feed), false)); box.addView(alerts);
        TextView label = new TextView(activity); label.setText("Máximo de descargas automáticas guardadas"); box.addView(label);
        int[] values = {1, 3, 5, 10}; Spinner limit = new Spinner(activity); ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, new String[]{"1 episodio", "3 episodios", "5 episodios", "10 episodios"}); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); limit.setAdapter(adapter); int saved = repository.prefs.getInt(key("limit", feed), 3); for (int i = 0; i < values.length; i++) if (saved == values[i]) limit.setSelection(i); box.addView(limit);
        TextView note = new TextView(activity); note.setText("Se comprueba periódicamente cuando Android permite trabajar en segundo plano. Al activar las opciones se toma la biblioteca actual como punto de partida, sin descargar todo el historial."); box.addView(note);
        new AlertDialog.Builder(activity).setTitle("Opciones del programa").setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", (d, w) -> {
            repository.prefs.edit().putBoolean(key("auto", feed), downloads.isChecked()).putBoolean(key("notify", feed), alerts.isChecked()).putInt(key("limit", feed), values[limit.getSelectedItemPosition()]).apply();
            // Initialize once; later toggles retain known IDs and never replay old alerts.
            BackgroundSync.initialize(activity, feed); BackgroundSync.schedule(activity);
            Toast.makeText(activity, "Opciones guardadas", Toast.LENGTH_SHORT).show();
        }).show();
    }
}
