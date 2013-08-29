package net.garyzhu.locannouncer;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;

public class KillActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.activity_kill);
		Log.d("killActivity", "launched KillActivity to kill LocAccouncer pid=" + android.os.Process.myPid());
		Intent intent = new Intent(this, LocAnnouncer.class);
    	stopService(intent);
		
    	this.finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.kill, menu);
		return true;
	}

}
