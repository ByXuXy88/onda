package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.os.Looper;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowSystemClock;
import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlaybackSettingsTest {
    @Test public void playlistConsumesPendingEpisodesInServiceWithoutMarkingManualSkipsListened() throws Exception {
        Repository repository = new Repository(RuntimeEnvironment.getApplication()); Podcast podcast = new Podcast("Uno", "https://example.com/rss"); repository.addPodcast(podcast);
        LibraryEntry a = new LibraryEntry(new Episode("a", "Primero", "", "https://example.com/a.mp3"), podcast), b = new LibraryEntry(new Episode("b", "Segundo", "", "https://example.com/b.mp3"), podcast); repository.enqueue(a); repository.enqueue(b);
        ServiceController<PlaybackService> controller = Robolectric.buildService(PlaybackService.class).create();
        try {
            java.lang.reflect.Field field = PlaybackService.class.getDeclaredField("player"); field.setAccessible(true); ExoPlayer player = (ExoPlayer) field.get(controller.get());
            player.setMediaItems(java.util.Arrays.asList(a.mediaItem(controller.get(), false), b.mediaItem(controller.get(), false)));
            assertEquals(1, repository.queue().size()); assertEquals("b", repository.queue().get(0).episode.id);
            player.seekToNextMediaItem(); assertEquals("b", player.getCurrentMediaItem().mediaId); assertTrue(repository.queue().isEmpty()); assertFalse(repository.listened("a"));
        } finally { controller.destroy(); }
    }
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @Test public void backgroundPlayerAppliesSettingsAndPausesWhenTimerExpires() throws Exception {
        Repository r = new Repository(RuntimeEnvironment.getApplication()); r.prefs.edit().putFloat("speed", 1.5f).putBoolean("skipSilence", true).commit();
        ServiceController<PlaybackService> controller = Robolectric.buildService(PlaybackService.class).create();
        try {
            java.lang.reflect.Field field = PlaybackService.class.getDeclaredField("player"); field.setAccessible(true); ExoPlayer player = (ExoPlayer) field.get(controller.get());
            assertEquals(1.5f, player.getPlaybackParameters().speed, .001f); assertTrue(player.getSkipSilenceEnabled());
            r.prefs.edit().putFloat("speed", 1.25f).commit(); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(1.25f, player.getPlaybackParameters().speed, .001f);
            player.setPlayWhenReady(true); r.prefs.edit().putLong("sleepDeadline", android.os.SystemClock.elapsedRealtime() + 1000).commit();
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2)); Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(player.getPlayWhenReady()); assertEquals(0, r.prefs.getLong("sleepDeadline", 0));
        } finally { controller.destroy(); }
    }
}
