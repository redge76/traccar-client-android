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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.*;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Background service
 */
public class TraccarService extends Service {

    public static final String LOG_TAG = "Traccar.TraccarService";

    private static final String SMS_COMMAND_POS = "pos";
    private static final String SMS_COMMAND_ENABLE = "enable";
    private static final String SMS_COMMAND_DISABLE = "disable";
    private static final String SMS_COMMAND_SERVER = "server";
    private static final String SMS_COMMAND_PORT = "port";
    private static final String SMS_COMMAND_FREQUENCY = "frequency";
    private static final String SMS_COMMAND_FREQ = "freq";
    private static final String SMS_COMMAND_NUMBER = "number";
    private static final String SMS_COMMAND_BATTERY = "battery";

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

    private boolean smsTracking;
    private String smsTrackingNumber;

    private boolean smsNotification;
    private String smsNotificationNumber;

    private boolean inetTracking;

    private int lowBatteryWarning;
    private BatteryBroadcastReceiver myBatteryBroadcastReceiver;
    private boolean lowBatteryWarningSent;

    private WakeLock wakeLock;

    @Override
    public void onCreate() {
        StatusActivity.addMessage(getString(R.string.status_service_create));

        lastLocation = null;
        smsCon = new SmsConnection(this);

        inetTracking = true;

        smsTracking = false;
        smsTrackingNumber = "";

        smsNotification = false;
        smsNotificationNumber = "";

        lowBatteryWarning = 100;
        lowBatteryWarningSent = false;

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        //wakeLock.acquire();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            id = sharedPreferences.getString(TraccarActivity.KEY_ID, null);
            address = sharedPreferences.getString(TraccarActivity.KEY_ADDRESS, null);
            provider = sharedPreferences.getString(TraccarActivity.KEY_PROVIDER, null);
            port = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_PORT, null));
            interval = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_INTERVAL, null));
            extended = sharedPreferences.getBoolean(TraccarActivity.KEY_EXTENDED, false);

            inetTracking = sharedPreferences.getBoolean(TraccarActivity.KEY_TRACKING_INET, true);
            smsTracking = sharedPreferences.getBoolean(TraccarActivity.KEY_TRACKING_SMS, false);
            smsTrackingNumber = sharedPreferences.getString(TraccarActivity.KEY_TRACKING_SMS_NUMBER, null);

            smsNotification = sharedPreferences.getBoolean(TraccarActivity.KEY_NOTIFICATION_SMS, false);
            smsNotificationNumber = sharedPreferences.getString(TraccarActivity.KEY_NOTIFICATION_SMS_NUMBER, null);

            lowBatteryWarning = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_BATTERY, null));
            if (lowBatteryWarning <= (int) getBatteryLevel()) {
                lowBatteryWarningSent = true;
            } else lowBatteryWarningSent = false;

        } catch (Exception error) {
            Log.w(LOG_TAG, error);
        }

        clientController = new ClientController(this, address, port, Protocol.createLoginMessage(id));
        clientController.start();

        positionProvider = new PositionProvider(this, provider, interval * 1000, positionListener);
        positionProvider.startUpdates();

        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        myBatteryBroadcastReceiver = new BatteryBroadcastReceiver();
        registerReceiver(myBatteryBroadcastReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_RECEIVE_SMS)) {
                Bundle bundle = intent.getExtras();
                String Number = (String) bundle.get(SmsBroadcastReceiver.KEY_NUMBER);
                String Message = (String) bundle.get(SmsBroadcastReceiver.KEY_MESSAGE);

                String msg = getString(R.string.sms_from) + ": " + Number;
                msg += "\n+" + getString(R.string.sms_body) + ": " + Message;

                StatusActivity.addMessage(msg);
                handleSms(Number, Message);
            } else if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
                int battery = (int) ((level * 100.0) / scale);

                if (lowBatteryWarning > 0 && lowBatteryWarning < 100 && battery <= lowBatteryWarning) {
                    if (!lowBatteryWarningSent) {
                        smsCon.send(smsNotificationNumber, getString(R.string.sms_bat_warning) + " " +
                                +battery + " " + getString(R.string.sms_bat_percent));
                    }
                    lowBatteryWarningSent = true;
                } else lowBatteryWarningSent = false;
            }
        }
        return START_STICKY;
    }

    public static final String ACTION_RECEIVE_SMS = "TraccarService.RECEIVE_SMS_ACTION";

    private void handleSms(String Number, String Message) {
        SharedPreferences.Editor prefEditor;
        Message = Message.toLowerCase();
        String param = null;
        int index;

        index = Message.indexOf(' ');
        if (index != -1) {
            param = Message.substring(index + 1);
            Message = Message.substring(0, index);
        }


        prefEditor = sharedPreferences.edit();

        if (Message.compareTo(SMS_COMMAND_POS) == 0) {
            if (lastLocation == null) {

                smsCon.send(Number, getString(R.string.status_location_unknown));

            } else {

                smsCon.send(Number, Protocol.createSMSLocationMessage(lastLocation, getBatteryLevel(),
                        getString(R.string.sms_pos_time), getString(R.string.sms_pos_values)));
            }
        } else if (Message.compareTo(SMS_COMMAND_ENABLE) == 0) {

            //smsLogging = true;
            prefEditor.putBoolean(TraccarActivity.KEY_TRACKING_SMS, true);


        } else if (Message.compareTo(SMS_COMMAND_DISABLE) == 0) {

            //smsLogging = false;
            prefEditor.putBoolean(TraccarActivity.KEY_TRACKING_SMS, false);

        } else if (Message.compareTo(SMS_COMMAND_SERVER) == 0 && param != null) {

            prefEditor.putString(TraccarActivity.KEY_ADDRESS, param);

        } else if (Message.compareTo(SMS_COMMAND_PORT) == 0 && param != null) {

            prefEditor.putString(TraccarActivity.KEY_PORT, Protocol.makeNumeric(param));

        } else if ((Message.compareTo(SMS_COMMAND_FREQUENCY) == 0
                || Message.compareTo(SMS_COMMAND_FREQ) == 0) && param != null) {

            prefEditor.putString(TraccarActivity.KEY_INTERVAL, Protocol.makeNumeric(param));

        } else if (Message.compareTo(SMS_COMMAND_NUMBER) == 0 && param != null) {

            prefEditor.putString(TraccarActivity.KEY_TRACKING_SMS_NUMBER, Protocol.makeNumeric(param));

        } else if (Message.compareTo(SMS_COMMAND_BATTERY) == 0 && param != null) {
            Integer level = Integer.parseInt(Protocol.makeNumeric(param));
            level = Math.min(level, 100);
            level = Math.max(level, 0);
            prefEditor.putString(TraccarActivity.KEY_BATTERY, level.toString());

        }

        prefEditor.apply();
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

        unregisterReceiver(myBatteryBroadcastReceiver);

        smsCon.close();

        //wakeLock.release();
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

                if (smsTracking) {
                    smsCon.send(smsTrackingNumber, Protocol.createSMSLocationMessage(location, getBatteryLevel(),
                            getString(R.string.sms_pos_time), getString(R.string.sms_pos_values)));
                }

                if (inetTracking) {
                    clientController.setNewLocation(Protocol.createLocationMessage(extended, location, getBatteryLevel()));
                }
            }
        }

    };

    OnSharedPreferenceChangeListener preferenceChangeListener = new OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            StatusActivity.addMessage(getString(R.string.status_preference_update));
            try {
                //Tracking
                if (key.equals(TraccarActivity.KEY_ID)) {
                    id = sharedPreferences.getString(TraccarActivity.KEY_ID, null);
                    clientController.setNewLogin(Protocol.createLoginMessage(id));
                } else if (key.equals(TraccarActivity.KEY_INTERVAL)) {
                    interval = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_INTERVAL, null));
                    positionProvider.stopUpdates();
                    positionProvider = new PositionProvider(TraccarService.this, provider, interval * 1000, positionListener);
                    positionProvider.startUpdates();
                } else if (key.equals(TraccarActivity.KEY_PROVIDER)) {
                    provider = sharedPreferences.getString(TraccarActivity.KEY_PROVIDER, null);
                    positionProvider.stopUpdates();
                    positionProvider = new PositionProvider(TraccarService.this, provider, interval * 1000, positionListener);
                    positionProvider.startUpdates();
                }
                //Backends
                //Inet
                else if (key.equals(TraccarActivity.KEY_ADDRESS)) {
                    address = sharedPreferences.getString(TraccarActivity.KEY_ADDRESS, null);
                    clientController.setNewServer(address, port);
                } else if (key.equals(TraccarActivity.KEY_PORT)) {
                    port = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_PORT, null));
                    clientController.setNewServer(address, port);
                } else if (key.equals(TraccarActivity.KEY_EXTENDED)) {
                    extended = sharedPreferences.getBoolean(TraccarActivity.KEY_EXTENDED, false);
                } else if (key.equals(TraccarActivity.KEY_TRACKING_INET)) {
                    inetTracking = sharedPreferences.getBoolean(TraccarActivity.KEY_TRACKING_INET, true);
                }
                //Sms
                else if (key.equals(TraccarActivity.KEY_TRACKING_SMS)) {
                    smsTracking = sharedPreferences.getBoolean(TraccarActivity.KEY_TRACKING_SMS, false);
                } else if (key.equals(TraccarActivity.KEY_TRACKING_SMS_NUMBER)) {
                    smsTrackingNumber = sharedPreferences.getString(TraccarActivity.KEY_TRACKING_SMS_NUMBER, null);
                }
                //Notifications
                else if (key.equals(TraccarActivity.KEY_NOTIFICATION_SMS)) {
                    smsNotification = sharedPreferences.getBoolean(TraccarActivity.KEY_NOTIFICATION_SMS, false);
                } else if (key.equals(TraccarActivity.KEY_NOTIFICATION_SMS_NUMBER)) {
                    smsNotificationNumber = sharedPreferences.getString(TraccarActivity.KEY_NOTIFICATION_SMS_NUMBER, null);
                } else if (key.equals(TraccarActivity.KEY_BATTERY)) {
                    lowBatteryWarning = Integer.valueOf(sharedPreferences.getString(TraccarActivity.KEY_BATTERY, null));
                    if (lowBatteryWarning <= getBatteryLevel()) {
                        lowBatteryWarningSent = true;
                    } else lowBatteryWarningSent = false;
                }
            } catch (Exception error) {
                Log.w(LOG_TAG, error);
            }
        }

    };

}
