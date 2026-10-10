package es.jesus.ampodcasts;

import android.app.*;
import android.widget.Toast;
import androidx.media3.session.MediaController;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

final class PlaybackTools {
    static final class Chapter { final String title; final long start; Chapter(String title, long start) { this.title = title; this.start = start; } }
    static void speed(Activity activity) { speed(activity, ""); }
    static void speed(Activity activity, String feed) {
        float[] speeds = {.75f, 1f, 1.25f, 1.5f, 1.75f, 2f}; Repository r = new Repository(activity); int selected = 1; for (int i = 0; i < speeds.length; i++) if (speeds[i] == ListeningPreferences.speed(r.prefs,feed)) selected = i;
        new AlertDialog.Builder(activity).setTitle(feed.isEmpty()?"Velocidad general":"Velocidad de este podcast").setSingleChoiceItems(new String[]{"0,75×", "1×", "1,25×", "1,5×", "1,75×", "2×"}, selected, (d, which) -> { r.prefs.edit().putFloat(feed.isEmpty()?"speed":ProgramOptions.key("programSpeed",feed), speeds[which]).apply(); d.dismiss(); }).setNegativeButton("Cerrar", null).setNeutralButton(feed.isEmpty()?null:"Usar ajuste general",(d,w)->r.prefs.edit().remove(ProgramOptions.key("programSpeed",feed)).apply()).show();
    }
    static List<Chapter> parse(String json) throws Exception {
        JSONArray array = new JSONObject(json).getJSONArray("chapters"); if (array.length() > 1000) throw new IOException("Demasiados capítulos"); List<Chapter> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) { JSONObject c = array.getJSONObject(i); double seconds = c.optDouble("startTime", -1); if (!c.optBoolean("toc", true) || !Double.isFinite(seconds) || seconds < 0 || seconds > 604800) continue; result.add(new Chapter(c.optString("title", "Capítulo " + (i + 1)), (long) (seconds * 1000))); }
        result.sort(Comparator.comparingLong(c -> c.start)); return result;
    }
    static List<Chapter> load(android.content.Context context, LibraryEntry entry) throws Exception {
        File directory=new File(context.getCacheDir(),"chapters"); directory.mkdirs(); File cache=new File(directory,Repository.key(entry.episode.chaptersUrl)+".json");
        if(cache.isFile() && cache.length()<=2_000_000) try { return parse(new String(java.nio.file.Files.readAllBytes(cache.toPath()),java.nio.charset.StandardCharsets.UTF_8)); } catch(Exception ignored) {}
        HttpURLConnection conn=(HttpURLConnection)new URL(Repository.normalizeFeed(entry.episode.chaptersUrl)).openConnection(); conn.setConnectTimeout(15000); conn.setReadTimeout(15000); conn.setInstanceFollowRedirects(false);
        try { if(conn.getResponseCode()!=200) throw new IOException("Capítulos no disponibles"); try(InputStream in=conn.getInputStream()){ ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>2_000_000)throw new IOException("Capítulos demasiado grandes");} String json=bytes.toString("UTF-8");List<Chapter> result=parse(json);try(FileOutputStream out=new FileOutputStream(cache)){out.write(bytes.toByteArray());}return result; } } finally {conn.disconnect();}
    }
    static void chapters(Activity activity, MediaController controller, ExecutorService worker) {
        if (controller.getCurrentMediaItem() == null) return; String id = controller.getCurrentMediaItem().mediaId; LibraryEntry entry = new Repository(activity).entry(id);
        if (entry == null || entry.episode.chaptersUrl.isEmpty()) { Toast.makeText(activity, "Este episodio no publica capítulos en su RSS", Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(activity, "Cargando capítulos…", Toast.LENGTH_SHORT).show(); worker.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(Repository.normalizeFeed(entry.episode.chaptersUrl)).openConnection(); conn.setConnectTimeout(15000); conn.setReadTimeout(15000); List<Chapter> list;
                try { if (conn.getResponseCode() != 200) throw new IOException(); try (InputStream in = conn.getInputStream()) { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) { bytes.write(b, 0, n); if (bytes.size() > 2_000_000) throw new IOException(); } list = parse(bytes.toString("UTF-8")); } } finally { conn.disconnect(); }
                activity.runOnUiThread(() -> { if (activity.isDestroyed()) return; if (list.isEmpty()) { Toast.makeText(activity, "No hay capítulos disponibles", Toast.LENGTH_LONG).show(); return; } String[] labels = new String[list.size()]; for (int i = 0; i < labels.length; i++) labels[i] = TimeFormat.display(list.get(i).start, true) + " · " + list.get(i).title;
                    new AlertDialog.Builder(activity).setTitle("Capítulos").setItems(labels, (d, which) -> { if (controller.getCurrentMediaItem() != null && controller.getCurrentMediaItem().mediaId.equals(id)) controller.seekTo(list.get(which).start); else Toast.makeText(activity, "El episodio en reproducción ha cambiado", Toast.LENGTH_SHORT).show(); }).setNegativeButton("Cerrar", null).show(); });
            } catch (Exception ignored) { activity.runOnUiThread(() -> { if (!activity.isDestroyed()) Toast.makeText(activity, "No se pudieron cargar los capítulos", Toast.LENGTH_LONG).show(); }); }
        });
    }
}
