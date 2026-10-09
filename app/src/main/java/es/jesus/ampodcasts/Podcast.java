package es.jesus.ampodcasts;

import org.json.JSONObject;

final class Podcast {
    final String title, feed, artwork, description;
    Podcast(String title, String feed) { this(title, feed, ""); }
    Podcast(String title, String feed, String artwork) { this(title, feed, artwork, ""); }
    Podcast(String title, String feed, String artwork, String description) { this.title=title; this.feed=feed; this.artwork=artworkUrl(artwork); this.description=description; }
    static String artworkUrl(String text) {
        try { return Repository.normalizeFeed(text); } catch (Exception ignored) { return ""; }
    }
    JSONObject json() throws Exception { return new JSONObject().put("title", title).put("feed", feed).put("artwork", artwork).put("description", description); }
    static Podcast from(JSONObject o) throws Exception { return new Podcast(o.getString("title"), o.getString("feed"), o.optString("artwork", ""),o.optString("description","")); }
}
