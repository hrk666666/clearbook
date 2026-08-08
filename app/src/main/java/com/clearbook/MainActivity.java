package com.clearbook;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1;
    private static final int PERMISSION_REQUEST = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#6750A4"));
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }

        // Request storage permissions at runtime
        requestStoragePermissions();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setTextZoom(100);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // JS interface
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onPageReady() {}

            @JavascriptInterface
            public String getAppVersion() {
                return "2.0";
            }

            @JavascriptInterface
            public void openExternal(String url) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "snackbar.show('无法打开链接')", null
                        );
                    });
                }
            }

            @JavascriptInterface
            public String downloadFile(String filename, String base64Content, String mimeType) {
                try {
                    byte[] data = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT);
                    String[] r = saveToDownloads(filename, data, mimeType);
                    // r[0]=显示路径, r[1]=uri(可打开), r[2]=是否因同名被重命名(0/1)
                    return "OK\u0001" + r[0] + "\u0001" + (r[1] != null ? r[1] : "") + "\u0001" + r[2];
                } catch (Exception e) {
                    return "ERR\u0001" + String.valueOf(e.getMessage());
                }
            }

            @JavascriptInterface
            public void openFile(String uri, String mimeType) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(uri), (mimeType != null && !mimeType.isEmpty()) ? mimeType : "text/plain");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "snackbar.show('无法打开文件: " + escapeJs(String.valueOf(e.getMessage())) + "')", null
                        );
                    });
                }
            }

            private String sanitizeFilename(String name) {
                if (name == null || name.isEmpty()) return "clearbook.txt";
                String safe = name.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_");
                return safe;
            }

            private String[] saveToDownloads(String filename, byte[] data, String mimeType) throws Exception {
                String safeName = sanitizeFilename(filename);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+: MediaStore 两阶段写入（IS_PENDING 必需，否则部分设备 insert 返回 null）
                    try {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safeName);
                        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType != null ? mimeType : "application/octet-stream");
                        values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
                        android.net.Uri collection = android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
                        android.net.Uri item = getContentResolver().insert(collection, values);
                        if (item == null) throw new java.io.IOException("MediaStore insert returned null");
                        java.io.OutputStream os = getContentResolver().openOutputStream(item);
                        if (os == null) throw new java.io.IOException("openOutputStream returned null");
                        os.write(data);
                        os.close();
                        values.clear();
                        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(item, values, null, null);
                        // 读取实际保存的文件名（同名时系统会自动加 (1) 后缀）
                        String actualName = safeName;
                        boolean renamed = false;
                        try {
                            android.database.Cursor c = getContentResolver().query(item,
                                new String[]{android.provider.MediaStore.Downloads.DISPLAY_NAME}, null, null, null);
                            if (c != null) {
                                if (c.moveToFirst()) {
                                    actualName = c.getString(0);
                                    renamed = !safeName.equals(actualName);
                                }
                                c.close();
                            }
                        } catch (Exception ignore) {}
                        return new String[]{"Download/" + actualName, item.toString(), renamed ? "1" : "0"};
                    } catch (Exception mediaStoreErr) {
                        // 兜底：写入 App 私有外部目录（无需任何权限，任何设备都可用）
                        java.io.File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                        if (dir == null) dir = getFilesDir();
                        if (!dir.exists()) dir.mkdirs();
                        java.io.File file = new java.io.File(dir, safeName);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                        fos.write(data);
                        fos.close();
                        return new String[]{"App 目录: " + file.getAbsolutePath(), null, "0"};
                    }
                } else {
                    // Android 9 及以下：直接写公共目录
                    java.io.File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    java.io.File file = new java.io.File(dir, safeName);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                    fos.write(data);
                    fos.close();
                    // Notify media scanner
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(Uri.fromFile(file));
                    sendBroadcast(mediaScanIntent);
                    return new String[]{"Download/" + safeName, Uri.fromFile(file).toString(), "0"};
                }
            }
        }, "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                    "document.body.style.scrollbarWidth='none';" +
                    "document.documentElement.style.scrollbarWidth='none';",
                    null
                );
            }
        });

        // Handle downloads
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url.startsWith("blob:")) {
                webView.evaluateJavascript(
                    "(function() {" +
                    "  var xhr = new XMLHttpRequest();" +
                    "  xhr.open('GET', '" + url + "');" +
                    "  xhr.responseType = 'blob';" +
                    "  xhr.onload = function() {" +
                    "    var reader = new FileReader();" +
                    "    reader.onloadend = function() {" +
                    "      var base64 = reader.result.split(',')[1];" +
                    "      Android.downloadFile('clearbook_clean.txt', base64, 'text/plain');" +
                    "    };" +
                    "    reader.readAsDataURL(xhr.response);" +
                    "  };" +
                    "  xhr.send();" +
                    "})();",
                    null
                );
            } else {
                String filename = "download";
                if (contentDisposition != null) {
                    try {
                        android.net.Uri dispositionUri = android.net.Uri.parse("http://a/?" + contentDisposition);
                        String cdFilename = dispositionUri.getQueryParameter("filename");
                        if (cdFilename != null && !cdFilename.isEmpty()) {
                            filename = cdFilename;
                        }
                    } catch (Exception ignored) {}
                }
                if (filename.equals("download") && url != null) {
                    try {
                        String path = android.net.Uri.parse(url).getLastPathSegment();
                        if (path != null && !path.isEmpty()) {
                            filename = path;
                        }
                    } catch (Exception ignored) {}
                }
                final String finalFilename = filename;
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setDescription("Downloading...");
                    request.setTitle(finalFilename);
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFilename);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
"snackbar.show('开始下载: " + escapeJs(finalFilename) + "')", null
                        );
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
"snackbar.show('下载失败: " + escapeJs(String.valueOf(e.getMessage())) + "')", null
                        );
                    });
                }
            }
        });

        // File chooser - use ACTION_GET_CONTENT for better compatibility
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                // Try FileChooserParams first, fallback to ACTION_GET_CONTENT
                Intent intent = null;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception ignored) {}

                if (intent == null) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    // Add accepted types from HTML accept attribute
                    String[] acceptTypes = fileChooserParams.getAcceptTypes();
                    if (acceptTypes != null && acceptTypes.length > 0) {
                        StringBuilder mimeBuilder = new StringBuilder();
                        for (String type : acceptTypes) {
                            if (type != null && !type.isEmpty() && !type.equals("*/*")) {
                                if (mimeBuilder.length() > 0) mimeBuilder.append(",");
                                mimeBuilder.append(type);
                            }
                        }
                        if (mimeBuilder.length() > 0) {
                            intent.setType(mimeBuilder.toString());
                        }
                    }
                }

                try {
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            };
            boolean needRequest = false;
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }
            if (needRequest) {
                requestPermissions(perms, PERMISSION_REQUEST);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Permissions handled - DownloadManager works without WRITE permission on Android 10+
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    private static String escapeJs(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
