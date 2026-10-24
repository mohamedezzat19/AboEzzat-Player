package com.aboezzat.videoplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    private PlayerView playerView;
    private ExoPlayer player;
    private LinearLayout welcomeLayout;
    private TextView ipText;

    private final BroadcastReceiver videoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.aboezzat.videoplayer.PLAY_VIDEO".equals(intent.getAction())) {
                String videoUrl = intent.getStringExtra("video_url");
                if (videoUrl != null) {
                    playVideo(videoUrl);
                }
            } else if ("com.aboezzat.videoplayer.REMOTE_ACTION".equals(intent.getAction())) {
                handleRemoteAction(intent.getStringExtra("action"));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.playerView);
        welcomeLayout = findViewById(R.id.welcomeLayout);
        ipText = findViewById(R.id.ipText);

        initializePlayer();
        displayServerInfo();

        Intent serviceIntent = new Intent(this, HttpServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.aboezzat.videoplayer.PLAY_VIDEO");
        filter.addAction("com.aboezzat.videoplayer.REMOTE_ACTION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(videoReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(videoReceiver, filter);
        }
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
    }

    private void displayServerInfo() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            String ipAddress = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            ipText.setText("عنوان الاتصال: http://" + ipAddress + ":1997");
        } catch (Exception e) {
            ipText.setText("عنوان الاتصال: المنفذ 1997");
        }
    }

    private void playVideo(String videoUrl) {
        if (player == null) return;

        runOnUiThread(() -> {
            welcomeLayout.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);

            DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000);

            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(httpDataSourceFactory);

            MediaItem mediaItem = MediaItem.fromUri(videoUrl);
            player.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem));
            player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            player.prepare();
            player.play();
        });
    }

    private void handleRemoteAction(String action) {
        if (player == null || action == null) return;
        runOnUiThread(() -> {
            switch (action) {
                case "play_pause":
                    if (player.isPlaying()) player.pause();
                    else player.play();
                    break;
                case "forward":
                    player.seekTo(player.getCurrentPosition() + 10000);
                    break;
                case "rewind":
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                    break;
                case "stop":
                    player.stop();
                    playerView.setVisibility(View.GONE);
                    welcomeLayout.setVisibility(View.VISIBLE);
                    break;
            }
        });
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(videoReceiver);
        } catch (Exception ignored) {}
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
