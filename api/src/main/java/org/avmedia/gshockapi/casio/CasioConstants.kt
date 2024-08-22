/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-27, 9:52 a.m.
 */
package org.avmedia.gshockapi.casio

import java.util.UUID

object CasioConstants {

    val WATCH_FEATURES_SERVICE_UUID: UUID =
        UUID.fromString("26eb000d-b012-49a8-b1f8-394fb2032b0f")

    // Modern Watches - All Features
    val CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("26eb002c-b012-49a8-b1f8-394fb2032b0f")

    val CASIO_ALL_FEATURES_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("26eb002d-b012-49a8-b1f8-394fb2032b0f")

    enum class CHARACTERISTICS(val code: Int) {
        CASIO_WATCH_NAME(0x23),
        CASIO_APP_INFORMATION(0x22),
        CASIO_BLE_FEATURES(0x10),
        CASIO_SETTING_FOR_BLE(0x11),
        CASIO_WATCH_CONDITION(0x28), // battery %
        CASIO_DST_WATCH_STATE(0x1d),
        CASIO_DST_SETTING(0x1e),
        CASIO_CURRENT_TIME(0x09),
        CASIO_SETTING_FOR_ALM(0x15),
        CASIO_SETTING_FOR_ALM2(0x16),
        CASIO_SETTING_FOR_BASIC(0x13),
        CASIO_CURRENT_TIME_MANAGER(0x39),
        CASIO_WORLD_CITIES(0x1f),
        CASIO_REMINDER_TITLE(0x30),
        CASIO_REMINDER_TIME(0x31),
        CASIO_TIMER(0x18),
        ERROR(0xFF),

        // ECB-30
        CMD_SET_TIMEMODE(0x47),
        FIND_PHONE(0x0A),
    }
}