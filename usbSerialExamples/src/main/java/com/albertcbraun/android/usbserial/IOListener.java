package com.albertcbraun.android.usbserial;

import android.util.Log;

import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;

class IOListener implements SerialInputOutputManager.Listener {
    private static final String TAG = IOListener.class.getSimpleName();

    @Override
    public void onRunError(Exception e) {
        Log.e(TAG, "Runner stopped.", e);
    }

    @Override
    public void onNewData(final byte[] data) {
        try {
            Log.w(TAG, "received new data! should we have?");
            //noinspection CharsetObjectCanBeUsed
            Log.w(TAG, new String(data, "UTF-8"));
        } catch (IOException ioe) {
            Log.e(TAG,"Could not log byte array", ioe);
        }
    }

}
