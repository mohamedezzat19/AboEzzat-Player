package com.aboezzat.videoplayer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private View welcomeLayout;
    private TextView tvIpAddress;
    private TextView tvServerStatus;
    private ExoPlayer player;
    private static final int PORT = 8080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        welcomeLayout = findViewById(R.id.welcome_layout);
        tvIpAddress = findViewById(R.id.tv_ip_address);
        tvServerStatus = findViewById(R.id.tv_server_status);

        // تشغيل خدمة الاستقبال الخلفية الدائمة
        Intent serviceIntent = new Intent(this, ReceiverService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        displayIpAddress();
        handleIntent(getIntent());
    }

    private void displayIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wm.getConnectionInfo().getIpAddress();
            if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                ipAddress = Integer.reverseBytes(ipAddress);
            }
            byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
            String ip = InetAddress.getByAddress(ipByteArray).getHostAddress();
            tvIpAddress.setText("عنوان الاتصال: http://" + ip + ":" + PORT);
            tvServerStatus.setText("حالة السيرفر: متصل في الخلفية ✓");
        } catch (Exception e) {
            tvIpAddress.setText("البورت: " + PORT);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("video_url")) {
            String url = intent.getStringExtra("video_url");
            if (url != null && !url.trim().isEmpty()) {
                playMedia(url.trim());
            }
        }
    }

    private void playMedia(String url) {
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            try {
                Intent ytIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(ytIntent);
                return;
            } catch (Exception ignored) {
            }
        }

        runOnUiThread(() -> {
            if (welcomeLayout != null) welcomeLayout.setVisibility(View.GONE);
            if (playerView != null) playerView.setVisibility(View.VISIBLE);

            if (player == null) {
                player = new ExoPlayer.Builder(MainActivity.this).build();
                playerView.setPlayer(player);
            }

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
