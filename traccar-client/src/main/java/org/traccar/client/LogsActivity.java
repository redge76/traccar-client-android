/*
 * Copyright 2012 - 2013 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.ListActivity;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class LogsActivity extends ListActivity {

    private static final String TAG = SettingsActivity.class.getName();

    private final static int LIMIT = 20;

    private static final LinkedList<ArrayList<String>> messages = new LinkedList<ArrayList<String>>();
    private static final List<String> messages2 = new LinkedList<String>();


    private static ArrayAdapter adapter;

    //TODO: make this better
    public static void addMessage(String message1, String message2) {
        Log.i(TAG, "Adding message1: " + message1 + "\n message2: " + message2);
        DateFormat format = DateFormat.getTimeInstance(DateFormat.SHORT);
        message1 = format.format(new Date()) + " - " + message1;
        messages.add(new ArrayList<String>(Arrays.asList(message1, message2)));
        trimMessages();
    }

    public static void addMessage(String message) {
        Log.i(TAG, "Adding message: " + message);
        DateFormat format = DateFormat.getTimeInstance(DateFormat.SHORT);
        message = format.format(new Date()) + " - " + message;

        messages.add(new ArrayList<String>(Arrays.asList(message)));

        trimMessages();
        if (adapter != null) adapter.notifyDataSetChanged();

    }

    private static void trimMessages() {
        while (messages.size() > LIMIT) {
            messages.removeFirst();
        }
    }

    public static void clearMessages() {
        messages.clear();
        adapter.notifyDataSetChanged();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Creating LogsActivity instance");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.logs);

        adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1, messages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = (TextView) view.findViewById(android.R.id.text1);
                TextView text2 = (TextView) view.findViewById(android.R.id.text2);
                if (messages.get(position).size() == 1) {
                    text1.setText(messages.get(position).get(0));
                    text2.setText("-");
                } else {
                    text1.setText(messages.get(position).get(0));
                    text2.setText(messages.get(position).get(1));
                }
                return view;
            }
        };
        setListAdapter(adapter);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.logs_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear) {
            clearMessages();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
//TODO: add longpress to the LogsActivity
}
