package com.deepdownstudios.skinshaderdemo;

import android.app.Application;

import java.lang.ref.WeakReference;

/**
 * Created by davidp on 6/29/16.
 * Provides gloabl context to use to fetch resources (shaders).
 */
public class CanvasApplication extends Application {
    private static WeakReference<CanvasApplication> thisApp = null;

    public void onCreate() {
        // Technically the Android lifecycle's process-kill semantics
        // make the WeakReference unnecessary but this is much better architecture.
        thisApp = new WeakReference<>(this);
        super.onCreate();
    }

    public static CanvasApplication getInstance() {
        return thisApp.get();
    }
}
