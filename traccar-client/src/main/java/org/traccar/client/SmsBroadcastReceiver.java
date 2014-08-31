package org.traccar.client;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

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
    }
}
