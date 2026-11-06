package ir.ziaai.user;

import android.app.*;import android.os.*;import android.view.*;import android.webkit.*;import android.widget.*;import android.content.*;

public class MainActivity extends Activity {
 EditText address; WebView web;
 public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(0xff101b32); LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,18,16,8);root.setBackgroundColor(0xfff4f7fb);
 TextView title=new TextView(this);title.setText("ZiaAI | تبدیل صوت فارسی به متن");title.setTextSize(20);title.setTextColor(0xff14213b);root.addView(title);
 address=new EditText(this);address.setSingleLine(true);address.setHint("آدرس سرور HTTPS را وارد کنید");address.setText(getPreferences(0).getString("server",""));root.addView(address);
 Button open=new Button(this);open.setText("اتصال به سامانه");root.addView(open);
 web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setWebViewClient(new WebViewClient(){public boolean shouldOverrideUrlLoading(WebView v,String u){return !u.startsWith("https://");}});root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
 open.setOnClickListener(v->{String s=address.getText().toString().trim();if(!s.startsWith("https://")){Toast.makeText(this,"نشانی معتبر HTTPS وارد کنید",Toast.LENGTH_LONG).show();return;}getPreferences(0).edit().putString("server",s).apply();web.loadUrl(s);});
 String saved=address.getText().toString().trim();if(saved.startsWith("https://"))web.loadUrl(saved);
 }
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
}
