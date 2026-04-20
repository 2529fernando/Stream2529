package com.fernan2529;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.fernan2529.WatchViewActivities.WatchActivityViewGeneral;

import java.util.ArrayList;
import java.util.List;

public class DescargasActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private VideoListAdapter adapter;

    private final List<VideoItem> items = new ArrayList<>();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = false;
                for (Boolean v : result.values()) {
                    if (v != null && v) { granted = true; break; }
                }
                if (granted) {
                    loadVideosFromDownloads();
                } else {
                    Toast.makeText(this, "Permiso denegado para leer videos.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_descargas);

        recyclerView = findViewById(R.id.recyclerViewDescargas);
        recyclerView.setLayoutManager(new LinearLayoutManager(this)); // ✅ un video por fila
        recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        );
        adapter = new VideoListAdapter(items, new VideoListAdapter.OnItemClick() {
            @Override public void onClick(int position) {
                if (position < 0 || position >= items.size()) return;
                VideoItem it = items.get(position);
                Intent intent = WatchActivityViewGeneral.newIntent(
                        DescargasActivity.this,
                        it.uri.toString(),
                        it.displayName
                );
                startActivity(intent);
            }
        });
        recyclerView.setAdapter(adapter);

        // Swipe derecha para eliminar
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int position = vh.getAdapterPosition();
                deleteVideo(position);
            }
        }).attachToRecyclerView(recyclerView);

        requestPermissionIfNeededAndLoad();
    }

    private void requestPermissionIfNeededAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{android.Manifest.permission.READ_MEDIA_VIDEO});
        } else {
            permissionLauncher.launch(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE});
        }
    }

    private void loadVideosFromDownloads() {
        items.clear();

        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Video.Media.RELATIVE_PATH : null),
                MediaStore.Video.Media.SIZE
        };

        String selection;
        String[] selectionArgs;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
            selectionArgs = new String[]{"Download/%"};
        } else {
            selection = MediaStore.Video.Media.DATA + " LIKE ?";
            selectionArgs = new String[]{"%/Download/%"};
        }

        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(
                collection,
                trimNulls(projection),
                selection,
                selectionArgs,
                sortOrder
        )) {
            if (cursor == null) {
                Toast.makeText(this, "No se pudo leer la biblioteca de videos.", Toast.LENGTH_SHORT).show();
                return;
            }

            int idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);

            while (cursor.moveToNext()) {
                long   id          = cursor.getLong(idCol);
                String displayName = cursor.getString(nameCol);
                long   sizeBytes   = 0;
                try { sizeBytes = cursor.getLong(sizeCol); } catch (Exception ignored) {}

                Uri contentUri = ContentUris.withAppendedId(collection, id);

                VideoItem item = new VideoItem(
                        contentUri,
                        (displayName != null ? displayName : "video.mp4"),
                        sizeBytes
                );
                items.add(item);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error leyendo videos: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        adapter.notifyDataSetChanged();

        if (items.isEmpty()) {
            Toast.makeText(this, "No se encontraron archivos de video en Descargas.", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteVideo(int position) {
        if (position < 0 || position >= items.size()) return;

        Uri uri = items.get(position).uri;
        ContentResolver resolver = getContentResolver();

        try {
            int rows = resolver.delete(uri, null, null);
            if (rows > 0) {
                String name = items.get(position).displayName;
                items.remove(position);
                adapter.notifyItemRemoved(position);
                Toast.makeText(this, "Video eliminado: " + name, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No se pudo eliminar el video.", Toast.LENGTH_SHORT).show();
                adapter.notifyItemChanged(position);
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Permiso denegado para eliminar el archivo.", Toast.LENGTH_LONG).show();
            adapter.notifyItemChanged(position);
        }
    }

    /** Quita los null del projection para versiones antiguas */
    private static String[] trimNulls(String[] arr) {
        List<String> out = new ArrayList<>(arr.length);
        for (String s : arr) if (s != null) out.add(s);
        return out.toArray(new String[0]);
    }

    /* ---------------------- Modelo ---------------------- */
    static class VideoItem {
        final Uri uri;
        final String displayName;
        final long sizeBytes;

        VideoItem(Uri uri, String displayName, long sizeBytes) {
            this.uri = uri;
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
        }
    }

    /* ---------------------- Adapter (1 fila por item) ---------------------- */
    static class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.VH> {

        interface OnItemClick { void onClick(int position); }

        private final List<VideoItem> data;
        private final OnItemClick listener;

        VideoListAdapter(List<VideoItem> data, OnItemClick listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
            return new VH(v, listener);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            VideoItem it = data.get(position);

            h.title.setText(it.displayName);
            h.size.setText(formatSize(h.itemView, it.sizeBytes));

            // Miniatura del primer segundo del video
            Glide.with(h.itemView.getContext())
                    .load(it.uri)
                    .apply(new RequestOptions().frame(1_000_000).centerCrop())
                    .placeholder(new ColorDrawable(0xFF303030))
                    .into(h.thumb);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView thumb;
            TextView  title;
            TextView  size;

            VH(@NonNull View itemView, OnItemClick listener) {
                super(itemView);
                thumb = itemView.findViewById(R.id.videoThumbnail);
                title = itemView.findViewById(R.id.videoTitle);
                size  = itemView.findViewById(R.id.videoSize);

                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onClick(getAdapterPosition());
                });
            }
        }

        private static String formatSize(View v, long bytes) {
            try {
                return Formatter.formatShortFileSize(v.getContext(), bytes);
            } catch (Exception e) {
                // Fallback manual a MB con 1 decimal
                double mb = bytes / (1024.0 * 1024.0);
                return String.format(java.util.Locale.getDefault(), "%.1f MB", mb);
            }
        }
    }
}
