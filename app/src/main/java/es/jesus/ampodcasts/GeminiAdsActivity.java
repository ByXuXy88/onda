package es.jesus.ampodcasts;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in beta. No network request or audio upload is triggered by opening this screen. */
public final class GeminiAdsActivity extends Activity {
    static final String WARNING="Beta experimental: Gemini no es infalible. Puede saltarse parte de la conversación, no detectar anuncios o calcular mal sus tiempos. Puedes desactivar los saltos y deshacer el último salto desde el reproductor.";
    private Repository repository; private LinearLayout content; private TextView status;
    private String episodeId="",message=""; private boolean busy, analysis;
    private volatile int generation; private final ThreadLocal<Integer> operation=new ThreadLocal<>();
    private volatile GeminiClient client; private volatile Future<?> task;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state){super.onCreate(state);UiPreferences.apply(this);repository=new Repository(this);episodeId=getIntent().getStringExtra("episodeId");if(episodeId==null)episodeId="";
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.BLACK);scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(16),dp(20),dp(24));scroll.addView(content);setContentView(scroll);render();
    }
    private void render(){
        content.removeAllViews();Button back=button("Volver",this::finish);back.setEnabled(!busy);text("Anuncios · Gemini Beta",25,0xffa8c7fa);text(WARNING,15,0xfff2f2f2);
        text("Solo analiza audio descargado. El envío a Google y el consumo de tu API requieren tu confirmación en cada análisis. Los tramos quedan guardados en este móvil; no se vuelve a analizar al reproducir.",14,0xffa6abb3);
        text(GeminiKeyStore.has(this)?"Clave API guardada y cifrada en este móvil":"Aún no has añadido una clave API",15,0xfff2f2f2);
        button(GeminiKeyStore.has(this)?"Cambiar clave API":"Añadir clave API",this::keyDialog).setEnabled(!busy);
        button("Eliminar clave API",()->new AlertDialog.Builder(this).setTitle("Eliminar clave API").setMessage("Los tramos ya analizados seguirán funcionando sin la clave.").setNegativeButton("Conservar",null).setPositiveButton("Eliminar",(d,w)->{GeminiKeyStore.remove(this);render();}).show()).setEnabled(!busy && GeminiKeyStore.has(this));
        text("La clave se cifra con Android Keystore y no se incluye en la copia de seguridad. Se envía únicamente a la API oficial de Google para autenticar tus solicitudes. La cuota y los costes dependen de tu cuenta y del modelo.",13,0xffa6abb3);
        String model=GeminiKeyStore.prefs(this).getString("model","");text("Modelo: "+(model.isEmpty()?"elige un modelo compatible con audio":model),14,0xfff2f2f2);
        button("Elegir modelo de Gemini",this::chooseModel).setEnabled(!busy && GeminiKeyStore.has(this));button("Escribir identificador de modelo",this::modelDialog).setEnabled(!busy);
        LibraryEntry entry=repository.entry(episodeId);
        if(entry==null){button("Elegir un episodio descargado",this::chooseEpisode).setEnabled(!busy);text("También puedes abrir esta pantalla desde el reproductor o el menú de un episodio.",13,0xffa6abb3);}
        else {
            text(entry.episode.title,20,0xfff2f2f2);button("Cambiar episodio",this::chooseEpisode).setEnabled(!busy);
            AdSegments.Record record=AdSegments.load(this,episodeId);boolean valid=record!=null && record.downloadId==repository.downloadId(entry.episode) && record.source.equals(entry.episode.url) && repository.localUri(entry.episode)!=null;
            if(record!=null){text(valid?record.segments.size()+" tramos detectados · "+record.model:"El análisis pertenece a una descarga anterior. Vuelve a analizar este archivo.",14,0xffa6abb3);
                Switch automatic=new Switch(this);automatic.setText("Saltar automáticamente los tramos detectados");automatic.setTextColor(0xfff2f2f2);automatic.setMinHeight(dp(48));automatic.setChecked(valid && record.enabled);automatic.setEnabled(valid && !busy);content.addView(automatic);automatic.setOnCheckedChangeListener((b,value)->{try{AdSegments.enabled(this,episodeId,value);}catch(Exception e){error("No se pudo cambiar el ajuste");render();}});
                for(AdSegments.Segment s:record.segments)text(TimeFormat.display(s.start,true)+" — "+TimeFormat.display(s.end,true)+" · "+s.label+(record.ignored.contains(s.start)?" · Conservado tras deshacer":""),14,0xfff2f2f2);
                button("Eliminar análisis de este episodio",()->new AlertDialog.Builder(this).setTitle("Eliminar análisis").setMessage("Se conservará el audio descargado.").setNegativeButton("Conservar",null).setPositiveButton("Eliminar",(d,w)->{AdSegments.remove(this,episodeId);render();}).show()).setEnabled(!busy);
            }
            button(record==null?"Analizar y activar saltos automáticos":"Volver a analizar y activar saltos",()->prepare(entry)).setEnabled(!busy && GeminiKeyStore.has(this) && !model.isEmpty());
        }
        status=text(message,14,0xffa8c7fa);if(busy)button("Cancelar",this::cancel);
    }
    private void keyDialog(){EditText input=new EditText(this);input.setHint("Clave API de Gemini");input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);input.setSaveEnabled(false);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Tu clave API de Gemini").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnShowListener(v->{d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{GeminiKeyStore.save(this,input.getText().toString());input.setText("");d.dismiss();render();}catch(Exception e){input.setError(GeminiKeyStore.saveError(e));}});});d.setOnDismissListener(v->input.setText(""));d.show();
    }
    private void modelDialog(){EditText input=new EditText(this);input.setHint("gemini-…");input.setSingleLine(true);input.setText(GeminiKeyStore.prefs(this).getString("model",""));AlertDialog d=new AlertDialog.Builder(this).setTitle("Modelo con entrada de audio").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{String model=input.getText().toString().trim().replaceFirst("^models/","");if(!model.matches("gemini-[A-Za-z0-9._-]+")){input.setError("Identificador de modelo no válido");return;}GeminiKeyStore.prefs(this).edit().putString("model",model).apply();d.dismiss();render();}));d.show();}
    private void chooseModel(){start(false,"Consultando los modelos disponibles…");task=submit(()->{try{client=new GeminiClient(GeminiKeyStore.get(this));List<String> models=client.models();post(()->{done("");if(models.isEmpty()){error("No hay modelos disponibles para esta clave");return;}new AlertDialog.Builder(this).setTitle("Elige un modelo que admita audio").setItems(models.toArray(new String[0]),(d,w)->{GeminiKeyStore.prefs(this).edit().putString("model",models.get(w)).apply();render();}).setNegativeButton("Cerrar",null).show();});}catch(Exception e){failed(e);}});}
    private void chooseEpisode(){Map<String,LibraryEntry> entries=new LinkedHashMap<>();for(LibraryEntry e:repository.remembered())if(repository.localUri(e.episode)!=null)entries.put(e.episode.id,e);for(Podcast p:repository.podcasts())for(Episode e:new Repository(this,p.feed).cached())if(repository.localUri(e)!=null)entries.put(e.id,new LibraryEntry(e,p));List<LibraryEntry> list=new ArrayList<>(entries.values());if(list.isEmpty()){error("Descarga primero un episodio de audio");return;}String[] names=new String[list.size()];for(int i=0;i<names.length;i++)names[i]=list.get(i).episode.title;new AlertDialog.Builder(this).setTitle("Episodio descargado").setItems(names,(d,w)->{LibraryEntry e=list.get(w);repository.remember(e);episodeId=e.episode.id;message="";render();}).setNegativeButton("Cancelar",null).show();}
    private static final class Audio { final Uri uri;final long bytes,duration,downloadId;final String mime;Audio(Uri u,long b,long d,long id,String m){uri=u;bytes=b;duration=d;downloadId=id;mime=m;} }
    private Audio audio(LibraryEntry entry) throws Exception {
        Uri uri=repository.localUri(entry.episode);if(uri==null)throw new IOException("Descarga completamente el episodio antes de analizarlo");
        if(entry.episode.url.equals(entry.episode.videoUrl))throw new IOException("Esta beta solo analiza episodios de audio");
        long bytes;try(android.content.res.AssetFileDescriptor file=getContentResolver().openAssetFileDescriptor(uri,"r")){if(file==null)throw new IOException("No se pudo abrir la descarga");bytes=file.getLength();if(bytes<0)bytes=file.getParcelFileDescriptor().getStatSize();}
        MediaMetadataRetriever metadata=new MediaMetadataRetriever();long duration;
        try{metadata.setDataSource(this,uri);String value=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);duration=value==null?0:Long.parseLong(value);}finally{metadata.release();}
        if(bytes<=0 || bytes>512_000_000 || duration<=0 || duration>AdSegments.MAX_DURATION)throw new IOException("La beta admite audio de hasta 512 MB y 9 horas, con duración conocida");
        String mime=getContentResolver().getType(uri);String path=Uri.parse(entry.episode.url).getPath();path=path==null?"":path.toLowerCase(Locale.ROOT);
        if(mime==null || !mime.startsWith("audio/")){if(path.endsWith(".mp3"))mime="audio/mpeg";else if(path.endsWith(".m4a") || path.endsWith(".mp4"))mime="audio/m4a";else if(path.endsWith(".aac"))mime="audio/aac";else if(path.endsWith(".ogg"))mime="audio/ogg";else if(path.endsWith(".opus"))mime="audio/opus";else if(path.endsWith(".flac"))mime="audio/flac";else if(path.endsWith(".wav"))mime="audio/wav";else throw new IOException("No se pudo identificar el formato de audio de esta descarga");}
        if(mime.equals("audio/mp4"))mime="audio/m4a";if(mime.equals("audio/x-wav"))mime="audio/wav";
        return new Audio(uri,bytes,duration,repository.downloadId(entry.episode),mime);
    }
    private void prepare(LibraryEntry entry){start(false,"Comprobando el archivo descargado…");task=submit(()->{try{Audio audio=audio(entry);post(()->{done("");String model=GeminiKeyStore.prefs(this).getString("model","");new AlertDialog.Builder(this).setTitle("Analizar con Gemini y activar saltos").setMessage(entry.episode.title+"\n\n"+DownloadsActivity.size(audio.bytes)+" · "+TimeFormat.display(audio.duration,true)+"\nModelo: "+model+"\n\nSe enviará este audio completo a Google usando tu clave API. Puede consumir datos móviles, cuota y generar cargos en tu cuenta. Onda intentará borrar el archivo temporal de Gemini al terminar.\n\n"+WARNING+"\n\nAl completar el análisis se activarán automáticamente los saltos de este episodio. Mantén esta pantalla abierta durante el análisis.").setNegativeButton("Cancelar",null).setPositiveButton("Analizar y activar",(d,w)->analyze(entry,audio,model)).show();});}catch(Exception e){failed(e);}});}
    private void analyze(LibraryEntry entry,Audio audio,String model){start(true,"Iniciando análisis…");task=submit(()->{try{client=new GeminiClient(GeminiKeyStore.get(this));List<AdSegments.Segment> segments=client.analyze(()->getContentResolver().openInputStream(audio.uri),audio.bytes,audio.mime,audio.duration,model,value->post(()->{message=value;if(status!=null)status.setText(value);}));client.check();
            if(repository.downloadId(entry.episode)!=audio.downloadId || repository.localUri(entry.episode)==null)throw new IOException("La descarga cambió durante el análisis. No se guardaron los tramos.");
            synchronized(this){if(operation.get()!=generation)throw new InterruptedIOException("Cancelado");client.check();AdSegments.save(this,entry.episode.id,new AdSegments.Record(audio.downloadId,audio.duration,entry.episode.url,model,segments,new HashSet<>(),true));}
            post(()->done(segments.isEmpty()?"Análisis completo: Gemini no detectó anuncios.":"Análisis completo: "+segments.size()+" tramos. Saltos automáticos activados."));
        }catch(Exception e){failed(e);}});}
    private Future<?> submit(Runnable runnable){int expected=generation;return worker.submit(()->{if(expected!=generation)return;operation.set(expected);try{runnable.run();}finally{operation.remove();}});}
    private void start(boolean analyzing,String value){generation++;busy=true;analysis=analyzing;message=value;if(analyzing)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);render();}
    private void done(String value){busy=false;analysis=false;client=null;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);message=value;render();}
    private void failed(Exception e){boolean cancelled=e instanceof InterruptedIOException || e instanceof InterruptedException || Thread.currentThread().isInterrupted();String value=cancelled?"Análisis cancelado. No se activaron resultados nuevos.":e instanceof IOException?e.getMessage():"No se pudo completar el análisis. Comprueba el modelo, la clave y la conexión.";post(()->done(value));}
    private synchronized void cancel(){generation++;GeminiClient current=client;if(current!=null)current.cancel();Future<?> currentTask=task;if(currentTask!=null)currentTask.cancel(true);done("Análisis cancelado. Las solicitudes ya enviadas pueden haber consumido cuota.");}
    private void post(Runnable r){Integer captured=operation.get();int expected=captured==null?generation:captured;handler.post(()->{if(expected==generation && !isFinishing() && !isDestroyed())r.run();});}
    private void error(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(12),0,dp(8));content.addView(v);return v;}
    private Button button(String value,Runnable action){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());content.addView(b);return b;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onBackPressed(){if(!busy){super.onBackPressed();return;}new AlertDialog.Builder(this).setTitle("Análisis en curso").setMessage("¿Cancelar y salir? Las solicitudes ya enviadas pueden haber consumido cuota.").setNegativeButton("Seguir",null).setPositiveButton("Cancelar y salir",(d,w)->{cancel();finish();}).show();}
    @Override protected void onDestroy(){generation++;GeminiClient current=client;if(current!=null)current.cancel();Future<?> currentTask=task;if(currentTask!=null)currentTask.cancel(true);handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
