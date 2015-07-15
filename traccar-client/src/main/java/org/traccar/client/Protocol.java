/*
 * Copyright 2012 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;
import java.util.TimeZone;

import android.location.Location;

/**
 * Protocol formatting
 */
public class Protocol {

    /**
     * Format device id message
     */
    public static String createLoginMessage(String id) {
        StringBuilder s = new StringBuilder("$PGID,");
        Formatter f = new Formatter(s, Locale.ENGLISH);

        s.append(id);

        byte checksum = 0;
        for (byte b : s.substring(1).getBytes()) {
            checksum ^= b;
        }
        f.format("*%02x\r\n", (int) checksum);
        f.close();

        return s.toString();
    }

    /**
     * Format location message
     */
    public static String createLocationMessage(boolean extended, Location l, double battery) {
        StringBuilder s = new StringBuilder(extended ? "$TRCCR," : "$GPRMC,");
        Formatter f = new Formatter(s, Locale.ENGLISH);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        calendar.setTimeInMillis(l.getTime());

        if (extended) {

            f.format("%1$tY%1$tm%1$td%1$tH%1$tM%1$tS.%1$tL,A,", calendar);

            f.format("%.6f,%.6f,", l.getLatitude(), l.getLongitude());
            f.format("%.2f,%.2f,", l.getSpeed() * 1.943844, l.getBearing());
            f.format("%.2f,", l.getAltitude());
            f.format("%.0f,", battery);

        } else {

            f.format("%1$tH%1$tM%1$tS.%1$tL,A,", calendar);

            double lat = l.getLatitude();
            double lon = l.getLongitude();
            f.format("%02d%07.4f,%c,", (int) Math.abs(lat), Math.abs(lat) % 1 * 60, lat < 0 ? 'S' : 'N');
            f.format("%03d%07.4f,%c,", (int) Math.abs(lon), Math.abs(lon) % 1 * 60, lon < 0 ? 'W' : 'E');

            double speed = l.getSpeed() * 1.943844; // speed in knots
            f.format("%.2f,%.2f,", speed, l.getBearing());
            f.format("%1$td%1$tm%1$ty,,", calendar);

        }

        byte checksum = 0;
        for (byte b : s.substring(1).getBytes()) {
            checksum ^= b;
        }
        f.format("*%02x\r\n", (int) checksum);
        f.close();

        return s.toString();
    }

    /**
     * Format location message for SMS
     */
    public static String createSMSLocationMessage(Location l, double battery, String time, String position) {
        StringBuilder s = new StringBuilder();
        //Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        //calendar.setTimeInMillis(l.getTime());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        s.append(time);
        s.append(": ");
        s.append(sdf.format(new Date(l.getTime())));
        s.append(" ");
        s.append(position);
        s.append(": ");
        s.append(l.getLatitude());
        s.append("; ");
        s.append(l.getLongitude());
        s.append("; ");
        s.append(l.getAltitude());
        s.append("; ");
        s.append((l.getSpeed() * 3.6));
        s.append("; ");
        s.append(battery);

        LogsActivity.addMessage(s.toString());
        LogsActivity.addMessage(new Integer(s.toString().length()).toString());

        return s.toString();
    }

    public static String makeNumeric(String param) {
        StringBuilder s = new StringBuilder();

        for(int i = 0; i < param.length(); i++) {
            if(param.charAt(i) >= '0' && param.charAt(i) <= '9') {
                s.append(param.charAt(i));
            }
        }

        return s.toString();
    }
}

