package com.aboezzat.videoplayer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        playerView = findViewById(R.id.player_view);

        initializePlayer();
        handleIntent(getIntent());
    }

    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
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
        // لو الرابط يوتيوب، افتحه فوراً بتطبيق خارجي (زي SmartTube أو المتصفح) مباشرة بأبسط أمر أندرويد قياسي
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            try {
                Intent youtubeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                youtubeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(youtubeIntent);
                finish();
                return;
            } catch (Exception ignored) {
            }
        }

        // تشغيل مباشر لأي رابط فيديو آخر داخل مشغلنا
        if (player == null) {
            initializePlayer();
        }
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.pause();
        }
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
