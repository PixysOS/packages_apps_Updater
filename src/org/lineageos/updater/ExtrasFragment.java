/*
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2019 The PixysOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.updater;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.snackbar.Snackbar;

import org.lineageos.updater.model.UpdateInfo;

import java.util.List;

public class ExtrasFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener {

    private static final String TAG = "ExtrasFragment";
    private Preference xdaPreference;
    private Preference donatePreference;
    private String mXdaThreadUrl;
    private String mDonationUrl;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_extras, rootKey);

        xdaPreference = findPreference(getString(R.string.key_xda_thread));
        donatePreference = findPreference(getString(R.string.key_donate));
        xdaPreference.setOnPreferenceClickListener(this);
        donatePreference.setOnPreferenceClickListener(this);
        getPreferenceScreen().removeAll();
    }

    private void startRequiredIntent(String requiredUrl) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(requiredUrl));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(browserIntent);
    }

    public void updatePreferences(List<UpdateInfo> updateInfo) {
        if (updateInfo == null || updateInfo.size() == 0) {
            Log.d(TAG, "update data is null or the size is 0");
            getPreferenceScreen().removeAll();
            getActivity().findViewById(R.id.extras_info).setVisibility(View.GONE);
            getActivity().findViewById(R.id.emptyView).setVisibility(View.VISIBLE);
            return;
        }
        try {
            TextView extrasInfo = getActivity().findViewById(R.id.extras_info);
            if (updateInfo.get(0).getXdaThreadUrl() != null && !updateInfo.get(0).getXdaThreadUrl().isEmpty()) {
                getActivity().findViewById(R.id.emptyView).setVisibility(View.GONE);
                extrasInfo.setText(getActivity().getString(R.string.extras_info));
                getActivity().findViewById(R.id.extras_fragment).setVisibility(View.GONE);
                extrasInfo.setVisibility(View.VISIBLE);
                mXdaThreadUrl = updateInfo.get(0).getXdaThreadUrl();
                getPreferenceScreen().addPreference(xdaPreference);
            }
            if (updateInfo.get(0).getDonationUrl() != null && !updateInfo.get(0).getDonationUrl().isEmpty()) {
                if (getActivity().findViewById(R.id.emptyView).getVisibility() == View.VISIBLE)
                    getActivity().findViewById(R.id.emptyView).setVisibility(View.GONE);

                extrasInfo.setText(getActivity().getString(R.string.extras_info));

                if (getActivity().findViewById(R.id.extras_fragment).getVisibility() == View.VISIBLE)
                getActivity().findViewById(R.id.extras_fragment).setVisibility(View.GONE);

                if (extrasInfo.getVisibility() == View.GONE)
                extrasInfo.setVisibility(View.VISIBLE);

                mDonationUrl = updateInfo.get(0).getDonationUrl();
                getPreferenceScreen().addPreference(donatePreference);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == xdaPreference) {
            try {
                startRequiredIntent(mXdaThreadUrl);
            } catch (Exception e) {
                showSnackbar();
            }
        } else if (preference == donatePreference) {
            try {
                startRequiredIntent(mDonationUrl);
            } catch (Exception e) {
                showSnackbar();
            }
        }
        return false;
    }

    private void showSnackbar() {
        Snackbar.make(getActivity().findViewById(R.id.main_container), R.string.snack_cannot_parse_url, Snackbar.LENGTH_SHORT).show();
    }
}
