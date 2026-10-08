package es.jesus.ampodcasts;

import org.json.JSONObject;

final class Episode {
    final String id, title, date, url;
    Episode(String id, String title, String date, String url) {
        this.id = id; this.title = title; this.date = date; this.url = url;
    }
    JSONObject json() throws Exception {
        return new JSONObject().put("id", id).put("title", title).put("date", date).put("url", url);
    }
    static Episode from(JSONObject o) throws Exception {
        return new Episode(o.getString("id"), o.getString("title"), o.getString("date"), o.getString("url"));
    }
}
