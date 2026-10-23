package com.aboezzat.videoplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import fi.iki.elonen.NanoHTTPD;
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
    private AudioManager audioManager;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
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
                .setContentText("السيرفر نشط ومستعد للاستقبال والتحكم الفوري")
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
            String uri = session.getUri();

            // 1. استقبال أوامر الريموت للتحكم في المشغل ومستوى الصوت
            String cmd = params.get("cmd");
            if (cmd == null) cmd = params.get("command");

            if (cmd != null && !cmd.trim().isEmpty()) {
                handleRemoteCommand(cmd.trim().toLowerCase());
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "CMD_OK: " + cmd);
            }

            // أوامر من مسار الرابط المباشر مثل /play_pause أو /next
            if (uri != null && uri.length() > 1) {
                String directCmd = uri.substring(1).toLowerCase();
                if (isRemoteCommand(directCmd)) {
                    handleRemoteCommand(directCmd);
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "CMD_OK: " + directCmd);
                }
            }

            // 2. طلبات الدمج (فيديو + صوت)
            String videoUrl = params.get("video");
            String audioUrl = params.get("audio");
            if (videoUrl != null && !videoUrl.trim().isEmpty()) {
                String finalVideo = videoUrl.trim();
                String finalAudio = (audioUrl != null && !audioUrl.trim().isEmpty()) ? audioUrl.trim() : null;

                executor.execute(() -> {
                    String resolvedVideo = resolveMediaUrl(finalVideo);
                    launchPlayerWithMerge(resolvedVideo, finalAudio);
                });

                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK: Merged Stream Received");
            }

            // 3. طلبات إضافة لقائمة التشغيل (Queue / Playlist)
            String queueUrl = params.get("queue");
            if (queueUrl != null && !queueUrl.trim().isEmpty()) {
                sendQueueToPlayer(queueUrl.trim());
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK: Added To Queue");
            }

            // 4. الرابط المباشر المفرد (فيديو أو صورة أو تيك توك أو يوتيوب)
            String rawUrl = params.get("url");
            if (rawUrl != null && !rawUrl.trim().isEmpty()) {
                String targetUrl = rawUrl.trim();

                executor.execute(() -> {
                    String finalPlayUrl = resolveMediaUrl(targetUrl);
                    if (finalPlayUrl != null && !finalPlayUrl.isEmpty()) {
                        launchPlayer(finalPlayUrl);
                    }
                });

                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK: URL Received");
            }

            return newFixedLengthResponse(Response.Status.OK, "text/plain", "AboEzzat TV Receiver is Online");
        }
    }

    private boolean isRemoteCommand(String cmd) {
        return cmd.equals("play_pause") || cmd.equals("play") || cmd.equals("pause")
                || cmd.equals("ff") || cmd.equals("forward")
                || cmd.equals("rw") || cmd.equals("rewind")
                || cmd.equals("next") || cmd.equals("prev") || cmd.equals("previous")
                || cmd.equals("vol_up") || cmd.equals("vol_down") || cmd.equals("mute")
                || cmd.equals("loop_toggle");
    }

    private void handleRemoteCommand(String cmd) {
        // التحكم في صوت الشاشة مباشرة
        if (cmd.equals("vol_up")) {
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            }
            return;
        } else if (cmd.equals("vol_down")) {
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            }
            return;
        } else if (cmd.equals("mute")) {
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                } else {
                    audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
                }
            }
            return;
        }

        // إرسال أوامر التحكم في المشغل لـ MainActivity
        Intent cmdIntent = new Intent(this, MainActivity.class);
        cmdIntent.putExtra("command", cmd);
        cmdIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(cmdIntent);
    }

    private void sendQueueToPlayer(String queueUrl) {
        Intent queueIntent = new Intent(this, MainActivity.class);
        queueIntent.putExtra("queue_url", queueUrl);
        queueIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(queueIntent);
    }

    private String resolveMediaUrl(String originalUrl) {
        if (originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be")
                || originalUrl.endsWith(".mp4") || originalUrl.endsWith(".m3u8") || originalUrl.endsWith(".mkv")
                || originalUrl.endsWith(".jpg") || originalUrl.endsWith(".png") || originalUrl.endsWith(".webp") || originalUrl.endsWith(".jpeg")) {
            return originalUrl;
        }

        if (originalUrl.contains("tiktok.com")) {
            try {
                String endpoint = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(originalUrl, "UTF-8");
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.optInt("code") == 0 && json.has("data")) {
                        JSONObject data = json.getJSONObject("data");
                        if (data.has("play")) {
                            String playUrl = data.getString("play");
                            if (!playUrl.startsWith("http")) {
                                playUrl = "https://www.tikwm.com" + playUrl;
                            }
                            return playUrl;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return originalUrl;
    }

    private void launchPlayer(String streamUrl) {
        Intent playIntent = new Intent(ReceiverService.this, MainActivity.class);
        playIntent.putExtra("video_url", streamUrl);
        playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(playIntent);
    }

    private void launchPlayerWithMerge(String streamUrl, String audioUrl) {
        Intent playIntent = new Intent(ReceiverService.this, MainActivity.class);
        playIntent.putExtra("video_url", streamUrl);
        if (audioUrl != null && !audioUrl.isEmpty()) {
            playIntent.putExtra("audio_url", audioUrl);
        }
        playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(playIntent);
    }
}
