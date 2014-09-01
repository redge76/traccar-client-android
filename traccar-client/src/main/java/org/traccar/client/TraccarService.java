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

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * Background service
 */
public class TraccarService extends Service {

    public static final String LOG_TAG = "Traccar.TraccarService";

    private String id;
    private String address;
    private int port;
    private int interval;
    private String provider;
    private boolean extended;

    private SharedPreferences sharedPreferences;
    private ClientController clientController;
    private PositionProvider positionProvider;

    private Location lastLocation;
    private SmsConnection smsCon;
    private boolean smsLogging;
    private String logNumber;
    
    private WakeLock wakeLock;

    @Override
    public void onCreate() {
        StatusActivity.addMessage(getString(R.string.status_service_create));

        lastLocation = null;
        smsCon = new SmsConnection(this);
        smsLogging = false;
        logNumber = "";

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.acquire();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            id = sharedPreferences.getString(TraccarActivity.KEY_ID, null);
            address = sharedPreferences.getString(TraccarActivity.KEY_ADDRESS, null);
            provider = sharedPreferences.getString(TraccarActivity.KEY_PROVIDER, null);
            port = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_PORT, null));
            interval = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_INTERVAL, null));
            extended = sharedPreferences.getBoolean(TraccarActivity.KEY_EXTENDED, false);

            logNumber = sharedPreferences.getString(TraccarActivity.KEY_NUMBER, null);
            smsLogging = sharedPreferences.getBoolean(TraccarActivity.KEY_SMSLOGGING, false);
        } catch (Exception error) {
            Log.w(LOG_TAG, error);
        }

        clientController = new ClientController(this, address, port, Protocol.createLoginMessage(id));
        clientController.start();

        positionProvider = new PositionProvider(this, provider, interval * 1000, positionListener);
        positionProvider.startUpdates();

        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if(intent != null && intent.getAction() != null && intent.getAction().equals(ACTION_RECEIVE_SMS))
        {
            Bundle bundle = intent.getExtras();
            String Number = (String) bundle.get("Number");
            String Message = (String) bundle.get("Message");

            String msg = "Received SMS from: " + Number;
            msg += "\nMessage: " + Message;

            StatusActivity.addMessage(msg);
            handleSms(Number, Message);
        }
        return START_STICKY;
    }

    public static final String ACTION_RECEIVE_SMS = "TraccarService.RECEIVE_SMS_ACTION";

    private void handleSms(String Number, String Message) {
        Message = Message.toLowerCase();
        if (Message.compareTo("pos") == 0) {
            if (lastLocation == null) {
                smsCon.send(Number, "No known position");
            } else {
                /*smsCon.send(Number, lastLocation.getLatitude() + "; " + lastLocation.getLongitude()
                        + "; " + lastLocation.getAltitude() + "; " + lastLocation.getSpeed()
                        + "; " + lastLocation.getTime());*/
                smsCon.send(Number, Protocol.createSMSLocationMessage(lastLocation, getBatteryLevel()));
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        StatusActivity.addMessage(getString(R.string.status_service_destroy));

        if (sharedPreferences != null) {
        	sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }

        if (positionProvider != null) {
        	positionProvider.stopUpdates();
        }

        if (clientController != null) {
        	clientController.stop();
        }

        smsCon.close();

        wakeLock.release();
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    public double getBatteryLevel() {
        if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.ECLAIR) {
            Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            return (level * 100.0) / scale;
        } else {
            return 0;
        }
    }

    private PositionProvider.PositionListener positionListener = new PositionProvider.PositionListener() {

        @Override
        public void onPositionUpdate(Location location) {
            if (location != null) {
                lastLocation = location;
                StatusActivity.addMessage(getString(R.string.status_location_update));

                if(smsLogging)
                {
                    //Protocol.createSMSLocationMessage(location, getBatteryLevel());
                    smsCon.send(logNumber, Protocol.createSMSLocationMessage(location, getBatteryLevel()));
                }

                clientController.setNewLocation(Protocol.createLocationMessage(extended, location, getBatteryLevel()));
            }
        }

    };

    OnSharedPreferenceChangeListener preferenceChangeListener = new OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            StatusActivity.addMessage(getString(R.string.status_preference_update));
            try {
                if (key.equals(TraccarActivity.KEY_ADDRESS)) {

                    address = sharedPreferences.getString(TraccarActivity.KEY_ADDRESS, null);
                    clientController.setNewServer(address, port);

                } else if (key.equals(TraccarActivity.KEY_PORT)) {

                    port = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_PORT, null));
                    clientController.setNewServer(address, port);

                } else if (key.equals(TraccarActivity.KEY_INTERVAL)) {

                    interval = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_INTERVAL, null));
                    positionProvider.stopUpdates();
                    positionProvider = new PositionProvider(TraccarService.this, provider, interval * 1000, positionListener);
                    positionProvider.startUpdates();

                } else if (key.equals(TraccarActivity.KEY_ID)) {

                    id = sharedPreferences.getString(TraccarActivity.KEY_ID, null);
                    clientController.setNewLogin(Protocol.createLoginMessage(id));

                } else if (key.equals(TraccarActivity.KEY_PROVIDER)) {

                    provider = sharedPreferences.getString(TraccarActivity.KEY_PROVIDER, null);
                    positionProvider.stopUpdates();
                    positionProvider = new PositionProvider(TraccarService.this, provider, interval * 1000, positionListener);
                    positionProvider.startUpdates();

                } else if (key.equals(TraccarActivity.KEY_EXTENDED)) {

                    extended = sharedPreferences.getBoolean(TraccarActivity.KEY_EXTENDED, false);

                } else if (key.equals(TraccarActivity.KEY_NUMBER)) {

                    logNumber = sharedPreferences.getString(TraccarActivity.KEY_NUMBER, null);

                } else if (key.equals(TraccarActivity.KEY_SMSLOGGING)) {

                    smsLogging = sharedPreferences.getBoolean(TraccarActivity.KEY_SMSLOGGING, false);

                }
            } catch (Exception error) {
                Log.w(LOG_TAG, error);
            }
        }

    };

}
