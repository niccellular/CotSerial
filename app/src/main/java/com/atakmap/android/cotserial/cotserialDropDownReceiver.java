
package com.atakmap.android.cotserial;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.view.View;
import android.widget.NumberPicker;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.gui.AutoSizeButton;
import com.atakmap.app.BuildConfig;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.cotserial.plugin.R;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.model.ModelInfoFactory;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class cotserialDropDownReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String TAG = "cotserialDropDownReceiver";

    public static final String SHOW_PLUGIN = "com.atakmap.android.cotserial.SHOW_PLUGIN";
    private final View templateView;
    private final Context pluginContext;
    private Activity activity;

    private AutoSizeButton enableBtn;

    public UsbSerialPort port;

    public static final String ACTION_USB_PERMISSION = BuildConfig.APPLICATION_ID + ".ACTION_USB_PERMISSION";

    public boolean enabled = false;

    private Thread reader;

    private boolean permissionGranted = false;

    private NumberPicker baudrate;

    private String[] strings = {"2400", "4800", "9600", "14400", "19200", "28800", "38400", "57600", "115200", "230400", "460800"};

    /**************************** CONSTRUCTOR *****************************/

    public cotserialDropDownReceiver(final MapView mapView,
                                   final Context context, Activity activity) {
        super(mapView);
        this.pluginContext = context;
        this.activity = activity;
        // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
        // In this case, using it is not necessary - but I am putting it here to remind
        // developers to look at this Inflator
        templateView = PluginLayoutInflater.inflate(context,
                R.layout.main_layout, null);

        enableBtn = templateView.findViewById(R.id.enable);

        baudrate = templateView.findViewById(R.id.baudrate);

        baudrate.setMinValue(0);
        baudrate.setMaxValue(strings.length - 1);
        baudrate.setDisplayedValues(strings);
        baudrate.setWrapSelectorWheel(false);

        baudrate.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int oldVal, int newVal) {
                Log.i(TAG, String.format("oldVal: %d newVal: %d baud: %s", oldVal, newVal, strings[newVal]));

            }
        });
    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_PLUGIN)) {

            Log.d(TAG, "showing plugin drop down");
            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);

            enableBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (enableBtn.getText().equals("Enable *OFF*")) {
                        setUp();
                        enableBtn.setText("Enable *ON*");
                        enabled = true;
                        reader = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                byte buffer[] = new byte[4096];
                                while (true) {
                                    try {
                                        if (!enabled) break;
                                        if (port == null) continue;
                                        int bytesRead = port.read(buffer, 2000);
                                        if (bytesRead > 0) {
                                            String cot = new String(buffer);
                                            CotEvent event = new CotEvent(CotEvent.parse(cot));
                                            if (event.isValid()) {
                                                CotMapComponent.getInternalDispatcher().dispatch(event);
                                            }
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                }
                            }
                        });
                        reader.start();
                    } else {
                        try {
                            tearDown();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        enableBtn.setText("Enable *OFF*");
                        enabled = false;
                    }
                }
            });
        }
    }

    private void tearDown() throws IOException {
        if (port != null)
            port.close();
    }

    private void setUp() {

        Log.i(TAG, "Begin Setup");
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getActivity().getApplicationContext().getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
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
                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity().getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
                manager.requestPermission(driver.getDevice(), usbPermissionIntent);
            } finally {
                Log.d(TAG, "Service started");
            }
        }

        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            Log.i(TAG, "connection == null");
            return;
        }

        port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            port.open(connection);
            port.setParameters(Integer.parseInt(strings[baudrate.getValue()]), 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "End Setup");
    }
    private Activity getActivity() {
        return this.activity;
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }
}
