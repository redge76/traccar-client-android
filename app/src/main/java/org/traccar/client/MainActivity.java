/*
 * Copyright 2012 - 2015 Anton Tananaev (anton.tananaev@gmail.com)
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

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

@SuppressWarnings("deprecation")
public class MainActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    public static final String KEY_DEVICE = "id";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_PORT = "port";
    public static final String KEY_INTERVAL = "interval";
    public static final String KEY_PROVIDER = "provider";
    public static final String KEY_STATUS = "status";
    public static final String KEY_FOREGROUND = "foreground";
    public static final String KEY_HTTP_BACKEND_STATUS = "http_backend_status";
    public static final String KEY_SMS_BACKEND_STATUS = "sms_backend_status";
    public static final String KEY_SMS_BACKEND_NO_SEND_TIME_LIMIT = "sms_backend_no_send_time_limit";
    public static final String KEY_SMS_BACKEND_NUMBER = "sms_backend_number";
    public static final String KEY_SMS_BACKEND_INTERVAL = "sms_backend_interval";

    private static final int PERMISSIONS_REQUEST_DEVICE = 1;
    private static final int PERMISSIONS_REQUEST_LOCATION = 2;

    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        addPreferencesFromResource(R.xml.preferences);
        initPreferences();

        if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
            checkPermissionsAndStartService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        if (!sharedPreferences.contains(KEY_DEVICE)) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                initDeviceId(true);
            } else {
                requestPermissions(new String[] { Manifest.permission.READ_PHONE_STATE }, PERMISSIONS_REQUEST_DEVICE);
            }
        } else {
            findPreference(KEY_DEVICE).setSummary(sharedPreferences.getString(KEY_DEVICE, null));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void setPreferencesEnabled(boolean enabled) {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.findPreference(KEY_DEVICE).setEnabled(enabled);
        preferenceScreen.findPreference(KEY_ADDRESS).setEnabled(enabled);
        preferenceScreen.findPreference(KEY_PORT).setEnabled(enabled);
        preferenceScreen.findPreference(KEY_INTERVAL).setEnabled(enabled);
        preferenceScreen.findPreference(KEY_PROVIDER).setEnabled(enabled);
        preferenceScreen.findPreference(KEY_FOREGROUND).setEnabled(enabled);
    }

    private void updateSummary(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "updateSummary(): Updating summary of " + key);
        findPreference(key).setSummary(String.valueOf(sharedPreferences.getString(key, "")));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged(): Preferences changed");
        switch (key) {
            case KEY_STATUS:
                if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
                    checkPermissionsAndStartService();
                } else {
                    stopService(new Intent(this, TrackingService.class));
                    setPreferencesEnabled(true);
                }
                break;
            case KEY_DEVICE:
            case KEY_ADDRESS:
            case KEY_PORT:
            case KEY_INTERVAL:
            case KEY_SMS_BACKEND_NUMBER:
            case KEY_SMS_BACKEND_NO_SEND_TIME_LIMIT:
                updateSummary(sharedPreferences, key);
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.status) {
            startActivity(new Intent(this, StatusActivity.class));
            return true;
        } else if (item.getItemId() == R.id.about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initPreferences() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    }

    private void initDeviceId(boolean devicePermission) {
        String id;
        if (devicePermission) {
            id = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        } else {
            id = String.valueOf(new Random().nextInt(900000) + 100000);
        }
        sharedPreferences.edit().putString(KEY_DEVICE, id).commit();
        EditTextPreference preference = (EditTextPreference) findPreference(KEY_DEVICE);
        preference.setText(id);
        preference.setSummary(id);
        updateSummary(sharedPreferences, KEY_DEVICE);
        updateSummary(sharedPreferences, KEY_ADDRESS);
        updateSummary(sharedPreferences, KEY_PORT);
        updateSummary(sharedPreferences, KEY_INTERVAL);
        updateSummary(sharedPreferences, KEY_PROVIDER);
        updateSummary(sharedPreferences, KEY_SMS_BACKEND_NUMBER);
        updateSummary(sharedPreferences, KEY_SMS_BACKEND_NO_SEND_TIME_LIMIT);
    }

    private void checkPermissionsAndStartService() {
        Set<String> missingPermissions = new HashSet<String>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (missingPermissions.isEmpty()) {
            setPreferencesEnabled(false);
            startService(new Intent(this, TrackingService.class));
        } else {
            requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), PERMISSIONS_REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_DEVICE) {
            initDeviceId(grantResults[0] == PackageManager.PERMISSION_GRANTED);
        } else if (requestCode == PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && (permissions.length < 2 || grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                setPreferencesEnabled(false);
                startService(new Intent(this, TrackingService.class));
            } else {
                sharedPreferences.edit().putBoolean(KEY_STATUS, false).commit();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    TwoStatePreference preference = (TwoStatePreference) findPreference(KEY_STATUS);
                    preference.setChecked(false);
                } else {
                    CheckBoxPreference preference = (CheckBoxPreference) findPreference(KEY_STATUS);
                    preference.setChecked(false);
                }
            }
        }
    }

}
