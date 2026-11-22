package com.android.settings.kic;

import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PerAppSpoofSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "PerAppSpoofSettings";

    private static final String SETTING_PER_APPS_DEVICE_SPOOF = "per_apps_device_spoof";
    private static final String SETTING_PER_APPS_DEVICE_SPOOF_ENABLED = "per_apps_device_spoof_enabled";

    private static final String KEY_MASTER_SWITCH = "per_app_spoof_master_switch";
    private static final String KEY_CATEGORY_CONFIGURED = "category_configured_apps";
    private static final String KEY_CATEGORY_ALL = "category_all_apps";

    private static final int MENU_TOGGLE_SYSTEM = 1;

    // Ordered list of built-in device profiles
    public static final Map<String, String> PROFILES = new LinkedHashMap<>();
    static {
        PROFILES.put("ROG9P", "ASUS ROG Phone 9 Pro (165Hz)");
        PROFILES.put("RM10P", "RedMagic 10 Pro (Snapdragon 8 Elite)");
        PROFILES.put("RM9P", "RedMagic 9 Pro");
        PROFILES.put("BS4C", "Black Shark 4");
        PROFILES.put("S25U", "Samsung Galaxy S25 Ultra");
        PROFILES.put("GZF5", "Samsung Galaxy Z Fold 5");
        PROFILES.put("OP13", "OnePlus 13");
        PROFILES.put("OP12", "OnePlus 12");
        PROFILES.put("MI14P", "Xiaomi 14 Pro");
        PROFILES.put("MI13P", "Xiaomi 13 Pro");
        PROFILES.put("MI13", "Xiaomi 13");
        PROFILES.put("MI11TP", "Xiaomi 11T Pro");
        PROFILES.put("F5", "POCO F5");
        PROFILES.put("PXL11PXL", "Google Pixel 11 Pro XL");
        PROFILES.put("HMV2R", "HONOR Magic V2 RSR");
        PROFILES.put("LY70023", "Lenovo Legion Y700 Tablet");
        PROFILES.put("RM15P5G", "Realme 15 Pro 5G");
    }

    private static class AppInfoItem implements Comparable<AppInfoItem> {
        final String packageName;
        final String label;
        final Drawable icon;
        final boolean isSystem;

        AppInfoItem(String packageName, String label, Drawable icon, boolean isSystem) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.isSystem = isSystem;
        }

        @Override
        public int compareTo(@NonNull AppInfoItem o) {
            return this.label.compareToIgnoreCase(o.label);
        }
    }

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean mShowSystem = false;
    private final List<AppInfoItem> mLoadedApps = new ArrayList<>();
    private final Map<String, String> mAppSpoofMap = new HashMap<>();

    private SwitchPreferenceCompat mMasterSwitch;
    private PreferenceCategory mConfiguredCategory;
    private PreferenceCategory mAllCategory;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getPrefContext());
        setPreferenceScreen(screen);

        mMasterSwitch = new SwitchPreferenceCompat(getPrefContext());
        mMasterSwitch.setKey(KEY_MASTER_SWITCH);
        mMasterSwitch.setTitle(R.string.kic_per_app_spoof_master_title);
        mMasterSwitch.setSummary(R.string.kic_per_app_spoof_master_summary);
        mMasterSwitch.setOnPreferenceChangeListener(this);
        screen.addPreference(mMasterSwitch);

        mConfiguredCategory = new PreferenceCategory(getPrefContext());
        mConfiguredCategory.setKey(KEY_CATEGORY_CONFIGURED);
        mConfiguredCategory.setTitle(getString(R.string.kic_category_configured, 0));
        screen.addPreference(mConfiguredCategory);

        mAllCategory = new PreferenceCategory(getPrefContext());
        mAllCategory.setKey(KEY_CATEGORY_ALL);
        mAllCategory.setTitle(getString(R.string.kic_category_all_apps, 0));
        screen.addPreference(mAllCategory);

        loadSettings();
        loadInstalledPackagesAsync();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_TOGGLE_SYSTEM, 0, mShowSystem ? R.string.kic_menu_hide_system : R.string.kic_menu_show_system);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_TOGGLE_SYSTEM) {
            mShowSystem = !mShowSystem;
            item.setTitle(mShowSystem ? R.string.kic_menu_hide_system : R.string.kic_menu_show_system);
            populatePreferences();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        if (preference == mMasterSwitch) {
            boolean enabled = (Boolean) newValue;
            Context ctx = getContext();
            if (ctx != null) {
                Settings.Secure.putInt(ctx.getContentResolver(), SETTING_PER_APPS_DEVICE_SPOOF_ENABLED, enabled ? 1 : 0);
            }
            return true;
        }
        return false;
    }

    private void loadSettings() {
        Context ctx = getContext();
        if (ctx == null) return;

        boolean enabled = Settings.Secure.getInt(ctx.getContentResolver(), SETTING_PER_APPS_DEVICE_SPOOF_ENABLED, 1) == 1;
        if (mMasterSwitch != null) {
            mMasterSwitch.setChecked(enabled);
        }

        mAppSpoofMap.clear();
        String current = Settings.Secure.getString(ctx.getContentResolver(), SETTING_PER_APPS_DEVICE_SPOOF);
        if (!TextUtils.isEmpty(current)) {
            for (String entry : current.split(",")) {
                String[] parts = entry.split(":");
                if (parts.length == 2 && !TextUtils.isEmpty(parts[0]) && !TextUtils.isEmpty(parts[1])) {
                    mAppSpoofMap.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

    private void saveSettings() {
        Context ctx = getContext();
        if (ctx == null) return;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : mAppSpoofMap.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }

        Settings.Secure.putString(ctx.getContentResolver(), SETTING_PER_APPS_DEVICE_SPOOF, sb.toString());
    }

    private void loadInstalledPackagesAsync() {
        Context ctx = getContext();
        if (ctx == null) return;

        final PackageManager pm = ctx.getPackageManager();

        new Thread(() -> {
            List<PackageInfo> installedPackages = pm.getInstalledPackages(0);
            List<AppInfoItem> items = new ArrayList<>();

            for (PackageInfo pi : installedPackages) {
                if (pi.applicationInfo == null) continue;
                String pkg = pi.packageName;
                String label = pi.applicationInfo.loadLabel(pm).toString();
                Drawable icon = pi.applicationInfo.loadIcon(pm);
                boolean isSystem = (pi.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;

                items.add(new AppInfoItem(pkg, label, icon, isSystem));
            }

            Collections.sort(items);

            mMainHandler.post(() -> {
                if (getActivity() == null || isDetached()) return;
                mLoadedApps.clear();
                mLoadedApps.addAll(items);
                populatePreferences();
            });
        }).start();
    }

    private void populatePreferences() {
        if (mConfiguredCategory == null || mAllCategory == null) return;

        mConfiguredCategory.removeAll();
        mAllCategory.removeAll();

        int configuredCount = 0;
        int allCount = 0;

        Context prefCtx = getPrefContext();

        for (AppInfoItem item : mLoadedApps) {
            String profileId = mAppSpoofMap.get(item.packageName);
            boolean isConfigured = !TextUtils.isEmpty(profileId);

            if (item.isSystem && !mShowSystem && !isConfigured) {
                continue;
            }

            Preference pref = new Preference(prefCtx);
            pref.setKey("spoof_" + item.packageName);
            pref.setTitle(item.label);
            pref.setIcon(item.icon);

            String profileName = isConfigured ? PROFILES.get(profileId) : null;
            if (profileName != null) {
                pref.setSummary(profileName + " (" + item.packageName + ")");
            } else {
                pref.setSummary(item.packageName);
            }

            final String pkg = item.packageName;
            pref.setOnPreferenceClickListener(p -> {
                showProfileChooserDialog(pkg, item.label);
                return true;
            });

            if (isConfigured) {
                mConfiguredCategory.addPreference(pref);
                configuredCount++;
            } else {
                mAllCategory.addPreference(pref);
                allCount++;
            }
        }

        mConfiguredCategory.setTitle(getString(R.string.kic_category_configured, configuredCount));
        mAllCategory.setTitle(getString(R.string.kic_category_all_apps, allCount));
    }

    private void showProfileChooserDialog(String packageName, String label) {
        Context ctx = getContext();
        if (ctx == null) return;

        List<String> keys = new ArrayList<>(PROFILES.keySet());
        List<String> names = new ArrayList<>(PROFILES.values());

        // Insert "Default (No Spoofing)" option at top
        names.add(0, getString(R.string.kic_profile_default));

        String currentKey = mAppSpoofMap.get(packageName);
        int selectedIndex = 0;
        if (currentKey != null) {
            int idx = keys.indexOf(currentKey);
            if (idx >= 0) {
                selectedIndex = idx + 1;
            }
        }

        CharSequence[] items = names.toArray(new CharSequence[0]);

        new AlertDialog.Builder(ctx)
                .setTitle(label)
                .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                    if (which == 0) {
                        mAppSpoofMap.remove(packageName);
                    } else {
                        String chosenKey = keys.get(which - 1);
                        mAppSpoofMap.put(packageName, chosenKey);
                    }
                    saveSettings();
                    populatePreferences();
                    dialog.dismiss();
                    Toast.makeText(ctx, R.string.kic_per_app_spoof_applied, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
