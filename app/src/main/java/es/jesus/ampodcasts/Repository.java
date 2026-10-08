package es.jesus.ampodcasts;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.AtomicFile;
import android.util.Xml;
import org.json.JSONArray;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class Repository {
    private static final String LEGACY_FEED = "https://feeds.megaphone.fm/GLT1394479782";
    private final Context context;
    final SharedPreferences prefs;
    private final DownloadManager downloads;
    final String feed;
    Repository(Context c) {
        this(c, "");
    }
    Repository(Context c, String feed) {
        context = c.getApplicationContext();
        this.feed = feed;
        prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE);
        downloads = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    }
    private File cacheFile() { return new File(context.getFilesDir(), feed.equals(LEGACY_FEED) ? "episodes.json" : "episodes-" + key(feed) + ".json"); }
    List<Podcast> podcasts() {
        List<Podcast> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("podcasts", "[]"));
            for (int n = 0; n < a.length(); n++) { Podcast p = Podcast.from(a.getJSONObject(n)); list.add(p); }
        } catch (Exception ignored) { }
        if (list.isEmpty() && !prefs.getBoolean("libraryInitialized", false) && new File(context.getFilesDir(), "episodes.json").exists()) list.add(new Podcast("AM", LEGACY_FEED));
        return list;
    }
    private void savePodcasts(List<Podcast> list) throws Exception {
        JSONArray a = new JSONArray(); for (Podcast p : list) a.put(p.json());
        if (!prefs.edit().putString("podcasts", a.toString()).putBoolean("libraryInitialized", true).commit()) throw new IOException("No se pudo guardar la biblioteca");
    }
    synchronized void removePodcast(String feed, boolean deleteDownloads) throws Exception {
        if (deleteDownloads) {
            Repository target = new Repository(context, feed);
            for (Episode e : target.cached()) target.removeDownload(e);
        }
        List<Podcast> list = podcasts(); list.removeIf(p -> p.feed.equals(feed)); savePodcasts(list);
    }
    synchronized boolean addPodcast(Podcast podcast) throws Exception {
        List<Podcast> list = podcasts();
        for (Podcast p : list) if (p.feed.equals(podcast.feed)) return false;
        list.add(podcast); savePodcasts(list);
        return true;
    }
    static String normalizeFeed(String text) throws Exception {
        java.net.URI uri = new java.net.URI(text.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IOException("Introduce un enlace RSS público que empiece por https://");
        return new java.net.URI("https://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() == -1 || uri.getPort() == 443 ? "" : ":" + uri.getPort()) + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath()) + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())).normalize().toASCIIString();
    }
    String episodeId(String raw) { return feed.equals(LEGACY_FEED) ? raw : key(feed) + "::" + raw; }
    static List<Podcast> parseOpml(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser(); p.setInput(in, null); List<Podcast> list = new ArrayList<>(); Set<String> seen = new HashSet<>();
        boolean opml = false;
        for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.nextToken()) {
            if (event == XmlPullParser.DOCDECL) throw new IOException("Archivo OPML no válido");
            if (event == XmlPullParser.START_TAG) {
                if ("opml".equalsIgnoreCase(p.getName())) opml = true;
                if ("outline".equalsIgnoreCase(p.getName())) {
                    String feed = p.getAttributeValue(null, "xmlUrl"); if (feed == null) continue;
                    try { feed = normalizeFeed(feed); } catch (Exception ignored) { continue; }
                    String title = p.getAttributeValue(null, "title"); if (title == null) title = p.getAttributeValue(null, "text");
                    if (seen.add(feed)) list.add(new Podcast(title == null ? "Podcast" : title, feed));
                    if (list.size() > 100) throw new IOException("Importa como máximo 100 podcasts por archivo");
                }
            }
        }
        if (!opml || list.isEmpty()) throw new IOException("El archivo no contiene podcasts RSS con enlaces HTTPS");
        return list;
    }
    List<Episode> cached() {
        if (feed.isEmpty()) return new ArrayList<>();
        List<Episode> list = new ArrayList<>();
        try (InputStream in = new AtomicFile(cacheFile()).openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            JSONArray a = new JSONArray(bytes.toString("UTF-8"));
            for (int n = 0; n < a.length(); n++) list.add(Episode.from(a.getJSONObject(n)));
        } catch (Exception ignored) { }
        return list;
    }
    List<Episode> refresh() throws Exception {
        return refreshFeed().episodes;
    }
    FeedResult refreshFeed() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(normalizeFeed(feed)).openConnection();
        conn.setConnectTimeout(15000); conn.setReadTimeout(25000);
        conn.setRequestProperty("User-Agent", "Onda/1.2 Android");
        FeedResult result;
        try {
            if (conn.getResponseCode() != 200) throw new IOException("La fuente no responde (" + conn.getResponseCode() + ")");
            try (InputStream in = conn.getInputStream()) { result = parseFeed(in); }
        } finally { conn.disconnect(); }
        List<Episode> list = new ArrayList<>();
        for (Episode e : result.episodes) list.add(new Episode(episodeId(e.id), e.title, e.date, e.url));
        if (list.isEmpty()) throw new IOException("La fuente no contiene episodios reproducibles");
        JSONArray a = new JSONArray(); for (Episode e : list) a.put(e.json());
        AtomicFile f = new AtomicFile(cacheFile());
        FileOutputStream out = f.startWrite();
        try { out.write(a.toString().getBytes(StandardCharsets.UTF_8)); f.finishWrite(out); }
        catch (Exception e) { f.failWrite(out); throw e; }
        return new FeedResult(result.title, list);
    }
    static List<Episode> parse(InputStream in) throws Exception {
        return parseFeed(in).episodes;
    }
    static final class FeedResult {
        final String title; final List<Episode> episodes;
        FeedResult(String title, List<Episode> episodes) { this.title = title; this.episodes = episodes; }
    }
    static FeedResult parseFeed(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser(); p.setInput(in, null);
        List<Episode> result = new ArrayList<>();
        String title = "", date = "", id = "", url = "", channelTitle = ""; boolean item = false;
        for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.nextToken()) {
            if (event == XmlPullParser.DOCDECL) throw new IOException("Fuente XML no válida");
            if (event == XmlPullParser.START_TAG) {
                String name = p.getName();
                if (name.equals("item")) { item = true; title = date = id = url = ""; }
                else if (!item && name.equals("title") && channelTitle.isEmpty()) channelTitle = p.nextText();
                else if (item) {
                    if (name.equals("title")) title = p.nextText();
                    else if (name.equals("pubDate")) date = p.nextText();
                    else if (name.equals("guid")) id = p.nextText();
                    else if (name.equals("enclosure")) { String u = p.getAttributeValue(null, "url"); if (u != null) url = u; }
                }
            } else if (event == XmlPullParser.END_TAG && p.getName().equals("item")) {
                if (!title.isEmpty() && url.startsWith("https://")) result.add(new Episode(id.isEmpty() ? url : id, title, date, url));
                item = false;
            }
        }
        return new FeedResult(channelTitle.isEmpty() ? "Podcast" : channelTitle, result);
    }
    static String key(String id) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder(); for (byte b : digest) s.append(String.format(Locale.ROOT, "%02x", b & 255)); return s.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    long position(String id) { return prefs.getLong("pos:" + key(id), 0); }
    void savePosition(String id, long pos) { if (!id.isEmpty()) prefs.edit().putLong("pos:" + key(id), Math.max(0, pos)).apply(); }
    long downloadId(Episode e) { return prefs.getLong("download:" + key(e.id), -1); }
    int downloadStatus(Episode e) {
        long id = downloadId(e); if (id < 0) return -1;
        try (Cursor c = downloads.query(new DownloadManager.Query().setFilterById(id))) {
            if (c != null && c.moveToFirst()) return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        }
        return -1;
    }
    Uri localUri(Episode e) {
        return downloadStatus(e) == DownloadManager.STATUS_SUCCESSFUL ? downloads.getUriForDownloadedFile(downloadId(e)) : null;
    }
    Uri playbackUri(Episode e) { Uri local = localUri(e); return local != null ? local : Uri.parse(e.url); }
    void download(Episode e) {
        if (downloadStatus(e) == DownloadManager.STATUS_SUCCESSFUL || downloadStatus(e) == DownloadManager.STATUS_RUNNING || downloadStatus(e) == DownloadManager.STATUS_PENDING || downloadStatus(e) == DownloadManager.STATUS_PAUSED) return;
        long old = downloadId(e); if (old >= 0) downloads.remove(old);
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(e.url)).setTitle(e.title).setDescription("Onda")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, "episodes", key(e.id) + "-" + System.currentTimeMillis() + ".mp3");
        long id = downloads.enqueue(r); prefs.edit().putLong("download:" + key(e.id), id).apply();
    }
    void removeDownload(Episode e) { long id = downloadId(e); if (id >= 0) downloads.remove(id); prefs.edit().remove("download:" + key(e.id)).apply(); }
}
