package com.dianping.loader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.dianping.app.MyActivity;
import com.dianping.app.MyApplication;
import com.dianping.loader.model.SiteSpec;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class HomeActivity extends MyActivity {
	TextView siteUrl;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);

		siteUrl = (TextView) findViewById(R.id.siteurl);
		findViewById(R.id.go).setOnClickListener(clickListener);
		findViewById(R.id.go_last).setOnClickListener(clickListener);
		findViewById(R.id.go_helloworld).setOnClickListener(clickListener);
		findViewById(R.id.go_bitmapfun).setOnClickListener(clickListener);
	}

	@Override
	protected void onResume() {
		super.onResume();

		File dir = new File(getFilesDir(), "repo");
		File site = new File(dir, "site.txt");
		findViewById(R.id.go_last).setEnabled(site.length() > 0);
		TextView lastUrl = (TextView) findViewById(R.id.last_url);
		lastUrl.setText(null);
		if (site.length() > 0) {
			File file = new File(dir, "lastUrl.txt");
			try {
				FileInputStream fis = new FileInputStream(file);
				byte[] bytes = new byte[fis.available()];
				fis.read(bytes);
				fis.close();
				String url = new String(bytes, "UTF-8");
				lastUrl.setText(url);
			} catch (Exception e) {
				SiteSpec lastSite = MyApplication.instance().readSite();
				String url = MyApplication.PRIMARY_SCHEME + "://"
						+ lastSite.fragments()[0].host();
				lastUrl.setText(url);
			}
		}
	}

	private final View.OnClickListener clickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (v.getId() == R.id.go) {
				Worker worker = new Worker(siteUrl.getText().toString());
				worker.start();
				v.setEnabled(false);
			} else if (v.getId() == R.id.go_last) {
				String url = ((TextView) findViewById(R.id.last_url)).getText()
						.toString();
				startActivity(url);
			} else if (v.getId() == R.id.go_helloworld) {
				siteUrl.setText("https://raw.github.com/mmin18/AndroidDynamicLoader/master/site/helloworld/site.txt");
				findViewById(R.id.go).performClick();
			} else if (v.getId() == R.id.go_bitmapfun) {
				siteUrl.setText("https://raw.github.com/mmin18/AndroidDynamicLoader/master/site/bitmapfun/site.txt");
				findViewById(R.id.go).performClick();
			}
		}
	};

	private class Worker extends Thread {
		private String url;

		public Worker(String url) {
			this.url = url;
		}

		@Override
		public void run() {
			final String siteUrl = url;
			try {
				URL url = new URL(siteUrl);
				HttpURLConnection conn = (HttpURLConnection) url
						.openConnection();
                if (siteUrl.startsWith("https")) {
                    // 创建SSLContext对象，并使用我们指定的信任管理器初始化
                    TrustManager[] tm = {new MyX509TrustManager()};
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, tm, new java.security.SecureRandom());

                    // 从上述SSLContext对象中得到SSLSocketFactory对象
                    SSLSocketFactory ssf = sslContext.getSocketFactory();
                    conn = (HttpsURLConnection) conn;
                    ((HttpsURLConnection) conn).setSSLSocketFactory(ssf);
                    ((HttpsURLConnection) conn).setHostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String arg0, SSLSession arg1) {
                            return true;
                        }
                    });
                }


				conn.setConnectTimeout(15000);
				InputStream ins = conn.getInputStream();
				ByteArrayOutputStream bos = new ByteArrayOutputStream(16 * 1024);
				byte[] buf = new byte[1024 * 4]; // 4k buffer
				int l;
				while ((l = ins.read(buf, 0, buf.length)) != -1) {
					bos.write(buf, 0, l);
				}
				bos.close();
				ins.close();
				conn.disconnect();

				byte[] bytes = bos.toByteArray();
				String str = new String(bytes, "UTF-8");

				// TODO:
				// verify signature

				JSONObject json = new JSONObject(str);
				final SiteSpec fSite = new SiteSpec(json);

				File dir = new File(getFilesDir(), "repo");
				new File(dir, "lastUrl.txt").delete();
				dir.mkdir();
				File local = new File(dir, "site.txt");
				File tmp = new File(dir, "site_tmp");
				try {
					FileOutputStream fos = new FileOutputStream(tmp);
					fos.write(bytes);
					fos.close();
					tmp.renameTo(local);
				} catch (Exception e) {
					tmp.delete();
				}

				Log.i("loader", "site.xml updated to " + fSite.id());
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						findViewById(R.id.go).setEnabled(true);
						String url = MyApplication.PRIMARY_SCHEME + "://"
								+ fSite.fragments()[0].host();
						Intent i = new Intent(Intent.ACTION_VIEW,
								Uri.parse(url));
						i.putExtra("_site", fSite);
						startActivity(i);
					}
				});
			} catch (final Exception e) {
				Log.w("loader", "fail to download site from " + siteUrl, e);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						findViewById(R.id.go).setEnabled(true);
						Toast.makeText(HomeActivity.this, e.toString(),
								Toast.LENGTH_LONG).show();
					}
				});
			}
		}
	}

}
