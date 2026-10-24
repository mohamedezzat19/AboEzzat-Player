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
import java.io.IOException;
import java.util.Map;

public class HttpServerService extends Service {
    private static final int PORT = 1997;
    private WebServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundService();
        try {
            server = new WebServer(PORT);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startForegroundService() {
        String channelId = "AboEzzatPlayerHttp";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId, "AboEzzat Player Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, channelId)
            .setContentTitle("AboEzzat Player")
            .setContentText("السيرفر يعمل لاستقبال الوسائط")
            .setSmallIcon(R.drawable.app_icon)
            .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class WebServer extends NanoHTTPD {
        public WebServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Map<String, String> parms = session.getParms();
            String uri = session.getUri();

            if ("/play".equals(uri)) {
                String videoUrl = parms.get("url");
                if (videoUrl != null && !videoUrl.isEmpty()) {
                    Intent intent = new Intent("com.aboezzat.videoplayer.PLAY_VIDEO");
                    intent.putExtra("video_url", videoUrl);
                    sendBroadcast(intent);
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
                }
            } else if ("/control".equals(uri)) {
                String action = parms.get("action");
                if (action != null) {
                    Intent intent = new Intent("com.aboezzat.videoplayer.REMOTE_ACTION");
                    intent.putExtra("action", action);
                    sendBroadcast(intent);
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
                }
            }
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "AboEzzat Player Server Running");
        }
    }
}
