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

// ===== IMPORTS =====
import com.fernan2529.MainActivity;         // cámbialo si tu inicio es otro
import com.fernan2529.Reproductor;
import com.fernan2529.Series.series1;
import com.fernan2529.Series.series2;
import com.fernan2529.Series.series3;
import com.fernan2529.Series.series4;
import com.fernan2529.Series.series5;
import com.fernan2529.Series.series6;

import com.fernan2529.Series.series7;
import com.fernan2529.data.CategoriesRepository;
import com.fernan2529.nav.CategoryNavigator;

public class Serie extends AppCompatActivity {

    private Spinner spinner;
    private String[] categories = new String[0];
    private int indexThis = -1;           // índice real de "Serie"
    private boolean userTouched = false;  // marca interacción real (no programática)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_serie);

        // Botones simples
        setupButton(R.id.series, MainActivity.class);
        setupButton(R.id.btn_ver, MainActivity.class);
        setupButton(R.id.button_reproductor, Reproductor.class);

        // Spinner + Portadas
        setupSpinner();     // ⬅️ lógica unificada con spinner_activities
        setupImageButtons();
    }

    // ----------------- Utilidades -----------------
    private void setupButton(int viewId, Class<?> targetActivity) {
        View v = findViewById(viewId);
        if (v != null && targetActivity != null) {
            v.setOnClickListener(_v -> startActivity(new Intent(this, targetActivity)));
        }
    }

    private void openActivity(Class<?> targetActivity) {
        if (targetActivity == null) return;
        startActivity(new Intent(this, targetActivity));
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

    // ----------------- Spinner con lógica robusta -----------------
    private void setupSpinner() {
        // ✅ usar spinner_activities (no spinner_activities3)
        spinner = findViewById(R.id.spinner_activities);
        if (spinner == null) return;

        // Cargar categorías desde el repositorio central
        CategoriesRepository repo = new CategoriesRepository();
        String[] loaded = repo.getCategories();
        if (loaded != null) categories = loaded;

        // Detectar índice real por nombre
        indexThis = findIndex(categories, "Serie");

        // Fallback seguro si no se encuentra (Serie suele ser 3)
        if (indexThis < 0) {
            indexThis = Math.min(3, Math.max(0, categories.length - 1));
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Mantener “Serie” seleccionado sin disparar navegación
        spinner.setSelection(indexThis, false);

        // Marcar interacción real (evita triggers por setSelection programático)
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
                if (position == 0 || position == indexThis) return; // placeholder o misma pantalla

                // Navegación centralizada
                Intent intent = CategoryNavigator.buildIntent(Serie.this, position);
                if (intent == null) {
                    toast("No se pudo abrir la categoría seleccionada.");
                    spinner.post(() -> spinner.setSelection(0, false));
                    return;
                }

                startActivity(intent);

                // Volver visualmente al placeholder para próximas selecciones
                spinner.post(() -> spinner.setSelection(0, false));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });
    }

    // ----------------- Imágenes (portadas) -----------------
    private void setupImageButtons() {
        SparseArray<Class<?>> map = new SparseArray<>();
        map.put(R.id.theend,       series1.class);
        map.put(R.id.futurama,     series2.class);
        map.put(R.id.luci,         series3.class);
        map.put(R.id.supernatural, series4.class);
        map.put(R.id.chucky,       series5.class);
        map.put(R.id.avatar,       series6.class);
        map.put(R.id.juegode,      series7.class);

        for (int i = 0; i < map.size(); i++) {
            final int viewId = map.keyAt(i);
            final Class<?> dest = map.valueAt(i);

            View iv = findViewById(viewId);
            if (iv == null || dest == null) continue;

            iv.setOnClickListener(v -> openActivity(dest));
        }
    }
}
