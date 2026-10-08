package es.jesus.ampodcasts;

import org.json.JSONObject;

final class Podcast {
    final String title, feed;
    Podcast(String title, String feed) { this.title = title; this.feed = feed; }
    JSONObject json() throws Exception { return new JSONObject().put("title", title).put("feed", feed); }
    static Podcast from(JSONObject o) throws Exception { return new Podcast(o.getString("title"), o.getString("feed")); }
}
