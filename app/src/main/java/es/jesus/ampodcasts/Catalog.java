package es.jesus.ampodcasts;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Catalog {
    static List<Podcast> search(String term) throws Exception {
        String query = URLEncoder.encode(term.trim(), "UTF-8");
        return request("https://itunes.apple.com/search?media=podcast&entity=podcast&country=ES&limit=25&term=" + query);
    }
    static String appleId(String text) throws Exception {
        java.net.URI uri = new java.net.URI(text.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"podcasts.apple.com".equalsIgnoreCase(uri.getHost()) || uri.getUserInfo() != null) throw new IOException("Pega un enlace de podcasts.apple.com");
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?:^|/)id([0-9]+)(?:/|$)").matcher(uri.getPath());
        if (!match.find()) throw new IOException("El enlace debe ser de un programa de Apple Podcasts");
        return match.group(1);
    }
    static Podcast fromAppleLink(String link) throws Exception {
        List<Podcast> found = request("https://itunes.apple.com/lookup?entity=podcast&country=ES&id=" + appleId(link));
        if (found.isEmpty()) throw new IOException("Este programa no ofrece un RSS público HTTPS");
        return found.get(0);
    }
    private static List<Podcast> request(String endpoint) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setRequestProperty("User-Agent", "Onda/1.4 Android");
        try {
            if (c.getResponseCode() != 200) throw new IOException("No se pudo consultar el catálogo");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) { out.write(b, 0, n); if (out.size() > 2_000_000) throw new IOException("Respuesta demasiado grande"); }
                return parse(out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { c.disconnect(); }
    }
    static List<Podcast> parse(String json) throws Exception {
        JSONArray results = new JSONObject(json).getJSONArray("results"); List<Podcast> list = new ArrayList<>(); Set<String> feeds = new HashSet<>();
        for (int n = 0; n < results.length(); n++) {
            JSONObject p = results.getJSONObject(n);
            String title = p.optString("collectionName", p.optString("trackName", "Podcast"));
            try { String feed = Repository.normalizeFeed(p.optString("feedUrl", "")); if (feeds.add(feed)) list.add(new Podcast(title, feed, p.optString("artworkUrl600", p.optString("artworkUrl100", "")))); }
            catch (Exception ignored) { }
        }
        return list;
    }
}
