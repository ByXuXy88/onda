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
        List<LibraryEntry> queue = queue(); queue.removeIf(e -> e.podcast.feed.equals(feed)); saveQueue(queue);
    }
    synchronized boolean addPodcast(Podcast podcast) throws Exception {
        List<Podcast> list = podcasts();
        for (Podcast p : list) if (p.feed.equals(podcast.feed)) return false;
        list.add(podcast); savePodcasts(list);
        return true;
    }
    synchronized void updateArtwork(String feed, String artwork) throws Exception {
        if (artwork.isEmpty()) return;
        List<Podcast> list = podcasts();
        for (int n = 0; n < list.size(); n++) {
            Podcast p = list.get(n);
            if (p.feed.equals(feed) && !p.artwork.equals(artwork)) { list.set(n, new Podcast(p.title, p.feed, artwork,p.description)); savePodcasts(list); return; }
        }
    }
    void exportOpml(OutputStream out) throws Exception {
        org.xmlpull.v1.XmlSerializer xml = Xml.newSerializer(); xml.setOutput(out, "UTF-8"); xml.startDocument("UTF-8", true);
        xml.startTag(null, "opml").attribute(null, "version", "2.0");
        xml.startTag(null, "head").startTag(null, "title").text("Biblioteca de Onda").endTag(null, "title").endTag(null, "head");
        xml.startTag(null, "body");
        for (Podcast p : podcasts()) xml.startTag(null, "outline").attribute(null, "type", "rss").attribute(null, "text", p.title).attribute(null, "title", p.title).attribute(null, "xmlUrl", p.feed).endTag(null, "outline");
        xml.endTag(null, "body").endTag(null, "opml"); xml.endDocument(); xml.flush();
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
        conn.setRequestProperty("User-Agent", "Onda/1.6 Android");
        FeedResult result;
        try {
            if (conn.getResponseCode() != 200) throw new IOException("La fuente no responde (" + conn.getResponseCode() + ")");
            try (InputStream in = conn.getInputStream()) { result = parseFeed(in); }
        } finally { conn.disconnect(); }
        List<Episode> list = new ArrayList<>();
        for (Episode e : result.episodes) list.add(new Episode(episodeId(e.id), e.title, e.date, e.url, e.videoUrl, e.durationMs, e.chaptersUrl, e.artwork,e.transcriptUrl,e.transcriptType));
        if (list.isEmpty()) throw new IOException("La fuente no contiene episodios reproducibles");
        JSONArray a = new JSONArray(); for (Episode e : list) a.put(e.json());
        AtomicFile f = new AtomicFile(cacheFile());
        FileOutputStream out = f.startWrite();
        try { out.write(a.toString().getBytes(StandardCharsets.UTF_8)); f.finishWrite(out); }
        catch (Exception e) { f.failWrite(out); throw e; }
        updateArtwork(feed, result.artwork);
        List<Podcast> programs=podcasts(); for(int i=0;i<programs.size();i++){Podcast p=programs.get(i);if(p.feed.equals(feed) && !p.description.equals(result.description)){ programs.set(i,new Podcast(p.title,p.feed,p.artwork,result.description));savePodcasts(programs);break; }}
        return new FeedResult(result.title, result.artwork, result.description, list);
    }
    static List<Episode> parse(InputStream in) throws Exception {
        return parseFeed(in).episodes;
    }
    static final class FeedResult {
        final String title, artwork, description; final List<Episode> episodes;
        FeedResult(String title, String artwork, List<Episode> episodes) { this(title,artwork,"",episodes); }
        FeedResult(String title,String artwork,String description,List<Episode> episodes){this.title=title;this.artwork=artwork;this.description=description;this.episodes=episodes;}
    }
    private static String descriptionText(XmlPullParser parser) throws Exception {
        int depth=parser.getDepth();StringBuilder text=new StringBuilder();int event;
        while((event=parser.nextToken())!=XmlPullParser.END_DOCUMENT){if(event==XmlPullParser.END_TAG && parser.getDepth()==depth)break;if(text.length()<10000){if(event==XmlPullParser.TEXT || event==XmlPullParser.CDSECT)text.append(parser.getText());else if(event==XmlPullParser.START_TAG)text.append(' ');}}
        return text.toString();
    }
    static FeedResult parseFeed(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser(); p.setInput(in, null);
        List<Episode> result = new ArrayList<>();
        String title = "", date = "", id = "", url = "", videoUrl = "", channelTitle = "", channelDescription = "", artwork = "", rssArtwork = ""; boolean item = false, channelImage = false; long audioQuality = -1, videoQuality = -1, duration = 0; String chapters = "", episodeArtwork = "", transcriptUrl="", transcriptType="";
        for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.nextToken()) {
            if (event == XmlPullParser.DOCDECL) throw new IOException("Fuente XML no válida");
            if (event == XmlPullParser.START_TAG) {
                String name = p.getName();
                if (name.equals("item")) { item = true; title = date = id = url = videoUrl = ""; audioQuality = videoQuality = -1; duration = 0; chapters = episodeArtwork = transcriptUrl = transcriptType = ""; }
                else if (!item && (name.equals("image") || name.equals("itunes:image"))) {
                    String href = p.getAttributeValue(null, "href");
                    if (href != null) artwork = Podcast.artworkUrl(href); else channelImage = true;
                }
                else if (!item && channelImage && name.equals("url")) rssArtwork = Podcast.artworkUrl(p.nextText());
                else if (!item && !channelImage && name.equals("title") && channelTitle.isEmpty()) channelTitle = p.nextText();
                else if (!item && !channelImage && (name.equals("description") || name.equals("itunes:summary")) && channelDescription.isEmpty()) { String raw=descriptionText(p); channelDescription=android.text.Html.fromHtml(raw,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim(); if(channelDescription.length()>5000)channelDescription=channelDescription.substring(0,5000); }
                else if (item) {
                    if (name.equals("image") || name.equals("itunes:image") || name.equals("media:thumbnail")) { String href = p.getAttributeValue(null, "href"); if (href == null) href = p.getAttributeValue(null, "url"); if (href != null) episodeArtwork = Podcast.artworkUrl(href); }
                    else if (name.equals("title")) title = p.nextText();
                    else if (name.equals("pubDate")) date = p.nextText();
                    else if (name.equals("guid")) id = p.nextText();
                    else if (name.equals("itunes:duration") || name.equals("duration")) duration = TimeFormat.parse(p.nextText());
                    else if (name.equals("podcast:chapters") || name.equals("chapters")) { String u = p.getAttributeValue(null, "url"); if (u != null) try { chapters = normalizeFeed(u); } catch (Exception ignored) { } }
                    else if (name.equals("podcast:transcript") || name.equals("transcript")) { String u=p.getAttributeValue(null,"url"), t=p.getAttributeValue(null,"type"); if(transcriptUrl.isEmpty() && u!=null && t!=null && TranscriptStore.supported(t))try{transcriptUrl=normalizeFeed(u);transcriptType=t;}catch(Exception ignored){} }
                    else if (name.equals("enclosure") || name.equals("media:content") || name.equals("content")) {
                        String u = p.getAttributeValue(null, "url"); if (u == null || !u.startsWith("https://")) continue;
                        String type = p.getAttributeValue(null, "type"), medium = p.getAttributeValue(null, "medium");
                        boolean explicitAudio = (type != null && type.startsWith("audio/")) || "audio".equals(medium);
                        boolean video = (type != null && type.startsWith("video/")) || "video".equals(medium) || (!explicitAudio && u.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4v|webm|mov)(\\?.*)?$"));
                        boolean audio = (type != null && type.startsWith("audio/")) || "audio".equals(medium) || (!video && name.equals("enclosure")) || u.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|m4a|aac|flac|ogg|opus)(\\?.*)?$");
                        long quality = mediaQuality(p);
                        if (video && quality > videoQuality) { videoUrl = u; videoQuality = quality; }
                        else if (audio && quality > audioQuality) { url = u; audioQuality = quality; }
                    }
                }
            } else if (event == XmlPullParser.END_TAG && p.getName().equals("image")) { channelImage = false;
            } else if (event == XmlPullParser.END_TAG && p.getName().equals("item")) {
                if (url.isEmpty()) url = videoUrl;
                if (!title.isEmpty() && url.startsWith("https://")) result.add(new Episode(id.isEmpty() ? url : id, title, date, url, videoUrl, duration, chapters, episodeArtwork,transcriptUrl,transcriptType));
                item = false;
            }
        }
        return new FeedResult(channelTitle.isEmpty() ? "Podcast" : channelTitle, artwork.isEmpty() ? rssArtwork : artwork, channelDescription, result);
    }
    private static long mediaQuality(XmlPullParser parser) {
        try { String rate = parser.getAttributeValue(null, "bitrate"); if (rate != null) return Math.min(1_000_000_000L, Math.max(0, Long.parseLong(rate))); } catch (Exception ignored) { }
        try { String height = parser.getAttributeValue(null, "height"); if (height != null) return Math.min(100_000L, Math.max(0, Long.parseLong(height))); } catch (Exception ignored) { }
        return 0;
    }
    static String key(String id) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder(); for (byte b : digest) s.append(String.format(Locale.ROOT, "%02x", b & 255)); return s.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    long position(String id) { return prefs.getLong("pos:" + key(id), 0); }
    void savePosition(String id, long pos) { if (!id.isEmpty()) prefs.edit().putLong("pos:" + key(id), Math.max(0, pos)).apply(); }
    boolean listened(String id) { return prefs.getBoolean("played:" + key(id), false); }
    void setListened(String id, boolean value) { prefs.edit().putBoolean("played:" + key(id), value).apply(); if (value) savePosition(id, 0); }
    boolean favorite(String id) { return prefs.getBoolean("favorite:" + key(id), false); }
    void setFavorite(LibraryEntry entry, boolean value) { remember(entry); prefs.edit().putBoolean("favorite:" + key(entry.episode.id), value).apply(); }
    void remember(LibraryEntry entry) {
        try { prefs.edit().putString("entry:" + key(entry.episode.id), entry.json().toString()).putLong("touched:" + key(entry.episode.id), System.currentTimeMillis()).apply(); } catch (Exception ignored) { }
    }
    List<LibraryEntry> remembered() {
        Set<String> feeds = new HashSet<>(); for (Podcast p : podcasts()) feeds.add(p.feed);
        List<LibraryEntry> list = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) if (entry.getKey().startsWith("entry:")) {
            try { LibraryEntry value = LibraryEntry.from(new org.json.JSONObject((String) entry.getValue())); if (feeds.contains(value.podcast.feed)) list.add(value); } catch (Exception ignored) { }
        }
        list.sort((a, b) -> Long.compare(prefs.getLong("touched:" + key(b.episode.id), 0), prefs.getLong("touched:" + key(a.episode.id), 0))); return list;
    }
    List<LibraryEntry> searchLibrary(String term) {
        String query = term.toLowerCase(Locale.ROOT).trim(); LinkedHashMap<String, LibraryEntry> result = new LinkedHashMap<>();
        for (LibraryEntry entry : remembered()) if (entry.episode.title.toLowerCase(Locale.ROOT).contains(query)) result.put(entry.episode.id, entry);
        for (Podcast p : podcasts()) for (Episode e : new Repository(context, p.feed).cached()) if (e.title.toLowerCase(Locale.ROOT).contains(query)) result.putIfAbsent(e.id, new LibraryEntry(e, p));
        return new ArrayList<>(result.values());
    }
    List<LibraryEntry> queue() {
        List<LibraryEntry> result = new ArrayList<>();
        try { JSONArray array = new JSONArray(prefs.getString("queue", "[]")); for (int i = 0; i < array.length(); i++) result.add(LibraryEntry.from(array.getJSONObject(i))); } catch (Exception ignored) { }
        return result;
    }
    private void saveQueue(List<LibraryEntry> list) throws Exception { JSONArray array = new JSONArray(); for (LibraryEntry entry : list) array.put(entry.json()); if (!prefs.edit().putString("queue", array.toString()).commit()) throw new IOException("No se pudo guardar la cola"); }
    synchronized boolean enqueue(LibraryEntry entry) throws Exception {
        List<LibraryEntry> list = queue(); for (LibraryEntry existing : list) if (existing.episode.id.equals(entry.episode.id)) return false;
        if (list.size() >= 200) throw new IOException("La cola admite hasta 200 episodios"); remember(entry); list.add(entry); saveQueue(list); return true;
    }
    synchronized void dequeue(String id) throws Exception { List<LibraryEntry> list = queue(); list.removeIf(e -> e.episode.id.equals(id)); saveQueue(list); }
    synchronized void moveQueue(String id, int direction) throws Exception {
        List<LibraryEntry> list = queue(); for (int i = 0; i < list.size(); i++) if (list.get(i).episode.id.equals(id)) { int target = i + direction; if (target >= 0 && target < list.size()) { Collections.swap(list, i, target); saveQueue(list); } return; }
    }
    static final class StoredDownload {
        final long id, bytes; final String title; final int status; final boolean played;
        StoredDownload(long id, long bytes, String title, int status, boolean played) { this.id = id; this.bytes = bytes; this.title = title; this.status = status; this.played = played; }
    }
    List<StoredDownload> storedDownloads() {
        List<StoredDownload> result = new ArrayList<>(); Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) if (entry.getKey().startsWith("download:") && entry.getValue() instanceof Long) {
            long id = (Long) entry.getValue();
            try (Cursor c = downloads.query(new DownloadManager.Query().setFilterById(id))) {
                if (c != null && c.moveToFirst()) result.add(new StoredDownload(id, Math.max(0, c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))), c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)), c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)), prefs.getBoolean("played:" + entry.getKey().substring(9), false)));
            }
        }
        result.sort(java.util.Comparator.comparing(d -> d.title == null ? "" : d.title)); return result;
    }
    void removeStoredDownload(long id) {
        downloads.remove(id); SharedPreferences.Editor edit = prefs.edit();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) if (entry.getKey().startsWith("download:") && entry.getValue() instanceof Long && ((Long) entry.getValue()) == id) edit.remove(entry.getKey()); edit.apply();
    }
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
    Uri playbackUri(Episode e) { LibraryEntry entry=entry(e.id); Uri prepared=entry==null?null:GeminiPreparation.readyUri(context,this,entry); if(prepared!=null)return prepared; Uri local = localUri(e); return local != null ? local : Uri.parse(e.url); }
    void download(Episode e) { download(e, prefs.getBoolean("wifiOnly", false), false); }
    void download(Episode e, boolean wifiOnly, boolean automatic) {
        if (downloadStatus(e) == DownloadManager.STATUS_SUCCESSFUL || downloadStatus(e) == DownloadManager.STATUS_RUNNING || downloadStatus(e) == DownloadManager.STATUS_PENDING || downloadStatus(e) == DownloadManager.STATUS_PAUSED) return;
        long old = downloadId(e); if (old >= 0) downloads.remove(old);
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(e.url)).setTitle(e.title).setDescription("Onda")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedNetworkTypes(wifiOnly ? DownloadManager.Request.NETWORK_WIFI : DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
            .setAllowedOverMetered(!wifiOnly).setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, "episodes", key(e.id) + "-" + System.currentTimeMillis() + (e.url.equals(e.videoUrl) ? ".mp4" : ".audio"));
        long id = downloads.enqueue(r); prefs.edit().putLong("download:" + key(e.id), id).putBoolean("automatic:" + key(e.id), automatic).apply();
    }
    long automaticDownloadBytes() {
        long bytes=0;
        for(StoredDownload download:storedDownloads()) {
            boolean automatic=false;
            for(Map.Entry<String,?> item:prefs.getAll().entrySet())if(item.getKey().startsWith("download:") && item.getValue() instanceof Long && (Long)item.getValue()==download.id)automatic=prefs.getBoolean("automatic:"+item.getKey().substring(9),false);
            if(automatic && download.status!=DownloadManager.STATUS_FAILED)bytes+=download.status==DownloadManager.STATUS_SUCCESSFUL?download.bytes:Math.max(download.bytes,128_000_000L);
        }
        return bytes;
    }
    boolean automaticSpaceAvailable() {long limit=prefs.getLong("automaticBudget",1_000_000_000L);return limit==0 || automaticDownloadBytes()+128_000_000L<=limit;}
    long duration(Episode e) { return prefs.getLong("duration:" + key(e.id), e.durationMs); }
    LibraryEntry entry(String id) { try { return LibraryEntry.from(new org.json.JSONObject(prefs.getString("entry:" + key(id), ""))); } catch (Exception ignored) { return null; } }
    void removeDownload(Episode e) { long id = downloadId(e); if (id >= 0) downloads.remove(id); prefs.edit().remove("download:" + key(e.id)).apply(); }
}
