package es.jesus.ampodcasts;

import android.app.*;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class DiscoverActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); UiPreferences.apply(this); getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK); int pad = (int) (16 * getResources().getDisplayMetrics().density); root.setPadding(pad, pad, pad, pad);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(), pad + insets.getSystemWindowInsetRight(), pad + insets.getSystemWindowInsetBottom()); return insets; }); setContentView(root);
        ImageButton back = new ImageButton(this); back.setImageDrawable(new ControlIcon("back", 0xffa8c7fa, pad * 3 / 2)); back.setContentDescription("Volver a la biblioteca"); back.setOnClickListener(v -> finish()); UiStyle.icon(back,"back","Volver a la biblioteca"); root.addView(back, new LinearLayout.LayoutParams(pad * 3, pad * 3));
        TextView title = new TextView(this); title.setText("Descubrir podcasts"); title.setTextSize(26); title.setTextColor(0xffa8c7fa); root.addView(title);
        Button personal=new Button(this);personal.setText("Para ti · Recomendaciones");personal.setAllCaps(false);personal.setMinHeight(pad*3);personal.setOnClickListener(v->startActivity(new android.content.Intent(this,RecommendationsActivity.class)));UiStyle.button(personal); root.addView(personal,UiStyle.spaced(this));
        Spinner language = spinner(root, new String[]{"Español", "Inglés", "Francés", "Alemán", "Italiano", "Portugués"}, "Idioma del podcast");
        Spinner category = spinner(root, new String[]{"Todas las categorías", "Noticias", "Tecnología", "Historia", "Educación", "Comedia", "Arte", "Sociedad y cultura"}, "Categoría");
        String[] languages = {"es", "en", "fr", "de", "it", "pt"}, categories = {"", "1489", "1318", "1487", "1304", "1303", "1301", "1324"};
        TextView note = new TextView(this); note.setText("Selección de las listas públicas de Apple Podcasts por categoría. El idioma se comprueba en el RSS. Solo se muestran programas con idioma declarado y RSS HTTPS; no se añaden automáticamente."); note.setTextColor(0xffa6abb3); root.addView(note);
        ImageButton find = new ImageButton(this); find.setImageDrawable(new ControlIcon("search", 0xffa8c7fa, pad * 3 / 2)); find.setContentDescription("Explorar catálogo"); UiStyle.icon(find,"search","Explorar catálogo"); root.addView(find, new LinearLayout.LayoutParams(pad * 3, pad * 3));
        TextView status = new TextView(this); status.setTextColor(0xffa6abb3); root.addView(status); ScrollView scroll = new ScrollView(this); LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); scroll.addView(results); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        find.setOnClickListener(v -> { String lang = languages[language.getSelectedItemPosition()], genre = categories[category.getSelectedItemPosition()]; find.setEnabled(false); results.removeAllViews(); status.setText("Buscando y comprobando el idioma del RSS…"); worker.execute(() -> {
            try { List<Podcast> found = Catalog.discover(lang, genre); runOnUiThread(() -> { if (isDestroyed()) return; find.setEnabled(true); status.setText(found.isEmpty() ? "Sin resultados con idioma verificable. Prueba otra categoría o busca por nombre desde +." : "Toca un programa para añadirlo"); for (Podcast p : found) { Button add = new Button(this); add.setText(p.title); add.setAllCaps(false); add.setMinHeight(pad * 3); add.setContentDescription("Añadir " + p.title); add.setOnClickListener(w -> { try { boolean added = new Repository(this).addPodcast(p); Toast.makeText(this, added ? "Añadido a tu biblioteca" : "Ya está en tu biblioteca", Toast.LENGTH_SHORT).show(); } catch (Exception e) { Toast.makeText(this, "No se pudo añadir el programa", Toast.LENGTH_SHORT).show(); } }); UiStyle.button(add); results.addView(add,UiStyle.spaced(this)); } }); }
            catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) { find.setEnabled(true); status.setText("No se pudo consultar el catálogo. Comprueba la conexión."); } }); }
        }); });
    }
    private Spinner spinner(LinearLayout root, String[] labels, String description) { Spinner spinner = new Spinner(this); ArrayAdapter<String> adapter = UiStyle.choices(this,labels); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); UiStyle.spinner(spinner); spinner.setContentDescription(description); LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,(int)(48*getResources().getDisplayMetrics().density));params.topMargin=(int)(8*getResources().getDisplayMetrics().density);params.bottomMargin=params.topMargin;root.addView(spinner,params); return spinner; }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
