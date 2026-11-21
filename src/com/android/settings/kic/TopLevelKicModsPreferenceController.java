package com.android.settings.kic;

import android.content.Context;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class TopLevelKicModsPreferenceController extends BasePreferenceController {

    public TopLevelKicModsPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(R.string.kic_mods_summary);
    }
}
