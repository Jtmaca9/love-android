/*
 * Copyright (c) 2006-2026 LOVE Development Team
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package org.love2d.android;

import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Keep;

/**
 * Manages a Presentation on the secondary display, providing a SurfaceView
 * whose backing Surface can be used to create an EGL rendering surface
 * from native code.
 */
public class SecondaryPresentation extends Presentation implements SurfaceHolder.Callback {
    private static final String TAG = "SecondaryPresentation";

    private SurfaceView surfaceView;
    private Surface surface;
    private boolean surfaceReady = false;
    private int surfaceWidth = 0;
    private int surfaceHeight = 0;
    private TouchForwarder touchForwarder;

    public interface TouchForwarder {
        void onSecondaryTouch(int action, float x, float y, int pointerId, float pressure);
    }

    public SecondaryPresentation(Context context, Display display) {
        super(context, display);
    }

    public void setTouchForwarder(TouchForwarder forwarder) {
        this.touchForwarder = forwarder;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Remove title bar and go fullscreen
        Window w = getWindow();
        if (w != null) {
            w.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );

            if (android.os.Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
        }

        surfaceView = new SurfaceView(getContext()) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (touchForwarder != null) {
                    int pointerIndex = event.getActionIndex();
                    int pointerId = event.getPointerId(pointerIndex);
                    touchForwarder.onSecondaryTouch(
                        event.getActionMasked(),
                        event.getX(pointerIndex),
                        event.getY(pointerIndex),
                        pointerId,
                        event.getPressure(pointerIndex)
                    );
                    return true;
                }
                return super.onTouchEvent(event);
            }
        };

        surfaceView.getHolder().addCallback(this);
        setContentView(surfaceView);

        // Hide system UI for true fullscreen
        surfaceView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        Log.d(TAG, "SecondaryPresentation created on display: " + getDisplay().getName()
            + " (" + getDisplay().getMode().getPhysicalWidth()
            + "x" + getDisplay().getMode().getPhysicalHeight() + ")");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        surfaceReady = true;
        Log.d(TAG, "Secondary surface created");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surface = holder.getSurface();
        surfaceWidth = width;
        surfaceHeight = height;
        Log.d(TAG, "Secondary surface changed: " + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surface = null;
        surfaceReady = false;
        Log.d(TAG, "Secondary surface destroyed");
    }

    @Keep
    public Surface getSurface() {
        return surface;
    }

    @Keep
    public boolean isSurfaceReady() {
        return surfaceReady;
    }

    @Keep
    public int getSurfaceWidth() {
        if (surfaceWidth > 0) return surfaceWidth;
        return getDisplay().getMode().getPhysicalWidth();
    }

    @Keep
    public int getSurfaceHeight() {
        if (surfaceHeight > 0) return surfaceHeight;
        return getDisplay().getMode().getPhysicalHeight();
    }
}
