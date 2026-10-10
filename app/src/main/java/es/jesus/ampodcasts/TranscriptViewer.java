package es.jesus.ampodcasts;

import android.app.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import androidx.media3.common.Player;
import java.util.*;
import java.util.concurrent.ExecutorService;

final class TranscriptViewer {
    static void show(Activity activity,LibraryEntry entry,Player player,ExecutorService worker) {
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);int pad=UiStyle.dp(activity,16);box.setPadding(pad,pad,pad,pad);
        TextView note=new TextView(activity);note.setText("Texto publicado por el podcast. Sus tiempos pueden variar si el editor inserta anuncios. No se envía audio a Gemini.");note.setTextColor(0xffa6abb3);box.addView(note);
        EditText search=new EditText(activity);search.setHint("Buscar una palabra o frase");search.setSingleLine(true);search.setTextColor(0xfff2f2f2);search.setHintTextColor(0xffa6abb3);box.addView(search);
        TextView status=new TextView(activity);status.setText("Cargando transcripción…");status.setTextColor(0xffa8c7fa);box.addView(status);
        ScrollView scroll=new ScrollView(activity);LinearLayout results=new LinearLayout(activity);results.setOrientation(LinearLayout.VERTICAL);scroll.addView(results);box.addView(scroll,new LinearLayout.LayoutParams(-1,UiStyle.dp(activity,340)));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Transcripción · "+entry.episode.title).setView(box).setNegativeButton("Cerrar",null).create();dialog.show();
        List<TranscriptStore.Cue> cues=new ArrayList<>();int[] limit={60};
        Runnable[] render={null};render[0]=()->{results.removeAllViews();List<TranscriptStore.Cue> found=TranscriptStore.search(cues,search.getText().toString());status.setText(found.size()+" fragmentos"+(found.size()>limit[0]?" · Mostrando "+limit[0]:""));for(int i=0;i<Math.min(limit[0],found.size());i++){TranscriptStore.Cue cue=found.get(i);Button button=new Button(activity);button.setText(TimeFormat.display(cue.start,true)+" · "+cue.text);UiStyle.button(button);button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setOnClickListener(v->{if(player.getCurrentMediaItem()!=null && player.getCurrentMediaItem().mediaId.equals(entry.episode.id)){player.seekTo(cue.start);dialog.dismiss();}else Toast.makeText(activity,"El episodio en reproducción ha cambiado",Toast.LENGTH_SHORT).show();});results.addView(button,UiStyle.spaced(activity));}if(found.size()>limit[0]){Button more=new Button(activity);more.setText("Mostrar más");UiStyle.button(more);more.setOnClickListener(v->{limit[0]+=60;render[0].run();});results.addView(more);}};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){limit[0]=60;render[0].run();}public void afterTextChanged(Editable text){}});
        if(entry.episode.transcriptUrl.isEmpty()){status.setText("El editor no publica una transcripción con tiempos compatible.");return;}
        worker.execute(()->{try{List<TranscriptStore.Cue> loaded=TranscriptStore.load(activity,entry.episode);activity.runOnUiThread(()->{if(activity.isDestroyed() || !dialog.isShowing())return;cues.addAll(loaded);render[0].run();});}catch(Exception e){activity.runOnUiThread(()->{if(!activity.isDestroyed() && dialog.isShowing())status.setText("No se pudo cargar. Comprueba la conexión o inténtalo de nuevo.");});}});
    }
}
