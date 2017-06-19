package com.michaeltroger.prediction1;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;

public class CollectorActivity extends Activity {
	private MyRecyclerViewAdapter adapter;
	private BroadcastReceiver mBroadcastReceiver;
	private final ArrayList<String> animalNames =  new ArrayList<>();

	public void onClearClicked(View view) {
		animalNames.clear();
		adapter.notifyDataSetChanged();
	}

	private enum State {
		IDLE, COLLECTING, TRAINING, CLASSIFYING
	}

	private final String[] mLabels = { Globals.CLASS_LABEL_STANDING,
			Globals.CLASS_LABEL_WALKING, Globals.CLASS_LABEL_RUNNING,
			Globals.CLASS_LABEL_OTHER };

	private Intent mServiceIntent;

	private State mState;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mState = State.IDLE;
		mServiceIntent = new Intent(this, SensorsService.class);

		RecyclerView recyclerView = (RecyclerView) findViewById(R.id.rvActivities);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		adapter = new MyRecyclerViewAdapter(this, animalNames);
		recyclerView.setAdapter(adapter);

		IntentFilter filter = new IntentFilter(Globals.ACTION_NAME);
		mBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String activity = intent.getStringExtra("key");
				animalNames.add(activity);
				adapter.notifyDataSetChanged();
			}
		};
		registerReceiver(mBroadcastReceiver, filter);






	}

	public void onRecognizeClicked(View view) {
		Log.d("TAG", "collecting");
		if (mState == State.IDLE) {
			mState = State.COLLECTING;
			((Button) view).setText(R.string.ui_collector_button_stop_title);

			Bundle extras = new Bundle();
			mServiceIntent.putExtras(extras);

			startService(mServiceIntent);

		} else if (mState == State.COLLECTING) {
			mState = State.IDLE;
			((Button) view).setText(R.string.ui_collector_button_start_title);

			stopService(mServiceIntent);
			((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
		}
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
		this.unregisterReceiver(mBroadcastReceiver);
		finish();
		super.onDestroy();
	}

}