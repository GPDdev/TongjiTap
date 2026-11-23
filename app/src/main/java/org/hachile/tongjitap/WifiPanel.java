package org.hachile.tongjitap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;
import java.util.function.Consumer;

/** Operates only the visible System UI panel and verifies WifiManager state after one tap. */
final class WifiPanel {
    private final TapService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean canceled, opened;
    WifiPanel(TapService service) { this.service = service; }
    boolean enabled() { return service.getApplicationContext().getSystemService(WifiManager.class).isWifiEnabled(); }
    void change(boolean desired, RelayConfig config, Consumer<Boolean> done) {
        if (enabled() == desired) { done.accept(true); return; }
        opened = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
        if (!opened) { done.accept(false); return; }
        handler.postDelayed(() -> tapWhenReady(desired, config, done, 0), 900);
    }
    private void tapWhenReady(boolean desired, RelayConfig config, Consumer<Boolean> done, int attempt) {
            if (canceled) return;
            if (!service.screenUnlocked()) { finish(false, done); return; }
            if (!systemUi() || !tileReady(config)) {
                if (attempt >= 15) { finish(false, done); return; }
                // A previous shade animation can outlive its accessibility window on some systems.
                if (attempt == 4 && (systemUi() || service.relayTargetVisible()))
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
                handler.postDelayed(() -> tapWhenReady(desired, config, done, attempt+1), 200); return;
            }
            if (BuildConfig.DEBUG) android.util.Log.d("TongjiTapWifi", "tap desired="+desired+" xy="+config.wifiX+","+config.wifiY);
            Path path = new Path(); path.moveTo(config.wifiX, config.wifiY);
            boolean submitted = service.dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 70)).build(),
                new AccessibilityService.GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription gesture) { verify(desired, 0, false, done); }
                    @Override public void onCancelled(GestureDescription gesture) { finish(false, done); }
                }, handler);
            if (!submitted) finish(false, done);
    }
    private boolean tileReady(RelayConfig config) {
        AccessibilityNodeInfo root=service.foregroundRoot(); if(root==null)return false;
        try { root.refresh(); return tileAt(root,config.wifiX,config.wifiY,0); } finally { root.recycle(); }
    }
    private boolean tileAt(AccessibilityNodeInfo node,int x,int y,int depth) {
        if(depth>16)return false;
        Rect r=new Rect();node.getBoundsInScreen(r);
        if(node.isVisibleToUser()&&node.isClickable()&&r.contains(x,y)&&r.height()<ScreenInfo.read(service).height/3&&hasNetworkLabel(node,0))return true;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo c=node.getChild(i);if(c==null)continue;
            try{if(tileAt(c,x,y,depth+1))return true;}finally{c.recycle();}}
        return false;
    }
    private boolean hasNetworkLabel(AccessibilityNodeInfo node,int depth) {
        String label=label(node);
        if(label.startsWith("wi-fi")||label.startsWith("wifi")||label.startsWith("wlan")||label.startsWith("internet")
                ||label.startsWith("互联网")||label.startsWith("インターネット")||label.startsWith("无线")||label.startsWith("無線"))return true;
        if(depth>=3)return false;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo c=node.getChild(i);if(c==null)continue;
            try{if(hasNetworkLabel(c,depth+1))return true;}finally{c.recycle();}}
        return false;
    }
    private String label(AccessibilityNodeInfo node) {
        return ((node.getText()==null?"":node.getText())+" "+(node.getContentDescription()==null?"":node.getContentDescription())).toLowerCase(Locale.ROOT)
            .replace('\u2011','-').replace('\u2010','-').replace('\u2013','-').trim();
    }
    private void verify(boolean desired, int attempt, boolean triedSwitch, Consumer<Boolean> done) {
        if (canceled) return;
        if (enabled() == desired) { finish(true, done); return; }
        if (attempt >= 10 || !systemUi()) { finish(false, done); return; }
        boolean clicked = triedSwitch;
        if (attempt == 2 && !triedSwitch) {
            AccessibilityNodeInfo root = service.foregroundRoot();
            if (root != null) { try { root.refresh(); clicked = clickWifiSwitch(root, desired, 0); } finally { root.recycle(); } }
            if (BuildConfig.DEBUG) android.util.Log.d("TongjiTapWifi", "fallback desired="+desired+" clicked="+clicked+" enabled="+enabled());
        }
        final boolean alreadyTried = clicked;
        handler.postDelayed(() -> verify(desired, attempt + 1, alreadyTried, done), 500);
    }
    private boolean clickWifiSwitch(AccessibilityNodeInfo node, boolean desired, int depth) {
        if (depth > 14) return false;
        node.refresh();
        String label = label(node);
        boolean wifi = label.contains("wi-fi") || label.contains("wifi") || label.contains("wlan");
        if (wifi && node.isCheckable() && node.isChecked() != desired && node.isClickable() && String.valueOf(node.getClassName()).contains("Switch"))
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (wifi && node.isClickable() && containsSwitch(node,desired)) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i); if (child == null) continue;
            try { if (clickWifiSwitch(child, desired, depth + 1)) return true; } finally { child.recycle(); }
        }
        return false;
    }
    private boolean containsSwitch(AccessibilityNodeInfo node,boolean desired) {
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i); if (child == null) continue;
            try { if (child.isCheckable() && child.isChecked()!=desired && String.valueOf(child.getClassName()).contains("Switch")) return true; }
            finally { child.recycle(); }
        }
        return false;
    }
    private boolean systemUi() {
        if (!service.screenUnlocked()) return false;
        AccessibilityNodeInfo root = service.foregroundRoot();
        if (root == null) return false;
        try { return "com.android.systemui".contentEquals(root.getPackageName()); }
        finally { root.recycle(); }
    }
    private void finish(boolean success, Consumer<Boolean> done) {
        if (canceled) return;
        dismiss(); handler.postDelayed(() -> { if (!canceled) done.accept(success); }, 1100);
    }
    private void dismiss() {
        if (!opened) return;
        if (systemUi()) {
            // Back also closes Android's Internet dialog; dismissing the shade alone leaves it open.
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            handler.postDelayed(() -> {
                if (!systemUi()) return;
                if (android.os.Build.VERSION.SDK_INT >= 31) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
                else service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            }, 250);
        }
        opened = false;
    }
    void cancel() { canceled = true; handler.removeCallbacksAndMessages(null); dismiss(); }
}
