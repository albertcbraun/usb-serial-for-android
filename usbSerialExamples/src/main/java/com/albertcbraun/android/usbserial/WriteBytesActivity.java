/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.albertcbraun.android.usbserial;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.examples.R;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Write a byte to a USB Serial port
 * @author albertcbraun
 */
public class WriteBytesActivity extends Activity {

    private final String TAG = WriteBytesActivity.class.getSimpleName();
    private ExecutorService serialIOExecutor;
    private ScheduledExecutorService writeBytesExecutor;
    private UsbSerialPort usbSerialPort = null;
    private TextView statusTextView;
    private ToggleButton writeBytesToggle;
    private SerialInputOutputManager serialInputOutputManager;

    private final SerialInputOutputManager.Listener serialIOManagerListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
            try {
                Log.w(TAG, "received new data! should we have?");
                Log.w(TAG, new String(data, "UTF-8"));
            } catch (IOException ioe) {
                Log.e(TAG,"Could not log byte array", ioe);
            }
        }
    };

    private final Runnable writeByteTask = new Runnable() {

        @Override
        public void run() {
            if (serialInputOutputManager != null) {
                serialInputOutputManager.writeAsync(new byte[]{0});
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.write_a_byte);
        statusTextView = findViewById(R.id.status);
        writeBytesToggle = findViewById(R.id.write_bytes_toggle);
        writeBytesToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (writeBytesExecutor == null) {
                    writeBytesExecutor = Executors.newSingleThreadScheduledExecutor();
                }
                if (isChecked) {
                    writeBytesExecutor.scheduleAtFixedRate(writeByteTask, 0L,
                            100L, TimeUnit.MILLISECONDS);
                } else {
                    writeBytesExecutor.shutdownNow();
                    writeBytesExecutor = null;
                }
            }
        });
        UsbManager usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers.size() > 0) {
            UsbSerialDriver driver = drivers.get(0);
            usbSerialPort = driver.getPorts().get(0);
            startIoManager();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (usbSerialPort != null) {
            try {
                usbSerialPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            usbSerialPort = null;
        }
        finish();
    }

//    void showStatus(TextView theTextView, String theLabel, boolean theValue){
//        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
//        theTextView.append(msg);
//    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + usbSerialPort);
        if (usbSerialPort == null) {
            statusTextView.setText("No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(usbSerialPort.getDriver().getDevice());
            if (connection == null) {
                statusTextView.setText("Opening device failed");
                return;
            }

            try {
                usbSerialPort.open(connection);
                usbSerialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

//                showStatus(mDumpTextView, "CD  - Carrier Detect", usbSerialPort.getCD());
//                showStatus(mDumpTextView, "CTS - Clear To Send", usbSerialPort.getCTS());
//                showStatus(mDumpTextView, "DSR - Data Set Ready", usbSerialPort.getDSR());
//                showStatus(mDumpTextView, "DTR - Data Terminal Ready", usbSerialPort.getDTR());
//                showStatus(mDumpTextView, "DSR - Data Set Ready", usbSerialPort.getDSR());
//                showStatus(mDumpTextView, "RI  - Ring Indicator", usbSerialPort.getRI());
//                showStatus(mDumpTextView, "RTS - Request To Send", usbSerialPort.getRTS());

            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                statusTextView.setText("Error opening device: " + e.getMessage());
                try {
                    usbSerialPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                usbSerialPort = null;
                return;
            }
            statusTextView.setText("Serial device: " + usbSerialPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (serialInputOutputManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            serialInputOutputManager.stop();
            serialInputOutputManager = null;
            if (serialIOExecutor != null) {
                serialIOExecutor.shutdownNow();
                serialIOExecutor = null;
            }
        }
    }

    private void startIoManager() {
        if (usbSerialPort == null) {
            statusTextView.setText("Cannot write bytes. a USB serial port is not available.");
        } else {
            Log.i(TAG, "Starting io manager ..");
            serialInputOutputManager = new SerialInputOutputManager(usbSerialPort, serialIOManagerListener);
            if (serialIOExecutor == null) {
                serialIOExecutor = Executors.newSingleThreadExecutor();
            }
            serialIOExecutor.submit(serialInputOutputManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }


}
