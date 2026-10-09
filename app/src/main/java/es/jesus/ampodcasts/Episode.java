package es.jesus.ampodcasts;

import org.json.JSONObject;

final class Episode {
    final String id, title, date, url, videoUrl, chaptersUrl, artwork;
    final long durationMs;
    Episode(String id, String title, String date, String url) {
        this(id, title, date, url, "");
    }
    Episode(String id, String title, String date, String url, String videoUrl) {
        this(id, title, date, url, videoUrl, 0, "");
    }
    Episode(String id, String title, String date, String url, String videoUrl, long durationMs, String chaptersUrl) {
        this(id, title, date, url, videoUrl, durationMs, chaptersUrl, "");
    }
    Episode(String id, String title, String date, String url, String videoUrl, long durationMs, String chaptersUrl, String artwork) {
        this.artwork = Podcast.artworkUrl(artwork); this.durationMs = Math.max(0, durationMs); this.chaptersUrl = chaptersUrl;
        this.id = id; this.title = title; this.date = date; this.url = url; this.videoUrl = videoUrl;
    }
    JSONObject json() throws Exception {
        return new JSONObject().put("id", id).put("title", title).put("date", date).put("url", url).put("videoUrl", videoUrl).put("durationMs", durationMs).put("chaptersUrl", chaptersUrl).put("artwork", artwork);
    }
    static Episode from(JSONObject o) throws Exception {
        return new Episode(o.getString("id"), o.getString("title"), o.getString("date"), o.getString("url"), o.optString("videoUrl", ""), o.optLong("durationMs", 0), o.optString("chaptersUrl", ""), o.optString("artwork", ""));
    }
}
