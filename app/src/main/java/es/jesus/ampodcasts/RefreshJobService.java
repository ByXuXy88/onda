package es.jesus.ampodcasts;

import android.app.job.*;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.*;

public final class RefreshJobService extends JobService {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> task;
    private JobParameters running;
    @Override public boolean onStartJob(JobParameters params) {
        running = params;
        task = worker.submit(() -> {
            boolean failed = false; Repository r = new Repository(this);
            for (Podcast p : r.podcasts()) {
                if (Thread.currentThread().isInterrupted()) return;
                if (!r.prefs.getBoolean(ProgramOptions.key("auto", p.feed), false) && !r.prefs.getBoolean(ProgramOptions.key("notify", p.feed), false)) continue;
                try { BackgroundSync.process(this, p, new Repository(this, p.feed).refresh()); } catch (Exception ignored) { failed = true; }
            }
            BackgroundSync.cleanup(this); final boolean retry = failed;
            new Handler(Looper.getMainLooper()).post(() -> { if (running == params) { running = null; jobFinished(params, retry); } });
        }); return true;
    }
    @Override public boolean onStopJob(JobParameters params) { running = null; if (task != null) task.cancel(true); return true; }
    @Override public void onDestroy() { if (task != null) task.cancel(true); worker.shutdownNow(); super.onDestroy(); }
}
