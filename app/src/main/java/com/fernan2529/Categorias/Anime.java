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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.util.SparseArray;

import com.fernan2529.R;
import com.fernan2529.data.CategoriesRepository;
import com.fernan2529.nav.CategoryNavigator;

public class Anime extends AppCompatActivity {

    // ========= util: carga segura de clases por nombre =========
    private static Class<?> safeClass(String fqcn) {
        try { return Class.forName(fqcn); }
        catch (Throwable t) { return null; }
    }

    // Ajusta estos FQCN si tus clases están en otro paquete/nombre
    private static final Class<?> HOME_ACTIVITY =
            safeClass("com.fernan2529.MainActivity");
    private static final Class<?> REPRODUCTOR_ACTIVITY =
            safeClass("com.fernan2529.Reproductor");

    private Spinner spinner;
    private String[] categories = new String[0];
    private int indexThis = -1;             // índice real de "Animaciones"
    private boolean userTouched = false;    // distinguir toque real vs. setSelection programático

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_anime);

        // Insets (opcional)
        View root = findViewById(R.id.relativeLayout);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // Botones (solo si la clase objetivo existe)
        safeSetClick(R.id.btn_inicio, HOME_ACTIVITY);
        safeSetClick(R.id.button_reproductor, REPRODUCTOR_ACTIVITY);

        // UI
        setupSpinner();     // ⬅️ tu spinner implementado aquí
        setupImageTiles();
    }

    // ----------------- Utilidades -----------------
    private void safeSetClick(int viewId, Class<?> targetActivity) {
        View v = findViewById(viewId);
        if (v != null && targetActivity != null) {
            v.setOnClickListener(_v -> startActivity(new Intent(this, targetActivity)));
        }
    }

    private void openActivity(Class<?> targetActivity) {
        if (targetActivity == null) {
            Toast.makeText(this, "Actividad no encontrada.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, targetActivity));
    }

    private static int findIndex(String[] arr, String target) {
        if (arr == null || target == null) return -1;
        for (int i = 0; i < arr.length; i++) {
            if (target.equalsIgnoreCase(arr[i])) return i;
        }
        return -1;
    }

    // ----------------- Spinner -----------------
    private void setupSpinner() {
        spinner = findViewById(R.id.spinner_activities);
        if (spinner == null) return;

        // Carga de categorías desde el repo (mismo array que usas en Main)
        CategoriesRepository repo = new CategoriesRepository();
        String[] loaded = repo.getCategories();
        if (loaded != null) {
            categories = loaded;
        }

        // Detecta el índice real de "Animaciones" por nombre (por si cambia el orden)
        indexThis = findIndex(categories, "Animaciones");

        // Si no está, intenta fallback razonable (por ej. 4) sin exceder límites
        if (indexThis < 0) {
            indexThis = Math.min(4, Math.max(0, categories.length - 1));
        }

        // Adapter
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Selecciona la categoría actual sin disparar el listener
        spinner.setSelection(indexThis, false);

        // Marca interacción real del usuario
        spinner.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_UP) {
                userTouched = true;
            }
            return false; // permitir comportamiento normal del spinner
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!userTouched) return; // ignora selección provocada por setSelection()
                userTouched = false;

                // Si no hay categorías válidas, o el índice es inválido, salir
                if (categories.length == 0 || position < 0 || position >= categories.length) return;

                // Ignora placeholder (si usas uno en posición 0) o la misma pantalla
                if (position == 0 || position == indexThis) return;

                // Navega con tu CategoryNavigator
                Intent intent = CategoryNavigator.buildIntent(Anime.this, position);
                if (intent != null) {
                    startActivity(intent);
                }

                // Volver visualmente a placeholder o a la categoría actual sin disparar de nuevo
                spinner.post(() -> spinner.setSelection(0, false));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });
    }

    // ----------------- Portadas (ImageView -> Activity) -----------------
    private void setupImageTiles() {
        // Si renombraste el paquete a minúsculas, cambia a com.fernan2529.anime.animeX
        SparseArray<Class<?>> map = new SparseArray<>();
        map.put(R.id.mirai,    safeClass("com.fernan2529.Anime.anime1"));
        map.put(R.id.ranma,    safeClass("com.fernan2529.Anime.anime2"));
        map.put(R.id.konosuba, safeClass("com.fernan2529.Anime.anime3"));
        map.put(R.id.dnote,    safeClass("com.fernan2529.Anime.anime4"));
        map.put(R.id.magia,    safeClass("com.fernan2529.Anime.anime5"));

        for (int i = 0; i < map.size(); i++) {
            final int viewId = map.keyAt(i);
            final Class<?> dest = map.valueAt(i);
            View tile = findViewById(viewId);
            if (tile != null && dest != null) {
                tile.setOnClickListener(v -> openActivity(dest));
            }
        }
    }
}