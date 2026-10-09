package es.jesus.ampodcasts;

import android.content.Context;
import android.content.res.Configuration;

final class UiPreferences {
    static void apply(Context context) {
        float multiplier = context.getSharedPreferences("library", Context.MODE_PRIVATE).getFloat("textScale", 1f);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.fontScale = android.content.res.Resources.getSystem().getConfiguration().fontScale * Math.max(1f, Math.min(1.3f, multiplier));
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
    }
}
