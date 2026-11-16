package local.lightpage.xhs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A local WebView shell. No native JavaScript bridge, analytics or remote configuration. */
public final class MainActivity extends Activity {
    private static final String HOME = "https://www.xiaohongshu.com/explore";
    private static final String VERSION = "0.2.0";
    /** Pages the shell keeps inside the WebView; everything else needs user confirmation. */
    private static final String[] SITE_HOSTS = { "www.xiaohongshu.com", "www.rednote.com" };
    /**
     * Login/authorisation pages. They redirect across hosts, so keeping them in-app is
     * required for third-party sign-in. The list is deliberately narrow instead of
     * "any https page", which would let a redirect chain open arbitrary site content.
     */
    private static final String[] AUTH_HOSTS = {
        "open.weixin.qq.com", "long.open.weixin.qq.com", "login.weixin.qq.com",
        "open.qq.com", "graph.qq.com", "ptlogin2.qq.com", "xui.ptlogin2.qq.com",
        "passport.weibo.com", "api.weibo.com", "open.weibo.com",
        "appleid.apple.com", "accounts.google.com"
    };
    private WebView web;
    private FrameLayout root;
    private String scripts;
    private String backHandler;
    private ValueCallback<Uri[]> fileCallback;
    private long lastExit;
    private boolean checkingBack;
    private View fullScreen;
    private WebChromeClient.CustomViewCallback fullScreenCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        scripts = readAsset("xhs-mobile-layout.user.js") + "\n;" + readAsset("xhs-app-back.user.js");
        backHandler = readAsset("back.js");
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        configureInsets();
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        String ua = settings.getUserAgentString();
        Matcher matcher = Pattern.compile("Chrome/([0-9.]+)").matcher(ua);
        String chrome = matcher.find() ? matcher.group(1) : "120.0.0.0";
        settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome + " Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { inject(url); }
            @Override public void onPageCommitVisible(WebView view, String url) { inject(url); }
            @Override public void onPageFinished(WebView view, String url) { inject(url); CookieManager.getInstance().flush(); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                boolean web = "https".equals(scheme) || "http".equals(scheme);
                // Site pages and sign-in pages stay inside the shell.
                if (web && (official(uri) || authHost(uri))) return false;
                // Non-main-frame requests (ads, embedded frames) never leave the site.
                if (!request.isForMainFrame()) return true;
                if (!web) {
                    // Custom schemes (weixin://, alipays://, ...) only via an explicit tap
                    // and an explicit confirmation; never launched automatically.
                    if (request.hasGesture()) confirmExternal(uri, "用其他应用打开？");
                    else toast("已阻止自动唤起其他应用");
                    return true;
                }
                if (request.hasGesture()) confirmExternal(uri, "在浏览器打开？");
                else toast("已阻止站外跳转：" + (uri.getHost() == null ? scheme : uri.getHost()));
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) toast("页面加载失败，可长按空白处选择重新加载");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try { startActivityForResult(params.createIntent(), 41); return true; }
                catch (Exception ignored) { fileCallback = null; callback.onReceiveValue(null); return true; }
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullScreen != null) { callback.onCustomViewHidden(); return; }
                fullScreen = view; fullScreenCallback = callback;
                web.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView() { leaveFullScreen(); }
        });
        web.setOnLongClickListener(view -> {
            if (web.getHitTestResult().getType() != WebView.HitTestResult.UNKNOWN_TYPE) return false;
            new AlertDialog.Builder(this).setTitle("轻页 · 非官方网页显示适配")
                .setItems(new String[]{"重新加载", "回首页", "用浏览器打开", "关于"}, (dialog, which) -> {
                    if (which == 0) web.reload();
                    else if (which == 1) web.loadUrl(HOME);
                    else if (which == 2) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web.getUrl()))); } catch (Exception ignored) {}
                    } else new AlertDialog.Builder(this).setMessage(VERSION + " 测试版\n内容由官方网站提供。本应用与小红书无隶属、赞助或授权关系，也未获其认可。\n不收集数据，无统计 SDK，无开发者服务器；登录信息仅保存在本机 WebView。\n使用即表示你已知晓这属于非官方适配，并自行承担风险。").setPositiveButton("知道了", null).show();
                }).show();
            return true;
        });
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, () -> appBack());
        if (state == null || web.restoreState(state) == null) web.loadUrl(HOME);
    }

    private void configureInsets() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                if (fullScreen != null) { view.setPadding(0, 0, 0, 0); return insets; }
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                return insets;
            });
        }
    }
    private void confirmExternal(Uri uri, String title) {
        String shown = uri.toString();
        if (shown.length() > 300) shown = shown.substring(0, 300) + "…";
        new AlertDialog.Builder(this).setTitle(title).setMessage(shown)
            .setPositiveButton("打开", (dialog, which) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ignored) { toast("没有可用的应用处理此链接"); }
            })
            .setNegativeButton("取消", null).show();
    }
    private static boolean authHost(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String allowed : AUTH_HOSTS) if (allowed.equals(host)) return true;
        return false;
    }
    private static boolean official(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("xiaohongshu.com") || host.endsWith(".xiaohongshu.com") || host.equals("rednote.com") || host.endsWith(".rednote.com");
    }
    private void inject(String url) {
        if (url == null) return;
        String host = Uri.parse(url).getHost();
        if (host == null) return;
        host = host.toLowerCase(Locale.ROOT);
        for (String site : SITE_HOSTS) {
            if (site.equals(host)) { web.evaluateJavascript(scripts, null); return; }
        }
    }
    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        } catch (Exception e) { throw new IllegalStateException("Missing asset: " + name, e); }
    }
    private void appBack() {
        if (fullScreen != null) { leaveFullScreen(); return; }
        if (checkingBack) return;
        checkingBack = true;
        web.evaluateJavascript(backHandler, result -> {
            checkingBack = false;
            if (isFinishing()) return;
            if ("\"handled\"".equals(result)) return;
            if ("\"home\"".equals(result)) {
                web.evaluateJavascript("(()=>{const r=document.querySelector('#app')?.__vue_app__?.config?.globalProperties?.$router;if(r){r.replace('/explore');return true;}return false;})()", routed -> {
                    if (!"true".equals(routed)) web.loadUrl(HOME);
                });
            } else if ("\"exit\"".equals(result)) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastExit < 1800) finish();
                else { lastExit = now; toast("再按一次返回退出轻页"); }
            } else if (web.canGoBack()) web.goBack();
            else web.loadUrl(HOME);
        });
    }
    private void leaveFullScreen() {
        if (fullScreen == null) return;
        root.removeView(fullScreen); fullScreen = null; web.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 30) root.requestApplyInsets();
        if (fullScreenCallback != null) { fullScreenCallback.onCustomViewHidden(); fullScreenCallback = null; }
    }
    @Override public void onBackPressed() { appBack(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request == 41 && fileCallback != null) { fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data)); fileCallback = null; }
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); web.saveState(state); }
    @Override protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() { if (fileCallback != null) fileCallback.onReceiveValue(null); web.destroy(); super.onDestroy(); }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show(); }
}
