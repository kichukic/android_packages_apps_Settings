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

import android.content.Context
import android.os.SystemProperties
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settings.R

class BrandBannerPreference(context: Context, attrs: AttributeSet) :
    Preference(context, attrs) {

    init {
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val version = SystemProperties.get("ro.kic.version", "24.0")
        holder.itemView.findViewById<TextView>(R.id.banner_title)?.text =
            context.getString(R.string.kic_banner_title_format, version)

        val modVersion = SystemProperties.get("ro.modversion", "")
        holder.itemView.findViewById<TextView>(R.id.banner_subtitle)?.text =
            if (modVersion.isNotBlank()) modVersion else "Android 17 | haydn"
    }
}
