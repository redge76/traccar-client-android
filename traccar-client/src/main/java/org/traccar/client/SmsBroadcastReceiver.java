package org.traccar.client;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals("SMS_SENT")) {
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
        else if(intent.getAction().equals("SMS_DELIVERED")) {
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

            for (int n = 0; n < messages.length; n++)
                smsMessage[n] = SmsMessage.createFromPdu((byte[]) messages[n]);

            String msg = "Received SMS from: " + smsMessage[0].getDisplayOriginatingAddress();
            msg += "\nMessage: " + smsMessage[0].getDisplayMessageBody();

            StatusActivity.addMessage(msg);
        }
    }
}
