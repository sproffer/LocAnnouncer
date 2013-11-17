package net.garyzhu.locannouncer;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class DisplayActivity extends Activity {
	
	private void dimScreen() {
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		float currentBP = lp.screenBrightness;
		Log.d("onDisplay", "Current brightness: " + currentBP);
		if (currentBP >= 0.3f) {			
			lp.screenBrightness = 0.05f;
			Log.d("onDisplay", "Dim screen to 0.05");
			getWindow().setAttributes(lp);
		} else if (currentBP < 0) {
			lp.screenBrightness = 0.01f;
			Log.d("onDisplay", "Dim screen to 0.01");
			getWindow().setAttributes(lp);
		}
	}
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    	
        setContentView(R.layout.activity_display);
        // allow hardware volume button to work here
    	this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
    	
    	//getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    protected void onDestroy() {
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
    	
    	dimScreen();
	}
	
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
		});
		mWebView.setWebChromeClient(new WebChromeClient() {	
			@Override
			public boolean onConsoleMessage(ConsoleMessage cm) {
				Log.d("onDisplay", cm.message() + " -- From line " + cm.lineNumber() + " of " + cm.sourceId());
				return true;
			}
		});
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
}
