package net.garyzhu.locannouncer;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class DisplayActivity extends Activity 
implements OnTouchListener {
	float origBrightness = 0.0f;
    boolean allDone = true;
	/**
	 * start the timer to dim the screen light
	 */
	CountDownTimer dimTimer = new CountDownTimer(6100, 2000) {

		 public void onTick(long millisUntilFinished) {
		     Log.d("onDisplay", "dim count down " + millisUntilFinished/1000);
		 }

		 public void onFinish() {
		     dimScreen();
		 }  
	};
	
	private void dimScreen() {
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		float currentBP = lp.screenBrightness;
		
		if (currentBP > 0.08f) {
			lp.screenBrightness = currentBP / 1.5f;
			Log.d("onDisplay", "Dim screen from " + currentBP + " to " + lp.screenBrightness);
			getWindow().setAttributes(lp);
			synchronized(dimTimer) {
				dimTimer.cancel();
				if (allDone == false)  dimTimer.start();
			}			
		} else {
			lp.screenBrightness = 0.01f;
			Log.d("onDisplay", "Dim screen to 0.01");
			getWindow().setAttributes(lp);
		}
	}
	
	private void lightupScreen() {
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		if (lp.screenBrightness > 0.2f) {
			//Log.d("onDisplay", "already light-up, do nothing");
			return;
		}
		
		// make it a little bit brighter than the original brightness
		lp.screenBrightness = origBrightness + (1f - origBrightness)/2.0f;
		
		getWindow().setAttributes(lp);
		// cancel previous dim timer and start a new one
		synchronized(dimTimer) {
			dimTimer.cancel();
			if (allDone == false)
			    dimTimer.start();
		}
	}
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    	
        setContentView(R.layout.activity_display);
        // allow hardware volume button to work here
    	this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
    	
    	// retrieve and save original brightness
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		
		origBrightness = lp.screenBrightness;
		if (origBrightness == -1) {		
			try {
				// if it is auto, try to get the system setting, which is in the scale of 255
				//  and assign it to the windows attribute for future calculation.
			    origBrightness = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS)/255f;
			} catch (SettingNotFoundException e) {
			    Log.d("onDisplay", "Failed to get current brightness " + e.getMessage());
			}
			lp.screenBrightness = origBrightness;
			getWindow().setAttributes(lp);
		}
		
		Log.d("onDisplay", "Original brightness is " + origBrightness);
		
    	//getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
    }
    
    protected void onDestroy() {
    	synchronized(dimTimer) {
    		allDone = true;
    		dimTimer.cancel();
    	}
    	super.onDestroy();
    	getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        	Log.d("onDisplay", "in onNewIntent -- update with stop intent");       	
    	}
    }
    
	@Override
	public void onResume() {
		super.onResume();
		
    	Intent i = this.getIntent();
    	if (isStopIntent(i)) {
        	pauseLocAnnouncer(this.findViewById(R.layout.activity_display));
        	this.finish();
        	return;
    	}
    	
    	TripHandle th = i.getParcelableExtra(DataManager.TH);
    	if (th != null) {
	    	// start a service
	    	Intent intent = new Intent(this, LocAnnouncer.class);
	    	intent.putExtra(DataManager.TH, th);
	    	Log.d("onDisplay", "TripHandle being passed in: " + th.toString());
	    	startService(intent);
    	}
    	fillInMapView(th);
    	allDone = false;
    	dimTimer.start();
	};
	
	private void fillInMapView(TripHandle th) {
		WebView mWebView = (WebView) findViewById(R.id.mapview);
		mWebView.clearCache(true);
		WebSettings mWebSettings = mWebView.getSettings();
		mWebSettings.setJavaScriptEnabled(true);
		mWebSettings.setSaveFormData(false);
		mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		mWebView.setWebViewClient(new WebViewClient() {
			@Override
		    public void onReceivedError(WebView view,int errorCode,String description,String failingUrl) {
				Log.e("onDisplay", "received error " + errorCode + "-" + description + " " + failingUrl);
				try {view.stopLoading();} catch(Exception e){
					Log.e("onDisplay", "stop loading ", e);
				}
				view.reload();
		    }
			@Override
		    public void onPageFinished(WebView view, String url) {
		        Log.d("onDisplay", "Map page loaded");
		    }
		});
		mWebView.setWebChromeClient(new WebChromeClient() {	
			@Override
			public boolean onConsoleMessage(ConsoleMessage cm) {
				Log.d("onDisplay", cm.message() + " -- From line " + cm.lineNumber() + " of " + cm.sourceId());
				return true;
			}
		});
		
		mWebView.setOnTouchListener(this);
		String mapUrl = "http://www.garyzhu.net/m.html";
		mWebView.loadUrl(mapUrl);
	}
	
    public void stopLocAnnouncer(View v) {
    	Log.d("onDisplay", "complete stop");
    	Intent intent = new Intent(LocAnnouncer.USER_STOP_SERVICE_REQUEST);
    	intent.putExtra(LocAnnouncer.USER_STOP_INTENT, getString(R.string.kill_intent_action));
    	this.sendBroadcast(intent);
        this.finish();
    }
    
    public void pauseLocAnnouncer(View v) {
    	Log.d("onDisplay", "pause trip");
    	Intent intent = new Intent(LocAnnouncer.USER_STOP_SERVICE_REQUEST);
    	this.sendBroadcast(intent);
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
    		Log.d("onDisplay", "received stop intent");
    	}
    	return retV;
    }


	@Override
	public boolean onTouchEvent(MotionEvent event) {
		//the current event does nothing, return false, let the webview handle all touch event
	    return false;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		// on touch, light up screen
		lightupScreen();
		return onTouchEvent(event);
	}
}
