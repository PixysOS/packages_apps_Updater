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
package org.pixysos.updater;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.pixysos.updater.controller.UpdaterController;
import org.pixysos.updater.controller.UpdaterService;
import org.pixysos.updater.download.DownloadClient;
import org.pixysos.updater.misc.BuildInfoUtils;
import org.pixysos.updater.misc.Constants;
import org.pixysos.updater.misc.StringGenerator;
import org.pixysos.updater.misc.Utils;
import org.pixysos.updater.model.UpdateInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UpdatesActivity extends UpdatesListActivity implements CurrentActionInterface {

    private static final String TAG = "UpdatesActivity";
    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    private UpdatesListAdapter mAdapter;

    private View mRefreshIconView;
    private RotateAnimation mRefreshAnimation;

    private String currentAction;

    private float rotationAngle = 0f;
    private ExtrasFragment extrasFragment;
    private boolean isPaddingSet = false;
    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            mAdapter.setUpdaterController(mUpdaterService.getUpdaterController());
            getUpdatesList();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAdapter.setUpdaterController(null);
            mUpdaterService = null;
            mAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public void currentAction(String action) {
        this.currentAction = action;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        mAdapter = new UpdatesListAdapter(this, this);
        recyclerView.setAdapter(mAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        extrasFragment = new ExtrasFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.extras_fragment, extrasFragment)
                .commit();

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    handleDownloadStatusChange(downloadId);
                    mAdapter.notifyDataSetChanged();
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.notifyItemChanged(downloadId);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.removeItem(downloadId);
                }
            }
        };

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ((TextView) toolbar.findViewById(R.id.titleText)).setText(getString(R.string.header_title_text, getString(R.string.display_name)));
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        updateLastCheckedString();

        TextView updateStatus = findViewById(R.id.update_status);
        updateStatus.setText(getString(R.string.up_to_date_notification));

        TextView updatesText = findViewById(R.id.updates_text);
        updatesText.setText(getString(R.string.updates));

        TextView currentBuildtext = findViewById(R.id.currentBuildText);
        currentBuildtext.setText(getString(R.string.current_build));

        TextView maintainerText = findViewById(R.id.maintainerText);
        maintainerText.setText(getString(R.string.maintainer));

        TextView lastCheckedText = findViewById(R.id.lastCheckedText);
        lastCheckedText.setText(getString(R.string.last_checked));

        TextView headerBuildDate = findViewById(R.id.header_build_date);
        headerBuildDate.setText(StringGenerator.getDateLocalizedUTC(this,
                DateFormat.LONG, BuildInfoUtils.getBuildDateTimestamp()));

        TextView maintainerName = findViewById(R.id.maintainer_name);
        String maintainer = getString(R.string.maintainer_name, BuildInfoUtils.getMaintainer());
        if (!maintainer.isEmpty())
            maintainerName.setText(maintainer);
        else
            maintainerName.setText(getString(R.string.unknown));

        TextView romInfoText = findViewById(R.id.romInfoText);
        romInfoText.setText(getString(R.string.rom_info));

        TextView deviceText = findViewById(R.id.deviceText);
        String device = getString(R.string.device);
        deviceText.setText(device);

        TextView headerDeviceName = findViewById(R.id.header_device_name);
        String deviceName = BuildInfoUtils.getDevice();
        if (!deviceName.isEmpty())
            headerDeviceName.setText(BuildInfoUtils.getDevice());
        else
            headerDeviceName.setText(getString(R.string.unknown));

        TextView extrasText = findViewById(R.id.extrasText);
        extrasText.setText(getString(R.string.extras));

        TextView lookingForMore = findViewById(R.id.lookingForMoreText);
        lookingForMore.setText(getString(R.string.looking_for_more));

        mRefreshAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        mRefreshAnimation.setInterpolator(new LinearInterpolator());
        mRefreshAnimation.setDuration(1000);

        FloatingActionButton checkUpdates = findViewById(R.id.check_updates);
        checkUpdates.getDrawable().mutate().setTint(Color.WHITE);
        checkUpdates.setOnClickListener(v -> downloadUpdatesList(true));

        findViewById(R.id.extras_layout).setOnClickListener(v -> startExpandAndCollapseAnimation());

        findViewById(R.id.mainScrollView).setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > oldScrollY)
                checkUpdates.hide();
            else
                checkUpdates.show();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, UpdaterService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_preferences: {
                showPreferencesDialog();
                return true;
            }
            case R.id.menu_show_changelog: {
                Intent openUrl = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(Utils.getChangelogURL(this)));
                startActivity(openUrl);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void startExpandAndCollapseAnimation() {
        TextView extrasInfo = findViewById(R.id.extras_info);
        extrasInfo.setText(getString(R.string.extras_info));

        FrameLayout extrasLayout = findViewById(R.id.extras_fragment);
        extrasLayout.setVisibility((extrasLayout.getVisibility() == View.GONE) ? View.VISIBLE : View.GONE);

        View emptyView = findViewById(R.id.emptyView);
        if (emptyView.getVisibility() == View.VISIBLE)
            extrasInfo.setVisibility(View.GONE);
        else
            extrasInfo.setVisibility((extrasInfo.getVisibility() == View.GONE) ? View.VISIBLE : View.GONE);
        if (findViewById(R.id.ic_expand).getVisibility() == View.VISIBLE) {
            RotateAnimation rotateAnimation;
            rotateAnimation = new RotateAnimation(rotationAngle, rotationAngle + 180.0f, Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setInterpolator(new LinearInterpolator());
            rotateAnimation.setDuration(250);
            rotateAnimation.setFillAfter(true);
            ImageView targetExpandCollapse = findViewById(R.id.ic_expand);
            targetExpandCollapse.startAnimation(rotateAnimation);
            rotationAngle += 180;
            rotationAngle %= 360;
        }
    }

    private void handleUpdateAction(boolean isRomInfoLayoutVisible) {
        TextView updateAction = findViewById(R.id.update_action);
        if (isRomInfoLayoutVisible) {
            updateAction.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            switch (currentAction) {
                case "PAUSE":
                    updateAction.setText(getString(R.string.action_pause));
                    updateAction.setBackgroundResource(R.drawable.button_background);
                    break;
                case "RESUME":
                    updateAction.setText(getString(R.string.action_resume));
                    updateAction.setBackgroundResource(R.drawable.button_background);
                    break;
                case "DOWNLOAD":
                    updateAction.setText(getString(R.string.action_download));
                    updateAction.setBackgroundResource(R.drawable.button_background);
                    break;
                case "CANCEL":
                    updateAction.setText(getString(R.string.action_cancel));
                    updateAction.setBackgroundResource(R.drawable.button_background);
                    break;
                case "DELETE":
                    updateAction.setText(getString(R.string.action_delete));
                    updateAction.setBackgroundResource(R.drawable.button_background);
                    break;
                case "INSTALL":
                    updateAction.setText(getString(R.string.action_install));
                    updateAction.setBackgroundResource(R.drawable.button_background);
                    break;
                case "REBOOT":
                    updateAction.setText(getString(R.string.reboot));
                    updateAction.setBackgroundResource(R.drawable.button_background);
                    break;
                case "INFO":
                    updateAction.setText(getString(R.string.action_info));
                    updateAction.setBackgroundResource(R.drawable.button_background);
                    break;
            }
        } else {
            switch (currentAction) {
                case "PAUSE":
                    updateAction.setText("");
                    updateAction.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.ic_pause), null, null, null);
                    updateAction.setBackgroundResource(0);
                    break;
                case "RESUME":
                    updateAction.setText("");
                    updateAction.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.ic_resume), null, null, null);
                    updateAction.setBackgroundResource(0);
                    break;
                case "DOWNLOAD":
                    updateAction.setText("");
                    updateAction.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.ic_download), null, null, null);
                    updateAction.setBackgroundResource(0);
                    break;
                case "CANCEL":
                    updateAction.setText("");
                    updateAction.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.ic_cancel), null, null, null);
                    updateAction.setBackgroundResource(0);
                    break;
                case "DELETE":
                    updateAction.setText("");
                    updateAction.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.ic_delete), null, null, null);
                    updateAction.setBackgroundResource(0);
                    break;
                case "INSTALL":
                    updateAction.setText("");
                    updateAction.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.ic_install), null, null, null);
                    updateAction.setBackgroundResource(0);
                    break;
                case "REBOOT":
                    updateAction.setText("");
                    updateAction.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.ic_reboot), null, null, null);
                    updateAction.setBackgroundResource(0);
                    break;
                case "INFO":
                    updateAction.setText("");
                    updateAction.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.ic_outline_info), null, null, null);
                    updateAction.setBackgroundResource(0);
                    break;
            }
        }
    }

    private void loadUpdatesList(File jsonFile, boolean manualRefresh)
            throws IOException, JSONException {
        Log.d(TAG, "Adding remote updates");
        extrasFragment.updatePreferences(Utils.parseJson(jsonFile, false));
        UpdaterController controller = mUpdaterService.getUpdaterController();
        boolean newUpdates = false;

        List<UpdateInfo> updates = Utils.parseJson(jsonFile, true);
        List<String> updatesOnline = new ArrayList<>();
        for (UpdateInfo update : updates) {
            newUpdates |= controller.addUpdate(update);
            updatesOnline.add(update.getDownloadId());
        }
        controller.setUpdatesAvailableOnline(updatesOnline, true);

        if (manualRefresh) {
            showSnackbar(
                    newUpdates ? R.string.snack_updates_found : R.string.snack_no_updates_found,
                    Snackbar.LENGTH_SHORT);
        }

        List<String> updateIds = new ArrayList<>();
        List<UpdateInfo> sortedUpdates = controller.getUpdates();
        TextView updateStatus = findViewById(R.id.update_status);
        if (sortedUpdates.isEmpty()) {
            Log.d(TAG, "Up to date");
            findViewById(R.id.recycler_view).setVisibility(View.GONE);
            updateStatus.setText(getString(R.string.up_to_date_notification));
            findViewById(R.id.lastCheckedText).setPadding(0, Utils.getUnitaInDip(this, 16), 0, 0);
            isPaddingSet = true;
            findViewById(R.id.rom_info_layout).setVisibility(View.VISIBLE);
            findViewById(R.id.updatesLayout).setOnClickListener(null);
        } else {
            View romInfoView = findViewById(R.id.rom_info_layout);
            romInfoView.setVisibility(View.GONE);
            findViewById(R.id.updatesLayout).setOnClickListener(v -> {
                romInfoView.setVisibility((romInfoView.getVisibility() == View.VISIBLE) ? View.GONE : View.VISIBLE);
                AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
                alphaAnimation.setDuration(100);
                alphaAnimation.setRepeatCount(1);
                alphaAnimation.setRepeatMode(Animation.REVERSE);

                alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                        handleUpdateAction(romInfoView.getVisibility() == View.VISIBLE);
                    }
                });
                findViewById(R.id.update_action).startAnimation(alphaAnimation);
            });

            updateStatus.setText(getString(R.string.update_found_notification));
            updateStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_outline_info, 0, 0, 0);
            findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
            findViewById(R.id.lastCheckedText).setPadding(0, 0, 0, 0);
            isPaddingSet = false;

            if (findViewById(R.id.update_action) != null)
                handleUpdateAction(romInfoView.getVisibility() == View.VISIBLE);

            sortedUpdates.sort((u1, u2) -> Long.compare(u2.getTimestamp(), u1.getTimestamp()));
            for (UpdateInfo update : sortedUpdates) {
                updateIds.add(update.getDownloadId());
            }
            mAdapter.setData(updateIds);
            mAdapter.notifyDataSetChanged();
        }
    }

    private void getUpdatesList() {
        File jsonFile = Utils.getCachedUpdateList(this);
        if (jsonFile.exists()) {
            try {
                loadUpdatesList(jsonFile, false);
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
        } else {
            downloadUpdatesList(false);
        }
    }

    private void processNewJson(File json, File jsonNew, boolean manualRefresh) {
        try {
            loadUpdatesList(jsonNew, manualRefresh);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            long millis = System.currentTimeMillis();
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK, millis).apply();
            updateLastCheckedString();
            if (json.exists() && Utils.isUpdateCheckEnabled(this) &&
                    Utils.checkForNewUpdates(json, jsonNew)) {
                UpdatesCheckReceiver.updateRepeatingUpdatesCheck(this);
            }
            // In case we set a one-shot check because of a previous failure
            UpdatesCheckReceiver.cancelUpdatesCheck(this);
            jsonNew.renameTo(json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not read json", e);
            TextView updateStatus = findViewById(R.id.update_status);
            updateStatus.setText(getString(R.string.up_to_date_notification));
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
        }
    }

    private void downloadUpdatesList(final boolean manualRefresh) {
        final File jsonFile = Utils.getCachedUpdateList(this);
        final File jsonFileTmp = new File(jsonFile.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL(this);
        Log.d(TAG, "Checking " + url);

        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download updates list");
                if (!isPaddingSet)
                    runOnUiThread(() -> findViewById(R.id.lastCheckedText).setPadding(0, Utils.getUnitaInDip(UpdatesActivity.this, 16), 0, 0));

                if (findViewById(R.id.ic_expand).getVisibility() == View.GONE)
                   runOnUiThread(() -> findViewById(R.id.ic_expand).setVisibility(View.VISIBLE));

                runOnUiThread(() -> {
                    if (!cancelled) {
                        TextView updateStatus = findViewById(R.id.update_status);
                        updateStatus.setText(getString(R.string.up_to_date_notification));
                        showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
                    }
                    refreshAnimationStop();
                });
            }

            @Override
            public void onResponse(int statusCode, String url,
                                   DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                runOnUiThread(() -> {
                    Log.d(TAG, "List downloaded");
                    processNewJson(jsonFile, jsonFileTmp, manualRefresh);
                    refreshAnimationStop();
                });
            }
        };

        final DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonFileTmp)
                    .setDownloadCallback(callback)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            TextView updateStatus = findViewById(R.id.update_status);
            updateStatus.setText(getString(R.string.up_to_date_notification));
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
            return;
        }

        refreshAnimationStart();
        downloadClient.start();
    }

    private void updateLastCheckedString() {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        long lastCheck = preferences.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000;
        String lastCheckString = getString(R.string.header_last_updates_check,
                StringGenerator.getDateLocalized(this, DateFormat.LONG, lastCheck),
                StringGenerator.getTimeLocalized(this, lastCheck));
        TextView headerLastCheck = findViewById(R.id.header_last_check);
        headerLastCheck.setText(lastCheckString);
    }

    private void handleDownloadStatusChange(String downloadId) {
        UpdateInfo update = mUpdaterService.getUpdaterController().getUpdate(downloadId);
        switch (update.getStatus()) {
            case PAUSED_ERROR:
                showSnackbar(R.string.snack_download_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFICATION_FAILED:
                showSnackbar(R.string.snack_download_verification_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFIED:
                showSnackbar(R.string.snack_download_verified, Snackbar.LENGTH_LONG);
                break;
        }
    }

    @Override
    public void showSnackbar(int stringId, int duration) {
        Snackbar.make(findViewById(R.id.main_container), stringId, duration).show();
    }

    private void refreshAnimationStart() {
        if (mRefreshIconView == null) {
            mRefreshIconView = findViewById(R.id.check_updates);
        }
        if (mRefreshIconView != null) {
            mRefreshAnimation.setRepeatCount(Animation.INFINITE);
            mRefreshIconView.startAnimation(mRefreshAnimation);
            mRefreshIconView.setEnabled(false);
        }
    }

    private void refreshAnimationStop() {
        if (mRefreshIconView != null) {
            mRefreshAnimation.setRepeatCount(0);
            mRefreshIconView.setEnabled(true);
        }
    }

    private void showPreferencesDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.preferences_dialog, null);
        Spinner autoCheckInterval =
                view.findViewById(R.id.preferences_auto_updates_check_interval);
        Switch autoDelete = view.findViewById(R.id.preferences_auto_delete_updates);
        Switch dataWarning = view.findViewById(R.id.preferences_mobile_data_warning);
        Switch abPerfMode = view.findViewById(R.id.preferences_ab_perf_mode);

        if (!Utils.isABDevice()) {
            abPerfMode.setVisibility(View.GONE);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        autoCheckInterval.setSelection(Utils.getUpdateCheckSetting(this));
        autoDelete.setChecked(prefs.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, false));
        dataWarning.setChecked(prefs.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true));
        abPerfMode.setChecked(prefs.getBoolean(Constants.PREF_AB_PERF_MODE, false));

        new AlertDialog.Builder(this, R.style.DialogTheme)
                .setTitle(R.string.menu_preferences)
                .setView(view)
                .setOnDismissListener(dialogInterface -> {
                    prefs.edit()
                            .putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL,
                                    autoCheckInterval.getSelectedItemPosition())
                            .putBoolean(Constants.PREF_AUTO_DELETE_UPDATES,
                                    autoDelete.isChecked())
                            .putBoolean(Constants.PREF_MOBILE_DATA_WARNING,
                                    dataWarning.isChecked())
                            .putBoolean(Constants.PREF_AB_PERF_MODE,
                                    abPerfMode.isChecked())
                            .apply();

                    if (Utils.isUpdateCheckEnabled(this)) {
                        UpdatesCheckReceiver.scheduleRepeatingUpdatesCheck(this);
                    } else {
                        UpdatesCheckReceiver.cancelRepeatingUpdatesCheck(this);
                        UpdatesCheckReceiver.cancelUpdatesCheck(this);
                    }

                    boolean enableABPerfMode = abPerfMode.isChecked();
                    mUpdaterService.getUpdaterController().setPerformanceMode(enableABPerfMode);
                })
                .show();
    }
}
