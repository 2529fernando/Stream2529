package com.fernan2529;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class FingerBattleActivity extends AppCompatActivity {
    private GameView gameView;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finger_battle);

        // Modo inmersivo
        final View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout container = findViewById(R.id.game_container);
        gameView = new GameView(this);
        container.addView(gameView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @Override protected void onPause() { super.onPause(); gameView.pause(); }
    @Override protected void onResume() { super.onResume(); gameView.resume(); }

    // ========================== GAME VIEW ==========================
    static class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
        private Thread loop;
        private volatile boolean running = false;
        private int W, H;

        enum State { MENU, PLAY, WIN }
        private State state = State.MENU;

        // División (0 = todo azul, H = todo rojo) — usamos ySplit como límite entre colores
        private float ySplit;

        // Pinturas y UI
        private final Paint pRed = new Paint();
        private final Paint pBlue = new Paint();
        private final Paint pBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF startBtn = new RectF();
        private final Paint pTitle = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Dedo y velocidad
        private static class Finger {
            int id = -1; boolean active = false;
            float lastY; long lastT; float speedAbs;
            void update(float y, long t) {
                if (active) {
                    float dy = y - lastY;
                    float dt = (t - lastT) / 1_000_000_000f;
                    if (dt > 0) {
                        float inst = Math.abs(dy) / dt;
                        speedAbs = 0.75f * speedAbs + 0.25f * inst; // EMA
                    }
                }
                lastY = y; lastT = t; active = true;
            }
            void reset() { id = -1; active = false; speedAbs = 0; }
        }
        private final Finger top = new Finger();    // rojo (arriba)
        private final Finger bottom = new Finger(); // azul (abajo)

        // *** Constantes de “velocidad/empije” — SIN CAMBIOS ***
        private final float PRESS_PUSH = 420f;     // empuje base al presionar
        private final float SPEED_GAIN = 0.00035f; // bonus por mover rápido
        private final float MAX_SPEED = 3500f;
        private final float DAMPING = 0.985f;

        private long lastNs;

        public GameView(Context ctx) {
            super(ctx);
            getHolder().addCallback(this);
            setKeepScreenOn(true);

            pRed.setColor(Color.rgb(206, 38, 38));      // rojo
            pBlue.setColor(Color.rgb(0, 150, 255));     // azul
            pWhite.setColor(Color.WHITE);
            pBlack.setColor(Color.BLACK);
            pBlack.setTextAlign(Paint.Align.CENTER);
            pBlack.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));

            pOutline.setColor(Color.BLACK);
            pOutline.setStyle(Paint.Style.STROKE);
            pOutline.setStrokeWidth(12f);

            pTitle.setColor(Color.BLACK);
            pTitle.setTextAlign(Paint.Align.CENTER);
            pTitle.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));

            setOnTouchListener(this::onTouch);
        }

        // ---------- Surface ----------
        @Override public void surfaceCreated(SurfaceHolder holder) { start(); }
        @Override public void surfaceDestroyed(SurfaceHolder holder) { stop(); }
        @Override public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
            W = w; H = h;
            ySplit = H / 2f;

            float btnW = W * 0.52f, btnH = H * 0.16f;
            startBtn.set(W/2f - btnW/2f, H/2f - btnH/2f, W/2f + btnW/2f, H/2f + btnH/2f);

            pTitle.setTextSize(Math.min(W, H) * 0.085f);
        }

        private void start() { running = true; loop = new Thread(this, "Loop"); loop.start(); }
        private void stop()  { running = false; if (loop!=null) try{ loop.join(); }catch(InterruptedException ignore){} }
        public void pause(){ stop(); }
        public void resume(){ if(!running) start(); }

        // ---------- Loop ----------
        @Override public void run() {
            lastNs = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                float dt = (now - lastNs) / 1_000_000_000f;
                lastNs = now;
                update(dt);
                drawFrame();
            }
        }

        private void update(float dt) {
            if (state != State.PLAY) return;

            // Decaimiento suave idéntico para ambos
            top.speedAbs *= DAMPING;
            bottom.speedAbs *= DAMPING;

            float sTop = Math.min(top.speedAbs, MAX_SPEED);
            float sBot = Math.min(bottom.speedAbs, MAX_SPEED);

            // Empuje por presionar (SIMÉTRICO y sin cambiar valores):
            // rojo (arriba) empuja hacia abajo (+), azul (abajo) empuja hacia arriba (-)
            float pressTop = top.active ? 1f : 0f;
            float pressBot = bottom.active ? 1f : 0f;
            float pushPress = (pressTop - pressBot) * PRESS_PUSH;

            // Empuje por velocidad (SIMÉTRICO):
            float pushSpeed = (sTop - sBot) * SPEED_GAIN * H;

            float push = pushPress + pushSpeed; // px/s
            ySplit += push * dt;

            if (ySplit <= 0) { ySplit = 0; state = State.WIN; }      // azul ocupó todo
            else if (ySplit >= H) { ySplit = H; state = State.WIN; } // rojo ocupó todo
        }

        // ---------- Input ----------
        private boolean onTouch(View v, MotionEvent e) {
            int action = e.getActionMasked();
            long t = System.nanoTime();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int idx = e.getActionIndex();
                    int id = e.getPointerId(idx);
                    float y = e.getY(idx);
                    if (y < ySplit) { top.id = id; top.update(y, t); }
                    else { bottom.id = id; bottom.update(y, t); }
                    if (state == State.MENU) state = State.PLAY;
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        int id = e.getPointerId(i);
                        float y = e.getY(i);
                        if (id == top.id) top.update(y, t);
                        else if (id == bottom.id) bottom.update(y, t);
                        else {
                            if (y < ySplit && top.id == -1) { top.id = id; top.update(y, t); }
                            else if (y >= ySplit && bottom.id == -1) { bottom.id = id; bottom.update(y, t); }
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL: {
                    int idx = e.getActionIndex();
                    int id = e.getPointerId(idx);
                    if (id == top.id) top.reset();
                    if (id == bottom.id) bottom.reset();

                    if (state == State.WIN) {
                        float x = e.getX(idx), y = e.getY(idx);
                        if (startBtn.contains(x, y)) resetMatch();
                    }
                    break;
                }
            }

            // Botón de inicio en menú
            if (state == State.MENU &&
                    (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)) {
                int idx = e.getActionIndex();
                float x = e.getX(idx), y = e.getY(idx);
                if (startBtn.contains(x, y)) state = State.PLAY;
            }
            return true;
        }

        private void resetMatch() {
            ySplit = H/2f;
            top.reset(); bottom.reset();
            state = State.MENU;
        }

        // ---------- Render ----------
        private void drawFrame() {
            Canvas c = null;
            try {
                c = getHolder().lockCanvas();
                if (c == null) return;

                // Fondo dividido (ROJO arriba, AZUL abajo)
                c.drawRect(0, 0, W, Math.max(0, ySplit), pRed);
                c.drawRect(0, Math.max(0, ySplit), W, H, pBlue);

                // Línea central
                pWhite.setStrokeWidth(6f);
                c.drawLine(0, ySplit, W, ySplit, pWhite);

                if (state == State.MENU) {
                    // Botón “PRESS TO START”
                    c.drawRoundRect(startBtn, 40, 40, pWhite);
                    c.drawRoundRect(startBtn, 40, 40, pOutline);
                    pBlack.setTextSize(Math.min(W, H) * 0.075f);
                    float cx = startBtn.centerX(), cy = startBtn.centerY();
                    c.drawText("PRESS", cx, cy - pBlack.getTextSize()*0.6f, pBlack);
                    c.drawText("TO",     cx, cy + pBlack.getTextSize()*0.05f, pBlack);
                    c.drawText("START",  cx, cy + pBlack.getTextSize()*0.75f, pBlack);
                } else if (state == State.WIN) {
                    String msg = (ySplit <= 0) ? "BLUE WINS" : "RED WINS";
                    c.drawText(msg, W/2f, H/2f - pTitle.getTextSize()*0.8f, pTitle);

                    c.drawRoundRect(startBtn, 40, 40, pWhite);
                    c.drawRoundRect(startBtn, 40, 40, pOutline);
                    pBlack.setTextSize(Math.min(W, H) * 0.075f);
                    c.drawText("PLAY",  startBtn.centerX(), startBtn.centerY() - pBlack.getTextSize()*0.3f, pBlack);
                    c.drawText("AGAIN", startBtn.centerX(), startBtn.centerY() + pBlack.getTextSize()*0.9f, pBlack);
                }
            } finally {
                if (c != null) getHolder().unlockCanvasAndPost(c);
            }
        }
    }
}
