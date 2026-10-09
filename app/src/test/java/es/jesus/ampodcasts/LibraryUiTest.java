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
            View root = controller.get().getWindow().getDecorView(); assertNotNull(find(root,"Abrir podcast Programa")); find(root,"Abrir podcast Programa").performClick(); assertNotNull(find(root, "Ver vídeo del episodio")); find(root, "Guardar en favoritos").performClick(); assertTrue(repository.favorite("a"));
            find(root, "Opciones del episodio").performClick(); AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog(); ListView choices = dialog.getListView(); choices.performItemClick(choices.getChildAt(1), 1, choices.getAdapter().getItemId(1));
            assertEquals(1, repository.queue().size()); assertEquals("a", repository.queue().get(0).episode.id);
        }
    }
    @Test public void browsingOtherProgramsKeepsPlayingItemAndPersonalMarks() throws Exception {
        disconnectService(); Repository r=new Repository(RuntimeEnvironment.getApplication());r.prefs.edit().putBoolean("autoRefresh",false).commit();Podcast first=new Podcast("Uno","https://example.com/one"),second=new Podcast("Dos","https://example.com/two");r.addPodcast(first);r.addPodcast(second);LibraryEntry entry=new LibraryEntry(new Episode("playing","Escuchando Uno","","https://example.com/a.mp3"),first);r.remember(entry);
        try(ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).setup()) {
            androidx.media3.exoplayer.ExoPlayer player=new androidx.media3.exoplayer.ExoPlayer.Builder(activity.get()).build();try{player.setMediaItem(entry.mediaItem(activity.get(),false));player.seekTo(120000);java.lang.reflect.Field field=MainActivity.class.getDeclaredField("controller");field.setAccessible(true);field.set(activity.get(),player);java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updatePlayer");update.setAccessible(true);update.invoke(activity.get());View root=activity.get().getWindow().getDecorView();find(root,"Abrir podcast Dos").performClick();assertEquals("playing",player.getCurrentMediaItem().mediaId);assertEquals(120000,player.getCurrentPosition());
                find(root,"Abrir reproductor completo").performClick();Dialog full=ShadowDialog.getLatestDialog();assertTrue(full.isShowing());findText(full.getWindow().getDecorView(),"Guardar una marca en este momento").performClick();AlertDialog name=(AlertDialog)ShadowDialog.getLatestDialog();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();EditText input=findInput(name.getWindow().getDecorView());input.setText("Tema interesante");name.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals("Tema interesante",BookmarkStore.list(r.prefs,"playing").get(0).name);assertEquals(120000,BookmarkStore.list(r.prefs,"playing").get(0).position);full.dismiss();field.set(activity.get(),null);
            }finally{player.release();}
        }
    }
    private View findText(View view,String text){if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View result=findText(g.getChildAt(i),text);if(result!=null)return result;}}return null;}
    private EditText findInput(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){EditText result=findInput(g.getChildAt(i));if(result!=null)return result;}}return null;}
    @Test public void videoScreenAndDownloadsScreenInitializeWithoutMedia() {
        disconnectService();
        try (ActivityController<VideoActivity> controller = Robolectric.buildActivity(VideoActivity.class).setup()) { assertNotNull(find(controller.get().getWindow().getDecorView(), "Volver a la biblioteca")); }
        try (ActivityController<DownloadsActivity> controller = Robolectric.buildActivity(DownloadsActivity.class).setup()) { assertNotNull(find(controller.get().getWindow().getDecorView(), "Volver a la biblioteca")); }
    }
    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), description); if (found != null) return found; } } return null;
    }
}
