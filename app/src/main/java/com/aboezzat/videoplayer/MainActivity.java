package com.aboezzat.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private LinearLayout layoutIdle;
    private TextView tvStatus, tvIp;
    private ImageView ivImageDisplay;
    private WebServer server;
    private SharedPreferences prefs;
    private AudioManager audioManager;

    // المنفذ الذهبي الموحد
    private static final int HTTP_PORT = 8080;
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
        ivImageDisplay = findViewById(R.id.iv_memorial);

        initPlayer();
        startServer();
        displayIpAddress();
        startUdpBeacon();
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        // تعديل زاوية الدوران التلقائي لمنع اعوجاج الفيديو
        player.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                runOnUiThread(() -> {
                    View videoSurface = playerView.getVideoSurfaceView();
                    if (videoSurface != null) {
                        int rotation = videoSize.unappliedRotationDegrees;
                        // لو الفيديو عمودي ومشقلب نعدل دورانه ومكانه فوراً
                        if (rotation == 90 || rotation == 270) {
                            videoSurface.setRotation(rotation);
                        } else if (videoSize.height > videoSize.width && videoSurface.getRotation() != 0) {
                            videoSurface.setRotation(0);
                        } else {
                            videoSurface.setRotation(0);
                        }
                    }
                });
            }

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

    private void playSingleMedia(String url) {
        runOnUiThread(() -> {
            currentPlayingUrl = url;
            layoutIdle.setVisibility(View.GONE);

            // لو الرابط صورة: عرضها فوراً على ImageView وإيقاف مشغل الفيديو
            if (url.contains("media_image") || url.contains(".jpg") || url.contains(".png") || url.contains(".jpeg")) {
                if (player != null) player.stop();
                playerView.setVisibility(View.GONE);
                if (ivImageDisplay != null) {
                    ivImageDisplay.setVisibility(View.VISIBLE);
                    loadImageAsync(url);
                }
                return;
            }

            if (ivImageDisplay != null) ivImageDisplay.setVisibility(View.GONE);
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

    private void playMergedMedia(String videoUrl, String audioUrl) {
        runOnUiThread(() -> {
            currentPlayingUrl = videoUrl;
            layoutIdle.setVisibility(View.GONE);
            if (ivImageDisplay != null) ivImageDisplay.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);

            DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
            MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl));
            MediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl));

            MergingMediaSource mergedSource = new MergingMediaSource(videoSource, audioSource);
            player.setMediaSource(mergedSource);
            player.prepare();
            player.play();
            Toast.makeText(MainActivity.this, "تم دمج مسار الصوت والفيديو بنجاح ✓", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadImageAsync(String imageUrl) {
        AsyncTask.execute(() -> {
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setDoInput(true);
                conn.connect();
                InputStream input = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                runOnUiThread(() -> {
                    if (ivImageDisplay != null && bitmap != null) {
                        ivImageDisplay.setImageBitmap(bitmap);
                        ivImageDisplay.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    private void showIdleView() {
        runOnUiThread(() -> {
            if (player != null) {
                player.stop();
            }
            currentPlayingUrl = "";
            playerView.setVisibility(View.GONE);
            if (ivImageDisplay != null) ivImageDisplay.setVisibility(View.GONE);
            layoutIdle.setVisibility(View.VISIBLE);
        });
    }

    private void startServer() {
        server = new WebServer(HTTP_PORT);
        try {
            server.start();
            tvStatus.setText("حالة السيرفر: جاهز للتشغيل ✓ (منفذ 8080)");
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
            Map<String, String> parms = session.getParms();
            String uri = session.getUri();

            if ("/play".equals(uri) || "/".equals(uri)) {
                String videoUrl = parms.get("video");
                String audioUrl = parms.get("audio");

                if (videoUrl != null && audioUrl != null) {
                    playMergedMedia(videoUrl, audioUrl);
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "Merged Playing");
                }

                String singleUrl = parms.get("url");
                if (singleUrl != null && !singleUrl.isEmpty()) {
                    playSingleMedia(singleUrl);
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
