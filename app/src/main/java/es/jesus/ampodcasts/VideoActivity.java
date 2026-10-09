package es.jesus.ampodcasts;

import android.app.*;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.util.Rational;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import androidx.media3.ui.PlayerView;
import com.google.common.util.concurrent.ListenableFuture;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class VideoActivity extends Activity {
    private PlayerView video;
    private LinearLayout toolbar;
    private TextView time;
    private ListenableFuture<MediaController> future;
    private MediaController controller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { public void run() { updateTime(); handler.postDelayed(this, 1000); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); UiPreferences.apply(this); getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; }); setContentView(root);
        toolbar = new LinearLayout(this); toolbar.setOrientation(LinearLayout.HORIZONTAL); root.addView(toolbar);
        button("back", "Volver a la biblioteca", this::finish); button("video", "Abrir ventana flotante", this::enterPip); button("settings", "Velocidad de reproducción", () -> PlaybackTools.speed(this));
        video = new PlayerView(this); video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); video.setKeepScreenOn(true); root.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
        time = new TextView(this); time.setText("0 min 00 s / Duración aún no disponible"); time.setTextColor(0xffa6abb3); time.setPadding(dp(16), dp(8), dp(16), dp(8)); root.addView(time);
    }
    private void button(String icon, String label, Runnable action) { ImageButton button = new ImageButton(this); button.setImageDrawable(new ControlIcon(icon, 0xffa8c7fa, dp(24))); button.setBackgroundColor(Color.TRANSPARENT); button.setContentDescription(label); button.setTooltipText(label); button.setOnClickListener(v -> action.run()); toolbar.addView(button, new LinearLayout.LayoutParams(dp(48), dp(48))); }
    @Override protected void onStart() {
        super.onStart(); handler.post(tick);
        ListenableFuture<MediaController> pending = new MediaController.Builder(this, new SessionToken(this, new ComponentName(this, PlaybackService.class))).buildAsync(); future = pending;
        pending.addListener(() -> {
            if (isDestroyed() || future != pending) return;
            try { controller = pending.get(); video.setPlayer(controller); video.setErrorMessageProvider(error -> android.util.Pair.create(0, "No se pudo reproducir este vídeo. Comprueba la conexión y el formato del podcast.")); controller.addListener(new Player.Listener() { @Override public void onIsPlayingChanged(boolean playing) { updatePip(); } @Override public void onVideoSizeChanged(VideoSize size) { updatePip(); } }); updatePip(); updateTime(); }
            catch (Exception e) { Toast.makeText(this, "No se pudo conectar al reproductor", Toast.LENGTH_LONG).show(); }
        }, getMainExecutor());
    }
    private boolean pipAllowed() { return getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) && new Repository(this).prefs.getBoolean("videoPip", true); }
    private PictureInPictureParams params() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        VideoSize size = controller == null ? VideoSize.UNKNOWN : controller.getVideoSize(); float ratio = size.height > 0 ? size.width * size.pixelWidthHeightRatio / size.height : 16f / 9f; ratio = Math.max(.42f, Math.min(2.39f, ratio)); builder.setAspectRatio(new Rational((int) (ratio * 1000), 1000));
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(pipAllowed() && controller != null && controller.isPlaying() && controller.getMediaMetadata().extras != null && controller.getMediaMetadata().extras.getBoolean("video", false)); return builder.build();
    }
    private void updatePip() { if (getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) setPictureInPictureParams(params()); }
    private void enterPip() { if (!pipAllowed()) { Toast.makeText(this, "La ventana flotante está desactivada o no está disponible", Toast.LENGTH_LONG).show(); return; } try { if (!enterPictureInPictureMode(params())) Toast.makeText(this, "Android no permite abrir la ventana flotante", Toast.LENGTH_LONG).show(); } catch (IllegalStateException ignored) { Toast.makeText(this, "No se pudo abrir la ventana flotante", Toast.LENGTH_LONG).show(); } }
    @Override protected void onUserLeaveHint() { super.onUserLeaveHint(); if (Build.VERSION.SDK_INT < 31 && controller != null && controller.isPlaying() && pipAllowed()) enterPip(); }
    @Override public void onPictureInPictureModeChanged(boolean pip, Configuration config) { super.onPictureInPictureModeChanged(pip, config); toolbar.setVisibility(pip ? View.GONE : View.VISIBLE); time.setVisibility(pip ? View.GONE : View.VISIBLE); video.setUseController(!pip); }
    private void updateTime() { if (controller == null) return; long position = Math.max(0, controller.getCurrentPosition()), duration = controller.getDuration(); time.setText(TimeFormat.display(position, true) + " / " + (duration > 0 ? TimeFormat.display(duration, true) + " · Quedan " + TimeFormat.display(Math.max(0, duration - position), true) : "Duración aún no disponible")); }
    @Override protected void onStop() { handler.removeCallbacks(tick); video.setPlayer(null); controller = null; if (future != null) { MediaController.releaseFuture(future); future = null; } super.onStop(); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
}
