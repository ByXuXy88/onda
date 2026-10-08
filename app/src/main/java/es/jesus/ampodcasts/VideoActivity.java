package es.jesus.ampodcasts;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Color;
import android.os.Bundle;
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
    private ListenableFuture<MediaController> future;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; }); setContentView(root);
        ImageButton back = new ImageButton(this); back.setImageDrawable(new ControlIcon("back", 0xffa8c7fa, dp(24))); back.setBackgroundColor(Color.TRANSPARENT); back.setContentDescription("Volver a la biblioteca"); back.setOnClickListener(v -> finish()); root.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        video = new PlayerView(this); video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); video.setKeepScreenOn(true); root.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView note = new TextView(this); note.setText("Vídeo original del podcast · Al volver, la reproducción continúa en segundo plano"); note.setTextColor(0xffa6abb3); note.setPadding(dp(16), dp(8), dp(16), dp(8)); root.addView(note);
    }
    @Override protected void onStart() {
        super.onStart();
        ListenableFuture<MediaController> pending = new MediaController.Builder(this, new SessionToken(this, new ComponentName(this, PlaybackService.class))).buildAsync(); future = pending;
        pending.addListener(() -> {
            if (isDestroyed() || future != pending) return;
            try { MediaController controller = pending.get(); video.setPlayer(controller); video.setErrorMessageProvider(error -> android.util.Pair.create(0, "No se pudo reproducir este vídeo. Comprueba la conexión y el formato del podcast.")); }
            catch (Exception e) { Toast.makeText(this, "No se pudo conectar al reproductor", Toast.LENGTH_LONG).show(); }
        }, getMainExecutor());
    }
    @Override protected void onStop() { video.setPlayer(null); if (future != null) { MediaController.releaseFuture(future); future = null; } super.onStop(); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
}
