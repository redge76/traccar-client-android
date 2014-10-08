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
