package com.aboezzat.videoplayer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.util.Map;

public class ReceiverService extends Service {

    private WebServer server;
    private static final int PORT = 8080; // البورت المستخدم للاستقبال

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (server == null) {
            server = new WebServer(PORT);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return START_STICKY;
    }

    private class WebServer extends NanoHTTPD {
        public WebServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Map<String, String> params = session.getParms();
            String url = params.get("url");

            if (url != null && !url.isEmpty()) {
                Intent playIntent = new Intent(ReceiverService.this, MainActivity.class);
                playIntent.putExtra("video_url", url);
                playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(playIntent);
                return newFixedLengthResponse("OK: Playing " + url);
            }

            return newFixedLengthResponse("AboEzzat TV Receiver is Online. Send ?url=YOUR_VIDEO_URL");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
