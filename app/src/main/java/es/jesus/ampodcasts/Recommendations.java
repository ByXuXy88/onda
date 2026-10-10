package es.jesus.ampodcasts;

import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.*;
import java.text.Normalizer;

/** Opt-in local interest history; only explicit catalogue searches leave the device. */
final class Recommendations {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("recommendations",Context.MODE_PRIVATE);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static long revision(Context c){return prefs(c).getLong("revision",0);}
    static synchronized void enabled(Context c,boolean value){prefs(c).edit().putBoolean("enabled",value).putLong("revision",revision(c)+1).commit();}
    static synchronized void clear(Context c){prefs(c).edit().remove("history").putLong("revision",revision(c)+1).commit();}
    static final class Heard {
        final String id,program,feed; final List<String> topics;
        Heard(String id,String program,String feed,List<String> topics){this.id=id;this.program=program;this.feed=feed;this.topics=topics;}
    }
    static List<Heard> history(Context c){return decode(prefs(c).getString("history","[]"));}
    static List<Heard> decode(String text){List<Heard> rows=new ArrayList<>();try{if(text.length()>200000)return rows;JSONArray a=new JSONArray(text);if(a.length()>100)return rows;for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);JSONArray tags=o.getJSONArray("topics");if(tags.length()>8)return new ArrayList<>();List<String> topics=new ArrayList<>();for(int j=0;j<tags.length();j++)topics.add(GeminiTopics.clean(tags.getString(j),50));rows.add(new Heard(o.getString("id"),GeminiTopics.clean(o.getString("program"),120),o.getString("feed"),topics));}}catch(Exception e){rows.clear();}return rows;}
    static synchronized void record(Context c,LibraryEntry entry,GeminiTopics.Content themes){if(!enabled(c) || themes==null || themes.topics.isEmpty())return;List<Heard> rows=history(c);rows.removeIf(r->r.id.equals(entry.episode.id));rows.add(0,new Heard(entry.episode.id,entry.podcast.title,entry.podcast.feed,themes.topics));if(rows.size()>100)rows=new ArrayList<>(rows.subList(0,100));try{JSONArray a=new JSONArray();for(Heard r:rows)a.put(new JSONObject().put("id",r.id).put("program",r.program).put("feed",r.feed).put("topics",new JSONArray(r.topics)));prefs(c).edit().putString("history",a.toString()).commit();}catch(Exception ignored){}}
    static String normalized(String text){return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]"," ").replaceAll("\\s+"," ").trim();}
    static final class Interest {final String topic,program;int score;Interest(String topic,String program,int score){this.topic=topic;this.program=program;this.score=score;}}
    static List<Interest> rank(List<Heard> rows,java.util.function.Predicate<String> favorite){Map<String,Interest> scores=new LinkedHashMap<>();for(Heard row:rows){Set<String> used=new HashSet<>();for(String tag:row.topics){String key=normalized(tag);if(key.length()<3 || Arrays.asList("publicidad","anuncios","introduccion","despedida","varios").contains(key) || !used.add(key))continue;Interest interest=scores.computeIfAbsent(key,k->new Interest(tag,row.program,0));interest.score+=favorite.test(row.id)?3:1;}}List<Interest> result=new ArrayList<>(scores.values());result.sort((a,b)->Integer.compare(b.score,a.score));return new ArrayList<>(result.subList(0,Math.min(3,result.size())));}
    static List<Interest> interests(Context c){Repository r=new Repository(c);return rank(history(c),r::favorite);}
    static final class Suggestion {final Podcast podcast;final Interest reason;Suggestion(Podcast podcast,Interest reason){this.podcast=podcast;this.reason=reason;}}
    interface Search {List<Podcast> find(String topic) throws Exception;}
    static List<Suggestion> search(List<Interest> topics,Set<String> followed,Search search) throws Exception {List<Suggestion> results=new ArrayList<>();Set<String> seen=new HashSet<>(followed);for(Interest topic:topics){if(Thread.currentThread().isInterrupted())throw new InterruptedException();int added=0;for(Podcast p:search.find(topic.topic)){if(seen.add(p.feed)){results.add(new Suggestion(p,topic));if(++added>=4)break;}}}return results;}
    static final class Tracker {
        String id="";long last=-1,heard,revision=-1;boolean previousPlaying,recorded;
        void tick(Context c,Repository r,String current,Uri uri,boolean playing,long now){long version=Recommendations.revision(c);if(!enabled(c) || !current.equals(id) || version!=revision){id=current;revision=version;heard=0;recorded=false;previousPlaying=false;last=now;}if(!enabled(c))return;long delta=last<0?0:now-last;last=now;if(playing && previousPlaying && delta>0 && delta<=2500)heard+=delta;previousPlaying=playing;if(recorded || heard<30000 || !playing)return;LibraryEntry entry=r.entry(current);AdSegments.Record record=AdSegments.load(c,current);Uri ready=entry==null?null:GeminiPreparation.readyUri(c,r,entry);if(record!=null && record.themes!=null && ready!=null && ready.equals(uri)){Recommendations.record(c,entry,record.themes);recorded=true;}}
    }
}
