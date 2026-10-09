package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateFeaturesTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private InputStream bytes(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    @Test public void timesUseHoursAfterSixtyMinutes() {
        assertEquals("59 min 59 s", TimeFormat.display(3599000, true)); assertEquals("1 h 0 min 00 s", TimeFormat.display(3600000, true)); assertEquals("2 h 4 min", TimeFormat.display(7448000, false)); assertEquals("0 min 00 s", TimeFormat.display(-1, true));
        assertEquals(7448000, TimeFormat.parse("2:04:08")); assertEquals(9048000, TimeFormat.parse("150:48")); assertEquals(0, TimeFormat.parse("bad")); assertEquals(0, TimeFormat.parse("NaN"));
    }
    @Test public void rssDurationArtworkAndChaptersSurviveCacheRoundTrip() throws Exception {
        String feed = "<rss xmlns:itunes='http://www.itunes.com/dtds/podcast-1.0.dtd' xmlns:podcast='https://podcastindex.org/namespace/1.0'><channel><title>Programa</title><itunes:image href='https://example.com/show.jpg'/><item><title>Vídeo</title><guid>a</guid><itunes:duration>1:24:08</itunes:duration><itunes:image href='https://example.com/episode.jpg'/><podcast:chapters url='https://example.com/chapters.json'/><enclosure url='https://example.com/a.mp4' type='video/mp4'/></item></channel></rss>";
        Repository.FeedResult parsed = Repository.parseFeed(bytes(feed)); Episode e = parsed.episodes.get(0); assertEquals(5048000, e.durationMs); assertEquals("https://example.com/episode.jpg", e.artwork); assertEquals("https://example.com/show.jpg", parsed.artwork); assertEquals(e.chaptersUrl, Episode.from(e.json()).chaptersUrl); assertEquals(e.artwork, new LibraryEntry(e, new Podcast("Programa", "https://example.com/rss", parsed.artwork)).artwork());
        assertEquals("https://example.com/show.jpg", new LibraryEntry(new Episode("old", "Anterior", "", "https://example.com/a.mp3"), new Podcast("Programa", "https://example.com/rss", parsed.artwork)).artwork());
    }
    @Test public void backupRestoresPortableStateWithoutDeviceDownloads() throws Exception {
        Repository r = new Repository(context); Podcast p = new Podcast("Programa", "https://example.com/rss"); r.addPodcast(p); LibraryEntry e = new LibraryEntry(new Episode("a", "Episodio", "", "https://example.com/a.mp3"), p); r.setFavorite(e, true); r.enqueue(e); r.savePosition("a", 3605000); r.prefs.edit().putLong("download:" + Repository.key("a"), 42).putLong("sleepDeadline", 123).putFloat("speed", 1.5f).commit();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); BackupStore.export(context, out); String json = out.toString("UTF-8"); assertFalse(json.contains("download:")); assertFalse(json.contains("sleepDeadline"));
        Map<String, Object> copy = BackupStore.validate(bytes(json)); r.setFavorite(e, false); r.savePosition("a", 0); BackupStore.restore(context, copy); assertTrue(r.favorite("a")); assertEquals(3605000, r.position("a")); assertEquals(1, r.queue().size()); assertEquals(42, r.prefs.getLong("download:" + Repository.key("a"), -1)); assertFalse(r.prefs.contains("sleepDeadline")); assertEquals(1.5f, r.prefs.getFloat("speed", 1), 0);
    }
    @Test public void corruptBackupDoesNotChangeLibrary() throws Exception {
        Repository r = new Repository(context); r.addPodcast(new Podcast("Programa", "https://example.com/rss"));
        for (String json : new String[]{"{}", "{\"app\":\"Onda\",\"version\":1,\"preferences\":{\"speed\":99}}", "{\"app\":\"Onda\",\"version\":1,\"preferences\":{\"podcasts\":\"[{}]\"}}"}) { try { BackupStore.validate(bytes(json)); fail("Invalid backup accepted"); } catch (Exception expected) { assertEquals(1, r.podcasts().size()); } }
    }
    @Test public void chaptersSortAndRejectNegativeTimesAndHiddenEntries() throws Exception {
        List<PlaybackTools.Chapter> list = PlaybackTools.parse("{\"chapters\":[{\"startTime\":60,\"title\":\"Segundo\"},{\"startTime\":0,\"title\":\"Inicio\"},{\"startTime\":-1},{\"startTime\":10,\"toc\":false}]}" ); assertEquals(2, list.size()); assertEquals("Inicio", list.get(0).title); assertEquals(60000, list.get(1).start);
    }
    @Test public void firstRefreshSetsBaselineWithoutDownloadingHistory() throws Exception {
        Podcast p = new Podcast("Programa", "https://example.com/rss"); Repository r = new Repository(context); r.addPodcast(p); r.prefs.edit().putBoolean(ProgramOptions.key("auto", p.feed), true).commit(); Episode e = new Episode("old", "Anterior", "", "https://example.com/old.mp3"); BackgroundSync.process(context, p, Arrays.asList(e)); assertTrue(r.prefs.getString(ProgramOptions.key("seen", p.feed), "").contains("old")); assertEquals(-1, r.downloadId(e)); assertEquals("[]", r.prefs.getString(ProgramOptions.key("pending", p.feed), ""));
    }
    @Test public void freshEpisodesOnMobileRemainPendingUntilWifi() throws Exception {
        Podcast p = new Podcast("Programa", "https://example.com/rss"); Repository r = new Repository(context); r.addPodcast(p); r.prefs.edit().putBoolean(ProgramOptions.key("auto", p.feed), true).putString(ProgramOptions.key("seen", p.feed), "[\"old\"]").commit(); Episode e = new Episode("new", "Nuevo", "", "https://example.com/new.mp3"); BackgroundSync.process(context, p, Arrays.asList(e)); assertEquals(-1, r.downloadId(e)); assertTrue(r.prefs.getString(ProgramOptions.key("pending", p.feed), "").contains("new")); BackgroundSync.process(context, p, Arrays.asList(e)); assertTrue(r.prefs.getString(ProgramOptions.key("pending", p.feed), "").contains("new"));
    }
    @Test public void wifiAutomationKeepsManualDownloadsAndProtectsCurrentEpisode() throws Exception {
        android.net.ConnectivityManager cm = context.getSystemService(android.net.ConnectivityManager.class); android.net.NetworkCapabilities caps = new android.net.NetworkCapabilities(); Shadows.shadowOf(caps).addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI); Shadows.shadowOf(cm).setNetworkCapabilities(cm.getActiveNetwork(), caps); assertTrue(BackgroundSync.wifi(context));
        Podcast p = new Podcast("Programa", "https://example.com/rss"); Repository r = new Repository(context); r.addPodcast(p); r.prefs.edit().putBoolean(ProgramOptions.key("auto", p.feed), true).putInt(ProgramOptions.key("limit", p.feed), 1).putString(ProgramOptions.key("seen", p.feed), "[]").commit();
        android.app.DownloadManager manager = context.getSystemService(android.app.DownloadManager.class); org.robolectric.shadows.ShadowDownloadManager downloads = Shadows.shadowOf(manager);
        Episode manual = new Episode("manual", "Manual", "", "https://example.com/manual.mp3"), a = new Episode("a", "Primero", "", "https://example.com/a.mp3"), b = new Episode("b", "Segundo", "", "https://example.com/b.mp3"); r.remember(new LibraryEntry(manual, p)); r.download(manual); Shadows.shadowOf(downloads.getRequest(r.downloadId(manual))).setStatus(android.app.DownloadManager.STATUS_SUCCESSFUL);
        BackgroundSync.process(context, p, Arrays.asList(a)); long first = r.downloadId(a); assertTrue(first >= 0); org.robolectric.shadows.ShadowDownloadManager.ShadowRequest request = Shadows.shadowOf(downloads.getRequest(first)); assertEquals(android.app.DownloadManager.Request.NETWORK_WIFI, request.getAllowedNetworkTypes()); request.setStatus(android.app.DownloadManager.STATUS_PENDING);
        r.prefs.edit().putString("activeEpisode", "a").commit(); BackgroundSync.process(context, p, Arrays.asList(b, a)); assertEquals(-1, r.downloadId(b)); assertEquals(first, r.downloadId(a)); assertTrue(r.downloadId(manual) >= 0);
        r.prefs.edit().remove("activeEpisode").commit(); BackgroundSync.process(context, p, Arrays.asList(b, a)); assertTrue(r.downloadId(b) >= 0); assertEquals(-1, r.downloadId(a)); assertNotNull(downloads.getRequest(r.downloadId(manual)));
    }
    @Test public void schedulerExistsOnlyWhenOptionsAreEnabled() {
        BackgroundSync.schedule(context); android.app.job.JobScheduler scheduler = context.getSystemService(android.app.job.JobScheduler.class); assertNull(scheduler.getPendingJob(BackgroundSync.JOB_ID)); new Repository(context).prefs.edit().putBoolean("deletePlayedDownloads", true).commit(); BackgroundSync.schedule(context); assertTrue(scheduler.getPendingJob(BackgroundSync.JOB_ID).isPersisted()); assertEquals(21600000, scheduler.getPendingJob(BackgroundSync.JOB_ID).getIntervalMillis());
    }
    @Test public void chartUsesAppleIdentifiersRatherThanSearchingForCategoryNumbers() throws Exception { assertEquals("123,456", Catalog.chartIds("{\"feed\":{\"entry\":[{\"id\":{\"attributes\":{\"im:id\":\"123\"}}},{\"id\":{\"attributes\":{\"im:id\":\"456\"}}}]}}")); assertEquals("", Catalog.chartIds("{\"feed\":{}}")); }
    @Test public void catalogLanguageIsReadFromRssNotCountry() throws Exception { assertEquals("es-ES", Catalog.feedLanguage(bytes("<rss><channel><language>es_ES</language><item/></channel></rss>"))); assertEquals("", Catalog.feedLanguage(bytes("<rss><channel><item/></channel></rss>"))); }
}
