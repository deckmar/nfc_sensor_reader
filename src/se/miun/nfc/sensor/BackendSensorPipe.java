package se.miun.nfc.sensor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;

import android.os.Handler;

public class BackendSensorPipe {

	private String mBackendHost;
	private int mBackendPort;
	private Socket mSocket;

	public BackendSensorPipe(String mBackendHost, int mBackendPort) {
		super();
		this.mBackendHost = mBackendHost;
		this.mBackendPort = mBackendPort;
	}

	public void connect() {
		try {
			mSocket = new Socket(mBackendHost, mBackendPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			killConnection();
		} catch (IOException e) {
			e.printStackTrace();
			killConnection();
		}
	}

	public void send(String msg) {
		if (getConnectionState()) {
			try {
				mSocket.getOutputStream().write(msg.getBytes("UTF-8"));
				mSocket.getOutputStream().write("\n".getBytes("UTF-8"));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				killConnection();
			} catch (IOException e) {
				e.printStackTrace();
				killConnection();
			}
		}
	}

	public boolean getConnectionState() {
		return mSocket != null && mSocket.isConnected();
	}

	public void killConnection() {
		if (mSocket != null) {
			try {
				mSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			mSocket = null;
		}
	}

}
