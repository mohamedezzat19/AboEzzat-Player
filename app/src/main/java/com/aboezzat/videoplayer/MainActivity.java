package com.aboezzat.videoplayer;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ImageView imageView;
    private View welcomeLayout;
    private TextView tvIpAddress;
    private TextView tvServerStatus;
    private ExoPlayer player;
    private DefaultHttpDataSource.Factory httpDataSourceFactory;
    private static final int PORT = 8080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        imageView = findViewById(R.id.image_view);
        welcomeLayout = findViewById(R.id.welcome_layout);
        tvIpAddress = findViewById(R.id.tv_ip_address);
        tvServerStatus = findViewById(R.id.tv_server_status);

        httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .setAllowCrossProtocolRedirects(true);

        startReceiverServiceSafely();
        displayIpAddress();
        handleIntent(getIntent());
    }

    private void startReceiverServiceSafely() {
        try {
            Intent serviceIntent = new Intent(this, ReceiverService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    startForegroundService(serviceIntent);
                } catch (Exception e) {
                    startService(serviceIntent);
                }
            } else {
                startService(serviceIntent);
            }
        } catch (Exception ignored) {}
    }

    private void displayIpAddress() {
        new Thread(() -> {
            String ip = getLocalIpAddress();
            runOnUiThread(() -> {
                if (ip != null && !ip.isEmpty()) {
                    tvIpAddress.setText("عنوان الاتصال: http://" + ip + ":" + PORT);
                    tvServerStatus.setText("حالة السيرفر: جاهز للتشغيل ✓");
                } else {
                    tvIpAddress.setText("البورت: " + PORT);
                    tvServerStatus.setText("تأكد من الاتصال بالشبكة");
                }
            });
        }).start();
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        // تنفيذ أوامر الريموت
        if (intent.hasExtra("command")) {
            String cmd = intent.getStringExtra("command");
            executePlayerCommand(cmd);
            return;
        }

        // إضافة فيديو لقائمة التشغيل (Queue)
        if (intent.hasExtra("queue_url")) {
            String qUrl = intent.getStringExtra("queue_url");
            addToQueue(qUrl);
            return;
        }

        // عرض صورة مباشرة
        if (intent.hasExtra("image_url")) {
            String imageUrl = intent.getStringExtra("image_url");
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                displayImage(imageUrl.trim());
                return;
            }
        }

        // تشغيل فيديو مفرد أو دمج فيديو وصوت
        if (intent.hasExtra("video_url")) {
            String videoUrl = intent.getStringExtra("video_url");
            String audioUrl = intent.getStringExtra("audio_url");

            if (videoUrl != null && !videoUrl.trim().isEmpty()) {
                videoUrl = videoUrl.trim();
                if (isImageUrl(videoUrl)) {
                    displayImage(videoUrl);
                } else if (audioUrl != null && !audioUrl.trim().isEmpty()) {
                    playMergedMedia(videoUrl, audioUrl.trim());
                } else {
                    playSingleMedia(videoUrl);
                }
            }
        }
    }

    private void ensurePlayerInitialized() {
        if (player == null) {
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(httpDataSourceFactory);

            player = new ExoPlayer.Builder(MainActivity.this)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build();

            playerView.setPlayer(player);
        }
    }

    private void playMergedMedia(String videoUrl, String audioUrl) {
        runOnUiThread(() -> {
            hideImageView();
            ensurePlayerInitialized();

            MediaSource videoSource = new ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)));

            MediaSource audioSource = new ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(audioUrl)));

            MergingMediaSource mergedSource = new MergingMediaSource(videoSource, audioSource);

            player.setMediaSource(mergedSource);
            player.prepare();
            player.play();
        });
    }

    private void playSingleMedia(String url) {
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            try {
                Intent ytIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(ytIntent);
                return;
            } catch (Exception ignored) {}
        }

        runOnUiThread(() -> {
            hideImageView();
            ensurePlayerInitialized();

            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder().setUri(Uri.parse(url));
            if (url.contains(".m3u8")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8);
            }

            player.setMediaItem(mediaItemBuilder.build());
            player.prepare();
            player.play();
        });
    }

    private void addToQueue(String url) {
        runOnUiThread(() -> {
            ensurePlayerInitialized();
            MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
            if (url.contains(".m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8);
            }
            player.addMediaItem(builder.build());

            if (!player.isPlaying() && player.getPlaybackState() == Player.STATE_IDLE) {
                hideImageView();
                player.prepare();
                player.play();
            }
        });
    }

    private void executePlayerCommand(String cmd) {
        runOnUiThread(() -> {
            if (player == null) return;

            switch (cmd) {
                case "play_pause":
                    if (player.isPlaying()) player.pause();
                    else player.play();
                    break;
                case "play":
                    player.play();
                    break;
                case "pause":
                    player.pause();
                    break;
                case "ff":
                case "forward":
                    player.seekTo(player.getCurrentPosition() + 10000);
                    break;
                case "rw":
                case "rewind":
                    long backPos = Math.max(player.getCurrentPosition() - 10000, 0);
                    player.seekTo(backPos);
                    break;
                case "next":
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem();
                    }
                    break;
                case "prev":
                case "previous":
                    if (player.hasPreviousMediaItem()) {
                        player.seekToPreviousMediaItem();
                    }
                    break;
                case "loop_toggle":
                    int mode = player.getRepeatMode();
                    if (mode == Player.REPEAT_MODE_OFF) {
                        player.setRepeatMode(Player.REPEAT_MODE_ONE);
                    } else {
                        player.setRepeatMode(Player.REPEAT_MODE_OFF);
                    }
                    break;
            }
        });
    }

    private void hideImageView() {
        if (imageView != null) {
            imageView.setVisibility(View.GONE);
            Glide.with(this).clear(imageView);
        }
        if (welcomeLayout != null) welcomeLayout.setVisibility(View.GONE);
        if (playerView != null) playerView.setVisibility(View.VISIBLE);
    }

    private boolean isImageUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    public void displayImage(String imageUrl) {
        runOnUiThread(() -> {
            if (player != null) player.stop();
            if (playerView != null) playerView.setVisibility(View.GONE);
            if (welcomeLayout != null) welcomeLayout.setVisibility(View.GONE);

            if (imageView != null) {
                imageView.setVisibility(View.VISIBLE);
                Glide.with(MainActivity.this)
                        .load(imageUrl)
                        .fitCenter()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                return false;
                            }
                        })
                        .into(imageView);
            }
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
