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
import com.fernan2529.MainActivity;
import com.fernan2529.Reproductor;
import com.fernan2529.WebViewActivities.WebViewActivityGeneral;
import com.fernan2529.WebViewActivities.WebViewActivityGeneral2;
import com.fernan2529.data.CategoriesRepository;
import com.fernan2529.nav.CategoryNavigator;

public class Deportes extends AppCompatActivity {

    private Spinner spinner;
    private String[] categories = new String[0];
    private int indexThis = -1;
    private boolean userTouched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deportes);

        setupButton(R.id.deportes, MainActivity.class);
        setupButton(R.id.btn_ver, MainActivity.class);
        setupButton(R.id.button_reproductor, Reproductor.class);

        setupSpinner();
        setupWebButtons();
    }

    // ---------------- BOTONES ----------------
    private void setupButton(int viewId, Class<?> activityClass) {
        View v = findViewById(viewId);
        if (v != null) {
            v.setOnClickListener(_v -> {
                startActivity(new Intent(this, activityClass));
                finish(); // 🔥 evita acumular pantallas
            });
        }
    }

    // ---------------- WEBVIEW ----------------
    private void openWebView(String url) {
        if (url == null || url.isEmpty()) {
            toast("URL inválida");
            return;
        }

        Intent intent = new Intent(this, WebViewActivityGeneral.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }

    // ---------------- UTIL ----------------
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

    // ---------------- SPINNER ----------------
    private void setupSpinner() {

        spinner = findViewById(R.id.spinner_activities);
        if (spinner == null) return;

        CategoriesRepository repo = new CategoriesRepository();
        String[] loaded = repo.getCategories();
        if (loaded != null && loaded.length > 0) {
            categories = loaded;
        }

        indexThis = findIndex(categories, "Deportes");

        if (indexThis < 0) {
            indexThis = 0;
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setSelection(indexThis, false);

        spinner.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                userTouched = true;
            }
            return false;
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                if (!userTouched) return;
                userTouched = false;

                if (position == indexThis) return;

                Intent intent = CategoryNavigator.buildIntent(Deportes.this, position);

                if (intent != null) {
                    startActivity(intent);
                    finish(); // 🔥 evita acumulación
                } else {
                    toast("Categoría no disponible");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ---------------- BOTONES WEB ----------------
    private void setupWebButtons() {

        SparseArray<String> map = new SparseArray<>();

        map.put(R.id.dsports,     "https://www.cablevisionhd.com/directv-sports-en-vivo.html");
        map.put(R.id.dsports2,    "https://www.cablevisionhd.com/directv-sports-2-en-vivo.html");
        map.put(R.id.dsportsplus, "https://www.cablevisionhd.com/directv-sports-plus-en-vivo.html");
        map.put(R.id.espn,        "https://www.cablevisionhd.com/espn-en-vivo.html");
        map.put(R.id.espn2,       "https://www.cablevisionhd.com/espn-2-en-vivo.html");
        map.put(R.id.espn3,       "https://www.cablevisionhd.com/espn-3-en-vivo.html");
        map.put(R.id.espn4,       "https://www.cablevisionhd.com/espn-4-en-vivo.html");
        map.put(R.id.espnpre,     "https://www.cablevisionhd.com/espn-premium-en-vivo.html");
        map.put(R.id.bein,        "https://www.cablevisionhd.com/bein-sports-extra-en-vivo.html");
        map.put(R.id.movistar,    "https://www.cablevisionhd.com/movistar-deportes-en-vivo.html");
        map.put(R.id.tntsports,   "https://www.cablevisionhd.com/tnt-sports-en-vivo.html");
        map.put(R.id.appletv,     "https://ufreetv.com/fox.html");
        map.put(R.id.goltv,       "https://www.cablevisionhd.com/gol-peru-en-vivo.html");
        map.put(R.id.caracol,     "https://cdn.chatytvgratis.net/caracoltabs.php?width=640&height=410");
        map.put(R.id.nba,         "https://masdeportesonline.com/partidos-nba/");
        map.put(R.id.foxsports,   "https://www.cablevisionhd.com/fox-sports-en-vivo.html");

        for (int i = 0; i < map.size(); i++) {

            final int viewId = map.keyAt(i);
            final String url = map.valueAt(i);

            View btn = findViewById(viewId);

            if (btn != null) {
                btn.setOnClickListener(v -> openWebView(url));
            }
        }
    }
}