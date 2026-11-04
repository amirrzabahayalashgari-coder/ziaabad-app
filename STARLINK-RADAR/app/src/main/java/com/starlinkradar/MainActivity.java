package com.starlinkradar;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.net.Uri;
import android.net.wifi.*;
import android.os.Bundle;
import android.webkit.*;
import android.widget.Toast;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
    private WebView web;
    private WifiManager wifi;
    private LocationManager locationManager;
    private BroadcastReceiver receiver;
    private boolean receiverRegistered=false;
    private static final int REQ=41;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF080F1D);
        getWindow().setNavigationBarColor(0xFF080F1D);
        wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        web=new WebView(this);
        web.setBackgroundColor(0xFF080F1D);
        WebSettings ws=web.getSettings(); ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true); ws.setAllowContentAccess(false); ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"Radar");
        setContentView(web);
        receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            boolean fresh=i.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED,false);
            sendNetworks(fresh);
        }};
        web.loadUrl("file:///android_asset/index.html");
        if(hasLocationPermission()) registerScanReceiver();
    }
    private boolean hasLocationPermission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void registerScanReceiver(){if(!receiverRegistered){registerReceiver(receiver,new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));receiverRegistered=true;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private String jsonNetworks(){
        JSONArray arr=new JSONArray();
        try{
            List<ScanResult> list=wifi.getScanResults();
            Collections.sort(list,(a,b)->Integer.compare(b.level,a.level));
            for(ScanResult n:list){
                JSONObject o=new JSONObject();
                o.put("ssid",n.SSID==null||n.SSID.isEmpty()?"شبکه مخفی":n.SSID);
                o.put("bssid",n.BSSID==null?"":n.BSSID);
                o.put("rssi",n.level); o.put("frequency",n.frequency);
                o.put("band",n.frequency>=5925?"6 GHz":(n.frequency>=4900?"5 GHz":"2.4 GHz"));
                o.put("capabilities",n.capabilities==null?"":n.capabilities);
                o.put("starlinkHint",n.SSID!=null&&n.SSID.toLowerCase(Locale.ROOT).contains("starlink"));
                arr.put(o);
            }
        }catch(Exception ignored){}
        return arr.toString();
    }
    private void sendNetworks(boolean fresh){final String data=jsonNetworks();final boolean f=fresh;runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.onRadarNetworks("+data+","+f+")",null);});}
    private void scan(){
        if(!hasLocationPermission()){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ);return;}
        registerScanReceiver();
        if(!wifi.isWifiEnabled()){toast("ابتدا Wi‑Fi گوشی را روشن کنید.");return;}
        boolean ok=wifi.startScan();
        if(!ok){sendNetworks(false);toast("اندروید فعلاً اسکن تازه را محدود کرده؛ نتایج موجود نمایش داده شد.");}
        else toast("درخواست اسکن ارسال شد.");
    }
    private String locationJson(){
        JSONObject o=new JSONObject();
        try{
            if(!hasLocationPermission()){o.put("error","مجوز مکان داده نشده");return o.toString();}
            Location best=null;
            for(String p:locationManager.getProviders(true)){
                Location l=locationManager.getLastKnownLocation(p);
                if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;
            }
            if(best==null){o.put("error","موقعیت در دسترس نیست؛ GPS را روشن کنید.");}
            else{o.put("lat",best.getLatitude());o.put("lon",best.getLongitude());o.put("accuracy",best.getAccuracy());o.put("time",best.getTime());}
        }catch(Exception e){try{o.put("error","دریافت موقعیت ناموفق بود");}catch(Exception ignored){}}
        return o.toString();
    }
    public class Bridge{
        @JavascriptInterface public void scan(){runOnUiThread(()->MainActivity.this.scan());}
        @JavascriptInterface public String getNetworks(){return jsonNetworks();}
        @JavascriptInterface public String getLocation(){return locationJson();}
        @JavascriptInterface public void openMap(){runOnUiThread(()->{
            try{JSONObject p=new JSONObject(locationJson());if(p.has("lat")){String u="https://www.openstreetmap.org/?mlat="+p.getDouble("lat")+"&mlon="+p.getDouble("lon")+"#map=15/"+p.getDouble("lat")+"/"+p.getDouble("lon");startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));}else toast(p.optString("error","موقعیت موجود نیست"));}catch(Exception e){toast("بازکردن نقشه ممکن نشد");}
        });}
        @JavascriptInterface public void shareReport(String text){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"اشتراک گزارش اسکن"));});}
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED){registerScanReceiver();toast("مجوز داده شد؛ دوباره اسکن را بزنید.");}else if(r==REQ)toast("برای اسکن Wi‑Fi مجوز مکان لازم است.");}
    @Override protected void onDestroy(){if(receiverRegistered)try{unregisterReceiver(receiver);}catch(Exception ignored){}if(web!=null)web.destroy();super.onDestroy();}
}