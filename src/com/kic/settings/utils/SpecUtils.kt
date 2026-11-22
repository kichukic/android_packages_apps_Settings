/*
 * Copyright (C) 2025 OrionOS Project
 * Copyright (C) 2026 The KIC OS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kic.settings.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemProperties
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import kotlin.math.roundToInt

object SpecUtils {
    private const val TAG = "SpecUtils"
    private const val POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile"

    private const val PROP_SCREEN_RESOLUTION = "ro.screen_resolution"
    private const val PROP_BUILD_STATUS = "ro.kic.build.status"

    fun getTotalInternalMemorySize(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.blockCountLong * stat.blockSizeLong / 1073741824.0
        return when (totalGb.roundToInt()) {
            in 0..16 -> "16"
            in 17..32 -> "32"
            in 33..64 -> "64"
            in 65..128 -> "128"
            in 129..256 -> "256"
            in 257..512 -> "512"
            else -> "512+"
        }
    }

    fun getTotalRAM(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return (info.totalMem / 1073741824.0).roundToInt()
    }

    fun getBatteryCapacity(context: Context): Int {
        return try {
            val clazz = Class.forName(POWER_PROFILE_CLASS)
            val profile = clazz.getConstructor(Context::class.java).newInstance(context)
            val capacity = clazz.getMethod("getAveragePower", String::class.java)
                .invoke(profile, "battery.capacity") as? Double ?: return 4520
            if (capacity > 0) capacity.roundToInt() else 4520
        } catch (e: Exception) {
            Log.w(TAG, "Battery capacity not available", e)
            4520
        }
    }

    fun getScreenResolution(context: Context): String {
        SystemProperties.get(PROP_SCREEN_RESOLUTION)
            .takeIf { it.isNotBlank() }
            ?.let { return it.replace("x", " x ").replace(Regex("\\s+"), " ").trim() }

        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.currentWindowMetrics.bounds
                "${b.width()} x ${b.height()}"
            } else {
                @Suppress("DEPRECATION")
                val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
                "${dm.widthPixels} x ${dm.heightPixels}"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Screen resolution not available", e)
            "1080 x 2400"
        }
    }

    fun getCpuModel(): String {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL?.takeIf { it.isNotBlank() }
        } else null
        val model = soc
            ?: SystemProperties.get("ro.soc.model").takeIf { it.isNotBlank() }
            ?: SystemProperties.get("ro.hardware.chipname").takeIf { it.isNotBlank() }
            ?: cpuFromProc()
            ?: Build.HARDWARE

        return when {
            model.equals("SM8350", ignoreCase = true) || model.contains("888", ignoreCase = true) ->
                "Qualcomm Snapdragon 888 5G"
            model.isNotBlank() -> model
            else -> "Qualcomm Snapdragon 888 5G"
        }
    }

    private fun cpuFromProc(): String? = try {
        File("/proc/cpuinfo").readLines().firstNotNullOfOrNull { line ->
            val key = line.substringBefore(':').trim()
            if (key in listOf("Hardware", "model name", "Processor")) {
                line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
            } else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error reading /proc/cpuinfo", e)
        null
    }

    fun getBuildStatus(): String {
        val status = SystemProperties.get(PROP_BUILD_STATUS, "Unofficial").trim()
        return status.replaceFirstChar { it.uppercase() }
    }
}
