package com.fernan2529;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.util.Pair;
import android.util.SparseArray;
import android.widget.Toast;

import com.fernan2529.Doramas.doramas3;
import com.fernan2529.Series.series6;
import com.fernan2529.WebViewActivities.WebViewActivityGeneral;
import com.fernan2529.WatchViewActivities.WatchActivityViewGeneral;
import com.fernan2529.data.CategoriesRepository;
import com.fernan2529.nav.CategoryNavigator;
import com.fernan2529.vm.MainViewModel;


import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // --- Spinner en Main ---
    private Spinner spinner;
    private String[] categories = new String[0];
    private boolean userTouched = false; // distinguir restauración vs. toque real

    // Anti doble click simple
    private long lastClickAt = 0L;
    private boolean canClick() {
        long now = System.currentTimeMillis();
        if (now - lastClickAt < 300) return false;
        lastClickAt = now;
        return true;
    }

    // Permisos (Activity Result API)
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {});

    private MainViewModel vm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vm = new ViewModelProvider(this).get(MainViewModel.class);


        setupSpinnerInMain();        // spinner interactivo (usa CategoryNavigator)
        resetSpinnerToPlaceholder(); // fuerza "Seleccione la Categoria" al abrir

        checkAndRequestPermissions();
        setupHeaderAndGridButtons();
        setupWebButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetSpinnerToPlaceholder();
    }

    /* =================== Helper para resetear Spinner =================== */
    private void resetSpinnerToPlaceholder() {
        if (spinner == null) return;
        userTouched = false;
        spinner.setSelection(0, false);
        vm.saveSelection(this, 0);
    }

    /* =================== Spinner en Main =================== */
    private void setupSpinnerInMain() {

        spinner = findViewById(R.id.spinner_activities);
        if (spinner == null) return;

        // Datos
        CategoriesRepository repo = new CategoriesRepository();
        String[] loaded = repo.getCategories();
        if (loaded != null && loaded.length > 0) {
            categories = loaded;
        } else {

            categories = new String[] {"Seleccione la Categoria"};
        }

        // Adapter
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);


        spinner.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_UP) {
                userTouched = true;
            }
            return false;
        });

        // Maneja selección: guarda en VM y navega con CategoryNavigator
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!userTouched) return;   // ignora setSelection(0) programático
                userTouched = false;

                // Guarda la selección
                vm.saveSelection(MainActivity.this, position);

                // No navegar con placeholder (índice 0) o si fuera out-of-bounds
                if (position <= 0 || position >= categories.length) return;

                if (canClick()) {
                    Intent intent = CategoryNavigator.buildIntent(MainActivity.this, position);
                    if (intent != null) {
                        startActivity(intent);
                        // Vuelve a placeholder para próximas selecciones
                        spinner.post(() -> {
                            userTouched = false;
                            spinner.setSelection(0, false);
                            vm.saveSelection(MainActivity.this, 0);
                        });
                    } else {
                        Toast.makeText(MainActivity.this, "No se pudo abrir la categoría.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });
    }

    /* =================== Permisos =================== */
    private void checkAndRequestPermissions() {
        List<String> toRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 33) {
            addIfNotGranted(toRequest, Manifest.permission.READ_MEDIA_AUDIO);
            addIfNotGranted(toRequest, Manifest.permission.READ_MEDIA_VIDEO);
            addIfNotGranted(toRequest, Manifest.permission.READ_MEDIA_IMAGES);
            if (!areNotificationsEnabled(this)) {
                addIfNotGranted(toRequest, Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            addIfNotGranted(toRequest, Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!toRequest.isEmpty()) {
            requestPermissionsLauncher.launch(toRequest.toArray(new String[0]));
        }
    }

    private void addIfNotGranted(List<String> list, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            list.add(permission);
        }
    }

    private boolean areNotificationsEnabled(@NonNull Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return nm != null && nm.areNotificationsEnabled();
        }
        return true;
    }

    /* =================== Botones a Activities =================== */
    private void setupHeaderAndGridButtons() {
        SparseArray<Class<?>> map = new SparseArray<>();
        // Cabecera
        map.put(R.id.donaciones,       donaciones.class);
        map.put(R.id.btn_juegos,       GamesActivity.class);
        map.put(R.id.potifyy,          MusicActivity.class);
        map.put(R.id.btnchat,          SplashActivity.class);
        // Fila 1
        map.put(R.id.btn_navegador,    Navegation.class);
        map.put(R.id.btn_descargas,    DownloadlinkActivity.class);
        map.put(R.id.descarg,          DescargasActivity.class);
        // Fila 2
        map.put(R.id.btn_reproductor,  Reproductor.class);
        map.put(R.id.btnaudio,         nubeactivity.class);
        map.put(R.id.btn_ver,          version.class);
        // Posters
        map.put(R.id.prop,             doramas3.class);
        map.put(R.id.avatar,           series6.class);

        for (int i = 0; i < map.size(); i++) {
            final int viewId = map.keyAt(i);
            final Class<?> dest = map.valueAt(i);
            View v = findViewById(viewId);
            if (v == null || dest == null) continue;
            v.setOnClickListener(_v -> {
                if (!canClick()) return;
                startActivity(new Intent(MainActivity.this, dest));
            });
        }

        // CNN a WatchActivityViewGeneral
        View cnnTile = findViewById(R.id.cnn);
        if (cnnTile != null) {
            cnnTile.setOnClickListener(v -> {
                if (!canClick()) return;
                String cnnUrl = "https://d3696l48vwq25d.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-0g2918mubifjw/index.m3u8";
                Intent i = WatchActivityViewGeneral.newIntent(MainActivity.this, cnnUrl, "CNN en vivo");
                startActivity(i);
            });
        }
    }

    /* =================== Botones Web =================== */
    private void setupWebButtons() {
        SparseArray<Pair<String, Class<?>>> web = new SparseArray<>();
        web.put(R.id.espn,   Pair.create("https://www.cablevisionhd.com/espn-en-vivo.html", WebViewActivityGeneral.class));
        web.put(R.id.spidey, Pair.create("https://kllamrd.org/video/tt10872600/",   WebViewActivityGeneral.class));
        web.put(R.id.sony,   Pair.create("https://www.cablevisionhd.com/canal-sony-en-vivo.html", WebViewActivityGeneral.class));

        for (int i = 0; i < web.size(); i++) {
            final int viewId = web.keyAt(i);
            final Pair<String, Class<?>> dest = web.valueAt(i);
            View v = findViewById(viewId);
            if (v == null || dest == null || dest.first == null || dest.second == null) continue;
            v.setOnClickListener(_v -> {
                if (!canClick()) return;
                openWebView(dest.first, dest.second);
            });
        }
    }

    private void openWebView(String url, Class<?> webViewActivity) {
        Intent intent = new Intent(MainActivity.this, webViewActivity);
        intent.putExtra("url", url);
        startActivity(intent);
    }
}
