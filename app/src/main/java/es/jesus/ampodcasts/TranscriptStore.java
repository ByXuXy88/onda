package es.jesus.ampodcasts;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

/** Publisher-provided VTT, SRT and Podcast Index JSON transcripts. No paid API calls. */
final class TranscriptStore {
    static final class Cue {
        final long start, end; final String text;
        Cue(long start, long end, String text) { this.start=start; this.end=end; this.text=text; }
    }
    static final int MAX_BYTES=4_000_000, MAX_CUES=10000;
    static List<Cue> parse(String source, String type) throws Exception {
        if(source.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Transcripción demasiado grande");
        List<Cue> result=new ArrayList<>();
        if(type.equals("application/json")) {
            JSONArray segments=new JSONObject(source).getJSONArray("segments");
            if(segments.length()>MAX_CUES)throw new IOException("Demasiados fragmentos");
            for(int i=0;i<segments.length();i++){JSONObject s=segments.getJSONObject(i);double a=s.optDouble("startTime",-1), b=s.optDouble("endTime",-1);if(Double.isFinite(a)&&Double.isFinite(b))add(result,(long)(a*1000),(long)(b*1000),s.optString("body",""));}
        } else {
            String normalized=source.replace("\r","").replace("\uFEFF","");
            for(String block:normalized.split("\n[ \t]*\n")) {
                String[] lines=block.split("\n");int timing=-1;
                for(int i=0;i<lines.length;i++)if(lines[i].contains("-->")){timing=i;break;}
                if(timing<0)continue;
                String[] range=lines[timing].trim().split("\\s+-->\\s+");if(range.length!=2)continue;
                long start=time(range[0]),end=time(range[1].trim().split("\\s+")[0]);StringBuilder body=new StringBuilder();
                for(int i=timing+1;i<lines.length;i++){if(body.length()>0)body.append(' ');body.append(lines[i]);}
                String text=android.text.Html.fromHtml(body.toString(),android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
                add(result,start,end,text);
                if(result.size()>MAX_CUES)throw new IOException("Demasiados fragmentos");
            }
        }
        result.sort(Comparator.comparingLong(c->c.start));
        if(result.isEmpty())throw new IOException("No hay fragmentos con tiempos compatibles");
        return result;
    }
    private static void add(List<Cue> result,long start,long end,String text) {
        text=text.trim();if(start<0 || end<=start || end>604800000L || text.isEmpty() || text.length()>10000)return;
        result.add(new Cue(start,end,text));
    }
    static long time(String raw) {
        Matcher m=Pattern.compile("^(?:(\\d{1,3}):)?(\\d{2}):(\\d{2})[.,](\\d{3})$").matcher(raw.trim());
        if(!m.matches())return -1;long h=m.group(1)==null?0:Long.parseLong(m.group(1)),min=Long.parseLong(m.group(2)),sec=Long.parseLong(m.group(3));
        return min>=60 || sec>=60?-1:((h*60+min)*60+sec)*1000+Long.parseLong(m.group(4));
    }
    static List<Cue> search(List<Cue> cues,String query) {
        String needle=normalize(query);List<Cue> found=new ArrayList<>();
        for(Cue cue:cues)if(needle.isEmpty() || normalize(cue.text).contains(needle))found.add(cue);
        return found;
    }
    private static String normalize(String text){return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();}
    static boolean supported(String type){return type.equals("text/vtt") || type.equals("application/x-subrip") || type.equals("application/srt") || type.equals("text/srt") || type.equals("application/json");}
    static List<Cue> load(Context context,Episode episode) throws Exception {
        if(episode.transcriptUrl.isEmpty() || !supported(episode.transcriptType))throw new IOException("Este episodio no publica una transcripción con tiempos compatible");
        File dir=new File(context.getCacheDir(),"transcripts");dir.mkdirs();File cache=new File(dir,Repository.key(episode.transcriptUrl)+"."+Repository.key(episode.transcriptType));
        if(cache.isFile() && cache.length()<=MAX_BYTES)try{return parse(new String(Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8),episode.transcriptType);}catch(Exception ignored){}
        String url=Repository.normalizeFeed(episode.transcriptUrl);
        for(int redirects=0;redirects<4;redirects++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(15000);conn.setReadTimeout(15000);conn.setInstanceFollowRedirects(false);
            try {
                int status=conn.getResponseCode();
                if(status>=300 && status<=308 && status!=304){String target=conn.getHeaderField("Location");if(target==null)throw new IOException("Redirección no válida");url=Repository.normalizeFeed(new URL(new URL(url),target).toString());continue;}
                if(status!=200)throw new IOException("No se pudo obtener la transcripción");
                try(InputStream in=conn.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();out.write(buffer,0,count);if(out.size()>MAX_BYTES)throw new IOException("Transcripción demasiado grande");}byte[] bytes=out.toByteArray();List<Cue> parsed=parse(new String(bytes,StandardCharsets.UTF_8),episode.transcriptType);File tmp=File.createTempFile("transcript-",".tmp",dir);try{Files.write(tmp.toPath(),bytes);Files.move(tmp.toPath(),cache.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{tmp.delete();}return parsed;}
            }finally{conn.disconnect();}
        }
        throw new IOException("Demasiadas redirecciones");
    }
}
