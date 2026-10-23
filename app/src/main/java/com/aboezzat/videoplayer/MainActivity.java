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
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ImageView imageView;
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
        imageView = findViewById(R.id.image_view);
        welcomeLayout = findViewById(R.id.welcome_layout);
        tvIpAddress = findViewById(R.id.tv_ip_address);
        tvServerStatus = findViewById(R.id.tv_server_status);

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
        if (intent == null) return;

        // استقبال رابط صورة صريح
        if (intent.hasExtra("image_url")) {
            String imageUrl = intent.getStringExtra("image_url");
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                displayImage(imageUrl.trim());
                return;
            }
        }

        // استقبال رابط ميديا عام (فيديو أو صورة)
        if (intent.hasExtra("video_url")) {
            String url = intent.getStringExtra("video_url");
            if (url != null && !url.trim().isEmpty()) {
                url = url.trim();
                // فحص إذا كان الرابط صورة بالامتداد
                if (isImageUrl(url)) {
                    displayImage(url);
                } else {
                    playMedia(url);
                }
            }
        }
    }

    private boolean isImageUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
               lower.endsWith(".png") || lower.endsWith(".webp") ||
               lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    public void displayImage(String imageUrl) {
        runOnUiThread(() -> {
            // 1. إيقاف وإخفاء مشغل الفيديو إن وُجد
            if (player != null) {
                player.stop();
            }
            if (playerView != null) playerView.setVisibility(View.GONE);
            if (welcomeLayout != null) welcomeLayout.setVisibility(View.GONE);

            // 2. إظهار عارض الصور
            if (imageView != null) {
                imageView.setVisibility(View.VISIBLE);

                // 3. تحميل وعرض الصورة عبر Glide مع احترام زاوية الدوران والأبعاد
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
            // إخفاء عارض الصور وإفراغ الذاكرة
            if (imageView != null) {
                imageView.setVisibility(View.GONE);
                Glide.with(MainActivity.this).clear(imageView);
            }

            if (welcomeLayout != null) welcomeLayout.setVisibility(View.GONE);
            if (playerView != null) playerView.setVisibility(View.VISIBLE);

            if (player == null) {
                // ترويسة متصفح حقيقية تمنع خطأ 403 وتدعم HLS للبث المباشر
                DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .setAllowCrossProtocolRedirects(true);

                DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(httpDataSourceFactory);

                player = new ExoPlayer.Builder(MainActivity.this)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .build();

                playerView.setPlayer(player);
            }

            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder().setUri(Uri.parse(url));

            // تحديد تلقائي لنوع البث إذا كان m3u8
            if (url.contains(".m3u8")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8);
            }

            player.setMediaItem(mediaItemBuilder.build());
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
