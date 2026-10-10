package es.jesus.ampodcasts;

import java.util.*;

final class HomeFeed {
    static List<LibraryEntry> continuing(Repository repository) {
        List<LibraryEntry> result = new ArrayList<>();
        for (LibraryEntry entry : repository.remembered())
            if (!repository.listened(entry.episode.id) && repository.position(entry.episode.id) > 0) result.add(entry);
        return result;
    }
    static List<LibraryEntry> latest(android.content.Context context, Repository repository) {
        Map<String, LibraryEntry> unique = new LinkedHashMap<>();
        for (Podcast podcast : repository.podcasts())
            for (Episode episode : new Repository(context, podcast.feed).cached())
                if (!repository.listened(episode.id) && repository.position(episode.id) == 0)
                    unique.putIfAbsent(episode.id, new LibraryEntry(episode, podcast));
        List<LibraryEntry> result = new ArrayList<>(unique.values());
        result.sort((a, b) -> Long.compare(date(b.episode.date), date(a.episode.date)));
        return result;
    }
    static long date(String raw) {
        try { return new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(raw).getTime(); }
        catch (Exception ignored) { return 0; }
    }
}
