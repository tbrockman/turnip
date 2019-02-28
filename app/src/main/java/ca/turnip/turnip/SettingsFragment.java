package ca.turnip.turnip;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.method.DigitsKeyListener;
import android.widget.EditText;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        final Preference saveQueueLengthPref = preferenceScreen.findPreference("save_queue_length");
        Preference saveQueuePref = preferenceScreen.findPreference("save_queue");
        saveQueuePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                saveQueueLengthPref.setEnabled((Boolean) newValue);
                return true;
            }
        });

        Context hostActivity = getActivity();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(hostActivity);
        Boolean saveQueue = prefs.getBoolean("save_queue", false);
        saveQueueLengthPref.setEnabled(saveQueue);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onResume() {
        super.onResume();
        getListView().setPadding(0, 0, 0, 0);
    }
}