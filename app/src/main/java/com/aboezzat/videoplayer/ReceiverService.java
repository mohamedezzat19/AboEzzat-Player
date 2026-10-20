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
                .setContentText("السيرفر نشط وجاهز لتشغيل جميع الفيديوهات")
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
                    // نتأكد إن الرابط المستخرج صالح قبل ما نفتح المشغل
                    if (finalPlayUrl != null && !finalPlayUrl.isEmpty() && isPlayable(finalPlayUrl)) {
                        launchPlayer(finalPlayUrl);
                    }
                });

                return newFixedLengthResponse("OK: URL Received");
            }

            return newFixedLengthResponse("AboEzzat TV Receiver is Online");
        }
    }

    private boolean isPlayable(String url) {
        return url.contains("youtube.com") || url.contains("youtu.be")
                || url.contains(".mp4") || url.contains(".m3u8") || url.contains(".mkv")
                || url.contains("googlevideo.com") || url.contains("fbcdn.net") 
                || url.contains("cdninstagram.com") || url.contains("tiktokcdn.com");
    }

    private String resolveMediaUrl(String originalUrl) {
        // 1. يوتيوب والملفات الخام المباشرة
        if (originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be")
                || originalUrl.endsWith(".mp4") || originalUrl.endsWith(".m3u8") || originalUrl.endsWith(".mkv")) {
            return originalUrl;
        }

        // 2. تيك توك: استخدام محرك TikWM الصاروخي المباشر
        if (originalUrl.contains("tiktok.com")) {
            try {
                String endpoint = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                String res = fetchGet(endpoint, 3500);
                if (res != null) {
                    JSONObject json = new JSONObject(res);
                    if (json.optInt("code") == 0 && json.has("data")) {
                        JSONObject data = json.getJSONObject("data");
                        if (data.has("play")) {
                            String playUrl = data.getString("play");
                            if (!playUrl.startsWith("http")) playUrl = "https://www.tikwm.com" + playUrl;
                            return playUrl;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. إنستجرام، فيسبوك، ثريدز، تويتر/X: المحرك المتخصص للمنصات المشفرة
        String[] metaApis = {
            "https://cobalt-api.kwiatekm.tokyo/api/json",
            "https://api.cobalt.0x3.ch/api/json"
        };

        for (String api : metaApis) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("url", originalUrl);
                String res = fetchPost(api, payload.toString(), 3500);
                if (res != null) {
                    JSONObject json = new JSONObject(res);
                    if (json.has("url")) {
                        return json.getString("url");
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 4. محرك مخصص لإنستجرام وثريدز كخطة بديلة سريعة
        if (originalUrl.contains("instagram.com") || originalUrl.contains("threads.net")) {
            try {
                String igEndpoint = "https://api.vkrdown.com/api/get?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                String res = fetchGet(igEndpoint, 3500);
                if (res != null) {
                    JSONObject json = new JSONObject(res);
                    if (json.has("data")) {
                        JSONObject data = json.getJSONObject("data");
                        if (data.has("url")) return data.getString("url");
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return originalUrl;
    }

    private String fetchGet(String targetUrl, int timeout) {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String fetchPost(String targetUrl, String body, int timeout) {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void launchPlayer(String streamUrl) {
        Intent playIntent = new Intent(ReceiverService.this, MainActivity.class);
        playIntent.putExtra("video_url", streamUrl);
        playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(playIntent);
    }
}
