package com.michaeltroger.featurecollector;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Toast;

import com.michaeltroger.featurecollector.databinding.MainBinding;

import java.io.File;

public class CollectorActivity extends Activity {

    private static final String[] mLabels = {
            Globals.CLASS_LABEL_STANDING,
            Globals.CLASS_LABEL_WALKING,
            Globals.CLASS_LABEL_RUNNING,
            Globals.CLASS_LABEL_OTHER
    };
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private MainBinding binding;
    private Intent mServiceIntent;
    private File mFeatureFile;
    private State mState;

    private enum State {
        IDLE,
        COLLECTING,
        TRAINING,
        CLASSIFYING
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
            );
        }
    }

    public class MyHandlers {
        public void onBtnCollectClicked(View view) {
            if (mState == State.IDLE) {
                mState = State.COLLECTING;
                binding.btnCollect.setText(R.string.ui_collector_button_stop_title);
                binding.btnDeleteData.setEnabled(false);
                binding.radioStanding.setEnabled(false);
                binding.radioWalking.setEnabled(false);
                binding.radioRunning.setEnabled(false);
                binding.radioOther.setEnabled(false);

                final int acvitivtyId = binding.radioGroupLabels.indexOfChild(
                        findViewById(binding.radioGroupLabels.getCheckedRadioButtonId())
                );
                final String label = mLabels[acvitivtyId];

                final Bundle extras = new Bundle();
                extras.putString(Globals.CLASS_LABEL_KEY, label);
                mServiceIntent.putExtras(extras);

                startService(mServiceIntent);

            } else if (mState == State.COLLECTING) {
                mState = State.IDLE;
                binding.btnCollect.setText(R.string.ui_collector_button_start_title);
                binding.btnDeleteData.setEnabled(true);
                binding.radioStanding.setEnabled(true);
                binding.radioWalking.setEnabled(true);
                binding.radioRunning.setEnabled(true);
                binding.radioOther.setEnabled(true);

                stopService(mServiceIntent);
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
            }
        }

        public void onBtnDeleteDataClicked(View view) {
            if (Environment.MEDIA_MOUNTED.equals(Environment
                    .getExternalStorageState())) {
                if (mFeatureFile.exists()) {
                    mFeatureFile.delete();
                }

                Toast.makeText(getApplicationContext(),
                        R.string.ui_collector_toast_file_deleted,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        binding = DataBindingUtil.setContentView(this, R.layout.main);
        binding.setHandlers(new MyHandlers());

        mState = State.IDLE;
        mFeatureFile = new File(getExternalFilesDir(null), Globals.FEATURE_FILE_NAME);
        mServiceIntent = new Intent(this, SensorsService.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestPermission();
    }

    @Override
    public void onBackPressed() {

        if (mState == State.TRAINING) {
            return;
        } else if (mState == State.COLLECTING || mState == State.CLASSIFYING) {
            stopService(mServiceIntent);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(Globals.NOTIFICATION_ID);
        }
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        // Stop the service and the notification.
        // Need to check whether the mSensorService is null or not.
        if (mState == State.TRAINING) {
            return;
        } else if (mState == State.COLLECTING || mState == State.CLASSIFYING) {
            stopService(mServiceIntent);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
        }
        finish();
        super.onDestroy();
    }

}