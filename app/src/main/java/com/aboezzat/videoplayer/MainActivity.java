package com.aboezzat.videoplayer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private View welcomeLayout;
    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        welcomeLayout = findViewById(R.id.welcome_layout);

        // تشغيل خدمة السيرفر
        startService(new Intent(this, ReceiverService.class));

        handleIntent(getIntent());
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

        // إخفاء شاشة الترحيب وإظهار مشغل الفيديو بالكامل
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
