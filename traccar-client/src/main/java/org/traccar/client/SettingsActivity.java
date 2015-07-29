/*
 * Copyright 2012 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.client;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * Main user interface
 */
@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity {
    //TODO: move the key to the string XML file
    public static final String LOG_TAG = "PreferenceActivity";

    //Tracking
    public static final String KEY_ID = "id";
    public static final String KEY_INTERVAL = "interval";
    public static final String KEY_PROVIDER = "provider";
    //Backends
    //Inet
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_PORT = "port";
    public static final String KEY_EXTENDED = "extended";
    public static final String KEY_TRACKING_INET = "tracking_inet";
    //Sms
    public static final String KEY_TRACKING_SMS = "tracking_sms";
    public static final String KEY_TRACKING_SMS_NUMBER = "tracking_sms_number";
    //Notifications
    public static final String KEY_NOTIFICATION_SMS = "notification_sms";
    public static final String KEY_NOTIFICATION_SMS_NUMBER = "notification_number";
    public static final String KEY_BATTERY = "battery";
    //Service
    public static final String KEY_STATUS = "status";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
        if (sharedPreferences.getBoolean(KEY_STATUS, false))
            startService(new Intent(this, TraccarService.class));
        findPreference(KEY_ID).setSummary(sharedPreferences.getString(KEY_ID, null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(
                preferenceChangeListener);
    }

    @Override
    protected void onPause() {
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                preferenceChangeListener);
        super.onPause();
    }

    OnSharedPreferenceChangeListener preferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(KEY_STATUS)) {
                if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
                    startService(new Intent(SettingsActivity.this, TraccarService.class));
                    Log.d(LOG_TAG, "Starting service");
                } else {
                    stopService(new Intent(SettingsActivity.this, TraccarService.class));
                    Log.d(LOG_TAG, "Stopping service");
                }
            } else if (key.equals(KEY_ID)) {
                findPreference(KEY_ID).setSummary(sharedPreferences.getString(KEY_ID, null));
            }

            //getPreferenceScreen().removeAll();
            //addPreferencesFromResource(R.xml.preferences);
        }
    };


    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (TraccarService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


}
