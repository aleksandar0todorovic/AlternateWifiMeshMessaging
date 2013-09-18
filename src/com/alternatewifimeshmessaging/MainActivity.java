package com.alternatewifimeshmessaging;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

public class MainActivity extends Activity {

	Button messageSendButton;
	EditText messageBody;
	ListView messageList;
	TelephonyManager telephonyManager;

	HostManager hostManager;
	Thread thread;
	private static Handler handler;

	@SuppressLint("HandlerLeak")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
				
		handler = new Handler() { 
			@Override
		  public void handleMessage(Message msg) {
			  hostManager.updateArrayAdapter();
		     }
		 };
		telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);

		hostManager = new HostManager(this, handler, telephonyManager);

		thread = new Thread(hostManager);
		thread.setDaemon(true);
		thread.start();

		messageSendButton = (Button) findViewById(R.id.messageSendButton);
		messageBody = (EditText) findViewById(R.id.messageBody);
		messageList = (ListView) findViewById(R.id.messageList);

		messageList.setAdapter(hostManager.getArrayAdapter());

		messageSendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				hostManager.addMessage(messageBody.getText().toString());

				messageBody.setText(null);
			}
		});
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	
	


}
