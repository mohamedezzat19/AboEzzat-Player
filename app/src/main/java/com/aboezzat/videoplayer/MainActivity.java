package com.aboezzat.videoplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    private TextView tvStatus;
    private Button btnRestart;
    private LinearLayout layoutInfo;
    private PlayerView playerView;
    private ExoPlayer player;

    private final BroadcastReceiver videoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.aboezzat.videoplayer.PLAY_VIDEO".equals(intent.getAction())) {
                String url = intent.getStringExtra("video_url");
                if (url != null && !url.isEmpty()) {
                    playVideo(url);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnRestart = findViewById(R.id.btn_restart);
        layoutInfo = findViewById(R.id.layout_info);
        playerView = findViewById(R.id.main_player_view);

        updateIpAddress();

        btnRestart.setOnClickListener(v -> {
            stopServerService();
            startServerService();
            updateIpAddress();
        });

        startServerService();

        IntentFilter filter = new IntentFilter("com.aboezzat.videoplayer.PLAY_VIDEO");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(videoReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(videoReceiver, filter);
        }
    }

    private void updateIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            tvStatus.setText("عنوان الآي بي: " + ip + " | المنفذ: 8080");
        } catch (Exception e) {
            tvStatus.setText("المنفذ: 8080");
        }
    }

    private void playVideo(String url) {
        runOnUiThread(() -> {
            try {
                layoutInfo.setVisibility(View.GONE);
                playerView.setVisibility(View.VISIBLE);

                if (player != null) {
                    player.release();
                }

                player = new ExoPlayer.Builder(MainActivity.this).build();
                playerView.setPlayer(player);

                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
                player.setMediaItem(mediaItem);
                player.prepare();
                player.play();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (playerView.getVisibility() == View.VISIBLE) {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
            playerView.setVisibility(View.GONE);
            layoutInfo.setVisibility(View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }

    private void startServerService() {
        Intent intent = new Intent(this, HttpServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopServerService() {
        Intent intent = new Intent(this, HttpServerService.class);
        stopService(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(videoReceiver);
        } catch (Exception ignored) {}
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
