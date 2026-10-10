package es.jesus.ampodcasts;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Generated navigation belongs to the same audio and atomic result as the ad intervals. */
final class GeminiTopics {
    static final class Part {
        final long start,end; final String title,summary; final List<Part> children;
        Part(long start,long end,String title,String summary,List<Part> children){this.start=start;this.end=end;this.title=title;this.summary=summary;this.children=Collections.unmodifiableList(children);}
    }
    static final class Content {
        final List<Part> chapters; final List<String> topics;
        Content(List<Part> chapters,List<String> topics){this.chapters=Collections.unmodifiableList(chapters);this.topics=Collections.unmodifiableList(topics);}
    }
    static boolean bound(androidx.media3.common.MediaItem item,android.net.Uri uri){return uri!=null && item.mediaMetadata.extras!=null && Repository.key(uri.toString()).equals(item.mediaMetadata.extras.getString("audioBinding",""));}
    static JSONObject schema() throws Exception {
        JSONObject childFields=new JSONObject().put("start_seconds",type("NUMBER")).put("end_seconds",type("NUMBER")).put("title",type("STRING"));
        JSONObject child=object(childFields,"start_seconds","end_seconds","title");
        JSONObject fields=new JSONObject(childFields.toString()).put("summary",type("STRING")).put("subtopics",array(child));
        return array(object(fields,"start_seconds","end_seconds","title","summary","subtopics"));
    }
    static JSONObject type(String name) throws Exception{return new JSONObject().put("type",name);}
    static JSONObject array(JSONObject items) throws Exception{return type("ARRAY").put("items",items);}
    static JSONObject object(JSONObject fields,String... required) throws Exception{return type("OBJECT").put("properties",fields).put("required",new JSONArray(Arrays.asList(required)));}
    static Content parse(JSONObject object,long duration,List<AdSegments.Segment> ads) throws Exception {
        JSONArray chapters=object.getJSONArray("chapters"),tags=object.getJSONArray("topics");
        if(chapters.length()>60 || tags.length()>8)throw invalid();
        List<Part> list=new ArrayList<>();int children=0;
        for(int i=0;i<chapters.length();i++){
            JSONObject c=chapters.getJSONObject(i);long start=time(c,"start_seconds",duration),end=time(c,"end_seconds",duration);
            if(end<=start)throw invalid();JSONArray sub=c.getJSONArray("subtopics");if(sub.length()>6 || (children+=sub.length())>120)throw invalid();List<Part> parts=new ArrayList<>();
            for(int j=0;j<sub.length();j++){JSONObject p=sub.getJSONObject(j);long a=time(p,"start_seconds",duration),b=time(p,"end_seconds",duration);if(a<start || b>end || b<=a)throw invalid();parts.add(new Part(a,b,text(p,"title",100),"",new ArrayList<>()));}
            ordered(parts);boolean onlyAd=false;for(AdSegments.Segment ad:ads)if(start>=ad.start && end<=ad.end)onlyAd=true;
            if(!onlyAd)list.add(new Part(start,end,text(c,"title",100),text(c,"summary",240),parts));
        }
        ordered(list);LinkedHashSet<String> topics=new LinkedHashSet<>();
        for(int i=0;i<tags.length();i++){if(!(tags.get(i) instanceof String))throw invalid();String topic=clean(tags.getString(i),50);if(!topic.isEmpty())topics.add(topic);}
        return new Content(list,new ArrayList<>(topics));
    }
    static long time(JSONObject o,String key,long duration) throws Exception {if(!(o.get(key) instanceof Number))throw invalid();double seconds=o.getDouble(key);if(!Double.isFinite(seconds) || seconds<0 || seconds*1000>duration+500)throw invalid();return Math.min(duration,Math.round(seconds*1000));}
    private static String text(JSONObject o,String key,int max) throws Exception {if(!(o.get(key) instanceof String))throw invalid();String s=clean(o.getString(key),max);if(s.isEmpty())throw invalid();return s;}
    static String clean(String s,int max){s=s.replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();return s.length()>max?s.substring(0,max):s;}
    private static void ordered(List<Part> parts) throws IOException {parts.sort(Comparator.comparingLong(p->p.start));long end=-1;for(Part p:parts){if(p.start<end)throw invalid();end=p.end;}}
    private static IOException invalid(){return new IOException("Gemini devolvió temas o tiempos no válidos. No se guardaron resultados parciales.");}
    static JSONObject encode(Content content) throws Exception {
        JSONArray chapters=new JSONArray();for(Part c:content.chapters){JSONArray sub=new JSONArray();for(Part p:c.children)sub.put(part(p));chapters.put(part(c).put("summary",c.summary).put("subtopics",sub));}
        return new JSONObject().put("chapters",chapters).put("topics",new JSONArray(content.topics));
    }
    private static JSONObject part(Part p) throws Exception{return new JSONObject().put("start_seconds",p.start/1000.0).put("end_seconds",p.end/1000.0).put("title",p.title);}
}
