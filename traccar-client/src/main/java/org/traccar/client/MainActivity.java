package org.traccar.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;


public class MainActivity extends Activity {
    private TextView idTextView;
    private TextView statusTextView;
    private EditText idEditText;
    private EditText statusEditText;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        initPreferences();

        idEditText = (EditText) findViewById(R.id.mainactivity_id_EditText);
        statusEditText = (EditText) findViewById(R.id.mainactivity_status_EditText);

        idEditText.setText(sharedPreferences.getString(getString(R.string.pref_key_id), "N/A"));
        statusEditText.setText(Boolean.toString(sharedPreferences.getBoolean(getString(R.string.pref_key_status), false)));
        sharedPreferences.registerOnSharedPreferenceChangeListener(
                preferenceChangeListener);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.settings_item) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.logs_item) {
            startActivity(new Intent(this, LogsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.about_item) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//        sharedPreferences.registerOnSharedPreferenceChangeListener(
//                preferenceChangeListener);
//        Log.d(SettingsActivity.LOG_TAG, "add Listener");
//    }
//
//    @Override
//    protected void onPause() {
//        sharedPreferences.unregisterOnSharedPreferenceChangeListener(
//                preferenceChangeListener);
//        Log.d(SettingsActivity.LOG_TAG, "delete listener");
//        super.onPause();
//    }

    SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(getString(R.string.pref_key_status))) {
                statusEditText.setText(Boolean.toString(sharedPreferences.getBoolean(getString(R.string.pref_key_status), true)));
            }
        }
    };


    private void initPreferences() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String id = telephonyManager.getDeviceId();
        if (!sharedPreferences.contains(getString(R.string.pref_key_id))) {
            sharedPreferences.edit().putString(getString(R.string.pref_key_id), id).commit();
        }
    }

}
