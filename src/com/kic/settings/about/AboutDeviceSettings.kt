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

import android.app.settings.SettingsEnums
import android.content.Context
import android.provider.SearchIndexableResource
import com.android.settings.R
import com.android.settings.dashboard.DashboardFragment
import com.android.settings.deviceinfo.firmwareversion.FirmwareVersionScreen
import com.android.settings.search.BaseSearchIndexProvider
import com.android.settingslib.core.AbstractPreferenceController
import com.android.settingslib.search.Indexable
import com.android.settingslib.search.SearchIndexable

@SearchIndexable
class AboutDeviceSettings : DashboardFragment() {

    override fun getPreferenceScreenBindingKey(context: Context): String =
        FirmwareVersionScreen.KEY

    override fun getPreferenceScreenResId(): Int = R.xml.kic_about_device

    override fun getLogTag(): String = TAG

    override fun getMetricsCategory(): Int = SettingsEnums.DIALOG_FIRMWARE_VERSION

    override fun createPreferenceControllers(context: Context): List<AbstractPreferenceController> =
        listOf(
            AboutPreferenceController(context),
            SecurityStatusPreferenceController(context)
        )

    companion object {
        private const val TAG = "AboutDeviceSettings"

        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER: Indexable.SearchIndexProvider =
            object : BaseSearchIndexProvider() {
                override fun getXmlResourcesToIndex(
                    context: Context,
                    enabled: Boolean
                ): List<SearchIndexableResource> =
                    listOf(
                        SearchIndexableResource(context).apply {
                            xmlResId = R.xml.kic_about_device
                        }
                    )
            }
    }
}
