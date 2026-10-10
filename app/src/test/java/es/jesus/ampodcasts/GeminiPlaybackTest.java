package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.app.DownloadManager;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.SilenceMediaSource;
import androidx.media3.session.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,33})
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class GeminiPlaybackTest {
    @Test public void savedAdsSkipDownloadedAudioAndUndoDoesNotLoopOrApplyToStreams() throws Exception {
        ServiceController<PlaybackService> service=Robolectric.buildService(PlaybackService.class).create();
        try {
            Repository r=new Repository(service.get());Podcast podcast=new Podcast("Test","https://example.com/rss");r.addPodcast(podcast);
            Episode episode=new Episode("ai-episode","Audio","","https://example.com/audio.mp3");LibraryEntry entry=new LibraryEntry(episode,podcast);r.remember(entry);r.download(episode);
            DownloadManager manager=service.get().getSystemService(DownloadManager.class);org.robolectric.shadows.ShadowDownloadManager downloads=Shadows.shadowOf(manager);Shadows.shadowOf(downloads.getRequest(r.downloadId(episode))).setStatus(DownloadManager.STATUS_SUCCESSFUL);assertNotNull(r.localUri(episode));
            AdSegments.Record record=new AdSegments.Record(r.downloadId(episode),180000,episode.url,"gemini-test",Arrays.asList(new AdSegments.Segment(10000,20000,"Anuncio")),new HashSet<>(),true);AdSegments.save(service.get(),episode.id,record);
            java.lang.reflect.Field field=PlaybackService.class.getDeclaredField("player");field.setAccessible(true);ExoPlayer player=(ExoPlayer)field.get(service.get());
            player.setMediaSource(source(entry.mediaItem(service.get(),false)));player.prepare();await(()->player.isCurrentMediaItemSeekable());player.seekTo(14000);player.play();await(player::isPlaying);service.get().applyAdSkips();
            assertTrue(player.getCurrentPosition()>=20000);assertEquals(episode.id,AdSegments.prefs(service.get()).getString("lastSkipId",""));
            assertEquals(SessionResult.RESULT_SUCCESS,service.get().undoAd().resultCode);assertTrue(player.getCurrentPosition()<20000);assertTrue(AdSegments.load(service.get(),episode.id).ignored.contains(10000L));service.get().applyAdSkips();assertTrue(player.getCurrentPosition()<20000);
            assertEquals(SessionError.ERROR_INVALID_STATE,service.get().undoAd().resultCode);
            // An RSS stream with the same GUID must not inherit download-specific times.
            AdSegments.save(service.get(),episode.id,record);MediaItem stream=entry.mediaItem(service.get(),false).buildUpon().setUri(episode.url).build();
            player.setMediaSource(source(stream));player.prepare();await(player::isCurrentMediaItemSeekable);player.seekTo(14000);player.play();await(player::isPlaying);service.get().applyAdSkips();assertTrue(player.getCurrentPosition()<20000);
            // A replacement download invalidates the old analysis even with a local URI.
            r.prefs.edit().putLong("download:"+Repository.key(episode.id),record.downloadId+1).commit();service.get().applyAdSkips();assertTrue(player.getCurrentPosition()<20000);
        } finally { service.destroy(); }
    }
    private SilenceMediaSource source(MediaItem item){SilenceMediaSource source=new SilenceMediaSource.Factory().setDurationUs(180000000).createMediaSource();source.updateMediaItem(item);return source;}
    interface Ready{boolean get();}
    private void await(Ready ready) throws Exception{long deadline=System.nanoTime()+10000000000L;while(!ready.get() && System.nanoTime()<deadline){Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();}assertTrue(ready.get());}
}
