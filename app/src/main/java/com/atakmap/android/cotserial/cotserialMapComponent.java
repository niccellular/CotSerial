
package com.atakmap.android.cotserial;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownMapComponent;

import com.atakmap.app.BuildConfig;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.cotserial.plugin.R;

public class cotserialMapComponent extends DropDownMapComponent {

    private static final String TAG = "cotserialMapComponent";

    private Context pluginContext;

    private cotserialDropDownReceiver ddr;

    private Activity activity;

    private OutboundMessageHandler outboundMessageHandler;



    public cotserialMapComponent(Activity activity) {
        this.activity = activity;
    }

    public void onCreate(final Context context, Intent intent,
            final MapView view) {

        context.setTheme(R.style.ATAKPluginTheme);
        super.onCreate(context, intent, view);
        pluginContext = context;

        ddr = new cotserialDropDownReceiver(
                view, context, activity);

        Log.d(TAG, "registering the plugin filter");
        DocumentedIntentFilter ddFilter = new DocumentedIntentFilter();
        ddFilter.addAction(cotserialDropDownReceiver.SHOW_PLUGIN);
        registerDropDownReceiver(ddr, ddFilter);
        outboundMessageHandler = new OutboundMessageHandler(pluginContext, ddr);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
    }

}
