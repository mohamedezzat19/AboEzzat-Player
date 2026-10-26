package com.aboezzat.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private LinearLayout layoutIdle;
    private TextView tvStatus, tvIp;
    private ImageView ivMemorial;
    private WebServer server;
    private SharedPreferences prefs;
    private AudioManager audioManager;

    private static final int HTTP_PORT = 8081;
    private static final int UDP_PORT = 8888;
    private boolean isBroadcasting = true;
    private Thread beaconThread;
    private String currentPlayingUrl = "";

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying() && !currentPlayingUrl.isEmpty()) {
                long pos = player.getCurrentPosition();
                prefs.edit().putLong("pos_" + currentPlayingUrl.hashCode(), pos).apply();
            }
            progressHandler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("AboEzzatPlayerPrefs", MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        playerView = findViewById(R.id.player_view);
        layoutIdle = findViewById(R.id.layout_idle);
        tvStatus = findViewById(R.id.tv_status);
        tvIp = findViewById(R.id.tv_ip);
        ivMemorial = findViewById(R.id.iv_memorial);

        initPlayer();
        startServer();
        displayIpAddress();
        startUdpBeacon();
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    if (!currentPlayingUrl.isEmpty()) {
                        prefs.edit().remove("pos_" + currentPlayingUrl.hashCode()).apply();
                    }
                    showIdleView();
                }
            }
        });

        progressHandler.post(progressRunnable);
    }

    private void playMedia(String url) {
        runOnUiThread(() -> {
            currentPlayingUrl = url;
            layoutIdle.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);

            long savedPos = prefs.getLong("pos_" + url.hashCode(), 0);

            MediaItem mediaItem = MediaItem.fromUri(url);
            player.setMediaItem(mediaItem);
            player.prepare();

            if (savedPos > 5000) {
                player.seekTo(savedPos);
                Toast.makeText(MainActivity.this, "تم استئناف التشغيل تلقائياً ✓", Toast.LENGTH_SHORT).show();
            }

            player.play();
        });
    }

    private void showIdleView() {
        runOnUiThread(() -> {
            if (player != null) {
                player.stop();
            }
            currentPlayingUrl = "";
            playerView.setVisibility(View.GONE);
            layoutIdle.setVisibility(View.VISIBLE);
        });
    }

    private void startServer() {
        server = new WebServer(HTTP_PORT);
        try {
            server.start();
            tvStatus.setText("حالة السيرفر: جاهز للتشغيل ✓");
        } catch (IOException e) {
            tvStatus.setText("خطأ في بدء السيرفر: " + e.getMessage());
        }
    }

    private String getDeviceIp() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private void displayIpAddress() {
        String ip = getDeviceIp();
        tvIp.setText("عنوان الاتصال: http://" + ip + ":" + HTTP_PORT);
    }

    private void startUdpBeacon() {
        beaconThread = new Thread(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);

                String deviceModel = Build.MODEL != null ? Build.MODEL : "AndroidTV";
                while (isBroadcasting) {
                    String ip = getDeviceIp();
                    String beaconData = "ABOEZZAT_BEACON|" + deviceModel + "|" + ip + "|" + HTTP_PORT;
                    byte[] buffer = beaconData.getBytes();

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                            InetAddress.getByName("255.255.255.255"), UDP_PORT);
                    socket.send(packet);

                    Thread.sleep(2000);
                }
            } catch (Exception ignored) {
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        });
        beaconThread.start();
    }

    private class WebServer extends NanoHTTPD {
        public WebServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Method method = session.getMethod();
            String uri = session.getUri();
            Map<String, String> parms = session.getParms();

            if ("/play".equals(uri) || "/".equals(uri)) {
                String videoUrl = parms.get("url");
                if (videoUrl == null && Method.POST.equals(method)) {
                    Map<String, String> files = new HashMap<>();
                    try {
                        session.parseBody(files);
                        parms = session.getParms();
                        videoUrl = parms.get("url");
                    } catch (Exception ignored) {}
                }

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    playMedia(videoUrl);
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "Playing");
                }

                String cmd = parms.get("command");
                if (cmd != null) {
                    handleRemoteCommand(cmd);
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "Command: " + cmd);
                }
            } else if ("/stop".equals(uri)) {
                showIdleView();
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "Stopped");
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }
    }

    private void handleRemoteCommand(String cmd) {
        runOnUiThread(() -> {
            if (player == null) return;
            switch (cmd) {
                case "play_pause":
                    if (player.isPlaying()) player.pause();
                    else player.play();
                    break;
                case "ff":
                    player.seekTo(player.getCurrentPosition() + 10000);
                    break;
                case "rw":
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                    break;
                case "vol_up":
                    if (audioManager != null) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    }
                    break;
                case "vol_down":
                    if (audioManager != null) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    }
                    break;
                case "mute":
                    if (audioManager != null) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                    }
                    break;
                case "stop":
                    showIdleView();
                    break;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isBroadcasting = false;
        progressHandler.removeCallbacks(progressRunnable);

        if (server != null) {
            server.stop();
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
