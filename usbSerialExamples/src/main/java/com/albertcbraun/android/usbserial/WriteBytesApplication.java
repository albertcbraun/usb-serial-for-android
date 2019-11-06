package com.albertcbraun.android.usbserial;

import android.app.Application;

public class WriteBytesApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

// TODO: set LeakCanary config customizations here if desired
//        if (BuildConfig.DEBUG) {
//            AppWatcher.Config myConfig = AppWatcher.INSTANCE.getConfig();
//            myConfig ...
//            AppWatcher.INSTANCE.setConfig();
//        }

    }
}
