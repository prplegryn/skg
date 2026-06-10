package com.prplegryn.skg;

import android.app.Application;

public final class SkgApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLogger.install(this);
    }
}
