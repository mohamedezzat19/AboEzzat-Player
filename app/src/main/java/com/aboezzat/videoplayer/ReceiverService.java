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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                .setContentText("السيرفر نشط وجاهز لتشغيل جميع المنصات")
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

        // 1. تيك توك: فيديوهات عادية وبثوث مباشرة (Live)
        if (originalUrl.contains("tiktok.com")) {
            // فحص إذا كان الرابط بث مباشر Live
            if (originalUrl.contains("/live")) {
                String liveStream = extractTikTokLive(originalUrl);
                if (liveStream != null) return liveStream;
            }

            // الفيديوهات العادية عبر محرك TikWM الناجح
            try {
                String endpoint = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                String response = fetchHttp(endpoint, "GET", null, false);
                JSONObject json = new JSONObject(response);
                if (json.optInt("code") == 0 && json.has("data")) {
                    JSONObject data = json.getJSONObject("data");
                    if (data.has("play")) {
                        String playUrl = data.getString("play");
                        if (!playUrl.startsWith("http")) playUrl = "https://www.tikwm.com" + playUrl;
                        return playUrl;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. فيسبوك (Facebook Videos / Reels)
        if (originalUrl.contains("facebook.com") || originalUrl.contains("fb.watch")) {
            try {
                String endpoint = "https://fdown.net/download.php";
                String postData = "URLz=" + URLEncoder.encode(originalUrl, "UTF-8");
                String html = fetchHttp(endpoint, "POST", postData, true);

                Pattern patternHd = Pattern.compile("id=\"hdlink\"\\s+href=\"([^\"]+)\"");
                Matcher matcherHd = patternHd.matcher(html);
                if (matcherHd.find()) {
                    return decodeHtml(matcherHd.group(1));
                }

                Pattern patternSd = Pattern.compile("id=\"sdlink\"\\s+href=\"([^\"]+)\"");
                Matcher matcherSd = patternSd.matcher(html);
                if (matcherSd.find()) {
                    return decodeHtml(matcherSd.group(1));
                }
            } catch (Exception ignored) {
            }
        }

        // 3. إنستجرام وثريدز (Instagram & Threads)
        if (originalUrl.contains("instagram.com") || originalUrl.contains("threads.net")) {
            try {
                // محرك fastdl المباشر
                String endpoint = "https://api.snapinsta.to/api/video?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                String response = fetchHttp(endpoint, "GET", null, false);
                JSONObject json = new JSONObject(response);
                if (json.has("url")) {
                    return json.getString("url");
                }
            } catch (Exception ignored) {
            }

            // محرك احتياطي سريع لإنستجرام
            try {
                String endpoint = "https://saveig.app/api/ajaxSearch";
                String postData = "q=" + URLEncoder.encode(originalUrl, "UTF-8") + "&t=media&lang=en";
                String res = fetchHttp(endpoint, "POST", postData, true);
                JSONObject json = new JSONObject(res);
                if (json.has("data")) {
                    String htmlData = json.getString("data");
                    Pattern p = Pattern.compile("href=\"(https://[^\"]+download[^\"]+)\"");
                    Matcher m = p.matcher(htmlData);
                    if (m.find()) {
                        return decodeHtml(m.group(1));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return originalUrl;
    }

    private String extractTikTokLive(String liveUrl) {
        try {
            // جلب صفحة البث لسحب رابط التدفق المباشر
            String html = fetchHttp(liveUrl, "GET", null, false);
            Pattern pattern = Pattern.compile("\"liveUrl\":\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String stream = matcher.group(1).replace("\\u002F", "/");
                return stream;
            }

            Pattern patternHls = Pattern.compile("(https://[^\"]+\\.m3u8[^\"]*)");
            Matcher matcherHls = patternHls.matcher(html);
            if (matcherHls.find()) {
                return matcherHls.group(1).replace("\\u0026", "&");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String decodeHtml(String str) {
        return str.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    private String fetchHttp(String targetUrl, String method, String postData, boolean isForm) throws Exception {
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(9000);
        conn.setReadTimeout(9000);

        if ("POST".equalsIgnoreCase(method) && postData != null) {
            if (isForm) {
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            } else {
                conn.setRequestProperty("Content-Type", "application/json");
            }
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
