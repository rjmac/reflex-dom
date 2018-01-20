package org.reflexfrp.reflexdom;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;


public class MainWidget {
  private static Object startMainWidget(Activity a, String url, long jsaddleCallbacks, final String initialJS) {
    CookieManager.setAcceptFileSchemeCookies(true); //TODO: Can we do this just for our own WebView?

    // Remove title and notification bars
    a.requestWindowFeature(Window.FEATURE_NO_TITLE);

    final WebView wv = new WebView(a);
    wv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    a.setContentView(wv);
    final WebSettings ws = wv.getSettings();
    ws.setJavaScriptEnabled(true);
    ws.setAllowFileAccessFromFileURLs(true);
    ws.setAllowUniversalAccessFromFileURLs(true);
    ws.setDomStorageEnabled(true);
    wv.setWebContentsDebuggingEnabled(true);
    // allow video to play without user interaction
    wv.getSettings().setMediaPlaybackRequiresUserGesture(false);

    wv.setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                      | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                      | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                      | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                      | View.SYSTEM_UI_FLAG_FULLSCREEN
                      | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

    wv.setWebViewClient(new WebViewClient() {
        @Override
        public void onPageFinished(WebView _view, String _url) {
          wv.evaluateJavascript(initialJS, null);
        }
      });

    wv.addJavascriptInterface(new JSaddleCallbacks(jsaddleCallbacks), "jsaddle");

    wv.loadUrl(url);

    final Handler hnd = new Handler();
    return new Object() {
      public final void evaluateJavascript(final String js) {
        android.util.Log.d("MC2", js);
        hnd.post(new Runnable() {
            @Override
            public void run() {
              // A weird thing happens going over this boundary into
              // the webview.  Unicode characters can sometimes
              // silently vanish!  I have NO IDEA what causes this,
              // but the upshot of it is that we can't put Unicode
              // there.
              String jsc = convertUnicode(js);
              android.util.Log.d("MC3", jsc);
              wv.evaluateJavascript(jsc, null);
            }

            private String convertUnicode(String str) {
              // This conversion would be way more elegant with Java 8
              // streams, but they're not supported until SDK 24 and
              // reflex-dom supports back to 21.
              //
              // It can be pretty naïve because non-ASCII chars are only
              // inside strings in the JS we generate, so we'll just blindly
              // convert everything like that.

              StringBuilder sb = new StringBuilder(str.length());

              // int codepoint;
              // for(int i = 0; i < str.length(); i += Character.charCount(codepoint)) {
              //   codepoint = str.codePointAt(i);
              //   if(codepoint < 128) {
              //     sb.append((char) codepoint);
              //   } else {
              //     sb.append("\\u{").
              //         append(Integer.toString(codepoint, 16)).
              //         append('}');
              //   }
              // }
              for(int i = 0; i < str.length(); ++i) {
                  char c = str.charAt(i);
                  if(c < 128) {
                      sb.append(c);
                  } else {
                      sb.append(String.format("\\u%04x", (int)c));
                  }
              }

              // If we didn't actually find any unicode, don't bother
              // re-re(-re-re)-copying the string
              return sb.length() == str.length() ? str : sb.toString();
            }
          });
      }
    };
  }

  private static class JSaddleCallbacks {
    private final long callbacks;
    private native void startProcessing(long callbacks);
    private native void processMessage(long callbacks, String msg);
    private native String processSyncMessage(long callbacks, String msg);

    public JSaddleCallbacks(long _callbacks) {
      callbacks = _callbacks;
    }

    @JavascriptInterface
    public boolean postReady() {
      startProcessing(callbacks);
      return true;
    }

    @JavascriptInterface
    public boolean postMessage(final String msg) {
      android.util.Log.d("MC4a", msg);
      processMessage(callbacks, msg);
      return true;
    }

    @JavascriptInterface
    public String syncMessage(final String msg) {
      android.util.Log.d("MC4s", msg);
      String result = processSyncMessage(callbacks, msg);
      android.util.Log.d("MC4r", result);
      return result;
    }
  }
}
