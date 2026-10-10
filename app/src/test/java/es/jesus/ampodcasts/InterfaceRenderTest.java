package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.app.*;
import android.content.ComponentName;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InterfaceRenderTest {
    @Test public void renderLibraryWithThumbnailsAndLargeText() throws Exception {
        Application app = RuntimeEnvironment.getApplication(); Shadows.shadowOf(app).declareComponentUnbindable(new ComponentName(app, PlaybackService.class)); Repository r = new Repository(app); r.prefs.edit().putBoolean("autoRefresh", false).commit();
        try (org.robolectric.android.controller.ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) { capture(c.get(), "biblioteca"); find(c.get().getWindow().getDecorView(), "Añadir podcasts").performClick(); capture(ShadowDialog.getLatestDialog(), "anadir"); AlertDialog add = (AlertDialog) ShadowDialog.getLatestDialog(); add.getListView().performItemClick(add.getListView().getChildAt(1), 1, 1); capture(ShadowDialog.getLatestDialog(), "rss"); }
        Podcast p = new Podcast("Programa de demostración", "https://example.com/rss", "https://example.com/show.png"); r.addPodcast(p);
        Episode a = new Episode("a", "Un episodio de audio para disfrutar", "", "https://example.com/a.mp3", "", 5048000, "", ""), b = new Episode("b", "Un episodio con vídeo y su miniatura", "", "https://example.com/b.mp3", "https://example.com/b.mp4", 7265000, "", "https://example.com/episode.png");
        org.json.JSONArray episodes = new org.json.JSONArray().put(a.json()).put(b.json()); try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), "episodes-" + Repository.key(p.feed) + ".json"))) { out.write(episodes.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        cacheLogo(app, p.artwork, 0xff183e37, "play"); cacheLogo(app, b.artwork, 0xff453044, "video"); r.remember(new LibraryEntry(a, p)); r.savePosition(a.id, 3648000); r.setFavorite(new LibraryEntry(b, p), true);
        try (org.robolectric.android.controller.ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            flushImages(c.get()); View root = c.get().getWindow().getDecorView(); capture(c.get(), "podcasts"); find(root,"Abrir podcast Programa de demostración").performClick(); measure(root); assertNotNull(find(root, "Ver vídeo del episodio")); assertNotNull(find(root, "Portada de Programa de demostración")); capture(c.get(), "episodios");
            androidx.media3.exoplayer.ExoPlayer player=new androidx.media3.exoplayer.ExoPlayer.Builder(c.get()).build();
            try { LibraryEntry entry=new LibraryEntry(a,p); r.remember(entry); BookmarkStore.add(r.prefs,a.id,"Una recomendación",480000); player.setMediaItem(entry.mediaItem(c.get(),false)); player.seekTo(3648000); java.lang.reflect.Field f=MainActivity.class.getDeclaredField("controller");f.setAccessible(true);f.set(c.get(),player); java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updatePlayer");update.setAccessible(true);update.invoke(c.get());find(root,"Abrir reproductor completo").performClick(); flushImages(c.get()); capture(ShadowDialog.getLatestDialog(),"reproductor"); ShadowDialog.getLatestDialog().dismiss(); f.set(c.get(),null); } finally {player.release();}
            find(root, "Secciones de tu biblioteca").performClick(); capture(ShadowDialog.getLatestDialog(), "secciones");
        }
        try (org.robolectric.android.controller.ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).setup().visible()) { capture(c.get(), "ajustes"); ProgramOptions.show(c.get(), p.feed); capture(ShadowDialog.getLatestDialog(), "opciones"); }
        try (org.robolectric.android.controller.ActivityController<DiscoverActivity> c = Robolectric.buildActivity(DiscoverActivity.class).setup().visible()) { capture(c.get(), "descubrir"); }
        try (org.robolectric.android.controller.ActivityController<DownloadsActivity> c = Robolectric.buildActivity(DownloadsActivity.class).setup().visible()) { java.lang.reflect.Method render=DownloadsActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(c.get());capture(c.get(),"descargas"); }
        try (org.robolectric.android.controller.ActivityController<RecommendationsActivity> c = Robolectric.buildActivity(RecommendationsActivity.class).setup().visible()) { capture(c.get(),"para-ti"); }
        r.prefs.edit().putFloat("textScale", 1.3f).commit();
        try (org.robolectric.android.controller.ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup().visible()) { flushImages(c.get()); View root = c.get().getWindow().getDecorView(); find(root,"Abrir podcast Programa de demostración").performClick(); measure(root); View video = find(root, "Ver vídeo del episodio"); assertNotNull(video); assertTrue(video.getWidth() >= 144); capture(c.get(), "texto-grande"); }
    }
    private void cacheLogo(Application app, String url, int color, String icon) throws Exception { File directory = new File(app.getCacheDir(), "artwork"); directory.mkdirs(); Bitmap bitmap = Bitmap.createBitmap(168, 168, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(bitmap); canvas.drawColor(color); ControlIcon drawable = new ControlIcon(icon, 0xffa8c7fa, 90); drawable.setBounds(39, 39, 129, 129); drawable.draw(canvas); try (FileOutputStream out = new FileOutputStream(new File(directory, Repository.key(url) + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); } }
    private void measure(View root) { root.measure(View.MeasureSpec.makeMeasureSpec(1236, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2676, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 1236, 2676); }
    private void capture(Activity activity, String name) throws Exception { draw(activity.getWindow().getDecorView(), name); }
    private void capture(Dialog dialog, String name) throws Exception { String directory = System.getProperty("onda.captureDir", ""); if (directory.isEmpty()) return; View root = dialog.getWindow().getDecorView(); root.measure(View.MeasureSpec.makeMeasureSpec(1236, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(2676, View.MeasureSpec.AT_MOST)); root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight()); clearIndicators(root); Bitmap bitmap = Bitmap.createBitmap(1236, 2676, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.BLACK); canvas.translate((1236 - root.getWidth()) / 2f, (2676 - root.getHeight()) / 2f); root.draw(canvas); try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); } }
    private void draw(View root, String name) throws Exception { measure(root); String directory = System.getProperty("onda.captureDir", ""); if (directory.isEmpty()) return; new File(directory).mkdirs(); Bitmap bitmap = Bitmap.createBitmap(1236, 2676, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.BLACK); clearIndicators(root); root.draw(canvas); try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); } }
    private void clearIndicators(View view) { view.setScrollIndicators(0); if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) clearIndicators(group.getChildAt(i)); } }
    private void flushImages(MainActivity activity) throws Exception { java.lang.reflect.Field field = MainActivity.class.getDeclaredField("images"); field.setAccessible(true); java.util.concurrent.ExecutorService images = (java.util.concurrent.ExecutorService) field.get(activity); images.submit(() -> {}).get(); images.submit(() -> {}).get(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(activity); images.submit(() -> {}).get(); images.submit(() -> {}).get(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); }
    private View find(View view, String description) { if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view; if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), description); if (found != null) return found; } } return null; }
}
