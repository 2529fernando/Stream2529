package com.fernan2529;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class GamesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_games);
        setTitle("Games");
    }

    // Handlers usados en android:onClick del XML
    public void openA(android.view.View v) {
        startActivity(new Intent(this, AirHockeyActivity.class));
    }

    public void openB(android.view.View v) {
        startActivity(new Intent(this, TetrisActivity.class));
    }

    public void openC(android.view.View v) {
        startActivity(new Intent(this, FingerBattleActivity.class));
    }



}
