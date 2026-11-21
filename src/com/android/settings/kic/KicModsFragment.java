package com.android.settings.kic;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

@SearchIndexable
public class KicModsFragment extends DashboardFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "KicModsFragment";

    private static final int REQUEST_CODE_PIF = 1001;
    private static final int REQUEST_CODE_KEYBOX = 1002;

    private static final String KEY_PIF_ENABLED = "kic_pif_enabled";
    private static final String KEY_PIF_IMPORT = "kic_pif_import";
    private static final String KEY_PIF_RESET = "kic_pif_reset";

    private static final String KEY_KEYBOX_ENABLED = "kic_keybox_enabled";
    private static final String KEY_KEYBOX_IMPORT = "kic_keybox_import";
    private static final String KEY_KEYBOX_RESET = "kic_keybox_reset";

    private static final String SETTING_SPOOF_PIF_CONFIG = "spoof_pif_config";
    private static final String SETTING_SPOOF_PIF_ENABLED = "spoof_pif_enabled";
    private static final String SETTING_SPOOF_TRICKYSTORE_KEYBOX = "spoof_trickystore_keybox";
    private static final String SETTING_SPOOF_TRICKYSTORE_ENABLED = "spoof_trickystore_enabled";
    private static final String SETTING_KEYBOX_SOURCE = "spoof_trickystore_keybox_source";

    private SwitchPreferenceCompat mPifSwitch;
    private SwitchPreferenceCompat mKeyboxSwitch;
    private Preference mPifImportPref;
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
        return R.xml.kic_mods_settings;
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

        mPifImportPref = findPreference(KEY_PIF_IMPORT);
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

        if (mPifImportPref != null) {
            String pif = Settings.Secure.getString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG);
            if (!TextUtils.isEmpty(pif)) {
                mPifImportPref.setSummary(getString(R.string.kic_pif_custom_loaded));
            } else {
                mPifImportPref.setSummary(getString(R.string.kic_pif_default_active));
            }
        }

        if (mKeyboxImportPref != null) {
            String keybox = Settings.Secure.getString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_KEYBOX);
            String source = Settings.Secure.getString(ctx.getContentResolver(), SETTING_KEYBOX_SOURCE);
            if (!TextUtils.isEmpty(keybox) && "user".equals(source)) {
                mKeyboxImportPref.setSummary(getString(R.string.kic_keybox_custom_loaded));
            } else {
                mKeyboxImportPref.setSummary(getString(R.string.kic_keybox_default_active));
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Context ctx = getContext();
        if (ctx == null) return false;

        if (KEY_PIF_ENABLED.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            Settings.Secure.putInt(ctx.getContentResolver(), SETTING_SPOOF_PIF_ENABLED, enabled ? 1 : 0);
            return true;
        } else if (KEY_KEYBOX_ENABLED.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            Settings.Secure.putInt(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_ENABLED, enabled ? 1 : 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == null) return super.onPreferenceTreeClick(preference);

        final String key = preference.getKey();
        if (KEY_PIF_IMPORT.equals(key)) {
            launchFilePicker(REQUEST_CODE_PIF);
            return true;
        } else if (KEY_PIF_RESET.equals(key)) {
            showResetDialog(R.string.kic_pif_reset_title, R.string.kic_pif_reset_confirm, this::resetPif);
            return true;
        } else if (KEY_KEYBOX_IMPORT.equals(key)) {
            launchFilePicker(REQUEST_CODE_KEYBOX);
            return true;
        } else if (KEY_KEYBOX_RESET.equals(key)) {
            showResetDialog(R.string.kic_keybox_reset_title, R.string.kic_keybox_reset_confirm, this::resetKeybox);
            return true;
        }

        return super.onPreferenceTreeClick(preference);
    }

    private void launchFilePicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
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
            Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG, content);
            Toast.makeText(ctx, R.string.kic_pif_import_success, Toast.LENGTH_LONG).show();
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
        File defaultFile = new File("/product/etc/security/pif.json");
        if (!defaultFile.exists()) {
            defaultFile = new File("/system/etc/security/pif.json");
        }
        if (defaultFile.exists()) {
            try {
                String defaultContent = new String(Files.readAllBytes(defaultFile.toPath()), StandardCharsets.UTF_8);
                Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG, defaultContent);
            } catch (Exception e) {
                Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG, "");
            }
        } else {
            Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_PIF_CONFIG, "");
        }
        Toast.makeText(ctx, R.string.kic_pif_reset_success, Toast.LENGTH_SHORT).show();
        updateSummariesAndSwitches();
    }

    private void resetKeybox() {
        Context ctx = getContext();
        if (ctx == null) return;
        File defaultFile = new File("/product/etc/security/keybox.xml");
        if (!defaultFile.exists()) {
            defaultFile = new File("/system/etc/security/keybox.xml");
        }
        if (defaultFile.exists()) {
            try {
                byte[] bytes = Files.readAllBytes(defaultFile.toPath());
                String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
                Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_KEYBOX, encoded);
                Settings.Secure.putString(ctx.getContentResolver(), SETTING_KEYBOX_SOURCE, "default");
            } catch (Exception e) {
                Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_KEYBOX, "");
                Settings.Secure.putString(ctx.getContentResolver(), SETTING_KEYBOX_SOURCE, "default");
            }
        } else {
            Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_KEYBOX, "");
            Settings.Secure.putString(ctx.getContentResolver(), SETTING_KEYBOX_SOURCE, "default");
        }
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
                    sir.xmlResId = R.xml.kic_mods_settings;
                    return Arrays.asList(sir);
                }
            };
}
