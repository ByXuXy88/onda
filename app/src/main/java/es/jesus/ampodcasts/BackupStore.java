package es.jesus.ampodcasts;

import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class BackupStore {
    static boolean allowed(String key) {
        return Arrays.asList("podcasts", "queue", "selectedFeed", "last", "libraryInitialized", "speed", "skipSilence", "resumePlayback", "wifiOnly", "autoRefresh", "oldestFirst", "hideListened", "textScale", "videoPip", "deletePlayedDownloads", "dynamicColors", "jumpBack", "jumpForward").contains(key)
            || key.matches("(pos|played|favorite|entry|touched|duration|auto|notify|limit|skipStart|skipEnd|marks):[a-f0-9]{64}");
    }
    static void export(Context context, OutputStream out) throws Exception {
        JSONObject values = new JSONObject(); for (Map.Entry<String, ?> e : new Repository(context).prefs.getAll().entrySet()) if (allowed(e.getKey())) values.put(e.getKey(), e.getValue());
        JSONObject json = new JSONObject().put("app", "Onda").put("version", 1).put("preferences", values);
        byte[] bytes = json.toString(2).getBytes(StandardCharsets.UTF_8); if (bytes.length > 10_000_000) throw new IOException("La copia supera 10 MB"); out.write(bytes);
    }
    static Map<String, Object> validate(InputStream in) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read; while ((read = in.read(buffer)) != -1) { bytes.write(buffer, 0, read); if (bytes.size() > 10_000_000) throw new IOException("La copia supera 10 MB"); }
        JSONObject json = new JSONObject(bytes.toString("UTF-8")); if (!"Onda".equals(json.optString("app")) || json.optInt("version") != 1) throw new IOException("No es una copia compatible de Onda");
        JSONObject values = json.getJSONObject("preferences"); Map<String, Object> result = new HashMap<>(); Iterator<String> keys = values.keys();
        while (keys.hasNext()) { String key = keys.next(); if (!allowed(key)) continue; Object value = values.get(key); validateValue(key, value); result.put(key, value); }
        JSONArray podcasts = new JSONArray((String) result.getOrDefault("podcasts", "[]")); if (podcasts.length() > 100) throw new IOException("Máximo de 100 programas por copia"); Set<String> feeds = new HashSet<>();
        for (int i = 0; i < podcasts.length(); i++) { Podcast p = Podcast.from(podcasts.getJSONObject(i)); Repository.normalizeFeed(p.feed); if (!feeds.add(p.feed)) throw new IOException("Programas duplicados"); }
        JSONArray queue = new JSONArray((String) result.getOrDefault("queue", "[]")); if (queue.length() > 200) throw new IOException("Cola demasiado grande"); Set<String> ids = new HashSet<>(); for (int i = 0; i < queue.length(); i++) { LibraryEntry e = checkedEntry(queue.getJSONObject(i)); if (!feeds.contains(e.podcast.feed) || !ids.add(e.episode.id)) throw new IOException("Cola no válida"); }
        for (Map.Entry<String, Object> e : result.entrySet()) if (e.getKey().startsWith("entry:")) { LibraryEntry entry = checkedEntry(new JSONObject((String) e.getValue())); if (!e.getKey().equals("entry:" + Repository.key(entry.episode.id))) throw new IOException("Episodio no válido"); }
        return result;
    }
    private static LibraryEntry checkedEntry(JSONObject json) throws Exception { LibraryEntry entry = LibraryEntry.from(json); Repository.normalizeFeed(entry.podcast.feed); Repository.normalizeFeed(entry.episode.url); if (!entry.episode.videoUrl.isEmpty()) Repository.normalizeFeed(entry.episode.videoUrl); if (!entry.episode.chaptersUrl.isEmpty()) Repository.normalizeFeed(entry.episode.chaptersUrl); if (entry.episode.id.isEmpty()) throw new IOException("Episodio sin identificador"); return entry; }
    private static void validateValue(String key, Object value) throws Exception {
        boolean string = Arrays.asList("podcasts", "queue", "selectedFeed", "last").contains(key) || key.startsWith("entry:") || key.startsWith("marks:");
        boolean number = key.equals("speed") || key.equals("textScale") || key.startsWith("pos:") || key.startsWith("touched:") || key.startsWith("duration:") || key.startsWith("limit:") || key.startsWith("skipStart:") || key.startsWith("skipEnd:") || key.equals("jumpBack") || key.equals("jumpForward");
        if (string ? !(value instanceof String) : number ? !(value instanceof Number) : !(value instanceof Boolean)) throw new IOException("Tipo de ajuste no válido");
        if (key.startsWith("marks:")) { JSONArray marks=new JSONArray((String)value); if(marks.length()>200 || BookmarkStore.parse((String)value).size()!=marks.length()) throw new IOException("Marcas no válidas"); }
        if (number) { double n = ((Number) value).doubleValue(); if (!Double.isFinite(n) || n < 0) throw new IOException("Valor no válido"); if (key.equals("speed") && (n < .5 || n > 3) || key.equals("textScale") && (n < 1 || n > 1.3) || key.startsWith("limit:") && (n < 1 || n > 10)) throw new IOException("Ajuste fuera de rango"); if ((key.startsWith("skipStart:") || key.startsWith("skipEnd:")) && (n>1800 || n!=Math.floor(n)) || (key.equals("jumpBack") || key.equals("jumpForward")) && n!=10 && n!=15 && n!=30 && n!=60) throw new IOException("Salto fuera de rango"); }
    }
    static void restore(Context context, Map<String, Object> values) throws Exception {
        Repository r = new Repository(context); SharedPreferences.Editor edit = r.prefs.edit();
        // Replace portable state; keep this device's downloads and discard runtime-only state.
        for (String key : r.prefs.getAll().keySet()) if (allowed(key) || key.startsWith("seen:") || key.startsWith("pending:") || key.startsWith("sleep") || key.equals("activeEpisode")) edit.remove(key);
        for (Map.Entry<String, Object> e : values.entrySet()) { String key = e.getKey(); Object value = e.getValue(); if (value instanceof String) edit.putString(key, (String) value); else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value); else if (key.equals("speed") || key.equals("textScale")) edit.putFloat(key, ((Number) value).floatValue()); else if (key.startsWith("limit:") || key.equals("jumpBack") || key.equals("jumpForward")) edit.putInt(key, ((Number) value).intValue()); else edit.putLong(key, ((Number) value).longValue()); }
        edit.putBoolean("libraryInitialized", true); if (!edit.commit()) throw new IOException("No se pudo restaurar la copia");
        for (Podcast p : r.podcasts()) BackgroundSync.initialize(context, p.feed); BackgroundSync.schedule(context);
    }
}
