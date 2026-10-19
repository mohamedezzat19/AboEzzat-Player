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
                .setContentText("السيرفر نشط وجاهز لتشغيل الفيديوهات والبثوث")
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

                return newFixedLengthResponse("OK: Processing media on TV");
            }

            return newFixedLengthResponse("AboEzzat TV Receiver is Online");
        }
    }

    private String resolveMediaUrl(String originalUrl) {
        // روابط يوتيوب أو الملفات المباشرة والبثوث المباشرة الخام
        if (originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be")
                || originalUrl.endsWith(".mp4") || originalUrl.endsWith(".m3u8") || originalUrl.endsWith(".mkv")) {
            return originalUrl;
        }

        // 1. تيك توك: فيديوهات عادية + دعم البث المباشر (Live Streams)
        if (originalUrl.contains("tiktok.com")) {
            try {
                String endpoint = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                String response = fetchHttp(endpoint);
                JSONObject json = new JSONObject(response);
                if (json.optInt("code") == 0 && json.has("data")) {
                    JSONObject data = json.getJSONObject("data");
                    
                    // لو بث مباشر هيرجع رابط m3u8 أو flv
                    if (data.has("live_url")) {
                        return data.getString("live_url");
                    }
                    if (data.has("play")) {
                        String playUrl = data.getString("play");
                        if (!playUrl.startsWith("http")) {
                            playUrl = "https://www.tikwm.com" + playUrl;
                        }
                        return playUrl;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. إنستجرام وفيديوهات ريلز (Instagram Reels & Videos)
        if (originalUrl.contains("instagram.com")) {
            try {
                String endpoint = "https://instagram-media-downloader.vercel.app/api/index?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                String response = fetchHttp(endpoint);
                JSONObject json = new JSONObject(response);
                if (json.has("result")) {
                    JSONArray arr = json.getJSONArray("result");
                    if (arr.length() > 0) {
                        JSONObject media = arr.getJSONObject(0);
                        if (media.has("video")) {
                            return media.getString("video");
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. فيسبوك (Facebook Videos & Reels) + محرك بديل عام
        if (originalUrl.contains("facebook.com") || originalUrl.contains("fb.watch")) {
            try {
                String endpoint = "https://fdown-api.vercel.app/api/fb?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                String response = fetchHttp(endpoint);
                JSONObject json = new JSONObject(response);
                if (json.has("hd")) {
                    return json.getString("hd");
                } else if (json.has("sd")) {
                    return json.getString("sd");
                }
            } catch (Exception ignored) {
            }
        }

        // 4. محرك استخراج شامل احتياطي لجميع المنصات الأخرى
        try {
            String backupEndpoint = "https://all-media-api.vercel.app/api/download?url=" + URLEncoder.encode(originalUrl, "UTF-8");
            String response = fetchHttp(backupEndpoint);
            JSONObject json = new JSONObject(response);
            if (json.has("download_url")) {
                return json.getString("download_url");
            }
        } catch (Exception ignored) {
        }

        return originalUrl;
    }

    private String fetchHttp(String targetUrl) throws Exception {
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
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

