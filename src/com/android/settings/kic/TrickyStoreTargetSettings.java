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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TrickyStoreTargetSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceClickListener {

    private static final String TAG = "TrickyStoreTargetSettings";

    private static final String SETTING_SPOOF_TRICKYSTORE_TARGET = "spoof_trickystore_target";

    private static final String KEY_ACTION_CATEGORY = "kic_target_action_category";
    private static final String KEY_BANKING_QUICK = "kic_target_banking_quick";
    private static final String KEY_RESET_DEFAULTS = "kic_target_reset_defaults";
    private static final String KEY_CATEGORY_TARGETED = "kic_category_targeted";
    private static final String KEY_CATEGORY_AVAILABLE = "kic_category_available";

    private static final int MENU_TOGGLE_SYSTEM = 1;

    // Stock KIC OS Keystore targets
    private static final String DEFAULT_TARGETS = String.join("\n",
            "android",
            "com.android.vending",
            "com.google.android.gsf",
            "com.google.android.gms",
            "com.google.android.contactkeys",
            "com.google.android.ims",
            "com.google.android.safetycore",
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nbu.paisa.user",
            "com.revolut.revolut!",
            "io.github.qwq233.keyattestation?",
            "io.github.vvb2060.keyattestation?",
            "io.github.vvb2060.mahoshojo?",
            "icu.nullptr.nativetest?",
            "com.reveny.nativecheck?",
            "com.zhenxi.hunter?",
            "com.android.nativetest?",
            "io.liankong.riskdetector?",
            "luna.safe.luna?",
            "com.eltavine.duckdetector?",
            "com.rem01gaming.disclosure?",
            "wu.keyChain.test?",
            "com.kikyps.crackme?",
            "com.chunqiunativecheck?"
    );

    // Common banking, UPI, and financial apps for one-touch auto-targeting
    private static final Set<String> COMMON_BANKING_PACKAGES = Set.of(
            "com.google.android.apps.nbu.paisa.user",
            "com.google.android.apps.walletnfcrel",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "com.sbi.lotusintouch",
            "com.msf.kbank.mobile",
            "com.hdfcbank.android",
            "com.icicibank.mobile",
            "com.axis.mobile",
            "com.revolut.revolut",
            "com.chase.sig.android",
            "com.bankofamerica.mobilebanking",
            "com.citi.citimobile",
            "com.wf.wellsfargomobile",
            "com.capitalone.mobile",
            "com.barclays.android.barclaysmobilebanking",
            "uk.co.santander.santanderUK",
            "com.starlingbank",
            "co.uk.monzo",
            "com.paypal.android.p2pmobile",
            "com.binance.dev",
            "com.coinbase.android",
            "id.dana",
            "com.shopee.id",
            "com.grabtaxi.passenger",
            "com.gojek.app"
    );

    private static class AppInfoItem implements Comparable<AppInfoItem> {
        final String packageName;
        final String label;
        final Drawable icon;
        final boolean isSystem;
        final boolean isBanking;

        AppInfoItem(String packageName, String label, Drawable icon, boolean isSystem, boolean isBanking) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.isSystem = isSystem;
            this.isBanking = isBanking;
        }

        @Override
        public int compareTo(@NonNull AppInfoItem o) {
            return this.label.compareToIgnoreCase(o.label);
        }
    }

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean mShowSystem = false;
    private final List<AppInfoItem> mLoadedApps = new ArrayList<>();
    private final Set<String> mTargetPackages = new HashSet<>();
    private final Map<String, String> mOriginalTargetEntries = new HashMap<>();

    private Preference mBankingQuickPref;
    private Preference mResetDefaultsPref;
    private PreferenceCategory mTargetedCategory;
    private PreferenceCategory mAvailableCategory;

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

        PreferenceCategory actionCategory = new PreferenceCategory(getPrefContext());
        actionCategory.setKey(KEY_ACTION_CATEGORY);
        screen.addPreference(actionCategory);

        mBankingQuickPref = new Preference(getPrefContext());
        mBankingQuickPref.setKey(KEY_BANKING_QUICK);
        mBankingQuickPref.setTitle(R.string.kic_target_banking_quick_title);
        mBankingQuickPref.setSummary(R.string.kic_target_banking_quick_summary);
        mBankingQuickPref.setOnPreferenceClickListener(this);
        actionCategory.addPreference(mBankingQuickPref);

        mResetDefaultsPref = new Preference(getPrefContext());
        mResetDefaultsPref.setKey(KEY_RESET_DEFAULTS);
        mResetDefaultsPref.setTitle(R.string.kic_target_reset_defaults_title);
        mResetDefaultsPref.setSummary(R.string.kic_target_reset_defaults_summary);
        mResetDefaultsPref.setOnPreferenceClickListener(this);
        actionCategory.addPreference(mResetDefaultsPref);

        mTargetedCategory = new PreferenceCategory(getPrefContext());
        mTargetedCategory.setKey(KEY_CATEGORY_TARGETED);
        mTargetedCategory.setTitle(getString(R.string.kic_category_targeted, 0));
        screen.addPreference(mTargetedCategory);

        mAvailableCategory = new PreferenceCategory(getPrefContext());
        mAvailableCategory.setKey(KEY_CATEGORY_AVAILABLE);
        mAvailableCategory.setTitle(getString(R.string.kic_category_available, 0));
        screen.addPreference(mAvailableCategory);

        loadTargetsFromSettings();
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
    public boolean onPreferenceClick(@NonNull Preference preference) {
        if (preference == mBankingQuickPref) {
            autoTargetBankingApps();
            return true;
        } else if (preference == mResetDefaultsPref) {
            confirmResetDefaults();
            return true;
        }
        return false;
    }

    private void loadTargetsFromSettings() {
        Context ctx = getContext();
        if (ctx == null) return;

        String current = Settings.Secure.getString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_TARGET);
        if (current == null) {
            current = DEFAULT_TARGETS;
        }

        mTargetPackages.clear();
        mOriginalTargetEntries.clear();

        for (String raw : current.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.endsWith("-")) continue;
            String bare = line.endsWith("!") || line.endsWith("?")
                    ? line.substring(0, line.length() - 1).trim() : line;
            mTargetPackages.add(bare);
            mOriginalTargetEntries.put(bare, line);
        }
    }

    private void saveTargetsToSettings() {
        Context ctx = getContext();
        if (ctx == null) return;

        StringBuilder sb = new StringBuilder();
        for (String pkg : mTargetPackages) {
            if (sb.length() > 0) sb.append('\n');
            String original = mOriginalTargetEntries.get(pkg);
            if (!TextUtils.isEmpty(original) && !original.endsWith("-")) {
                sb.append(original);
            } else {
                sb.append(pkg);
            }
        }

        Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_TARGET, sb.toString());
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
                boolean isBanking = COMMON_BANKING_PACKAGES.contains(pkg) ||
                        pkg.contains("bank") || pkg.contains("wallet") || pkg.contains("paisa") ||
                        pkg.contains("paytm") || pkg.contains("phonepe") || pkg.contains("upi");

                items.add(new AppInfoItem(pkg, label, icon, isSystem, isBanking));
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
        if (mTargetedCategory == null || mAvailableCategory == null) return;

        mTargetedCategory.removeAll();
        mAvailableCategory.removeAll();

        int targetedCount = 0;
        int availableCount = 0;

        Context prefCtx = getPrefContext();

        for (AppInfoItem item : mLoadedApps) {
            boolean isTargeted = mTargetPackages.contains(item.packageName);

            if (item.isSystem && !mShowSystem && !isTargeted) {
                continue;
            }

            SwitchPreferenceCompat pref = new SwitchPreferenceCompat(prefCtx);
            pref.setKey("target_" + item.packageName);
            pref.setTitle(item.label);

            String summary = item.packageName;
            if (item.isBanking) {
                summary = getString(R.string.kic_badge_banking) + summary;
            }
            pref.setSummary(summary);
            pref.setIcon(item.icon);
            pref.setChecked(isTargeted);

            final String pkg = item.packageName;
            pref.setOnPreferenceChangeListener((p, newValue) -> {
                boolean checked = (Boolean) newValue;
                onAppTargetToggled(pkg, checked);
                return true;
            });

            if (isTargeted) {
                mTargetedCategory.addPreference(pref);
                targetedCount++;
            } else {
                mAvailableCategory.addPreference(pref);
                availableCount++;
            }
        }

        mTargetedCategory.setTitle(getString(R.string.kic_category_targeted, targetedCount));
        mAvailableCategory.setTitle(getString(R.string.kic_category_available, availableCount));
    }

    private void onAppTargetToggled(String packageName, boolean targeted) {
        if (targeted) {
            mTargetPackages.add(packageName);
            if (!mOriginalTargetEntries.containsKey(packageName)) {
                mOriginalTargetEntries.put(packageName, packageName);
            }
        } else {
            mTargetPackages.remove(packageName);
        }
        saveTargetsToSettings();
        populatePreferences();
    }

    private void autoTargetBankingApps() {
        Context ctx = getContext();
        if (ctx == null) return;

        int newlyAdded = 0;
        for (AppInfoItem item : mLoadedApps) {
            if (item.isBanking && !mTargetPackages.contains(item.packageName)) {
                mTargetPackages.add(item.packageName);
                mOriginalTargetEntries.put(item.packageName, item.packageName);
                newlyAdded++;
            }
        }

        if (newlyAdded > 0) {
            saveTargetsToSettings();
            populatePreferences();
            Toast.makeText(ctx, getString(R.string.kic_target_banking_success, newlyAdded), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(ctx, R.string.kic_target_banking_none, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmResetDefaults() {
        Context ctx = getContext();
        if (ctx == null) return;

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.kic_target_reset_defaults_title)
                .setMessage(R.string.kic_target_reset_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Settings.Secure.putString(ctx.getContentResolver(), SETTING_SPOOF_TRICKYSTORE_TARGET, DEFAULT_TARGETS);
                    loadTargetsFromSettings();
                    populatePreferences();
                    Toast.makeText(ctx, R.string.kic_target_reset_success, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
