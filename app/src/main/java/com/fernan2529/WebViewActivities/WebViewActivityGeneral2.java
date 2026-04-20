package com.fernan2529.WebViewActivities;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.fernan2529.R;

public class WebViewActivityGeneral2 extends AppCompatActivity {

    private WebView webView;
    private ProgressBar loading;
    private boolean videoLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view_general2);

        webView = findViewById(R.id.webView);
        loading = findViewById(R.id.loading);

        // ⚙️ Configuración WebView
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        String url = getIntent().getStringExtra("url");

        loading.setVisibility(View.VISIBLE);

        webView.setWebViewClient(new WebViewClient() {

            // 🔥 Detecta .m3u8 o .mp4 automáticamente
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {

                String requestUrl = request.getUrl().toString();

                if (!videoLoaded && (requestUrl.contains(".m3u8") || requestUrl.contains(".mp4"))) {

                    videoLoaded = true;

                    runOnUiThread(() -> loadOnlyVideo(requestUrl));
                }

                return super.shouldInterceptRequest(view, request);
            }

            // 🔍 Buscar <video> en la página
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                loading.setVisibility(View.GONE);

                if (videoLoaded) return;

                view.evaluateJavascript(
                        "(function() {" +
                                "var v = document.querySelector('video');" +
                                "return v ? v.src : null;" +
                                "})();",
                        value -> {

                            if (value != null && !value.equals("null") && !value.equals("\"\"")) {

                                String videoUrl = value.replace("\"", "");

                                if (!videoLoaded && (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {

                                    videoLoaded = true;
                                    loadOnlyVideo(videoUrl);
                                }
                            }
                        }
                );
            }
        });

        webView.loadUrl(url);
    }

    // 🎬 Mostrar SOLO el video
    private void loadOnlyVideo(String videoUrl) {

        String html =
                "<html><body style='margin:0;background:black;'>" +
                        "<video width='100%' height='100%' controls autoplay>" +
                        "<source src='" + videoUrl + "' type='application/x-mpegURL'>" +
                        "</video>" +
                        "</body></html>";

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }
}