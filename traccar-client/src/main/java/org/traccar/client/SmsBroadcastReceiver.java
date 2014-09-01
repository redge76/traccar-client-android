package org.traccar.client;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(SmsConnection.SENT)) {
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    StatusActivity.addMessage("SMS send");
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    StatusActivity.addMessage("Generic failure");
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    StatusActivity.addMessage("No service");
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    StatusActivity.addMessage("Null PDU");
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    StatusActivity.addMessage("Radio off");
                    break;
            }
        }
        else if(intent.getAction().equals(SmsConnection.DELIVERED)) {
            switch(getResultCode()) {
                case Activity.RESULT_OK:
                    StatusActivity.addMessage("SMS delivered");
                    break;
                case Activity.RESULT_CANCELED:
                    StatusActivity.addMessage("SMS not delivered");
                    break;
            }
        }
        else if(intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED"))
        {
            Bundle bundle = intent.getExtras();
            Object messages[] = (Object[]) bundle.get("pdus");
            SmsMessage smsMessage[] = new SmsMessage[messages.length];

            for (int i = 0; i < messages.length; i++)
                smsMessage[i] = SmsMessage.createFromPdu((byte[]) messages[i]);

            Intent receivedIntent = new Intent(context, TraccarService.class);
            receivedIntent.setAction(TraccarService.ACTION_RECEIVE_SMS);
            receivedIntent.putExtra("Number", smsMessage[0].getDisplayOriginatingAddress());
            receivedIntent.putExtra("Message", smsMessage[0].getDisplayMessageBody());
            context.startService(receivedIntent);
        }
    }
}
