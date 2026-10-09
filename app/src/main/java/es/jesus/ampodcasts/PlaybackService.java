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
    static final String JUMP_BACK = "onda.jumpBack", JUMP_FORWARD = "onda.jumpForward";
    private ExoPlayer player;
    private MediaSession session;
    private Repository repository;
    private String previousId = "";
    private boolean initialSkipPending;
    private String endingSkippedId = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (prefs, key) -> { if ("speed".equals(key) || "skipSilence".equals(key) || "jumpBack".equals(key) || "jumpForward".equals(key)) handler.post(this::applyPreferences); };
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            long deadline = repository.prefs.getLong("sleepDeadline", 0);
            if (deadline > 0 && SystemClock.elapsedRealtime() >= deadline) { player.pause(); repository.prefs.edit().remove("sleepDeadline").apply(); }
            applyOffsets(); save(); handler.postDelayed(this, 1000);
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
                endingSkippedId = ""; initialSkipPending = item != null && (!repository.prefs.getBoolean("resumePlayback",true) || repository.position(item.mediaId) == 0);
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
                if (reason == Player.DISCONTINUITY_REASON_SEEK) initialSkipPending = false;
                if (oldPos.mediaItem != null) {
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) repository.setListened(oldPos.mediaItem.mediaId, true);
                    else repository.savePosition(oldPos.mediaItem.mediaId, oldPos.positionMs);
                }
            }
            @Override public void onPlaybackStateChanged(int state) {
                if(state == Player.STATE_READY) applyOffsets();
                if (state == Player.STATE_ENDED && !previousId.isEmpty()) repository.setListened(previousId, true);
                if (state == Player.STATE_ENDED && previousId.equals(repository.prefs.getString("sleepEpisode", ""))) { player.pause(); repository.prefs.edit().remove("sleepEpisode").apply(); }
            }
        });
        PendingIntent activity = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player).setSessionActivity(activity).setMediaButtonPreferences(notificationButtons()).setCallback(new MediaSession.Callback() {
            @Override public MediaSession.ConnectionResult onConnect(MediaSession session, MediaSession.ControllerInfo info) {
                SessionCommands commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(new SessionCommand(JUMP_BACK, android.os.Bundle.EMPTY)).add(new SessionCommand(JUMP_FORWARD, android.os.Bundle.EMPTY)).build();
                Player.Commands playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS;
                if (session.isMediaNotificationController(info)) playerCommands = playerCommands.buildUpon().remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).build();
                if (getPackageName().equals(info.getPackageName())) commands = commands.buildUpon().add(new SessionCommand(PREPARE_RESTORE, android.os.Bundle.EMPTY)).build();
                return new MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).setAvailablePlayerCommands(playerCommands).build();
            }
            @Override public com.google.common.util.concurrent.ListenableFuture<SessionResult> onCustomCommand(MediaSession session, MediaSession.ControllerInfo info, SessionCommand command, android.os.Bundle args) {
                if (JUMP_BACK.equals(command.customAction) || JUMP_FORWARD.equals(command.customAction)) return com.google.common.util.concurrent.Futures.immediateFuture(notificationJump(command.customAction));
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
        if (session != null) session.setMediaButtonPreferences(notificationButtons());
        player.setPlaybackSpeed(repository.prefs.getFloat("speed", 1f));
        player.setSkipSilenceEnabled(repository.prefs.getBoolean("skipSilence", false));
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setForceHighestSupportedBitrate(true).build());
    }
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    java.util.List<CommandButton> notificationButtons() {
        return java.util.Arrays.asList(notificationButton(false), notificationButton(true));
    }
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private CommandButton notificationButton(boolean forward) {
        int seconds = SkipRules.seconds(repository.prefs, forward);
        int icon = forward ? CommandButton.ICON_SKIP_FORWARD : CommandButton.ICON_SKIP_BACK;
        if (seconds == 10) icon = forward ? CommandButton.ICON_SKIP_FORWARD_10 : CommandButton.ICON_SKIP_BACK_10;
        else if (seconds == 15) icon = forward ? CommandButton.ICON_SKIP_FORWARD_15 : CommandButton.ICON_SKIP_BACK_15;
        else if (seconds == 30) icon = forward ? CommandButton.ICON_SKIP_FORWARD_30 : CommandButton.ICON_SKIP_BACK_30;
        return new CommandButton.Builder(icon).setDisplayName((forward ? "Avanzar " : "Retroceder ") + seconds + " segundos")
                .setSessionCommand(new SessionCommand(forward ? JUMP_FORWARD : JUMP_BACK, android.os.Bundle.EMPTY))
                .setSlots(forward ? CommandButton.SLOT_FORWARD : CommandButton.SLOT_BACK).build();
    }
    SessionResult notificationJump(String action) {
        if (!JUMP_BACK.equals(action) && !JUMP_FORWARD.equals(action)) return new SessionResult(SessionError.ERROR_NOT_SUPPORTED);
        if (player == null || player.getCurrentMediaItem() == null || !player.isCurrentMediaItemSeekable()) return new SessionResult(SessionError.ERROR_INVALID_STATE);
        long target = Math.max(0, player.getCurrentPosition() + (JUMP_FORWARD.equals(action) ? 1 : -1) * SkipRules.seconds(repository.prefs, JUMP_FORWARD.equals(action)) * 1000L);
        if (player.getDuration() > 0) target = Math.min(target, player.getDuration());
        player.seekTo(target); save(); return new SessionResult(SessionResult.RESULT_SUCCESS);
    }
    private void applyOffsets() {
        MediaItem item = player.getCurrentMediaItem(); if(item==null || player.getPlaybackState()!=Player.STATE_READY || player.getDuration()<=0) return;
        android.os.Bundle extras=item.mediaMetadata.extras; if(extras==null) return; String feed=extras.getString("feed", "");
        long start=SkipRules.offset(repository.prefs,"skipStart",feed), end=SkipRules.offset(repository.prefs,"skipEnd",feed), duration=player.getDuration();
        if(initialSkipPending) { initialSkipPending=false; long position=player.getCurrentPosition(); if(start+end<duration) { long target=SkipRules.initial(position,start,duration); if(target!=position) { player.seekTo(target); return; } } }
        if(player.isPlaying() && !item.mediaId.equals(endingSkippedId) && SkipRules.finish(player.getCurrentPosition(),duration,start,end)) { endingSkippedId=item.mediaId; player.seekTo(duration); }
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
