/*
 * Copyright (C) 2017 The CandyRom Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.candy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;

import java.util.List;
import java.util.Locale;

public class CandyUtils {

    public static boolean isAvailableApp(String packageName, Context context) {
        Context mContext = context;
        final PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            int enabled = pm.getApplicationEnabledSetting(packageName);
            return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isChineseLanguage() {
       return Resources.getSystem().getConfiguration().locale.getLanguage().startsWith(
               Locale.CHINESE.getLanguage());
    }

    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (NameNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        return isPackageInstalled(context, pkg, true);
    }

    // Omni Switch Constants

    /**
     * Package name of the omnniswitch app
     */
    public static final String APP_PACKAGE_NAME = "org.omnirom.omniswitch";

    /**
     * Intent broadcast action for showing the omniswitch overlay
     */
    public static final String ACTION_SHOW_OVERLAY = APP_PACKAGE_NAME + ".ACTION_SHOW_OVERLAY";

    /**
     * Intent broadcast action for hiding the omniswitch overlay
     */
    public static final String ACTION_HIDE_OVERLAY = APP_PACKAGE_NAME + ".ACTION_HIDE_OVERLAY";

    /**
     * Intent broadcast action for toogle the omniswitch overlay
     */
    public static final String ACTION_TOGGLE_OVERLAY = APP_PACKAGE_NAME + ".ACTION_TOGGLE_OVERLAY";

    /**
     * Intent for launching the omniswitch settings actvity
     */
    public static Intent INTENT_LAUNCH_APP = new Intent(Intent.ACTION_MAIN)
            .setClassName(APP_PACKAGE_NAME, APP_PACKAGE_NAME + ".SettingsActivity");

    // On-The-Go-Mode

    /**
     * Package name of onthego app
     */
    public static final String ACTION_ONTHEGO_TOGGLE = "action_onthego_toggle";

    public static void processAction(final Context context, final String action) {

        if (action == null || action.isEmpty()) {
            return;
        }

        if (ACTION_ONTHEGO_TOGGLE.equals(action)) {
            actionOnTheGoToggle(context);
        }

    }

    private static void actionOnTheGoToggle(final Context context) {
        final ComponentName cn = new ComponentName("com.android.systemui",
                "com.android.systemui.candy.onthego.OnTheGoService");
        final Intent startIntent = new Intent();
        startIntent.setComponent(cn);
        startIntent.setAction("start");
        context.startService(startIntent);
    }

    /**
     * Check if system has a camera.
     *
     * @param context
     * @return
     */
    public static boolean hasCamera(final Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm != null && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    /**
     * Check if system has a front camera.
     *
     * @param context
     * @return
     */
    public static boolean hasFrontCamera(final Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm != null && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }
}
