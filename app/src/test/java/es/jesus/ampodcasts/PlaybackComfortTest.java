package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PlaybackComfortTest {
    @Test public void skipsPreserveResumeAndRequireKnownNonOverlappingDuration() {
        assertEquals(60000,SkipRules.initial(0,60000,3600000));
        assertEquals(15000,SkipRules.initial(15000,60000,3600000));
        assertEquals(0,SkipRules.initial(0,60000,-1)); assertEquals(0,SkipRules.initial(0,60000,30000));
        assertFalse(SkipRules.finish(3500000,3600000,60000,30000)); assertTrue(SkipRules.finish(3570000,3600000,60000,30000));
        assertFalse(SkipRules.finish(30000,60000,40000,30000)); assertFalse(SkipRules.finish(10000,-1,0,30000)); assertFalse(SkipRules.finish(3600000,3600000,0,0));
    }
    @Test public void marksAndProgramOffsetsSurviveBackupRestoreWithEpisodeIsolation() throws Exception {
        Repository r=new Repository(RuntimeEnvironment.getApplication()); Podcast p=new Podcast("Programa","https://example.com/rss","","Descripción");r.addPodcast(p);
        BookmarkStore.add(r.prefs,"episode-a","Tema B",120000);BookmarkStore.add(r.prefs,"episode-a","Tema A",10000);BookmarkStore.add(r.prefs,"episode-b","Otro tema",30000);
        r.prefs.edit().putLong(ProgramOptions.key("skipStart",p.feed),60).putLong(ProgramOptions.key("skipEnd",p.feed),30).putInt("jumpForward",60).commit();
        ByteArrayOutputStream out=new ByteArrayOutputStream(); BackupStore.export(RuntimeEnvironment.getApplication(),out); r.prefs.edit().clear().commit();BackupStore.restore(RuntimeEnvironment.getApplication(),BackupStore.validate(new ByteArrayInputStream(out.toByteArray())));
        assertEquals("Tema A",BookmarkStore.list(r.prefs,"episode-a").get(0).name); assertEquals(1,BookmarkStore.list(r.prefs,"episode-b").size());assertEquals(60000,SkipRules.offset(r.prefs,"skipStart",p.feed));assertEquals(0,SkipRules.offset(r.prefs,"skipStart","https://example.com/other"));assertEquals(60,SkipRules.seconds(r.prefs,true));assertEquals("Descripción",r.podcasts().get(0).description);
        BookmarkStore.remove(r.prefs,"episode-a",BookmarkStore.list(r.prefs,"episode-a").get(0));assertEquals(1,BookmarkStore.list(r.prefs,"episode-a").size());
    }
    @Test public void invalidComfortSettingsRejectWholeBackup() throws Exception {
        String[] values={"\"jumpForward\":12","\"skipEnd:"+Repository.key("feed")+"\":1801","\"marks:"+Repository.key("episode")+"\":\"[{\\\"name\\\":\\\"Tema\\\",\\\"position\\\":-1}]\""};
        for(String value:values)try { BackupStore.validate(new ByteArrayInputStream(("{\"app\":\"Onda\",\"version\":1,\"preferences\":{"+value+"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));fail("Invalid settings accepted");}catch(Exception expected){}
    }
    @Test public void descriptionComesFromRssAndOldPodcastJsonStillLoads() throws Exception {
        Repository.FeedResult feed=Repository.parseFeed(new ByteArrayInputStream("<rss><channel><title>Uno</title><description><![CDATA[Hola <b>mundo</b>]]></description></channel></rss>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertEquals("Hola mundo",feed.description);
        assertEquals("",Podcast.from(new org.json.JSONObject("{\"title\":\"Antiguo\",\"feed\":\"https://example.com/rss\"}")).description);
    }
}
