package com.alternatewifimeshmessaging;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;

public class HostManager implements Runnable {
	private static final String TAG = "AlternateWifiMeshMessaging HostManager";
	private static final String AP_NAME = "rftrt";
	protected static final int PORT = 12345;
	private static final long[] MIN_TIME_RANGE = { 30, 90 }; // in seconds
	private int readers, writters;
	private ArrayList<Data> data;
	private WifiManager wifiManager;
	private WifiConfiguration wifiConfig;
	private Handler handler;
	private Random random;
	private int ID;
	private ArrayAdapter<String> arrayAdapter;
	private String identity;

	public HostManager(Context context, Handler handler) {
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		this.handler = handler;
		random = new Random();
		readers = 0;
		writters = 0;
		data = new ArrayList<Data>();
		ID = 0;

		arrayAdapter = new ArrayAdapter<String>(context,
				android.R.layout.simple_list_item_1);
		createIdentity();

	}

	@Override
	public void run() {

		while (true) {

			server();
			client();
		}

	}
	
	private void createIdentity() {
		int randomInt = random.nextInt();
		identity = wifiConfig.BSSID + randomInt;
		
		byte[] byteArray = null;
		try {
			byteArray = identity.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}

		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		byte[] digest = md.digest(byteArray);
		
		identity = new String(digest);
	}

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// /////////////// SERVER METHODS
	// /////////////////////////////////////////////////////////////////////
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private void server() {

		createAccessPoint();

		long begin = System.currentTimeMillis();

		while (System.currentTimeMillis() < getMinTime() + begin) {
		
			ArrayList<InetAddress> connectedClients = connectedClientList();
			ArrayList<Thread> threadList = new ArrayList<Thread>();
			
			if (connectedClients.size() > 0) {
				Log.wtf(TAG, connectedClients.size() + "");
			}

			for (InetAddress inetAddress : connectedClients) {
				ServerCommunicator communicator = new ServerCommunicator(
						inetAddress, this);
				Thread thread = new Thread(communicator);
				thread.start();
				threadList.add(thread);

			}

			for (Thread thread : threadList) {
				try {
					thread.join();
				} catch (InterruptedException e) {
					Log.e(TAG, "Thread joining error", e);
				}
			}
			
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Log.e(TAG, "Loop regulation error", e);
			}

		}

		closeAccessPoint();
	}

	private void createAccessPoint() {
		wifiManager.setWifiEnabled(false);
		wifiConfig = new WifiConfiguration();
		wifiConfig.SSID = AP_NAME;
		wifiConfig.allowedKeyManagement.set(KeyMgmt.NONE);
		Method method;
		try {
			method = wifiManager.getClass().getMethod("setWifiApEnabled",
					WifiConfiguration.class, boolean.class);
			method.invoke(wifiManager, wifiConfig, true);
		} catch (Exception e) {
			Log.e(TAG, "", e);
		}

	}

	private void closeAccessPoint() {
		wifiManager.setWifiEnabled(false);
		wifiConfig = new WifiConfiguration();
		wifiConfig.SSID = AP_NAME;
		wifiConfig.allowedKeyManagement.set(KeyMgmt.NONE);
		Method method;
		try {
			method = wifiManager.getClass().getMethod("setWifiApEnabled",
					WifiConfiguration.class, boolean.class);
			method.invoke(wifiManager, wifiConfig, false);
		} catch (Exception e) {
			Log.e(TAG, "", e);
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// ////////////////// CLIENT METHOD
	// ////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private void client() {
		openWireless();

		long begin = System.currentTimeMillis();
		long minTime = getMinTime();

		List<WifiConfiguration> hotspotList = null;

		while (System.currentTimeMillis() - begin < minTime) {

			doWiFiScan();
			while (hotspotList == null) {
				hotspotList = wifiManager.getConfiguredNetworks();
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {

					e.printStackTrace();
				}
			}
			// Log.v(TAG, hotspotList.size() + "");
			for (WifiConfiguration wc : hotspotList) {
				if (wc.SSID.contains(AP_NAME)) {
					wifiManager.disconnect();
					wifiManager.enableNetwork(wc.networkId, true);
					wifiManager.reconnect();

					ClientCommunicator communicator = new ClientCommunicator(
							this);
					communicator.run();
				}
			}

		}

		closeWireless();
	}

	private void openWireless() {
		wifiManager.setWifiEnabled(true);
	}

	private void doWiFiScan() {
		wifiManager.startScan();

		List<ScanResult> results = null;

		results = wifiManager.getScanResults();

		while (results == null) {
			results = wifiManager.getScanResults();
		}
		for (int i = 0; i < results.size(); i++) {
			configureNetwork(results.get(i));
			// Log.d(TAG, "do wifi scan for loop");
		}

	}

	private void closeWireless() {
		wifiManager.setWifiEnabled(false);
	}

	public void updateArrayAdapter() {
		arrayAdapter.clear();

		for (int i = 0; i < data.size(); i++) {
			arrayAdapter.add(data.get(i).data);
		}
		arrayAdapter.notifyDataSetChanged();
	}

	public ArrayAdapter<String> getArrayAdapter() {
		return arrayAdapter;
	}

	public void addMessage(String message) {

		writeLockOn();

		Data tmp = new Data(identity, ID++, message);
		data.add(tmp);
		
		//updateArrayAdapter();
		
		handler.sendEmptyMessage(0);

		writeLockOff();
	}

	private void configureNetwork(ScanResult scanResult) {
		WifiConfiguration wc = new WifiConfiguration();
		wc.SSID = "\"" + scanResult.SSID + "\"";
		wc.BSSID = scanResult.BSSID;
		wc.status = WifiConfiguration.Status.DISABLED;
		wc.priority = 40;
		wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
		wc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
		wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
		wc.status = WifiConfiguration.Status.ENABLED;

		wifiManager.addNetwork(wc);
		wifiManager.saveConfiguration();
	}

	public ArrayList<Data> getData() {
		readLockOn();
		ArrayList<Data> tmp = data;
		readLockOff();
		return tmp;
	}

	protected synchronized void insertData(Data data) {
		writeLockOn();

		if (!contains(data)) {
			this.data.add(data);
		}

		writeLockOff();
		
		handler.sendEmptyMessage(0);
	}

	private long getMinTime() {
		long time = random.nextLong() % MIN_TIME_RANGE[1] * 1000;
		

		if (time < MIN_TIME_RANGE[0] * 1000) {
			return getMinTime();
		} else {
			return time;
		}
	}

	/**
	 * 
	 * Synchronization methods
	 * 
	 */

	private synchronized void readLockOn() {
		while (writters != 0) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Log.e(TAG, "", e);
			}
		}
		readers++;
	}

	private synchronized void readLockOff() {
		readers--;
	}

	private synchronized void writeLockOff() {
		writters--;
	}

	private synchronized void writeLockOn() {
		while (writters != 0 || readers != 0) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Log.e(TAG, "", e);
			}
		}
		writters++;
	}

	private boolean contains(String BSSID, int ID) {
		for (Data d : data) {
			if (d.BSSID == BSSID && d.ID == ID) {
				return true;
			}
		}
		return false;
	}

	private boolean contains(Data data) {
		return contains(data.BSSID, data.ID);
	}

	private ArrayList<InetAddress> connectedClientList() {
		ArrayList<InetAddress> clients = new ArrayList<InetAddress>();

		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new FileReader("/proc/net/arp"));
		} catch (FileNotFoundException e) {
			Log.e(TAG, "File not found", e);
			e.printStackTrace();
		}

		String line = null;

		try {
			line = reader.readLine();
		} catch (IOException e) {
			Log.e(TAG, "Error line reading", e);
		}

		while (line != null) {

			String[] splitted = line.split(" +");

			if ((splitted != null) && (splitted.length >= 4)) {

				String mac = splitted[3];

				if (mac.matches("..:..:..:..:..:..")) {

					try {
						clients.add(InetAddress.getByName(splitted[0]));
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
				}
			}

			try {
				line = reader.readLine();
			} catch (IOException e) {
				Log.e(TAG, "Error line reading", e);
			}
		}

		try {
			reader.close();
		} catch (IOException e) {
			Log.e(TAG, "Error closing reader", e);
		}

		return clients;
	}

	public String getJSON() {

		JSONArray array = new JSONArray();

		ArrayList<Data> dataList = getData();

		for (Data data : dataList) {
			JSONObject object = new JSONObject();
			try {
				object.put("BSSID", data.BSSID);
				object.put("ID", data.ID);
				object.put("DATA", data.data);
				array.put(object);
			} catch (JSONException e) {
				Log.e(TAG, "JSON creating error", e);
			}
		}

		return array.toString();
	}

	public void addJSON(String JSON) {
		JSONArray array = null;
		try {
			array = new JSONArray(JSON);
		} catch (JSONException e) {
			Log.e(TAG, "Error parsing JSON array", e);
		}

		for (int n = 0; n < array.length(); n++) {

			try {
				JSONObject object = array.getJSONObject(n);
				Data data = new Data("",
						object.getInt("ID"), object.getString("DATA"));
				insertData(data);
			} catch (JSONException e) {
				Log.e(TAG, "Error parsing JSON object", e);
			}

		}
	}

}

class ServerCommunicator implements Runnable {
	private Socket socket;
	private DataOutputStream output;
	private HostManager hostManager;
	private static final String TAG = "AlternateWifiMeshMessaging ServerCommunicator";
	InetAddress ip;

	ServerCommunicator(InetAddress ip, HostManager hostManager) {
		this.ip = ip;
		this.hostManager = hostManager;
	}

	void connect() throws IOException {
		socket = new Socket(ip, HostManager.PORT);
		output = new DataOutputStream(socket.getOutputStream());
	}

	void disconnect() throws IOException {
		socket.close();
	}

	void send() throws IOException {
		String data = hostManager.getJSON();
		output.writeUTF(data);
	}

	@Override
	public void run() {
		try {
			connect();
		} catch (IOException e) {
			Log.e(TAG, "Connection to " + ip.toString() + " failed", e);
			return;
		}
		try {
			send();
		} catch (IOException e) {
			Log.e(TAG, "Data transfer to " + ip.toString() + " failed", e);
		}
		try {
			disconnect();
		} catch (IOException e) {
			Log.e(TAG, "Disconnection from " + ip + " failed", e);
		}
	}

}

class ClientCommunicator implements Runnable {

	private ServerSocket serverSocket;
	private HostManager hostManager;
	private static final String TAG = "";

	@Override
	public void run() {

		openSocket();

		getData();

		closeSocket();

	}

	public ClientCommunicator(HostManager hostManager) {
		this.hostManager = hostManager;
	}

	private void openSocket() {
		try {
			serverSocket = new ServerSocket(HostManager.PORT);
		} catch (IOException e) {
			Log.e(TAG, "Socket opening error", e);
		}
		
		try {
			serverSocket.setSoTimeout(10000);
		} catch (SocketException e) {
			Log.e(TAG, "Cannot set timeout", e);
		}
	}

	private void getData() {

		Socket socket = null;
		try {
			socket = serverSocket.accept();
		} catch (IOException e) {
			//Log.e(TAG, "Socket accepting error", e);
			return;
		}

		DataInputStream in = null;

		try {
			in = new DataInputStream(socket.getInputStream());
		} catch (IOException e) {
			Log.e(TAG, "Input getting error", e);
		}

		try {
			hostManager.addJSON(in.readUTF());
		} catch (IOException e) {
			Log.e(TAG, "Input reading error", e);
		}

		try {
			in.close();
		} catch (IOException e) {
			Log.e(TAG, "Can't close input stream", e);
		}

	}

	private void closeSocket() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			Log.e(TAG, "Socket closing error", e);
		}
	}

}