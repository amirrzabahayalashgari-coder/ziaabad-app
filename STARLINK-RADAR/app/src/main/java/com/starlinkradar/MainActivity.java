package com.starlinkradar;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.wifi.*;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import java.util.*;

public class MainActivity extends Activity {
    private WifiManager wifi;
    private TextView status, summary;
    private LinearLayout results;
    private BroadcastReceiver receiver;
    private boolean receiverRegistered = false;
    private static final int REQ_LOCATION = 41;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7, 15, 29));
        getWindow().setNavigationBarColor(Color.rgb(7, 15, 29));
        wifi = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        buildUi();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                boolean updated = i.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                showNetworks(wifi.getScanResults(), updated);
            }
        };
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            receiverRegistered = true;
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }
        showNetworks(wifi.getScanResults(), false);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(7,15,29));
        LinearLayout root = new LinearLayout(this);
        root.setPadding(22,28,22,28); root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        TextView brand = new TextView(this); brand.setText("◉  STARLINK RADAR"); brand.setTextSize(24); brand.setTextColor(Color.WHITE); brand.setTypeface(null,Typeface.BOLD);
        root.addView(brand);
        TextView subtitle = new TextView(this); subtitle.setText("پایشگر شبکه‌های بی‌سیم اطراف"); subtitle.setTextSize(14); subtitle.setTextColor(Color.rgb(142,164,190)); subtitle.setPadding(0,8,0,22); root.addView(subtitle);
        summary = new TextView(this); summary.setText("آماده اسکن"); summary.setTextColor(Color.rgb(90,220,190)); summary.setTextSize(15); summary.setPadding(16,18,16,18); summary.setBackgroundColor(Color.rgb(17,31,51)); root.addView(summary);
        Button scan = new Button(this); scan.setText("اسکن شبکه‌های Wi‑Fi"); scan.setOnClickListener(v -> startScan()); root.addView(scan);
        status = new TextView(this); status.setText("برای شروع، دکمه اسکن را بزنید."); status.setTextColor(Color.LTGRAY); status.setPadding(0,14,0,12); root.addView(status);
        TextView disclaimer = new TextView(this); disclaimer.setText("نکته علمی: گوشی شبکه‌های Wi‑Fi محلی را می‌بیند، نه سیگنال مستقیم ماهواره‌های Starlink. تشخیص بر اساس نام شبکه فقط یک احتمال است و محل دیش را نشان نمی‌دهد."); disclaimer.setTextColor(Color.rgb(255,196,105)); disclaimer.setTextSize(12); disclaimer.setPadding(14,14,14,14); disclaimer.setBackgroundColor(Color.rgb(48,37,25)); root.addView(disclaimer);
        TextView heading = new TextView(this); heading.setText("شبکه‌های شناسایی‌شده"); heading.setTextColor(Color.WHITE); heading.setTextSize(18); heading.setTypeface(null,Typeface.BOLD); heading.setPadding(0,24,0,10); root.addView(heading);
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); root.addView(results);
        setContentView(scroll);
    }

    private void startScan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION); return;
        }
        if (!wifi.isWifiEnabled()) { status.setText("Wi‑Fi خاموش است؛ ابتدا Wi‑Fi گوشی را روشن کنید."); return; }
        status.setText("در حال درخواست اسکن…");
        boolean accepted = wifi.startScan();
        status.setText(accepted ? "درخواست ثبت شد؛ منتظر نتیجه اسکن…" : "اسکن انجام نشد یا محدودیت زمانی اندروید فعال است. کمی بعد دوباره تلاش کنید.");
    }

    private void showNetworks(List<ScanResult> networks, boolean updated) {
        if (results == null) return;
        results.removeAllViews();
        if (networks == null || networks.isEmpty()) {
            summary.setText("شبکه‌ای در دسترس نیست");
            status.setText(updated ? "نتیجه‌ای دریافت نشد." : "برای دریافت نتایج، اسکن را اجرا کنید.");
            return;
        }
        Collections.sort(networks, (a,b) -> Integer.compare(b.level,a.level));
        int probable = 0;
        for (ScanResult n : networks) {
            String ssid = (n.SSID == null || n.SSID.isEmpty()) ? "(شبکه مخفی)" : n.SSID;
            boolean hint = ssid.toLowerCase(Locale.ROOT).contains("starlink");
            if (hint) probable++;
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(16,14,16,14); card.setBackgroundColor(Color.rgb(17,31,51));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,10); results.addView(card,lp);
            TextView name = new TextView(this); name.setText(ssid); name.setTextColor(Color.WHITE); name.setTextSize(16); name.setTypeface(null,Typeface.BOLD); card.addView(name);
            TextView detail = new TextView(this); detail.setText("قدرت سیگنال: "+n.level+" dBm   •   فرکانس: "+n.frequency+" MHz"); detail.setTextColor(Color.rgb(160,181,205)); detail.setTextSize(12); detail.setPadding(0,7,0,0); card.addView(detail);
            TextView tag = new TextView(this); tag.setText(hint ? "قرینه نام شبکه: احتمال ارتباط با Starlink (تأییدنشده)" : "شبکه Wi‑Fi محلی"); tag.setTextColor(hint ? Color.rgb(255,196,105) : Color.rgb(90,220,190)); tag.setTextSize(12); tag.setPadding(0,7,0,0); card.addView(tag);
        }
        summary.setText(networks.size()+" شبکه پیدا شد  •  "+probable+" مورد با نام مشکوک به Starlink");
        status.setText(updated ? "نتایج تازه اسکن دریافت شد." : "نمایش آخرین نتایج ذخیره‌شده اندروید.");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (!receiverRegistered) { registerReceiver(receiver,new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)); receiverRegistered=true; }
            status.setText("مجوز داده شد؛ اکنون اسکن را اجرا کنید.");
        } else if (requestCode == REQ_LOCATION) status.setText("برای اسکن Wi‑Fi باید مجوز مکان را بدهید.");
    }

    @Override protected void onDestroy() {
        if (receiverRegistered) { try { unregisterReceiver(receiver); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
