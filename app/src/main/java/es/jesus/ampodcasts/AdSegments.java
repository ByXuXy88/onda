package es.jesus.ampodcasts;

import android.content.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Device-only results tied to the exact DownloadManager file, never to an RSS stream. */
final class AdSegments {
    static final long MAX_DURATION = 9 * 60 * 60 * 1000L;
    static final class Segment {
        final long start, end; final String label;
        Segment(long start, long end, String label) { this.start=start; this.end=end; this.label=label; }
    }
    static final class Record {
        final long downloadId, duration; final String source, model; final List<Segment> segments;
        final Set<Long> ignored; final boolean enabled; final String preparedFile; final long preparedBytes;
        Record(long downloadId, long duration, String source, String model, List<Segment> segments, Set<Long> ignored, boolean enabled) {
            this(downloadId,duration,source,model,segments,ignored,enabled,"",0);
        }
        Record(long downloadId,long duration,String source,String model,List<Segment> segments,Set<Long> ignored,boolean enabled,String preparedFile,long preparedBytes) {
            this.preparedFile=preparedFile;this.preparedBytes=preparedBytes;
            this.downloadId=downloadId; this.duration=duration; this.source=source; this.model=model; this.segments=segments; this.ignored=ignored; this.enabled=enabled;
        }
        Segment at(long position, long playingDuration) {
            if(!enabled || playingDuration<=0 || Math.abs(playingDuration-duration)>2000) return null;
            for(Segment s:segments) if(position>=s.start && position<s.end && !ignored.contains(s.start)) return s;
            return null;
        }
    }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("gemini_ad_segments", Context.MODE_PRIVATE); }
    static String key(String id) { return "ads:"+Repository.key(id); }
    static List<Segment> parseDetection(String text, long duration) throws Exception {
        if(duration<=0 || duration>MAX_DURATION || text.length()>200000) throw new IOException("Respuesta de anuncios no válida");
        JSONArray array=new JSONObject(text).getJSONArray("segments");
        if(array.length()>200) throw new IOException("Demasiados tramos detectados");
        List<Segment> result=new ArrayList<>();
        for(int i=0;i<array.length();i++) {
            JSONObject o=array.getJSONObject(i);
            if(!(o.get("start_seconds") instanceof Number) || !(o.get("end_seconds") instanceof Number)) throw new IOException("Tiempos no válidos");
            double start=o.getDouble("start_seconds"), end=o.getDouble("end_seconds");
            if(!Double.isFinite(start) || !Double.isFinite(end) || start<0 || end<=start || end*1000>duration+500) throw new IOException("Gemini devolvió tiempos fuera del episodio. Reintenta el análisis.");
            String label=o.optString("label","Publicidad").trim(); if(label.isEmpty())label="Publicidad"; if(label.length()>120)label=label.substring(0,120);
            long a=Math.round(start*1000), b=Math.min(duration,Math.round(end*1000)); if(b<=a) throw new IOException("Tramo demasiado corto");
            result.add(new Segment(a,b,label));
        }
        result.sort(Comparator.comparingLong(s->s.start)); List<Segment> merged=new ArrayList<>();
        for(Segment s:result) { if(!merged.isEmpty() && s.start<=merged.get(merged.size()-1).end) { Segment p=merged.remove(merged.size()-1); merged.add(new Segment(p.start,Math.max(p.end,s.end),p.label)); } else merged.add(s); }
        return merged;
    }
    static String encode(Record r) throws Exception {
        JSONArray segments=new JSONArray(), ignored=new JSONArray();
        for(Segment s:r.segments)segments.put(new JSONObject().put("start",s.start).put("end",s.end).put("label",s.label));
        for(long start:r.ignored)ignored.put(start);
        return new JSONObject().put("preparedFile",r.preparedFile).put("preparedBytes",r.preparedBytes).put("downloadId",r.downloadId).put("duration",r.duration).put("source",r.source).put("model",r.model).put("enabled",r.enabled).put("segments",segments).put("ignored",ignored).toString();
    }
    static Record decode(String text) {
        try { if(text.length()>200000)return null; JSONObject o=new JSONObject(text); long duration=o.getLong("duration"), download=o.getLong("downloadId");
            if(duration<=0 || duration>MAX_DURATION || download<0)return null;
            JSONArray a=o.getJSONArray("segments"), ignored=o.optJSONArray("ignored"); if(a.length()>200)return null;
            List<Segment> segments=new ArrayList<>(); Set<Long> exclusions=new HashSet<>(); long previous=-1;
            for(int i=0;i<a.length();i++){JSONObject s=a.getJSONObject(i);long start=s.getLong("start"), end=s.getLong("end"); if(start<0 || start<previous || end<=start || end>duration)return null;segments.add(new Segment(start,end,s.optString("label","Publicidad")));previous=end;}
            if(ignored!=null) { if(ignored.length()>200)return null; for(int i=0;i<ignored.length();i++)exclusions.add(ignored.getLong(i)); }
            return new Record(download,duration,o.getString("source"),o.getString("model"),segments,exclusions,o.optBoolean("enabled",false),o.optString("preparedFile",""),o.optLong("preparedBytes",0));
        } catch(Exception e){return null;}
    }
    static Record load(Context c, String id) { return decode(prefs(c).getString(key(id),"")); }
    static synchronized void save(Context c,String id,Record r) throws Exception { if(!prefs(c).edit().putString(key(id),encode(r)).commit())throw new IOException("No se pudieron guardar los tramos"); }
    static synchronized void enabled(Context c,String id,boolean value) throws Exception { Record r=load(c,id); if(r!=null)save(c,id,new Record(r.downloadId,r.duration,r.source,r.model,r.segments,r.ignored,value,r.preparedFile,r.preparedBytes)); }
    static synchronized void ignore(Context c,String id,long start) throws Exception { Record r=load(c,id); if(r!=null){Set<Long> ignored=new HashSet<>(r.ignored);ignored.add(start);save(c,id,new Record(r.downloadId,r.duration,r.source,r.model,r.segments,ignored,r.enabled,r.preparedFile,r.preparedBytes));} }
    static void remove(Context c,String id) { prefs(c).edit().remove(key(id)).apply(); }
}
