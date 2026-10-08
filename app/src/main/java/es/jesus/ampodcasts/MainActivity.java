package es.jesus.ampodcasts;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.common.util.concurrent.ListenableFuture;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int PURPLE = 0xffa8c7fa, INK = 0xfff2f2f2, MUTED = 0xffa6abb3;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Repository repository;
    private List<Episode> episodes = new ArrayList<>();
    private LinearLayout list;
    private TextView status, current, time;
    private ImageButton toggle, refresh, allTab, offlineTab, remove;
    private Spinner programs;
    private TextView programTitle, programDescription, cover;
    private List<Podcast> podcasts = new ArrayList<>();
    private int requestGeneration;
    private boolean populating;
    private long lastSearch;
    private SeekBar seek;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private boolean downloadedOnly, seeking, loading;
    private int displayLimit = 40;
    private final List<Runnable> downloadLabels = new ArrayList<>();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updatePlayer(); for (Runnable r : downloadLabels) r.run(); handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repository = new Repository(this);
        List<Podcast> saved = repository.podcasts();
        String selectedFeed = repository.prefs.getString("selectedFeed", "");
        boolean exists = false; for (Podcast p : saved) if (p.feed.equals(selectedFeed)) exists = true;
        if (!exists) selectedFeed = saved.isEmpty() ? "" : saved.get(0).feed;
        repository = new Repository(this, selectedFeed); episodes = repository.cached();
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        setContentView(root);
        LinearLayout head = column(20);
        head.addView(label("TU BIBLIOTECA", 11, PURPLE, true));
        LinearLayout titleRow = row();
        titleRow.addView(label("Onda", 32, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton add = icon("add", "Añadir podcasts"); add.setOnClickListener(v -> addMenu());
        titleRow.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48))); head.addView(titleRow);
        programs = new Spinner(this); head.addView(programs);
        programs.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View v, int index, long id) {
                if (!populating && index < podcasts.size() && !podcasts.get(index).feed.equals(repository.feed)) switchPodcast(podcasts.get(index));
            }
        });
        LinearLayout show = row(); show.setPadding(0, dp(12), 0, dp(12));
        cover = label("+", 32, Color.WHITE, true); cover.setGravity(Gravity.CENTER); cover.setBackground(bg(0xff183e37, 18));
        show.addView(cover, new LinearLayout.LayoutParams(dp(86), dp(86)));
        LinearLayout description = column(0); description.setPadding(dp(16), 0, 0, 0);
        programTitle = label("Tu biblioteca", 23, INK, true); programTitle.setMaxLines(2); description.addView(programTitle);
        programDescription = label("Tu biblioteca de podcasts", 14, MUTED, false); description.addView(programDescription);
        show.addView(description, new LinearLayout.LayoutParams(0, -2, 1)); head.addView(show);
        remove = icon("close", "Quitar programa de la biblioteca"); remove.setOnClickListener(v -> removePodcast());
        show.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout tabs = row();
        allTab = icon("list", "Todos los episodios"); offlineTab = icon("download", "Tus descargas");
        ImageButton all = allTab, offline = offlineTab;
        all.setOnClickListener(v -> { downloadedOnly = false; displayLimit = 40; render(); }); offline.setOnClickListener(v -> { downloadedOnly = true; displayLimit = 40; render(); });
        tabs.addView(all, new LinearLayout.LayoutParams(0, dp(48), 1)); tabs.addView(offline, new LinearLayout.LayoutParams(0, dp(48), 1));
        refresh = icon("refresh", "Actualizar episodios"); refresh.setContentDescription("Actualizar episodios"); refresh.setOnClickListener(v -> load()); tabs.addView(refresh, new LinearLayout.LayoutParams(dp(55), dp(48)));
        head.addView(tabs); status = label("", 12, MUTED, false); status.setPadding(0, dp(8), 0, 0); head.addView(status); root.addView(head);
        ScrollView scroll = new ScrollView(this); list = column(16); scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout player = column(16); player.setBackgroundColor(Color.BLACK);
        current = label("Elige un episodio", 15, INK, true); current.setMaxLines(2); player.addView(current);
        seek = new SeekBar(this); seek.setMax(1000); player.addView(seek, new LinearLayout.LayoutParams(-1, dp(30)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { }
            public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            public void onStopTrackingTouch(SeekBar s) { if (controller != null && controller.getDuration() > 0) controller.seekTo(controller.getDuration() * s.getProgress() / 1000); seeking = false; }
        });
        time = label("00:00 / 00:00", 12, MUTED, false); time.setGravity(Gravity.CENTER); player.addView(time);
        LinearLayout controls = row(); ImageButton back = icon("rewind", "Retroceder 15 segundos"), forward = icon("forward", "Avanzar 30 segundos"); toggle = icon("play", "Reproducir");
        back.setOnClickListener(v -> { if (controller != null) controller.seekTo(Math.max(0, controller.getCurrentPosition() - 15000)); });
        forward.setOnClickListener(v -> { if (controller != null) controller.seekTo(Math.min(Math.max(0, controller.getDuration() > 0 ? controller.getDuration() : Long.MAX_VALUE), controller.getCurrentPosition() + 30000)); });
        toggle.setOnClickListener(v -> { if (controller == null) return; if (controller.isPlaying()) controller.pause(); else { if (controller.getPlaybackState() == Player.STATE_ENDED) controller.seekTo(0); controller.prepare(); controller.play(); } });
        controls.addView(back, new LinearLayout.LayoutParams(0, dp(48), 1)); controls.addView(toggle, new LinearLayout.LayoutParams(0, dp(48), 2)); controls.addView(forward, new LinearLayout.LayoutParams(0, dp(48), 1)); player.addView(controls); root.addView(player);
        populatePrograms(); render();
        if (!podcasts.isEmpty()) connectPlayer();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        load();
    }
    @Override protected void onStart() { super.onStart(); handler.post(tick); }
    @Override protected void onStop() { handler.removeCallbacks(tick); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); worker.shutdownNow(); if (controllerFuture != null) MediaController.releaseFuture(controllerFuture); super.onDestroy(); }
    private void connectPlayer() {
        if (controllerFuture != null) return;
        controllerFuture = new MediaController.Builder(this, new SessionToken(this, new ComponentName(this, PlaybackService.class))).buildAsync();
        controllerFuture.addListener(() -> {
            if (isDestroyed()) return;
            try {
                controller = controllerFuture.get();
                controller.addListener(new Player.Listener() {
                    @Override public void onPlayerError(PlaybackException e) { status.setText("No se pudo reproducir. Comprueba la conexión o descarga el episodio."); }
                });
                if (controller.getCurrentMediaItem() == null) {
                    String last = repository.prefs.getString("last", "");
                    for (Episode e : episodes) if (e.id.equals(last)) { select(e, false); break; }
                }
                updatePlayer();
            } catch (Exception e) { status.setText("No se pudo conectar al reproductor. Cierra y abre la app."); }
        }, getMainExecutor());
    }
    private Podcast currentPodcast() {
        for (Podcast p : podcasts) if (p.feed.equals(repository.feed)) return p;
        return new Podcast("Tu biblioteca", "");
    }
    private void populatePrograms() {
        populating = true; podcasts = repository.podcasts(); List<String> titles = new ArrayList<>(); int selected = 0;
        for (int n = 0; n < podcasts.size(); n++) { titles.add(podcasts.get(n).title); if (podcasts.get(n).feed.equals(repository.feed)) selected = n; }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, titles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); programs.setAdapter(adapter); if (!podcasts.isEmpty()) programs.setSelection(selected);
        programs.setVisibility(podcasts.isEmpty() ? View.GONE : View.VISIBLE);
        updateProgramHeader(); populating = false;
    }
    private void updateProgramHeader() {
        Podcast p = currentPodcast(); programTitle.setText(p.title); programDescription.setText(p.feed.isEmpty() ? "Añade tu primer podcast con +" : "Podcast de tu biblioteca");
        remove.setVisibility(p.feed.isEmpty() ? View.GONE : View.VISIBLE);
        cover.setText(p.feed.isEmpty() ? "+" : p.title.length() < 2 ? p.title : p.title.substring(0, 2).toUpperCase(Locale.ROOT));
    }
    private void switchPodcast(Podcast podcast) {
        requestGeneration++; loading = false;
        repository = new Repository(this, podcast.feed); repository.prefs.edit().putString("selectedFeed", podcast.feed).apply();
        episodes = repository.cached(); displayLimit = 40; updateProgramHeader(); render();
        if (!podcast.feed.isEmpty()) connectPlayer();
        load();
    }
    private void addMenu() {
        new AlertDialog.Builder(this).setTitle("Añadir a tu biblioteca")
            .setItems(new String[]{"Buscar podcast por nombre", "Añadir podcast por enlace RSS", "Importar biblioteca OPML"}, (dialog, which) -> {
                if (which == 0) searchPodcasts();
                else if (which == 1) addRss();
                else { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, 42); }
            }).setNegativeButton("Cerrar", null).show();
    }
    private void searchPodcasts() {
        EditText input = new EditText(this); input.setHint("Nombre del podcast"); input.setSingleLine(true);
        LinearLayout content = column(16); LinearLayout search = row(); search.addView(input, new LinearLayout.LayoutParams(0, dp(56), 1));
        ImageButton find = icon("search", "Buscar podcasts"); search.addView(find, new LinearLayout.LayoutParams(dp(48), dp(48))); content.addView(search);
        TextView message = label("Busca por nombre en el catálogo público de Apple Podcasts.", 13, MUTED, false); content.addView(message);
        ScrollView scroll = new ScrollView(this); LinearLayout results = column(0); scroll.addView(results); content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(260)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Buscar podcasts").setView(content).setNegativeButton("Cerrar", null).create();
        find.setOnClickListener(v -> {
            String term = input.getText().toString().trim(); if (term.length() < 2) { input.setError("Escribe al menos dos letras"); return; }
            long now = SystemClock.elapsedRealtime(); if (lastSearch != 0 && now - lastSearch < 3000) { message.setText("Espera un momento antes de otra búsqueda."); return; }
            lastSearch = now; find.setEnabled(false); message.setText("Buscando…"); results.removeAllViews();
            worker.execute(() -> {
                try {
                    List<Podcast> found = Catalog.search(term);
                    runOnUiThread(() -> {
                        if (isDestroyed() || !dialog.isShowing()) return; find.setEnabled(true);
                        message.setText(found.isEmpty() ? "No hay resultados con RSS público HTTPS. Puedes añadirlo por enlace." : "Toca un programa para añadirlo a tu biblioteca.");
                        for (Podcast p : found) {
                            LinearLayout card = column(8); TextView title = label(p.title, 16, INK, true); card.addView(title);
                            TextView source = label(android.net.Uri.parse(p.feed).getHost(), 12, MUTED, false); card.addView(source);
                            card.setBackground(bg(0xff111318, 12)); card.setPadding(dp(12), dp(12), dp(12), dp(12)); card.setContentDescription("Añadir " + p.title);
                            card.setOnClickListener(w -> {
                                for (Podcast existing : repository.podcasts()) if (existing.feed.equals(p.feed)) { Toast.makeText(this, "Ya está en tu biblioteca", Toast.LENGTH_SHORT).show(); return; }
                                try { repository.addPodcast(p); dialog.dismiss(); switchPodcast(p); populatePrograms(); render(); }
                                catch (Exception e) { message.setText("No se pudo guardar el programa"); }
                            });
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8); results.addView(card, params);
                        }
                    });
                } catch (Exception e) { runOnUiThread(() -> { if (isDestroyed() || !dialog.isShowing()) return; find.setEnabled(true); message.setText("No se pudo consultar el catálogo. Comprueba la conexión o añade el RSS manualmente."); }); }
            });
        }); dialog.show();
    }
    private void removePodcast() {
        Podcast target = currentPodcast(); if (target.feed.isEmpty()) return;
        CheckBox delete = new CheckBox(this); delete.setText("Eliminar también los episodios descargados");
        LinearLayout content = column(16); content.addView(label("Quitar " + target.title + " de tu biblioteca.", 16, INK, false)); content.addView(delete);
        new AlertDialog.Builder(this).setTitle("Quitar programa").setView(content).setNegativeButton("Cancelar", null).setPositiveButton("Quitar", (d, w) -> {
            try {
                if (controller != null && controller.getCurrentMediaItem() != null) {
                    String id = controller.getCurrentMediaItem().mediaId;
                    for (Episode e : episodes) if (e.id.equals(id)) { controller.pause(); controller.clearMediaItems(); break; }
                }
                repository.removePodcast(target.feed, delete.isChecked());
                List<Podcast> remaining = repository.podcasts();
                switchPodcast(remaining.isEmpty() ? new Podcast("Tu biblioteca", "") : remaining.get(0)); populatePrograms(); render();
                if (controller != null && controller.getCurrentMediaItem() == null) current.setText("Elige un episodio");
            } catch (Exception e) { Toast.makeText(this, "No se pudo quitar el programa", Toast.LENGTH_LONG).show(); }
        }).show();
    }
    private void addRss() {
        EditText input = new EditText(this); input.setHint("https://… / enlace RSS"); input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout box = column(20); box.addView(label("Pega el enlace RSS público del podcast.", 14, MUTED, false)); box.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Añadir podcast").setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Añadir", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String feed;
            try { feed = Repository.normalizeFeed(input.getText().toString()); }
            catch (Exception e) { input.setError(e.getMessage()); return; }
            for (Podcast p : repository.podcasts()) if (p.feed.equals(feed)) { Toast.makeText(this, "Este podcast ya está en tu biblioteca", Toast.LENGTH_SHORT).show(); dialog.dismiss(); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); input.setEnabled(false); status.setText("Comprobando el podcast…");
            worker.execute(() -> {
                try {
                    Repository added = new Repository(this, feed); Repository.FeedResult result = added.refreshFeed();
                    runOnUiThread(() -> {
                        if (isDestroyed() || !dialog.isShowing()) return;
                        try { Podcast p = new Podcast(result.title, feed); repository.addPodcast(p); dialog.dismiss(); populatePrograms(); switchPodcast(p); populatePrograms(); }
                        catch (Exception e) { input.setError("No se pudo guardar el podcast"); input.setEnabled(true); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> { if (isDestroyed() || !dialog.isShowing()) return; input.setEnabled(true); input.setError("No se pudo leer el RSS. Comprueba el enlace y la conexión."); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); status.setText("No se añadió el podcast"); });
                }
            });
        })); dialog.show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 42 || result != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData(); status.setText("Importando biblioteca…");
        worker.execute(() -> {
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                List<Podcast> imported = Repository.parseOpml(in);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    try { int added = 0; for (Podcast p : imported) if (repository.addPodcast(p)) added++;
                        if (repository.feed.isEmpty() && !repository.podcasts().isEmpty()) { switchPodcast(repository.podcasts().get(0)); }
                        populatePrograms();
                        new AlertDialog.Builder(this).setTitle("Biblioteca importada").setMessage(added + " programas añadidos. Selecciónalos en la lista superior para cargar sus episodios. Los duplicados se han omitido.").setPositiveButton("Aceptar", null).show(); render();
                    } catch (Exception e) { status.setText("No se pudo guardar toda la biblioteca. Comprueba los programas añadidos."); populatePrograms(); }
                });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) new AlertDialog.Builder(this).setTitle("No se pudo importar").setMessage("Selecciona un archivo OPML con enlaces RSS públicos HTTPS, hasta 100 programas.").setPositiveButton("Aceptar", null).show(); }); }
        });
    }
    private void load() {
        if (repository.feed.isEmpty()) { refresh.setEnabled(false); render(); return; }
        if (loading) return; loading = true; final int generation = ++requestGeneration; final Repository target = repository;
        refresh.setEnabled(false); status.setText("Buscando nuevos episodios…");
        worker.execute(() -> {
            try { List<Episode> result = target.refresh(); runOnUiThread(() -> { if (isDestroyed() || generation != requestGeneration) return; episodes = result; loading = false; refresh.setEnabled(true); render(); }); }
            catch (Exception e) { runOnUiThread(() -> { if (isDestroyed() || generation != requestGeneration) return; loading = false; refresh.setEnabled(true); status.setText(episodes.isEmpty() ? "No se pudo cargar el programa. Comprueba el enlace y la conexión." : "Sin conexión. Mostrando la biblioteca guardada."); }); }
        });
    }
    private void render() {
        list.removeAllViews(); downloadLabels.clear(); int count = 0;
        if (podcasts.isEmpty()) {
            refresh.setEnabled(false); status.setText("Tu biblioteca está vacía");
            list.addView(label("Encuentra tus podcasts", 24, INK, true));
            list.addView(label("Toca + para buscar por nombre, pegar un enlace RSS o importar tu biblioteca OPML.", 16, MUTED, false));
            ImageButton add = icon("add", "Añadir tu primer podcast"); add.setOnClickListener(v -> addMenu()); list.addView(add, new LinearLayout.LayoutParams(dp(64), dp(64))); return;
        }
        allTab.setAlpha(downloadedOnly ? .45f : 1f); offlineTab.setAlpha(downloadedOnly ? 1f : .45f);
        for (Episode e : episodes) {
            if (downloadedOnly && repository.downloadStatus(e) < 0) continue;
            count++;
            if (count > displayLimit) continue;
            LinearLayout card = column(14); GradientDrawable surface = bg(Color.BLACK, 16); surface.setStroke(dp(1), 0xff25272b); card.setBackground(surface);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2); cardParams.bottomMargin = dp(10);
            card.addView(label(prettyDate(e.date), 11, MUTED, false));
            TextView title = label(e.title, 16, INK, true); title.setPadding(0, dp(4), 0, dp(6)); card.addView(title);
            LinearLayout actions = row(); ImageButton play = icon("play", "Escuchar episodio"), download = icon("download", "Descargar episodio");
            TextView downloadState = label("", 11, MUTED, false);
            play.setOnClickListener(v -> select(e, true));
            download.setOnClickListener(v -> {
                int state = repository.downloadStatus(e);
                if (state == DownloadManager.STATUS_SUCCESSFUL || state == DownloadManager.STATUS_RUNNING || state == DownloadManager.STATUS_PENDING || state == DownloadManager.STATUS_PAUSED) {
                    new AlertDialog.Builder(this).setTitle(state == DownloadManager.STATUS_SUCCESSFUL ? "¿Eliminar la descarga?" : "¿Cancelar la descarga?")
                        .setMessage(e.title).setNegativeButton("Conservar", null).setPositiveButton("Eliminar", (d, w) -> { repository.removeDownload(e); render(); }).show();
                } else {
                    try { repository.download(e); Toast.makeText(this, "Descarga iniciada", Toast.LENGTH_SHORT).show(); }
                    catch (Exception ex) { Toast.makeText(this, "No se pudo iniciar la descarga", Toast.LENGTH_LONG).show(); }
                }
            });
            Runnable dl = () -> { int s = repository.downloadStatus(e); boolean busy = s == DownloadManager.STATUS_RUNNING || s == DownloadManager.STATUS_PENDING || s == DownloadManager.STATUS_PAUSED;
                setIcon(download, s == DownloadManager.STATUS_SUCCESSFUL ? "check" : s == DownloadManager.STATUS_FAILED ? "refresh" : busy ? "close" : "download", s == DownloadManager.STATUS_SUCCESSFUL ? "Eliminar descarga" : busy ? "Cancelar descarga" : "Descargar episodio");
                downloadState.setText(s == DownloadManager.STATUS_SUCCESSFUL ? "Disponible sin conexión" : s == DownloadManager.STATUS_FAILED ? "Descarga fallida · toca para reintentar" : busy ? "Descargando…" : ""); };
            dl.run(); downloadLabels.add(dl);
            actions.addView(play, new LinearLayout.LayoutParams(0, dp(48), 1)); actions.addView(download, new LinearLayout.LayoutParams(0, dp(48), 1)); card.addView(actions); card.addView(downloadState); list.addView(card, cardParams);
        }
        status.setText(downloadedOnly ? count + " episodios en descargas" : episodes.size() + " episodios · " + currentPodcast().title);
        if (count == 0) list.addView(label(downloadedOnly ? "Tus descargas aparecerán aquí.\nDescarga un episodio desde Episodios." : "Conéctate a Internet para cargar este programa.", 16, MUTED, false));
        if (count > displayLimit) { ImageButton more = icon("expand", "Mostrar más episodios"); more.setOnClickListener(v -> { displayLimit += 40; render(); }); list.addView(more); }
    }
    private void select(Episode e, boolean start) {
        if (controller == null) { Toast.makeText(this, "Conectando al reproductor…", Toast.LENGTH_SHORT).show(); return; }
        MediaItem existing = controller.getCurrentMediaItem();
        if (existing == null || !existing.mediaId.equals(e.id) || repository.localUri(e) != null) {
            long startPosition = existing != null && existing.mediaId.equals(e.id) ? controller.getCurrentPosition() : repository.position(e.id);
            if (controller.getPlaybackState() == Player.STATE_ENDED && existing != null && existing.mediaId.equals(e.id)) startPosition = 0;
            MediaMetadata meta = new MediaMetadata.Builder().setTitle(e.title).setArtist(currentPodcast().title).setAlbumTitle(currentPodcast().title).build();
            controller.setMediaItem(new MediaItem.Builder().setMediaId(e.id).setUri(repository.playbackUri(e)).setMediaMetadata(meta).build(), startPosition);
        } else if (controller.getPlaybackState() == Player.STATE_ENDED) controller.seekTo(0);
        controller.prepare(); if (start) controller.play(); updatePlayer();
    }
    private void updatePlayer() {
        boolean has = controller != null && controller.getCurrentMediaItem() != null;
        toggle.setEnabled(has); seek.setEnabled(has);
        if (!has) return;
        current.setText(controller.getMediaMetadata().title); long duration = Math.max(0, controller.getDuration()), position = Math.max(0, controller.getCurrentPosition());
        if (!seeking) seek.setProgress(duration > 0 ? (int) (position * 1000 / duration) : 0);
        time.setText(clock(position) + " / " + clock(duration));
        setIcon(toggle, controller.isPlaying() || controller.getPlayWhenReady() ? "pause" : "play", controller.isPlaying() || controller.getPlayWhenReady() ? "Pausar" : "Reproducir");
    }
    private String clock(long ms) { return String.format(Locale.ROOT, "%02d:%02d", ms / 60000, (ms / 1000) % 60); }
    private String prettyDate(String raw) {
        try { SimpleDateFormat in = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US); Date d = in.parse(raw); return new SimpleDateFormat("d MMM yyyy", new Locale("es", "ES")).format(d); }
        catch (Exception e) { return raw; }
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout column(int padding) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(padding), dp(padding), dp(padding), dp(padding)); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView label(String text, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(null, Typeface.BOLD); return t; }
    private ImageButton icon(String type, String description) {
        ImageButton b = new ImageButton(this); b.setBackground(bg(0xff111318, 24)); b.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE); setIcon(b, type, description); return b;
    }
    private void setIcon(ImageButton button, String type, String description) {
        if (!type.equals(button.getTag())) { button.setImageDrawable(new ControlIcon(type, PURPLE, dp(24))); button.setTag(type); }
        button.setContentDescription(description); button.setTooltipText(description);
    }
    private GradientDrawable bg(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
}
