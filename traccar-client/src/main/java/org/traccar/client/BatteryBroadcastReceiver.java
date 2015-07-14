/*
 * Copyright 2012 - 2015 Armagetron
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

public class BatteryBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);

        Intent receivedIntent = new Intent(context, TraccarService.class);
        receivedIntent.setAction(intent.getAction());
        receivedIntent.putExtra(BatteryManager.EXTRA_LEVEL, level);
        receivedIntent.putExtra(BatteryManager.EXTRA_SCALE, scale);
        context.startService(receivedIntent);
    }
}
