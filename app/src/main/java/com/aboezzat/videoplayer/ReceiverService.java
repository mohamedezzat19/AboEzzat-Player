package com.aboezzat.videoplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import fi.iki.elonen.NanoHTTPD;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReceiverService extends Service {

    private static final String CHANNEL_ID = "AboEzzatReceiverChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int PORT = 8080;
    private BackgroundServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        startServer();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AboEzzat Receiver Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AboEzzat Player")
                .setContentText("السيرفر نشط وجاهز لتشغيل الميديا")
                .setSmallIcon(R.drawable.app_icon)
                .setOngoing(true)
                .build();
    }

    private void startServer() {
        if (server == null) {
            server = new BackgroundServer(PORT);
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startServer();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
            server = null;
        }
        executor.shutdown();
    }

    private class BackgroundServer extends NanoHTTPD {
        public BackgroundServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Map<String, String> params = session.getParms();
            String rawUrl = params.get("url");

            if (rawUrl != null && !rawUrl.trim().isEmpty()) {
                String targetUrl = rawUrl.trim();

                executor.execute(() -> {
                    String finalPlayUrl = resolveMediaUrl(targetUrl);
                    launchPlayer(finalPlayUrl);
                });

                return newFixedLengthResponse("OK: Playing on TV");
            }

            return newFixedLengthResponse("AboEzzat TV Receiver is Online");
        }
    }

    private String resolveMediaUrl(String originalUrl) {
        if (originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be")
                || originalUrl.endsWith(".mp4") || originalUrl.endsWith(".m3u8") || originalUrl.endsWith(".mkv")) {
            return originalUrl;
        }

        // 1. تيك توك: فيديوهات عادية وبثوث مباشرة
        if (originalUrl.contains("tiktok.com")) {
            try {
                String endpoint = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                String response = fetchHttp(endpoint, "GET", null);
                JSONObject json = new JSONObject(response);
                if (json.optInt("code") == 0 && json.has("data")) {
                    JSONObject data = json.getJSONObject("data");
                    
                    // فحص رابط البث المباشر HLS
                    if (data.has("live_url")) {
                        String live = data.optString("live_url");
                        if (live != null && !live.isEmpty()) return live;
                    }
                    if (data.has("play")) {
                        String playUrl = data.getString("play");
                        if (!playUrl.startsWith("http")) playUrl = "https://www.tikwm.com" + playUrl;
                        return playUrl;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. محرك بديل فوري لفيسبوك وإنستجرام عبر Rapid Extraction API
        try {
            String apiUrl = "https://co.wuk.sh/api/json";
            JSONObject body = new JSONObject();
            body.put("url", originalUrl);

            String response = fetchHttp(apiUrl, "POST", body.toString());
            JSONObject resJson = new JSONObject(response);
            if (resJson.has("url")) {
                return resJson.getString("url");
            }
        } catch (Exception ignored) {
        }

        // 3. محرك إنستجرام الاحتياطي المباشر
        if (originalUrl.contains("instagram.com")) {
            try {
                String endpoint = "https://api.threadsphotodownloader.com/v2/media?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                String response = fetchHttp(endpoint, "GET", null);
                JSONObject json = new JSONObject(response);
                if (json.has("video_url")) {
                    return json.getString("video_url");
                }
            } catch (Exception ignored) {
            }
        }

        return originalUrl;
    }

    private String fetchHttp(String targetUrl, String method, String postData) throws Exception {
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        if ("POST".equalsIgnoreCase(method) && postData != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes("UTF-8"));
            }
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void launchPlayer(String streamUrl) {
        Intent playIntent = new Intent(ReceiverService.this, MainActivity.class);
        playIntent.putExtra("video_url", streamUrl);
        playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(playIntent);
    }
}
