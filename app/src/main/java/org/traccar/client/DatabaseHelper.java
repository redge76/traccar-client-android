/*
 * Copyright 2015 Anton Tananaev (anton.tananaev@gmail.com)
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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.Date;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = DatabaseHelper.class.getSimpleName();
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "traccar.db";

    public interface DatabaseHandler<T> {
        void onSuccess(T result);

        void onFailure(RuntimeException error);
    }

    private static abstract class DatabaseAsyncTask<T> extends AsyncTask<Void, Void, T> {

        private DatabaseHandler<T> handler;
        private RuntimeException error;

        public DatabaseAsyncTask(DatabaseHandler<T> handler) {
            this.handler = handler;
        }

        @Override
        protected T doInBackground(Void... params) {
            try {
                return executeMethod();
            } catch (RuntimeException error) {
                this.error = error;
                return null;
            }
        }

        protected abstract T executeMethod();

        @Override
        protected void onPostExecute(T result) {
            if (error == null) {
                handler.onSuccess(result);
            } else {
                handler.onFailure(error);
            }
        }
    }

    private SQLiteDatabase db;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        db = getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE position (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "deviceId TEXT," +
                "time INTEGER," +
                "latitude REAL," +
                "longitude REAL," +
                "altitude REAL," +
                "speed REAL," +
                "course REAL," +
                "battery REAL," +
                "sent INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS position;");
        onCreate(db);
    }

    public void insertPosition(Position position) {
        ContentValues values = new ContentValues();
        values.put("deviceId", position.getDeviceId());
        values.put("time", position.getTime().getTime());
        values.put("latitude", position.getLatitude());
        values.put("longitude", position.getLongitude());
        values.put("altitude", position.getAltitude());
        values.put("speed", position.getSpeed());
        values.put("course", position.getCourse());
        values.put("battery", position.getBattery());
        values.put("sent", 0);
        db.insertOrThrow("position", null, values);
    }

    public void insertPositionAsync(final Position position, DatabaseHandler<Void> handler) {
        new DatabaseAsyncTask<Void>(handler) {
            @Override
            protected Void executeMethod() {
                insertPosition(position);
                return null;
            }
        }.execute();
    }

    public ArrayList<Position> selectPositions(boolean all, int number) {
        Cursor cursor;
        ArrayList<Position> positions = new ArrayList<Position>();

        if (all) {
            cursor = db.rawQuery("SELECT * FROM position WHERE sent = 0 ORDER BY id LIMIT ?", new String[]{Integer.toString(number)});
        } else {
            cursor = db.rawQuery("SELECT * FROM position WHERE sent = 0 ORDER BY id LIMIT ?", new String[]{Integer.toString(number)});
        }
        try {
            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    Position position = new Position();
                    position.setId(cursor.getLong(cursor.getColumnIndex("id")));
                    position.setDeviceId(cursor.getString(cursor.getColumnIndex("deviceId")));
                    position.setTime(new Date(cursor.getLong(cursor.getColumnIndex("time"))));
                    position.setLatitude(cursor.getDouble(cursor.getColumnIndex("latitude")));
                    position.setLongitude(cursor.getDouble(cursor.getColumnIndex("longitude")));
                    position.setAltitude(cursor.getDouble(cursor.getColumnIndex("altitude")));
                    position.setSpeed(cursor.getDouble(cursor.getColumnIndex("speed")));
                    position.setCourse(cursor.getDouble(cursor.getColumnIndex("course")));
                    position.setBattery(cursor.getDouble(cursor.getColumnIndex("battery")));
                    positions.add(position);
                }
            }
        } finally {
            cursor.close();
        }
        return positions;
    }

    public Position selectPosition(boolean all, int number) {
        ArrayList<Position> positions = selectPositions(false, 1);
        if (positions.size() > 0) {
            return selectPositions(all, number).get(1);
        } else {
            return null;
        }
    }

    public void selectLatestPositionAsync(DatabaseHandler<Position> handler) {
        new DatabaseAsyncTask<Position>(handler) {
            @Override
            protected Position executeMethod() {
                return selectPosition(true, 1);
            }
        }.execute();
    }

    public void selectPositionsAsync(DatabaseHandler<ArrayList<Position>> handler) {
        new DatabaseAsyncTask<ArrayList<Position>>(handler) {
            @Override
            protected ArrayList<Position> executeMethod() {
                return selectPositions(false, 1);
            }
        }.execute();
    }

    public void selectPositionAsync(DatabaseHandler<Position> handler) {
        new DatabaseAsyncTask<Position>(handler) {
            @Override
            protected Position executeMethod() {
                return selectPosition(false, 1);
            }
        }.execute();
    }

    public void markPositionSent(long id) {
        ContentValues args = new ContentValues();
        args.put("sent", "1");
        if (db.update("position", args, "id = ?", new String[]{String.valueOf(id)}) != 1) {
            throw new SQLException();
        }
    }

    public void deletePosition(long id) {
        markPositionSent(id);
//        if (db.delete("position", "id = ?", new String[]{String.valueOf(id)}) != 1) {
//            throw new SQLException();
//        }
    }

    public void deletePositionAsync(final long id, DatabaseHandler<Void> handler) {
        new DatabaseAsyncTask<Void>(handler) {
            @Override
            protected Void executeMethod() {
                deletePosition(id);
                return null;
            }
        }.execute();
    }

}
