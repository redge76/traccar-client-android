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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Background service
 */
public class TraccarService extends Service {

    private static final String TAG = TraccarService.class.getName();


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
        Log.i(TAG, "Creating service obj");
        LogsActivity.addMessage(getString(R.string.status_service_create));

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
        //TODO: do we really need wakelock ?
        //wakeLock.acquire();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            id = sharedPreferences.getString(getString(R.string.pref_key_id), null);
            address = sharedPreferences.getString(getString(R.string.pref_key_address), null);
            provider = sharedPreferences.getString(getString(R.string.pref_key_provider), null);
            port = Integer.valueOf(sharedPreferences.getString(getString(R.string.pref_key_port), null));
            interval = Integer.valueOf(sharedPreferences.getString(getString(R.string.pref_key_interval), null));
            extended = sharedPreferences.getBoolean(getString(R.string.pref_key_extended), false);

            inetTracking = sharedPreferences.getBoolean(getString(R.string.pref_key_tracking_inet), true);
            smsTracking = sharedPreferences.getBoolean(getString(R.string.pref_key_tracking_sms), false);
            smsTrackingNumber = sharedPreferences.getString(getString(R.string.pref_key_tracking_sms_number), null);

            smsNotification = sharedPreferences.getBoolean(getString(R.string.pref_key_notification_sms), false);
            smsNotificationNumber = sharedPreferences.getString(getString(R.string.pref_key_notification_sms_number), null);

            lowBatteryWarning = Integer.valueOf(sharedPreferences.getString(getString(R.string.pref_key_battery), null));
//            if (lowBatteryWarning <= (int) getBatteryLevel()) {
//                lowBatteryWarningSent = true;
//            } else lowBatteryWarningSent = false;

        } catch (Exception error) {
            Log.e(TAG, error.toString());
        }

        clientController = new ClientController(this, address, port, Protocol.createLoginMessage(id));
        clientController.start();

        positionProvider = new PositionProvider(this, provider, interval * 1000, positionListener);
        positionProvider.startUpdates();

        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        myBatteryBroadcastReceiver = new BatteryBroadcastReceiver();
//        registerReceiver(myBatteryBroadcastReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service received onStartCommand id: " + startId + " intent: " + intent);
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_RECEIVE_SMS)) {
                Bundle bundle = intent.getExtras();
                String Number = (String) bundle.get(SmsBroadcastReceiver.KEY_NUMBER);
                String Message = (String) bundle.get(SmsBroadcastReceiver.KEY_MESSAGE);

                String msg = getString(R.string.sms_from) + ": " + Number;
                msg += "\n+" + getString(R.string.sms_body) + ": " + Message;

                LogsActivity.addMessage(msg);
                handleSms(Number, Message);
            } else if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
                int battery = (int) ((level * 100.0) / scale);

                if (smsNotification) {
                    if (lowBatteryWarning > 0 && lowBatteryWarning < 100 && battery <= lowBatteryWarning) {
                        if (!lowBatteryWarningSent) {
                            smsCon.send(smsNotificationNumber, getString(R.string.sms_bat_warning) + " " +
                                    +battery + " " + getString(R.string.sms_bat_percent));
                        }
                        lowBatteryWarningSent = true;
                    } else lowBatteryWarningSent = false;
                }
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


        if (Message.compareTo(getString(R.string.sms_command_pos)) == 0) {
            if (lastLocation == null) {

                smsCon.send(Number, getString(R.string.status_location_unknown));

            } else {

//                smsCon.send(Number, Protocol.createSMSLocationMessage(lastLocation, getBatteryLevel(),
//                        getString(R.string.sms_pos_time), getString(R.string.sms_pos_values)));
            }
        } else if (Message.compareTo(getString(R.string.pref_key_tracking_sms)) == 0) {

            //smsLogging = true;
            prefEditor.putBoolean(getString(R.string.pref_key_tracking_sms), true);


        } else if (Message.compareTo(getString(R.string.sms_command_disable)) == 0) {

            //smsLogging = false;
            prefEditor.putBoolean(getString(R.string.pref_key_tracking_sms), false);

        } else if (Message.compareTo(getString(R.string.sms_command_server)) == 0 && param != null) {

            prefEditor.putString(getString(R.string.pref_key_address), param);

        } else if (Message.compareTo(getString(R.string.sms_command_port)) == 0 && param != null) {

            prefEditor.putString(getString(R.string.pref_key_port), Protocol.makeNumeric(param));

        } else if ((Message.compareTo(getString(R.string.sms_command_frequency)) == 0
                || Message.compareTo(getString(R.string.sms_command_freq)) == 0) && param != null) {

            prefEditor.putString(getString(R.string.pref_key_interval), Protocol.makeNumeric(param));

        } else if (Message.compareTo(getString(R.string.sms_command_number)) == 0 && param != null) {

            prefEditor.putString(getString(R.string.pref_key_tracking_sms_number), Protocol.makeNumeric(param));

        } else if (Message.compareTo(getString(R.string.sms_command_battery)) == 0 && param != null) {
            Integer level = Integer.parseInt(Protocol.makeNumeric(param));
            level = Math.min(level, 100);
            level = Math.max(level, 0);
            prefEditor.putString(getString(R.string.pref_key_battery), level.toString());

        }

        prefEditor.apply();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        LogsActivity.addMessage(getString(R.string.status_service_destroy));

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

//    @TargetApi(Build.VERSION_CODES.ECLAIR)
//    public double getBatteryLevel() {
//        if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.ECLAIR) {
//            Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
//            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
//            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
//            return (level * 100.0) / scale;
//        } else {
//            return 0;
//        }
//    }

    private PositionProvider.PositionListener positionListener = new PositionProvider.PositionListener() {

        @Override
        public void onPositionUpdate(Location location) {
            if (location != null) {
                lastLocation = location;
                LogsActivity.addMessage(getString(R.string.status_location_update));

//                if (smsTracking) {
//                    smsCon.send(smsTrackingNumber, Protocol.createSMSLocationMessage(location, getBatteryLevel(),
//                            getString(R.string.sms_pos_time), getString(R.string.sms_pos_values)));
//                }
//
//                if (inetTracking) {
//                    clientController.setNewLocation(Protocol.createLocationMessage(extended, location, getBatteryLevel()));
//                }
            }
        }

    };

    OnSharedPreferenceChangeListener preferenceChangeListener = new OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            LogsActivity.addMessage(getString(R.string.status_preference_update));
            try {
                //Tracking
                if (key.equals(getString(R.string.pref_key_id))) {
                    id = sharedPreferences.getString(getString(R.string.pref_key_id), null);
                    clientController.setNewLogin(Protocol.createLoginMessage(id));
                } else if (key.equals(getString(R.string.pref_key_interval))) {
                    interval = Integer.valueOf(sharedPreferences.getString(getString(R.string.pref_key_interval), null));
                    positionProvider.stopUpdates();
                    positionProvider = new PositionProvider(TraccarService.this, provider, interval * 1000, positionListener);
                    positionProvider.startUpdates();
                } else if (key.equals(getString(R.string.pref_key_provider))) {
                    provider = sharedPreferences.getString(getString(R.string.pref_key_provider), null);
                    positionProvider.stopUpdates();
                    positionProvider = new PositionProvider(TraccarService.this, provider, interval * 1000, positionListener);
                    positionProvider.startUpdates();
                }
                //Backends
                //Inet
                else if (key.equals(getString(R.string.pref_key_address))) {
                    address = sharedPreferences.getString(getString(R.string.pref_key_address), null);
                    clientController.setNewServer(address, port);
                } else if (key.equals(getString(R.string.pref_key_port))) {
                    port = Integer.valueOf(sharedPreferences.getString(getString(R.string.pref_key_port), null));
                    clientController.setNewServer(address, port);
                } else if (key.equals(getString(R.string.pref_key_extended))) {
                    extended = sharedPreferences.getBoolean(getString(R.string.pref_key_extended), false);
                } else if (key.equals(getString(R.string.pref_key_tracking_inet))) {
                    inetTracking = sharedPreferences.getBoolean(getString(R.string.pref_key_tracking_inet), true);
                }
                //Sms
                else if (key.equals(getString(R.string.pref_key_tracking_sms))) {
                    smsTracking = sharedPreferences.getBoolean(getString(R.string.pref_key_tracking_sms), false);
                } else if (key.equals(getString(R.string.pref_key_tracking_sms_number))) {
                    smsTrackingNumber = sharedPreferences.getString(getString(R.string.pref_key_tracking_sms_number), null);
                }
                //Notifications
                else if (key.equals(getString(R.string.pref_key_notification_sms))) {
                    smsNotification = sharedPreferences.getBoolean(getString(R.string.pref_key_notification_sms), false);
                } else if (key.equals(getString(R.string.pref_key_notification_sms_number))) {
                    smsNotificationNumber = sharedPreferences.getString(getString(R.string.pref_key_notification_sms_number), null);
                } else if (key.equals(getString(R.string.pref_key_battery))) {
                    lowBatteryWarning = Integer.valueOf(sharedPreferences.getString(getString(R.string.pref_key_battery), null));
//                    if (lowBatteryWarning <= getBatteryLevel()) {
//                        lowBatteryWarningSent = true;
//                    } else lowBatteryWarningSent = false;
                }
            } catch (Exception error) {
                Log.e(TAG, error.toString());
            }
        }

    };

}
