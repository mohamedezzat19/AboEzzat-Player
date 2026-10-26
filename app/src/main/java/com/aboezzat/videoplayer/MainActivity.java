package com.aboezzat.videoplayer;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private LinearLayout layoutIdle;
    private TextView tvStatus, tvIp;
    private ImageView ivMemorial;
    private WebServer server;
    private static final int PORT = 8081;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        layoutIdle = findViewById(R.id.layout_idle);
        tvStatus = findViewById(R.id.tv_status);
        tvIp = findViewById(R.id.tv_ip);
        ivMemorial = findViewById(R.id.iv_memorial);

        initPlayer();
        startServer();
        displayIpAddress();
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    showIdleView();
                }
            }
        });
    }

    private void playMedia(String url) {
        runOnUiThread(() -> {
            layoutIdle.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);

            MediaItem mediaItem = MediaItem.fromUri(url);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        });
    }

    private void showIdleView() {
        runOnUiThread(() -> {
            if (player != null) {
                player.stop();
            }
            playerView.setVisibility(View.GONE);
            layoutIdle.setVisibility(View.VISIBLE);
        });
    }

    private void startServer() {
        server = new WebServer(PORT);
        try {
            server.start();
            tvStatus.setText("حالة السيرفر: جاهز للتشغيل ✓");
        } catch (IOException e) {
            tvStatus.setText("خطأ في بدء السيرفر: " + e.getMessage());
        }
    }

    private void displayIpAddress() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        tvIp.setText("عنوان الاتصال: http://" + ip + ":" + PORT);
    }

    private class WebServer extends NanoHTTPD {
        public WebServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Method method = session.getMethod();
            String uri = session.getUri();

            if (Method.POST.equals(method) && "/play".equals(uri)) {
                Map<String, String> files = new HashMap<>();
                try {
                    session.parseBody(files);
                    Map<String, String> params = session.getParms();
                    String videoUrl = params.get("url");

                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        playMedia(videoUrl);
                        return newFixedLengthResponse(Response.Status.OK, "text/plain", "Playing");
                    }
                } catch (Exception e) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
                }
            } else if (Method.POST.equals(method) && "/stop".equals(uri)) {
                showIdleView();
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "Stopped");
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
