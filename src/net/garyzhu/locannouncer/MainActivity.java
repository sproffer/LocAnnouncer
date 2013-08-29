package net.garyzhu.locannouncer;


import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;

public class MainActivity extends Activity {
		
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("mainActivity", "done onCreate pid=" + android.os.Process.myPid());
    }
    
    public void stopAnnouncer(View v) {
        stopService(new Intent(this, LocAnnouncer.class));
        this.finish();
    }
    
    @Override
    public void onStart() {
    	super.onStart();
    	// allow hardware volume button to work here
    	this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

    	Log.d("mainActivity", "done onStart");
    }
        
    @Override
    public void onResume() {    	
    	super.onResume();   // for activity, always call super class method first
    	
    	Log.d("mainActivity", "starting service,  pid=" +android.os.Process.myPid());
    	// start a service
    	Intent intent = new Intent(this, LocAnnouncer.class);
    	startService(intent);
    	Log.d("mainActivity", "service started");
    }
    
    @Override
    public void onPause() {
    	super.onPause();  // for activity, always call super class method first
    	Log.d("mainActivity", "Pause to stop");
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    	Log.d("mainActivity", "done onStop");
    	
    	// when activity is running, UP button, would call onStop, while HOME button would call destroy
    	// the problem is that MainActivity would be re-started again when you
    	// kill it from the notification menu.
    	
    	// this is HOME button or UP button, in any case, we should stop MainActivity
    	this.finish(); // destroy here, this is 
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	Log.d("mainActivity", "onDestroy");
    	// this is either destroy or UP button, which should stop the app entirely
    	// just like Google Map, UP button will stop the map entirely; 
    	// while HOME button will put Map to background 
    	// 
    	// destroy is called on UP button
    	Intent intent = new Intent(this, LocAnnouncer.class);
    	stopService(intent);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

}
