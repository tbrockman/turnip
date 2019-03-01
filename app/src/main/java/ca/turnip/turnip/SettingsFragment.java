package ca.turnip.turnip;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String saveQueueLengthSummaryPrefix = "Set the maximum number of songs to store.\nCurrent: ";

    Context hostActivity;
    Preference saveQueuePref;
    Preference saveQueueLengthPref;
    PreferenceScreen preferenceScreen;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        preferenceScreen = getPreferenceScreen();
        saveQueueLengthPref = preferenceScreen.findPreference("save_queue_length");
        saveQueuePref = preferenceScreen.findPreference("save_queue");
        saveQueuePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                saveQueueLengthPref.setEnabled((Boolean) newValue);
                return true;
            }
        });

        saveQueueLengthPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(renderSaveQueueLengthSummary((String) newValue));
                return true;
            }
        });

        hostActivity = getActivity();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(hostActivity);
        Boolean saveQueue = prefs.getBoolean("save_queue", false);
        saveQueueLengthPref.setEnabled(saveQueue);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(hostActivity);
        String saveQueueLength = prefs.getString("save_queue_length", "50");
        saveQueueLengthPref.setSummary(renderSaveQueueLengthSummary(saveQueueLength));
        //        getListView().setPadding(0, 0, 0, 0);
    }

    public CharSequence renderSaveQueueLengthSummary(String saveQueueLength) {
        return saveQueueLengthSummaryPrefix + saveQueueLength;
    }
}