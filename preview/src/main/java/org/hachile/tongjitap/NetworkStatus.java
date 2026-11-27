package org.hachile.tongjitap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/** Reads only the radio state and the transport reported by Android. */
final class NetworkStatus {
    private final Context context;
    private final ConnectivityManager connectivity;
    private final WifiManager wifi;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView radio, transport;
    private boolean listening;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { refresh(); }
    };
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { refresh(); }
        @Override public void onLost(Network network) { refresh(); }
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { refresh(); }
    };

    NetworkStatus(Context c) {
        context = c;
        connectivity = c.getSystemService(ConnectivityManager.class);
        wifi = c.getApplicationContext().getSystemService(WifiManager.class);
    }

    void show(LinearLayout page) {
        LinearLayout body = Ui.column(context);
        body.addView(Ui.heading(context, context.getString(R.string.network)));
        Ui.gap(body, 14);
        LinearLayout state = new LinearLayout(context);
        state.setGravity(Gravity.CENTER_VERTICAL);
        state.addView(new BlueUi.Mark(context, BlueUi.Mark.Kind.WIFI, Ui.BLUE), MusicUi.size(context, 34, 34));
        radio = Ui.heading(context, "");
        radio.setPadding(Ui.dp(context, 12), 0, 0, 0);
        state.addView(radio);
        body.addView(state);
        Ui.gap(body, 16);
        BlueUi.divider(body);
        Ui.gap(body, 12);
        LinearLayout connection = new LinearLayout(context);
        connection.setGravity(Gravity.CENTER_VERTICAL);
        connection.addView(Ui.text(context, context.getString(R.string.connection), Ui.INK), new LinearLayout.LayoutParams(0, -2, 1));
        transport = Ui.text(context, "", Ui.MUTED);
        connection.addView(transport);
        body.addView(connection);
        page.addView(BlueUi.statusPanel(context, body));
        Ui.gap(page, 28);
        page.addView(Ui.heading(context, context.getString(R.string.help)));
        Ui.gap(page, 8);
        page.addView(Ui.text(context, context.getString(R.string.network_help), Ui.MUTED));
        refresh();
    }

    void start() {
        if (listening) return;
        listening = true;
        ContextCompat.registerReceiver(context, changes, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        connectivity.registerDefaultNetworkCallback(callback, main);
        refresh();
    }

    void stop() {
        if (!listening) return;
        listening = false;
        context.unregisterReceiver(changes);
        connectivity.unregisterNetworkCallback(callback);
    }

    private void refresh() {
        if (radio == null) return;
        int state = wifi == null ? WifiManager.WIFI_STATE_UNKNOWN : wifi.getWifiState();
        int label = state == WifiManager.WIFI_STATE_ENABLED ? R.string.wifi_on
                : state == WifiManager.WIFI_STATE_DISABLED ? R.string.wifi_off : R.string.unavailable;
        radio.setText(context.getString(label));
        NetworkCapabilities caps = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        String name;
        if (caps == null) name = context.getString(R.string.offline);
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) name = "VPN";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) name = "Wi-Fi";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) name = context.getString(R.string.mobile);
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) name = context.getString(R.string.ethernet);
        else name = context.getString(R.string.other);
        transport.setText(name);
    }
}
