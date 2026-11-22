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

package com.kic.settings.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemProperties
import android.util.Log
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.core.BasePreferenceController

class MaintainerPreferenceController(context: Context, key: String) :
    BasePreferenceController(context, key) {

    companion object {
        private const val TAG = "MaintainerPreferenceController"
        private const val URL_GITHUB = "https://github.com/kichukic"
        private const val KIC_MAINTAINER = "ro.kic.maintainer"
    }

    private val maintainer: String = SystemProperties.get(
        KIC_MAINTAINER,
        mContext.getString(R.string.kic_maintainer_default)
    )

    override fun getAvailabilityStatus(): Int =
        if (maintainer.isNotBlank()) AVAILABLE else CONDITIONALLY_UNAVAILABLE

    override fun getSummary(): CharSequence = maintainer

    override fun handlePreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key != preferenceKey) return false

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL_GITHUB))
        try {
            mContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app available to open $URL_GITHUB", e)
        }
        return true
    }
}
