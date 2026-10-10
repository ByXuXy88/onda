package es.jesus.ampodcasts;

import android.content.SharedPreferences;

/** Per-program overrides fall back to the user's global playback settings. */
final class ListeningPreferences {
    static float speed(SharedPreferences prefs, String feed) {
        float value = prefs.getFloat(ProgramOptions.key("programSpeed", feed), prefs.getFloat("speed", 1f));
        return Float.isFinite(value) && value >= .5f && value <= 3f ? value : 1f;
    }
    static boolean silence(SharedPreferences prefs, String feed) {
        return prefs.getBoolean(ProgramOptions.key("programSilence", feed), prefs.getBoolean("skipSilence", false));
    }
    static float sleepVolume(long remaining, boolean enabled) {
        return !enabled || remaining < 0 || remaining >= 30000 ? 1f : Math.max(0f, remaining / 30000f);
    }
}
