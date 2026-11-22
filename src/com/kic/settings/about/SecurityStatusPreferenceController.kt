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
import android.os.SELinux
import android.widget.TextView
import androidx.preference.PreferenceScreen
import com.android.settings.R
import com.android.settingslib.DeviceInfoUtils
import com.android.settingslib.core.AbstractPreferenceController
import com.android.settingslib.widget.LayoutPreference

class SecurityStatusPreferenceController(context: Context) : AbstractPreferenceController(context) {

    companion object {
        private const val KEY_KIC_ABOUT_FOOTER_INFO = "about_phone_info_footer"
    }

    override fun displayPreference(screen: PreferenceScreen) {
        super.displayPreference(screen)
        val layout = screen.findPreference<LayoutPreference>(KEY_KIC_ABOUT_FOOTER_INFO) ?: return

        layout.findViewById<TextView>(R.id.selinux_summary)?.text =
            mContext.getString(
                if (SELinux.isSELinuxEnforced()) R.string.selinux_status_enforcing
                else R.string.selinux_status_permissive
            )

        layout.findViewById<TextView>(R.id.secpatch_summary)?.text =
            DeviceInfoUtils.getSecurityPatch()
    }

    override fun isAvailable(): Boolean = true
    override fun getPreferenceKey(): String = KEY_KIC_ABOUT_FOOTER_INFO
}
