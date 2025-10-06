package com.fernan2529.Series;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

import com.fernan2529.R;
import com.fernan2529.WebViewActivities.WebViewActivityGeneral;

public class series7 extends AppCompatActivity {
    private Spinner spinnerVideos;
    private ArrayAdapter<String> videosAdapter;
    private Random random;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series7);

        spinnerVideos = findViewById(R.id.spinner_videos);
        Button playRandomButton = findViewById(R.id.aleatorio);

        final String[] videoNames = {
                "Seleccione el Capitulo",
                "T1-01 - Se acerca el invierno",
                "T1-02 -El camino real",
                "T1-03 -Señor Nieve",
                "T1-04 -Tullidos, bastardos y cosas rotas",
                "T1-05 -El lobo y el león",
                "T1-06 -Una corona de oro",
                "T1-07 -Ganas o mueres",
                "T1-08 -Por el lado de la punta",
                "T1-09 -Baelor",
                "T1-10 -Fuego y sangre"

        };

        final String[] videoUrls = {
                "",
                "https://embed69.org/f/tt0944947-1x01/",
                "https://embed69.org/f/tt0944947-1x02/",
                "https://embed69.org/f/tt0944947-1x03/",
                "https://embed69.org/f/tt0944947-1x04/",
                "https://embed69.org/f/tt0944947-1x05/",
                "https://embed69.org/f/tt0944947-1x06/",
                "https://embed69.org/f/tt0944947-1x07/",
                "https://embed69.org/f/tt0944947-1x08/",
                "https://embed69.org/f/tt0944947-1x09/",
                "https://embed69.org/f/tt0944947-1x10/",


        };

        videosAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, videoNames);
        videosAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVideos.setAdapter(videosAdapter);

        random = new Random();

        spinnerVideos.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (position != 0) {
                    String selectedVideoUrl = videoUrls[position];
                    if (selectedVideoUrl != null && !selectedVideoUrl.isEmpty()) {
                        openWatchActivity(selectedVideoUrl);
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        playRandomButton.setOnClickListener(v -> {
            if (videoUrls.length > 1) {
                int randomIndex = random.nextInt(videoUrls.length - 1) + 1; // evita índice 0
                String randomVideoUrl = videoUrls[randomIndex];
                if (randomVideoUrl != null && !randomVideoUrl.isEmpty()) {
                    openWatchActivity(randomVideoUrl);
                }
            }
        });
    }

    // Abre WebViewActivityGeneral con la URL seleccionada
    private void openWatchActivity(String videoUrl) {
        Intent intent = new Intent(series7.this, WebViewActivityGeneral.class);
        intent.putExtra("url", videoUrl);
        startActivity(intent);
    }
}
