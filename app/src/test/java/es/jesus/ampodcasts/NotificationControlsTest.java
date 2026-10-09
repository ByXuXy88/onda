package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.os.Looper;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.SilenceMediaSource;
import androidx.media3.session.*;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 33})
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class NotificationControlsTest {
    @Test public void notificationOrdersJumpPlayJumpAndRefreshesConfiguredSeconds() throws Exception {
        ServiceController<PlaybackService> service = Robolectric.buildService(PlaybackService.class).create();
        try {
            java.lang.reflect.Field field = PlaybackService.class.getDeclaredField("session"); field.setAccessible(true);
            MediaSession session = (MediaSession) field.get(service.get());
            class Provider extends DefaultMediaNotificationProvider {
                Provider() { super(service.get()); }
                ImmutableList<CommandButton> buttons() { return getMediaButtons(session, MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS, session.getMediaButtonPreferences(), false); }
            }
            Provider provider = new Provider(); ImmutableList<CommandButton> buttons = provider.buttons();
            assertEquals(3, buttons.size()); assertEquals(PlaybackService.JUMP_BACK, buttons.get(0).sessionCommand.customAction);
            assertEquals(Player.COMMAND_PLAY_PAUSE, buttons.get(1).playerCommand);
            assertEquals(PlaybackService.JUMP_FORWARD, buttons.get(2).sessionCommand.customAction);
            Repository r = new Repository(service.get()); r.prefs.edit().putInt("jumpBack", 60).putInt("jumpForward", 10).commit();
            Shadows.shadowOf(Looper.getMainLooper()).idle(); buttons = provider.buttons();
            assertEquals("Retroceder 60 segundos", buttons.get(0).displayName.toString());
            assertEquals(CommandButton.ICON_SKIP_BACK, buttons.get(0).icon);
            assertEquals("Avanzar 10 segundos", buttons.get(2).displayName.toString());
            assertEquals(CommandButton.ICON_SKIP_FORWARD_10, buttons.get(2).icon);
        } finally { service.destroy(); }
    }
    @Test public void notificationJumpsStayInEpisodeAndClampAtBothEnds() throws Exception {
        ServiceController<PlaybackService> service = Robolectric.buildService(PlaybackService.class).create();
        try {
            java.lang.reflect.Field field = PlaybackService.class.getDeclaredField("player"); field.setAccessible(true);
            ExoPlayer player = (ExoPlayer) field.get(service.get());
            assertEquals(SessionError.ERROR_INVALID_STATE, service.get().notificationJump(PlaybackService.JUMP_FORWARD).resultCode);
            player.setMediaSource(new SilenceMediaSource.Factory().setDurationUs(180000000).createMediaSource()); player.prepare();
            long timeout = System.nanoTime() + 5000000000L;
            while (!player.isCurrentMediaItemSeekable() && System.nanoTime() < timeout) { Thread.sleep(10); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
            assertTrue(player.isCurrentMediaItemSeekable()); String id = player.getCurrentMediaItem().mediaId;
            java.lang.reflect.Field sessionField = PlaybackService.class.getDeclaredField("session"); sessionField.setAccessible(true);
            MediaSession session = (MediaSession) sessionField.get(service.get());
            com.google.common.util.concurrent.ListenableFuture<MediaController> connected = new MediaController.Builder(service.get(), session.getToken()).buildAsync();
            timeout = System.nanoTime() + 5000000000L;
            while (!connected.isDone() && System.nanoTime() < timeout) { Thread.sleep(10); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
            assertTrue(connected.isDone()); MediaController remote = connected.get();
            try {
            assertTrue(remote.getAvailableSessionCommands().contains(new SessionCommand(PlaybackService.JUMP_BACK, android.os.Bundle.EMPTY)));
            assertTrue(remote.getAvailableSessionCommands().contains(new SessionCommand(PlaybackService.JUMP_FORWARD, android.os.Bundle.EMPTY)));
            player.seekTo(5000);
            com.google.common.util.concurrent.ListenableFuture<SessionResult> result = remote.sendCustomCommand(new SessionCommand(PlaybackService.JUMP_BACK, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY);
            timeout = System.nanoTime() + 5000000000L;
            while (!result.isDone() && System.nanoTime() < timeout) { Thread.sleep(10); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
            assertTrue(result.isDone()); assertEquals(SessionResult.RESULT_SUCCESS, result.get().resultCode); assertEquals(0, player.getCurrentPosition());
            Repository r = new Repository(service.get()); r.prefs.edit().putInt("jumpForward", 60).commit();
            player.seekTo(150000); service.get().notificationJump(PlaybackService.JUMP_FORWARD); assertTrue(player.getCurrentPosition() >= player.getDuration() - 1 && player.getCurrentPosition() <= player.getDuration());
            assertEquals(id, player.getCurrentMediaItem().mediaId); assertEquals(player.getCurrentPosition(), r.position(id));
            assertEquals(SessionError.ERROR_NOT_SUPPORTED, service.get().notificationJump("unknown").resultCode);
            } finally { remote.release(); }
        } finally { service.destroy(); }
    }
}
