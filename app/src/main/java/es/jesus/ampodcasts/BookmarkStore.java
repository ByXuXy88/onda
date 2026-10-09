package es.jesus.ampodcasts;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

final class BookmarkStore {
    static final class Mark { final String name; final long position; Mark(String name, long position) { this.name=name; this.position=position; } }
    static String key(String id) { return "marks:" + Repository.key(id); }
    static List<Mark> list(SharedPreferences prefs, String id) { return parse(prefs.getString(key(id), "[]")); }
    static List<Mark> parse(String json) {
        List<Mark> result = new ArrayList<>();
        try { JSONArray array=new JSONArray(json); if (array.length()>200) return result;
            for(int i=0;i<array.length();i++) { JSONObject o=array.getJSONObject(i); long p=o.getLong("position"); String name=o.getString("name"); if(p<0 || p>604800000L || name.isEmpty() || name.length()>120) throw new JSONException("Marca no válida"); result.add(new Mark(name,p)); }
        } catch(Exception e) { result.clear(); }
        result.sort(Comparator.comparingLong(m->m.position)); return result;
    }
    static void add(SharedPreferences prefs, String id, String name, long position) throws Exception {
        List<Mark> marks=list(prefs,id); if(marks.size()>=200) throw new IllegalStateException("Máximo de 200 marcas por episodio");
        name=name.trim(); if(name.isEmpty() || name.length()>120 || position<0 || position>604800000L) throw new IllegalArgumentException("Nombre o posición no válidos");
        marks.add(new Mark(name,position)); save(prefs,id,marks);
    }
    static void remove(SharedPreferences prefs, String id, Mark mark) throws Exception {
        List<Mark> marks=list(prefs,id); for(int i=0;i<marks.size();i++) if(marks.get(i).position==mark.position && marks.get(i).name.equals(mark.name)) { marks.remove(i); break; } save(prefs,id,marks);
    }
    private static void save(SharedPreferences prefs,String id,List<Mark> marks) throws Exception { JSONArray a=new JSONArray(); for(Mark m:marks) a.put(new JSONObject().put("name",m.name).put("position",m.position)); prefs.edit().putString(key(id),a.toString()).apply(); }
}
