package se.miun.nfc.sensor;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;

import se.deckmar.nodejsdroid.NodeJSConnector;
import se.miun.nfc.lib.NfcHelper;
import se.miun.nfc.lib.listeners.NfcTagListener;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MainSensorReader extends Activity implements NfcTagListener {

	// //////////////////////
	// Här är hårdkodningen//
	// //////////////////////

	private String confBackendHost = "www.deckmar.net";
	private int confBackendPort = 53453;

	// /Read Temp
	private byte readTempFlags = (byte) 0x00;
	private byte readTempCommand = (byte) 0xAD;
	private byte[] readTemptrancieveData = new byte[] { readTempFlags, readTempCommand, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };

	// Other Command
	private byte otherFlags = (byte) 0x00;
	private byte otherCommand = (byte) 0xA3;
	private byte[] otherTrancieveData = new byte[] { otherFlags, otherCommand, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };

	// //////////////////////////
	// Här slutar hårdkodningen//
	// //////////////////////////

	private NfcHelper nfcHelper;
	private ArrayAdapter<String> mReadoutArrayAdapter;
	private TextView mTvTagId;
	private TextView mTvStdMean;
	private TextView mTvStdDeviation;
	private TextView mTvStdSize;

	/**
	 * Statistics
	 */
	private int std_numValues = 0;
	private float std_sumValues = 0;
	private float std_mean = 0;
	private float std_sumSquareDifference = 0;
	private float std_deviation = 0;

	/**
	 * Backend
	 */
	private BackendSensorPipe mBackendSensorPipe;
	private NodeJSConnector mNodeJSConn;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.main);
		setShowProgress(false);

		nfcHelper = new NfcHelper(this);
		this.nfcHelper.listenForAllTags(null, this);

		mReadoutArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
		ListView lv = (ListView) findViewById(android.R.id.list);
		lv.setAdapter(mReadoutArrayAdapter);

		mTvTagId = (TextView) findViewById(R.id.tv_tag_id);
		mTvStdMean = (TextView) findViewById(R.id.tv_std_mean);
		mTvStdDeviation = (TextView) findViewById(R.id.tv_std_deviation);
		mTvStdSize = (TextView) findViewById(R.id.tv_std_size);

		clearSensorValues();
		updateStdGui();

//		mNodeJSConn = new NodeJSConnector("www.deckmar.net", 9999, false, mNodeJSHandler);
	}

	private Handler mNodeJSHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case NodeJSConnector.MESSAGE_STATE_CHANGED:
				System.out.println("NodeJS state is now: " + msg.arg1);

				if (msg.arg1 == NodeJSConnector.STATE_CONNECTED) {
					try {
						mNodeJSConn.sendMessage(new JSONObject("{hello:'world'}"));
						mNodeJSConn.sendMessage(new JSONObject("{hejsan:'världen'}"));
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				break;

			case NodeJSConnector.MESSAGE_INCOMMING:
				System.out.println("Incomming JSON: " + (JSONObject) msg.obj);
				break;
			}
		}
	};
	

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		this.nfcHelper.onPauseHook();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		this.nfcHelper.onResumeHook();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// TODO Auto-generated method stub
		super.onNewIntent(intent);
		this.nfcHelper.onNewIntentHook(intent);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// setContentView(R.layout.main);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mNodeJSConn.kill();
	}

	@Override
	public void nfcDiscoverTag(Tag tag, Object callbackFlag) {
		// System.out.println("Tag discovered");
		final NfcV nfcV = NfcV.get(tag);
		if (nfcV == null)
			return;

		Executors.newSingleThreadExecutor().submit(new Runnable() {
			@Override
			public void run() {

				int totTries = 3;

				for (int t = 0; t < totTries; t++) {

					try {
						if (nfcV == null)
							return;
						if (!nfcV.isConnected())
							nfcV.connect();

						if (!nfcV.isConnected()) {
							continue;
						}

						String tagIdString = "NO_TAG_ID";
						if (nfcV.getTag().getId() != null && nfcV.getTag().getId().length > 0) {
							tagIdString = toHex(nfcV.getTag().getId());
						}
						final String tagId = tagIdString;

						if (mBackendSensorPipe == null || !mBackendSensorPipe.getConnectionState()) {
							mBackendSensorPipe = new BackendSensorPipe(confBackendHost, confBackendPort);
							mBackendSensorPipe.connect();
							mBackendSensorPipe.send("Hello " + tagId);
						}

						MainSensorReader.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								setSensorAndClearIfNew(tagId);
								updateStdGui();
								setShowProgress(true);
							}
						});

						for (;;) {
							byte[] recv = nfcV.transceive(readTemptrancieveData);
							// byte[] other =
							// nfcV.transceive(otherTrancieveData);

							// Get the readTempReturnValue
							byte[] readTempReturnValue = recv;
							String displayTempHex = toHex(readTempReturnValue);

							// Convert to numerical values using the formula and
							// using 2 decimals.
							// byte b1 = readTempReturnValue[0];
							byte b2 = readTempReturnValue[1];
							byte b3 = readTempReturnValue[2];
							int readTempRaw = ((b3 & 0xff) << 8) | (b2 & 0xff);
							double readTempValue = readTempRaw * 0.169 - 92.7 - 5.4;
							readTempValue = readTempValue * 100;
							readTempValue = Math.round(readTempValue);
							readTempValue = readTempValue / 100;
							final String displayTempNum = String.valueOf(readTempValue);

							mBackendSensorPipe.send(displayTempNum);

							System.out.println("displayTempNum: " + displayTempNum);

							addNewSensorValue((float) readTempValue);

							MainSensorReader.this.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									mReadoutArrayAdapter.add(displayTempNum);
									updateStdGui();
								}
							});

						}
					} catch (Exception e) {
						e.printStackTrace();
						try {
							nfcV.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						final int triesLeft = totTries - t;

						System.out.println("Connecting again (" + triesLeft + ")");

						// MainSensorReader.this.runOnUiThread(new Runnable() {
						// @Override
						// public void run() {
						// Toast.makeText(MainSensorReader.this,
						// "Connecting again (" + triesLeft + ")",
						// Toast.LENGTH_SHORT)
						// .show();
						// }
						// });
					}
				}

				mBackendSensorPipe.killConnection();

				/**
				 * Done with measurements
				 */
				MainSensorReader.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						setShowProgress(false);
					}
				});
			}
		});

	}

	public void setShowProgress(boolean state) {
		setProgressBarIndeterminateVisibility(state);
	}

	private void setSensorAndClearIfNew(String id) {
		if (!id.equals(mTvTagId.getText().subSequence(5, mTvTagId.length()))) {
			mTvTagId.setText("Tag: " + id);
			mReadoutArrayAdapter.clear();
			clearSensorValues();
		}
	}

	private void addNewSensorValue(float value) {
		std_numValues++;
		std_sumValues += value;
		if (std_numValues <= 1) {
			return;
		}
		std_mean = std_sumValues / std_numValues;
		std_sumSquareDifference += (value - std_mean) * (value - std_mean);
		std_deviation = (float) Math.sqrt(std_sumSquareDifference / (std_numValues - 1));
	}

	private void updateStdGui() {
		mTvStdMean.setText(String.format("%.2f", std_mean));
		mTvStdDeviation.setText(String.format("%.2f", std_deviation));
		mTvStdSize.setText("" + std_numValues);
	}

	private void clearSensorValues() {
		std_mean = 0;
		std_deviation = 0;
		std_numValues = 0;
		std_sumSquareDifference = 0;
		std_sumValues = 0;
	}

	// Help function, for Hex convertion
	private String toHex(byte[] bytes) {
		BigInteger bi = new BigInteger(1, bytes);
		return String.format("%0" + (bytes.length << 1) + "X", bi);
	}

	@Override
	public void nfcDiscoverNdef(Ndef tag, NdefMessage ndefMessage, Object callbackFlag) {
		System.out.println("Ndef discovered");
	}
}