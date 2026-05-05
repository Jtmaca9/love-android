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

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.Keep;

/**
 * Manages discovery and lifecycle of secondary displays for dual-screen devices
 * like the Ayn Thor. Uses Android's DisplayManager and Presentation APIs.
 */
public class DualScreenManager {
    private static final String TAG = "DualScreenManager";

    private final Activity activity;
    private final DisplayManager displayManager;
    private Display[] displays;
    private Display secondaryDisplay;
    private SecondaryPresentation presentation;

    private static native void nativeOnSecondaryTouch(int action, float x, float y, int pointerId, float pressure);

    public DualScreenManager(Activity activity) {
        this.activity = activity;
        this.displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        refreshDisplays();
    }

    private void refreshDisplays() {
        displays = displayManager.getDisplays();
        secondaryDisplay = null;

        Log.d(TAG, "Found " + displays.length + " display(s)");
        for (Display display : displays) {
            Log.d(TAG, "  Display " + display.getDisplayId() + ": " + display.getName()
                + " (" + display.getMode().getPhysicalWidth() + "x"
                + display.getMode().getPhysicalHeight() + ")");
        }

        if (displays.length >= 2) {
            // The secondary display is any display that isn't the default
            for (Display display : displays) {
                if (display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    secondaryDisplay = display;
                    break;
                }
            }
        }
    }

    @Keep
    public int getDisplayCount() {
        return displays != null ? displays.length : 1;
    }

    @Keep
    public int getMainWidth() {
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        return defaultDisplay != null ? defaultDisplay.getMode().getPhysicalWidth() : 0;
    }

    @Keep
    public int getMainHeight() {
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        return defaultDisplay != null ? defaultDisplay.getMode().getPhysicalHeight() : 0;
    }

    @Keep
    public int getExtWidth() {
        if (secondaryDisplay == null) return 0;
        return secondaryDisplay.getMode().getPhysicalWidth();
    }

    @Keep
    public int getExtHeight() {
        if (secondaryDisplay == null) return 0;
        return secondaryDisplay.getMode().getPhysicalHeight();
    }

    @Keep
    public float getMainRefreshRate() {
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        return defaultDisplay != null ? defaultDisplay.getRefreshRate() : 0f;
    }

    @Keep
    public float getExtRefreshRate() {
        if (secondaryDisplay == null) return 0f;
        return secondaryDisplay.getRefreshRate();
    }

    @Keep
    public boolean initSecondary() {
        if (secondaryDisplay == null) {
            Log.w(TAG, "No secondary display available");
            return false;
        }

        if (presentation != null) {
            Log.d(TAG, "Secondary already initialized");
            return true;
        }

        // Presentation must be shown on the UI thread
        final boolean[] result = {false};
        final Object lock = new Object();

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                presentation = new SecondaryPresentation(activity, secondaryDisplay);
                presentation.setTouchForwarder((action, x, y, pointerId, pressure) -> {
                    nativeOnSecondaryTouch(action, x, y, pointerId, pressure);
                });
                presentation.show();
                result[0] = true;
                Log.d(TAG, "Secondary Presentation shown on display: "
                    + secondaryDisplay.getName());
            } catch (Exception e) {
                Log.e(TAG, "Failed to show SecondaryPresentation", e);
                presentation = null;
                result[0] = false;
            }
            synchronized (lock) {
                lock.notify();
            }
        });

        synchronized (lock) {
            try {
                lock.wait(5000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted waiting for Presentation", e);
                return false;
            }
        }

        // Wait for the surface to be ready
        if (result[0] && presentation != null) {
            for (int i = 0; i < 50; i++) {
                if (presentation.isSurfaceReady()) {
                    Log.d(TAG, "Secondary surface ready");
                    return true;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            Log.w(TAG, "Timed out waiting for secondary surface");
        }

        return result[0];
    }

    @Keep
    public void deinitSecondary() {
        if (presentation != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                presentation.dismiss();
                presentation = null;
                Log.d(TAG, "Secondary Presentation dismissed");
            });
        }
    }

    @Keep
    public Surface getSecondarySurface() {
        if (presentation != null) {
            return presentation.getSurface();
        }
        return null;
    }

    @Keep
    public int getSecondarySurfaceWidth() {
        if (presentation != null) {
            return presentation.getSurfaceWidth();
        }
        return getExtWidth();
    }

    @Keep
    public int getSecondarySurfaceHeight() {
        if (presentation != null) {
            return presentation.getSurfaceHeight();
        }
        return getExtHeight();
    }

    @Keep
    public boolean isSecondaryAvailable() {
        return secondaryDisplay != null;
    }
}
