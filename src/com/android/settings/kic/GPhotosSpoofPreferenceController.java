package com.android.settings.kic;

import android.content.Context;
import android.os.SystemProperties;
import androidx.annotation.NonNull;
import com.android.settings.core.TogglePreferenceController;

public class GPhotosSpoofPreferenceController extends TogglePreferenceController {

    private static final String PROP_KEY = "persist.sys.kic.gphotos_spoof";

    public GPhotosSpoofPreferenceController(@NonNull Context context, @NonNull String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return SystemProperties.getBoolean(PROP_KEY, true);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        SystemProperties.set(PROP_KEY, isChecked ? "true" : "false");
        return true;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return 0;
    }
}
