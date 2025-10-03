package com.fernan2529.Categorias;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import android.util.SparseArray;

import com.fernan2529.R;
import com.fernan2529.MainActivity;   // cámbialo si tu inicio real es otro
import com.fernan2529.Reproductor;
import com.fernan2529.WebViewActivities.WebViewActivityGeneral;
import com.fernan2529.WatchViewActivities.WatchActivityViewGeneral;
import com.fernan2529.WebViewActivities.WebViewActivityAds;

import com.fernan2529.data.CategoriesRepository;
import com.fernan2529.nav.CategoryNavigator;

public class Noticias extends AppCompatActivity {

    private Spinner spinner;
    private String[] categories = new String[0];
    private int indexThis = -1;           // índice real de "Noticias"
    private boolean userTouched = false;  // marca interacción real (no programática)
    private long lastClickAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_noticias);

        // Botones básicos
        setupButton(R.id.noticias, MainActivity.class);
        setupButton(R.id.btn_ver, MainActivity.class);
        setupButton(R.id.button_reproductor, Reproductor.class);

        // UI
        setupSpinner();     // lógica con spinner_activities
        setupWebButtons();
        setupRtsShortcut(); // caso especial: abrir RTS en WebView con anuncios desde el tile bbcnews
        setupCnnTile();     // ✅ MOVIDO: registrar CNN después de setContentView()
    }

    // ----------------- Utilidades -----------------
    private void openWebViewAds(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, WebViewActivityAds.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }

    private boolean canClick() {
        long now = System.currentTimeMillis();
        if (now - lastClickAt < 600) return false; // 600ms debounce
        lastClickAt = now;
        return true;
    }

    private void setupButton(int viewId, Class<?> targetActivity) {
        View v = findViewById(viewId);
        if (v != null && targetActivity != null) {
            v.setOnClickListener(_v -> startActivity(new Intent(this, targetActivity)));
        }
    }

    private void openWebView(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, WebViewActivityGeneral.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }

    private static int findIndex(String[] arr, String target) {
        if (arr == null || target == null) return -1;
        for (int i = 0; i < arr.length; i++) {
            if (target.equalsIgnoreCase(arr[i])) return i;
        }
        return -1;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ----------------- Spinner con lógica unificada -----------------
    private void setupSpinner() {
        spinner = findViewById(R.id.spinner_activities); // usa este ID en tu XML
        if (spinner == null) return;

        // Cargar categorías desde el repositorio
        CategoriesRepository repo = new CategoriesRepository();
        String[] loaded = repo.getCategories();
        if (loaded != null) categories = loaded;

        // Detectar el índice real de esta pantalla por nombre
        indexThis = findIndex(categories, "Noticias");

        // Fallback seguro si no se encuentra
        if (indexThis < 0) {
            if (categories.length == 0) {
                categories = new String[]{"Seleccione…", "Noticias"};
            }
            indexThis = Math.min(13, categories.length - 1);
            if (indexThis < 0) indexThis = 0;
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Mantener “Noticias” seleccionado sin disparar navegación
        spinner.setSelection(indexThis, false);

        // Marcar interacción real para evitar triggers por setSelection()
        spinner.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_UP) {
                userTouched = true;
            }
            return false;
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!userTouched) return; // ignora selección programática
                userTouched = false;

                if (categories.length == 0 || position < 0 || position >= categories.length) return;

                // Ignora placeholder (si es 0) o la misma pantalla
                if (position == 0 || position == indexThis) return;

                // Navegación centralizada
                Intent intent = CategoryNavigator.buildIntent(Noticias.this, position);
                if (intent == null) {
                    toast("No se pudo abrir la categoría seleccionada.");
                    spinner.post(() -> spinner.setSelection(indexThis, false));
                    return;
                }

                startActivity(intent);

                // Volver visualmente al placeholder o a la posición actual
                spinner.post(() -> spinner.setSelection(indexThis, false));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });
    }

    // ----------------- Botones Web (sin anuncios) -----------------
    private void setupWebButtons() {
        SparseArray<String> map = new SparseArray<>();
        map.put(R.id.nbcnews, "https://ufreetv.com/nbc.html");
        map.put(R.id.cbs,     "https://ufreetv.com/cbs-2-new-york.html");
        map.put(R.id.bbcnews, "https://www.livehdtv.com/bbc-one/");

        for (int i = 0; i < map.size(); i++) {
            final int viewId = map.keyAt(i);
            final String url = map.valueAt(i);

            View btn = findViewById(viewId);
            if (btn == null || url == null || url.isEmpty()) continue;

            btn.setOnClickListener(v -> openWebView(url));
        }
    }

    // ----------------- Atajo especial: abrir RTS con anuncios desde el tile bbcnews -----------------
    private void setupRtsShortcut() {
        View bbcBtn = findViewById(R.id.bbcnews);
        if (bbcBtn != null) {
            bbcBtn.setOnClickListener(v -> {
                if (!canClick()) return;
                String rtsUrl = "https://www.livehdtv.com/bbc-one/";
                openWebViewAds(rtsUrl);
            });
        }
    }

    // ----------------- Tile CNN: abrir con WatchActivityViewGeneral -----------------
    private void setupCnnTile() {
        View cnnTile = findViewById(R.id.cnn);
        if (cnnTile != null) {
            cnnTile.setOnClickListener(v -> {
                if (!canClick()) return;
                String cnnUrl = "https://d3696l48vwq25d.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-0g2918mubifjw/index.m3u8";
                Intent i = WatchActivityViewGeneral.newIntent(Noticias.this, cnnUrl, "CNN en vivo");
                startActivity(i);
            });
        }
    }
}
