package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RepositoryTest {
    @Test public void oldLibraryEntriesMigrateWithoutLosingFeedAndArtworkIsSaved() throws Exception {
        Podcast old = Podcast.from(new org.json.JSONObject("{\"title\":\"Mi podcast\",\"feed\":\"https://example.com/rss\"}"));
        assertEquals("", old.artwork);
        Repository r = new Repository(RuntimeEnvironment.getApplication()); r.addPodcast(old);
        r.updateArtwork(old.feed, "https://example.com/cover.jpg");
        Podcast restored = new Repository(RuntimeEnvironment.getApplication()).podcasts().get(0);
        assertEquals(old.feed, restored.feed); assertEquals(old.title, restored.title); assertEquals("https://example.com/cover.jpg", restored.artwork);
    }
    @Test public void podcastArtworkPrefersChannelItunesImageOverRssAndEpisodeImages() throws Exception {
        String xml = "<rss xmlns:itunes='http://www.itunes.com/dtds/podcast-1.0.dtd'><channel><image><title>Image title</title><url>https://example.com/rss.png</url></image><title>Programa</title><itunes:image href='https://example.com/show.png'/><item><title>Uno</title><itunes:image href='https://example.com/episode.png'/><enclosure url='https://example.com/one.mp3'/></item></channel></rss>";
        Repository.FeedResult result = Repository.parseFeed(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals("Programa", result.title); assertEquals("https://example.com/show.png", result.artwork);
    }
    @Test public void rssArtworkFallbackAndInsecureImagesAreHandled() throws Exception {
        String xml = "<rss><channel><title>Programa</title><image><url>https://example.com/rss.png</url></image><item><title>Uno</title><enclosure url='https://example.com/one.mp3'/></item></channel></rss>";
        assertEquals("https://example.com/rss.png", Repository.parseFeed(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).artwork);
        assertEquals("", new Podcast("Uno", "https://example.com/rss", "http://example.com/cover.png").artwork);
    }
    @Test public void appleLinksExtractOnlyShowIdsAndCatalogPreservesArtwork() throws Exception {
        assertEquals("123456", Catalog.appleId("https://podcasts.apple.com/es/podcast/mi-programa/id123456?i=998877"));
        assertThrows(Exception.class, () -> Catalog.appleId("https://podcasts.apple.com.evil.example/id123456"));
        List<Podcast> podcasts = Catalog.parse("{\"results\":[{\"collectionName\":\"Uno\",\"feedUrl\":\"https://example.com/rss\",\"artworkUrl600\":\"https://example.com/cover.jpg\"}]}");
        assertEquals("https://example.com/cover.jpg", podcasts.get(0).artwork);
    }
    @Test public void exportedLibraryRoundTripsEscapedNamesWithoutIncludingProgress() throws Exception {
        Repository r = new Repository(RuntimeEnvironment.getApplication()); r.addPodcast(new Podcast("Noticias & Ciencia <Hoy>", "https://example.com/rss?a=1&b=2")); r.savePosition("episode", 15000);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); r.exportOpml(out);
        List<Podcast> restored = Repository.parseOpml(new ByteArrayInputStream(out.toByteArray()));
        assertEquals("Noticias & Ciencia <Hoy>", restored.get(0).title); assertEquals("https://example.com/rss?a=1&b=2", restored.get(0).feed);
        assertFalse(out.toString("UTF-8").contains("15000"));
    }
    @Test public void freshInstallHasNoPodcastsOrEpisodes() {
        Repository r = new Repository(RuntimeEnvironment.getApplication());
        assertTrue(r.podcasts().isEmpty()); assertTrue(r.cached().isEmpty());
    }
    @Test public void removingLastPodcastLeavesLibraryEmptyAfterRestart() throws Exception {
        Repository r = new Repository(RuntimeEnvironment.getApplication());
        r.addPodcast(new Podcast("Uno", "https://example.com/rss")); r.removePodcast("https://example.com/rss", false);
        assertTrue(new Repository(RuntimeEnvironment.getApplication()).podcasts().isEmpty());
    }
    @Test public void catalogFiltersInvalidFeedsAndDeduplicates() throws Exception {
        String json = "{\"results\":[{\"collectionName\":\"Uno\",\"feedUrl\":\"https://example.com/rss\"},{\"collectionName\":\"Duplicado\",\"feedUrl\":\"https://example.com/rss\"},{\"collectionName\":\"Sin RSS\"},{\"collectionName\":\"HTTP\",\"feedUrl\":\"http://example.com/rss\"}]}";
        List<Podcast> result = Catalog.parse(json); assertEquals(1, result.size()); assertEquals("Uno", result.get(0).title);
    }
    @Test public void legacySubscriptionIsRetainedOnlyWhenOldCacheExists() throws Exception {
        android.content.Context c = RuntimeEnvironment.getApplication();
        java.io.File cache = new java.io.File(c.getFilesDir(), "episodes.json");
        try (FileOutputStream out = new FileOutputStream(cache)) { out.write("[]".getBytes(StandardCharsets.UTF_8)); }
        Repository r = new Repository(c); assertEquals(1, r.podcasts().size());
        String feed = r.podcasts().get(0).feed; r.removePodcast(feed, false);
        assertTrue(new Repository(c).podcasts().isEmpty());
    }
    @Test public void realFeedContainsPlayableAmEpisodes() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/am-feed.xml")) {
            List<Episode> episodes = Repository.parse(in);
            assertEquals(2, episodes.size());
            assertTrue(episodes.get(0).title.contains("8 de octubre"));
            assertTrue(episodes.get(0).url.startsWith("https://traffic.megaphone.fm/"));
            assertFalse(episodes.get(0).id.isEmpty());
            assertEquals(episodes.size(), episodes.stream().map(e -> e.id).distinct().count());
        }
    }
    @Test public void progressSurvivesNewRepositoryInstanceAndIdsStaySeparate() {
        Repository r = new Repository(RuntimeEnvironment.getApplication());
        r.savePosition("episode/a", 47000);
        r.savePosition("episode?a", 13000);
        Repository reopened = new Repository(RuntimeEnvironment.getApplication());
        assertEquals(47000, reopened.position("episode/a"));
        assertEquals(13000, reopened.position("episode?a"));
    }
    @Test public void rejectsXmlWithExternalEntities() throws Exception {
        String xml = "<!DOCTYPE rss [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><rss><channel><item><title>&secret;</title></item></channel></rss>";
        assertThrows(Exception.class, () -> Repository.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }
    @Test public void ignoresInsecureEnclosures() throws Exception {
        String xml = "<rss><channel><item><title>Unsafe</title><enclosure url='http://example.com/a.mp3'/></item><item><title>Safe</title><guid>x</guid><enclosure url='https://example.com/a.mp3'/></item></channel></rss>";
        List<Episode> list = Repository.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, list.size()); assertEquals("Safe", list.get(0).title);
    }
    @Test public void defaultAmKeepsItsOriginalIdsAndProgressWhileOtherFeedsAreIsolated() {
        Repository am = new Repository(RuntimeEnvironment.getApplication(), "https://feeds.megaphone.fm/GLT1394479782");
        Repository other = new Repository(RuntimeEnvironment.getApplication(), "https://example.com/feed");
        assertEquals("original-guid", am.episodeId("original-guid"));
        assertNotEquals(am.episodeId("original-guid"), other.episodeId("original-guid"));
        am.savePosition(am.episodeId("original-guid"), 72000);
        assertEquals(72000, new Repository(RuntimeEnvironment.getApplication()).position("original-guid"));
        assertEquals(0, other.position(other.episodeId("original-guid")));
    }
    @Test public void importsNestedOpmlAndDeduplicatesFeeds() throws Exception {
        String xml = "<opml version='2.0'><body><outline text='Noticias'><outline text='Uno' xmlUrl='https://example.com/a'/><outline title='Duplicado' xmlUrl='https://example.com/a'/><outline title='Dos' xmlUrl='https://example.com/b'/><outline text='Viejo' xmlUrl='http://example.com/c'/></outline></body></opml>";
        List<Podcast> list = Repository.parseOpml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, list.size()); assertEquals("Uno", list.get(0).title); assertEquals("Dos", list.get(1).title);
    }
    @Test public void librarySurvivesRestartAndStartsEmpty() throws Exception {
        Repository r = new Repository(RuntimeEnvironment.getApplication()); r.prefs.edit().remove("podcasts").commit();
        assertTrue(r.addPodcast(new Podcast("Otro", "https://example.com/rss")));
        assertFalse(r.addPodcast(new Podcast("Otro repetido", "https://example.com/rss")));
        List<Podcast> list = new Repository(RuntimeEnvironment.getApplication()).podcasts();
        assertEquals(1, list.size()); assertEquals("Otro", list.get(0).title);
    }
    @Test public void feedNormalizationPreservesEncodedQueryAndRejectsCredentials() throws Exception {
        assertEquals("https://example.com/rss?a=x%26y", Repository.normalizeFeed(" HTTPS://EXAMPLE.COM:443/rss?a=x%26y#section "));
        assertThrows(Exception.class, () -> Repository.normalizeFeed("https://user:password@example.com/rss"));
    }
    @Test public void channelTitleIsIndependentFromEpisodeTitle() throws Exception {
        String xml = "<rss><channel><title>Mi programa</title><item><title>Capítulo 1</title><enclosure url='https://example.com/audio.mp3'/></item></channel></rss>";
        Repository.FeedResult result = Repository.parseFeed(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals("Mi programa", result.title); assertEquals("Capítulo 1", result.episodes.get(0).title);
    }
    @Test public void legacyAmDownloadReferencesArePreserved() {
        Repository r = new Repository(RuntimeEnvironment.getApplication());
        r.prefs.edit().putLong("download:" + Repository.key("old-guid"), 1234).commit();
        Episode e = new Episode("old-guid", "AM antiguo", "", "https://example.com/am.mp3");
        assertEquals(1234, new Repository(RuntimeEnvironment.getApplication()).downloadId(e));
    }
}
