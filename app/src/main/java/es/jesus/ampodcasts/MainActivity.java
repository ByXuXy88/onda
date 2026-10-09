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
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    private ArtworkStore artworkStore;
    private Repository repository;
    private List<Episode> episodes = new ArrayList<>();
    private LinearLayout list;
    private TextView status, current, time;
    private ImageButton toggle, refresh, allTab, offlineTab, remove;
    private Spinner programs;
    private TextView programTitle, programDescription, cover;
    private ImageView coverImage, playerArtwork;
    private TextView playerProgram;
    private ImageButton sleep;
    private List<Podcast> podcasts = new ArrayList<>();
    private int requestGeneration;
    private boolean populating;
    private long lastSearch;
    private SeekBar seek;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private boolean downloadedOnly, seeking, loading;
    private int displayLimit = 40;
    private int libraryMode;
    private ImageButton videoButton;
    private float appliedTextScale;
    private final List<Runnable> downloadLabels = new ArrayList<>();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updatePlayer(); for (Runnable r : downloadLabels) r.run(); handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); UiPreferences.apply(this); repository = new Repository(this); artworkStore = new ArtworkStore(this); appliedTextScale = repository.prefs.getFloat("textScale", 1f);
        List<Podcast> saved = repository.podcasts();
        String selectedFeed = getIntent().getStringExtra("feed"); if (selectedFeed == null) selectedFeed = repository.prefs.getString("selectedFeed", "");
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
        titleRow.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageButton programSettings = icon("more", "Opciones de este programa"); programSettings.setOnClickListener(v -> { if (!repository.feed.isEmpty()) ProgramOptions.show(this, repository.feed); else Toast.makeText(this, "Añade primero un podcast", Toast.LENGTH_SHORT).show(); }); titleRow.addView(programSettings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageButton settings = icon("settings", "Ajustes"); settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class))); titleRow.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48))); head.addView(titleRow);
        programs = new Spinner(this); head.addView(programs);
        programs.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View v, int index, long id) {
                if (!populating && index < podcasts.size() && !podcasts.get(index).feed.equals(repository.feed)) switchPodcast(podcasts.get(index));
            }
        });
        LinearLayout show = row(); show.setPadding(0, dp(12), 0, dp(12));
        cover = label("+", 32, Color.WHITE, true); cover.setGravity(Gravity.CENTER); cover.setBackground(bg(0xff183e37, 18));
        FrameLayout artwork = new FrameLayout(this); artwork.setBackground(bg(0xff183e37, 18)); artwork.setClipToOutline(true); artwork.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        coverImage = new ImageView(this); coverImage.setScaleType(ImageView.ScaleType.CENTER_CROP); coverImage.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); artwork.addView(coverImage, new FrameLayout.LayoutParams(-1, -1));
        show.addView(artwork, new LinearLayout.LayoutParams(dp(86), dp(86)));
        LinearLayout description = column(0); description.setPadding(dp(16), 0, 0, 0);
        programTitle = label("Tu biblioteca", 23, INK, true); programTitle.setMaxLines(2); description.addView(programTitle);
        programDescription = label("Tu biblioteca de podcasts", 14, MUTED, false); description.addView(programDescription);
        show.addView(description, new LinearLayout.LayoutParams(0, -2, 1)); head.addView(show);
        remove = icon("close", "Quitar programa de la biblioteca"); remove.setOnClickListener(v -> removePodcast());
        show.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout tabs = row();
        allTab = icon("list", "Todos los episodios"); offlineTab = icon("download", "Tus descargas");
        ImageButton all = allTab, offline = offlineTab;
        all.setOnClickListener(v -> { downloadedOnly = false; libraryMode = 0; displayLimit = 40; render(); }); offline.setOnClickListener(v -> { downloadedOnly = true; libraryMode = 0; displayLimit = 40; render(); });
        tabs.addView(all, new LinearLayout.LayoutParams(0, dp(48), 1)); tabs.addView(offline, new LinearLayout.LayoutParams(0, dp(48), 1));
        ImageButton collections = icon("library", "Secciones de tu biblioteca"); collections.setOnClickListener(v -> libraryMenu()); tabs.addView(collections, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageButton searchLibrary = icon("search", "Buscar en tu biblioteca"); searchLibrary.setOnClickListener(v -> searchLibrary()); tabs.addView(searchLibrary, new LinearLayout.LayoutParams(dp(48), dp(48)));
        refresh = icon("refresh", "Actualizar episodios"); refresh.setContentDescription("Actualizar episodios"); refresh.setOnClickListener(v -> load()); tabs.addView(refresh, new LinearLayout.LayoutParams(dp(55), dp(48)));
        head.addView(tabs); status = label("", 12, MUTED, false); status.setPadding(0, dp(8), 0, 0); head.addView(status); root.addView(head);
        ScrollView scroll = new ScrollView(this); list = column(16); scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout player = column(14); player.setBackground(bg(0xff111318, 24));
        LinearLayout playerHeader = row();
        FrameLayout playerCover = new FrameLayout(this); playerCover.setBackground(bg(0xff263344, 14)); playerCover.setClipToOutline(true);
        ImageView placeholder = new ImageView(this); placeholder.setImageDrawable(new ControlIcon("wave", PURPLE, dp(32))); placeholder.setPadding(dp(12),dp(12),dp(12),dp(12)); playerCover.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        playerArtwork = new ImageView(this); playerArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP); playerArtwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); playerCover.addView(playerArtwork,new FrameLayout.LayoutParams(-1,-1));
        playerHeader.addView(playerCover,new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout playerText = column(0); playerText.setPadding(dp(12),0,0,0);
        current = label("Elige un episodio", 16, INK, true); current.setMaxLines(2); playerText.addView(current);
        playerProgram = label("Tu próxima escucha",12,MUTED,false); playerProgram.setMaxLines(1); playerText.addView(playerProgram);
        playerHeader.addView(playerText,new LinearLayout.LayoutParams(0,-2,1)); player.addView(playerHeader);
        videoButton = icon("video", "Abrir vídeo actual"); videoButton.setVisibility(View.GONE); videoButton.setOnClickListener(v -> startActivity(new Intent(this, VideoActivity.class))); player.addView(videoButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        seek = new SeekBar(this); seek.setMax(1000); player.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { }
            public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            public void onStopTrackingTouch(SeekBar s) { if (controller != null && controller.getDuration() > 0) controller.seekTo(controller.getDuration() * s.getProgress() / 1000); seeking = false; }
        });
        time = label("0 min 00 s / —", 12, MUTED, false); time.setGravity(Gravity.CENTER); player.addView(time);
        LinearLayout tools = row(); ImageButton speedButton = icon("settings", "Cambiar velocidad de reproducción"), chaptersButton = icon("list", "Capítulos del episodio");
        speedButton.setOnClickListener(v -> PlaybackTools.speed(this)); chaptersButton.setOnClickListener(v -> { if (controller != null) PlaybackTools.chapters(this, controller, worker); });
        tools.addView(speedButton, new LinearLayout.LayoutParams(dp(48), dp(48))); tools.addView(chaptersButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Space toolSpace = new Space(this); tools.addView(toolSpace,new LinearLayout.LayoutParams(0,1,1));
        sleep = icon("timer", "Temporizador para dormir"); sleep.setOnClickListener(v -> sleepMenu()); tools.addView(sleep,new LinearLayout.LayoutParams(dp(48),dp(48))); player.addView(tools);
        LinearLayout controls = row(); ImageButton back = icon("rewind", "Retroceder 15 segundos"), forward = icon("forward", "Avanzar 30 segundos"); toggle = icon("play", "Reproducir");
        back.setOnClickListener(v -> { if (controller != null) controller.seekTo(Math.max(0, controller.getCurrentPosition() - 15000)); });
        forward.setOnClickListener(v -> { if (controller != null) controller.seekTo(Math.min(Math.max(0, controller.getDuration() > 0 ? controller.getDuration() : Long.MAX_VALUE), controller.getCurrentPosition() + 30000)); });
        toggle.setOnClickListener(v -> { if (controller == null) return; if (controller.isPlaying()) controller.pause(); else { if (controller.getPlaybackState() == Player.STATE_ENDED) controller.seekTo(0); controller.prepare(); controller.play(); } });
        controls.setGravity(Gravity.CENTER); controls.setPadding(0,dp(6),0,0);
        stylePlaybackControl(back,false); stylePlaybackControl(toggle,true); stylePlaybackControl(forward,false);
        LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(dp(56),dp(56)); skipParams.setMargins(dp(14),0,dp(14),0);
        controls.addView(back,skipParams); controls.addView(toggle,new LinearLayout.LayoutParams(dp(72),dp(72))); controls.addView(forward,new LinearLayout.LayoutParams(skipParams)); player.addView(controls);
        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(-1,-2); playerParams.setMargins(dp(12),dp(6),dp(12),dp(10)); root.addView(player,playerParams);
        populatePrograms(); render(); BackgroundSync.schedule(this);
        if (!podcasts.isEmpty()) connectPlayer();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        if (repository.prefs.getBoolean("autoRefresh", true)) load();
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                java.util.regex.Matcher match = java.util.regex.Pattern.compile("https://[^\\s<>]+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
                if (match.find()) addRss(match.group());
            }
        }
    }
    @Override protected void onResume() { super.onResume(); if (repository != null && !podcastSignature(repository.podcasts()).equals(podcastSignature(podcasts))) { List<Podcast> savedPrograms = repository.podcasts(); boolean found = false; for (Podcast p : savedPrograms) if (p.feed.equals(repository.feed)) found = true; if (!found) repository = new Repository(this, savedPrograms.isEmpty() ? "" : savedPrograms.get(0).feed); episodes = repository.cached(); populatePrograms(); if (!savedPrograms.isEmpty()) connectPlayer(); } if (getIntent().getBooleanExtra("restoreLibrary", false)) { getIntent().removeExtra("restoreLibrary"); recreate(); return; } if (appliedTextScale != getSharedPreferences("library", MODE_PRIVATE).getFloat("textScale", 1f)) { recreate(); return; } if (list != null) { updateProgramHeader(); render(); } }
    @Override protected void onStart() { super.onStart(); handler.post(tick); }
    @Override protected void onStop() { handler.removeCallbacks(tick); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); worker.shutdownNow(); images.shutdownNow(); if (controllerFuture != null) MediaController.releaseFuture(controllerFuture); super.onDestroy(); }
    private void connectPlayer() {
        if (controllerFuture != null) return;
        controllerFuture = new MediaController.Builder(this, new SessionToken(this, new ComponentName(this, PlaybackService.class))).buildAsync();
        controllerFuture.addListener(() -> {
            if (isDestroyed()) return;
            try {
                controller = controllerFuture.get();
                controller.addListener(new Player.Listener() {
                    @Override public void onMediaItemTransition(MediaItem item, int reason) { if (list != null) render(); updatePlayer(); }
                    @Override public void onPlaybackStateChanged(int state) { if (state == Player.STATE_ENDED && list != null) render(); }
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
    private String podcastSignature(List<Podcast> programs) { StringBuilder signature = new StringBuilder(); for (Podcast p : programs) signature.append(p.feed).append(p.title).append(p.artwork).append('\n'); return signature.toString(); }
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
        for (Episode e : episodes) if (repository.position(e.id) > 0 && !repository.prefs.contains("entry:" + Repository.key(e.id))) repository.remember(new LibraryEntry(e, currentPodcast()));
    }
    private void updateProgramHeader() {
        Podcast p = currentPodcast(); programTitle.setText(p.title); programDescription.setText(p.feed.isEmpty() ? "Añade tu primer podcast con +" : "Podcast de tu biblioteca");
        remove.setVisibility(p.feed.isEmpty() ? View.GONE : View.VISIBLE);
        bindArtwork(coverImage, p.artwork);
        cover.setText(p.feed.isEmpty() ? "+" : p.title.length() < 2 ? p.title : p.title.substring(0, 2).toUpperCase(Locale.ROOT));
    }
    private void switchPodcast(Podcast podcast) {
        requestGeneration++; loading = false;
        repository = new Repository(this, podcast.feed); repository.prefs.edit().putString("selectedFeed", podcast.feed).apply();
        episodes = repository.cached(); displayLimit = 40; updateProgramHeader(); render();
        if (!podcast.feed.isEmpty()) connectPlayer();
        if (repository.prefs.getBoolean("autoRefresh", true)) load();
    }
    private void addMenu() {
        new AlertDialog.Builder(this).setTitle("Añadir a tu biblioteca")
            .setItems(new String[]{"Buscar en Apple Podcasts", "Añadir por RSS o enlace de Apple", "Importar biblioteca OPML", "Explorar por idioma y categoría"}, (dialog, which) -> {
                if (which == 0) searchPodcasts();
                else if (which == 1) addRss();
                else if (which == 3) startActivity(new Intent(this, DiscoverActivity.class));
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
                            LinearLayout card = column(8); LinearLayout heading = row(); FrameLayout picture = podcastPicture(p); heading.addView(picture, new LinearLayout.LayoutParams(dp(48), dp(48))); TextView title = label(p.title, 16, INK, true); title.setPadding(dp(12), 0, 0, 0); heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); card.addView(heading);
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
        addRss("");
    }
    private void addRss(String initialLink) {
        EditText input = new EditText(this); input.setHint("Enlace RSS o de Apple Podcasts"); input.setSingleLine(true);
        input.setText(initialLink);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout box = column(20); box.addView(label("Pega el RSS público o el enlace de un programa de Apple Podcasts.", 14, MUTED, false)); box.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Añadir podcast").setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Añadir", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String link = input.getText().toString().trim();
            final boolean apple = link.toLowerCase(Locale.ROOT).startsWith("https://podcasts.apple.com/");
            final String feed;
            try { if (apple) { Catalog.appleId(link); feed = ""; } else feed = Repository.normalizeFeed(link); }
            catch (Exception e) { input.setError(e.getMessage()); return; }
            for (Podcast p : repository.podcasts()) if (p.feed.equals(feed)) { Toast.makeText(this, "Este podcast ya está en tu biblioteca", Toast.LENGTH_SHORT).show(); dialog.dismiss(); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); input.setEnabled(false); status.setText("Comprobando el podcast…");
            worker.execute(() -> {
                try {
                    Podcast linked = apple ? Catalog.fromAppleLink(link) : new Podcast("Podcast", feed);
                    Repository added = new Repository(this, linked.feed); Repository.FeedResult result = added.refreshFeed();
                    runOnUiThread(() -> {
                        if (isDestroyed() || !dialog.isShowing()) return;
                        try { Podcast p = new Podcast(result.title, linked.feed, result.artwork.isEmpty() ? linked.artwork : result.artwork); repository.addPodcast(p); dialog.dismiss(); populatePrograms(); switchPodcast(p); populatePrograms(); }
                        catch (Exception e) { input.setError("No se pudo guardar el podcast"); input.setEnabled(true); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> { if (isDestroyed() || !dialog.isShowing()) return; input.setEnabled(true); input.setError("No se pudo añadir. Comprueba el enlace, la conexión y que el podcast tenga un RSS público HTTPS."); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); status.setText("No se añadió el podcast"); });
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
            try { List<Episode> result = target.refresh(); runOnUiThread(() -> { if (isDestroyed() || generation != requestGeneration) return; episodes = result; loading = false; refresh.setEnabled(true); populatePrograms(); render(); }); }
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
        List<LibraryEntry> visible = new ArrayList<>();
        if (libraryMode == 0) { List<Episode> ordered = new ArrayList<>(episodes); if (repository.prefs.getBoolean("oldestFirst", false)) Collections.reverse(ordered); for (Episode e : ordered) visible.add(new LibraryEntry(e, currentPodcast())); }
        else if (libraryMode == 3) visible = repository.queue();
        else for (LibraryEntry entry : repository.remembered()) { if (libraryMode == 1 && repository.position(entry.episode.id) > 0 && !repository.listened(entry.episode.id) || libraryMode == 2 && repository.favorite(entry.episode.id)) visible.add(entry); }
        if (libraryMode != 0) list.addView(label(libraryMode == 1 ? "Continuar escuchando" : libraryMode == 2 ? "Favoritos" : "Escuchar después", 24, INK, true));
        for (LibraryEntry entry : visible) {
            Episode e = entry.episode;
            if (libraryMode == 0 && repository.prefs.getBoolean("hideListened", false) && repository.listened(e.id)) continue;
            if (downloadedOnly && repository.downloadStatus(e) < 0) continue;
            count++;
            if (count > displayLimit) continue;
            LinearLayout card = column(14); GradientDrawable surface = bg(Color.BLACK, 16); surface.setStroke(dp(1), 0xff25272b); card.setBackground(surface);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2); cardParams.bottomMargin = dp(10);
            card.addView(label((libraryMode == 0 ? "" : entry.podcast.title + " · ") + prettyDate(e.date), 11, MUTED, false));
            TextView title = label(e.title, 16, INK, true); title.setPadding(0, dp(4), 0, dp(6)); LinearLayout episodeHeading = row(); FrameLayout thumbnail = podcastPicture(new Podcast(entry.podcast.title, entry.podcast.feed, entry.artwork())); thumbnail.setContentDescription("Portada de " + entry.podcast.title); episodeHeading.addView(thumbnail, new LinearLayout.LayoutParams(dp(56), dp(56))); title.setPadding(dp(12), dp(4), 0, dp(6)); episodeHeading.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); card.addView(episodeHeading);
            long publishedDuration = repository.duration(e); card.addView(label((e.videoUrl.isEmpty() ? "Audio" : "Vídeo disponible") + " · " + (publishedDuration > 0 ? TimeFormat.display(publishedDuration, false) : "Duración aún no disponible"), 12, MUTED, false));
            LinearLayout actions = row(); ImageButton play = icon("play", "Escuchar episodio"), download = icon("download", "Descargar episodio");
            TextView downloadState = label("", 11, MUTED, false);
            play.setOnClickListener(v -> selectEntry(entry, true, e.url.equals(e.videoUrl)));
            ImageButton favorite = icon(repository.favorite(e.id) ? "favorite_on" : "favorite", repository.favorite(e.id) ? "Quitar de favoritos" : "Guardar en favoritos"); favorite.setOnClickListener(v -> { repository.setFavorite(entry, !repository.favorite(e.id)); render(); });
            ImageButton moreActions = icon("more", "Opciones del episodio"); moreActions.setOnClickListener(v -> episodeMenu(entry));
            if (!e.videoUrl.isEmpty()) { ImageButton video = icon("video", "Ver vídeo del episodio"); video.setOnClickListener(v -> selectEntry(entry, true, true)); actions.addView(video, new LinearLayout.LayoutParams(dp(48), dp(48))); }
            download.setOnClickListener(v -> {
                int state = repository.downloadStatus(e);
                if (state == DownloadManager.STATUS_SUCCESSFUL || state == DownloadManager.STATUS_RUNNING || state == DownloadManager.STATUS_PENDING || state == DownloadManager.STATUS_PAUSED) {
                    new AlertDialog.Builder(this).setTitle(state == DownloadManager.STATUS_SUCCESSFUL ? "¿Eliminar la descarga?" : "¿Cancelar la descarga?")
                        .setMessage(e.title).setNegativeButton("Conservar", null).setPositiveButton("Eliminar", (d, w) -> { repository.removeDownload(e); render(); }).show();
                } else {
                    try { repository.remember(entry); repository.download(e); Toast.makeText(this, "Descarga iniciada", Toast.LENGTH_SHORT).show(); }
                    catch (Exception ex) { Toast.makeText(this, "No se pudo iniciar la descarga", Toast.LENGTH_LONG).show(); }
                }
            });
            Runnable dl = () -> { int s = repository.downloadStatus(e); boolean busy = s == DownloadManager.STATUS_RUNNING || s == DownloadManager.STATUS_PENDING || s == DownloadManager.STATUS_PAUSED;
                setIcon(download, s == DownloadManager.STATUS_SUCCESSFUL ? "check" : s == DownloadManager.STATUS_FAILED ? "refresh" : busy ? "close" : "download", s == DownloadManager.STATUS_SUCCESSFUL ? "Eliminar descarga" : busy ? "Cancelar descarga" : "Descargar episodio");
                downloadState.setText(s == DownloadManager.STATUS_SUCCESSFUL ? "Disponible sin conexión" : s == DownloadManager.STATUS_FAILED ? "Descarga fallida · toca para reintentar" : busy ? "Descargando…" : ""); };
            dl.run(); downloadLabels.add(dl);
                        actions.addView(play, 0, new LinearLayout.LayoutParams(dp(48), dp(48))); actions.addView(download, new LinearLayout.LayoutParams(dp(48), dp(48))); actions.addView(favorite, new LinearLayout.LayoutParams(dp(48), dp(48))); actions.addView(moreActions, new LinearLayout.LayoutParams(dp(48), dp(48))); card.addView(actions); card.addView(downloadState);
            long position = repository.position(e.id), duration = repository.duration(e);
            if (repository.listened(e.id)) card.addView(label("Escuchado", 12, PURPLE, false));
            else if (position > 0) { card.addView(label("En curso · " + clock(position) + (duration > 0 ? " / " + clock(duration) : ""), 12, PURPLE, false)); if (duration > 0) { ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(1000); progress.setProgress((int) Math.min(1000, position * 1000 / duration)); card.addView(progress); } }
            list.addView(card, cardParams);
        }
        status.setText(libraryMode != 0 ? count + " episodios en esta sección" : downloadedOnly ? count + " episodios en descargas" : count + " episodios · " + currentPodcast().title);
        if (count == 0) list.addView(label(libraryMode != 0 ? "Los episodios que guardes o empieces a escuchar aparecerán aquí." : downloadedOnly ? "Tus descargas aparecerán aquí.\nDescarga un episodio desde Episodios." : "No hay episodios que mostrar. Actualiza el programa o revisa el filtro de escuchados.", 16, MUTED, false));
        if (count > displayLimit) { ImageButton more = icon("expand", "Mostrar más episodios"); more.setOnClickListener(v -> { displayLimit += 40; render(); }); list.addView(more); }
    }
    private void select(Episode e, boolean start) { selectEntry(new LibraryEntry(e, currentPodcast()), start, e.url.equals(e.videoUrl)); }
    private void selectEntry(LibraryEntry entry, boolean start, boolean video) {
        if (controller == null) { connectPlayer(); Toast.makeText(this, "Conectando al reproductor…", Toast.LENGTH_SHORT).show(); return; }
        Episode e = entry.episode; MediaItem existing = controller.getCurrentMediaItem();
        boolean same = existing != null && existing.mediaId.equals(e.id);
        long position = same ? controller.getCurrentPosition() : repository.prefs.getBoolean("resumePlayback", true) ? repository.position(e.id) : 0;
        if (same && controller.getPlaybackState() == Player.STATE_ENDED) position = 0;
        repository.remember(entry); repository.setListened(e.id, false);
        try { repository.dequeue(e.id); } catch (Exception ignored) { }
        List<MediaItem> items = new ArrayList<>(); items.add(entry.mediaItem(this, video)); for (LibraryEntry queued : repository.queue()) if (!queued.episode.id.equals(e.id)) items.add(queued.mediaItem(this, false));
        controller.setMediaItems(items, 0, position); controller.prepare(); if (start) controller.play(); updatePlayer(); render();
        if (video && start) startActivity(new Intent(this, VideoActivity.class));
    }
    private void syncQueue() {
        if (controller == null || controller.getCurrentMediaItem() == null) return;
        MediaItem playing = controller.getCurrentMediaItem(); long position = controller.getCurrentPosition(); boolean play = controller.getPlayWhenReady();
        List<MediaItem> items = new ArrayList<>(); items.add(playing); for (LibraryEntry queued : repository.queue()) if (!queued.episode.id.equals(playing.mediaId)) items.add(queued.mediaItem(this, false));
        controller.setMediaItems(items, 0, position); controller.prepare(); if (play) controller.play();
    }
    private void libraryMenu() {
        new AlertDialog.Builder(this).setTitle("Tu biblioteca").setItems(new String[]{"Episodios del programa", "Continuar escuchando", "Favoritos", "Escuchar después"}, (dialog, index) -> { libraryMode = index; downloadedOnly = false; displayLimit = 40; render(); }).setNegativeButton("Cerrar", null).show();
    }
    private void episodeMenu(LibraryEntry entry) {
        Episode e = entry.episode; List<String> options = new ArrayList<>(); options.add(repository.listened(e.id) ? "Marcar como no escuchado" : "Marcar como escuchado"); options.add("Añadir a Escuchar después");
        if (libraryMode == 3) { options.add("Mover antes"); options.add("Mover después"); options.add("Quitar de la cola"); }
        new AlertDialog.Builder(this).setTitle(e.title).setItems(options.toArray(new String[0]), (dialog, index) -> {
            try {
                if (index == 0) { if (controller != null && controller.getCurrentMediaItem() != null && controller.getCurrentMediaItem().mediaId.equals(e.id)) { controller.pause(); controller.seekTo(0); } repository.remember(entry); repository.setListened(e.id, !repository.listened(e.id)); }
                else if (index == 1) { if (controller != null && controller.getCurrentMediaItem() != null && controller.getCurrentMediaItem().mediaId.equals(e.id)) { Toast.makeText(this, "Este episodio ya está en reproducción", Toast.LENGTH_SHORT).show(); return; } boolean added = repository.enqueue(entry); Toast.makeText(this, added ? "Añadido a Escuchar después" : "Ya está en la cola", Toast.LENGTH_SHORT).show(); syncQueue(); }
                else if (index == 2 || index == 3) { repository.moveQueue(e.id, index == 2 ? -1 : 1); syncQueue(); }
                else { repository.dequeue(e.id); syncQueue(); }
                render();
            } catch (Exception ex) { Toast.makeText(this, "No se pudo actualizar la cola", Toast.LENGTH_LONG).show(); }
        }).setNegativeButton("Cerrar", null).show();
    }
    private void searchLibrary() {
        EditText input = new EditText(this); input.setHint("Título de un episodio"); input.setSingleLine(true);
        LinearLayout box = column(16); box.addView(input); TextView message = label("Busca entre los episodios guardados de todos tus programas.", 13, MUTED, false); box.addView(message);
        Button search = new Button(this); search.setText("Buscar"); search.setAllCaps(false); box.addView(search); ScrollView scroll = new ScrollView(this); LinearLayout results = column(0); scroll.addView(results); box.addView(scroll, new LinearLayout.LayoutParams(-1, dp(260)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Buscar en tu biblioteca").setView(box).setNegativeButton("Cerrar", null).create();
        search.setOnClickListener(v -> { String query = input.getText().toString().trim(); if (query.length() < 2) { input.setError("Escribe al menos dos letras"); return; } search.setEnabled(false); message.setText("Buscando…");
            worker.execute(() -> { List<LibraryEntry> found = repository.searchLibrary(query); runOnUiThread(() -> { if (isDestroyed() || !dialog.isShowing()) return; search.setEnabled(true); results.removeAllViews(); message.setText(found.isEmpty() ? "Sin resultados. Actualiza tus programas para guardar sus episodios." : found.size() + " resultados · toca uno para escuchar");
                for (LibraryEntry entry : found.subList(0, Math.min(found.size(), 100))) { TextView title = label(entry.episode.title + "\n" + entry.podcast.title, 15, INK, false); title.setPadding(dp(8), dp(12), dp(8), dp(12)); title.setOnClickListener(w -> { dialog.dismiss(); selectEntry(entry, true, entry.episode.url.equals(entry.episode.videoUrl)); }); results.addView(title); }
            }); });
        }); dialog.show();
    }
    private void updatePlayer() {
        boolean has = controller != null && controller.getCurrentMediaItem() != null;
        toggle.setEnabled(has); toggle.setAlpha(has ? 1f : .45f); seek.setEnabled(has);
        long remaining = repository.prefs.getLong("sleepDeadline", 0) - SystemClock.elapsedRealtime(); boolean endSleep = !repository.prefs.getString("sleepEpisode", "").isEmpty(); sleep.setAlpha(remaining > 0 || endSleep ? 1f : .5f); sleep.setContentDescription(endSleep ? "Temporizador: hasta terminar el episodio" : remaining > 0 ? "Temporizador: " + TimeFormat.display(remaining, false) + " restantes" : "Temporizador para dormir");
        videoButton.setVisibility(has && controller.getMediaMetadata().extras != null && controller.getMediaMetadata().extras.getBoolean("video", false) ? View.VISIBLE : View.GONE);
        if (!has) { current.setText("Elige un episodio"); playerProgram.setText("Tu próxima escucha"); bindArtwork(playerArtwork, ""); setIcon(toggle, "play", "Reproducir"); seek.setProgress(0); time.setText("0 min 00 s / —"); return; }
        current.setText(controller.getMediaMetadata().title);
        playerProgram.setText(controller.getMediaMetadata().artist);
        android.net.Uri playingArtwork = controller.getMediaMetadata().artworkUri; bindArtwork(playerArtwork,playingArtwork == null ? "" : playingArtwork.toString()); long duration = Math.max(0, controller.getDuration()), position = Math.max(0, controller.getCurrentPosition());
        if (!seeking) seek.setProgress(duration > 0 ? (int) (position * 1000 / duration) : 0);
        time.setText(clock(position) + " / " + (duration > 0 ? clock(duration) + " · Quedan " + clock(Math.max(0, duration - position)) : "Duración aún no disponible"));
        seek.setContentDescription("Posición: " + clock(position) + (duration > 0 ? " de " + clock(duration) : ""));
        setIcon(toggle, controller.isPlaying() || controller.getPlayWhenReady() ? "pause" : "play", controller.isPlaying() || controller.getPlayWhenReady() ? "Pausar" : "Reproducir");
    }
    private void sleepMenu() {
        long remaining = repository.prefs.getLong("sleepDeadline", 0) - SystemClock.elapsedRealtime();
        new AlertDialog.Builder(this).setTitle(remaining > 0 ? "Temporizador · " + (remaining + 59999) / 60000 + " min restantes" : "Temporizador para dormir").setItems(new String[]{"Desactivado", "15 minutos", "30 minutos", "45 minutos", "1 hora", "Hasta terminar este episodio"}, (dialog, index) -> {
            repository.prefs.edit().remove("sleepDeadline").remove("sleepEpisode").apply();
            if (index == 5) { if (controller == null || controller.getCurrentMediaItem() == null) { Toast.makeText(this, "Elige primero un episodio", Toast.LENGTH_SHORT).show(); return; } repository.prefs.edit().putString("sleepEpisode", controller.getCurrentMediaItem().mediaId).apply(); }
            else if (index > 0) repository.prefs.edit().putLong("sleepDeadline", SystemClock.elapsedRealtime() + index * 15 * 60000L).apply();
            Toast.makeText(this, index == 0 ? "Temporizador desactivado" : index == 5 ? "Se pausará al terminar el episodio" : "Se pausará en " + TimeFormat.display(index * 15 * 60000L, false), Toast.LENGTH_SHORT).show(); updatePlayer();
        }).setNegativeButton("Cerrar", null).show();
    }
    private FrameLayout podcastPicture(Podcast podcast) {
        FrameLayout frame = new FrameLayout(this); frame.setBackground(bg(0xff183e37, 10)); frame.setClipToOutline(true);
        TextView fallback = label(podcast.title.isEmpty() ? "♫" : podcast.title.substring(0, 1).toUpperCase(Locale.ROOT), 20, INK, true); fallback.setGravity(Gravity.CENTER); frame.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); frame.addView(image, new FrameLayout.LayoutParams(-1, -1)); bindArtwork(image, podcast.artwork); return frame;
    }
    private void bindArtwork(ImageView image, String url) {
        if (url.equals(image.getTag())) return;
        image.setTag(url); image.setImageDrawable(null); if (url.isEmpty()) return;
        images.execute(() -> {
            try { android.graphics.Bitmap bitmap = artworkStore.load(url); runOnUiThread(() -> { if (!isDestroyed() && url.equals(image.getTag())) { image.setImageBitmap(bitmap); if (bitmap == null) image.setTag(null); } }); }
            catch (Exception ignored) { runOnUiThread(() -> { if (!isDestroyed() && url.equals(image.getTag())) image.setTag(null); }); }
        });
    }
    private String clock(long ms) { return TimeFormat.display(ms, true); }
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
        if (!type.equals(button.getTag())) { button.setImageDrawable(new ControlIcon(type, button == toggle ? 0xff102037 : PURPLE, dp(button == toggle ? 32 : 24))); button.setTag(type); }
        button.setContentDescription(description); button.setTooltipText(description);
    }
    private void stylePlaybackControl(ImageButton button, boolean primary) {
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(primary ? 0x44304760 : 0x44a8c7fa),bg(primary ? PURPLE : 0xff242a34,primary ? 24 : 28),null));
        button.setPadding(dp(primary ? 20 : 12),dp(primary ? 20 : 12),dp(primary ? 20 : 12),dp(primary ? 20 : 12));
        button.setOnTouchListener((v,event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) v.animate().scaleX(.94f).scaleY(.94f).setDuration(90).start();
            else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            return false;
        });
        if (primary) { button.setTag(null); setIcon(button,"play","Reproducir"); }
    }
    private GradientDrawable bg(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
}
