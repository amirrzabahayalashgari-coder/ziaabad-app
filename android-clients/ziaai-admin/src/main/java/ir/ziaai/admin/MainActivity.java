package ir.ziaai.admin;

import android.app.*;import android.os.*;import android.view.*;import android.webkit.*;import android.widget.*;

public class MainActivity extends Activity {
 EditText address; WebView web;
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff101a2d);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,18,16,8);root.setBackgroundColor(0xfff3f5f9);
 TextView title=new TextView(this);title.setText("ZiaAI Admin | مدیریت سامانه");title.setTextSize(20);title.setTextColor(0xff17233b);root.addView(title);
 address=new EditText(this);address.setSingleLine(true);address.setHint("آدرس سرور HTTPS را وارد کنید");address.setText(getPreferences(0).getString("server",""));root.addView(address);
 Button open=new Button(this);open.setText("ورود به پنل مدیریت");root.addView(open);
 web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setWebViewClient(new WebViewClient(){public boolean shouldOverrideUrlLoading(WebView v,String u){return !u.startsWith("https://");}});root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
 open.setOnClickListener(v->{String s=address.getText().toString().trim();if(!s.startsWith("https://")){Toast.makeText(this,"نشانی معتبر HTTPS وارد کنید",Toast.LENGTH_LONG).show();return;}getPreferences(0).edit().putString("server",s).apply();web.loadUrl(s.replaceAll("/+$","")+"/admin");});
 String saved=address.getText().toString().trim();if(saved.startsWith("https://"))web.loadUrl(saved.replaceAll("/+$","")+"/admin");
 }
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
}
