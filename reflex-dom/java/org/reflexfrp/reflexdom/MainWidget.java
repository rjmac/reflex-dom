package org.reflexfrp.reflexdom;

import java.lang.reflect.Method;

import android.Manifest;
import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.content.pm.PackageManager;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;

import java.nio.charset.StandardCharsets;

public class MainWidget {
  static final int REQ_READ_CAMERA = 1;

  private static Object startMainWidget(final Activity a, String url, long jsaddleCallbacks, final String initialJS) {
    CookieManager.setAcceptFileSchemeCookies(true); //TODO: Can we do this just for our own WebView?

    // Remove title and notification bars
    a.requestWindowFeature(Window.FEATURE_NO_TITLE);

    final WebView wv = new WebView(a);
    wv.addJavascriptInterface(new CustomJS(a, wv), "magicounter");
    wv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    wv.setWebChromeClient(new WebChromeClient() {
            PermissionRequest pendingRequest;

            {
                try {
                    Class<?> cls = a.getClass();
                    Method m = cls.getMethod("setOnRequestPermissionsResultCallback", Object.class);
                    m.invoke(a, this);
                } catch(RuntimeException e) {
                    throw e;
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }
            private void askPermission(Activity a, PermissionRequest request) {
                if(a.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(request.getResources());
                } else {
                    pendingRequest = request;
                    a.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_READ_CAMERA);
                }
            }

            public boolean onRequestPermissionsResultCallback(int requestCode, String[] permissions, int[] grantResults) {
                if(pendingRequest != null) {
                    PermissionRequest request = pendingRequest;
                    pendingRequest = null;
                    if(requestCode == REQ_READ_CAMERA) {
                        if(permissions.length != 1 || grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                            request.deny();
                        } else {
                            request.grant(request.getResources());
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                for(String r : request.getResources()) {
                    if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                        askPermission(a, request);
                        break;
                    }
                }
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                pendingRequest = null;
            }
        });
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
      public final void evaluateJavascript(final byte[] js) {
        final String jsStr = new String(js, StandardCharsets.UTF_8);
        hnd.post(new Runnable() {
            @Override
            public void run() {
              wv.evaluateJavascript(jsStr, null);
            }
          });
      }
    };
  }

  private static class CustomJS {
      private final Activity activity;
      private final WebView webView;

      public CustomJS(Activity activity, WebView webView) {
          this.activity = activity;
          this.webView = webView;
      }

      @JavascriptInterface
      public String setupBattery() {
          try {
              Class<?> cls = activity.getClass();
              Method m = cls.getMethod("setBatteryStatusCallback", Object.class);
              return (String) m.invoke(activity, CustomJS.this);
          } catch(RuntimeException e) {
              throw e;
          } catch(Exception e) {
              throw new RuntimeException(e);
          }
      }

      public void onBatteryStatusCallback(final boolean charging, final float percent) {
          webView.post(new Runnable() {
                  public void run() {
                      webView.evaluateJavascript("batterycb({ charging: " + charging + ", percent: " + percent + "});", null);
                  }
              });
      }
  }

  private static class JSaddleCallbacks {
    private final long callbacks;
    private native void startProcessing(long callbacks);
    private native void processMessage(long callbacks, byte[] msg);
    private native byte[] processSyncMessage(long callbacks, byte[] msg);

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
      processMessage(callbacks, msg.getBytes(StandardCharsets.UTF_8));
      return true;
    }

    @JavascriptInterface
    public String syncMessage(final String msg) {
      return new String(processSyncMessage(callbacks, msg.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8);
    }
  }
}
