package es.jesus.ampodcasts;

import static org.junit.Assert.*;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class EmptyLibraryUiTest {
    @Test public void firstLaunchInvitesUserToAddTheirOwnPodcast() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertTrue(new Repository(activity).podcasts().isEmpty());
            View add = findByDescription(activity.getWindow().getDecorView(), "Añadir tu primer podcast");
            assertNotNull(add); add.performClick();
            android.app.Dialog dialog = ShadowDialog.getLatestDialog(); assertNotNull(dialog); assertTrue(dialog.isShowing());
        }
    }
    private View findByDescription(View view, String text) {
        if (text.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int n = 0; n < group.getChildCount(); n++) { View found = findByDescription(group.getChildAt(n), text); if (found != null) return found; } }
        return null;
    }
}
