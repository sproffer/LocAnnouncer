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
    
    /**
     * This is called when current activity is running, and
     * an intent is invoked with Intent.FLAG_ACTIVITY_SINGLE_TOP
     * 
     */
    @Override
    public void onNewIntent(Intent i) {
    	if (isStopIntent(i)) {
    		// this is a stop intent for an existing activity, 
    		// update this activity's intent with this stop intent so that onResume will detect it
    		// and stop the activity. Otherwise, this activity's intent would still be original one.
    		// note that no matter how to 'stop' the service, onResume will be called, so the service 
    		// will get re-started.  So let the onResume to do the job, just pass in stop intent.
    		setIntent(i);  
        	Log.d("mainActivity", "in onNewIntent -- update with stop intent");       	
    	}
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
    	
    	Intent i = this.getIntent();
    	if (isStopIntent(i)) {
        	stopAnnouncer(this.findViewById(R.layout.activity_main));
        	this.finish();
        	return;
    	}
    	Log.d("mainActivity", "in onResumt .. starting service,  pid=" +android.os.Process.myPid());
    	// start a service
    	Intent intent = new Intent(this, LocAnnouncer.class);
    	startService(intent);
    	Log.d("mainActivity", "service started");
    }
    
    @Override
    public void onPause() {
    	super.onPause();  // for activity, always call super class method first
    	Log.d("mainActivity", "Pause");
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    	Log.d("mainActivity", "done onStop");
    	
    	// when activity is running, UP button, would call onStop, while HOME button would call destroy
    	// the problem is that MainActivity would be re-started again when you
    	// kill it from the notification menu.
    	
    	// this is HOME button or UP button, in any case, we should stop MainActivity
    	this.finish(); 
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	Log.d("mainActivity", "onDestroy");
        // do not call stopService, onDestroy will be called when a screen goes dark,
    	// in order to make UP button stop the service, i will have to find another way.
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    public void stopAnnouncer(View v) {
        stopService(new Intent(this, LocAnnouncer.class));
        this.finish();
    }
    
    /**
     * test to see whether this intent has Extra parameter for isSopt=true
     * @param i  the Intent to be examined
     * @return   true if this intent is for stop
     */
    private boolean isStopIntent(Intent i) {
    	boolean retV =  false;

    	if (i.getAction() != null && i.getAction().equals(getString(R.string.kill_intent_action))) {
    		retV = true;
    		Log.d("mainActivity", "received stop intent");
    	}
    	return retV;
    }
}
