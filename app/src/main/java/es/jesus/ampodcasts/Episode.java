package es.jesus.ampodcasts;

import org.json.JSONObject;

final class Episode {
    final String id, title, date, url, videoUrl;
    Episode(String id, String title, String date, String url) {
        this(id, title, date, url, "");
    }
    Episode(String id, String title, String date, String url, String videoUrl) {
        this.id = id; this.title = title; this.date = date; this.url = url; this.videoUrl = videoUrl;
    }
    JSONObject json() throws Exception {
        return new JSONObject().put("id", id).put("title", title).put("date", date).put("url", url).put("videoUrl", videoUrl);
    }
    static Episode from(JSONObject o) throws Exception {
        return new Episode(o.getString("id"), o.getString("title"), o.getString("date"), o.getString("url"), o.optString("videoUrl", ""));
    }
}
