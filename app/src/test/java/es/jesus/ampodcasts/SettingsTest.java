package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsTest {
    @Test public void settingsPersistAndReopenWithSelectedValues() {
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            Switch wifi = (Switch) find(activity.getWindow().getDecorView(), "Descargar solo por Wi‑Fi"); assertNotNull(wifi); assertFalse(wifi.isChecked()); wifi.setChecked(true);
            Switch silence = (Switch) find(activity.getWindow().getDecorView(), "Omitir silencios"); silence.setChecked(true);
            assertTrue(new Repository(activity).prefs.getBoolean("wifiOnly", false)); assertTrue(new Repository(activity).prefs.getBoolean("skipSilence", false));
        }
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            assertTrue(((Switch) find(controller.get().getWindow().getDecorView(), "Descargar solo por Wi‑Fi")).isChecked());
        }
    }
    private View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), text); if (found != null) return found; } }
        return null;
    }
}
