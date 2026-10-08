package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LibraryFeaturesTest {
    private Repository repository() { return new Repository(RuntimeEnvironment.getApplication()); }
    private LibraryEntry entry(String id) { return new LibraryEntry(new Episode(id, "Capítulo " + id, "", "https://example.com/" + id + ".mp3"), new Podcast("Programa", "https://example.com/rss")); }
    @Test public void queueDeduplicatesReordersAndSurvivesRestart() throws Exception {
        Repository r = repository(); LibraryEntry a = entry("a"), b = entry("b"); r.addPodcast(a.podcast);
        assertTrue(r.enqueue(a)); assertTrue(r.enqueue(b)); assertFalse(r.enqueue(a)); r.moveQueue("b", -1);
        Repository restored = repository(); assertEquals("b", restored.queue().get(0).episode.id); restored.dequeue("b"); assertEquals("a", restored.queue().get(0).episode.id);
        restored.removePodcast(a.podcast.feed, false); assertTrue(repository().queue().isEmpty());
    }
    @Test public void favoritesProgressAndListenedStateAreIndependentAndPersistent() throws Exception {
        Repository r = repository(); LibraryEntry a = entry("a"); r.addPodcast(a.podcast); r.setFavorite(a, true); r.savePosition("a", 50000);
        assertTrue(repository().favorite("a")); assertEquals(50000, repository().position("a")); assertEquals("Capítulo a", repository().remembered().get(0).episode.title);
        r.setListened("a", true); assertTrue(repository().listened("a")); assertEquals(0, repository().position("a")); assertTrue(repository().favorite("a"));
        r.setListened("a", false); assertFalse(repository().listened("a"));
    }
    @Test public void searchFindsSavedEpisodesAcrossProgramsAndOmitsRemovedShows() throws Exception {
        Repository r = repository(); LibraryEntry a = entry("ciencia"); r.addPodcast(a.podcast); r.remember(a);
        LibraryEntry b = new LibraryEntry(new Episode("b", "CIENCIA al día", "", "https://example.org/b.mp3"), new Podcast("Otro", "https://example.org/rss")); r.addPodcast(b.podcast); r.remember(b);
        assertEquals(2, r.searchLibrary("ciencia").size()); r.removePodcast(a.podcast.feed, false); assertEquals(1, r.searchLibrary("ciencia").size());
    }
    @Test public void oldEpisodesMigrateAndAudioSelectionPreservesBestOriginalUrl() throws Exception {
        Episode old = Episode.from(new org.json.JSONObject("{\"id\":\"a\",\"title\":\"Uno\",\"date\":\"\",\"url\":\"https://example.com/a.mp3\"}")); assertEquals("", old.videoUrl);
        String xml = "<rss xmlns:media='http://search.yahoo.com/mrss/'><channel><item><guid>a</guid><title>Audio</title><enclosure url='https://example.com/low.mp3' type='audio/mpeg'/><media:content url='https://example.com/high.flac?token=a%26b' type='audio/flac' bitrate='1000'/><media:content url='https://example.com/mid.mp3' type='audio/mpeg' bitrate='128'/></item></channel></rss>";
        Episode audio = Repository.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).get(0); assertEquals("https://example.com/high.flac?token=a%26b", audio.url); assertEquals("", audio.videoUrl);
    }
    @Test public void videoAndAudioVariantsRemainAvailableAndEpisodeArtworkDoesNotInterfere() throws Exception {
        String xml = "<rss xmlns:media='http://search.yahoo.com/mrss/'><channel><item><title>Ambos</title><enclosure url='https://example.com/audio.mp4' type='audio/mp4'/><media:content url='https://example.com/small.mp4' type='video/mp4' height='360'/><media:content url='https://example.com/full.mp4' type='video/mp4' height='1080'/><media:content url='https://example.com/image.jpg' type='image/jpeg'/></item><item><title>Solo vídeo</title><enclosure url='https://example.com/video.webm' type='video/webm'/></item></channel></rss>";
        List<Episode> episodes = Repository.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))); assertEquals(2, episodes.size()); assertEquals("https://example.com/audio.mp4", episodes.get(0).url); assertEquals("https://example.com/full.mp4", episodes.get(0).videoUrl); assertEquals(episodes.get(1).url, episodes.get(1).videoUrl);
        Episode restored = Episode.from(episodes.get(0).json()); assertEquals(episodes.get(0).videoUrl, restored.videoUrl);
    }
    @Test public void localAudioAndExplicitVideoProduceDistinctPlaybackUris() throws Exception {
        LibraryEntry entry = new LibraryEntry(new Episode("a", "Ambos", "", "https://example.com/a.mp3", "https://example.com/a.mp4"), new Podcast("Uno", "https://example.com/rss"));
        assertEquals("https://example.com/a.mp3", entry.mediaItem(RuntimeEnvironment.getApplication(), false).localConfiguration.uri.toString());
        assertEquals("https://example.com/a.mp4", entry.mediaItem(RuntimeEnvironment.getApplication(), true).localConfiguration.uri.toString());
        assertTrue(entry.mediaItem(RuntimeEnvironment.getApplication(), true).mediaMetadata.extras.getBoolean("video"));
    }
}
