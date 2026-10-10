package es.jesus.ampodcasts;

import android.content.*;
import android.media.MediaMetadataRetriever;
import android.net.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Full-file preflight. Playback uses these same private bytes, never a second ad-inserted stream. */
final class GeminiPreparation {
    static final long MAX_BYTES=512_000_000L;
    static final String ENABLED="prepareBeforePlay", WIFI="prepareWifiOnly";
    interface Progress { void update(String value); }
    interface Work { Uri run(LibraryEntry entry,Progress progress) throws Exception; void cancel(); }
    static boolean enabled(Context c){return GeminiKeyStore.prefs(c).getBoolean(ENABLED,false);}
    static boolean busy(Context c,String id){return GeminiKeyStore.prefs(c).getBoolean("preparing",false) && id.equals(GeminiKeyStore.prefs(c).getString("preparingId",""));}
    static File directory(Context c){return new File(c.getCacheDir(),"gemini_audio");}
    static File file(Context c,String name){return name.matches("[a-f0-9-]{36}\\.audio")?new File(directory(c),name):null;}
    static boolean valid(Context c,Repository repository,LibraryEntry entry,AdSegments.Record record){
        if(record==null || !record.source.equals(entry.episode.url))return false;
        if(record.preparedFile.isEmpty())return record.downloadId==repository.downloadId(entry.episode) && repository.localUri(entry.episode)!=null;
        File f=file(c,record.preparedFile);return f!=null && f.isFile() && record.preparedBytes>0 && record.preparedBytes<=MAX_BYTES && f.length()==record.preparedBytes;
    }
    static Uri readyUri(Context c,Repository r,LibraryEntry entry){AdSegments.Record record=AdSegments.load(c,entry.episode.id);if(!valid(c,r,entry,record))return null;return record.preparedFile.isEmpty()?r.localUri(entry.episode):Uri.fromFile(file(c,record.preparedFile));}
    static void checkNetwork(Context c) throws IOException {
        ConnectivityManager manager=c.getSystemService(ConnectivityManager.class);NetworkCapabilities caps=manager==null?null:manager.getNetworkCapabilities(manager.getActiveNetwork());
        if(caps==null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))throw new IOException("No hay conexión. Conecta el móvil y pulsa Play para reintentar.");
        if(GeminiKeyStore.prefs(c).getBoolean(WIFI,false) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))throw new IOException("El modo Solo wifi está activado. Puedes permitir datos móviles en los ajustes de Gemini.");
    }
    static String mime(String supplied,String source) throws IOException {
        String m=supplied==null?"":supplied.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
        if(m.equals("audio/mp3"))m="audio/mpeg";if(m.equals("audio/mp4") || m.equals("video/mp4"))m="audio/m4a";if(m.equals("audio/x-wav"))m="audio/wav";
        if(Arrays.asList("audio/mpeg","audio/m4a","audio/aac","audio/ogg","audio/opus","audio/flac","audio/wav").contains(m))return m;
        String path=Uri.parse(source).getPath();path=path==null?"":path.toLowerCase(Locale.ROOT);
        if(path.endsWith(".mp3"))return "audio/mpeg";if(path.endsWith(".m4a") || path.endsWith(".mp4"))return "audio/m4a";if(path.endsWith(".aac"))return "audio/aac";if(path.endsWith(".ogg"))return "audio/ogg";if(path.endsWith(".opus"))return "audio/opus";if(path.endsWith(".flac"))return "audio/flac";if(path.endsWith(".wav"))return "audio/wav";
        throw new IOException("No se pudo identificar un formato de audio compatible con Gemini.");
    }
    static long duration(Context c,Uri uri) throws Exception {MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(c,uri);String s=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);long value=s==null?0:Long.parseLong(s);if(value<=0 || value>AdSegments.MAX_DURATION)throw new IOException("El episodio supera 9 horas o no tiene duración disponible.");return value;}finally{m.release();}}
    static final class Analysis implements Work {
        final Context context;final Repository repository;final AtomicBoolean cancelled=new AtomicBoolean();volatile HttpURLConnection connection;volatile GeminiClient client;
        Analysis(Context c){context=c.getApplicationContext();repository=new Repository(context);}
        public void cancel(){cancelled.set(true);HttpURLConnection active=connection;if(active!=null)active.disconnect();GeminiClient current=client;if(current!=null)current.cancel();}
        void check() throws InterruptedIOException {if(cancelled.get() || Thread.currentThread().isInterrupted())throw new InterruptedIOException("Preparación cancelada");}
        public Uri run(LibraryEntry entry,Progress progress) throws Exception {
            check();String model=GeminiKeyStore.prefs(context).getString("model","");if(model.isEmpty())throw new IOException("Elige un modelo en Ajustes → Anuncios · Gemini Beta.");
            String key=GeminiKeyStore.get(context);if(key.isEmpty())throw new IOException("Añade tu clave en los ajustes de Gemini.");checkNetwork(context);
            Uri uri=repository.localUri(entry.episode);File fresh=null;boolean saved=false;String type="";long bytes,downloadId=repository.downloadId(entry.episode);
            try {
                if(uri==null){File dir=directory(context);if(!dir.isDirectory() && !dir.mkdirs())throw new IOException("No se pudo preparar el espacio temporal.");fresh=new File(dir,UUID.randomUUID()+".audio");type=fetch(entry.episode.url,fresh,progress);uri=Uri.fromFile(fresh);bytes=fresh.length();downloadId=0;}
                else {try(android.content.res.AssetFileDescriptor fd=context.getContentResolver().openAssetFileDescriptor(uri,"r")){if(fd==null)throw new IOException("No se pudo abrir el audio");bytes=fd.getLength();if(bytes<0)bytes=fd.getParcelFileDescriptor().getStatSize();}type=context.getContentResolver().getType(uri);}
                if(bytes<=0 || bytes>MAX_BYTES)throw new IOException("La beta admite audio de hasta 512 MB.");check();progress.update("Comprobando el audio antes de enviarlo a Gemini…");long duration=duration(context,uri);type=mime(type,entry.episode.url);
                final Uri upload=uri;client=new GeminiClient(key);client.guard=()->checkNetwork(context);GeminiClient.PodcastAnalysis result=client.analyzePodcast(()->context.getContentResolver().openInputStream(upload),bytes,type,duration,model,value->{check();checkNetwork(context);progress.update(value);});check();
                if(fresh==null && (downloadId!=repository.downloadId(entry.episode) || repository.localUri(entry.episode)==null))throw new IOException("La descarga cambió durante el análisis.");
                AdSegments.save(context,entry.episode.id,new AdSegments.Record(downloadId,duration,entry.episode.url,model,result.ads,new HashSet<>(),true,fresh==null?"":fresh.getName(),fresh==null?0:bytes,result.themes));saved=true;
                prune(context,fresh==null?"":fresh.getName(),repository.prefs.getString("activeEpisode",""));return uri;
            } finally {if(fresh!=null && !saved)fresh.delete();}
        }
        String fetch(String source,File destination,Progress progress) throws Exception {
            long deadline=System.nanoTime()+30L*60*1000000000L;String current=source;
            for(int redirects=0;redirects<=5;redirects++) {
                check();checkNetwork(context);URL url=new URL(current);if(!url.getProtocol().equals("https") || url.getUserInfo()!=null)throw new IOException("El audio debe usar una dirección HTTPS.");
                HttpURLConnection c=(HttpURLConnection)url.openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestProperty("Accept-Encoding","identity");
                try {
                    int code=c.getResponseCode();if(Arrays.asList(301,302,303,307,308).contains(code)){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Redirección de audio no válida");current=new URL(url,location).toString();continue;}
                    if(code!=200)throw new IOException("No se pudo obtener el audio (HTTP "+code+").");long length=c.getContentLengthLong();if(length>MAX_BYTES)throw new IOException("El audio supera 512 MB.");
                    long required=length>0?length:MAX_BYTES;if(directory(context).getUsableSpace()<required+16_000_000L)throw new IOException("No hay espacio suficiente para preparar el audio temporal.");
                    String type=c.getContentType();long total=0,last=-1;byte[] buffer=new byte[65536];
                    try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(destination)) {int n;while((n=in.read(buffer))!=-1){check();if(System.nanoTime()>deadline)throw new IOException("La preparación tardó demasiado. Puedes volver a intentarlo.");total+=n;if(total>MAX_BYTES)throw new IOException("El audio supera 512 MB.");out.write(buffer,0,n);long mb=total/1_000_000;if(mb!=last){last=mb;checkNetwork(context);progress.update("Preparando audio temporal: "+mb+" MB"+(length>0?" de "+length/1_000_000+" MB":"")+"…");}}}
                    if(total==0 || (length>=0 && total!=length))throw new IOException("El audio no se recibió completamente.");check();return type;
                } catch(InterruptedIOException e){throw e;}catch(IOException e){check();throw new IOException("No se pudo preparar el audio. Comprueba la conexión y el espacio disponible.");} finally {c.disconnect();connection=null;}
            }
            throw new IOException("El audio tiene demasiadas redirecciones.");
        }
    }
    static void prune(Context c,String keep,String activeId){File[] files=directory(c).listFiles();if(files==null)return;AdSegments.Record active=AdSegments.load(c,activeId);String protectedName=active==null?"":active.preparedFile;Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());long bytes=0;int count=0;for(File f:files)if(f.getName().equals(keep) || f.getName().equals(protectedName)){bytes+=f.length();count++;}for(File f:files)if(!f.getName().equals(keep) && !f.getName().equals(protectedName)){if(count>=3 || bytes+f.length()>MAX_BYTES)f.delete();else {bytes+=f.length();count++;}}}
    static void clear(Context c,String activeId){File[] files=directory(c).listFiles();AdSegments.Record active=AdSegments.load(c,activeId);String keep=active==null?"":active.preparedFile;if(files!=null)for(File f:files)if(!f.getName().equals(keep))f.delete();}
}
