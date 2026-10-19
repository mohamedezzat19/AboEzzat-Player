package com.aboezzat.videoplayer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private static boolean isExtractorInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // إبقاء الشاشة مضاءة دائماً أثناء التشغيل
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        playerView = findViewById(R.id.player_view);

        // تهيئة محرك الاستخراج مرة واحدة فقط
        if (!isExtractorInitialized) {
            NewPipe.init(org.schabi.newpipe.extractor.downloader.DownloaderTestImpl.getInstance());
            isExtractorInitialized = true;
        }

        initializePlayer();
        handleIntent(getIntent());
    }

    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
            playerView.setShowNextButton(false);
            playerView.setShowPreviousButton(false);
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
            String rawUrl = intent.getStringExtra("video_url");
            if (rawUrl != null && !rawUrl.isEmpty()) {
                resolveAndPlay(rawUrl);
            }
        }
    }

    private void resolveAndPlay(String url) {
        // إذا كان الرابط مباشر أصلاً (مثل m3u8 أو mp4) نشغله فوراً بدون استخراج
        if (isDirectStream(url)) {
            playMedia(url);
            return;
        }

        // لو الرابط من صفحات يوتيوب ومواقع الفيديو، نشغل الاستخراج في الخلفية
        Toast.makeText(this, "جاري استخراج رابط الفيديو...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String streamUrl = null;

                if (url.contains("youtube.com") || url.contains("youtu.be")) {
                    YoutubeStreamExtractor extractor = (YoutubeStreamExtractor) ServiceList.YouTube.getStreamExtractor(url);
                    extractor.fetchPage();
                    
                    // استخراج الفيديو المباشر بأعلى جودة متوفرة بصوت مدمج
                    List<VideoStream> videoStreams = extractor.getVideoStreams();
                    if (!videoStreams.isEmpty()) {
                        streamUrl = videoStreams.get(0).getContent();
                    } else if (extractor.getHlsUrl() != null) {
                        streamUrl = extractor.getHlsUrl();
                    }
                }

                String finalUrl = streamUrl != null ? streamUrl : url;

                new Handler(Looper.getMainLooper()).post(() -> playMedia(finalUrl));

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "تعذر استخراج الرابط، جاري تجربة المشغل المباشر", Toast.LENGTH_SHORT).show();
                    playMedia(url);
                });
            }
        }).start();
    }

    private boolean isDirectStream(String url) {
        String clean = url.toLowerCase();
        return clean.contains(".m3u8") || clean.contains(".mp4") || clean.contains(".mkv") || clean.contains(".ts");
    }

    private void playMedia(String finalUrl) {
        if (player == null) {
            initializePlayer();
        }
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(finalUrl));
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
