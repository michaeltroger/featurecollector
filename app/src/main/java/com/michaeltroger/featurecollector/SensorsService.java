/**
 * LocationService.java
 * 
 * Created by Xiaochao Yang on Sep 11, 2011 4:50:19 PM
 * 
 */

package com.michaeltroger.featurecollector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;
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
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import com.google.common.util.concurrent.AtomicDouble;
import com.meapsoft.FFT;

public class SensorsService extends Service implements SensorEventListener {

	private static final int M_FEAT_LEN = Globals.ACCELEROMETER_BLOCK_CAPACITY + 2;
	private static final boolean USE_OVERLAPPING_WINDOW = true;
	private File mFeatureFile;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private int mServiceTaskType;
	private String mLabel;
	private Instances mDataset;
	private Attribute mClassAttribute;
	private FeatureVectorTask featureVectorTask;

	private static final String[] LABELS = {
			Globals.CLASS_LABEL_STANDING,
			Globals.CLASS_LABEL_WALKING,
			Globals.CLASS_LABEL_RUNNING,
			Globals.CLASS_LABEL_OTHER
	};


	private AtomicDouble cachedAccValue = new AtomicDouble();

	private static ArrayBlockingQueue<Double> mAccBuffer;
	public static final DecimalFormat mdf = new DecimalFormat("#.##");
	private SamplingTask samplingTask;

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

		mFeatureFile = new File(getExternalFilesDir(null), Globals.FEATURE_FILE_NAME);

		mServiceTaskType = Globals.SERVICE_TASK_TYPE_COLLECT;

		// Create the container for attributes
		final ArrayList<Attribute> allAttr = new ArrayList<>();

		// Adding FFT coefficient attributes
		final DecimalFormat df = new DecimalFormat("0000");

		for (int i = 0; i < Globals.ACCELEROMETER_BLOCK_CAPACITY; i++) {
			allAttr.add(new Attribute(Globals.FEAT_FFT_COEF_LABEL + df.format(i)));
		}
		// Adding the max feature
		allAttr.add(new Attribute(Globals.FEAT_MAX_LABEL));

		// Declare a nominal attribute along with its candidate values
		final ArrayList<String> labelItems = new ArrayList<>(Arrays.asList(LABELS));
		mClassAttribute = new Attribute(Globals.CLASS_LABEL_KEY, labelItems);
		allAttr.add(mClassAttribute);

		// Construct the dataset with the attributes specified as allAttr and
		// capacity 10000
		mDataset = new Instances(Globals.FEAT_SET_NAME, allAttr, Globals.FEATURE_SET_CAPACITY);

		// Set the last column/attribute (standing/walking/running) as the class
		// index for classification
		mDataset.setClassIndex(mDataset.numAttributes() - 1);

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
				.setSmallIcon(R.drawable.ic_stat_name).setContentIntent(pi).build();
		final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notification.flags = notification.flags | Notification.FLAG_ONGOING_EVENT;
		notificationManager.notify(0, notification);

		samplingTask = new SamplingTask();
		featureVectorTask = new FeatureVectorTask();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			samplingTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			featureVectorTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} else {
			samplingTask.execute();
			featureVectorTask.execute();
		}

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		featureVectorTask.cancel(true);
		samplingTask.cancel(true);
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		mSensorManager.unregisterListener(this);
		super.onDestroy();

	}

	private class SamplingTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... voids) {
			long startTimeMillis = System.currentTimeMillis();

			while (true) {
				if (isCancelled()) {
					return null;
				}

				final long currentTimeMillis = System.currentTimeMillis();

				if (currentTimeMillis >= startTimeMillis + Globals.SAMPLING_RATE_MILLIS) {
					startTimeMillis = currentTimeMillis;
					final double cachedValue = cachedAccValue.get();
					try {
						mAccBuffer.add(cachedValue);
					} catch (IllegalStateException e) {
						final ArrayBlockingQueue<Double> newBuf = new ArrayBlockingQueue<>(mAccBuffer.size() * 2);

						mAccBuffer.drainTo(newBuf);
						mAccBuffer = newBuf;
						mAccBuffer.add(cachedValue);
					}
				}
			}
		}
	}

	private class FeatureVectorTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... arg0) {

			final Instance inst = new DenseInstance(M_FEAT_LEN);
			inst.setDataset(mDataset);

			int blockSize = 0;
			int blockSize2 = 0;

			final FFT fft = new FFT(Globals.ACCELEROMETER_BLOCK_CAPACITY);
			final double[] accBlock = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];
			final double[] re = accBlock;
			final double[] im = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];

			final double[] accBlock2 = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];
			final double[] re2 = accBlock2;
			final double[] im2 = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];

			final int ACCELEROMETER_HALF_BLOCK_CAPACITY = Globals.ACCELEROMETER_BLOCK_CAPACITY / 2;
			boolean isInitial = true;

			double max = Double.MIN_VALUE;
			double max2 = Double.MIN_VALUE;

			while (true) {
				try {
					if (isCancelled())
				    {
				        return null;
				    }

					// Dumping buffer
					final double buffer = mAccBuffer.take();
					accBlock[blockSize++] = buffer;

					if (!isInitial) {
						accBlock2[blockSize2++] = buffer;
					}

					if (isInitial && blockSize == ACCELEROMETER_HALF_BLOCK_CAPACITY) {
						isInitial = false;
					}

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
							final double mag = Math.sqrt(re[i] * re[i] + im[i] * im[i]);
							inst.setValue(i, mag);
							im[i] = .0; // Clear the field
						}

						// Append max after frequency component
						inst.setValue(Globals.ACCELEROMETER_BLOCK_CAPACITY, max);
						inst.setValue(mClassAttribute, mLabel);
						mDataset.add(inst);
					} else if (USE_OVERLAPPING_WINDOW && blockSize2 == Globals.ACCELEROMETER_BLOCK_CAPACITY) {
						blockSize2 = 0;

						// time = System.currentTimeMillis();
						max2 = .0;
						for (final double val : accBlock2) {
							if (max2 < val) {
								max2 = val;
							}
						}

						fft.fft(re2, im2);

						for (int i = 0; i < re2.length; i++) {
							final double mag = Math.sqrt(re2[i] * re2[i] + im2[i] * im2[i]);
							inst.setValue(i, mag);
							im2[i] = .0; // Clear the field
						}

						// Append max after frequency component
						inst.setValue(Globals.ACCELEROMETER_BLOCK_CAPACITY, max2);
						inst.setValue(mClassAttribute, mLabel);
						mDataset.add(inst);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		protected void onCancelled() {
			if (mServiceTaskType == Globals.SERVICE_TASK_TYPE_CLASSIFY) {
				super.onCancelled();
				return;
			}
			String toastDisp;

			if (mFeatureFile.exists()) {

				// merge existing and delete the old dataset
				final DataSource source;
				try {
					// Create a datasource from mFeatureFile where
					// mFeatureFile = new File(getExternalFilesDir(null),
					// "features.arff");
					source = new DataSource(new FileInputStream(mFeatureFile));
					// Read the dataset set out of this datasource
					final Instances oldDataset = source.getDataSet();
					oldDataset.setClassIndex(mDataset.numAttributes() - 1);
					// Sanity checking if the dataset format matches.
					if (!oldDataset.equalHeaders(mDataset)) {
						throw new Exception(
								"The two datasets have different headers:\n");
					}

					// Move all items over manually
					for (int i = 0; i < mDataset.size(); i++) {
						oldDataset.add(mDataset.get(i));
					}

					mDataset = oldDataset;
					// Delete the existing old file.
					mFeatureFile.delete();
				} catch (Exception e) {
					e.printStackTrace();
				}
				toastDisp = getString(R.string.ui_sensor_service_toast_success_file_updated);

			} else {
				toastDisp = getString(R.string.ui_sensor_service_toast_success_file_created)   ;
			}
			// create new Arff file
			final ArffSaver saver = new ArffSaver();
			// Set the data source of the file content
			saver.setInstances(mDataset);
			try {
				// Set the destination of the file.
				// mFeatureFile = new File(getExternalFilesDir(null),
				// "features.arff");
				saver.setFile(mFeatureFile);
				// Write into the file
				saver.writeBatch();
			} catch (IOException e) {
				toastDisp = getString(R.string.ui_sensor_service_toast_error_file_saving_failed);
				e.printStackTrace();
			}
			Toast.makeText(getApplicationContext(), toastDisp, Toast.LENGTH_SHORT).show();

			super.onCancelled();
		}

	}

	public void onSensorChanged(SensorEvent event) {

		if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
			cachedAccValue.set(Math.sqrt(
					event.values[0] * event.values[0]
					+ event.values[1] * event.values[1]
					+ event.values[2] * event.values[2]
			));
		}
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
