package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.app.*;
import android.content.ComponentName;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import java.io.*;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LibraryUiTest {
    private void disconnectService() { Shadows.shadowOf(RuntimeEnvironment.getApplication()).declareComponentUnbindable(new ComponentName(RuntimeEnvironment.getApplication(), PlaybackService.class)); }
    @Test public void episodeControlsExposeVideoFavoritesAndQueueWithoutChangingLibrary() throws Exception {
        disconnectService(); Repository repository = new Repository(RuntimeEnvironment.getApplication()); repository.prefs.edit().putBoolean("autoRefresh", false).commit();
        Podcast podcast = new Podcast("Programa", "https://example.com/rss"); repository.addPodcast(podcast);
        Episode episode = new Episode("a", "Mi episodio", "", "https://example.com/a.mp3", "https://example.com/a.mp4");
        try (FileOutputStream out = new FileOutputStream(new File(RuntimeEnvironment.getApplication().getFilesDir(), "episodes-" + Repository.key(podcast.feed) + ".json"))) { out.write(new org.json.JSONArray().put(episode.json()).toString().getBytes(StandardCharsets.UTF_8)); }
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView(); assertNotNull(find(root, "Ver vídeo del episodio")); find(root, "Guardar en favoritos").performClick(); assertTrue(repository.favorite("a"));
            find(root, "Opciones del episodio").performClick(); AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog(); ListView choices = dialog.getListView(); choices.performItemClick(choices.getChildAt(1), 1, choices.getAdapter().getItemId(1));
            assertEquals(1, repository.queue().size()); assertEquals("a", repository.queue().get(0).episode.id);
        }
    }
    @Test public void videoScreenAndDownloadsScreenInitializeWithoutMedia() {
        disconnectService();
        try (ActivityController<VideoActivity> controller = Robolectric.buildActivity(VideoActivity.class).setup()) { assertNotNull(find(controller.get().getWindow().getDecorView(), "Volver a la biblioteca")); }
        try (ActivityController<DownloadsActivity> controller = Robolectric.buildActivity(DownloadsActivity.class).setup()) { assertNotNull(find(controller.get().getWindow().getDecorView(), "Volver a ajustes")); }
    }
    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), description); if (found != null) return found; } } return null;
    }
}
