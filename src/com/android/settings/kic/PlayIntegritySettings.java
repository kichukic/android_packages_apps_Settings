/*
 * Copyright (C) 2026 KIC OS
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

package com.android.settings.kic;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.security.trickystore.KeyBoxManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.android.PixelDeviceRepository;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SearchIndexable
public class PlayIntegritySettings extends DashboardFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "PlayIntegritySettings";

    private static final int REQUEST_CODE_PIF = 1001;
    private static final int REQUEST_CODE_KEYBOX = 1002;

    private static final String KEY_PIF_ENABLED = "kic_pif_enabled";
    private static final String KEY_PIF_FETCH = "kic_pif_fetch";
    private static final String KEY_PIF_IMPORT = "kic_pif_import";
    private static final String KEY_PIF_RESET = "kic_pif_reset";

    private static final String KEY_KEYBOX_ENABLED = "kic_keybox_enabled";
    private static final String KEY_KEYBOX_FETCH = "kic_keybox_fetch";
    private static final String KEY_KEYBOX_IMPORT = "kic_keybox_import";
    private static final String KEY_KEYBOX_RESET = "kic_keybox_reset";

    private static final String SETTING_SPOOF_PIF_CONFIG = "spoof_pif_config";
    private static final String SETTING_SPOOF_PIF_ENABLED = "spoof_pif_enabled";
    private static final String SETTING_SPOOF_TRICKYSTORE_KEYBOX = "spoof_trickystore_keybox";
    private static final String SETTING_SPOOF_TRICKYSTORE_ENABLED = "spoof_trickystore_enabled";
    private static final String SETTING_KEYBOX_SOURCE = "spoof_trickystore_keybox_source";

    private static final String[] OFFICIAL_KEYBOX_URLS = {
            "https://gitea.com/Evolution-X/keybox/raw/branch/main/keybox.xml",
            "https://git.evolution-x.org/EvoX/keybox/raw/branch/main/keybox.xml"
    };

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private SwitchPreferenceCompat mPifSwitch;
    private SwitchPreferenceCompat mKeyboxSwitch;
    private Preference mPifFetchPref;
    private Preference mPifImportPref;
    private Preference mKeyboxFetchPref;
    private Preference mKeyboxImportPref;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.kic_integrity_settings;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPifSwitch = findPreference(KEY_PIF_ENABLED);
        if (mPifSwitch != null) {
            mPifSwitch.setOnPreferenceChangeListener(this);
        }

        mKeyboxSwitch = findPreference(KEY_KEYBOX_ENABLED);
        if (mKeyboxSwitch != null) {
            mKeyboxSwitch.setOnPreferenceChangeListener(this);
        }

        mPifFetchPref = findPreference(KEY_PIF_FETCH);
        mPifImportPref = findPreference(KEY_PIF_IMPORT);
        mKeyboxFetchPref = findPreference(KEY_KEYBOX_FETCH);
        mKeyboxImportPref = findPreference(KEY_KEYBOX_IMPORT);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummariesAndSwitches();
    }

    private void updateSummariesAndSwitches() {
        Context ctx = getContext();
        if (ctx == null) return;

        if (mPifSwitch != null) {
            boolean pifEnabled = Settings.Secure.getInt(ctx.getContentResolver(),
                    SETTING_SPOOF_PIF_ENABLED, 1) != 0;
            mPifSwitch.setChecked(pifEnabled);
        }

        if (mKeyboxSwitch != null) {
            boolean keyboxEnabled = Settings.Secure.getInt(ctx.getContentResolver(),
                    SETTING_SPOOF_TRICKYSTORE_ENABLED, 1) != 0;
            mKeyboxSwitch.setChecked(keyboxEnabled);
        }

        String pif = Settings.Secure.getString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG);
        boolean isManualPif = false;
        String model = null;
        if (!TextUtils.isEmpty(pif)) {
            try {
                JSONObject obj = new JSONObject(pif);
                isManualPif = obj.optBoolean("manually_imported", false);
                model = obj.optString("MODEL", null);
            } catch (Exception ignored) {}
        }

        if (mPifFetchPref != null) {
            if (!TextUtils.isEmpty(pif) && !isManualPif) {
                mPifFetchPref.setSummary(getString(R.string.kic_pif_server_active)
                        + (model != null ? " (" + model + ")" : "")
                        + " \u2014 Tap to choose Pixel model");
            } else {
                mPifFetchPref.setSummary(getString(R.string.kic_pif_fetch_summary));
            }
        }

        if (mPifImportPref != null) {
            if (isManualPif) {
                mPifImportPref.setSummary(getString(R.string.kic_pif_custom_loaded)
                        + (model != null ? " (" + model + ")" : ""));
            } else {
                mPifImportPref.setSummary(getString(R.string.kic_pif_import_summary));
            }
        }

        String keybox = Settings.Secure.getString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_KEYBOX);
        String source = Settings.Secure.getString(ctx.getContentResolver(), SETTING_KEYBOX_SOURCE);

        if (mKeyboxFetchPref != null) {
            if (!TextUtils.isEmpty(keybox) && "official".equals(source)) {
                mKeyboxFetchPref.setSummary(getString(R.string.kic_keybox_server_active)
                        + " \u2014 Tap to re-fetch official XML");
            } else {
                mKeyboxFetchPref.setSummary(getString(R.string.kic_keybox_fetch_summary));
            }
        }

        if (mKeyboxImportPref != null) {
            if (!TextUtils.isEmpty(keybox) && "user".equals(source)) {
                mKeyboxImportPref.setSummary(getString(R.string.kic_keybox_custom_loaded));
            } else {
                mKeyboxImportPref.setSummary(getString(R.string.kic_keybox_import_summary));
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Context ctx = getContext();
        if (ctx == null) return false;

        String key = preference.getKey();
        if (KEY_PIF_ENABLED.equals(key)) {
            boolean enabled = (Boolean) newValue;
            Settings.Secure.putInt(ctx.getContentResolver(), SETTING_SPOOF_PIF_ENABLED, enabled ? 1 : 0);
            return true;
        } else if (KEY_KEYBOX_ENABLED.equals(key)) {
            boolean enabled = (Boolean) newValue;
            Settings.Secure.putInt(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_ENABLED, enabled ? 1 : 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (KEY_PIF_FETCH.equals(key)) {
            fetchPifFromServer();
            return true;
        } else if (KEY_PIF_IMPORT.equals(key)) {
            launchFilePicker("application/json", REQUEST_CODE_PIF);
            return true;
        } else if (KEY_PIF_RESET.equals(key)) {
            showResetDialog(R.string.kic_pif_reset_title, R.string.kic_pif_reset_confirm, this::resetPif);
            return true;
        } else if (KEY_KEYBOX_FETCH.equals(key)) {
            fetchKeyboxFromServer();
            return true;
        } else if (KEY_KEYBOX_IMPORT.equals(key)) {
            launchFilePicker("text/xml", REQUEST_CODE_KEYBOX);
            return true;
        } else if (KEY_KEYBOX_RESET.equals(key)) {
            showResetDialog(R.string.kic_keybox_reset_title, R.string.kic_keybox_reset_confirm, this::resetKeybox);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void fetchPifFromServer() {
        Context ctx = getContext();
        if (ctx == null) return;
        Toast.makeText(ctx, R.string.kic_pif_fetching_devices, Toast.LENGTH_SHORT).show();

        mExecutor.execute(() -> {
            try {
                List<PixelDeviceRepository.PixelProfile> profiles =
                        PixelDeviceRepository.getProfiles(ctx, true);
                if (profiles == null || profiles.isEmpty()) {
                    profiles = PixelDeviceRepository.FALLBACK_PROFILES;
                }

                final List<PixelDeviceRepository.PixelProfile> sortedProfiles = new ArrayList<>(profiles);
                final String defaultCodename = PixelDeviceRepository.getDefaultPifCodename(sortedProfiles);
                final String currentDevice = SystemProperties.get("ro.product.device", "");

                sortedProfiles.sort((a, b) -> {
                    if (defaultCodename.equals(a.codename) && !defaultCodename.equals(b.codename)) return -1;
                    if (!defaultCodename.equals(a.codename) && defaultCodename.equals(b.codename)) return 1;
                    if (currentDevice.equals(a.device) && !currentDevice.equals(b.device)) return -1;
                    if (!currentDevice.equals(a.device) && currentDevice.equals(b.device)) return 1;
                    int idxA = PixelDeviceRepository.PICKER_ORDER.indexOf(a.codename);
                    int idxB = PixelDeviceRepository.PICKER_ORDER.indexOf(b.codename);
                    if (idxA < 0) idxA = Integer.MAX_VALUE;
                    if (idxB < 0) idxB = Integer.MAX_VALUE;
                    return Integer.compare(idxA, idxB);
                });

                String currentSpoofedDevice = null;
                String pifConfig = Settings.Secure.getString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG);
                if (!TextUtils.isEmpty(pifConfig)) {
                    try {
                        currentSpoofedDevice = new JSONObject(pifConfig).optString("DEVICE", null);
                    } catch (Exception ignored) {}
                }

                final String[] modelNames = new String[sortedProfiles.size()];
                int preselectedIndex = 0;
                for (int i = 0; i < sortedProfiles.size(); i++) {
                    PixelDeviceRepository.PixelProfile p = sortedProfiles.get(i);
                    if (currentSpoofedDevice != null && currentSpoofedDevice.equals(p.device)) {
                        preselectedIndex = i;
                    } else if (currentSpoofedDevice == null && defaultCodename.equals(p.codename)) {
                        preselectedIndex = i;
                    }
                    if (defaultCodename.equals(p.codename)) {
                        modelNames[i] = p.model + " \u2014 " + getString(R.string.kic_pif_recommended_suffix);
                    } else {
                        modelNames[i] = p.model + (p.isCanary ? " (Canary)" : "");
                    }
                }

                final int finalPreselectedIndex = preselectedIndex;
                mMainHandler.post(() -> {
                    new AlertDialog.Builder(ctx)
                            .setTitle(R.string.kic_pif_select_device_title)
                            .setSingleChoiceItems(modelNames, finalPreselectedIndex, (dialog, which) -> {
                                dialog.dismiss();
                                applySelectedPixelProfile(sortedProfiles.get(which));
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch device profiles", e);
                mMainHandler.post(() -> {
                    Toast.makeText(ctx, getString(R.string.kic_pif_fetch_failed,
                            e.getMessage() != null ? e.getMessage() : "Network error"), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void applySelectedPixelProfile(PixelDeviceRepository.PixelProfile profile) {
        Context ctx = getContext();
        if (ctx == null || profile == null) return;

        if (!PixelDeviceRepository.isValidFingerprint(profile.fingerprint)) {
            Toast.makeText(ctx, getString(R.string.kic_pif_fetch_failed, "Invalid fingerprint"), Toast.LENGTH_SHORT).show();
            return;
        }

        mExecutor.execute(() -> {
            try {
                JSONObject pifJson = new JSONObject();
                String brandCap = (profile.brand != null && !profile.brand.isEmpty())
                        ? Character.toUpperCase(profile.brand.charAt(0)) + profile.brand.substring(1)
                        : "Google";
                pifJson.put("MANUFACTURER", brandCap);
                pifJson.put("BRAND", profile.brand);
                pifJson.put("MODEL", profile.model);
                pifJson.put("PRODUCT", profile.product);
                pifJson.put("DEVICE", profile.device);
                pifJson.put("FINGERPRINT", profile.fingerprint);
                pifJson.put("SECURITY_PATCH", profile.securityPatch);
                pifJson.put("DEVICE_INITIAL_SDK_INT", PixelDeviceRepository.getInitialSdkInt(profile.device));
                if (profile.isCanary && profile.securityPatch != null && profile.securityPatch.length() >= 7) {
                    pifJson.put("_canary_month", profile.securityPatch.substring(0, 7));
                    if (profile.releaseDate != null) {
                        pifJson.put("_canary_release_date", profile.releaseDate);
                    }
                }
                pifJson.put("manually_imported", false);

                Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG, pifJson.toString(2));
                Settings.Secure.putLong(ctx.getContentResolver(), "spoof_pif_last_auto_fetch", 0L);
                if (!TextUtils.isEmpty(profile.securityPatch)) {
                    Settings.Secure.putString(ctx.getContentResolver(), "spoof_trickystore_patch", profile.securityPatch);
                }

                restartGms(ctx);

                mMainHandler.post(() -> {
                    Toast.makeText(ctx, getString(R.string.kic_pif_fetch_success, profile.model), Toast.LENGTH_LONG).show();
                    updateSummariesAndSwitches();
                    showWipePlayStoreDialog();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply Pixel profile", e);
                mMainHandler.post(() -> {
                    Toast.makeText(ctx, getString(R.string.kic_pif_fetch_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showWipePlayStoreDialog() {
        Context ctx = getContext();
        if (ctx == null) return;
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.kic_pif_wipe_title)
                .setMessage(R.string.kic_pif_wipe_message)
                .setPositiveButton(R.string.kic_pif_wipe_action, (dialog, which) -> {
                    try {
                        ctx.getPackageManager().clearApplicationUserData("com.android.vending", null);
                        Toast.makeText(ctx, "Play Store cache cleared", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to clear Play Store data", e);
                    }
                })
                .setNegativeButton(R.string.kic_pif_wipe_cancel, null)
                .show();
    }

    private void fetchKeyboxFromServer() {
        Context ctx = getContext();
        if (ctx == null) return;
        Toast.makeText(ctx, R.string.kic_keybox_fetching, Toast.LENGTH_SHORT).show();

        mExecutor.execute(() -> {
            try {
                String xml = null;
                for (String urlStr : OFFICIAL_KEYBOX_URLS) {
                    try {
                        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(8000);
                        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            try (InputStream is = conn.getInputStream()) {
                                xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            }
                        }
                        conn.disconnect();
                        if (!TextUtils.isEmpty(xml) && looksLikeKeybox(xml)) {
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                if (TextUtils.isEmpty(xml) || !looksLikeKeybox(xml)) {
                    throw new IOException("Failed to download a valid keybox XML from mirror servers");
                }

                String validationProblem = KeyBoxManager.validate(xml);
                if (validationProblem != null) {
                    throw new IOException(validationProblem);
                }

                String encoded = Base64.encodeToString(xml.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_KEYBOX, encoded);
                Settings.Secure.putString(ctx.getContentResolver(), SETTING_KEYBOX_SOURCE, "official");

                restartGms(ctx);

                mMainHandler.post(() -> {
                    Toast.makeText(ctx, R.string.kic_keybox_fetch_success, Toast.LENGTH_LONG).show();
                    updateSummariesAndSwitches();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch keybox from server", e);
                mMainHandler.post(() -> {
                    Toast.makeText(ctx, getString(R.string.kic_keybox_fetch_failed,
                            e.getMessage() != null ? e.getMessage() : "Network error"), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private static boolean looksLikeKeybox(String xml) {
        if (xml == null) return false;
        String trimmed = xml.trim();
        return (trimmed.contains("<AndroidAttestationKeyBox") || trimmed.contains("<Keybox"))
                && trimmed.contains("<CertificateChain>")
                && trimmed.contains("<PrivateKey");
    }

    private void restartGms(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                for (String pkg : new String[]{"com.google.android.gms", "com.google.android.gms.unstable", "com.android.vending"}) {
                    am.forceStopPackage(pkg);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to force-stop GMS packages", e);
        }
    }

    private void launchFilePicker(String mimeType, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] extraMimes;
        if (requestCode == REQUEST_CODE_PIF) {
            extraMimes = new String[]{"application/json", "text/plain", "*/*"};
        } else {
            extraMimes = new String[]{"text/xml", "application/xml", "text/plain", "*/*"};
        }
        intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select File"), requestCode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch file picker", e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_CODE_PIF) {
            importPif(uri);
        } else if (requestCode == REQUEST_CODE_KEYBOX) {
            importKeybox(uri);
        }
    }

    private void importPif(Uri uri) {
        Context ctx = getContext();
        if (ctx == null) return;
        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            String content = sb.toString().trim();
            if (TextUtils.isEmpty(content)) {
                Toast.makeText(ctx, R.string.kic_pif_empty_error, Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject json = new JSONObject(content);
                json.put("manually_imported", true);
                content = json.toString(2);
            } catch (Exception ignored) {}
            Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG, content);
            restartGms(ctx);
            Toast.makeText(ctx, R.string.kic_pif_import_success, Toast.LENGTH_SHORT).show();
            updateSummariesAndSwitches();
        } catch (Exception e) {
            Log.e(TAG, "Failed to import PIF", e);
            Toast.makeText(ctx, R.string.kic_pif_import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void importKeybox(Uri uri) {
        Context ctx = getContext();
        if (ctx == null) return;
        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            String content = sb.toString().trim();
            if (TextUtils.isEmpty(content)) {
                Toast.makeText(ctx, R.string.kic_keybox_empty_error, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!content.contains("<AndroidAttestationKeyBox") && !content.contains("<Keybox")) {
                Toast.makeText(ctx, R.string.kic_keybox_invalid_error, Toast.LENGTH_LONG).show();
                return;
            }
            Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_KEYBOX, content);
            Settings.Secure.putString(ctx.getContentResolver(), SETTING_KEYBOX_SOURCE, "user");
            restartGms(ctx);
            Toast.makeText(ctx, R.string.kic_keybox_import_success, Toast.LENGTH_LONG).show();
            updateSummariesAndSwitches();
        } catch (Exception e) {
            Log.e(TAG, "Failed to import Keybox", e);
            Toast.makeText(ctx, R.string.kic_keybox_import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetPif() {
        Context ctx = getContext();
        if (ctx == null) return;
        Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG, null);
        restartGms(ctx);
        Toast.makeText(ctx, R.string.kic_pif_reset_success, Toast.LENGTH_SHORT).show();
        updateSummariesAndSwitches();
    }

    private void resetKeybox() {
        Context ctx = getContext();
        if (ctx == null) return;
        Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_KEYBOX, null);
        Settings.Secure.putString(ctx.getContentResolver(), SETTING_KEYBOX_SOURCE, null);
        restartGms(ctx);
        Toast.makeText(ctx, R.string.kic_keybox_reset_success, Toast.LENGTH_SHORT).show();
        updateSummariesAndSwitches();
    }

    private void showResetDialog(int titleRes, int messageRes, Runnable onConfirm) {
        Context ctx = getContext();
        if (ctx == null) return;
        new AlertDialog.Builder(ctx)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> onConfirm.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.kic_integrity_settings;
                    return Arrays.asList(sir);
                }
            };
}
