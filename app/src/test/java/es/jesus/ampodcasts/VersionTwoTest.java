package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class VersionTwoTest {
    @androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
    @Test public void serviceAppliesProgramOverridesWhenChangingPodcast() throws Exception {
        Repository r=new Repository(RuntimeEnvironment.getApplication());Podcast a=new Podcast("Uno","https://example.com/a"),b=new Podcast("Dos","https://example.com/b");r.addPodcast(a);r.addPodcast(b);
        LibraryEntry one=new LibraryEntry(new Episode("one","Uno","","https://example.com/one.mp3"),a),two=new LibraryEntry(new Episode("two","Dos","","https://example.com/two.mp3"),b);r.remember(one);r.remember(two);
        r.prefs.edit().putFloat("speed",1.25f).putBoolean("skipSilence",false).putFloat(ProgramOptions.key("programSpeed",a.feed),1.75f).putBoolean(ProgramOptions.key("programSilence",a.feed),true).commit();
        org.robolectric.android.controller.ServiceController<PlaybackService> service=Robolectric.buildService(PlaybackService.class).create();
        try{java.lang.reflect.Field f=PlaybackService.class.getDeclaredField("player");f.setAccessible(true);androidx.media3.exoplayer.ExoPlayer player=(androidx.media3.exoplayer.ExoPlayer)f.get(service.get());player.setMediaItem(one.mediaItem(service.get(),false));assertEquals(1.75f,player.getPlaybackParameters().speed,0);assertTrue(player.getSkipSilenceEnabled());player.setMediaItem(two.mediaItem(service.get(),false));assertEquals(1.25f,player.getPlaybackParameters().speed,0);assertFalse(player.getSkipSilenceEnabled());}finally{service.destroy();}
    }
    @Test public void automaticBudgetIsPortableAndRejectsInvalidValues() throws Exception {
        Repository r=new Repository(RuntimeEnvironment.getApplication());r.prefs.edit().putLong("automaticBudget",500_000_000L).commit();ByteArrayOutputStream out=new ByteArrayOutputStream();BackupStore.export(RuntimeEnvironment.getApplication(),out);Map<String,Object> values=BackupStore.validate(new ByteArrayInputStream(out.toByteArray()));r.prefs.edit().clear().commit();BackupStore.restore(RuntimeEnvironment.getApplication(),values);assertEquals(500_000_000L,r.prefs.getLong("automaticBudget",0));assertTrue(r.automaticSpaceAvailable());r.prefs.edit().putLong("automaticBudget",1).commit();assertFalse(r.automaticSpaceAvailable());
        try{BackupStore.validate(new ByteArrayInputStream("{\"app\":\"Onda\",\"version\":1,\"preferences\":{\"automaticBudget\":123}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));fail();}catch(IOException expected){}
    }
    @Test public void perProgramPreferencesFallbackAndSurvivePortableBackup() throws Exception {
        Repository r=new Repository(RuntimeEnvironment.getApplication());String feed="https://example.com/rss";r.addPodcast(new Podcast("Uno",feed));
        r.prefs.edit().putFloat("speed",1.25f).putBoolean("skipSilence",true).putFloat(ProgramOptions.key("programSpeed",feed),1.75f).putBoolean(ProgramOptions.key("programSilence",feed),false).putBoolean("sleepFade",false).commit();
        assertEquals(1.75f,ListeningPreferences.speed(r.prefs,feed),0);assertEquals(1.25f,ListeningPreferences.speed(r.prefs,"other"),0);assertFalse(ListeningPreferences.silence(r.prefs,feed));assertTrue(ListeningPreferences.silence(r.prefs,"other"));
        ByteArrayOutputStream out=new ByteArrayOutputStream();BackupStore.export(RuntimeEnvironment.getApplication(),out);Map<String,Object> values=BackupStore.validate(new ByteArrayInputStream(out.toByteArray()));r.prefs.edit().clear().commit();BackupStore.restore(RuntimeEnvironment.getApplication(),values);
        assertEquals(1.75f,ListeningPreferences.speed(r.prefs,feed),0);assertFalse(ListeningPreferences.silence(r.prefs,feed));assertFalse(r.prefs.getBoolean("sleepFade",true));
        r.prefs.edit().remove(ProgramOptions.key("programSpeed",feed)).commit();assertEquals(1.25f,ListeningPreferences.speed(r.prefs,feed),0);
    }
    @Test public void sleepFadeHasNoEffectOutsideLastThirtySeconds() {
        assertEquals(1f,ListeningPreferences.sleepVolume(-1,true),0);assertEquals(1f,ListeningPreferences.sleepVolume(60000,true),0);assertEquals(.5f,ListeningPreferences.sleepVolume(15000,true),0);assertEquals(0f,ListeningPreferences.sleepVolume(0,true),0);assertEquals(1f,ListeningPreferences.sleepVolume(0,false),0);
    }
    @Test public void timedTranscriptsSupportVttSrtJsonAndAccentInsensitiveSearch() throws Exception {
        String vtt="WEBVTT\n\n00:00:01.000 --> 00:00:04.000\nLa <b>música</b> de hoy\n\n00:00:05.500 --> 00:00:08.000 align:start\nSegundo tema\n";
        List<TranscriptStore.Cue> cues=TranscriptStore.parse(vtt,"text/vtt");assertEquals(2,cues.size());assertEquals(1000,cues.get(0).start);assertEquals("La música de hoy",cues.get(0).text);assertEquals(1,TranscriptStore.search(cues,"MUSICA").size());
        assertEquals(1500,TranscriptStore.parse("1\r\n00:00:01,500 --> 00:00:04,000\r\nHola\r\n","application/x-subrip").get(0).start);
        assertEquals(2000,TranscriptStore.parse("{\"segments\":[{\"startTime\":2,\"endTime\":4,\"body\":\"Tema\"}]}","application/json").get(0).start);
        assertEquals(-1,TranscriptStore.time("00:60:00.000"));assertEquals(-1,TranscriptStore.time("garbage"));
        try{TranscriptStore.parse("00:00:04.000 --> 00:00:01.000\nInvalid","text/vtt");fail();}catch(IOException expected){}
    }
    @Test public void rssTranscriptMetadataSurvivesCacheAndOldEpisodeJson() throws Exception {
        String rss="<rss xmlns:podcast=\"https://podcastindex.org/namespace/1.0\"><channel><item><title>Uno</title><guid>a</guid><enclosure url=\"https://example.com/a.mp3\" type=\"audio/mpeg\"/><podcast:transcript url=\"http://example.com/a.vtt\" type=\"text/vtt\"/><podcast:transcript url=\"https://example.com/a.vtt\" type=\"text/vtt\"/></item></channel></rss>";
        Episode e=Repository.parse(new ByteArrayInputStream(rss.getBytes(java.nio.charset.StandardCharsets.UTF_8))).get(0);assertEquals("https://example.com/a.vtt",e.transcriptUrl);assertEquals("text/vtt",Episode.from(e.json()).transcriptType);
        assertEquals("",Episode.from(new org.json.JSONObject("{\"id\":\"old\",\"title\":\"Old\",\"date\":\"\",\"url\":\"https://example.com/o.mp3\"}")).transcriptUrl);
    }
    @Test public void homeShowsOnlyUnfinishedProgressAndNewestUnplayedEpisodes() throws Exception {
        android.content.Context c=RuntimeEnvironment.getApplication();Repository r=new Repository(c);Podcast p=new Podcast("Uno","https://example.com/rss");r.addPodcast(p);
        Episode older=new Episode("a","Old","Fri, 09 Oct 2026 10:00:00 +0000","https://example.com/a.mp3"), newer=new Episode("b","New","Sat, 10 Oct 2026 10:00:00 +0000","https://example.com/b.mp3");
        try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"episodes-"+Repository.key(p.feed)+".json"))){out.write(new org.json.JSONArray().put(older.json()).put(newer.json()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        assertEquals("b",HomeFeed.latest(c,r).get(0).episode.id);r.remember(new LibraryEntry(older,p));r.savePosition("a",10000);assertEquals(1,HomeFeed.continuing(r).size());assertEquals(1,HomeFeed.latest(c,r).size());r.setListened("a",true);assertTrue(HomeFeed.continuing(r).isEmpty());
    }
}
