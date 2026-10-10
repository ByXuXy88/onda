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

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    static final String PREPARE_RESTORE = "onda.prepareRestore";
    static final String JUMP_BACK = "onda.jumpBack", JUMP_FORWARD = "onda.jumpForward";
    static final String UNDO_AD = "onda.undoAd", CANCEL_PREPARATION="onda.cancelPreparation", PLAY_WITHOUT_ANALYSIS="onda.playWithoutAnalysis";
    private static final int PREPARING_NOTIFICATION=1903;
    private GeminiPlaybackGate sessionPlayer;
    private java.util.concurrent.ExecutorService preparationWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    java.util.function.Supplier<GeminiPreparation.Work> preparationFactory=()->new GeminiPreparation.Analysis(this);
    private GeminiPreparation.Work preparationWork;private java.util.concurrent.Future<?> preparationTask;
    private volatile int preparationGeneration;private boolean preparing,destroyed;private String preparationId="",bypassId="",queuedFrom="";
    private android.os.PowerManager.WakeLock preparationLock;
    private final android.content.BroadcastReceiver noisyReceiver=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,Intent i){if(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction()) && preparing){cancelPreparation("Auriculares desconectados: preparación cancelada");player.pause();}}};
    private String adCacheId="", adCacheText="", lastAdId="";
    private AdSegments.Record adCache;
    private long lastAdStart=-1, lastAdFrom=-1;
    private ExoPlayer player;
    private MediaSession session;
    private Repository repository;
    private String previousId = "";
    private boolean initialSkipPending;
    private String endingSkippedId = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener geminiChanged=(prefs,key)->{if(GeminiPreparation.ENABLED.equals(key))handler.post(()->{if(!GeminiPreparation.enabled(this)){cancelPreparation("Preparación automática desactivada");}else if(player!=null && player.getMediaItemCount()>1){int index=player.getCurrentMediaItemIndex();if(index+1<player.getMediaItemCount())player.removeMediaItems(index+1,player.getMediaItemCount());}});};
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (prefs, key) -> { if ("speed".equals(key) || "skipSilence".equals(key) || "jumpBack".equals(key) || "jumpForward".equals(key)) handler.post(this::applyPreferences); };
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            long deadline = repository.prefs.getLong("sleepDeadline", 0);
            if (deadline > 0 && SystemClock.elapsedRealtime() >= deadline) { cancelPreparation("Temporizador finalizado: preparación cancelada");player.pause(); repository.prefs.edit().remove("sleepDeadline").apply(); }
            applyOffsets(); applyAdSkips(); save(); handler.postDelayed(this, 1000);
        }
    };
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @Override public void onCreate() {
        super.onCreate(); repository = new Repository(this);
        android.content.IntentFilter noisy=new android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(noisyReceiver,noisy,android.content.Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisyReceiver,noisy);
        AdSegments.prefs(this).edit().remove("lastSkipId").apply();
        GeminiKeyStore.prefs(this).edit().putBoolean("preparing",false).remove("preparingId").remove("preparingStatus").apply();
        GeminiKeyStore.prefs(this).registerOnSharedPreferenceChangeListener(geminiChanged);
        DefaultMediaNotificationProvider notificationProvider = new DefaultMediaNotificationProvider.Builder(this).build();
        notificationProvider.setSmallIcon(R.drawable.ic_notification_onda);
        setMediaNotificationProvider(notificationProvider);
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        applyPreferences(); repository.prefs.registerOnSharedPreferenceChangeListener(preferencesChanged);
        sessionPlayer=new GeminiPlaybackGate(player,new GeminiPlaybackGate.Owner(){
            public boolean automatic(){return GeminiPreparation.enabled(PlaybackService.this);}
            public boolean needsPreparation(){return PlaybackService.this.needsPreparation();}
            public void requestPlay(){PlaybackService.this.requestPlay();}
            public void cancelPreparation(){PlaybackService.this.cancelPreparation("Preparación cancelada. Puedes pulsar Play para reintentar.");}
            public void changingItem(String id){if(!id.equals(preparationId))PlaybackService.this.cancelPreparation("");if(player.getCurrentMediaItem()==null || !id.equals(player.getCurrentMediaItem().mediaId))bypassId="";}
        });
        setForegroundServiceTimeoutMs(30*60*1000L);
        player.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                if(item==null || !item.mediaId.equals(lastAdId))clearLastAd();
                if(item==null || !item.mediaId.equals(preparationId))cancelPreparation("");
                queuedFrom="";
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
                else if(state==Player.STATE_ENDED && GeminiPreparation.enabled(PlaybackService.this) && !previousId.equals(queuedFrom)) {String finished=previousId;queuedFrom=finished;handler.post(()->advancePreparedQueue(finished));}
            }
        });
        PendingIntent activity = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, sessionPlayer).setSessionActivity(activity).setMediaButtonPreferences(notificationButtons()).setCallback(new MediaSession.Callback() {
            @Override public MediaSession.ConnectionResult onConnect(MediaSession session, MediaSession.ControllerInfo info) {
                SessionCommands commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(new SessionCommand(JUMP_BACK, android.os.Bundle.EMPTY)).add(new SessionCommand(JUMP_FORWARD, android.os.Bundle.EMPTY)).build();
                Player.Commands playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS;
                if (session.isMediaNotificationController(info)) playerCommands = playerCommands.buildUpon().remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).build();
                if (getPackageName().equals(info.getPackageName())) commands = commands.buildUpon().add(new SessionCommand(PREPARE_RESTORE, android.os.Bundle.EMPTY)).add(new SessionCommand(UNDO_AD,android.os.Bundle.EMPTY)).add(new SessionCommand(CANCEL_PREPARATION,android.os.Bundle.EMPTY)).add(new SessionCommand(PLAY_WITHOUT_ANALYSIS,android.os.Bundle.EMPTY)).build();
                return new MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).setAvailablePlayerCommands(playerCommands).build();
            }
            @Override public com.google.common.util.concurrent.ListenableFuture<SessionResult> onCustomCommand(MediaSession session, MediaSession.ControllerInfo info, SessionCommand command, android.os.Bundle args) {
                if (JUMP_BACK.equals(command.customAction) || JUMP_FORWARD.equals(command.customAction)) return com.google.common.util.concurrent.Futures.immediateFuture(notificationJump(command.customAction));
                if(CANCEL_PREPARATION.equals(command.customAction) && getPackageName().equals(info.getPackageName())){cancelPreparation("Preparación cancelada. Puedes pulsar Play para reintentar.");player.pause();return com.google.common.util.concurrent.Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));}
                if(PLAY_WITHOUT_ANALYSIS.equals(command.customAction) && getPackageName().equals(info.getPackageName())){cancelPreparation("");MediaItem item=player.getCurrentMediaItem();if(item==null)return com.google.common.util.concurrent.Futures.immediateFuture(new SessionResult(SessionError.ERROR_INVALID_STATE));bypassId=item.mediaId;player.prepare();player.play();return com.google.common.util.concurrent.Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));}
                if(UNDO_AD.equals(command.customAction) && getPackageName().equals(info.getPackageName()))return com.google.common.util.concurrent.Futures.immediateFuture(undoAd());
                if (PREPARE_RESTORE.equals(command.customAction) && getPackageName().equals(info.getPackageName())) { prepareForRestore(); return com.google.common.util.concurrent.Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS)); }
                return com.google.common.util.concurrent.Futures.immediateFuture(new SessionResult(SessionError.ERROR_NOT_SUPPORTED));
            }
        }).build();
        handler.post(checkpoint);
    }
    void prepareForRestore() { cancelPreparation("");player.pause(); save(); player.clearMediaItems(); repository.prefs.edit().remove("sleepEpisode").remove("sleepDeadline").remove("activeEpisode").apply(); }
    private boolean audioItem(MediaItem item){return item!=null && (item.mediaMetadata.extras==null || !item.mediaMetadata.extras.getBoolean("video",false));}
    boolean needsPreparation(){MediaItem item=player.getCurrentMediaItem();if(!GeminiPreparation.enabled(this) || !audioItem(item) || item.mediaId.equals(bypassId))return false;LibraryEntry entry=repository.entry(item.mediaId);return entry==null || GeminiPreparation.readyUri(this,repository,entry)==null;}
    void requestPlay(){
        MediaItem item=player.getCurrentMediaItem();if(item==null || destroyed)return;
        if(preparing && item.mediaId.equals(preparationId))return;
        if(!needsPreparation()){startReadyPlayback();return;}
        player.pause();LibraryEntry entry=repository.entry(item.mediaId);
        if(entry==null){preparationStatus(item.mediaId,"No se encontró el episodio. Vuelve a seleccionarlo.",false);return;}
        if(!GeminiKeyStore.has(this) || GeminiKeyStore.prefs(this).getString("model","").isEmpty()){preparationStatus(item.mediaId,"Añade la clave y el modelo en Ajustes → Anuncios · Gemini Beta.",false);return;}
        cancelPreparation("");preparing=true;preparationId=item.mediaId;final int expected=++preparationGeneration;final String id=item.mediaId;
        preparationStatus(id,"Gemini está preparando el episodio. La reproducción empezará al terminar…",true);
        handler.postDelayed(()->{if(expected==preparationGeneration && preparing)cancelPreparation("La preparación superó 30 minutos. Puedes reintentar o escuchar sin análisis.");},30*60*1000L);
        try {
            preparingNotification();android.os.PowerManager power=getSystemService(android.os.PowerManager.class);if(power!=null){preparationLock=power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"Onda:GeminiPreparation");preparationLock.acquire(30*60*1000L);}
            GeminiPreparation.Work work=preparationFactory.get();preparationWork=work;
            preparationTask=preparationWorker.submit(()->{try {android.net.Uri uri=work.run(entry,value->handler.post(()->{if(expected==preparationGeneration && preparing && !destroyed){preparationStatus(id,value,true);preparingNotification();}}));handler.post(()->{
                if(expected!=preparationGeneration || destroyed || !preparing)return;
                MediaItem selected=player.getCurrentMediaItem();if(selected==null || !selected.mediaId.equals(id)){cancelPreparation("");return;}
                finishPreparation();preparationStatus(id,"Análisis completo. Iniciando el episodio con los saltos preparados.",false);
                long position=player.getCurrentPosition();player.setMediaItem(selected.buildUpon().setUri(uri).build(),position);startReadyPlayback();
            });}catch(Exception error){handler.post(()->{if(expected!=preparationGeneration || destroyed)return;finishPreparation();String message=error instanceof java.io.InterruptedIOException?"Preparación cancelada":error instanceof java.io.IOException?error.getMessage():"No se pudo completar la preparación. Comprueba la clave, el modelo y la conexión.";preparationStatus(id,message,false);});}});
        }catch(Exception error){finishPreparation();preparationStatus(id,"Android no permitió iniciar la preparación. Abre Onda y pulsa Play para reintentar.",false);}
    }
    private void startReadyPlayback(){
        MediaItem item=player.getCurrentMediaItem();if(item==null)return;
        LibraryEntry entry=repository.entry(item.mediaId);AdSegments.Record record=AdSegments.load(this,item.mediaId);android.net.Uri uri=entry==null?null:GeminiPreparation.readyUri(this,repository,entry);
        if(audioItem(item) && uri!=null && !item.mediaId.equals(bypassId)){
            long position=player.getCurrentPosition();if(item.localConfiguration==null || !uri.equals(item.localConfiguration.uri))player.setMediaItem(item.buildUpon().setUri(uri).build(),position);
            AdSegments.Segment segment=record.at(position,record.duration);
            if(segment!=null && segment.end<record.duration){lastAdId=item.mediaId;lastAdFrom=position;lastAdStart=segment.start;player.seekTo(segment.end);AdSegments.prefs(this).edit().putString("lastSkipId",item.mediaId).apply();}
        }
        player.prepare();player.play();
    }
    private void advancePreparedQueue(String finished){
        MediaItem item=player.getCurrentMediaItem();if(destroyed || item==null || !item.mediaId.equals(finished) || player.getPlaybackState()!=Player.STATE_ENDED)return;
        long deadline=repository.prefs.getLong("sleepDeadline",0);if(deadline>0 && android.os.SystemClock.elapsedRealtime()>=deadline)return;
        java.util.List<LibraryEntry> queue=repository.queue();if(queue.isEmpty())return;LibraryEntry next=queue.get(0);repository.remember(next);long position=repository.prefs.getBoolean("resumePlayback",true)?repository.position(next.episode.id):0;sessionPlayer.setMediaItem(next.mediaItem(this,false),position);sessionPlayer.prepare();sessionPlayer.play();
    }
    private void preparationStatus(String id,String value,boolean busy){GeminiKeyStore.prefs(this).edit().putString("preparingId",id).putString("preparingStatus",value==null?"Preparación fallida":value).putBoolean("preparing",busy).apply();}
    private void finishPreparation(){preparing=false;preparationWork=null;preparationTask=null;if(preparationLock!=null && preparationLock.isHeld())preparationLock.release();preparationLock=null;stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(android.app.NotificationManager.class).cancel(PREPARING_NOTIFICATION);if(session!=null && !destroyed)super.onUpdateNotification(session,player.getPlayWhenReady());}
    void cancelPreparation(String message){
        if(!preparing)return;preparationGeneration++;GeminiPreparation.Work work=preparationWork;if(work!=null)work.cancel();java.util.concurrent.Future<?> task=preparationTask;if(task!=null)task.cancel(true);String id=preparationId;finishPreparation();preparationStatus(id,message,false);preparationId="";
    }
    private void preparingNotification(){
        android.app.NotificationManager manager=getSystemService(android.app.NotificationManager.class);manager.createNotificationChannel(new android.app.NotificationChannel("gemini_preparation","Preparación con Gemini",android.app.NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,1903,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancel=PendingIntent.getService(this,1904,new Intent(this,PlaybackService.class).setAction(CANCEL_PREPARATION),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification notification=new android.app.Notification.Builder(this,"gemini_preparation").setSmallIcon(R.drawable.ic_notification_onda).setContentTitle("Gemini está preparando tu podcast").setContentText(GeminiKeyStore.prefs(this).getString("preparingStatus","")).setStyle(new android.app.Notification.BigTextStyle().bigText(GeminiKeyStore.prefs(this).getString("preparingStatus",""))).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(0,0,true).addAction(new android.app.Notification.Action.Builder(null,"Cancelar",cancel).build()).build();
        if(android.os.Build.VERSION.SDK_INT>=29)startForeground(PREPARING_NOTIFICATION,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(PREPARING_NOTIFICATION,notification);
    }
    @Override public void onUpdateNotification(MediaSession mediaSession,boolean startInForeground){if(preparing)preparingNotification();else super.onUpdateNotification(mediaSession,startInForeground);}
    @Override public boolean isPlaybackOngoing(){return preparing || super.isPlaybackOngoing();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null && CANCEL_PREPARATION.equals(intent.getAction())){cancelPreparation("Preparación cancelada. Pulsa Play para reintentar.");player.pause();return START_NOT_STICKY;}return super.onStartCommand(intent,flags,startId);}
    @Override public void onTimeout(int startId,int type){cancelPreparation("Android detuvo la preparación por tiempo agotado.");player.pause();stopSelf();}
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
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    SessionResult notificationJump(String action) {
        if (!JUMP_BACK.equals(action) && !JUMP_FORWARD.equals(action)) return new SessionResult(SessionError.ERROR_NOT_SUPPORTED);
        if (player == null || player.getCurrentMediaItem() == null || !player.isCurrentMediaItemSeekable()) return new SessionResult(SessionError.ERROR_INVALID_STATE);
        long target = Math.max(0, player.getCurrentPosition() + (JUMP_FORWARD.equals(action) ? 1 : -1) * SkipRules.seconds(repository.prefs, JUMP_FORWARD.equals(action)) * 1000L);
        if (player.getDuration() > 0) target = Math.min(target, player.getDuration());
        player.seekTo(target); save(); return new SessionResult(SessionResult.RESULT_SUCCESS);
    }
    private void clearLastAd(){lastAdId="";lastAdFrom=-1;lastAdStart=-1;AdSegments.prefs(this).edit().remove("lastSkipId").apply();}
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    void applyAdSkips(){
        MediaItem item=player.getCurrentMediaItem();if(item==null || !player.isPlaying() || !player.isCurrentMediaItemSeekable() || item.localConfiguration==null)return;
        String text=AdSegments.prefs(this).getString(AdSegments.key(item.mediaId),"");
        if(!item.mediaId.equals(adCacheId) || !text.equals(adCacheText)){adCacheId=item.mediaId;adCacheText=text;adCache=AdSegments.decode(text);}
        if(adCache==null || !adCache.enabled)return;
        LibraryEntry entry=repository.entry(item.mediaId);if(entry==null || !GeminiPreparation.valid(this,repository,entry,adCache))return;
        android.net.Uri local=GeminiPreparation.readyUri(this,repository,entry);if(local==null || !local.equals(item.localConfiguration.uri))return;
        AdSegments.Segment segment=adCache.at(player.getCurrentPosition(),player.getDuration());if(segment==null)return;
        long target=Math.min(segment.end,player.getDuration()-1);if(target<=player.getCurrentPosition())return;
        lastAdId=item.mediaId;lastAdFrom=player.getCurrentPosition();lastAdStart=segment.start;
        player.seekTo(target);save();AdSegments.prefs(this).edit().putString("lastSkipId",item.mediaId).apply();
    }
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    SessionResult undoAd(){
        MediaItem item=player.getCurrentMediaItem();if(item==null || !item.mediaId.equals(lastAdId) || lastAdFrom<0 || !player.isCurrentMediaItemSeekable())return new SessionResult(SessionError.ERROR_INVALID_STATE);
        try{AdSegments.ignore(this,item.mediaId,lastAdStart);player.seekTo(Math.min(lastAdFrom,Math.max(0,player.getDuration()-1)));save();clearLastAd();return new SessionResult(SessionResult.RESULT_SUCCESS);}
        catch(Exception e){return new SessionResult(SessionError.ERROR_UNKNOWN);}
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
        destroyed=true;unregisterReceiver(noisyReceiver);cancelPreparation("");preparationWorker.shutdownNow();GeminiKeyStore.prefs(this).unregisterOnSharedPreferenceChangeListener(geminiChanged);
        save(); clearLastAd(); repository.prefs.edit().remove("activeEpisode").apply(); handler.removeCallbacksAndMessages(null);
        repository.prefs.unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        if (session != null) session.release(); if (player != null) player.release(); super.onDestroy();
    }
}
