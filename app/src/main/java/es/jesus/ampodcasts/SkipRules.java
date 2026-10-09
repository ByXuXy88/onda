package es.jesus.ampodcasts;

import android.content.SharedPreferences;

/** Explicit per-program offsets. Never attempts to infer advertising from audio. */
final class SkipRules {
    static long offset(SharedPreferences prefs, String kind, String feed) {
        return Math.max(0, Math.min(1800, prefs.getLong(ProgramOptions.key(kind, feed), 0))) * 1000;
    }
    static long initial(long savedPosition, long start, long duration) {
        if (savedPosition > 0 || start <= 0 || duration <= 0 || start >= duration) return savedPosition;
        return start;
    }
    static boolean finish(long position, long duration, long start, long end) {
        return duration > 0 && end > 0 && start + end < duration && position >= duration - end;
    }
    static int seconds(SharedPreferences prefs, boolean forward) {
        int value = prefs.getInt(forward ? "jumpForward" : "jumpBack", forward ? 30 : 15);
        return value == 10 || value == 15 || value == 30 || value == 60 ? value : forward ? 30 : 15;
    }
}
