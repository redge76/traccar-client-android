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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TrackingService extends Service {

    private static final String TAG = TrackingService.class.getSimpleName();
    private static final int NOTIFICATION_ID = 1;

    private TrackingController trackingController;

    private boolean foreground;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate(): service create");
        StatusActivity.addMessage(getString(R.string.status_service_create));

        trackingController = new TrackingController(this);
        trackingController.start();

        foreground = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(MainActivity.KEY_FOREGROUND, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR && foreground) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

            Notification notification = new Notification(android.R.drawable.stat_notify_sync_noanim, null, 0);
            try {
                Method method = notification.getClass().getMethod("setLatestEventInfo", Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                try {
                    method.invoke(notification, this, getString(R.string.app_name), getString(R.string.settings_status_on_summary), pendingIntent);
                } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                    Log.w(TAG, e);
                }
            } catch (SecurityException | NoSuchMethodException e) {
                Log.w(TAG, e);
            }

            int notificationId = NOTIFICATION_ID;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                notification.priority = Notification.PRIORITY_MIN;
            } else {
                notificationId = 0;
            }

            startForeground(notificationId, notification);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStart(Intent intent, int startId) {
        if (intent != null) {
            AutostartReceiver.completeWakefulIntent(intent);
        }
        String action = intent.getStringExtra("action");
        if ( action != null) {
            switch (action) {
                case "send_sms":
                    trackingController.smsReadLatestPosition();
                    break;
                default:
                    Log.e(TAG, "no action");
                    break;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "service destroy");
        StatusActivity.addMessage(getString(R.string.status_service_destroy));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR && foreground) {
            stopForeground(true);
        }

        if (trackingController != null) {
            trackingController.stop();
        }
    }

}
