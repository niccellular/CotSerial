package com.atakmap.android.cotserial;

import android.content.Context;

import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotEvent;

import com.atakmap.coremap.log.Log;
//import com.hoho.android.usbserial.driver.SerialTimeoutException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class OutboundMessageHandler implements CommsMapComponent.PreSendProcessor{

    private static final String TAG = OutboundMessageHandler.class.getSimpleName();;

    private Context pluginContext;

    private static final int WRITE_WAIT_MILLIS = 2000;

    private cotserialDropDownReceiver ddr;

    public OutboundMessageHandler(Context pluginContext, cotserialDropDownReceiver ddr) {
        this.pluginContext = pluginContext;
        this.ddr = ddr;
        CommsMapComponent.getInstance().registerPreSendProcessor(this);
    }

    @Override
    public void processCotEvent(CotEvent cotEvent, String[] strings) {
        Log.i(TAG, cotEvent.toString());
        // ftdi not enabled
        if (!ddr.enabled || ddr.port == null) {
            Log.i(TAG, "Not enabled processCotEvent");
            return;
        }
        try {
            ddr.port.write(cotEvent.toString().getBytes(StandardCharsets.UTF_8), WRITE_WAIT_MILLIS);
            Log.i(TAG, "Sent");
        //} catch (SerialTimeoutException e) {
        //    Log.e(TAG, "Write failed due to timeout");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
