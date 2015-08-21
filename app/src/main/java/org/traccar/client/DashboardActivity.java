package org.traccar.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class DashboardActivity extends Activity {

    private static final String TAG = StatusActivity.class.getName();

    private TextView idTextView;
    private TextView statusTextView;
    private EditText idEditText;
    private EditText statusEditText;
    private Button smsButton;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Starting the activity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dashboard);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        initPreferences();

        idEditText = (EditText) findViewById(R.id.mainactivity_id_EditText);
        statusEditText = (EditText) findViewById(R.id.mainactivity_status_EditText);
        smsButton = (Button) findViewById(R.id.send_sms_Button);

        idEditText.setText(sharedPreferences.getString(MainActivity.KEY_DEVICE, "N/A"));
        statusEditText.setText(Boolean.toString(sharedPreferences.getBoolean(MainActivity.KEY_STATUS, false)));
        sharedPreferences.registerOnSharedPreferenceChangeListener(
                preferenceChangeListener);
        smsButton.setOnClickListener(handleSmsClick);
        StatusActivity.addMessage("Starting traccar");
    }


    private View.OnClickListener handleSmsClick = new View.OnClickListener() {
        public void onClick(View arg0) {
            Intent mServiceIntent;
            mServiceIntent = new Intent(DashboardActivity.this, TrackingService.class);
            mServiceIntent.putExtra("action","send_sms");
            DashboardActivity.this.startService(mServiceIntent);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.settings_item) {
            startActivity(new Intent(this, MainActivity.class));
            return true;
        } else if (id == R.id.logs_item) {
            startActivity(new Intent(this, StatusActivity.class));
            return true;
        } else if (id == R.id.about_item) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(MainActivity.KEY_STATUS)) {
                statusEditText.setText(Boolean.toString(sharedPreferences.getBoolean(MainActivity.KEY_STATUS, true)));
            }
        }
    };

    private void initPreferences() {
        Log.d(TAG, "Initializing preferences");
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String id = telephonyManager.getDeviceId();
        if (!sharedPreferences.contains(MainActivity.KEY_DEVICE)) {
            sharedPreferences.edit().putString(MainActivity.KEY_DEVICE, id).commit();
        }
    }


}
