/*
 * Copyright 2015 Anton Tananaev (anton.tananaev@gmail.com)
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;

public class TrackingController implements PositionProvider.PositionListener, NetworkManager.NetworkHandler {

    public static final String ALARM_INTENT_HTTP = "org.traccar.client.HTTP";
    public static final String ALARM_INTENT_SMS = "org.traccar.client.SMS";


    private static final String TAG = TrackingController.class.getSimpleName();
    private static final int RETRY_DELAY = 30 * 1000;
    private static final int WAKE_LOCK_TIMEOUT = 60 * 1000;

    private boolean isOnline;
    private boolean isWaiting;

    private Context context;
    private Handler handler;

    private SharedPreferences preferences;
    private Date noSendTimeLimit;

    private String address;
    private int port;

    private PositionProvider positionProvider;
    private DatabaseHelper databaseHelper;
    private NetworkManager networkManager;

    private PowerManager.WakeLock wakeLock;
    4

    ArrayList<Position> positionCache;

    private void lock() {
        wakeLock.acquire(WAKE_LOCK_TIMEOUT);
    }

    private void unlock() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public TrackingController(Context context) {
        this.context = context;
        handler = new Handler();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getString(MainActivity.KEY_PROVIDER, null).equals("mixed")) {
            positionProvider = new MixedPositionProvider(context, this);
        } else {
            positionProvider = new SimplePositionProvider(context, this);
        }
        databaseHelper = new DatabaseHelper(context);
        networkManager = new NetworkManager(context, this);
        isOnline = networkManager.isOnline();

        address = preferences.getString(MainActivity.KEY_ADDRESS, null);
        port = Integer.parseInt(preferences.getString(MainActivity.KEY_PORT, null));

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());


    }

    public void start() {
        if (isOnline) {
            //read();
        }
        positionProvider.startUpdates();
        networkManager.start();
        noSendTimeLimit = new Date();
        setAlarms();
    }

    public void stop() {
        networkManager.stop();
        positionProvider.stopUpdates();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onPositionUpdate(Position position) {
        StatusActivity.addMessage(context.getString(R.string.status_location_update));
        if (position != null) {
            write(position);
        }
    }

    @Override
    public void onNetworkUpdate(boolean isOnline) {
        StatusActivity.addMessage(context.getString(R.string.status_connectivity_change));
        if (!this.isOnline && isOnline) {
            //         read();
        }
        this.isOnline = isOnline;
    }

    //
    // State transition examples:
    //
    // write -> read -> send -> delete -> read
    //
    // read -> send -> retry -> read -> send
    //

    private void log(String action, Position position) {
        if (position != null) {
            action += " (" +
                    "id:" + position.getId() +
                    " time:" + position.getTime().getTime() / 1000 +
                    " lat:" + position.getLatitude() +
                    " lon:" + position.getLongitude() + ")";
        }
        Log.d(TAG, action);
    }

    private void write(Position position) {
        log("write", position);
//        lock();
        databaseHelper.insertPositionAsync(position, new DatabaseHelper.DatabaseHandler<Void>() {
            @Override
            public void onComplete(boolean success, Void result) {
                if (success) {
                    Log.d(TAG, "write(): Position inserted");
                    if (isOnline && isWaiting) {
                        //read();
                        isWaiting = false;
                    }
                } else {
                    Log.d(TAG, "write(): Failed position insertion");
                }
//                unlock();
            }
        });
    }

    private void read() {
        log("read():", null);
//        lock();
        databaseHelper.selectPositionAsync(new DatabaseHelper.DatabaseHandler<Position>() {
            @Override
            public void onComplete(boolean success, Position result) {
                if (success) {
                    Log.d(TAG, "read(): Selection success");
                    if (result != null) {
                        Log.d(TAG, "read(): Calling send");
                        send(result);
                    } else {
                        isWaiting = true;
                    }
                } else {
                    Log.d(TAG, "read(): Retrying the selection");
                    retry();
                }
//                unlock();
            }
        });
    }

    private void delete(Position position) {
        log("delete()", position);
        lock();
        databaseHelper.deletePositionAsync(position.getId(), new DatabaseHelper.DatabaseHandler<Void>() {
            @Override
            public void onComplete(boolean success, Void result) {
                if (success) {
                    //read();
                } else {
                    retry();
                }
                unlock();
            }
        });
    }

    private void send(final Position position) {
        log("send", position);
//        lock();
        String request = ProtocolFormatter.formatRequest(address, port, position);
        RequestManager.sendRequestAsync(request, new RequestManager.RequestHandler() {
            @Override
            public void onComplete(boolean success) {
                if (success) {
                    Log.d(TAG, "send(): Position sucessfully sent");
                    delete(position);
                    noSendTimeLimit.setTime(position.getTime().getTime() + Integer.parseInt(preferences.getString(MainActivity.KEY_SMS_BACKEND_NO_SEND_TIME_LIMIT, null)) * 60 * 1000);
                } else {
                    StatusActivity.addMessage(context.getString(R.string.status_send_fail));
                    Log.e(TAG, "Send(): Error while sending position");
                    retry();
                }
//                unlock();
            }
        });
    }

    private void retry() {
        Log.d(TAG, "retry()");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isOnline) {
                    read();
                }
            }
        }, RETRY_DELAY);
    }

    private void sendSms(final Position position) {
        log("sendSms()", position);

        if (position.getTime().after(noSendTimeLimit) && preferences.getBoolean(MainActivity.KEY_SMS_BACKEND_STATUS, false)) {
            SmsRequestManager.sendRequestAsync(context, "0796281978", "test timeout", new SmsRequestManager.RequestHandler() {
                @Override
                public void onComplete(boolean success) {
                    if (success) {
                        noSendTimeLimit.setTime(position.getTime().getTime() + Integer.parseInt(preferences.getString(MainActivity.KEY_SMS_BACKEND_NO_SEND_TIME_LIMIT, null)) * 60 * 1000);
                    } else {
                        StatusActivity.addMessage(context.getString(R.string.status_send_fail));
                        retry();
                    }
                }
            });
        }
    }


    public void readLatestPositions(type) {
        Log.d(TAG, "readLatestPositions()");
        final Position returnResult;
        databaseHelper.selectPositionsAsync(new DatabaseHelper.DatabaseHandler<ArrayList<Position>>() {
            public void onComplete(boolean success, Position result) {
                if (success) {
                    Log.d(TAG, "smsReadLatestPosition(): position selected");
                    if (result != null) {
                        returnResult = result;
                    }
                } else {
                    Log.e(TAG, "smsReadLatestPosition(): Position selection failed");
                }
            }
        });
    }


    public void sendLatestPositionBySms(Position position) {
        String message = SmsProtocolFormatter.formatRequest(position);
        SmsRequestManager.sendRequestAsync(context, preferences.getString(MainActivity.KEY_SMS_BACKEND_NUMBER, null), message, new SmsRequestManager.RequestHandler() {
                    @Override
                    public void onComplete(boolean success) {
                        if (success) {
                            Log.d(TAG, "sendLatestPositionbySms(): SMS send OK");
                        } else {
                            Log.d(TAG, "sendLatestPositionbySms(): SMS send BAD");
                        }
                    }
                }
        );
    }

    private final BroadcastReceiver alarmReceiverHttp = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG, "onReceive(): Alarm HTTP ");
            if (isOnline) {
                if (positionCache.isEmpty() || hasNewPosition) positionCache = ReadLatestPositions("SMS");
                success = sendPositionByHttp(positionCache);
                if (sucess) {
                    StatusActivity.addMessage(context.getString(R.string.status_send_sucess));
                    hasNewPosition = false;
                } else {
                    StatusActivity.addMessage(context.getString(R.string.status_send_fail));
                    setRetryAlarm();

                }
            }
        }
    };

    private final BroadcastReceiver alarmReceiverSms = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG, "onReceive(): Alarm SMS");
        }
    };

    public void setAlarms() {
        Log.e(TAG, "setAlarm(): adding all alarms   ");
        long wakeupTime = System.currentTimeMillis() + 5000;

        IntentFilter filterHttp = new IntentFilter();
        filterHttp.addAction(TrackingController.ALARM_INTENT_HTTP);
        context.registerReceiver(alarmReceiverHttp, filterHttp);

        IntentFilter filterSms = new IntentFilter();
        filterSms.addAction(TrackingController.ALARM_INTENT_SMS);
        context.registerReceiver(alarmReceiverSms, filterSms);

        Intent myIntentHttp = new Intent(TrackingController.ALARM_INTENT_HTTP);
        PendingIntent pendingIntentHttp = PendingIntent.getBroadcast(context, 0, myIntentHttp, 0);

        Intent myIntentSms = new Intent(TrackingController.ALARM_INTENT_SMS);
        PendingIntent pendingIntentSms = PendingIntent.getBroadcast(context, 0, myIntentSms, 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, wakeupTime, 300000, pendingIntentHttp);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, wakeupTime, 300000, pendingIntentSms);
    }

    public void cancelAlarms() {
        Log.e(TAG, "cancelAlarms(): deleting all alarms   ");

        IntentFilter filterHttp = new IntentFilter();
        filterHttp.addAction(TrackingController.ALARM_INTENT_HTTP);
        context.registerReceiver(alarmReceiverHttp, filterHttp);

        IntentFilter filterSms = new IntentFilter();
        filterSms.addAction(TrackingController.ALARM_INTENT_SMS);
        context.registerReceiver(alarmReceiverSms, filterSms);

        Intent myIntentHttp = new Intent(TrackingController.ALARM_INTENT_HTTP);
        PendingIntent pendingIntentHttp = PendingIntent.getBroadcast(context, 0, myIntentHttp, 0);

        Intent myIntentSms = new Intent(TrackingController.ALARM_INTENT_SMS);
        PendingIntent pendingIntentSms = PendingIntent.getBroadcast(context, 0, myIntentSms, 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntentHttp);
        alarmManager.cancel(pendingIntentSms);

    }
}


