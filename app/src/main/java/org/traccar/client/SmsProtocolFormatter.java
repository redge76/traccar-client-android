/*
 * Copyright 2012 - 2015 Anton Tananaev (anton.tananaev@gmail.com)
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

public class SmsProtocolFormatter {

    public static String formatRequest(String address, int port, Position position) {

        String message = "position"
                .concat("id").concat(position.getDeviceId())
                .concat("timestamp").concat(String.valueOf(position.getTime().getTime() / 1000))
                .concat("lat").concat(String.valueOf(position.getLatitude()))
                .concat("lon").concat(String.valueOf(position.getLongitude()))
                .concat("speed").concat(String.valueOf(position.getSpeed() * 1.943844))
                .concat("bearing").concat(String.valueOf(position.getCourse()))
                .concat("altitude").concat(String.valueOf(position.getAltitude()))
                .concat("batt").concat(String.valueOf(position.getBattery()));

        return message;
    }

}
