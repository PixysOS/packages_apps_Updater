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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import android.text.SpannableString;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.pixysos.updater.controller.UpdaterController;
import org.pixysos.updater.controller.UpdaterService;
import org.pixysos.updater.misc.BuildInfoUtils;
import org.pixysos.updater.misc.Constants;
import org.pixysos.updater.misc.PermissionsUtils;
import org.pixysos.updater.misc.StringGenerator;
import org.pixysos.updater.misc.Utils;
import org.pixysos.updater.model.UpdateInfo;
import org.pixysos.updater.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.List;

public class UpdatesListAdapter extends RecyclerView.Adapter<UpdatesListAdapter.ViewHolder> {

    private static final String TAG = "UpdateListAdapter";

    private final float mAlphaDisabledValue;
    private final CurrentActionInterface currentActionInterface;
    private List<String> mDownloadIds;
    private String mSelectedDownload;
    private UpdaterController mUpdaterController;
    private final UpdatesListActivity mActivity;
    private TextView updateAction;

    public UpdatesListAdapter(UpdatesListActivity activity, CurrentActionInterface currentActionInterface) {
        mActivity = activity;
        this.currentActionInterface = currentActionInterface;
        TypedValue tv = new TypedValue();
        mActivity.getTheme().resolveAttribute(android.R.attr.disabledAlpha, tv, true);
        mAlphaDisabledValue = tv.getFloat();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.update_item_view, viewGroup, false);
        view.findViewById(R.id.view_changelog).setOnClickListener(v -> mActivity.startActivity(new Intent(mActivity, ChangelogActivity.class)));
        return new ViewHolder(view);
    }

    public void setUpdaterController(UpdaterController updaterController) {
        mUpdaterController = updaterController;
        notifyDataSetChanged();
    }

    private void handleActiveStatus(ViewHolder viewHolder, UpdateInfo update) {
        boolean canDelete = false;

        final String downloadId = update.getDownloadId();
        if (mUpdaterController.isDownloading(downloadId)) {
            canDelete = true;
            String downloaded = StringGenerator.bytesToMegabytes(mActivity,
                    update.getFile().length());
            String total = Formatter.formatShortFileSize(mActivity, update.getFileSize());
            String percentage = NumberFormat.getPercentInstance().format(
                    update.getProgress() / 100.f);
            long eta = update.getEta();
            if (eta > 0) {
                CharSequence etaString = StringGenerator.formatETA(mActivity, eta * 1000);
                viewHolder.mProgressText.setText(mActivity.getString(
                        R.string.list_download_progress_eta_new, downloaded, total, etaString,
                        percentage));
            } else {
                viewHolder.mProgressText.setText(mActivity.getString(
                        R.string.list_download_progress_new, downloaded, total, percentage));
            }
            setButtonAction(viewHolder.mAction, Action.PAUSE, downloadId, true);
            viewHolder.mProgressBar.setIndeterminate(update.getStatus() == UpdateStatus.STARTING);
            viewHolder.mProgressBar.setProgress(update.getProgress());
        } else if (mUpdaterController.isInstallingUpdate(downloadId)) {
            setButtonAction(viewHolder.mAction, Action.CANCEL_INSTALLATION, downloadId, true);
            boolean notAB = !mUpdaterController.isInstallingABUpdate();
            viewHolder.mProgressText.setText(notAB ? R.string.dialog_prepare_zip_message :
                    update.getFinalizing() ?
                            R.string.finalizing_package :
                            R.string.preparing_ota_first_boot);
            viewHolder.mProgressBar.setIndeterminate(false);
            viewHolder.mProgressBar.setProgress(update.getInstallProgress());
        } else if (mUpdaterController.isVerifyingUpdate(downloadId)) {
            setButtonAction(viewHolder.mAction, Action.INSTALL, downloadId, false);
            viewHolder.mProgressText.setText(R.string.list_verifying_update);
            viewHolder.mProgressBar.setIndeterminate(true);
        } else {
            canDelete = true;
            setButtonAction(viewHolder.mAction, Action.RESUME, downloadId, !isBusy());
            String downloaded = StringGenerator.bytesToMegabytes(mActivity,
                    update.getFile().length());
            String total = Formatter.formatShortFileSize(mActivity, update.getFileSize());
            String percentage = NumberFormat.getPercentInstance().format(
                    update.getProgress() / 100.f);
            viewHolder.mProgressText.setText(mActivity.getString(R.string.list_download_progress_new,
                    downloaded, total, percentage));
            viewHolder.mProgressBar.setIndeterminate(false);
            viewHolder.mProgressBar.setProgress(update.getProgress());
        }

        viewHolder.itemView.setOnLongClickListener(getLongClickListener(update, canDelete,
                viewHolder.mBuildDate));
        viewHolder.mProgressBar.setVisibility(View.VISIBLE);
        viewHolder.mProgressText.setVisibility(View.VISIBLE);

        setConstraintsForChangelogView(viewHolder.mChangelogView, viewHolder.mProgressText);
        viewHolder.mProgressText.setPadding(0, 0, 0, 0);

        viewHolder.mBuildSize.setVisibility(View.INVISIBLE);
    }

    private void handleNotActiveStatus(ViewHolder viewHolder, UpdateInfo update) {
        final String downloadId = update.getDownloadId();
        if (mUpdaterController.isWaitingForReboot(downloadId)) {
            viewHolder.itemView.setOnLongClickListener(
                    getLongClickListener(update, false, viewHolder.mBuildDate));
            setButtonAction(viewHolder.mAction, Action.REBOOT, downloadId, true);
        } else if (update.getPersistentStatus() == UpdateStatus.Persistent.VERIFIED) {
            viewHolder.itemView.setOnLongClickListener(
                    getLongClickListener(update, true, viewHolder.mBuildDate));
            setButtonAction(viewHolder.mAction,
                    Utils.canInstall(update) ? Action.INSTALL : Action.DELETE,
                    downloadId, !isBusy());
        } else if (!Utils.canInstall(update)) {
            viewHolder.itemView.setOnLongClickListener(
                    getLongClickListener(update, false, viewHolder.mBuildDate));
            setButtonAction(viewHolder.mAction, Action.INFO, downloadId, !isBusy());
        } else {
            viewHolder.itemView.setOnLongClickListener(
                    getLongClickListener(update, false, viewHolder.mBuildDate));
            setButtonAction(viewHolder.mAction, Action.DOWNLOAD, downloadId, !isBusy());
        }
        String fileSize = Formatter.formatShortFileSize(mActivity, update.getFileSize());
        viewHolder.mBuildSize.setText(fileSize);

        viewHolder.mProgressBar.setVisibility(View.INVISIBLE);
        viewHolder.mProgressText.setVisibility(View.INVISIBLE);
        setConstraintsForChangelogView(viewHolder.mChangelogView, viewHolder.mBuildSize);
        viewHolder.mProgressText.setPadding(0, 0, 0, 0);
        viewHolder.mBuildSize.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, int i) {
        if (mDownloadIds == null) {
            viewHolder.mAction.setEnabled(false);
            return;
        }

        final String downloadId = mDownloadIds.get(i);
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        if (update == null) {
            // The update was deleted
            viewHolder.mAction.setEnabled(false);
            viewHolder.mAction.setText(R.string.action_download);
            return;
        }

        viewHolder.itemView.setSelected(downloadId.equals(mSelectedDownload));

        boolean activeLayout;
        switch (update.getPersistentStatus()) {
            case UpdateStatus.Persistent.UNKNOWN:
                activeLayout = update.getStatus() == UpdateStatus.STARTING;
                break;
            case UpdateStatus.Persistent.VERIFIED:
                activeLayout = update.getStatus() == UpdateStatus.INSTALLING;
                break;
            case UpdateStatus.Persistent.INCOMPLETE:
                activeLayout = true;
                break;
            default:
                throw new RuntimeException("Unknown update status");
        }

        String buildDate = StringGenerator.getDateLocalizedUTC(mActivity,
                DateFormat.LONG, update.getTimestamp());
        String buildVersion = mActivity.getString(R.string.list_build_version,
                update.getVersion());
        viewHolder.mBuildDate.setText(buildDate);
        viewHolder.mBuildVersion.setText(buildVersion);
        viewHolder.mBuildVersion.setCompoundDrawables(null, null, null, null);

        if (activeLayout) {
            handleActiveStatus(viewHolder, update);
        } else {
            handleNotActiveStatus(viewHolder, update);
        }
    }

    @Override
    public int getItemCount() {
        return mDownloadIds == null ? 0 : mDownloadIds.size();
    }

    public void setData(List<String> downloadIds) {
        mDownloadIds = downloadIds;
    }

    public void notifyItemChanged(String downloadId) {
        if (mDownloadIds == null) {
            return;
        }
        notifyItemChanged(mDownloadIds.indexOf(downloadId));
    }

    public void removeItem(String downloadId) {
        if (mDownloadIds == null) {
            return;
        }
        int position = mDownloadIds.indexOf(downloadId);
        mDownloadIds.remove(downloadId);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, getItemCount());
    }

    private void startDownloadWithWarning(final String downloadId) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean warn = preferences.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true);
        if (Utils.isOnWifiOrEthernet(mActivity) || !warn) {
            mUpdaterController.startDownload(downloadId);
            return;
        }

        View checkboxView = LayoutInflater.from(mActivity).inflate(R.layout.checkbox_view, null);
        CheckBox checkbox = checkboxView.findViewById(R.id.checkbox);
        checkbox.setText(R.string.checkbox_mobile_data_warning);

        new AlertDialog.Builder(mActivity, R.style.DialogTheme)
                .setTitle(R.string.update_on_mobile_data_title)
                .setMessage(R.string.update_on_mobile_data_message)
                .setView(checkboxView)
                .setPositiveButton(R.string.action_download,
                        (dialog, which) -> {
                            if (checkbox.isChecked()) {
                                preferences.edit()
                                        .putBoolean(Constants.PREF_MOBILE_DATA_WARNING, false)
                                        .apply();
                                mActivity.supportInvalidateOptionsMenu();
                            }
                            mUpdaterController.startDownload(downloadId);
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setButtonAction(TextView button, Action action, final String downloadId,
                                 boolean enabled) {
        final View.OnClickListener clickListener;
        switch (action) {
            case DOWNLOAD:
                String currentAction = "DOWNLOAD";
                currentActionInterface.currentAction(currentAction);
                if (mActivity.findViewById(R.id.rom_info_layout).getVisibility() == View.VISIBLE) {
                    button.setText(R.string.action_download);
                    button.setBackgroundResource(R.drawable.button_background_outlined);
                } else {
                    button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_download, 0, 0, 0);
                    button.setBackgroundResource(0);
                }
                button.setEnabled(enabled);
                clickListener = enabled ? view -> startDownloadWithWarning(downloadId) : null;
                break;
            case PAUSE:
                currentAction = "PAUSE";
                currentActionInterface.currentAction(currentAction);
                if (mActivity.findViewById(R.id.rom_info_layout).getVisibility() == View.VISIBLE)
                    button.setText(R.string.action_pause);
                else
                    button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> mUpdaterController.pauseDownload(downloadId)
                        : null;
                break;
            case RESUME: {
                currentAction = "RESUME";
                currentActionInterface.currentAction(currentAction);
                if (mActivity.findViewById(R.id.rom_info_layout).getVisibility() == View.VISIBLE)
                    button.setText(R.string.action_resume);
                else
                    button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_resume, 0, 0, 0);
                button.setEnabled(enabled);
                UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update) ||
                        update.getFile().length() == update.getFileSize();
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        mUpdaterController.resumeDownload(downloadId);
                    } else {
                        mActivity.showSnackbar(R.string.snack_update_not_installable,
                                Snackbar.LENGTH_LONG);
                    }
                } : null;
            }
            break;
            case INSTALL: {
                currentAction = "INSTALL";
                currentActionInterface.currentAction(currentAction);
                ((TextView) mActivity.findViewById(R.id.update_status)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0);
                ((TextView) mActivity.findViewById(R.id.update_status)).setText(mActivity.getString(R.string.update_downloaded_status));
                if (mActivity.findViewById(R.id.rom_info_layout).getVisibility() == View.VISIBLE)
                    button.setText(R.string.action_install);
                else
                    button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_install, 0, 0, 0);
                button.setEnabled(enabled);
                UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update);
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        getInstallDialog(downloadId).show();
                    } else {
                        mActivity.showSnackbar(R.string.snack_update_not_installable,
                                Snackbar.LENGTH_LONG);
                    }
                } : null;
            }
            break;
            case INFO: {
                currentAction = "INFO";
                currentActionInterface.currentAction(currentAction);
                if (mActivity.findViewById(R.id.rom_info_layout).getVisibility() == View.VISIBLE)
                    button.setText(R.string.action_info);
                else
                    button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_outline_info, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> showInfoDialog() : null;
            }
            break;
            case DELETE: {
                currentAction = "DELETE";
                currentActionInterface.currentAction(currentAction);
                if (mActivity.findViewById(R.id.rom_info_layout).getVisibility() == View.VISIBLE)
                    button.setText(R.string.action_delete);
                else
                    button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_delete, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> getDeleteDialog(downloadId).show() : null;
            }
            break;
            case CANCEL_INSTALLATION: {
                currentAction = "CANCEL";
                currentActionInterface.currentAction(currentAction);
                if (mActivity.findViewById(R.id.rom_info_layout).getVisibility() == View.VISIBLE)
                    button.setText(R.string.action_cancel);
                else
                    button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_cancel, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> getCancelInstallationDialog().show() : null;
            }
            break;
            case REBOOT: {
                currentAction = "REBOOT";
                currentActionInterface.currentAction(currentAction);
                if (mActivity.findViewById(R.id.rom_info_layout).getVisibility() == View.VISIBLE)
                    button.setText(R.string.reboot);
                else
                    button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_reboot, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> {
                    PowerManager pm =
                            (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
                    pm.reboot(null);
                } : null;
            }
            break;
            default:
                clickListener = null;
        }
        button.setAlpha(enabled ? 1.f : mAlphaDisabledValue);

        // Disable action mode when a button is clicked
        button.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(v);
            }
        });
    }

    private boolean isBusy() {
        return mUpdaterController.hasActiveDownloads() || mUpdaterController.isVerifyingUpdate()
                || mUpdaterController.isInstallingUpdate();
    }

    private AlertDialog.Builder getDeleteDialog(final String downloadId) {
        return new AlertDialog.Builder(mActivity, R.style.DialogTheme)
                .setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            mUpdaterController.pauseDownload(downloadId);
                            mUpdaterController.deleteUpdate(downloadId);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private View.OnLongClickListener getLongClickListener(final UpdateInfo update,
                                                          final boolean canDelete, View anchor) {
        return view -> {
            startActionMode(update, canDelete, anchor);
            return true;
        };
    }

    private AlertDialog.Builder getInstallDialog(final String downloadId) {
        if (!isBatteryLevelOk()) {
            Resources resources = mActivity.getResources();
            String message = resources.getString(R.string.dialog_battery_low_message_pct,
                    resources.getInteger(R.integer.battery_ok_percentage_discharging),
                    resources.getInteger(R.integer.battery_ok_percentage_charging));
            return new AlertDialog.Builder(mActivity, R.style.DialogTheme)
                    .setTitle(R.string.dialog_battery_low_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null);
        }
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        int resId;
        try {
            if (Utils.isABUpdate(update.getFile())) {
                resId = R.string.apply_update_dialog_message_ab;
            } else {
                resId = R.string.apply_update_dialog_message;
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not determine the type of the update");
            return null;
        }

        String buildDate = StringGenerator.getDateLocalizedUTC(mActivity,
                DateFormat.MEDIUM, update.getTimestamp());
        String buildInfoText = mActivity.getString(R.string.list_build_version_date,
                BuildInfoUtils.getBuildVersion(), buildDate);
        return new AlertDialog.Builder(mActivity, R.style.DialogTheme)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(mActivity.getString(resId, buildInfoText,
                        mActivity.getString(android.R.string.ok)))
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> Utils.triggerUpdate(mActivity, downloadId))
                .setNegativeButton(android.R.string.cancel, null);
    }

    private AlertDialog.Builder getCancelInstallationDialog() {
        return new AlertDialog.Builder(mActivity, R.style.DialogTheme)
                .setMessage(R.string.cancel_installation_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            Intent intent = new Intent(mActivity, UpdaterService.class);
                            intent.setAction(UpdaterService.ACTION_INSTALL_STOP);
                            mActivity.startService(intent);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private void startActionMode(final UpdateInfo update, final boolean canDelete, View anchor) {
        mSelectedDownload = update.getDownloadId();
        notifyItemChanged(update.getDownloadId());

        ContextThemeWrapper wrapper = new ContextThemeWrapper(mActivity,
                R.style.AppTheme_PopupMenuOverlapAnchor);
        PopupMenu popupMenu = new PopupMenu(wrapper, anchor, Gravity.NO_GRAVITY,
                R.attr.actionOverflowMenuStyle, 0);
        popupMenu.inflate(R.menu.menu_action_mode);

        MenuBuilder menu = (MenuBuilder) popupMenu.getMenu();
        menu.findItem(R.id.menu_delete_action).setVisible(canDelete);
        menu.findItem(R.id.menu_copy_url).setVisible(update.getAvailableOnline());
        menu.findItem(R.id.menu_export_update).setVisible(
                update.getPersistentStatus() == UpdateStatus.Persistent.VERIFIED);

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.menu_delete_action:
                    getDeleteDialog(update.getDownloadId()).show();
                    return true;
                case R.id.menu_copy_url:
                    Utils.addToClipboard(mActivity,
                            mActivity.getString(R.string.label_download_url),
                            update.getDownloadUrl(),
                            mActivity.getString(R.string.toast_download_url_copied));
                    return true;
                case R.id.menu_export_update:
                    // TODO: start exporting once the permission has been granted
                    boolean hasPermission = PermissionsUtils.checkAndRequestStoragePermission(
                            mActivity, 0);
                    if (hasPermission) {
                        exportUpdate(update);
                    }
                    return true;
            }
            return false;
        });

        MenuPopupHelper helper = new MenuPopupHelper(wrapper, menu, anchor);
        helper.show();
    }

    private void exportUpdate(UpdateInfo update) {
        File dest = new File(Utils.getExportPath(mActivity), update.getName());
        if (dest.exists()) {
            dest = Utils.appendSequentialNumber(dest);
        }
        Intent intent = new Intent(mActivity, ExportUpdateService.class);
        intent.setAction(ExportUpdateService.ACTION_START_EXPORTING);
        intent.putExtra(ExportUpdateService.EXTRA_SOURCE_FILE, update.getFile());
        intent.putExtra(ExportUpdateService.EXTRA_DEST_FILE, dest);
        mActivity.startService(intent);
    }

    private void showInfoDialog() {
        String messageString = String.format(StringGenerator.getCurrentLocale(mActivity),
                mActivity.getString(R.string.blocked_update_dialog_message),
                mActivity.getString(R.string.blocked_update_info_url));
        SpannableString message = new SpannableString(messageString);
        Linkify.addLinks(message, Linkify.WEB_URLS);
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mActivity, R.style.DialogTheme)
                .setTitle(R.string.blocked_update_dialog_title)
                .setPositiveButton(android.R.string.ok, null)
                .setMessage(message);

        AlertDialog dialog = dialogBuilder.create();
        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(mActivity, R.color.theme_accent)));
        dialog.show();
        TextView textView = dialog.findViewById(android.R.id.message);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private boolean isBatteryLevelOk() {
        Intent intent = mActivity.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (!intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
            return true;
        }
        int percent = Math.round(100.f * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) /
                intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int BATTERY_PLUGGED_ANY = (BatteryManager.BATTERY_PLUGGED_AC) | (BatteryManager.BATTERY_PLUGGED_USB) | (BatteryManager.BATTERY_PLUGGED_WIRELESS);
        int required = (plugged & BATTERY_PLUGGED_ANY) != 0 ?
                mActivity.getResources().getInteger(R.integer.battery_ok_percentage_charging) :
                mActivity.getResources().getInteger(R.integer.battery_ok_percentage_discharging);
        return percent >= required;
    }

    private void setConstraintsForChangelogView(TextView changelogView, TextView topView) {
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, topView.getId());
        params.setMargins(0, Utils.getUnitsInDip(mActivity, 8), 0, 0);
        changelogView.setLayoutParams(params);
    }

    private enum Action {
        DOWNLOAD,
        PAUSE,
        RESUME,
        INSTALL,
        INFO,
        DELETE,
        CANCEL_INSTALLATION,
        REBOOT,
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView mAction;

        private final TextView mBuildDate;
        private final TextView mBuildVersion;
        private final TextView mBuildSize;

        private final ProgressBar mProgressBar;
        private final TextView mProgressText;
        private final TextView mChangelogView;

        ViewHolder(final View view) {
            super(view);
            mAction = view.findViewById(R.id.update_action);

            mBuildDate = view.findViewById(R.id.build_date);
            mBuildVersion = view.findViewById(R.id.build_version);
            mBuildSize = view.findViewById(R.id.build_size);

            mProgressBar = view.findViewById(R.id.progress_bar);
            mProgressText = view.findViewById(R.id.progress_text);
            mChangelogView = view.findViewById(R.id.view_changelog);
        }
    }
}
