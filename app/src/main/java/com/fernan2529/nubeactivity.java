package com.fernan2529;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fernan2529.WatchViewActivities.WatchActivityViewGeneral;

import java.util.Arrays;
import java.util.List;

public class nubeactivity extends AppCompatActivity {

    private static final String STATE_PIN_OK = "state_pin_ok";
    private static final String CORRECT_PIN = "1997";

    private Button openWebButton;
    private boolean pinGranted = false;

    // Lanzadores de permiso (solo si hace falta en versiones antiguas)
    private ActivityResultLauncher<String> storagePermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pinGranted = savedInstanceState != null && savedInstanceState.getBoolean(STATE_PIN_OK, false);

        // Permiso de almacenamiento (solo pre-Android 10/13 según APIs)
        storagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    // No hacemos nada inmediato; el permiso se usa al descargar si hace falta.
                    if (!isGranted) {
                        Toast.makeText(this, "Permiso de almacenamiento denegado. La descarga puede fallar.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        if (pinGranted) {
            setContentView(R.layout.activity_nubeactivity);
            iniciarContenido();
        } else {
            requestPin();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PIN_OK, pinGranted);
    }

    private void requestPin() {
        final EditText pinInput = new EditText(this);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("Ingresa el PIN");

        new AlertDialog.Builder(this)
                .setTitle("Protección con PIN")
                .setMessage("Introduce el PIN de 4 dígitos para acceder")
                .setCancelable(false)
                .setView(pinInput)
                .setPositiveButton("Aceptar", (dialog, which) -> {
                    String enteredPin = pinInput.getText().toString().trim();
                    if (enteredPin.equals(CORRECT_PIN)) {
                        pinGranted = true;
                        setContentView(R.layout.activity_nubeactivity);
                        iniciarContenido();
                    } else {
                        Toast.makeText(this, "PIN incorrecto", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("Salir", (d, w) -> finish())
                .show();
    }

    private void iniciarContenido() {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setHasFixedSize(true);

        openWebButton = findViewById(R.id.openWebButton);
        openWebButton.setOnClickListener(view -> {
            // 👉 Aquí defines el link solo una vez
            String url = "https://drive.google.com/drive/folders/1ZxTMneku1OYgW3y4P3hRLRx7ABxtapGb";

            Intent intent = new Intent(nubeactivity.this, MainActivityfin.class);
            intent.putExtra("url", url); // Se pasa el link
            startActivity(intent);
        });

        List<String> imageUrls = getImageUrls();
        List<String> videoUrls = getVideoUrls();
        List<String> titles    = getTitles();

        int minSize = Math.min(imageUrls.size(), Math.min(videoUrls.size(), titles.size()));
        ImageAdapter adapter = new ImageAdapter(
                this,
                imageUrls.subList(0, minSize),
                videoUrls.subList(0, minSize),
                titles.subList(0, minSize),
                this::ensureDownloadPermissionThen // callback de permiso antes de descargar
        );
        recyclerView.setAdapter(adapter);
    }

    /** Pide permiso si hace falta (solo en APIs antiguas) y ejecuta la descarga */
    private void ensureDownloadPermissionThen(Runnable onReady) {
        // A partir de Android 10 (API 29) DownloadManager puede escribir en Downloads sin WRITE_EXTERNAL_STORAGE.
        // En Android 13+ se usan READ_MEDIA_* para lectura; para descargar no suele requerirse.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // API 28 o menor
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                Toast.makeText(this, "Vuelve a intentar la descarga si no inicia automáticamente.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        onReady.run();
    }

    // ====== Datos ======

    private List<String> getImageUrls() {
        return Arrays.asList(
                "android.resource://" + getPackageName() + "/" + R.drawable.akira,
                "android.resource://" + getPackageName() + "/" + R.drawable.domestic,
                "android.resource://" + getPackageName() + "/" + R.drawable.ranma,
                "android.resource://" + getPackageName() + "/" + R.drawable.school,
                "android.resource://" + getPackageName() + "/" + R.drawable.schooldos,
                "android.resource://" + getPackageName() + "/" + R.drawable.schooltres,
                "android.resource://" + getPackageName() + "/" + R.drawable.theend,
                "android.resource://" + getPackageName() + "/" + R.drawable.zero,
                "android.resource://" + getPackageName() + "/" + R.drawable.yosuga,
                "android.resource://" + getPackageName() + "/" + R.drawable.boring,
                "android.resource://" + getPackageName() + "/" + R.drawable.dora,
                "android.resource://" + getPackageName() + "/" + R.drawable.futu,
                "android.resource://" + getPackageName() + "/" + R.drawable.mirai,
                "android.resource://" + getPackageName() + "/" + R.drawable.miraitres,
                "android.resource://" + getPackageName() + "/" + R.drawable.miraicinco,
                "android.resource://" + getPackageName() + "/" + R.drawable.mirainikki,
                "android.resource://" + getPackageName() + "/" + R.drawable.musho,
                "android.resource://" + getPackageName() + "/" + R.drawable.nose,
                "android.resource://" + getPackageName() + "/" + R.drawable.bgata
        );
    }

    private List<String> getVideoUrls() {
        return Arrays.asList(
                "https://archive.org/download/n-10_20250302/n%20%2816%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%281%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%2810%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%2811%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%2812%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%2813%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%2814%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%2815%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%2817%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%2818%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%282%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%283%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%284%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%285%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%286%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%287%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%288%29.mp4",
                "https://archive.org/download/n-10_20250302/n%20%289%29.mp4",
                "https://archive.org/download/04_20250505_20250505_1952/04.mp4"
        );
    }

    private List<String> getTitles() {
        return Arrays.asList(
                "05 - La obscuridad revelada",
                "Domestic girlfriend ep1",
                "Ranma ep123 una navidad sin ranma",
                "School Days - 01",
                "School Days - 04",
                "School Days - 11",
                "The End of the F---ing World",
                "Zero no Tsukaima 2 Opening 1   I Say Yes",
                "11 - Sentimos que estamos flotando",
                "bedroom - in my head",
                "Doraemon maquina de reseteo para una nueva vida",
                "Futurama mientras tanto",
                "Mirai Nikki - 02",
                "mirai nikki 3",
                "Mirai nikki ep11",
                "mirai nikki",
                "Mushoku Tensei - 02",
                "No se   Melody   VÍDEO ORIGINAL EN HD",
                "B gata H Kei 04"
        );
    }

    // ===== Adapter =====

    static class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
        private final Context context;
        private final List<String> imageUrls;
        private final List<String> videoUrls;
        private final List<String> titles;
        private final java.util.function.Consumer<Runnable> askPermissionThen;

        ImageAdapter(Context context,
                     List<String> imageUrls,
                     List<String> videoUrls,
                     List<String> titles,
                     java.util.function.Consumer<Runnable> askPermissionThen) {
            this.context = context;
            this.imageUrls = imageUrls;
            this.videoUrls = videoUrls;
            this.titles = titles;
            this.askPermissionThen = askPermissionThen;
            setHasStableIds(true);
        }

        @Override public long getItemId(int position) { return position; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String imageUrl = imageUrls.get(position);
            String videoUrl = videoUrls.get(position);
            String title    = titles.get(position);

            Glide.with(context)
                    .load(Uri.parse(imageUrl))
                    .into(holder.imageView);

            holder.titleText.setText(title);

            // Reproducir con WatchActivityViewGeneral (URL + título)
            holder.imageView.setOnClickListener(v -> {
                Intent intent = WatchActivityViewGeneral.newIntent(context, videoUrl, title);
                context.startActivity(intent);
            });

            // Descargar
            holder.downloadButton.setOnClickListener(v ->
                    askPermissionThen.accept(() -> downloadVideo(videoUrl, title))
            );
        }

        @Override
        public int getItemCount() { return titles.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView titleText;
            Button downloadButton;

            ViewHolder(View itemView) {
                super(itemView);
                imageView      = itemView.findViewById(R.id.imageView);
                titleText      = itemView.findViewById(R.id.titleText);
                downloadButton = itemView.findViewById(R.id.downloadButton);
            }
        }

        private void downloadVideo(String videoUrl, String title) {
            try {
                Uri uri = Uri.parse(videoUrl);
                String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
                DownloadManager.Request request = new DownloadManager.Request(uri)
                        .setTitle(title)
                        .setDescription("Descargando video...")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeTitle + ".mp4");

                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(context, "Descarga iniciada...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Error al iniciar la descarga", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}
