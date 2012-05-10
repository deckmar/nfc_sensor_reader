package se.miun.nfc.sensor;

import java.io.IOException;
import java.util.concurrent.Executors;

import se.miun.nfc.lib.NfcHelper;
import se.miun.nfc.lib.listeners.NfcTagListener;
import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcV;
import android.os.Bundle;

public class MainSensorReader extends Activity implements NfcTagListener {

	private NfcHelper nfcHelper;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		nfcHelper = new NfcHelper(this);
		this.nfcHelper.listenForAllTags(null, this);
	}
	
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
	public void nfcDiscoverTag(Tag tag, Object callbackFlag) {
		System.out.println("Tag discovered");
		final NfcV nfcV = NfcV.get(tag);
		if (nfcV == null)
			return;

		Executors.newSingleThreadExecutor().submit(new Runnable() {
			@Override
			public void run() {

				byte[] send = new byte[0];
				try {
					nfcV.connect();
					byte[] recv = nfcV.transceive(send);
					System.out.println("recv len: " + recv.length);
					System.out.println("recv content:");
					for (byte b : recv) {
						System.out.println(String.format("%s -- %i", new String(new byte[] { b }), (int) b));
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

	}

	@Override
	public void nfcDiscoverNdef(Ndef tag, NdefMessage ndefMessage, Object callbackFlag) {
		System.out.println("Ndef discovered");
	}
}