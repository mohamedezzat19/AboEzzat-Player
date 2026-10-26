package com.aboezzat.player;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends Activity {

    // المنفذ الموحد المعتمد
    private static final int SERVER_PORT = 8081;

    private StyledPlayerView playerView;
    private LinearLayout layoutIdle;
    private TextView tvStatus;
    private TextView tvIp;

    private ExoPlayer player;
    private WebServer server;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        layoutIdle = findViewById(R.id.layout_idle);
        tvStatus = findViewById(R.id.tv_status);
        tvIp = findViewById(R.id.tv_ip);

        initPlayer();
        startServer();
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    showIdleScreen();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "خطأ في تشغيل الرابط: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    showIdleScreen();
                });
            }
        });
    }

    private void startServer() {
        try {
            server = new WebServer(SERVER_PORT);
            server.start();

            String ipAddress = getDeviceIpAddress();
            tvStatus.setText("حالة السيرفر: جاهز للتشغيل ✓");
            tvIp.setText("عنوان الاتصال: http://" + ipAddress + ":" + SERVER_PORT);
        } catch (IOException e) {
            tvStatus.setText("تعذر تشغيل السيرفر على المنفذ " + SERVER_PORT);
            tvIp.setText("تأكد من عدم تعارض المنفذ");
        }
    }

    private class WebServer extends NanoHTTPD {

        public WebServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Map<String, String> params = session.getParms();

            // 1. استقبال الروابط المفردة والبث المباشر (مع دعم هيدر Referer لتيك توك)
            if (params.containsKey("url")) {
                final String mediaUrl = params.get("url");
                final String referer = params.get("referer");

                if (mediaUrl != null && !mediaUrl.isEmpty()) {
                    mainHandler.post(() -> playMedia(mediaUrl, referer));
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
                }
            }

            // 2. استقبال المسارات المنفصلة (فيديو + صوت مدمج)
            if (params.containsKey("video")) {
                final String videoUrl = params.get("video");
                final String audioUrl = params.get("audio");

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    mainHandler.post(() -> playMergedMedia(videoUrl, audioUrl));
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
                }
            }

            // 3. أوامر التحكم بالريموت من تطبيق الهاتف
            if (params.containsKey("action")) {
                String action = params.get("action");
                mainHandler.post(() -> handleRemoteAction(action));
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
            }

            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid Request");
        }
    }

    private void playMedia(String url, String referer) {
        if (player == null) return;

        layoutIdle.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");

        // تمرير هيدر تيك توك لتفادي الشاشة السوداء
        Map<String, String> headers = new HashMap<>();
        if (referer != null && !referer.isEmpty()) {
            headers.put("Referer", referer);
        } else if (url.contains("tiktokcdn.com") || url.contains("pull-")) {
            headers.put("Referer", "https://www.tiktok.com/");
        }
        if (!headers.isEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(headers);
        }

        Uri uri = Uri.parse(url);
        MediaSource mediaSource;

        if (url.contains(".m3u8")) {
            mediaSource = new HlsMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri));
        } else {
            mediaSource = new ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri));
        }

        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void playMergedMedia(String videoUrl, String audioUrl) {
        if (player == null) return;

        layoutIdle.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36");

        MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)));

        if (audioUrl != null && !audioUrl.isEmpty()) {
            MediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(audioUrl)));
            MergingMediaSource mergedSource = new MergingMediaSource(videoSource, audioSource);
            player.setMediaSource(mergedSource);
        } else {
            player.setMediaSource(videoSource);
        }

        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void handleRemoteAction(String action) {
        if (player == null) return;

        switch (action) {
            case "play":
                player.play();
                break;
            case "pause":
                player.pause();
                break;
            case "stop":
                showIdleScreen();
                break;
            case "forward":
                player.seekTo(player.getCurrentPosition() + 10000);
                break;
            case "rewind":
                player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                break;
        }
    }

    private void showIdleScreen() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        playerView.setVisibility(View.GONE);
        layoutIdle.setVisibility(View.VISIBLE);
    }

    private String getDeviceIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();
                if (ip != 0) {
                    return Formatter.formatIpAddress(ip);
                }
            }

            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr != null && sAddr.indexOf(':') < 0) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (playerView.getVisibility() == View.VISIBLE) {
                showIdleScreen();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
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
