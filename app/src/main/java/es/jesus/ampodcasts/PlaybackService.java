package es.jesus.ampodcasts;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.*;

public final class PlaybackService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession session;
    private Repository repository;
    private String previousId = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() { save(); handler.postDelayed(this, 3000); }
    };
    @Override public void onCreate() {
        super.onCreate(); repository = new Repository(this);
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        player.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem item, int reason) { previousId = item == null ? "" : item.mediaId; save(); }
            @Override public void onIsPlayingChanged(boolean isPlaying) { save(); }
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPos, Player.PositionInfo newPos, int reason) {
                if (!previousId.isEmpty()) repository.savePosition(previousId, oldPos.positionMs);
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && !previousId.isEmpty()) repository.savePosition(previousId, 0);
            }
        });
        PendingIntent activity = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player).setSessionActivity(activity).build();
        handler.post(checkpoint);
    }
    private void save() {
        MediaItem item = player == null ? null : player.getCurrentMediaItem();
        if (item != null && player.getPlaybackState() != Player.STATE_ENDED) {
            repository.savePosition(item.mediaId, player.getCurrentPosition());
            repository.prefs.edit().putString("last", item.mediaId).apply();
        }
    }
    @androidx.media3.common.util.UnstableApi
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return getPackageName().equals(controllerInfo.getPackageName()) || controllerInfo.isTrusted() ? session : null;
    }
    @Override public void onDestroy() {
        save(); handler.removeCallbacksAndMessages(null);
        if (session != null) session.release(); if (player != null) player.release(); super.onDestroy();
    }
}
