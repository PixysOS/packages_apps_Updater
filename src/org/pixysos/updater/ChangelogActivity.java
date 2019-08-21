/*

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


import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.pixysos.updater.download.DownloadClient;
import org.pixysos.updater.misc.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class ChangelogActivity extends AppCompatActivity {
    private static final String TAG = "ChangelogActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changelog);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        loadChangelog();
    }

    private void loadChangelog() {
        StringBuilder changelogBuilder = new StringBuilder();
        String changelog = "";
        try {
            FileReader changelogFile = new FileReader(Utils.getCachedChangelogList(this));
            BufferedReader reader = new BufferedReader(changelogFile);
            String line;
            while ((line = reader.readLine()) != null) {
                changelogBuilder.append(line).append("\n");
            }
            changelog = changelogBuilder.toString();
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        if (!changelog.isEmpty()) {
            String finalChangelog = changelog;
            runOnUiThread(() -> {
                findViewById(R.id.error_connection_layout).setVisibility(View.GONE);
                findViewById(R.id.changelog_text_scrollView).setVisibility(View.VISIBLE);
                TextView changelogView = findViewById(R.id.changelog_text);
                changelogView.setVisibility(View.VISIBLE);
                changelogView.setText(finalChangelog);
            });
        } else {
            runOnUiThread(() -> {
                findViewById(R.id.changelog_text).setVisibility(View.GONE);
                findViewById(R.id.changelog_text_scrollView).setVisibility(View.GONE);
                findViewById(R.id.error_connection_layout).setVisibility(View.VISIBLE);
                findViewById(R.id.action_retry).setOnClickListener(v -> tryLoadingChangelog());
            });
        }
    }

    private void tryLoadingChangelog() {
        final File changelogFile = Utils.getCachedChangelogList(this);
        String changelogUrl = Utils.getChangelogURL(this);

        DownloadClient.DownloadCallback changelogCallback = new DownloadClient.DownloadCallback() {
            @Override
            public void onResponse(int statusCode, String url, DownloadClient.Headers headers) {

            }

            @Override
            public void onSuccess(File destination) {
                Log.d(TAG, "Changelog downloaded");
                loadChangelog();
            }

            @Override
            public void onFailure(boolean cancelled) {
                findViewById(R.id.changelog_text).setVisibility(View.GONE);
                findViewById(R.id.changelog_text_scrollView).setVisibility(View.GONE);
                findViewById(R.id.error_connection_layout).setVisibility(View.VISIBLE);
            }
        };
        DownloadClient changelogDownloadClient = null;
        try {
            changelogDownloadClient = new DownloadClient.Builder()
                    .setUrl(changelogUrl)
                    .setDestination(new File(changelogFile.getAbsolutePath()))
                    .setDownloadCallback(changelogCallback)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (changelogDownloadClient != null)
            changelogDownloadClient.start();
        else {
            runOnUiThread(() -> {
                findViewById(R.id.changelog_text).setVisibility(View.GONE);
                findViewById(R.id.changelog_text_scrollView).setVisibility(View.GONE);
                findViewById(R.id.error_connection_layout).setVisibility(View.VISIBLE);
            });

        }

    }

}