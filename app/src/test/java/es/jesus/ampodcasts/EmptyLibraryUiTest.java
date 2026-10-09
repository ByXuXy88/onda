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
    @Test public void wideControlsKeepSquare24DpIcons() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);
            ImageButton button = (ImageButton) findByDescription(root, "Añadir podcasts");
            int size = (int) (24 * activity.getResources().getDisplayMetrics().density + .5f);
            assertEquals(button.getWidth(), button.getHeight());
            assertEquals(size, button.getDrawable().getIntrinsicWidth());
            assertEquals(size, button.getDrawable().getIntrinsicHeight());
            assertEquals(size, button.getDrawable().getBounds().width());
            assertEquals(size, button.getDrawable().getBounds().height());
            assertEquals(ImageView.ScaleType.CENTER_INSIDE, button.getScaleType());
        }
    }
    private View findByDescription(View view, String text) {
        if (text.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int n = 0; n < group.getChildCount(); n++) { View found = findByDescription(group.getChildAt(n), text); if (found != null) return found; } }
        return null;
    }
}
