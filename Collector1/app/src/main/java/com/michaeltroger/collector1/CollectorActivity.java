package com.michaeltroger.collector1;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class CollectorActivity extends Activity {

    @BindView(R.id.radioStanding)
    RadioButton radioStanding;
    @BindView(R.id.radioWalking)
    RadioButton radioWalking;
    @BindView(R.id.radioRunning)
    RadioButton radioRunning;
    @BindView(R.id.radioOther)
    RadioButton radioOther;
    @BindView(R.id.radioGroupLabels)
    RadioGroup radioGroupLabels;
    @BindView(R.id.btnCollect)
    Button btnCollect;
    @BindView(R.id.btnDeleteData)
    Button btnDeleteData;

    private enum State {
        IDLE, COLLECTING, TRAINING, CLASSIFYING
    }

    private final String[] mLabels = {Globals.CLASS_LABEL_STANDING,
            Globals.CLASS_LABEL_WALKING, Globals.CLASS_LABEL_RUNNING,
            Globals.CLASS_LABEL_OTHER};

    private Intent mServiceIntent;
    private File mFeatureFile;

    private State mState;

    @OnClick(R.id.btnCollect)
    public void onBtnCollectClicked(Button button) {
        if (mState == State.IDLE) {
            mState = State.COLLECTING;
            button.setText(R.string.ui_collector_button_stop_title);
            btnDeleteData.setEnabled(false);
            radioStanding.setEnabled(false);
            radioWalking.setEnabled(false);
            radioRunning.setEnabled(false);
            radioOther.setEnabled(false);

            int acvitivtyId = radioGroupLabels.indexOfChild(findViewById(radioGroupLabels
                    .getCheckedRadioButtonId()));
            String label = mLabels[acvitivtyId];

            Bundle extras = new Bundle();
            extras.putString(Globals.CLASS_LABEL_KEY, label);
            mServiceIntent.putExtras(extras);

            startService(mServiceIntent);

        } else if (mState == State.COLLECTING) {
            mState = State.IDLE;
            button.setText(R.string.ui_collector_button_start_title);
            btnDeleteData.setEnabled(true);
            radioStanding.setEnabled(true);
            radioWalking.setEnabled(true);
            radioRunning.setEnabled(true);
            radioOther.setEnabled(true);

            stopService(mServiceIntent);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
        }
    }

    @OnClick(R.id.btnDeleteData)
    public void onBtnDeleteDataClicked() {
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ButterKnife.bind(this);

        mState = State.IDLE;
        mFeatureFile = new File(getExternalFilesDir(null),
                Globals.FEATURE_FILE_NAME);
        mServiceIntent = new Intent(this, SensorsService.class);
    }

    @Override
    public void onBackPressed() {

        if (mState == State.TRAINING) {
            return;
        } else if (mState == State.COLLECTING || mState == State.CLASSIFYING) {
            stopService(mServiceIntent);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .cancel(Globals.NOTIFICATION_ID);
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
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .cancelAll();
        }
        finish();
        super.onDestroy();
    }

}