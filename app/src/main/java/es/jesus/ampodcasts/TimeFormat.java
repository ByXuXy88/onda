package es.jesus.ampodcasts;

final class TimeFormat {
    static long parse(String raw) {
        try {
            String[] parts = raw.trim().split(":"); if (parts.length > 3) return 0;
            double seconds = 0; for (int i = 0; i < parts.length; i++) { double part = Double.parseDouble(parts[i]); if (!Double.isFinite(part) || part < 0 || i > 0 && part >= 60) return 0; seconds = seconds * 60 + part; }
            return Double.isFinite(seconds) && seconds > 0 && seconds <= 604800 ? (long) (seconds * 1000) : 0;
        } catch (Exception ignored) { return 0; }
    }
    static String display(long ms, boolean seconds) {
        long total = Math.max(0, ms) / 1000, hours = total / 3600, minutes = total / 60 % 60, rest = total % 60;
        String value = hours > 0 ? hours + " h " + minutes + " min" : minutes + " min";
        return seconds ? value + " " + String.format(java.util.Locale.ROOT, "%02d s", rest) : total < 60 ? rest + " s" : value;
    }
}
