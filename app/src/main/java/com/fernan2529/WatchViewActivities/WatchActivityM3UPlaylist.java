package com.fernan2529.WatchViewActivities;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.fernan2529.R;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.media3.common.MediaMetadata;

public class WatchActivityM3UPlaylist extends AppCompatActivity {

    // === EXTRAS ===
    public static final String EXTRA_M3U_URL     = "extra_m3u_url";
    public static final String EXTRA_M3U_TEXT    = "extra_m3u_text";
    public static final String EXTRA_SINGLE_URL  = "extra_single_url";
    public static final String EXTRA_TITLE       = "extra_title";
    public static final String EXTRA_START_INDEX = "extra_start_index";

    public static Intent newIntentFromM3uUrl(Context ctx, String m3uUrl, @Nullable String title) {
        Intent i = new Intent(ctx, WatchActivityM3UPlaylist.class);
        i.putExtra(EXTRA_M3U_URL, m3uUrl);
        if (title != null) i.putExtra(EXTRA_TITLE, title);
        return i;
    }
    public static Intent newIntentFromM3uText(Context ctx, String m3uText, @Nullable String title) {
        Intent i = new Intent(ctx, WatchActivityM3UPlaylist.class);
        i.putExtra(EXTRA_M3U_TEXT, m3uText);
        if (title != null) i.putExtra(EXTRA_TITLE, title);
        return i;
    }
    public static Intent newIntentFromUrl(Context ctx, String url, @Nullable String title) {
        Intent i = new Intent(ctx, WatchActivityM3UPlaylist.class);
        i.putExtra(EXTRA_SINGLE_URL, url);
        if (title != null) i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    // === UI del layout que pegaste ===
    private PlayerView playerView;                   // @id/player
    private ProgressBar progressBar;                 // @id/progress_bar
    private View loadingOverlay;                     // @id/loading_overlay
    private ProgressBar loadingProgress;             // @id/loading_progress
    private TextView loadingPercent;                 // @id/loading_percent
    private View volumeOverlay;                      // @id/volume_overlay
    private ProgressBar volumeProgress;              // @id/volume_progress
    private TextView volumePercent;                  // @id/volume_percent
    private View seekOverlay;                        // @id/seek_overlay
    private TextView seekDelta;                      // @id/seek_delta
    private TextView seekTarget;                     // @id/seek_target

    // === Controles del controller personalizado (IDs exactos de tu XML) ===
    private ImageView bt_fullscreen, bt_lockscreen, bt_fullscreen_aspect, bt_repeat, bt_rotation_lock, bufferLogo2;
    private ImageView exoRew, exoPlay, exoPause, exoFfwd;
    private View timeBar;
    private TextView videoTitle;

    // === Player ===
    private @Nullable ExoPlayer exoPlayer;

    // Estado de UI/Player
    private boolean isFullScreen = false;
    private boolean isLock = false;
    private boolean isLooping = false;
    private boolean isRotationLocked = false;
    private boolean firstReadyReached = false;

    private final int[] resizeModes = new int[]{
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    };
    private int resizeIndex = 0;

    // Carga animada 1→100 (igual estilo a tu otra activity)
    private Handler loadingHandler;
    private static final int LOADING_TICK_MS = 40;
    private int loadingValue = 1;

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
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;

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
    private static final float RIGHT_EDGE_FRACTION = 0.25f; // 25% derecho

    private boolean seeking = false;
    private float touchStartX = 0f;
    private long startPositionMs = 0L;
    private long targetPositionMs = 0L;
    private boolean downInRightEdge = false;
    private static final float DIR_THRESHOLD = 1.2f;
    private static final long SEEK_FULL_WIDTH_MS = 120_000L;

    // Playlist
    private final ArrayList<ChannelItem> channels = new ArrayList<>();
    private int currentIndex = 0;

    // IO
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_view_general); // tu layout

        // Bind del layout
        playerView       = findViewById(R.id.player);
        progressBar      = findViewById(R.id.progress_bar);
        loadingOverlay   = findViewById(R.id.loading_overlay);
        loadingProgress  = findViewById(R.id.loading_progress);
        loadingPercent   = findViewById(R.id.loading_percent);
        volumeOverlay    = findViewById(R.id.volume_overlay);
        volumeProgress   = findViewById(R.id.volume_progress);
        volumePercent    = findViewById(R.id.volume_percent);
        seekOverlay      = findViewById(R.id.seek_overlay);
        seekDelta        = findViewById(R.id.seek_delta);
        seekTarget       = findViewById(R.id.seek_target);

        if (volumeOverlay != null) volumeOverlay.setVisibility(View.GONE);
        if (seekOverlay   != null) seekOverlay.setVisibility(View.GONE);

        // Controles del controller
        bt_repeat           = playerView.findViewById(R.id.bt_repeat);
        videoTitle          = playerView.findViewById(R.id.video_title);
        bt_rotation_lock    = playerView.findViewById(R.id.bt_rotation_lock);
        bt_lockscreen       = playerView.findViewById(R.id.exo_lock);
        bufferLogo2         = playerView.findViewById(R.id.buffer_logo2);
        exoRew              = playerView.findViewById(R.id.exo_rew);
        exoPlay             = playerView.findViewById(R.id.exo_play);
        exoPause            = playerView.findViewById(R.id.exo_pause);
        exoFfwd             = playerView.findViewById(R.id.exo_ffwd);
        bt_fullscreen_aspect= playerView.findViewById(R.id.bt_fullscreen_aspect);
        bt_fullscreen       = playerView.findViewById(R.id.bt_fullscreen);
        timeBar             = playerView.findViewById(R.id.exo_progress);

        // Título opcional
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null && !title.trim().isEmpty()) {
            setTitle(title);
            if (videoTitle != null) {
                videoTitle.setText(title);
                videoTitle.setVisibility(View.VISIBLE);
            }
        } else if (videoTitle != null) {
            videoTitle.setVisibility(View.GONE);
        }

        // ExoPlayer
        exoPlayer = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(5_000)
                .setSeekForwardIncrementMs(5_000)
                .build();
        playerView.setPlayer(exoPlayer);
        playerView.setKeepScreenOn(true);
        playerView.setResizeMode(resizeModes[resizeIndex]);
        exoPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        playerView.setUseController(true);

        exoPlayer.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
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
            }

            @Override public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (mediaItem != null && mediaItem.mediaMetadata != null && mediaItem.mediaMetadata.title != null) {
                    if (videoTitle != null) {
                        videoTitle.setText(String.valueOf(mediaItem.mediaMetadata.title));
                        videoTitle.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        // Overlay de carga 1→100
        loadingHandler = new Handler(getMainLooper());
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        if (loadingProgress != null) { loadingProgress.setMax(100); loadingProgress.setProgress(1); }
        if (loadingPercent  != null) loadingPercent.setText("1%");
        if (bufferLogo2 != null) bufferLogo2.setVisibility(View.GONE);
        startLoadingAnimation();

        // Botones controller
        if (bt_fullscreen != null) {
            bt_fullscreen.setOnClickListener(v -> {
                if (isLock) return;
                if (!isFullScreen) {
                    bt_fullscreen.setImageDrawable(ContextCompat.getDrawable(
                            this, R.drawable.ic_baseline_fullscreen_exit));
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    isFullScreen = true;
                } else {
                    bt_fullscreen.setImageDrawable(ContextCompat.getDrawable(
                            this, R.drawable.ic_baseline_fullscreen));
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    isFullScreen = false;
                }
            });
        }
        if (bt_lockscreen != null) {
            bt_lockscreen.setOnClickListener(v -> {
                boolean newLock = !isLock;
                lockScreen(newLock);
                bt_lockscreen.setImageDrawable(ContextCompat.getDrawable(
                        this, newLock ? R.drawable.ic_baseline_lock : R.drawable.ic_outline_lock_open
                ));
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

        // Siguiente/Anterior canal con long click en FF/REW
        if (exoFfwd != null) exoFfwd.setOnLongClickListener(v -> { nextChannel(); return true; });
        if (exoRew  != null) exoRew.setOnLongClickListener(v -> { prevChannel(); return true; });

        // Selector de canales con long click en el título (centrado)
        if (videoTitle != null) {
            videoTitle.setOnLongClickListener(v -> { showChannelPicker(); return true; });
        }

        // Back: salir de landscape primero
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

        // Gestos (volumen y seek) con tu overlay
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setupGesturesOnPlayerView();

        // === Cargar playlist ===
        String m3uUrl  = getIntent().getStringExtra(EXTRA_M3U_URL);
        String m3uText = getIntent().getStringExtra(EXTRA_M3U_TEXT);
        String single  = getIntent().getStringExtra(EXTRA_SINGLE_URL);
        currentIndex   = getIntent().getIntExtra(EXTRA_START_INDEX, 0);

        if (m3uText != null && !m3uText.trim().isEmpty()) {
            buildPlaylistFromText(m3uText);
        } else if (m3uUrl != null && !m3uUrl.trim().isEmpty()) {
            loadM3uFromNetwork(m3uUrl);
        } else if (single != null && !single.trim().isEmpty()) {
            channels.clear();
            channels.add(new ChannelItem("Canal", single));
            applyPlaylistAndPlay(currentIndex);
        } else {
            Toast.makeText(this, "No se recibió M3U ni URL de reproducción", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ===== Gestos =====
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
                    return false; // permite mostrar el controller con tap
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

    // ===== Carga M3U =====
    private void loadM3uFromNetwork(String m3uUrl) {
        progress(true);
        ioExecutor.execute(() -> {
            try {
                String txt = downloadText(m3uUrl);
                uiHandler.post(() -> {
                    progress(false);
                    buildPlaylistFromText(txt);
                });
            } catch (IOException e) {
                uiHandler.post(() -> {
                    progress(false);
                    Toast.makeText(this, "Error al descargar M3U: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void buildPlaylistFromText(String m3uText) {
        ArrayList<ChannelItem> list = M3uParser.parse(m3uText);
        if (list.isEmpty()) {
            Toast.makeText(this, "No se encontraron canales en el M3U", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        channels.clear();
        channels.addAll(list);
        if (currentIndex < 0 || currentIndex >= channels.size()) currentIndex = 0;
        applyPlaylistAndPlay(currentIndex);
        Toast.makeText(this, "Canales cargados: " + channels.size(), Toast.LENGTH_SHORT).show();
    }

    private void applyPlaylistAndPlay(int startIndex) {
        if (exoPlayer == null) return;
        ArrayList<MediaItem> mediaItems = new ArrayList<>(channels.size());
        for (ChannelItem ch : channels) {
            MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(ch.url));
            if (ch.name != null && !ch.name.isEmpty()) {
                MediaMetadata md = new MediaMetadata.Builder()
                        .setTitle(ch.name)
                        .build();
                b.setMediaMetadata(md);
            }
            mediaItems.add(b.build());
        }
        exoPlayer.setMediaItems(mediaItems, startIndex, 0);
        exoPlayer.prepare();
        exoPlayer.play();

        if (videoTitle != null) {
            String t = channels.get(startIndex).name;
            if (t != null && !t.isEmpty()) {
                videoTitle.setText(t);
                videoTitle.setVisibility(View.VISIBLE);
            } else {
                videoTitle.setVisibility(View.GONE);
            }
        }
    }

    private static String downloadText(String urlStr) throws IOException {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "ExoPlayer-M3U/1.0");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    conn.disconnect();
                    url = new URL(loc);
                    conn = (HttpURLConnection) url.openConnection();
                }
            }
            is = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    // ===== Navegación de canales =====
    private void showChannelPicker() {
        if (channels.isEmpty()) return;
        String[] names = new String[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            String n = channels.get(i).name;
            names[i] = (n == null || n.isEmpty()) ? ("Canal " + (i + 1)) : n;
        }
        new AlertDialog.Builder(this)
                .setTitle("Selecciona canal")
                .setSingleChoiceItems(names, currentIndex, (dialog, which) -> {
                    dialog.dismiss();
                    playAt(which);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
    private void playAt(int index) {
        if (exoPlayer == null) return;
        if (index < 0 || index >= channels.size()) return;
        currentIndex = index;
        exoPlayer.seekTo(index, 0);
        exoPlayer.play();
        if (videoTitle != null) {
            String t = channels.get(index).name;
            if (t != null && !t.isEmpty()) {
                videoTitle.setText(t);
                videoTitle.setVisibility(View.VISIBLE);
            } else {
                videoTitle.setVisibility(View.GONE);
            }
        }
    }
    private void nextChannel() {
        if (exoPlayer == null || channels.isEmpty()) return;
        int next = (currentIndex + 1) % channels.size();
        playAt(next);
    }
    private void prevChannel() {
        if (exoPlayer == null || channels.isEmpty()) return;
        int prev = (currentIndex - 1 + channels.size()) % channels.size();
        playAt(prev);
    }

    // ===== Overlay de carga =====
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

    // ===== Utilidades =====
    private void progress(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (bufferLogo2  != null) bufferLogo2.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    private void lockScreen(boolean lock) {
        setIconEnabled(bt_fullscreen,       !lock);
        setIconEnabled(bt_repeat,           !lock);
        setIconEnabled(bt_rotation_lock,    !lock);
        setIconEnabled(bt_fullscreen_aspect,!lock);
        setIconEnabled(timeBar,             !lock);
        setIconEnabled(exoRew,              !lock);
        setIconEnabled(exoPlay,             !lock);
        setIconEnabled(exoPause,            !lock);
        setIconEnabled(exoFfwd,             !lock);
        setIconEnabled(bt_lockscreen,       true);
        isLock = lock;
    }
    private void setIconEnabled(View v, boolean enabled) {
        if (v == null) return;
        v.setEnabled(enabled);
        v.setClickable(enabled);
        if (v.getId() == R.id.exo_lock) v.setAlpha(1f);
        else v.setAlpha(enabled ? 1f : 0.35f);
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

    @Override protected void onPause() { super.onPause(); if (exoPlayer != null) exoPlayer.pause(); }
    @Override protected void onStop()  { super.onStop();  if (exoPlayer != null) exoPlayer.setPlayWhenReady(false); }
    @Override protected void onDestroy() {
        if (loadingHandler != null) loadingHandler.removeCallbacksAndMessages(null);
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
    @Override public void onConfigurationChanged(Configuration newConfig) {
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

    // ===== Modelo + Parser M3U =====
    private static class ChannelItem {
        final String name;
        final String url;
        ChannelItem(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
    private static class M3uParser {
        static ArrayList<ChannelItem> parse(String text) {
            ArrayList<ChannelItem> out = new ArrayList<>();
            String[] lines = text.replace("\r","").split("\n");
            String pendingName = null;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#EXTINF")) {
                    // #EXTINF:-1 tvg-id="..." group-title="...",Nombre
                    String name = "";
                    int comma = line.indexOf(',');
                    if (comma >= 0 && comma < line.length()-1) {
                        name = line.substring(comma + 1).trim();
                    }
                    pendingName = name.isEmpty() ? null : name;
                } else if (line.startsWith("#")) {
                    continue;
                } else {
                    String url = line;
                    String name = (pendingName != null) ? pendingName : "";
                    out.add(new ChannelItem(name, url));
                    pendingName = null;
                }
            }
            return out;
        }
    }
}
