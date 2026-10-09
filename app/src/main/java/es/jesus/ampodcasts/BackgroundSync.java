package es.jesus.ampodcasts;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.net.*;
import android.os.*;
import org.json.JSONArray;
import java.util.*;

final class BackgroundSync {
    static final int JOB_ID = 1601;
    static void schedule(Context context) {
        Repository r = new Repository(context); boolean enabled = r.prefs.getBoolean("deletePlayedDownloads", false);
        for (Podcast p : r.podcasts()) enabled |= r.prefs.getBoolean(ProgramOptions.key("auto", p.feed), false) || r.prefs.getBoolean(ProgramOptions.key("notify", p.feed), false);
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (!enabled) { scheduler.cancel(JOB_ID); return; }
        if (scheduler.getPendingJob(JOB_ID) == null) scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, RefreshJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setRequiresBatteryNotLow(true).setPersisted(true).setPeriodic(6 * 60 * 60 * 1000L).build());
    }
    static void initialize(Context context, String feed) {
        Repository r = new Repository(context, feed); String key = ProgramOptions.key("seen", feed);
        if (r.prefs.contains(key)) return;
        List<Episode> episodes = r.cached(); if (!episodes.isEmpty()) { JSONArray ids = new JSONArray(); for (Episode e : episodes) ids.put(e.id); r.prefs.edit().putString(key, ids.toString()).apply(); }
    }
    static List<Episode> fresh(List<Episode> episodes, Set<String> seen) { List<Episode> result = new ArrayList<>(); for (Episode e : episodes) if (!seen.contains(e.id)) result.add(e); return result; }
    static boolean wifi(Context context) { ConnectivityManager cm = context.getSystemService(ConnectivityManager.class); NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork()); return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI); }
    static void process(Context context, Podcast podcast, List<Episode> episodes) throws Exception {
        Repository r = new Repository(context, podcast.feed); String seenKey = ProgramOptions.key("seen", podcast.feed); boolean baseline = r.prefs.contains(seenKey); Set<String> seen = new HashSet<>(); JSONArray saved = new JSONArray(r.prefs.getString(seenKey, "[]")); for (int i = 0; i < saved.length(); i++) seen.add(saved.getString(i));
        List<Episode> fresh = baseline ? fresh(episodes, seen) : Collections.emptyList();
        if (r.prefs.getBoolean(ProgramOptions.key("auto", podcast.feed), false)) {
            // Pending IDs survive a check on mobile data and are downloaded when Wi-Fi returns.
            String pendingKey = ProgramOptions.key("pending", podcast.feed); Set<String> pending = new LinkedHashSet<>(); JSONArray prior = new JSONArray(r.prefs.getString(pendingKey, "[]")); for (int i = 0; i < prior.length(); i++) pending.add(prior.getString(i)); for (Episode e : fresh) pending.add(e.id);
            int limit = Math.max(1, Math.min(10, r.prefs.getInt(ProgramOptions.key("limit", podcast.feed), 3)));
            List<Episode> candidates = new ArrayList<>(); for (Episode e : episodes) if (pending.contains(e.id) && !r.listened(e.id)) candidates.add(e);
            candidates.sort((a, b) -> Long.compare(date(b.date), date(a.date))); if (candidates.size() > limit) candidates = new ArrayList<>(candidates.subList(0, limit));
            if (wifi(context)) {
                List<Episode> owned = new ArrayList<>(); for (LibraryEntry entry : r.remembered()) if (entry.podcast.feed.equals(podcast.feed) && r.prefs.getBoolean("automatic:" + Repository.key(entry.episode.id), false) && r.downloadStatus(entry.episode) > 0) owned.add(entry.episode);
                owned.sort((a, b) -> Long.compare(date(a.date), date(b.date)));
                for (Episode e : candidates) { if (Thread.currentThread().isInterrupted()) return; if (r.downloadStatus(e) > 0) { pending.remove(e.id); continue; } while (owned.size() >= limit) { Episode removable = null; for (Episode old : owned) if (!old.id.equals(r.prefs.getString("activeEpisode", ""))) { removable = old; break; } if (removable == null) break; r.removeDownload(removable); owned.remove(removable); } if (owned.size() >= limit) continue; r.remember(new LibraryEntry(e, podcast)); r.download(e, true, true); owned.add(e); pending.remove(e.id); }
            }
            JSONArray next = new JSONArray(); for (Episode e : candidates) if (pending.contains(e.id)) next.put(e.id); r.prefs.edit().putString(pendingKey, next.toString()).apply();
        }
        if (!fresh.isEmpty() && r.prefs.getBoolean(ProgramOptions.key("notify", podcast.feed), false)) notify(context, podcast, fresh);
        // Retain rolling history so feeds with rotating order don't generate duplicate alerts.
        for (Episode e : episodes) seen.add(e.id); JSONArray ids = new JSONArray(); for (Episode e : episodes) ids.put(e.id); int count = ids.length(); for (String id : seen) if (count < 5000 && episodes.stream().noneMatch(e -> e.id.equals(id))) { ids.put(id); count++; } r.prefs.edit().putString(seenKey, ids.toString()).apply();
    }
    private static long date(String raw) { try { return new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(raw).getTime(); } catch (Exception ignored) { return 0; } }
    static void cleanup(Context context) {
        Repository r = new Repository(context); if (!r.prefs.getBoolean("deletePlayedDownloads", false)) return;
        String active = r.prefs.getString("activeEpisode", ""); long activeDownload = r.prefs.getLong("download:" + Repository.key(active), -1);
        for (Repository.StoredDownload d : r.storedDownloads()) if (d.played && d.status == DownloadManager.STATUS_SUCCESSFUL && d.id != activeDownload) r.removeStoredDownload(d.id);
    }
    private static void notify(Context context, Podcast podcast, List<Episode> fresh) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class); if (!manager.areNotificationsEnabled()) return;
        manager.createNotificationChannel(new NotificationChannel("episodes", "Nuevos episodios", NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(context, MainActivity.class).putExtra("feed", podcast.feed);
        PendingIntent open = PendingIntent.getActivity(context, podcast.feed.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(podcast.feed.hashCode(), new Notification.Builder(context, "episodes").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(podcast.title).setContentText(fresh.size() == 1 ? fresh.get(0).title : fresh.size() + " nuevos episodios").setContentIntent(open).setAutoCancel(true).build());
    }
}
