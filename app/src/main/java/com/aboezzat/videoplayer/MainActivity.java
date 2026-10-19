package com.aboezzat.videoplayer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;
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

        // إبقاء الشاشة مضاءة دائماً أثناء المشاهدة
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
                routeAndPlay(url.trim());
            }
        }
    }

    private void routeAndPlay(String url) {
        // فحص إذا كان الرابط يوتيوب
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            if (tryLaunchSmartTube(url)) {
                finish(); // إغلاق مشغلنا وترك الشاشة لسمارت تيوب
                return;
            }
        }

        // تشغيل مباشر لأي رابط فيديو آخر داخل مشغل أبو عزت
        playMedia(url);
    }

    private boolean tryLaunchSmartTube(String url) {
        // حزم سمارت تيوب الرسمية على الفاير ستيك والأندرويد تي في
        String[] smartTubePackages = {
            "com.teamsmart.videomanager.tv",
            "com.liskovsoft.videomanager",
            "com.liskovsoft.smarttubetv.beta"
        };

        PackageManager pm = getPackageManager();
        for (String pkg : smartTubePackages) {
            try {
                pm.getPackageInfo(pkg, 0);
                Intent launchIntent = new Intent(Intent.ACTION_VIEW);
                launchIntent.setData(Uri.parse(url));
                launchIntent.setPackage(pkg);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private void playMedia(String url) {
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
