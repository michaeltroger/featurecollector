/**
 * LocationService.java
 * 
 * Created by Xiaochao Yang on Sep 11, 2011 4:50:19 PM
 * 
 */

package com.michaeltroger.prediction1;

import java.io.File;
import java.text.DecimalFormat;
import java.util.concurrent.ArrayBlockingQueue;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.meapsoft.FFT;

public class SensorsService extends Service implements SensorEventListener {

	private static final int mFeatLen = Globals.ACCELEROMETER_BLOCK_CAPACITY + 2;
	
	private File mFeatureFile;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private int mServiceTaskType;
	private String mLabel;
	private OnSensorChangedTask mAsyncTask;
	private static final String[] LABELS = {
            Globals.CLASS_LABEL_STANDING,
            Globals.CLASS_LABEL_WALKING,
            Globals.CLASS_LABEL_RUNNING,
            Globals.CLASS_LABEL_OTHER
	};

	private static ArrayBlockingQueue<Double> mAccBuffer;
	public static final DecimalFormat mdf = new DecimalFormat("#.##");

	@Override
	public void onCreate() {
		super.onCreate();

		mAccBuffer = new ArrayBlockingQueue<>(Globals.ACCELEROMETER_BUFFER_CAPACITY);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

		final Bundle extras = intent.getExtras();
		mLabel = extras.getString(Globals.CLASS_LABEL_KEY);

		mServiceTaskType = Globals.SERVICE_TASK_TYPE_COLLECT;

		final Intent i = new Intent(this, CollectorActivity.class);
		// Read:
		// http://developer.android.com/guide/topics/manifest/activity-element.html#lmode
		// IMPORTANT!. no re-create activity
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

		final PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

		final Notification notification = new Notification.Builder(this)
				.setContentTitle(
						getApplicationContext().getString(
								R.string.ui_sensor_service_notification_title))
				.setContentText(
						getResources()
								.getString(
										R.string.ui_sensor_service_notification_content))
				.setSmallIcon(R.drawable.greend).setContentIntent(pi).build();
		final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notification.flags = notification.flags | Notification.FLAG_ONGOING_EVENT;
		notificationManager.notify(0, notification);


		mAsyncTask = new OnSensorChangedTask();
		mAsyncTask.execute();

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		mAsyncTask.cancel(true);
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		mSensorManager.unregisterListener(this);
		super.onDestroy();

	}

	private class OnSensorChangedTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... arg0) {

			int blockSize = 0;
			final FFT fft = new FFT(Globals.ACCELEROMETER_BLOCK_CAPACITY);
			final double[] accBlock = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];
			final double[] re = accBlock;
			final double[] im = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];

			double max = Double.MIN_VALUE;

			final Double[] featureVector = new Double[65];
			int[] recognizedActivityCounts = new int[4];
			double time = System.currentTimeMillis();

			while (true) {
				try {
					// need to check if the AsyncTask is cancelled or not in the while loop
					if (isCancelled())
				    {
				        return null;
				    }
					
					// Dumping buffer
					accBlock[blockSize++] = mAccBuffer.take();

					if (blockSize == Globals.ACCELEROMETER_BLOCK_CAPACITY) {
						blockSize = 0;

						// time = System.currentTimeMillis();
						max = .0;
						for (final double val : accBlock) {
							if (max < val) {
								max = val;
							}
						}

						fft.fft(re, im);

						for (int i = 0; i < re.length; i++) {
							double mag = Math.sqrt(re[i] * re[i] + im[i] * im[i]);
							featureVector[i] = mag;
							im[i] = .0; // Clear the field
						}

						// Append max after frequency component
						featureVector[64] = max;
						final Double p = WekaClassifier.classify(featureVector);

						int indexDetectedActivity = p.intValue();
						recognizedActivityCounts[indexDetectedActivity]++;

						final double currentTime = System.currentTimeMillis();
						if (currentTime >= time + 5000) {
							time = currentTime;
							int maxIndex = -1;
							for (int i = 0; i < recognizedActivityCounts.length; i++) {
								if (recognizedActivityCounts[i] > maxIndex) {
									maxIndex = i;
								}
								recognizedActivityCounts[i] = 0;
							}
							//Put your all data using put extra

							final Intent broadcastIntent = new Intent();
							broadcastIntent.putExtra("key", LABELS[maxIndex]);
							broadcastIntent.setAction(Globals.ACTION_NAME);
							sendBroadcast(broadcastIntent);
						}

					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
		}

	}

	public void onSensorChanged(SensorEvent event) {

		if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

			final double m = Math.sqrt(
					event.values[0] * event.values[0]
					+ event.values[1] * event.values[1]
					+ event.values[2] * event.values[2]
			);

			// Inserts the specified element into this queue if it is possible
			// to do so immediately without violating capacity restrictions,
			// returning true upon success and throwing an IllegalStateException
			// if no space is currently available. When using a
			// capacity-restricted queue, it is generally preferable to use
			// offer.

			try {
				mAccBuffer.add(m);
			} catch (IllegalStateException e) {

				// Exception happens when reach the capacity.
				// Doubling the buffer. ListBlockingQueue has no such issue,
				// But generally has worse performance
				ArrayBlockingQueue<Double> newBuf = new ArrayBlockingQueue<>(mAccBuffer.size() * 2);

				mAccBuffer.drainTo(newBuf);
				mAccBuffer = newBuf;
				mAccBuffer.add(m);
			}
		}
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
