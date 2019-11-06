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
 * Repeatedly write a byte to a USB Serial port
 */
public class WriteBytesActivity extends Activity {

    private static final String TAG = WriteBytesActivity.class.getSimpleName();
    private static final long INITIAL_WRITE_DELAY = 0L;
    private static final long WRITE_DELAY_INTERVAL = 100L;

    private volatile SerialInputOutputManager serialIOManager;
    private UsbSerialPort usbSerialPort = null;
    private ExecutorService serialIOExecutor;
    private ScheduledExecutorService writeBytesExecutor;
    private TextView statusTextView;
    private final SerialInputOutputManager.Listener ioListener = new IOListener();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.write_a_byte);
        statusTextView = findViewById(R.id.status);
        ToggleButton writeBytesToggle = findViewById(R.id.write_bytes_toggle);
        writeBytesToggle.setOnCheckedChangeListener(new ToggleButtonListener());
        UsbManager usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers.size() > 0) {
            UsbSerialDriver driver = drivers.get(0);
            usbSerialPort = driver.getPorts().get(0);
            setupIO();
        } else {
            Log.v(TAG, "USB Serial Probe did not obtain serial USB driver");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        tearDownIO();
        try {
            if (usbSerialPort != null)  usbSerialPort.close();
        } catch(IOException ioException) {
            Log.e(TAG, "Could not close USB Serial Port", ioException);
        } finally {
            usbSerialPort = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + usbSerialPort);
        if (usbSerialPort == null) {
            statusTextView.setText(R.string.no_serial_device);
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection connection = usbManager.openDevice(usbSerialPort.getDriver().getDevice());
            if (connection == null) {
                statusTextView.setText(R.string.opening_device_failed);
                return;
            }

            try {
                usbSerialPort.open(connection);
                usbSerialPort.setParameters(115200, 8,
                        UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                statusTextView.setText(getString(R.string.serial_device_format,
                        usbSerialPort.getClass().getSimpleName()));

//                showStatus(mDumpTextView, "CD  - Carrier Detect", usbSerialPort.getCD());
//                showStatus(mDumpTextView, "CTS - Clear To Send", usbSerialPort.getCTS());
//                showStatus(mDumpTextView, "DSR - Data Set Ready", usbSerialPort.getDSR());
//                showStatus(mDumpTextView, "DTR - Data Terminal Ready", usbSerialPort.getDTR());
//                showStatus(mDumpTextView, "DSR - Data Set Ready", usbSerialPort.getDSR());
//                showStatus(mDumpTextView, "RI  - Ring Indicator", usbSerialPort.getRI());
//                showStatus(mDumpTextView, "RTS - Request To Send", usbSerialPort.getRTS());

            } catch (IOException ioException) {
                Log.e(TAG, "Error setting up device.", ioException);
                statusTextView.setText(getString(R.string.error_opening_device_format,
                        ioException.getMessage()));
                try {
                    usbSerialPort.close();
                } catch (IOException ioException2) {
                    Log.e(TAG, "Could not close USB Serial Port", ioException2);
                } finally {
                    usbSerialPort = null;
                }
            }
        }
        onDeviceStateChange();
    }

    private void tearDownIO() {
        Log.v(TAG, "tearDownIO");
        if (serialIOExecutor != null) {
            serialIOExecutor.shutdownNow();
            serialIOExecutor = null;
        }
        if (writeBytesExecutor != null) {
            writeBytesExecutor.shutdownNow();
            writeBytesExecutor = null;
        }
        if (serialIOManager != null) {
            serialIOManager.stop();
            serialIOManager = null;
        }
    }

    private void setupIO() {
        Log.v(TAG, "setupIO");
        if (usbSerialPort == null) {
            statusTextView.setText(R.string.cannot_write_bytes);
        } else {
            Log.v(TAG, "Starting io manager ..");
            serialIOManager = new SerialInputOutputManager(usbSerialPort, ioListener);
            if (serialIOExecutor == null) {
                serialIOExecutor = Executors.newSingleThreadExecutor();
            }
            serialIOExecutor.submit(serialIOManager);
        }
    }

    private void onDeviceStateChange() {
        tearDownIO();
        setupIO();
    }

    private class WriteByteTask implements Runnable {
        @Override
        public void run() {
            Log.v(TAG, "WriteByteTask run");
            if (serialIOManager != null) {
                serialIOManager.writeAsync(new byte[]{0});
            }
        }
    }

    private class ToggleButtonListener implements ToggleButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                if (serialIOManager == null) {
                    Log.v(TAG, "serialIOManager is null. setting button to off again.");
                    buttonView.setChecked(false);
                } else {
                    if (writeBytesExecutor == null) {
                        writeBytesExecutor = Executors.newSingleThreadScheduledExecutor();
                    }
                    writeBytesExecutor.scheduleAtFixedRate(new WriteByteTask(), INITIAL_WRITE_DELAY,
                            WRITE_DELAY_INTERVAL, TimeUnit.MILLISECONDS);
                }
            } else {
                if (writeBytesExecutor != null) {
                    writeBytesExecutor.shutdownNow();
                }
                writeBytesExecutor = null;
            }
        }
    }
}
