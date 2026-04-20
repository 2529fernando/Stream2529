package com.fernan2529.WatchViewActivities;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.fernan2529.R;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public class WatchActivityViewGeneral extends AppCompatActivity {

    public static final String EXTRA_URL   = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";

    public static Intent newIntent(Context ctx, String url) {
        Intent i = new Intent(ctx, WatchActivityViewGeneral.class);
        i.putExtra(EXTRA_URL, url);
        return i;
    }
    public static Intent newIntent(Context ctx, String url, String title) {
        Intent i = newIntent(ctx, url);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    // Player
    private @Nullable ExoPlayer exoPlayer;
    private PlayerView playerView;
    private ProgressBar progressBar;

    // Controller views
    private ImageView bt_fullscreen, bt_lockscreen, bt_fullscreen_aspect, bt_repeat, bt_rotation_lock, bufferLogo2;
    private ImageView exoRew, exoFfwd;
    private ImageButton exoPlayPause; // Botón unificado
    private View timeBar;
    private TextView videoTitle;

    // Overlays de la Activity
    private View        loadingOverlay;
    private TextView    loadingPercent;
    private ProgressBar loadingProgress;

    // Overlay de volumen
    private View volumeOverlay;
    private ProgressBar volumeProgress;
    private TextView volumePercent;

    // Overlay de seek
    private View seekOverlay;
    private TextView seekDelta, seekTarget;

    // Estados
    private boolean isFullScreen = false;
    private boolean isLock = false;
    private boolean isLooping = false;
    private boolean isRotationLocked = false;
    private boolean firstReadyReached = false;

    // Resize cycle
    private final int[] resizeModes = new int[]{
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    };
    private int resizeIndex = 0;

    // Loading anim
    private Handler loadingHandler;
    private static final int LOADING_TICK_MS = 40;
    private int  loadingValue = 1;

    private final Runnable loadingRunnable = new Runnable() {
        @Override public void run() {
            if (firstReadyReached) return;
            if (loadingValue < 95) {
                loadingValue++;
                updateLoadingUI(loadingValue);
                loadingHandler.postDelayed(this, LOADING_TICK_MS);
            } else {
                loadingHandler.postDelayed(this, LOADING_TICK_MS * 3);
            }
        }
    };

    // Gestos
    private AudioManager audioManager;
    private final Handler uiHandler = new Handler();

    private final Runnable hideVolumeOverlayRunnable = () -> {
        if (volumeOverlay != null) volumeOverlay.setVisibility(View.GONE);
    };
    private final Runnable hideSeekOverlayRunnable = () -> {
        if (seekOverlay != null) seekOverlay.setVisibility(View.GONE);
    };

    private boolean adjustingVolume = false;
    private float touchStartY = 0f;
    private int startVolume = 0;
    private static final float VOLUME_SENSITIVITY = 1.2f;
    private static final float RIGHT_EDGE_FRACTION = 0.25f;

    private boolean seeking = false;
    private float touchStartX = 0f;
    private long startPositionMs = 0L;
    private long targetPositionMs = 0L;
    private boolean downInRightEdge = false;
    private static final float DIR_THRESHOLD = 1.2f;
    private static final long SEEK_FULL_WIDTH_MS = 120_000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Modo Inmersivo Real
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        setContentView(R.layout.activity_watch_view_general);

        playerView       = findViewById(R.id.player);
        progressBar      = findViewById(R.id.progress_bar);
        loadingOverlay   = findViewById(R.id.loading_overlay);
        loadingProgress  = findViewById(R.id.loading_progress);
        loadingPercent   = findViewById(R.id.loading_percent);

        volumeOverlay  = findViewById(R.id.volume_overlay);
        volumeProgress = findViewById(R.id.volume_progress);
        volumePercent  = findViewById(R.id.volume_percent);
        if (volumeOverlay != null) volumeOverlay.setVisibility(View.GONE);

        seekOverlay = findViewById(R.id.seek_overlay);
        seekDelta   = findViewById(R.id.seek_delta);
        seekTarget  = findViewById(R.id.seek_target);
        if (seekOverlay != null) seekOverlay.setVisibility(View.GONE);

        bt_fullscreen        = playerView.findViewById(R.id.bt_fullscreen);
        bt_lockscreen        = playerView.findViewById(R.id.exo_lock);
        bt_fullscreen_aspect = playerView.findViewById(R.id.bt_fullscreen_aspect);
        bt_repeat            = playerView.findViewById(R.id.bt_repeat);
        bt_rotation_lock     = playerView.findViewById(R.id.bt_rotation_lock);
        bufferLogo2          = playerView.findViewById(R.id.buffer_logo2);

        exoRew       = playerView.findViewById(R.id.exo_rew);
        exoFfwd      = playerView.findViewById(R.id.exo_ffwd);
        exoPlayPause = playerView.findViewById(R.id.exo_play_pause); // Enlace al botón unificado
        timeBar      = playerView.findViewById(R.id.exo_progress);
        videoTitle   = playerView.findViewById(R.id.video_title);

        // Soporte para recepción "Abrir con..." y apertura interna
        String url = null;
        String title = null;

        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            // Viene desde otra app (ej. Explorador de archivos)
            url = intent.getData().toString();
            title = "Reproducción Local";
        } else {
            // Viene desde dentro de tu propia app
            url = intent.getStringExtra(EXTRA_URL);
            title = intent.getStringExtra(EXTRA_TITLE);
        }

        if (title != null && !title.trim().isEmpty()) {
            setTitle(title);
            if (videoTitle != null) {
                videoTitle.setText(title);
                videoTitle.setVisibility(View.VISIBLE);
            }
        } else if (videoTitle != null) {
            videoTitle.setVisibility(View.GONE);
        }

        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "No se recibió URL de reproducción", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        exoPlayer = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(5_000)
                .setSeekForwardIncrementMs(5_000)
                .build();

        playerView.setPlayer(exoPlayer);
        playerView.setKeepScreenOn(true);
        playerView.setResizeMode(resizeModes[resizeIndex]);
        exoPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        updateRepeatIcon();
        playerView.setUseController(true);

        loadingHandler = new Handler(getMainLooper());
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        if (loadingProgress != null) { loadingProgress.setMax(100); loadingProgress.setProgress(1); }
        if (loadingPercent  != null) loadingPercent.setText("1%");
        if (bufferLogo2 != null) bufferLogo2.setVisibility(View.GONE);
        startLoadingAnimation();

        // Configuración del Listener del Player
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (progressBar != null) {
                    progressBar.setVisibility(state == Player.STATE_BUFFERING && !firstReadyReached
                            ? View.VISIBLE : View.GONE);
                }

                if (!firstReadyReached) {
                    if (state == Player.STATE_BUFFERING) {
                        playerView.showController();
                        if (bufferLogo2 != null) bufferLogo2.setVisibility(View.VISIBLE);
                        startLoadingAnimation();
                    } else if (state == Player.STATE_READY) {
                        finishLoadingAnimation();
                        if (bufferLogo2 != null) bufferLogo2.setVisibility(View.GONE);
                    }
                }

                updatePlayPauseIcon();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // Actualiza el icono (Play/Pause) inmediatamente cuando cambia el estado
                updatePlayPauseIcon();
            }
        });

        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        exoPlayer.prepare();
        exoPlayer.play();

        setupClickListeners();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (isLock) return;
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    if (bt_fullscreen != null) bt_fullscreen.performClick();
                } else {
                    setEnabled(false);
                    finish();
                }
            }
        });

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setupGesturesOnPlayerView();
    }

    // Función para cambiar los iconos rojos del botón central
    private void updatePlayPauseIcon() {
        if (exoPlayPause == null || exoPlayer == null) return;

        if (exoPlayer.isPlaying()) {
            exoPlayPause.setImageResource(R.drawable.ic_pause_red);
        } else {
            exoPlayPause.setImageResource(R.drawable.ic_play_red);
        }
    }

    private void setupClickListeners() {
        if (bt_fullscreen != null) {
            bt_fullscreen.setOnClickListener(v -> {
                if (isLock) return;
                if (!isFullScreen) {
                    bt_fullscreen.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_fullscreen_exit));
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    isFullScreen = true;
                } else {
                    bt_fullscreen.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_fullscreen));
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    isFullScreen = false;
                }
            });
        }

        if (bt_lockscreen != null) {
            bt_lockscreen.setOnClickListener(v -> {
                boolean newLock = !isLock;
                lockScreen(newLock);
                bt_lockscreen.setImageDrawable(ContextCompat.getDrawable(this, newLock ? R.drawable.ic_baseline_lock : R.drawable.ic_outline_lock_open));
                Toast.makeText(this, newLock ? "Controles bloqueados" : "Controles desbloqueados", Toast.LENGTH_SHORT).show();
            });
        }

        if (bt_fullscreen_aspect != null) {
            bt_fullscreen_aspect.setOnClickListener(v -> {
                if (isLock) return;
                toggleAspectQuickCycle3();
            });
        }

        if (bt_repeat != null) {
            bt_repeat.setOnClickListener(v -> {
                if (isLock) return;
                isLooping = !isLooping;
                if (exoPlayer != null) {
                    exoPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
                }
                updateRepeatIcon();
                Toast.makeText(this, isLooping ? "Repetición activada" : "Repetición desactivada", Toast.LENGTH_SHORT).show();
            });
        }

        if (bt_rotation_lock != null) {
            bt_rotation_lock.setOnClickListener(v -> {
                if (isLock) return;
                isRotationLocked = !isRotationLocked;
                if (isRotationLocked) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                    Toast.makeText(this, "Bloqueo rotacional activado", Toast.LENGTH_SHORT).show();
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    Toast.makeText(this, "Bloqueo rotacional desactivado", Toast.LENGTH_SHORT).show();
                }
                updateRotationLockIcon();
            });
            updateRotationLockIcon();
        }
    }

    private void setupGesturesOnPlayerView() {
        if (playerView == null) return;

        playerView.setOnTouchListener((v, event) -> {
            if (isLock) return false;

            int action = event.getActionMasked();
            int width  = v.getWidth();
            int height = v.getHeight();
            float x = event.getX();
            float y = event.getY();
            float rightEdgeX = width * (1f - RIGHT_EDGE_FRACTION);

            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    adjustingVolume = false;
                    seeking = false;
                    touchStartX = x;
                    touchStartY = y;
                    downInRightEdge = (x >= rightEdgeX);

                    if (exoPlayer != null) {
                        startPositionMs = exoPlayer.getCurrentPosition();
                        targetPositionMs = startPositionMs;
                    } else {
                        startPositionMs = 0L;
                        targetPositionMs = 0L;
                    }
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    return false;
                }
                case MotionEvent.ACTION_MOVE: {
                    float dx = x - touchStartX;
                    float dy = y - touchStartY;
                    float absDx = Math.abs(dx);
                    float absDy = Math.abs(dy);

                    if (!adjustingVolume && !seeking) {
                        if (absDx > absDy * DIR_THRESHOLD) {
                            seeking = true;
                            showSeekOverlay(0, startPositionMs);
                            return true;
                        } else if (downInRightEdge && absDy > absDx * DIR_THRESHOLD) {
                            adjustingVolume = true;
                            showVolumeOverlay(startVolume);
                            return true;
                        } else {
                            return false;
                        }
                    }

                    if (seeking) {
                        long deltaMs = (long)((dx / width) * SEEK_FULL_WIDTH_MS);
                        long duration = (exoPlayer != null) ? exoPlayer.getDuration() : -1L;
                        if (duration <= 0) duration = Long.MAX_VALUE;

                        targetPositionMs = clampLong(startPositionMs + deltaMs, 0L, duration);
                        showSeekOverlay(deltaMs, targetPositionMs);
                        return true;
                    }

                    if (adjustingVolume) {
                        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        float deltaPercent = (-(dy) / height) * VOLUME_SENSITIVITY;
                        int deltaSteps = Math.round(deltaPercent * max);
                        int target = clamp(startVolume + deltaSteps, 0, max);
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                        showVolumeOverlay(target);
                        return true;
                    }

                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (seeking) {
                        if (exoPlayer != null) exoPlayer.seekTo(targetPositionMs);
                        uiHandler.removeCallbacks(hideSeekOverlayRunnable);
                        uiHandler.postDelayed(hideSeekOverlayRunnable, 700);
                        seeking = false;
                        return true;
                    }
                    if (adjustingVolume) {
                        uiHandler.removeCallbacks(hideVolumeOverlayRunnable);
                        uiHandler.postDelayed(hideVolumeOverlayRunnable, 700);
                        adjustingVolume = false;
                        return true;
                    }
                    return false;
                }
            }
            return false;
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isLock) return super.onKeyDown(keyCode, event);

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (audioManager != null) {
                int direction = keyCode == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
                int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                showVolumeOverlay(currentVolume);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showVolumeOverlay(int volume) {
        if (volumeOverlay == null || volumeProgress == null || volumePercent == null) return;
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int pct = Math.round((volume * 100f) / max);
        volumeProgress.setMax(100);
        volumeProgress.setProgress(pct);
        volumePercent.setText(pct + "%");
        if (volumeOverlay.getVisibility() != View.VISIBLE) volumeOverlay.setVisibility(View.VISIBLE);
        uiHandler.removeCallbacks(hideVolumeOverlayRunnable);
        uiHandler.postDelayed(hideVolumeOverlayRunnable, 1200);
    }

    private void showSeekOverlay(long deltaMs, long targetMs) {
        if (seekOverlay == null || seekDelta == null || seekTarget == null) return;
        long deltaSec = deltaMs / 1000;
        String sign = deltaSec > 0 ? "+" : (deltaSec < 0 ? "-" : "");
        seekDelta.setText(sign + Math.abs(deltaSec) + "s");
        seekTarget.setText(formatTime(targetMs));
        if (seekOverlay.getVisibility() != View.VISIBLE) seekOverlay.setVisibility(View.VISIBLE);
        uiHandler.removeCallbacks(hideSeekOverlayRunnable);
        uiHandler.postDelayed(hideSeekOverlayRunnable, 1200);
    }

    private void startLoadingAnimation() {
        if (firstReadyReached) return;
        loadingHandler.removeCallbacks(loadingRunnable);
        loadingHandler.postDelayed(loadingRunnable, LOADING_TICK_MS);
        if (loadingOverlay != null && loadingOverlay.getVisibility() != View.VISIBLE) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        updateLoadingUI(loadingValue);
    }

    private void updateLoadingUI(int value) {
        if (loadingPercent  != null) loadingPercent.setText(value + "%");
        if (loadingProgress != null) loadingProgress.setProgress(value);
    }

    private void finishLoadingAnimation() {
        firstReadyReached = true;
        loadingHandler.removeCallbacks(loadingRunnable);
        loadingValue = 100;
        updateLoadingUI(loadingValue);
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void toggleAspectQuickCycle3() {
        if (playerView == null) return;
        int current = playerView.getResizeMode();
        int next = (current == AspectRatioFrameLayout.RESIZE_MODE_FIT)
                ? AspectRatioFrameLayout.RESIZE_MODE_FILL
                : (current == AspectRatioFrameLayout.RESIZE_MODE_FILL)
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FIT;
        playerView.setResizeMode(next);

        String label = (next == AspectRatioFrameLayout.RESIZE_MODE_FILL)
                ? "Aspecto: Estirado (STRETCH)"
                : (next == AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                ? "Aspecto: Zoom"
                : "Aspecto: Ajustar (FIT)";
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
    }

    private void lockScreen(boolean lock) {
        setIconEnabled(bt_fullscreen,       !lock);
        setIconEnabled(bt_repeat,           !lock);
        setIconEnabled(bt_rotation_lock,    !lock);
        setIconEnabled(bt_fullscreen_aspect,!lock);
        setIconEnabled(timeBar,             !lock);
        setIconEnabled(exoRew,              !lock);
        setIconEnabled(exoPlayPause,        !lock); // Se deshabilita el botón principal unificado
        setIconEnabled(exoFfwd,             !lock);
        setIconEnabled(bt_lockscreen,       true);
        isLock = lock;
    }

    private void setIconEnabled(View v, boolean enabled) {
        if (v == null) return;
        v.setEnabled(enabled);
        v.setClickable(enabled);
        if (v.getId() == R.id.exo_lock) {
            v.setAlpha(1f);
        } else {
            v.setAlpha(enabled ? 1f : 0.35f);
        }
    }

    private void updateRepeatIcon() {
        if (bt_repeat == null) return;
        bt_repeat.setImageResource(R.drawable.ic_repeat);
        bt_repeat.setAlpha(isLooping ? 1.0f : 0.5f);
        bt_repeat.setContentDescription(isLooping ? "Repetición activada" : "Repetición desactivada");
    }

    private void updateRotationLockIcon() {
        if (bt_rotation_lock == null) return;
        bt_rotation_lock.setImageResource(
                isRotationLocked ? R.drawable.ic_screen_rotation_lock : R.drawable.ic_screen_rotation
        );
        bt_rotation_lock.setAlpha(isRotationLocked ? 1.0f : 0.6f);
        bt_rotation_lock.setContentDescription(isRotationLocked ? "Bloqueo rotacional activado" : "Bloqueo rotacional desactivado");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) exoPlayer.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (exoPlayer != null) {
            exoPlayer.pause();
            exoPlayer.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (loadingHandler != null) loadingHandler.removeCallbacksAndMessages(null);
        if (uiHandler != null) uiHandler.removeCallbacksAndMessages(null);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (bt_fullscreen != null) {
            bt_fullscreen.setImageDrawable(ContextCompat.getDrawable(
                    this, isFullScreen ? R.drawable.ic_baseline_fullscreen_exit : R.drawable.ic_baseline_fullscreen
            ));
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
    private static long clampLong(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }
    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        int totalSec = (int) (ms / 1000);
        int s = totalSec % 60;
        int m = (totalSec / 60) % 60;
        int h = totalSec / 3600;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }
}