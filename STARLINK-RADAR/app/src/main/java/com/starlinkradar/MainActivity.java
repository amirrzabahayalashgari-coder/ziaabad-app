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
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private WebView web; private WifiManager wifi; private LocationManager locationManager;
    private BroadcastReceiver receiver; private boolean receiverRegistered=false;
    private static final int REQ=41;
    private final LocationListener locationListener = new LocationListener(){
        @Override public void onLocationChanged(Location l){sendLocation(l);}
        @Override public void onProviderEnabled(String p){}
        @Override public void onProviderDisabled(String p){}
        @Deprecated @Override public void onStatusChanged(String p,int s,android.os.Bundle b){}
    };
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(0xFF050B18);getWindow().setNavigationBarColor(0xFF050B18);
        wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        web=new WebView(this);web.setBackgroundColor(0xFF050B18);
        WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setAllowFileAccess(true);ws.setAllowContentAccess(false);ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"Radar");setContentView(web);
        receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){sendNetworks(i.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED,false));}};
        web.loadUrl("file:///android_asset/index.html");if(hasLocationPermission())registerScanReceiver();
    }
    private boolean hasLocationPermission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void registerScanReceiver(){if(!receiverRegistered){registerReceiver(receiver,new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));receiverRegistered=true;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private String jsonNetworks(){
        JSONArray arr=new JSONArray();try{List<ScanResult> list=wifi.getScanResults();Collections.sort(list,(a,b)->Integer.compare(b.level,a.level));
            for(ScanResult n:list){JSONObject o=new JSONObject();o.put("ssid",n.SSID==null||n.SSID.isEmpty()?"شبکه مخفی":n.SSID);o.put("bssid",n.BSSID==null?"":n.BSSID);o.put("rssi",n.level);o.put("frequency",n.frequency);
                String band=n.frequency>=5925?"6 GHz":(n.frequency>=4900?"5 GHz":"2.4 GHz");o.put("band",band);o.put("capabilities",n.capabilities==null?"":n.capabilities);o.put("starlinkHint",n.SSID!=null&&n.SSID.toLowerCase(Locale.ROOT).contains("starlink"));JSONObject clue=classifyStarlinkName(n.SSID);o.put("starlinkScore",clue.optInt("score"));o.put("starlinkLevel",clue.optString("level"));o.put("starlinkReason",clue.optString("reason"));arr.put(o);}
        }catch(Exception ignored){}return arr.toString();
    }
    private JSONObject classifyStarlinkName(String ssid){
        JSONObject out=new JSONObject();String raw=ssid==null?"":ssid.toLowerCase(Locale.ROOT);
        String compact=raw.replaceAll("[^a-z0-9]","");
        int score=0;String level="بدون نشانه";String reason="نام شبکه نشانه متنی مشخصی از Starlink ندارد.";
        if(compact.contains("starlink")){score=95;level="سرنخ متنی قوی";reason="نام شبکه شامل واژه Starlink است؛ این فقط قرینه نام‌گذاری است.";}
        else if(compact.matches(".*(starl1nk|starlik|starlnk|starlinkwifi|starlikwifi).*")){score=80;level="شباهت نوشتاری";reason="نام شبکه به شکل نوشتاری Starlink شباهت دارد؛ مالکیت تأیید نمی‌شود.";}
        else if(compact.matches(".*(satellite|satnet|orbit|spacenet|dishnet).*")){score=18;level="سرنخ عمومی ضعیف";reason="نام شبکه واژه عمومی مرتبط با ماهواره/فضا دارد؛ اختصاصی Starlink نیست.";}
        try{out.put("score",score);out.put("level",level);out.put("reason",reason);}catch(Exception ignored){}return out;
    }
    private void sendNetworks(boolean fresh){final String data=jsonNetworks();runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.onRadarNetworks("+data+","+fresh+")",null);});}
    private void scan(){if(!hasLocationPermission()){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ);return;}
        registerScanReceiver();if(!wifi.isWifiEnabled()){toast("ابتدا Wi-Fi گوشی را روشن کنید.");return;}boolean ok=wifi.startScan();
        if(!ok){sendNetworks(false);toast("اندروید اسکن تازه را موقتاً محدود کرده؛ نتایج موجود نمایش داده شد.");}else toast("درخواست اسکن ارسال شد.");}
    private JSONObject locationObject(Location l){JSONObject o=new JSONObject();try{o.put("lat",l.getLatitude());o.put("lon",l.getLongitude());o.put("accuracy",l.getAccuracy());o.put("time",l.getTime());o.put("provider",l.getProvider()==null?"":l.getProvider());}catch(Exception ignored){}return o;}
    private String locationJson(){try{if(!hasLocationPermission())return new JSONObject().put("error","مجوز مکان داده نشده").toString();Location best=null;
        for(String p:locationManager.getProviders(true)){Location l=locationManager.getLastKnownLocation(p);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}
        if(best==null)return new JSONObject().put("error","موقعیت موجود نیست؛ GPS را روشن کنید و دریافت زنده را بزنید.").toString();
        return locationObject(best).toString();}catch(Exception e){return "{\"error\":\"دریافت موقعیت ناموفق بود\"}";}}
    private void sendLocation(Location l){String data=locationObject(l).toString();runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.onRadarLocation("+data+")",null);});}
    private void requestLiveLocation(){if(!hasLocationPermission()){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ);return;}
        try{Location last=null;for(String p:locationManager.getProviders(true)){Location l=locationManager.getLastKnownLocation(p);if(l!=null&&(last==null||l.getTime()>last.getTime()))last=l;}
            if(last!=null)sendLocation(last);locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,1,locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000,5,locationListener);toast("دریافت زنده موقعیت فعال شد.");}
        catch(SecurityException e){toast("مجوز مکان لازم است.");}catch(Exception e){toast("GPS یا خدمات مکان‌یابی در دسترس نیست.");}}
    public class Bridge{
        @JavascriptInterface public void scan(){runOnUiThread(()->MainActivity.this.scan());}
        @JavascriptInterface public String getNetworks(){return jsonNetworks();}
        @JavascriptInterface public String getLocation(){return locationJson();}
        @JavascriptInterface public void requestLocation(){runOnUiThread(()->requestLiveLocation());}
        @JavascriptInterface public void openMap(int radius){runOnUiThread(()->{try{JSONObject p=new JSONObject(locationJson());if(p.has("lat")){String u="https://www.openstreetmap.org/?mlat="+p.getDouble("lat")+"&mlon="+p.getDouble("lon")+"#map="+(radius>500?14:radius>200?15:radius>80?16:17)+"/"+p.getDouble("lat")+"/"+p.getDouble("lon");startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}else toast(p.optString("error","موقعیت موجود نیست"));}catch(Exception e){toast("ابتدا موقعیت GPS را دریافت کنید.");}});}
        @JavascriptInterface public void speedTest(){ExecutorService ex=Executors.newSingleThreadExecutor();ex.execute(()->{HttpURLConnection c=null;try{long start=System.nanoTime();c=(HttpURLConnection)new URL("https://speed.cloudflare.com/__down?bytes=3000000").openConnection();c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setRequestProperty("Cache-Control","no-cache");InputStream in=c.getInputStream();byte[] buf=new byte[8192];long total=0;int n;while((n=in.read(buf))!=-1){total+=n;if(total>=2500000)break;}in.close();double sec=(System.nanoTime()-start)/1e9;double mbps=(total*8.0/1000000.0)/sec;final double value=mbps;runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.onRadarSpeed("+value+",null)",null);});}catch(Exception e){runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.onRadarSpeed(0,'آزمون سرعت انجام نشد؛ اتصال اینترنت را بررسی کنید.')",null);});}finally{if(c!=null)c.disconnect();ex.shutdown();}});}
        @JavascriptInterface public void shareReport(String text){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"اشتراک گزارش اسکن"));});}
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ&&hasLocationPermission()){registerScanReceiver();toast("مجوز مکان داده شد؛ دوباره عملیات را اجرا کنید.");}else if(r==REQ)toast("برای اسکن و GPS مجوز مکان لازم است.");}
    @Override protected void onPause(){super.onPause();try{locationManager.removeUpdates(locationListener);}catch(Exception ignored){}}
    @Override protected void onDestroy(){if(receiverRegistered)try{unregisterReceiver(receiver);}catch(Exception ignored){}try{locationManager.removeUpdates(locationListener);}catch(Exception ignored){}if(web!=null)web.destroy();super.onDestroy();}
}