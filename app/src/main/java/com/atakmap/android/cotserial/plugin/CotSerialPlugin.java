package com.atakmap.android.cotserial.plugin;

import android.app.Activity;
import android.app.PendingIntent;
import android.os.Build;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.usb.UsbManager;
import android.view.View;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.cotserial.cotserialMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.BuildConfig;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.cotserial.cotserialDropDownReceiver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class CotSerialPlugin implements IPlugin {

    private static final String TAG = "CotSerialPlugin";
    public static final String ACTION_USB_PERMISSION = BuildConfig.APPLICATION_ID + ".ACTION_USB_PERMISSION";

    private IServiceController serviceController;
    private Context pluginContext;
    private IHostUIService uiService;
    private ToolbarItem toolbarItem;
    private Pane serialPane;
    private final Collection<MapComponent> overlays;
    private BroadcastReceiver m_usbReceiver;

    public CotSerialPlugin(IServiceController serviceController) {
        this.serviceController = serviceController;
        this.overlays = new LinkedList<>();
        
        final PluginContextProvider ctxProvider = serviceController
                .getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }

        // Initialize native loader
        PluginNativeLoader.init(pluginContext);

        // obtain the UI service
        uiService = serviceController.getService(IHostUIService.class);

        // create the button
        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        // Send broadcast to show the dropdown
                        Intent i = new Intent(cotserialDropDownReceiver.SHOW_PLUGIN);
                        AtakBroadcast.getInstance().sendBroadcast(i);
                    }
                })
                .build();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        
        // Get the MapView instance
        MapView mapView = MapView.getMapView();
        
        // the plugin is starting, add the button to the toolbar
        if (uiService != null) {
            uiService.addToolbarItem(toolbarItem);
        }

        if (mapView != null && pluginContext != null) {
            // Get activity from plugin context if it's an activity context
            Activity activity = null;
            if (pluginContext instanceof Activity) {
                activity = (Activity) pluginContext;
            } else if (pluginContext instanceof android.content.ContextWrapper) {
                Context baseContext = ((android.content.ContextWrapper) pluginContext).getBaseContext();
                if (baseContext instanceof Activity) {
                    activity = (Activity) baseContext;
                }
            }
            
            if (activity == null) {
                // Try to get activity from the MapView's context
                Context mvContext = mapView.getContext();
                if (mvContext instanceof Activity) {
                    activity = (Activity) mvContext;
                }
            }
            
            if (activity != null) {
                // Create map component
                overlays.add(new cotserialMapComponent(activity));

                // Initialize components
                Iterator<MapComponent> iter = overlays.iterator();
                MapComponent c;
                while (iter.hasNext()) {
                    c = iter.next();
                    try {
                        c.onCreate(pluginContext, activity.getIntent(), mapView);
                    } catch (Exception e) {
                        Log.w(TAG, "Unhandled exception trying to create overlays MapComponent", e);
                        iter.remove();
                    }
                }

                // Start all components
                for (MapComponent component : overlays) {
                    component.onStart(pluginContext, mapView);
                }

                // Set up USB receiver
                setupUsbReceiver(activity);
                
                // Check for USB devices
                checkForUsbDevices(activity);
            } else {
                Log.e(TAG, "Could not get activity reference");
            }
        }
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        
        // the plugin is stopping, remove the button from the toolbar
        if (uiService != null) {
            uiService.removeToolbarItem(toolbarItem);
        }

        MapView mapView = MapView.getMapView();
        
        // Stop all components
        if (mapView != null) {
            for (MapComponent c : overlays) {
                c.onStop(pluginContext, mapView);
            }
        }

        // Clean up USB receiver
        if (m_usbReceiver != null && pluginContext != null) {
            try {
                pluginContext.getApplicationContext().unregisterReceiver(m_usbReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver not registered", e);
            }
        }

        // Destroy all components
        if (mapView != null) {
            for (MapComponent c : overlays) {
                c.onDestroy(pluginContext, mapView);
            }
        }
    }

    private void setupUsbReceiver(final Activity activity) {
        m_usbReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) || 
                    UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                    try {
                        Log.i(TAG, "USB device attached!");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) || 
                           UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                    Log.i(TAG, "USB device detached!");
                } else if (ACTION_USB_PERMISSION.equals(action)) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(TAG, "USB Permission granted");
                    } else {
                        Log.i(TAG, "USB Permission Denied");
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);

        activity.getApplicationContext().registerReceiver(m_usbReceiver, filter);
    }

    private void checkForUsbDevices(final Activity activity) {
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) activity.getApplicationContext()
                .getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber()
                .findAllDrivers(manager);
        
        if (availableDrivers.isEmpty()) {
            Log.i(TAG, "availableDrivers is empty");
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);

        Log.d(TAG, "Permission = " + manager.hasPermission(driver.getDevice()));

        // Checking for USB device permission is granted
        if (manager.hasPermission(driver.getDevice())) {
            Log.d(TAG, "Service started");
        } else {
            try {
                int flags = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags = PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(
                        activity.getApplicationContext(), 0, 
                        new Intent(ACTION_USB_PERMISSION), flags);
                manager.requestPermission(driver.getDevice(), usbPermissionIntent);
            } finally {
                Log.d(TAG, "Service started");
            }
        }
    }
}