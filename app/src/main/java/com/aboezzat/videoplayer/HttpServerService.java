package com.aboezzat.videoplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

public class HttpServerService extends Service {
    private static final int PORT = 8080;
    private static final String CHANNEL_ID = "AboEzzatPlayerChannel";
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AboEzzat Player Service")
                .setContentText("Listening on port " + PORT)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();
        startForeground(1, notification);
        startServer();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AboEzzat Server",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startServer() {
        isRunning = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line = reader.readLine();
                
                String targetVideoUrl = null;

                if (line != null && (line.startsWith("GET") || line.startsWith("POST"))) {
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        String fullPath = parts[1];

                        if (fullPath.contains("url=")) {
                            String raw = fullPath.substring(fullPath.indexOf("url=") + 4);
                            if (raw.contains("&")) {
                                raw = raw.substring(0, raw.indexOf("&"));
                            }
                            targetVideoUrl = URLDecoder.decode(raw, "UTF-8");
                        } else if (fullPath.contains("http://") || fullPath.contains("https://")) {
                            int httpIndex = fullPath.indexOf("http://");
                            if (httpIndex == -1) httpIndex = fullPath.indexOf("https://");
                            targetVideoUrl = URLDecoder.decode(fullPath.substring(httpIndex), "UTF-8");
                        }
                    }
                }

                // رد فوري للمتصفح لمنع خطأ Not Found أو Timeout
                String responseBody = "OK";
                String httpResponse = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain; charset=UTF-8\r\n" +
                        "Content-Length: " + responseBody.getBytes().length + "\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n" +
                        responseBody;

                OutputStream out = socket.getOutputStream();
                out.write(httpResponse.getBytes("UTF-8"));
                out.flush();
                socket.close();

                // تشغيل شاشة المشغل مباشرة إذا تم العثور على الرابط
                if (targetVideoUrl != null && !targetVideoUrl.trim().isEmpty()) {
                    Intent intent = new Intent(HttpServerService.this, PlayerActivity.class);
                    intent.setData(Uri.parse(targetVideoUrl.trim()));
                    intent.putExtra("video_url", targetVideoUrl.trim());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
