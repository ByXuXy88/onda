package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.content.*;
import android.net.Uri;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,33})
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class GeminiPreparationTest {
    private LibraryEntry entry(Repository r,String id) throws Exception {Podcast podcast=new Podcast("Test","https://example.com/rss");r.addPodcast(podcast);LibraryEntry e=new LibraryEntry(new Episode(id,"Audio "+id,"","https://example.com/"+id+".mp3"),podcast);r.remember(e);return e;}
    private Player gate(PlaybackService service) throws Exception {java.lang.reflect.Field f=PlaybackService.class.getDeclaredField("sessionPlayer");f.setAccessible(true);return (Player)f.get(service);}
    private ExoPlayer player(PlaybackService service) throws Exception {java.lang.reflect.Field f=PlaybackService.class.getDeclaredField("player");f.setAccessible(true);return (ExoPlayer)f.get(service);}
    private void enable(Context c){GeminiKeyStore.prefs(c).edit().putBoolean(GeminiPreparation.ENABLED,true).putString("encrypted_key","fake-for-injected-work").putString("model","gemini-test").commit();}
    private Uri cache(Context c,LibraryEntry e,boolean enabled) throws Exception {File dir=GeminiPreparation.directory(c);dir.mkdirs();File f=new File(dir,UUID.randomUUID()+".audio");try(OutputStream out=new FileOutputStream(f)){java.nio.ByteBuffer header=java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);header.put("RIFF".getBytes("US-ASCII")).putInt(2_880_036).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(2_880_000);out.write(header.array());out.write(new byte[2_880_000]);}AdSegments.save(c,e.episode.id,new AdSegments.Record(0,180000,e.episode.url,"gemini-test",Arrays.asList(new AdSegments.Segment(0,20000,"Publicidad")),new HashSet<>(),enabled,f.getName(),f.length()));return Uri.fromFile(f);}
    @Test public void playWaitsForWholeAnalysisThenStartsWithOpeningAdAlreadySkipped() throws Exception {
        ServiceController<PlaybackService> service=Robolectric.buildService(PlaybackService.class).create();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        try {PlaybackService s=service.get();Repository r=new Repository(s);LibraryEntry e=entry(r,"wait");enable(s);Shadows.shadowOf(Looper.getMainLooper()).idle();
            s.preparationFactory=()->new GeminiPreparation.Work(){public Uri run(LibraryEntry selected,GeminiPreparation.Progress progress) throws Exception {calls.incrementAndGet();entered.countDown();release.await();return cache(s,selected,true);}public void cancel(){release.countDown();}};
            Player gate=gate(s);gate.setMediaItem(e.mediaItem(s,false));gate.prepare();assertEquals(Player.STATE_IDLE,player(s).getPlaybackState());gate.play();assertTrue(entered.await(2,TimeUnit.SECONDS));assertFalse(player(s).getPlayWhenReady());assertTrue(GeminiPreparation.busy(s,e.episode.id));gate.play();assertEquals(1,calls.get());
            release.countDown();await(()->playerUnchecked(s).getPlayWhenReady());assertFalse(GeminiPreparation.busy(s,e.episode.id));assertTrue(player(s).getCurrentPosition()>=20000);assertEquals(e.episode.id,AdSegments.prefs(s).getString("lastSkipId",""));assertEquals(GeminiPreparation.readyUri(s,r,e),player(s).getCurrentMediaItem().localConfiguration.uri);
            gate.pause();gate.play();assertEquals(1,calls.get()); // cached bytes and analysis, no second API call
        }finally{release.countDown();service.destroy();}
    }
    @Test public void cancelAndChangingEpisodeCannotStartAStaleCompletion() throws Exception {
        ServiceController<PlaybackService> service=Robolectric.buildService(PlaybackService.class).create();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try {PlaybackService s=service.get();Repository r=new Repository(s);LibraryEntry first=entry(r,"first"),second=entry(r,"second");enable(s);Shadows.shadowOf(Looper.getMainLooper()).idle();
            s.preparationFactory=()->new GeminiPreparation.Work(){public Uri run(LibraryEntry selected,GeminiPreparation.Progress progress) throws Exception {entered.countDown();try{release.await();}catch(InterruptedException ignored){}return Uri.parse("file:///stale.audio");}public void cancel(){release.countDown();}};
            Player gate=gate(s);gate.setMediaItem(first.mediaItem(s,false));gate.play();assertTrue(entered.await(2,TimeUnit.SECONDS));gate.setMediaItem(second.mediaItem(s,false));release.countDown();Thread.sleep(100);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(second.episode.id,player(s).getCurrentMediaItem().mediaId);assertFalse(player(s).getPlayWhenReady());assertFalse(GeminiPreparation.busy(s,first.episode.id));
        }finally{release.countDown();service.destroy();}
    }
    @Test public void failedAnalysisNeverPlaysOrAutomaticallyRetries() throws Exception {
        ServiceController<PlaybackService> service=Robolectric.buildService(PlaybackService.class).create();AtomicInteger calls=new AtomicInteger();
        try {PlaybackService s=service.get();LibraryEntry e=entry(new Repository(s),"failure");enable(s);Shadows.shadowOf(Looper.getMainLooper()).idle();s.preparationFactory=()->new GeminiPreparation.Work(){public Uri run(LibraryEntry selected,GeminiPreparation.Progress progress) throws Exception {calls.incrementAndGet();throw new IOException("Cuota agotada");}public void cancel(){}};
            Player gate=gate(s);gate.setMediaItem(e.mediaItem(s,false));gate.play();await(()->!GeminiPreparation.busy(s,e.episode.id));assertEquals("Cuota agotada",GeminiKeyStore.prefs(s).getString("preparingStatus",""));assertFalse(player(s).getPlayWhenReady());Thread.sleep(50);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,calls.get());
        }finally{service.destroy();}
    }
    @Test public void cachedAnalysisIsBoundToExactBytesAndKeepsUndoExclusionsAndDisabledState() throws Exception {
        Context c=RuntimeEnvironment.getApplication();Repository r=new Repository(c);LibraryEntry e=entry(r,"cached");Uri uri=cache(c,e,true);AdSegments.Record record=AdSegments.load(c,e.episode.id);assertTrue(GeminiPreparation.valid(c,r,e,record));assertEquals(uri,r.playbackUri(e.episode));
        AdSegments.ignore(c,e.episode.id,0);AdSegments.enabled(c,e.episode.id,false);record=AdSegments.load(c,e.episode.id);assertFalse(record.enabled);assertTrue(record.ignored.contains(0L));assertEquals(uri,GeminiPreparation.readyUri(c,r,e));
        LibraryEntry changed=new LibraryEntry(new Episode(e.episode.id,"Other","","https://example.com/changed.mp3"),e.podcast);assertFalse(GeminiPreparation.valid(c,r,changed,record));File f=GeminiPreparation.file(c,record.preparedFile);try(OutputStream out=new FileOutputStream(f,true)){out.write(1);}assertNull(GeminiPreparation.readyUri(c,r,e));assertNull(GeminiPreparation.file(c,"../../key"));
    }
    @Test public void clearingTemporaryAudioPreservesCurrentPlaybackAndInvalidatesOtherResults() throws Exception {
        Context c=RuntimeEnvironment.getApplication();Repository r=new Repository(c);LibraryEntry active=entry(r,"active"),other=entry(r,"other");cache(c,active,true);cache(c,other,true);GeminiPreparation.clear(c,active.episode.id);assertNotNull(GeminiPreparation.readyUri(c,r,active));assertNull(GeminiPreparation.readyUri(c,r,other));
        assertEquals("audio/mpeg",GeminiPreparation.mime("audio/mpeg; charset=binary",active.episode.url));assertEquals("audio/m4a",GeminiPreparation.mime("audio/mp4","https://example.com/audio"));assertEquals("audio/ogg",GeminiPreparation.mime("application/octet-stream","https://example.com/audio.ogg?token=test"));
    }
    @Test public void unfinishedCacheSurvivesMoreThanThreeEpisodesAndCompletionOnlyRemovesPlayedBytes() throws Exception {
        Context c=RuntimeEnvironment.getApplication();Repository r=new Repository(c);List<LibraryEntry> pending=new ArrayList<>();List<Uri> uris=new ArrayList<>();
        for(int i=0;i<5;i++){LibraryEntry e=entry(r,"pending-"+i);pending.add(e);uris.add(cache(c,e,true));r.savePosition(e.episode.id,45000);}
        File orphan=new File(GeminiPreparation.directory(c),UUID.randomUUID()+".audio");try(OutputStream out=new FileOutputStream(orphan)){out.write(1);}
        GeminiPreparation.prune(c,"",pending.get(4).episode.id);
        assertFalse(orphan.exists());for(int i=0;i<5;i++)assertEquals(uris.get(i),GeminiPreparation.readyUri(c,r,pending.get(i)));
        LibraryEntry finished=pending.get(0);GeminiPreparation.completed(c,finished.episode.id,Uri.parse("file:///different.audio"));assertEquals(uris.get(0),GeminiPreparation.readyUri(c,r,finished));
        GeminiPreparation.completed(c,finished.episode.id,uris.get(0));assertNull(GeminiPreparation.readyUri(c,r,finished));assertNotNull(AdSegments.load(c,finished.episode.id));
        for(int i=1;i<5;i++){assertEquals(uris.get(i),GeminiPreparation.readyUri(c,r,pending.get(i)));assertEquals(45000,r.position(pending.get(i).episode.id));}
    }
    // Robolectric's API 28 AudioTrack shadow does not complete the silent source at EOF.
    // Exercise real ExoPlayer end-of-playback transitions with the API 33 audio shadow.
    @Test @Config(sdk=33) public void queueWaitsForItsOwnAnalysisInsteadOfStartingAnUnpreparedStream() throws Exception {
        queueCase(false);
    }
    @Test @Config(sdk=33) public void sleepUntilEpisodeEndDoesNotPrepareOrStartTheNextEpisode() throws Exception {
        queueCase(true);
    }
    private void queueCase(boolean sleep) throws Exception {
        ServiceController<PlaybackService> service=Robolectric.buildService(PlaybackService.class).create();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        try {PlaybackService s=service.get();Repository r=new Repository(s);LibraryEntry first=entry(r,"queue-first"),next=entry(r,"queue-next");cache(s,first,true);r.enqueue(next);enable(s);Shadows.shadowOf(Looper.getMainLooper()).idle();if(sleep)r.prefs.edit().putString("sleepEpisode",first.episode.id).commit();
            s.preparationFactory=()->new GeminiPreparation.Work(){public Uri run(LibraryEntry selected,GeminiPreparation.Progress progress) throws Exception {assertEquals(next.episode.id,selected.episode.id);calls.incrementAndGet();entered.countDown();release.await();throw new InterruptedIOException("cancel");}public void cancel(){release.countDown();}};
            Player gate=gate(s);gate.setMediaItems(Arrays.asList(first.mediaItem(s,false),next.mediaItem(s,false)),0,0);assertEquals(1,player(s).getMediaItemCount());
            androidx.media3.exoplayer.source.SilenceMediaSource source=new androidx.media3.exoplayer.source.SilenceMediaSource.Factory().setDurationUs(180000000).createMediaSource();source.updateMediaItem(first.mediaItem(s,false));player(s).setMediaSource(source);gate.prepare();await(()->playerUnchecked(s).isCurrentMediaItemSeekable());gate.play();await(()->playerUnchecked(s).isPlaying());player(s).seekTo(180000);
            if(sleep){await(()->playerUnchecked(s).getPlaybackState()==Player.STATE_ENDED);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,calls.get());assertEquals(first.episode.id,player(s).getCurrentMediaItem().mediaId);assertEquals(1,r.queue().size());}
            else {await(()->calls.get()==1);assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals(next.episode.id,player(s).getCurrentMediaItem().mediaId);assertFalse(player(s).getPlayWhenReady());assertTrue(GeminiPreparation.busy(s,next.episode.id));}
        assertNull(GeminiPreparation.readyUri(s,r,first));
        }finally{release.countDown();service.destroy();}
    }
    @Test public void mobileDataIsAllowedByDefaultAndWifiOnlyIsAnExplicitChoice() throws Exception {
        Context c=RuntimeEnvironment.getApplication();android.net.ConnectivityManager manager=c.getSystemService(android.net.ConnectivityManager.class);android.net.NetworkCapabilities caps=new android.net.NetworkCapabilities();Shadows.shadowOf(caps).addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);Shadows.shadowOf(caps).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);Shadows.shadowOf(manager).setNetworkCapabilities(manager.getActiveNetwork(),caps);
        GeminiPreparation.checkNetwork(c);GeminiKeyStore.prefs(c).edit().putBoolean(GeminiPreparation.WIFI,true).commit();try{GeminiPreparation.checkNetwork(c);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("wifi"));}
        Shadows.shadowOf(caps).addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI);GeminiPreparation.checkNetwork(c);
    }
    private ExoPlayer playerUnchecked(PlaybackService s){try{return player(s);}catch(Exception e){throw new RuntimeException(e);}}
    interface Ready{boolean get();}
    private void await(Ready ready) throws Exception {long deadline=System.nanoTime()+5_000_000_000L;while(!ready.get() && System.nanoTime()<deadline){Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idleFor(50,TimeUnit.MILLISECONDS);}assertTrue(ready.get());}
}
