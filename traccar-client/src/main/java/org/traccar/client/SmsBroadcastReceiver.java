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

    static final String KEY_NUMBER = "SmsBroadcastReceiver_Number";
    static final String KEY_MESSAGE = "SmsBroadcastReceiver_Message";

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(SmsConnection.SENT)) {
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    StatusActivity.addMessage(context.getString(R.string.sms_sent));
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    StatusActivity.addMessage(context.getString(R.string.sms_generic_failure));
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    StatusActivity.addMessage(context.getString(R.string.sms_no_service));
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    StatusActivity.addMessage(context.getString(R.string.sms_null_pdu));
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    StatusActivity.addMessage(context.getString(R.string.sms_radio_off));
                    break;
            }
        }
        else if(intent.getAction().equals(SmsConnection.DELIVERED)) {
            switch(getResultCode()) {
                case Activity.RESULT_OK:
                    StatusActivity.addMessage(context.getString(R.string.sms_delivered));
                    break;
                case Activity.RESULT_CANCELED:
                    StatusActivity.addMessage(context.getString(R.string.sms_not_delivered));
                    break;
            }
        }
        else if(intent.getAction().equals(SmsConnection.RECEIVED))
        {
            Bundle bundle = intent.getExtras();
            Object messages[] = (Object[]) bundle.get("pdus");
            SmsMessage smsMessage[] = new SmsMessage[messages.length];

            for (int i = 0; i < messages.length; i++)
                smsMessage[i] = SmsMessage.createFromPdu((byte[]) messages[i]);

            Intent receivedIntent = new Intent(context, TraccarService.class);
            receivedIntent.setAction(TraccarService.ACTION_RECEIVE_SMS);
            receivedIntent.putExtra(KEY_NUMBER, smsMessage[0].getDisplayOriginatingAddress());
            receivedIntent.putExtra(KEY_MESSAGE, smsMessage[0].getDisplayMessageBody());
            context.startService(receivedIntent);
        }
    }
}
