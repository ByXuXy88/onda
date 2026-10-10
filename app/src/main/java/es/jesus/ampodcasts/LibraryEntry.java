package es.jesus.ampodcasts;

import android.os.Bundle;
import androidx.media3.common.*;
import org.json.JSONObject;

/** Snapshot retained when an episode is played, queued, downloaded or bookmarked. */
final class LibraryEntry {
    final Episode episode;
    final Podcast podcast;
    LibraryEntry(Episode episode, Podcast podcast) { this.episode = episode; this.podcast = podcast; }
    JSONObject json() throws Exception { return new JSONObject().put("episode", episode.json()).put("podcast", podcast.json()); }
    static LibraryEntry from(JSONObject json) throws Exception { return new LibraryEntry(Episode.from(json.getJSONObject("episode")), Podcast.from(json.getJSONObject("podcast"))); }
    String artwork() { return episode.artwork.isEmpty() ? podcast.artwork : episode.artwork; }
    MediaItem mediaItem(android.content.Context context, boolean video) {
        Repository repository = new Repository(context, podcast.feed);
        Bundle extras = new Bundle(); extras.putString("feed", podcast.feed); extras.putBoolean("video", video || episode.url.equals(episode.videoUrl));
        MediaMetadata metadata = new MediaMetadata.Builder().setTitle(episode.title).setArtist(podcast.title).setAlbumTitle(podcast.title)
            .setArtworkUri(artwork().isEmpty() ? null : android.net.Uri.parse(artwork())).setExtras(extras).build();
        android.net.Uri uri = video && !episode.videoUrl.isEmpty() && !episode.url.equals(episode.videoUrl) ? android.net.Uri.parse(episode.videoUrl) : repository.playbackUri(episode);
        extras.putString("audioBinding",Repository.key(uri.toString()));metadata=metadata.buildUpon().setExtras(extras).build();
        return new MediaItem.Builder().setMediaId(episode.id).setUri(uri).setMediaMetadata(metadata).build();
    }
}
