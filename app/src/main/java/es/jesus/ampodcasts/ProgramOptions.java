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
        TextView playback = new TextView(activity); playback.setText("Reproducción para este podcast"); box.addView(playback);
        String[] speeds={"Usar velocidad general","0,75×","1×","1,25×","1,5×","1,75×","2×"}; float[] speedValues={0,.75f,1f,1.25f,1.5f,1.75f,2f};
        Spinner speed=new Spinner(activity); speed.setAdapter(UiStyle.choices(activity,speeds)); speed.setContentDescription("Velocidad de este podcast"); UiStyle.spinner(speed);
        String speedKey=key("programSpeed",feed); int speedIndex=0; if(repository.prefs.contains(speedKey))for(int i=1;i<speedValues.length;i++)if(speedValues[i]==repository.prefs.getFloat(speedKey,1f))speedIndex=i; speed.setSelection(speedIndex); box.addView(speed);
        Spinner silence=new Spinner(activity); silence.setAdapter(UiStyle.choices(activity,new String[]{"Silencios: usar ajuste general","Conservar silencios","Omitir silencios"})); silence.setContentDescription("Silencios de este podcast"); UiStyle.spinner(silence);
        String silenceKey=key("programSilence",feed); silence.setSelection(repository.prefs.contains(silenceKey)?repository.prefs.getBoolean(silenceKey,false)?2:1:0); box.addView(silence);
        TextView note = new TextView(activity); note.setText("Se comprueba periódicamente cuando Android permite trabajar en segundo plano. Al activar las opciones se toma la biblioteca actual como punto de partida, sin descargar todo el historial."); box.addView(note);
        TextView skips = new TextView(activity); skips.setText("Saltos de inicio y final (segundos) · 0 desactiva"); box.addView(skips);
        TextView introLabel=new TextView(activity);introLabel.setText("Omitir inicio (segundos)");box.addView(introLabel);
        EditText intro = offset(activity, repository, feed, "skipStart", "Omitir inicio (segundos)"); box.addView(intro);
        TextView outroLabel=new TextView(activity);outroLabel.setText("Omitir final (segundos)");box.addView(outroLabel);
        EditText outro = offset(activity, repository, feed, "skipEnd", "Omitir final (segundos)"); box.addView(outro);
        TextView explanation = new TextView(activity); explanation.setText("Son tiempos fijos, no un detector de anuncios. El inicio se omite solo al empezar desde cero. El final requiere duración conocida; no se aplica si los saltos abarcan todo el episodio."); box.addView(explanation);
        ScrollView scroll = new ScrollView(activity); scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Opciones del programa").setView(scroll).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long start, end; try { start = Long.parseLong(intro.getText().toString()); end = Long.parseLong(outro.getText().toString()); if (start < 0 || start > 1800 || end < 0 || end > 1800) throw new NumberFormatException(); } catch (NumberFormatException e) { intro.setError("Usa segundos entre 0 y 1800 en ambos campos"); return; }
            repository.prefs.edit().putLong(key("skipStart", feed), start).putLong(key("skipEnd", feed), end).putBoolean(key("auto", feed), downloads.isChecked()).putBoolean(key("notify", feed), alerts.isChecked()).putInt(key("limit", feed), values[limit.getSelectedItemPosition()]).apply();
            android.content.SharedPreferences.Editor playbackEdit=repository.prefs.edit();
            if(speed.getSelectedItemPosition()==0)playbackEdit.remove(speedKey);else playbackEdit.putFloat(speedKey,speedValues[speed.getSelectedItemPosition()]);
            if(silence.getSelectedItemPosition()==0)playbackEdit.remove(silenceKey);else playbackEdit.putBoolean(silenceKey,silence.getSelectedItemPosition()==2); playbackEdit.apply();
            // Initialize once; later toggles retain known IDs and never replay old alerts.
            BackgroundSync.initialize(activity, feed); BackgroundSync.schedule(activity);
            Toast.makeText(activity, "Opciones guardadas", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        })); dialog.show();
    }
    private static EditText offset(Activity activity, Repository repository, String feed, String kind, String hint) {
        EditText field=new EditText(activity); field.setHint(hint); field.setContentDescription(hint); field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); field.setSingleLine(true); field.setText(String.valueOf(repository.prefs.getLong(key(kind,feed),0))); return field;
    }
}
