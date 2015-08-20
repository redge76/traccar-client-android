package org.traccar.client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.traccar.client.MainActivity;

public class EntryActivity extends Activity
{

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // launch a different activity 
        Intent launchIntent = new Intent();
        Class<?> launchActivity;
        try
        {
            String className = getScreenClassName();
            launchActivity = Class.forName(className);
        }
        catch (ClassNotFoundException e)
        {
            launchActivity = MainActivity.class;
        }
        launchIntent.setClass(getApplicationContext(), launchActivity);
        startActivity(launchIntent);

        finish();
    }

    /** return Class name of Activity to show **/
    private String getScreenClassName()
    {
        // NOTE - Place logic here to determine which screen to show next
        // Default is used in this demo code
        String activity = MainActivity.class.getName();
        return activity;
    }

} 