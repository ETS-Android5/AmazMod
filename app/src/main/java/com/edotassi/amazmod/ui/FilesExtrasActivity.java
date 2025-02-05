package com.edotassi.amazmod.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;

import com.edotassi.amazmod.AmazModApplication;
import com.edotassi.amazmod.R;
import com.edotassi.amazmod.databinding.ActivityFilesExtrasBinding;
import com.edotassi.amazmod.db.model.CommandHistoryEntity;
import com.edotassi.amazmod.db.model.CommandHistoryEntity_Table;
import com.edotassi.amazmod.db.model.NotificationPreferencesEntity;
import com.edotassi.amazmod.db.model.NotificationPreferencesEntity_Table;
import com.edotassi.amazmod.support.SilenceApplicationHelper;
import com.edotassi.amazmod.util.Permissions;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.pixplicity.easyprefs.library.Prefs;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.tinylog.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import amazmod.com.transport.Constants;

public class FilesExtrasActivity extends BaseAppCompatActivity {

    private String ENABLED;
    private String DISABLED;
    private String NEVER;
    private String NONE;
    private String DATA;
    private String DOWNLOADS;


    private File saveDirectory;
    private final String bkpDirectory = File.separator + Constants.TAG;
    private String testDirectory;
    private String fileName;
    private boolean useDownloads = false;
    private boolean useFiles = false;

    private ActivityFilesExtrasBinding binding;

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFilesExtrasBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Logger.trace("FilesExtrasActivity onCreate");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.activity_files_extras);

        this.ENABLED = getResources().getString(R.string.enabled);
        this.DISABLED = getResources().getString(R.string.disabled);
        this.NEVER = getResources().getString(R.string.never);
        this.NONE = getResources().getString(R.string.none);
        this.DATA = getResources().getString(R.string.data);
        this.DOWNLOADS = getResources().getString(R.string.downloads);

        this.fileName = Constants.TAG + "_prefs.bkp";
        testDirectory = File.separator + "test" + System.currentTimeMillis();

        if (!Permissions.hasPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Snackbar
                    .make(binding.activityFilesMainContainer, R.string.no_storage_permission, Snackbar.LENGTH_LONG)
                    .setAction(R.string.grant, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openPermissions();
                        }
                    })
                    .show();
        }

        binding.activityFilesBackup.setOnClickListener(v -> {
            save();
        });

        binding.activityFilesRestore.setOnClickListener(v -> {
            load();
        });

        binding.activityFilesPermission.setOnClickListener(v -> {
            openPermissions();
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        updateData();
    }

    public void openPermissions() {
        if (!Permissions.hasPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
        updateData();
    }

    @SuppressLint("CheckResult")
    private void updateData() {
        Logger.trace("FilesExtrasActivity updateData");

        String obsText = "";

        binding.activityFilesProgress.setVisibility(View.VISIBLE);
        binding.activityFilesMainContainer.setVisibility(View.GONE);

        if (Permissions.hasPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            binding.activityFilesPermission.setText(this.ENABLED.toUpperCase());
            binding.activityFilesPermission.setTextColor(getResources().getColor(R.color.colorCharging, getTheme()));
        } else {
            binding.activityFilesPermission.setText(this.DISABLED.toUpperCase());
            binding.activityFilesPermission.setTextColor(getResources().getColor(R.color.colorAccent, getTheme()));
        }

        final String timeLastSave = Prefs.getString(Constants.PREF_TIME_LAST_SAVE, "null");
        if (timeLastSave.equals("null")) {
            binding.activityFilesDateLastBackup.setText(this.NEVER.toUpperCase());
        } else {
            binding.activityFilesDateLastBackup.setText(timeLastSave);
        }

        if (checkBackupFile()) {
            if (useFiles) {
                binding.activityFilesFile.setText(this.DATA.toUpperCase());
                binding.activityFilesFile.setTextColor(getResources().getColor(R.color.colorCharging, getTheme()));
            } else if (useDownloads) {
                binding.activityFilesFile.setText(this.DOWNLOADS.toUpperCase());
                binding.activityFilesFile.setTextColor(getResources().getColor(R.color.colorCharging, getTheme()));
            }

        } else {
            binding.activityFilesFile.setText(this.NONE.toUpperCase());
            binding.activityFilesFile.setTextColor(getResources().getColor(R.color.colorAccent, getTheme()));
        }

        if (checkWriteDirectory()) {
            if (useFiles) {
                obsText = getResources().getString(R.string.activity_files_backup_obs) + "\n" + this.saveDirectory + "/" + this.fileName;
            } else if (useDownloads) {
                obsText = getResources().getString(R.string.activity_files_backup_downloads) + "\n" + this.saveDirectory + "/" + this.fileName;
            }
        } else {
            obsText = getResources().getString(R.string.activity_files_backup_error);
        }

        binding.activityFilesObs.setText(obsText);

        binding.activityFilesProgress.setVisibility(View.GONE);
        binding.activityFilesMainContainer.setVisibility(View.VISIBLE);

    }

    private void save() {
        Logger.trace("FilesExtrasActivity save");

        Date lastDate = new Date();
        String time = DateFormat.getTimeInstance(DateFormat.SHORT, AmazModApplication.defaultLocale).format(lastDate);
        String date = DateFormat.getDateInstance(DateFormat.SHORT, AmazModApplication.defaultLocale).format(lastDate);

        Prefs.putString(Constants.PREF_TIME_LAST_SAVE, date + " " + time);

        final String packageName = this.getPackageName();

        if (checkWriteDirectory()) {

            boolean success = true;
            try {
                if (!this.saveDirectory.exists()) {
                    success = this.saveDirectory.mkdir();
                }
                if (success) {
                    saveAppsDbToPrefs(); //Restore selected apps to Prefs
                    saveCommandHistoryToPrefs();
                    File data = Environment.getDataDirectory();
                    String currentDBPath = "/data/" + packageName + "/shared_prefs/" + packageName + "_preferences.xml";
                    File currentDB = new File(data, currentDBPath);
                    File backupDB = new File(this.saveDirectory, this.fileName);
                    try {
                        FileChannel source = new FileInputStream(currentDB).getChannel();
                        FileChannel destination = new FileOutputStream(backupDB).getChannel();
                        destination.transferFrom(source, 0, source.size());
                        source.close();
                        destination.close();
                        Toast.makeText(this, getResources().getString(R.string.activity_files_backup_ok), Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, getResources().getString(R.string.activity_files_backup_failed), Toast.LENGTH_SHORT).show();
                    }
                    eraseAppsPrefs(); //Overwrite Prefs after backup
                }
            } catch (Exception e) {
                Toast.makeText(this, getResources().getString(R.string.activity_files_file_error), Toast.LENGTH_SHORT).show();
                Logger.error("FilesExtrasActivity save exception: " + e.toString());
            }
            updateData();
        } else {
            Toast.makeText(this, getResources().getString(R.string.activity_files_no_write_permission), Toast.LENGTH_SHORT).show();
        }

    }

    private void load() {
        Logger.trace("FilesExtrasActivity load");

        final String packageName = this.getPackageName();

        boolean success = false;
        if (checkBackupFile()) {
            File backupDB = new File(this.saveDirectory, this.fileName);
            try {
                if (backupDB.exists()) {
                    File data = Environment.getDataDirectory();
                    String currentDBPath = "/data/" + packageName + "/shared_prefs/" + packageName + "_preferences.xml";
                    File currentDB = new File(data, currentDBPath);
                    try {
                        FileChannel source = new FileInputStream(backupDB).getChannel();
                        FileChannel destination = new FileOutputStream(currentDB).getChannel();
                        destination.transferFrom(source, 0, source.size());
                        source.close();
                        destination.close();
                        success = true;
                        Toast.makeText(this, getResources().getString(R.string.activity_files_restore_ok), Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                        success = false;
                        Toast.makeText(this, getResources().getString(R.string.activity_files_file_error), Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                success = false;
                Toast.makeText(this, getResources().getString(R.string.activity_files_restore_failed), Toast.LENGTH_SHORT).show();
                Logger.error("FilesExtrasActivity load exception: " + e.toString());
            }
        } else {
            success = false;
            Toast.makeText(this, getResources().getString(R.string.activity_files_backup_not_found), Toast.LENGTH_SHORT).show();
        }

        if (success) {
            Handler mHandler = new Handler();
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        System.exit(2);
                        Logger.info("FilesExtrasActivity load delayed System.exit()");
                    } catch (Exception e) {
                        e.printStackTrace();
                        Logger.error("FilesExtrasActivity load exception: " + e.toString());
                    }
                }
            }, 2000);
        }
    }

    private boolean checkWriteDirectory() {
        Logger.trace("FilesExtrasActivity checkWriteDirectory");

        this.useDownloads = false;
        this.useFiles = false;
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + testDirectory);
        boolean success = true;
        if (!file.exists()) {
            success = file.mkdir();
        }
        if (success) {
            useDownloads = true;
            this.saveDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + bkpDirectory);
            file.delete();
        } else {
            file = new File(this.getExternalFilesDir(null) + testDirectory);
            success = true;
            if (!file.exists()) {
                success = file.mkdir();
            }
            if (success) {
                useFiles = true;
                this.saveDirectory = this.getExternalFilesDir(null);
                file.delete();
            }
        }
        return useDownloads || useFiles;
    }

    private boolean checkBackupFile() {
        Logger.trace("FilesExtrasActivity checkBackupFile");

        this.useDownloads = false;
        this.useFiles = false;
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + bkpDirectory, fileName);
        if (file.exists()) {
            this.useDownloads = true;
            this.saveDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + bkpDirectory);
        } else {
            file = new File(this.getExternalFilesDir(null) + File.separator, fileName);
            if (file.exists()) {
                this.useFiles = true;
                this.saveDirectory = this.getExternalFilesDir(null);
            }
        }
        return useFiles || useDownloads;
    }

    public static void checkApps(Context context) {
        Logger.trace("FilesExtrasActivity checkApps");

        Gson gson = new Gson();
        List<String> packagesList = new ArrayList<>(Arrays.asList(gson
                .fromJson(Prefs.getString(Constants.PREF_ENABLED_NOTIFICATIONS_PACKAGES, "[]"), String[].class)));

        if (packagesList.size() > 0)
            loadAppsPrefsFromJSON();

        List<NotificationPreferencesEntity> apps = SQLite
                .select()
                .from(NotificationPreferencesEntity.class)
                .queryList();

        if (apps.size() > 0)
            checkAppsSql(context, apps);

    }

    public static void checkAppsJson(Context context, List<String> packagesList) {
        Logger.trace("FilesExtrasActivity checkAppsJson");

        List<String> installedApps = getInstalledPackagesNames(context);

        List<String> dummy = new ArrayList<>();

        for (String p : packagesList) {

            if (Collections.binarySearch(installedApps, p) < 0) {
                dummy.add(p);
                Logger.info("FilesExtrasActivity checkAppsJson removed app: " + p);
            }
        }

        if (dummy.size() > 0) packagesList.removeAll(dummy);

        String pref = new Gson().toJson(packagesList);

        Prefs.putString(Constants.PREF_ENABLED_NOTIFICATIONS_PACKAGES, pref);

    }

    private static void checkAppsSql(Context context, List<NotificationPreferencesEntity> apps) {
        Logger.trace("FilesExtrasActivity checkAppsSql");

        List<String> installedApps = getInstalledPackagesNames(context);

        for (NotificationPreferencesEntity p : apps) {

            if (Collections.binarySearch(installedApps, p.getPackageName()) < 0) {
                SQLite
                        .delete()
                        .from(NotificationPreferencesEntity.class)
                        .where(NotificationPreferencesEntity_Table.packageName.eq(p.getPackageName()))
                        .query();

                Logger.info("FilesExtrasActivity checkAppsSql removed app: " + p.getPackageName());
            }
        }

    }

    public static void loadAppsPrefsFromJSON() {
        Logger.trace("FilesExtrasActivity loadAppsPrefsFromJSON");

        String packagesJson = Prefs.getString(Constants.PREF_ENABLED_NOTIFICATIONS_PACKAGES, "[]");

        if (!packagesJson.equals("[]")) {

            String[] packagesList = new Gson().fromJson(packagesJson, String[].class);

            for (String p : packagesList) {
                SilenceApplicationHelper.enablePackage(p);
            }

            Prefs.putString(Constants.PREF_ENABLED_NOTIFICATIONS_PACKAGES, "[]");
            Logger.info("FilesExtrasActivity loadAppsPrefsFromJSON finished");
        }

        String filtersJson = Prefs.getString(Constants.PREF_ENABLED_NOTIFICATIONS_PACKAGES_FILTERS, "[]");
        Logger.debug("FilesExtrasActivity loadAppsPrefsFromJSON filters: " + filtersJson);
        try {
            if (!filtersJson.equals("[]")) {
                Map<String, String> packagesfilters = new Gson().fromJson(filtersJson, Map.class);

                for (Map.Entry<String, String> pair : packagesfilters.entrySet()) {
                    NotificationPreferencesEntity app =
                            SQLite
                                    .select()
                                    .from(NotificationPreferencesEntity.class)
                                    .where(NotificationPreferencesEntity_Table.packageName.eq(pair.getKey()))
                                    .querySingle();
                    app.setFilter(pair.getValue());
                    app.setWhitelist(false);
                    FlowManager
                            .getModelAdapter(NotificationPreferencesEntity.class)
                            .update(app);
                }
            }
        } catch (Exception ex) {
            Logger.error(ex, ex.getMessage());
        }
    }

    private static List<String> getInstalledPackagesNames(Context context) {

        Logger.debug("FilesExtrasActivity getInstalledPackagesNames");

        List<PackageInfo> packagesInstalled = context.getPackageManager().getInstalledPackages(0);
        List<String> packagesInstalledNames = new ArrayList<>();

        for (PackageInfo p : packagesInstalled) {
            packagesInstalledNames.add(p.packageName);
        }

        Collections.sort(packagesInstalledNames);

        return packagesInstalledNames;

    }

    public static void saveAppsDbToPrefs() {

        Logger.trace("FilesExtrasActivity saveAppsDbToPrefs");

        List<NotificationPreferencesEntity> apps = SQLite
                .select()
                .from(NotificationPreferencesEntity.class)
                .queryList();

        if (apps.size() > 0) {

            List<String> dummy = new ArrayList<>();
            ArrayMap<String, String> filters = new ArrayMap<>();

            for (NotificationPreferencesEntity p : apps) {
                dummy.add(p.getPackageName());
                filters.put(p.getPackageName(), p.getFilter());
                //Logger.debug("FilesExtrasActivity saveAppsDbToPrefs added: " + p.getPackageName());
            }

            if (dummy.size() > 0) {
                String pref = new Gson().toJson(dummy);
                Prefs.putString(Constants.PREF_ENABLED_NOTIFICATIONS_PACKAGES, pref);
                String pref_filter = new Gson().toJson(filters);
                Prefs.putString(Constants.PREF_ENABLED_NOTIFICATIONS_PACKAGES_FILTERS, pref_filter);
                Prefs.edit().commit();
            }
        }
    }


    public static void saveCommandHistoryToPrefs() {
        Logger.trace("FilesExtrasActivity saveCommandHistoryToPrefs");

        List<CommandHistoryEntity> commandHistoryValues = SQLite
                .select()
                .from(CommandHistoryEntity.class)
                .orderBy(CommandHistoryEntity_Table.date.asc())
                .queryList();

        if (commandHistoryValues.size() > 0) {
            List<String> commands = new ArrayList<>();

            for (CommandHistoryEntity p : commandHistoryValues) {
                commands.add(p.getCommand());
            }

            if (commands.size() > 0) {
                String pref = new Gson().toJson(commands);
                Prefs.putString(Constants.PREF_COMMAND_HISTORY, pref);
                Prefs.edit().commit();
            }
        }
    }

    public static void loadCommandHistoryFromPrefs() {
        Logger.trace("FilesExtrasActivity loadCommandHistoryFromPrefs");

        String commandsJSON = Prefs.getString(Constants.PREF_COMMAND_HISTORY, "[]");

        if (!commandsJSON.equals("[]")) {
            String[] commands = new Gson().fromJson(commandsJSON, String[].class);
            for (String p : commands) {
                saveCommandToHistory(p);
            }

            Prefs.putString(Constants.PREF_COMMAND_HISTORY, "[]");
            Logger.info("FilesExtrasActivity loadCommandHistoryFromPrefs finished");
        }
    }

    public static void saveCommandToHistory(String command) {
        Logger.trace("FilesExtrasActivity saveCommandToHistory");

        CommandHistoryEntity previousSameCommand = SQLite
                .select()
                .from(CommandHistoryEntity.class)
                .where(CommandHistoryEntity_Table.command.eq(command))
                .querySingle();

        if (previousSameCommand != null) {
            previousSameCommand.setDate(System.currentTimeMillis());
            FlowManager
                    .getModelAdapter(CommandHistoryEntity.class)
                    .update(previousSameCommand);
        } else {
            CommandHistoryEntity commandHistoryEntity = new CommandHistoryEntity();
            commandHistoryEntity.setCommand(command);
            commandHistoryEntity.setDate(System.currentTimeMillis());

            FlowManager
                    .getModelAdapter(CommandHistoryEntity.class)
                    .insert(commandHistoryEntity);
        }
    }

    public static void eraseAppsPrefs() {
        Logger.debug("FilesExtrasActivity eraseAppsPrefs");

        Prefs.putString(Constants.PREF_ENABLED_NOTIFICATIONS_PACKAGES, "[]");
        Prefs.putString(Constants.PREF_ENABLED_NOTIFICATIONS_PACKAGES_FILTERS, "[]");
        Prefs.edit().commit();

    }
}