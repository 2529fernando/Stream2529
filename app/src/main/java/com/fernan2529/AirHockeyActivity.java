package com.fernan2529;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

public class AirHockeyActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override protected void onResume() { super.onResume(); if (gameView != null) gameView.resume(); }
    @Override protected void onPause()  { if (gameView != null) gameView.pause(); super.onPause(); }

    // =====================================================================
    private static class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

        private Thread gameThread;
        private volatile boolean running = false;

        private int width, height;

        private final Paint textPaint = new Paint();
        private final Paint uiPaint   = new Paint();

        // -------- Bitmaps --------
        private Bitmap boardSrc, boardBmp;
        private Bitmap puckSrc,  puckBmp;
        private Bitmap p1Src,    p1Bmp;  // jugador 1 (abajo)
        private Bitmap p2Src,    p2Bmp;  // jugador 2 / IA (arriba)
        // -------------------------

        // Entidades
        private float puckX, puckY, puckVx, puckVy, puckR;
        private float playerX, playerY, playerR;
        private float aiX, aiY, aiR;

        // Velocidad de paletas (para boost)
        private float playerVx, playerVy, aiVx, aiVy;

        // Física
        private float friction = 0.9865f; // conserva mejor la velocidad
        private float bounce   = 0.85f;
        private float maxPuckSpeed;
        private float minActiveSpeed;

        // IA
        private float aiMaxSpeed;
        private float aiFollow = 0.10f;

        // Marcador / fin de juego
        private int  scorePlayer = 0, scoreAI = 0;
        private long scoreFreezeUntil = 0;
        private static final int WIN_SCORE = 7;
        private boolean gameOver = false;
        private String  winText  = "";

        // UI / modo
        private boolean paused = false, twoPlayers = true; // Multijugador por defecto
        private RectF pauseBtn = new RectF(); // derecha pegado
        private RectF btnResume = new RectF(), btnRestart = new RectF(), btn1P = new RectF(), btn2P = new RectF();
        private float dp;

        // Táctil
        private boolean touchActiveBottom = false, touchActiveTop = false;
        private int pointerBottomId = -1, pointerTopId = -1;

        // Zona jugable (para tableros con esquinas redondeadas)
        private float playInset = 0f;

        // Serves / saques
        private boolean pendingAutoServe = false; // solo para el primer saque
        private long    autoServeAtMs    = 0L;
        private boolean allowServeTop    = false; // solo el que recibió el punto puede sacar
        private boolean allowServeBottom = false;
        private int     nextServe        = 0;     // -1 = top, 1 = bottom, 0 = ninguno
        private float   serveSpeed       = 0f;    // se define al crear superficie

        // Timing
        private static final double TARGET_FPS = 90.0;
        private static final double IDEAL_DT   = 1.0 / TARGET_FPS;

        public GameView(Context ctx) {
            super(ctx);
            getHolder().addCallback(this);
            setFocusable(true);

            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setAntiAlias(true);

            uiPaint.setAntiAlias(true);
            dp = getResources().getDisplayMetrics().density;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            width  = holder.getSurfaceFrame() != null ? holder.getSurfaceFrame().width()  : getWidth();
            height = holder.getSurfaceFrame() != null ? holder.getSurfaceFrame().height() : getHeight();
            if (width <= 0 || height <= 0) { width = Math.max(1, getWidth()); height = Math.max(1, getHeight()); }

            // Tamaños
            puckR   = Math.max(40, width * 0.070f); // disco más grande
            playerR = Math.max(60, width * 0.080f); // manillas más grandes
            aiR     = playerR;

            // Velocidades base (más rápido)
            maxPuckSpeed   = Math.max(16, width * 0.045f);
            minActiveSpeed = Math.max( 6, width * 0.014f);
            aiMaxSpeed     = Math.max(6, height * 0.008f);

            // Velocidad base de saque
            serveSpeed = Math.max(12f, height * 0.030f);

            textPaint.setTextSize(Math.max(40, width * 0.06f));

            // Botón de pausa (derecha, pegado y centrado)
            float btnSize = 44 * dp, inset = 2 * dp, top = height / 2f - btnSize / 2f;
            pauseBtn.set(width - btnSize - inset, top, width - inset, top + btnSize);

            // Menú de pausa (centrado) — 4 botones
            float bw = width * 0.6f, bh = 56 * dp, gap = 14 * dp, cx = width / 2f;
            float startY = height / 2f - (2 * bh + 1.5f * gap);
            btnResume .set(cx - bw / 2f, startY,                        cx + bw / 2f, startY + bh);
            btnRestart.set(cx - bw / 2f, startY + (bh + gap),           cx + bw / 2f, startY + (bh + gap) + bh);
            btn1P    .set(cx - bw / 2f, startY + 2 * (bh + gap),        cx + bw / 2f, startY + 2 * (bh + gap) + bh);
            btn2P    .set(cx - bw / 2f, startY + 3 * (bh + gap),        cx + bw / 2f, startY + 3 * (bh + gap) + bh);

            // Margen jugable para esquinas redondeadas del tablero
            playInset = Math.max(width, height) * 0.02f;

            // Cargar y escalar sprites
            loadBitmaps();
            scaleBitmaps(); // tablero STRETCH a pantalla completa

            twoPlayers = true; // refuerza multijugador al crear
            resetAll();        // pone disco en centro y programa primer saque automático
            running = true;
            gameThread = new Thread(this, "AirHockeyThread"); gameThread.start();
        }

        @Override public void surfaceDestroyed(SurfaceHolder holder) {
            pause();
            recycleBitmap(boardSrc); recycleBitmap(boardBmp);
            recycleBitmap(puckSrc);  recycleBitmap(puckBmp);
            recycleBitmap(p1Src);    recycleBitmap(p1Bmp);
            recycleBitmap(p2Src);    recycleBitmap(p2Bmp);
        }
        @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}

        private void loadBitmaps() {
            boardSrc = BitmapFactory.decodeResource(getResources(), R.drawable.tablero);
            puckSrc  = BitmapFactory.decodeResource(getResources(), R.drawable.disco);
            p1Src    = BitmapFactory.decodeResource(getResources(), R.drawable.bluehand);
            p2Src    = BitmapFactory.decodeResource(getResources(), R.drawable.redhand);
        }

        private void scaleBitmaps() {
            // === Fondo: STRETCH a tamaño exacto de pantalla ===
            if (boardSrc != null) {
                int bw = Math.max(1, width);
                int bh = Math.max(1, height);
                boardBmp = Bitmap.createScaledBitmap(boardSrc, bw, bh, true);
            }

            // Disco y manillas según los radios actuales
            int dPuck = Math.max(1, Math.round(puckR   * 2));
            int dP1   = Math.max(1, Math.round(playerR * 2));
            int dP2   = Math.max(1, Math.round(aiR     * 2));
            if (puckSrc != null) puckBmp = Bitmap.createScaledBitmap(puckSrc, dPuck, dPuck, true);
            if (p1Src   != null)   p1Bmp = Bitmap.createScaledBitmap(p1Src,   dP1,   dP1,   true);
            if (p2Src   != null)   p2Bmp = Bitmap.createScaledBitmap(p2Src,   dP2,   dP2,   true);
        }

        private void recycleBitmap(Bitmap b) { if (b != null && !b.isRecycled()) b.recycle(); }

        // =============================================================
        // Dibujo de texto con estilo unificado: blanco + contorno negro
        private void drawStyledText(Canvas c, String text, float x, float y, float size, float angle) {
            Paint p = new Paint(textPaint);
            p.setTextSize(size);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setAntiAlias(true);

            Paint stroke = new Paint(p);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(4f);
            stroke.setColor(Color.BLACK);

            c.save();
            if (angle != 0) c.rotate(angle, x, y);
            c.drawText(text, x, y, stroke);
            c.drawText(text, x, y, p);
            if (angle != 0) c.restore();
        }
        // =============================================================

        private void drawVerticalScoreLeft(Canvas c) {
            String s = scoreAI + " - " + scorePlayer;
            float margin = 28 * dp;   // más afuera
            float x = margin, y = height / 2f;
            float size = textPaint.getTextSize() * 1.3f;

            drawStyledText(c, s, x, y + size * 0.35f, size, 90);
        }

        private void drawPauseButton(Canvas c) {
            uiPaint.setColor(Color.argb(170, 0, 0, 0));
            c.drawRoundRect(pauseBtn, 12 * dp, 12 * dp, uiPaint);
            uiPaint.setColor(Color.WHITE);
            float pad = 10 * dp, barW = 6 * dp;
            // icono “||”
            c.drawRect(pauseBtn.left + pad,  pauseBtn.top + pad,
                    pauseBtn.left + pad + barW, pauseBtn.bottom - pad, uiPaint);
            c.drawRect(pauseBtn.right - pad - barW, pauseBtn.top + pad,
                    pauseBtn.right - pad,        pauseBtn.bottom - pad, uiPaint);
        }

        private void drawPauseMenu(Canvas c) {
            uiPaint.setColor(Color.argb(160, 0, 0, 0));
            c.drawRect(0, 0, width, height, uiPaint);

            uiPaint.setColor(Color.argb(220, 30, 30, 30));
            float r = 18 * dp;
            c.drawRoundRect(btnResume,  r, r, uiPaint);
            c.drawRoundRect(btnRestart, r, r, uiPaint);
            c.drawRoundRect(btn1P,      r, r, uiPaint);
            c.drawRoundRect(btn2P,      r, r, uiPaint);

            float ts = textPaint.getTextSize() * 0.7f;
            drawStyledText(c, "REANUDAR",          btnResume.centerX(),  btnResume.centerY()  + ts * 0.35f, ts, 0);
            drawStyledText(c, "REINICIAR PARTIDA", btnRestart.centerX(), btnRestart.centerY() + ts * 0.35f, ts, 0);
            drawStyledText(c, "1 JUGADOR (IA)",    btn1P.centerX(),      btn1P.centerY()      + ts * 0.35f, ts, 0);
            drawStyledText(c, "MULTIJUGADOR",      btn2P.centerX(),      btn2P.centerY()      + ts * 0.35f, ts, 0);
        }

        // ========================= GAME LOOP =========================
        @Override
        public void run() {
            final long frameTargetNanos = (long) (1_000_000_000L / TARGET_FPS);
            long lastNano = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                long diff = now - lastNano;
                lastNano = now;

                double dtSec = diff / 1_000_000_000.0;
                double frameScale = dtSec / IDEAL_DT;
                if (frameScale < 0.25) frameScale = 0.25;
                if (frameScale > 2.0 ) frameScale = 2.0;

                update(frameScale);
                drawFrame();

                long elapsed = System.nanoTime() - now;
                long sleepNs = frameTargetNanos - elapsed;
                if (sleepNs > 0) {
                    try {
                        long ms = sleepNs / 1_000_000L, ns = sleepNs % 1_000_000L;
                        if (ms > 0) Thread.sleep(ms, (int) ns); else Thread.sleep(0, (int) Math.max(200_000, ns));
                    } catch (InterruptedException ignored) {}
                } else {
                    try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                }
            }
        }
        // =============================================================

        private void update(double frameScale) {
            long nowMs = System.currentTimeMillis();
            if (gameOver) return;

            if (nowMs < scoreFreezeUntil || paused) {
                playerVx *= 0.85f; playerVy *= 0.85f;
                aiVx     *= 0.85f; aiVy     *= 0.85f;
                return;
            }

            // Primer saque automático (solo una vez)
            if (pendingAutoServe && nowMs >= autoServeAtMs) {
                pendingAutoServe = false;
                // Dirección aleatoria hacia arriba o abajo
                float dir = (Math.random() < 0.5) ? -1f : 1f;
                puckVx = 0f;
                puckVy = dir * serveSpeed;
            }

            // IA (solo en 1 jugador)
            if (!twoPlayers) {
                float dx = puckX - aiX;
                float targetY = (puckY < height * 0.6f) ? puckY : height * 0.22f;
                float dy = targetY - aiY;
                float vx = clamp(dx * aiFollow, -aiMaxSpeed, aiMaxSpeed);
                float vy = clamp(dy * (puckY < height * 0.6f ? 0.10f : 0.08f), -aiMaxSpeed, aiMaxSpeed);
                aiVx = vx; aiVy = vy;
                aiX += vx * (float)frameScale;
                aiY += vy * (float)frameScale;
                aiX = clamp(aiX, playInset + aiR, width - playInset - aiR);
                aiY = clamp(aiY, playInset + aiR, height / 2f - aiR);
            }

            // Disco
            puckX += puckVx * (float)frameScale;
            puckY += puckVy * (float)frameScale;

            float frictionExp = (float) Math.pow(friction, frameScale);
            puckVx *= frictionExp; puckVy *= frictionExp;

            float sp = (float) Math.hypot(puckVx, puckVy);
            if (sp > 0.01f && sp < minActiveSpeed) {
                float k = (minActiveSpeed + 0.0001f) / sp; puckVx *= k; puckVy *= k;
            }
            if (sp > maxPuckSpeed) {
                float k = maxPuckSpeed / sp; puckVx *= k; puckVy *= k;
            }

            // Paredes laterales (respetando playInset)
            if (puckX - puckR < playInset) {
                puckX = playInset + puckR; puckVx = -puckVx * bounce;
            } else if (puckX + puckR > width - playInset) {
                puckX = width - playInset - puckR; puckVx = -puckVx * bounce;
            }

            // Arcos (zona central). Ajustado a 35% del ancho (más pequeño)
            boolean inGoalX = puckX > width * 0.325f && puckX < width * 0.675f;

            if (puckY - puckR < playInset) {
                if (inGoalX) {
                    scorePlayer++;         // anota abajo
                    nextServe = -1;        // saca ARRIBA (recibió punto)
                    checkWinOrReset();
                    return;
                }
                puckY = playInset + puckR; puckVy = -puckVy * bounce;
            }
            if (puckY + puckR > height - playInset) {
                if (inGoalX) {
                    scoreAI++;             // anota arriba
                    nextServe = 1;         // saca ABAJO (recibió punto)
                    checkWinOrReset();
                    return;
                }
                puckY = height - playInset - puckR; puckVy = -puckVy * bounce;
            }

            // Colisiones con manillas (incluye boost por fuerza del golpe)
            resolveCircle(playerX, playerY, playerR, playerVx, playerVy, true);
            resolveCircle(aiX, aiY, aiR, aiVx, aiVy, true);

            // amortiguar “memoria” de velocidad de manillas
            float damp = (float) Math.pow(0.85f, frameScale);
            playerVx *= damp; playerVy *= damp;
            aiVx     *= damp; aiVy     *= damp;
        }

        // Acelera más el disco cuando la manilla golpea con mayor velocidad
        private void resolveCircle(float cx, float cy, float cr, float cvx, float cvy, boolean addImpulse) {
            float dx = puckX - cx, dy = puckY - cy;
            float dist = (float) Math.hypot(dx, dy), minDist = puckR + cr;
            if (dist < 0.0001f) return;

            if (dist < minDist) {
                float overlap = minDist - dist, nx = dx / dist, ny = dy / dist;

                // Separación para evitar “pegado”
                puckX += nx * overlap;
                puckY += ny * overlap;

                // Velocidad relativa disco↔manilla
                float relVx = puckVx - cvx, relVy = puckVy - cvy;
                float dot   = relVx * nx + relVy * ny;

                if (dot < 0) {
                    // Rebote con pérdidas + transferencia básica
                    relVx -= (1 + bounce) * dot * nx;
                    relVy -= (1 + bounce) * dot * ny;
                    puckVx = relVx + (addImpulse ? cvx * 0.18f : 0f);
                    puckVy = relVy + (addImpulse ? cvy * 0.18f : 0f);
                } else if (addImpulse) {
                    // Empujón mínimo si no cierra normal
                    puckVx += nx * 0.06f;
                    puckVy += ny * 0.06f;
                }

                // BOOST dependiente de la velocidad de la manilla (más agresivo)
                if (addImpulse) {
                    float malletSpeed = (float) Math.hypot(cvx, cvy);

                    float boostK     = 0.26f;                         // gana más velocidad por golpe
                    float maxBoost   = Math.max(22f, width * 0.055f); // tope de impulso mayor
                    float boost      = Math.min(malletSpeed * boostK, maxBoost);

                    puckVx += nx * boost;
                    puckVy += ny * boost;

                    // Tope dinámico sube más con golpes fuertes
                    float extraCapK  = 1.10f;
                    float extraMax   = Math.max(36f, width * 0.12f);
                    float dynamicMax = maxPuckSpeed + Math.min(malletSpeed * extraCapK, extraMax);

                    float sp2 = (float) Math.hypot(puckVx, puckVy);
                    if (sp2 > dynamicMax) {
                        float kk = dynamicMax / sp2;
                        puckVx *= kk; puckVy *= kk;
                    }
                } else {
                    // Sin impulso de manilla: respetar cap normal
                    float sp2 = (float) Math.hypot(puckVx, puckVy);
                    if (sp2 > maxPuckSpeed) {
                        float kk = maxPuckSpeed / sp2;
                        puckVx *= kk; puckVy *= kk;
                    }
                }
            }
        }

        private void checkWinOrReset() {
            if (scorePlayer >= WIN_SCORE || scoreAI >= WIN_SCORE) {
                gameOver = true;
                winText = (scorePlayer >= WIN_SCORE) ? "¡GANÓ JUGADOR 1!" : "¡GANÓ JUGADOR 2!";
                puckVx = puckVy = 0f;
                scoreFreezeUntil = Long.MAX_VALUE; // congelado total hasta reinicio
                allowServeTop = allowServeBottom = false; // sin saques
                pendingAutoServe = false;
            } else {
                resetPositions(true); // disco al centro con breve pausa
                // Configura quién puede sacar (solo el que recibió el punto)
                allowServeTop    = (nextServe == -1);
                allowServeBottom = (nextServe == 1);
                pendingAutoServe = false; // no hay auto-saque después del primero
            }
        }

        private void resetAll() {
            scorePlayer = scoreAI = 0; gameOver = false; winText = "";
            nextServe = 0;
            allowServeTop = allowServeBottom = false;

            resetPositions(false);

            // Programa PRIMER saque automático
            pendingAutoServe = true;
            autoServeAtMs    = System.currentTimeMillis() + 700; // 0.7s
        }

        private void resetPositions(boolean afterGoal) {
            puckX = width / 2f; puckY = height / 2f; puckVx = 0; puckVy = 0;

            playerX = width / 2f; playerY = height * 0.80f;
            aiX     = width / 2f; aiY     = height * 0.20f;

            playerVx = playerVy = 0f; aiVx = aiVy = 0f;

            touchActiveBottom = false; pointerBottomId = -1;
            touchActiveTop    = false; pointerTopId    = -1;

            scoreFreezeUntil = afterGoal ? System.currentTimeMillis() + 700 : 0;
        }

        private void drawCentered(Canvas c, Bitmap bmp, float cx, float cy, float radius) {
            if (bmp == null) return;
            float left = cx - radius;
            float top  = cy - radius;
            c.drawBitmap(bmp, left, top, null);
        }

        private void drawFrame() {
            SurfaceHolder holder = getHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) return;

                // Suaviza escalado y bordes (fondo/sprites/textos)
                c.setDrawFilter(new PaintFlagsDrawFilter(
                        0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

                // Fondo tablero (STRETCH a pantalla completa)
                if (boardBmp != null) {
                    c.drawBitmap(boardBmp, 0f, 0f, null);
                } else {
                    c.drawColor(Color.rgb(10,110,140)); // fallback si no hay imagen
                }

                // Marcador vertical IZQUIERDA (estilo unificado)
                drawVerticalScoreLeft(c);

                // Botón de pausa (derecha)
                if (!gameOver) drawPauseButton(c);

                // Sprites
                drawCentered(c, puckBmp,  puckX,   puckY,   puckR);
                drawCentered(c, p1Bmp,    playerX, playerY, playerR);
                drawCentered(c, p2Bmp,    aiX,     aiY,     aiR);

                // Menú de pausa
                if (paused) drawPauseMenu(c);

                // Mensajes
                if (gameOver) {
                    float bigSize = textPaint.getTextSize() * 2.0f;
                    drawStyledText(c, winText, width/2f, height/2f - bigSize*0.4f, bigSize, 0);

                    float sub = textPaint.getTextSize() * 0.7f;
                    drawStyledText(c, "TOCA PARA REINICIAR", width/2f, height/2f + sub*1.5f, sub, 0);
                } else if (Math.abs(puckVx) < 0.01f && Math.abs(puckVy) < 0.01f && !paused && !pendingAutoServe) {
                    // Mostrar quién puede sacar
                    float tipSize = textPaint.getTextSize() * 0.55f;
                    String msg = allowServeBottom ? "SAQUE: JUGADOR 1" : (allowServeTop ? "SAQUE: JUGADOR 2" : "ESPERE...");
                    drawStyledText(c, msg, width / 2f, height / 2f + tipSize * 1.5f, tipSize, 0);
                }

            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();

            // Si terminó, cualquier toque reinicia
            if (gameOver) {
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                    resetAll(); return true;
                }
                return true;
            }

            // Pulsar botón de pausa / menú primero
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                float x = e.getX(e.getActionIndex());
                float y = e.getY(e.getActionIndex());

                if (pauseBtn.contains(x, y)) { paused = !paused; return true; }
                if (paused) {
                    if (btnResume.contains(x, y))  { paused = false; return true; }
                    if (btnRestart.contains(x, y)) { resetAll(); paused = false; return true; }
                    if (btn1P.contains(x, y))      { twoPlayers = false; paused = false; resetAll(); return true; }
                    if (btn2P.contains(x, y))      { twoPlayers = true;  paused = false; resetAll(); return true; }
                    return true;
                }
            }

            if (paused) return true;

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int idx = e.getActionIndex();
                    int id  = e.getPointerId(idx);
                    float x = e.getX(idx), y = e.getY(idx);

                    // ► Saque manual después de los goles:
                    // Solo el lado autorizado puede iniciar el movimiento si el disco está detenido y no hay auto-saque pendiente.
                    if (!pendingAutoServe && Math.abs(puckVx) < 0.01f && Math.abs(puckVy) < 0.01f) {
                        if (y > height / 2f && allowServeBottom) {
                            // Saca abajo hacia ARRIBA
                            puckVx = (x - width / 2f) * 0.004f;
                            puckVy = -serveSpeed;
                            allowServeBottom = allowServeTop = false;
                        } else if (y < height / 2f && allowServeTop) {
                            // Saca arriba hacia ABAJO
                            puckVx = (x - width / 2f) * 0.004f;
                            puckVy =  serveSpeed;
                            allowServeBottom = allowServeTop = false;
                        }
                    }

                    if (y > height / 2f) { pointerBottomId = id; touchActiveBottom = true; moveBottomTo(x, y); }
                    else if (twoPlayers) { pointerTopId    = id; touchActiveTop    = true; moveTopTo(x, y); }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (touchActiveBottom && pointerBottomId != -1) {
                        int idx = e.findPointerIndex(pointerBottomId);
                        if (idx >= 0 && idx < e.getPointerCount()) moveBottomTo(e.getX(idx), e.getY(idx));
                    }
                    if (twoPlayers && touchActiveTop && pointerTopId != -1) {
                        int idx = e.findPointerIndex(pointerTopId);
                        if (idx >= 0 && idx < e.getPointerCount()) moveTopTo(e.getX(idx), e.getY(idx));
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL: {
                    int id = e.getPointerId(e.getActionIndex());
                    if (id == pointerBottomId) { touchActiveBottom = false; pointerBottomId = -1; }
                    if (id == pointerTopId)    { touchActiveTop    = false; pointerTopId    = -1; }
                    break;
                }
            }
            return true;
        }

        private void moveBottomTo(float x, float y) {
            x = clamp(x, playInset + playerR, width  - playInset - playerR);
            y = clamp(y, height / 2f + playerR, height - playInset - playerR);
            playerVx = x - playerX; playerVy = y - playerY;
            playerX = x; playerY = y;
        }

        private void moveTopTo(float x, float y) {
            x = clamp(x, playInset + aiR, width - playInset - aiR);
            y = clamp(y, playInset + aiR, height / 2f - aiR);
            aiVx = x - aiX; aiVy = y - aiY;
            aiX = x; aiY = y;
        }

        private float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

        public void resume() {
            if (running) return;
            running = true;
            gameThread = new Thread(this, "AirHockeyThread"); gameThread.start();
        }
        public void pause() {
            running = false;
            if (gameThread != null) { try { gameThread.join(); } catch (InterruptedException ignored) {} gameThread = null; }
        }
    }
}
