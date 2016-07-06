package com.deepdownstudios.skinshaderdemo;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;


public class CanvasActivity extends Activity {

    private CanvasRenderer mRenderer;

    // The IDE complains about SYSTEM_UI and the min SDK but the values are inlined
    // at compile time and mean nothing to lower SDK versions.
    @SuppressLint("InlinedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make sure we have GLES 2.0.  You always will if targeting API 8 or greater.
        // In a real app, you could rely on the app store
        // to confirm that the device has support by putting
        // <uses-feature android:glEsVersion="0x00020000" android:required="true" />
        // in your manifest.  Then, you get spammed by angry users.
        if (detectOpenGLES20()) {
            // Call `setEGLConfigChooser` if you wish to choose a pixel format for the window.
            // Default is R8-G8-B8-D16-S0 (last are depth and stencil bit-size).

            // This view is just instructions
            TextView messageView = new TextView(this);
            messageView.setTextColor(0xFFAACC11);   // ARGB
            messageView.setTextSize(20.0f);
            messageView.setText(R.string.canvas_msg);

            // This view shows the mModelName parameters
            TextView modelInfoView = new TextView(this);
            modelInfoView.setTextColor(0xFF11AACC);   // ARGB
            modelInfoView.setTextSize(20.0f);

            LinearLayout messages = new LinearLayout(this);
            messages.setOrientation(LinearLayout.VERTICAL);

            FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            messages.setLayoutParams(params);

            messages.addView(modelInfoView);
            messages.addView(messageView);

            mGLSurfaceView = new GLSurfaceView(this);
            mGLSurfaceView.setEGLContextClientVersion(2);           // GLES 2.0
            mRenderer = new CanvasRenderer(mGLSurfaceView, modelInfoView);
            mGLSurfaceView.setRenderer(mRenderer);
            mGLSurfaceView.setOnClickListener(mRenderer);

            FrameLayout contentView = new FrameLayout(this);
            contentView.addView(mGLSurfaceView);
            contentView.addView(messages);

            setContentView(contentView);

            // Hide UI... ?
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }

            contentView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN            // hide sticky
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY      // Status/nav are timed to expire
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);     // Hide nav
            getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN            // hide status
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY      // Status/nav are timed to expire
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);     // Hide nav
                }
            });
        } else {
            // Display an error message to the user.
            setContentView(R.layout.gles_2_required);
        }
    }

    private boolean detectOpenGLES20() {
        ActivityManager am =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        return (info.reqGlEsVersion >= 0x20000);
    }

    @Override
    protected void onResume() {
        // Ideally a game should implement onResume() and onPause()
        // to take appropriate action when the activity looses focus
        super.onResume();

        if (mGLSurfaceView == null)
            return;

        mGLSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        // Ideally a game should implement onResume() and onPause()
        // to take appropriate action when the activity looses focus
        super.onPause();
        if (mGLSurfaceView != null)
            mGLSurfaceView.onPause();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (mRenderer != null) {
            mRenderer.onTrimMemory(level);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN                      // hide status
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY      // Status/nav are timed to expire
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);     // Hide nav
    }

    private GLSurfaceView mGLSurfaceView;
}


