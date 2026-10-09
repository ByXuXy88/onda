package es.jesus.ampodcasts;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.SharedPreferences;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.*;

public final class PlaybackService extends MediaSessionService {
    static final String PREPARE_RESTORE = "onda.prepareRestore";
    private ExoPlayer player;
    private MediaSession session;
    private Repository repository;
    private String previousId = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (prefs, key) -> { if ("speed".equals(key) || "skipSilence".equals(key)) handler.post(this::applyPreferences); };
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            long deadline = repository.prefs.getLong("sleepDeadline", 0);
            if (deadline > 0 && SystemClock.elapsedRealtime() >= deadline) { player.pause(); repository.prefs.edit().remove("sleepDeadline").apply(); }
            save(); handler.postDelayed(this, 1000);
        }
    };
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @Override public void onCreate() {
        super.onCreate(); repository = new Repository(this);
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        applyPreferences(); repository.prefs.registerOnSharedPreferenceChangeListener(preferencesChanged);
        player.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !previousId.isEmpty()) repository.setListened(previousId, true);
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && previousId.equals(repository.prefs.getString("sleepEpisode", ""))) { player.pause(); repository.prefs.edit().remove("sleepEpisode").apply(); }
                previousId = item == null ? "" : item.mediaId; repository.prefs.edit().putString("activeEpisode", previousId).apply();
                if (item != null) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && repository.prefs.getBoolean("resumePlayback", true)) { long position = repository.position(item.mediaId); if (position > 0) player.seekTo(position); }
                    repository.prefs.edit().putLong("touched:" + Repository.key(item.mediaId), System.currentTimeMillis()).apply();
                    try { repository.dequeue(item.mediaId); } catch (Exception ignored) { }
                }
                save();
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) { save(); }
            @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPos, Player.PositionInfo newPos, int reason) {
                if (oldPos.mediaItem != null) {
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) repository.setListened(oldPos.mediaItem.mediaId, true);
                    else repository.savePosition(oldPos.mediaItem.mediaId, oldPos.positionMs);
                }
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && !previousId.isEmpty()) repository.setListened(previousId, true);
                if (state == Player.STATE_ENDED && previousId.equals(repository.prefs.getString("sleepEpisode", ""))) { player.pause(); repository.prefs.edit().remove("sleepEpisode").apply(); }
            }
        });
        PendingIntent activity = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player).setSessionActivity(activity).setCallback(new MediaSession.Callback() {
            @Override public MediaSession.ConnectionResult onConnect(MediaSession session, MediaSession.ControllerInfo info) {
                SessionCommands commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS;
                if (getPackageName().equals(info.getPackageName())) commands = commands.buildUpon().add(new SessionCommand(PREPARE_RESTORE, android.os.Bundle.EMPTY)).build();
                return new MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build();
            }
            @Override public com.google.common.util.concurrent.ListenableFuture<SessionResult> onCustomCommand(MediaSession session, MediaSession.ControllerInfo info, SessionCommand command, android.os.Bundle args) {
                if (PREPARE_RESTORE.equals(command.customAction) && getPackageName().equals(info.getPackageName())) { prepareForRestore(); return com.google.common.util.concurrent.Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS)); }
                return com.google.common.util.concurrent.Futures.immediateFuture(new SessionResult(SessionError.ERROR_NOT_SUPPORTED));
            }
        }).build();
        handler.post(checkpoint);
    }
    void prepareForRestore() { player.pause(); save(); player.clearMediaItems(); repository.prefs.edit().remove("sleepEpisode").remove("sleepDeadline").remove("activeEpisode").apply(); }
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void applyPreferences() {
        if (player == null) return;
        player.setPlaybackSpeed(repository.prefs.getFloat("speed", 1f));
        player.setSkipSilenceEnabled(repository.prefs.getBoolean("skipSilence", false));
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setForceHighestSupportedBitrate(true).build());
    }
    private void save() {
        MediaItem item = player == null ? null : player.getCurrentMediaItem();
        if (item != null && player.getPlaybackState() != Player.STATE_ENDED) {
            repository.savePosition(item.mediaId, player.getCurrentPosition());
            if (player.getDuration() > 0) repository.prefs.edit().putLong("duration:" + Repository.key(item.mediaId), player.getDuration()).apply();
            repository.prefs.edit().putString("last", item.mediaId).apply();
        }
    }
    @androidx.media3.common.util.UnstableApi
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return getPackageName().equals(controllerInfo.getPackageName()) || controllerInfo.isTrusted() ? session : null;
    }
    @Override public void onDestroy() {
        save(); repository.prefs.edit().remove("activeEpisode").apply(); handler.removeCallbacksAndMessages(null);
        repository.prefs.unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        if (session != null) session.release(); if (player != null) player.release(); super.onDestroy();
    }
}
