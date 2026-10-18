package com.aboezzat.videoplayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private TextView tvIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        rootLayout.setPadding(40, 30, 40, 30);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0F2027, 0xFF203A43, 0xFF2C5364}
        );
        rootLayout.setBackground(bg);

        // 1. أعلى الشاشة في المنتصف
        TextView tvWelcome = new TextView(this);
        tvWelcome.setText("أهلاً بك يا أبو عزت");
        tvWelcome.setTextSize(36);
        tvWelcome.setTextColor(0xFF00E676);
        tvWelcome.setTypeface(null, Typeface.BOLD);
        tvWelcome.setGravity(Gravity.CENTER);
        tvWelcome.setPadding(0, 10, 0, 20);
        rootLayout.addView(tvWelcome);

        // بطاقة الآي بي والمنفذ
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(30, 20, 30, 20);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0x33FFFFFF);
        cardBg.setCornerRadius(18);
        card.setBackground(cardBg);

        tvIp = new TextView(this);
        tvIp.setText("عنوان الآي بي: " + getLocalIpAddress() + "   |   المنفذ: 8080");
        tvIp.setTextSize(22);
        tvIp.setTextColor(Color.WHITE);
        tvIp.setGravity(Gravity.CENTER);
        card.addView(tvIp);

        TextView tvStatus = new TextView(this);
        tvStatus.setText("المشغل المدمج جاهز لتشغيل الفيديوهات مباشرة بملء الشاشة");
        tvStatus.setTextSize(18);
        tvStatus.setTextColor(0xFFB0BEC5);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 10, 0, 0);
        card.addView(tvStatus);

        rootLayout.addView(card);

        // زر إعادة تشغيل الخادم
        Button btnRestart = new Button(this);
        btnRestart.setText("إعادة تشغيل الخادم");
        btnRestart.setTextSize(18);
        btnRestart.setTextColor(Color.WHITE);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF1976D2);
        btnBg.setCornerRadius(12);
        btnRestart.setBackground(btnBg);
        btnRestart.setPadding(35, 12, 35, 12);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, 20, 0, 0);
        btnRestart.setLayoutParams(btnParams);
        btnRestart.setFocusable(true);

        btnRestart.setOnClickListener(v -> {
            restartBackgroundService();
            tvIp.setText("عنوان الآي بي: " + getLocalIpAddress() + "   |   المنفذ: 8080");
            Toast.makeText(MainActivity.this, "تمت إعادة تشغيل الخادم بنجاح", Toast.LENGTH_SHORT).show();
        });
        rootLayout.addView(btnRestart);

        // مسافة لدفع الذكر لأسفل الشاشة
        View spacer = new View(this);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        spacer.setLayoutParams(spacerParams);
        rootLayout.addView(spacer);

        // 2. أسفل الشاشة
        TextView tvZikr = new TextView(this);
        tvZikr.setText("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ، سُبْحَانَ اللَّهِ الْعَظِيمِ");
        tvZikr.setTextSize(38);
        tvZikr.setTextColor(0xFFFFD54F);
        tvZikr.setTypeface(null, Typeface.BOLD);
        tvZikr.setGravity(Gravity.CENTER);
        tvZikr.setPadding(0, 10, 0, 20);

        LinearLayout.LayoutParams zikrParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tvZikr.setLayoutParams(zikrParams);
        rootLayout.addView(tvZikr);

        setContentView(rootLayout);

        startBackgroundService();
    }

    private void startBackgroundService() {
        try {
            Intent serviceIntent = new Intent(this, HttpServerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception ignored) {}
    }

    private void restartBackgroundService() {
        try {
            Intent serviceIntent = new Intent(this, HttpServerService.class);
            stopService(serviceIntent);
            startBackgroundService();
        } catch (Exception ignored) {}
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr != null && sAddr.indexOf(':') < 0) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "غير متصل بالواي فاي";
    }
}
