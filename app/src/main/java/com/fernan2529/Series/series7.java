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
                "T1-02 - El camino real",
                "T1-03 - Señor Nieve",
                "T1-04 - Tullidos, bastardos y cosas rotas",
                "T1-05 - El lobo y el león",
                "T1-06 - Una corona de oro",
                "T1-07 - Ganas o mueres",
                "T1-08 - Por el lado de la punta",
                "T1-09 - Baelor",
                "T1-10 - Fuego y sangre",

                "T2-01 - Se acuerda el Norte",
                "T2-02 - Las tierras de la noche",
                "T2-03 - Lo que está muerto no puede morir",
                "T2-04 - Jardín de huesos",
                "T2-05 - El fantasma de Harrenhal",
                "T2-06 - Los viejos dioses y los nuevos",
                "T2-07 - Un hombre sin honor",
                "T2-08 - El príncipe de Invernalia",
                "T2-09 - Aguas Negras",
                "T2-10 - Valar Morghulis",

                "T3-01 - Valar Dohaeris",
                "T3-02 - Alas oscuras, palabras oscuras",
                "T3-03 - Camino del castigo",
                "T3-04 - Y ahora termina su guardia",
                "T3-05 - Beso de fuego",
                "T3-06 - La escalada",
                "T3-07 - El oso y la doncella",
                "T3-08 - Segundos hijos",
                "T3-09 - Las lluvias de Castamere",
                "T3-10 - Mhysa",

                "T4-01 - Dos espadas",
                "T4-02 - El león y la rosa",
                "T4-03 - Rompedor de cadenas",
                "T4-04 - Guardajuramentos",
                "T4-05 - El primero de su nombre",
                "T4-06 - Las leyes de dioses y hombres",
                "T4-07 - Pinzón",
                "T4-08 - La montaña y la víbora",
                "T4-09 - Los vigilantes en el muro",
                "T4-10 - Los hijos",

                "T5-01 - Las guerras venideras",
                "T5-02 - La Casa de Negro y Blanco",
                "T5-03 - Gorrión Supremo",
                "T5-04 - Hijos de la arpía",
                "T5-05 - Matad al chico",
                "T5-06 - Nunca doblegado, nunca roto",
                "T5-07 - El regalo",
                "T5-08 - Casa Austera",
                "T5-09 - Danza de dragones",
                "T5-10 - Misericordia",

                "T6-01 - La mujer roja",
                "T6-02 - Hogar",
                "T6-03 - Juramentado",
                "T6-04 - Libro del extraño",
                "T6-05 - La puerta",
                "T6-06 - Sangre de mi sangre",
                "T6-07 - El hombre roto",
                "T6-08 - Nadie",
                "T6-09 - La batalla de los bastardos",
                "T6-10 - Los vientos del invierno",

                "T7-01 - Dragonstone",
                "T7-02 - Nacida de la tormenta",
                "T7-03 - La justicia de la reina",
                "T7-04 - Los despojos de la guerra",
                "T7-05 - Vigía del este",
                "T7-06 - Más allá del muro",
                "T7-07 - El dragón y el lobo",

                "T8-01 - Invernalia",
                "T8-02 - Un caballero de los Siete Reinos",
                "T8-03 - La larga noche",
                "T8-04 - El último de los Stark",
                "T8-05 - Las campanas",
                "T8-06 - El trono de hierro"




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

                "https://embed69.org/f/tt0944947-2x01/",
                "https://embed69.org/f/tt0944947-2x02/",
                "https://embed69.org/f/tt0944947-2x03/",
                "https://embed69.org/f/tt0944947-2x04/",
                "https://embed69.org/f/tt0944947-2x05/",
                "https://embed69.org/f/tt0944947-2x06/",
                "https://embed69.org/f/tt0944947-2x07/",
                "https://embed69.org/f/tt0944947-2x08/",
                "https://embed69.org/f/tt0944947-2x09/",
                "https://embed69.org/f/tt0944947-2x10/",

                "https://embed69.org/f/tt0944947-3x01/",
                "https://embed69.org/f/tt0944947-3x02/",
                "https://embed69.org/f/tt0944947-3x03/",
                "https://embed69.org/f/tt0944947-3x04/",
                "https://embed69.org/f/tt0944947-3x05/",
                "https://embed69.org/f/tt0944947-3x06/",
                "https://embed69.org/f/tt0944947-3x07/",
                "https://embed69.org/f/tt0944947-3x08/",
                "https://embed69.org/f/tt0944947-3x09/",
                "https://embed69.org/f/tt0944947-3x10/",

                "https://embed69.org/f/tt0944947-4x01/",
                "https://embed69.org/f/tt0944947-4x02/",
                "https://embed69.org/f/tt0944947-4x03/",
                "https://embed69.org/f/tt0944947-4x04/",
                "https://embed69.org/f/tt0944947-4x05/",
                "https://embed69.org/f/tt0944947-4x06/",
                "https://embed69.org/f/tt0944947-4x07/",
                "https://embed69.org/f/tt0944947-4x08/",
                "https://embed69.org/f/tt0944947-4x09/",
                "https://embed69.org/f/tt0944947-4x10/",

                "https://embed69.org/f/tt0944947-5x01/",
                "https://embed69.org/f/tt0944947-5x02/",
                "https://embed69.org/f/tt0944947-5x03/",
                "https://embed69.org/f/tt0944947-5x04/",
                "https://embed69.org/f/tt0944947-5x05/",
                "https://embed69.org/f/tt0944947-5x06/",
                "https://embed69.org/f/tt0944947-5x07/",
                "https://embed69.org/f/tt0944947-5x08/",
                "https://embed69.org/f/tt0944947-5x09/",
                "https://embed69.org/f/tt0944947-5x10/",

                "https://embed69.org/f/tt0944947-6x01/",
                "https://embed69.org/f/tt0944947-6x02/",
                "https://embed69.org/f/tt0944947-6x03/",
                "https://embed69.org/f/tt0944947-6x04/",
                "https://embed69.org/f/tt0944947-6x05/",
                "https://embed69.org/f/tt0944947-6x06/",
                "https://embed69.org/f/tt0944947-6x07/",
                "https://embed69.org/f/tt0944947-6x08/",
                "https://embed69.org/f/tt0944947-6x09/",
                "https://embed69.org/f/tt0944947-6x10/",

                "https://embed69.org/f/tt0944947-7x01/",
                "https://embed69.org/f/tt0944947-7x02/",
                "https://embed69.org/f/tt0944947-7x03/",
                "https://embed69.org/f/tt0944947-7x04/",
                "https://embed69.org/f/tt0944947-7x05/",
                "https://embed69.org/f/tt0944947-7x06/",
                "https://embed69.org/f/tt0944947-7x07/",

                "https://embed69.org/f/tt0944947-8x01/",
                "https://embed69.org/f/tt0944947-8x02/",
                "https://embed69.org/f/tt0944947-8x03/",
                "https://embed69.org/f/tt0944947-8x04/",
                "https://embed69.org/f/tt0944947-8x05/",
                "https://embed69.org/f/tt0944947-8x06/",


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
