package net.garyzhu.locannouncer;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class LocAnnouncer extends Service implements TextToSpeech.OnInitListener {
	
	private final long pollTimeMillis = 21000;   // poll location every 20 seconds
    private final int speakInterval = 6;
    private int polledTimes = 0;
        
	private final static String NEXT_LANG = "next_lang";
	
	DataManager dm = null;
	boolean fwReady = false;
	String tripName = "";
	private SharedPreferences sharedPref = null;
	boolean noLocation = true;
	private JSONObject lastLocationJSObj = null;
	
	private int timesNoGps = 0;
	private int s = 0;
	private LocationListener myLocListener = null;

	private List<String> locProviders = null;
	private TextToSpeech  tts = null;
	private String nextLang = "";
	private boolean ttsReady = false;
	private AudioManager audioManager = null;
	private OnAudioFocusChangeListener  afChangeListener = null;
	
	// receiving stop notification
	final public static String USER_STOP_SERVICE_REQUEST = "USER_STOP_SERVICE";
	final public static String USER_STOP_INTENT = "USER_STOP_INTENT";
	public class UserStopServiceReceiver extends BroadcastReceiver  
	{  
	    @Override  
	    public void onReceive(Context context, Intent intent)  
	    {  
	        //code that handles user specific way of stopping service 
	    	String stopCommand = intent.getStringExtra(USER_STOP_INTENT);
	    	Log.d("onServce", "stop command is " + stopCommand);

	    	if (stopCommand != null && stopCommand.equalsIgnoreCase(getString(R.string.kill_intent_action))) {
	    		dm.close(true);
	    	} else {
	    		dm.close(false);
	    	}
	    	stopSelf();
	    }  
	}
	private UserStopServiceReceiver stopReceiver = null;
			
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
	private class utteranceListener extends UtteranceProgressListener {
		@Override
        public void onDone(String utteranceId)
        {			
			tts.setSpeechRate(1.0f);
			tts.setPitch(1.0f);
			
			Log.d("onUtter", "Enter utterance onDone  " + utteranceId);
			Log.d("onUtter", " adandoning audio...");
			int res = audioManager.abandonAudioFocus(afChangeListener);
			if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
				// allow time for audio focus abandoning...
				Log.e("onUtter", "Failed to abandon audio");  
			}
			// wait is necessary, otherwise, there are overlappings, or stopped talking for all apps.
			threadWait(100);
    		Log.d("onUtter", "Exit utterance onDone " + utteranceId);
        }

        @Override
        public synchronized void onError(String utteranceId)
        {
        	Log.e("onUtter", "Failed");
    		audioManager.abandonAudioFocus(afChangeListener);
        }

        @Override
        public void onStart(String utteranceId)
        {
        	Log.d("onUtter", " onStart " + utteranceId);
        	int res = audioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, 
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        	if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
        		// throw this worker thread out, don't speak.
        		throw new RuntimeException();
        	} else {
        		res = audioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, 
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        		if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            		// throw this worker thread out, don't speak.
        			speakOut(TextToSpeech.QUEUE_FLUSH, "\n\nFailed to get focus with Duck!\n");
        		}
        	}
        }
	}
	
	private class MyLocationListener implements LocationListener {
		public MyLocationListener()  {
			super();
		};
		
	    public void onLocationChanged(Location location) {
		    try {
		        Log.d("onLocChange", location.getProvider() + ":   "+ location.getLatitude() + ", " + location.getLongitude() + ";   altitude=" + location.getAltitude() +
		        		";   precision=" + location.getAccuracy());
		        
		        if (location.getProvider().equalsIgnoreCase(LocationManager.GPS_PROVIDER))
		        {
		        	// have GPS signal, reset the counter
		        	timesNoGps = 0;
		        } else {
		        	timesNoGps++;
		        }
	
		        String lastProvider = "";
		        float lastAccuracy = 1000.0f;
		        if (lastLocationJSObj != null) {
		        	lastProvider = lastLocationJSObj.getString(DataManager.PROVIDER);
		        	lastAccuracy = (float)lastLocationJSObj.getDouble(DataManager.ACCURACY);
		        }
		        /**
		         * check to see whether to update the location:
		         * 1.  if same provider, update
		         * 2.  if different provider, but better accuracy, update
		         * 3.  if different provider, worse accuracy, at least 3 cycles without GPS, then start using less accurate provider
		         */
		        boolean doUpdate = false;
		        if (lastProvider.equalsIgnoreCase(location.getProvider())) {
		        	doUpdate = true;
		        } else if (lastAccuracy > location.getAccuracy()) {
		        	doUpdate = true;
		        } else if (timesNoGps >= 5 ) {
	        		Log.d("onLocChange", "5 cycles without GPS, use less accurate location");
	        		doUpdate = true;

	        		speakOut(TextToSpeech.QUEUE_ADD, "  " + timesNoGps + " cycles without GPS, use provider = " + location.getProvider() +".");
		        } else {
		        	Log.d("onLocChange", "Ignore " + location.getProvider() +", waiting for GPS");
		        }
		        
		        if (doUpdate) {
		        	int loopIn = 0;
		        	while (fwReady == false) {
		        		Log.w("onService", "Location data coming before file is opened, wait for a while");
		        		threadWait(500);
		        		if (loopIn++ > 20) {
		        			Log.e("onService", "gave up");
		        			return;
		        		}
		        	}
		        	lastLocationJSObj = dm.save(location);
		        	
		        	polledTimes++;
		        	if ((polledTimes % speakInterval) == 1 ) {
		        		speakLoc(lastLocationJSObj);
		        	}
		        	// announce current time every 15 minutes
		        	if ((polledTimes % (speakInterval * 5)) == 1) {
		        		java.util.Date t = new java.util.Date();
		        		SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");
		        		String tstr = sdf.format(t);
		        		tts.setSpeechRate(1.0f);
		        		tts.setPitch(1.0f);
		        		speakOut(TextToSpeech.QUEUE_ADD, "\n\n Current time is " + tstr);
		        	}
		        }
		    }
	    	catch(Exception jex) {
	    		Log.e("onLocChange", "have JSON or IO exception", jex);
	    	}
	    }
	    
	    public void onStatusChanged(String provider, int status, Bundle extras) {}

	    public void onProviderEnabled(String provider) {}

	    public void onProviderDisabled(String provider) {
	    	noLocation = true;
	    }
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
	@Override
	public void onInit(int status) {
    	Log.d("onUtter", "TTS init called");
        if (tts != null && status == TextToSpeech.SUCCESS) {       	
        	int result = 0;
            if (tts.getLanguage() != null) {
            	Log.d("onUtter", "system default: " + tts.getLanguage().getDisplayLanguage() + " (" + tts.getLanguage().getDisplayCountry() + ")");
            } 
            
            if (nextLang.equals("British") && tts.isLanguageAvailable(Locale.UK) == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            	result = tts.setLanguage(Locale.UK);
            	Log.d("onUtter", "toggle to British");
            } else {
            	result = tts.setLanguage(Locale.US);
            	Log.d("onUtter", "toggle to American");
            }
            
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        		Log.e("onUtter", "This Language is not supported " + result);	    
        		tts = null;
            }
       } else {
            Log.e("onUtter", "Initilization Failed!");
            tts = null;
       }
       if (tts != null) {
           afChangeListener = new OnAudioFocusChangeListener() {
				public void onAudioFocusChange(int focusChange) {
					switch(focusChange) {
					case AudioManager.AUDIOFOCUS_GAIN:
					case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
					case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
						// do nothing
						Log.d("onUtter", "Gained Audio Focus");
						break;
					case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
					case AudioManager.AUDIOFOCUS_LOSS:
					case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
						tts.stop();
						audioManager.abandonAudioFocus(afChangeListener);
						Log.d("onUtter", "Lost Audio Focus, wait for a while");
						threadWait(1800);
						break;
					default:
						Log.e("onUtter", "Wrong value " + focusChange);
					}
				}
           };
    	   if (Build.VERSION.SDK_INT >= 15) {
    		   UtteranceProgressListener ul = new utteranceListener();
    		   tts.setOnUtteranceProgressListener(ul);
    		   Log.d("onUtter", "set up utterance listener");
    	   }
    	   ttsReady = true;
       }
    }

	@Override
	public void onCreate() {
		Log.d("onService", "starting onCreate");

		sharedPref = getSharedPreferences(getString(R.string.pref_file_name), Context.MODE_PRIVATE);
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		// Restore saved language
		nextLang = sharedPref.getString(NEXT_LANG, "");

		tts = new TextToSpeech(this, this);
		
		setupLocationCallbacks();
		Log.d("onService", "done onCreate, pid=" + android.os.Process.myPid());
	}

	  @Override
	  public int onStartCommand(Intent intent, int flags, int startId) {
		  Log.d("onService", "service onStartCommand");	
      
		  if (fwReady == false) {
			// Activity UI prompt users to select a trip file
			// and pass the file name in this intent.
			TripHandle th = (TripHandle) intent.getParcelableExtra(DataManager.TH);

			dm = new DataManager(this);
			fwReady = dm.openTripHandle(th);
		  }

		  // intent to re-launch Activity UI
		  Intent notificationIntent = new Intent(this, DisplayActivity.class);
		  notificationIntent.setAction(getString(R.string.relaunch_intent_action));
		  notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		  PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
			
		  // intent to launch Activity with kill intent,
		  // the comparison of intent does not look for extras, so I have to set 
		  // Action differently and allow DisplayActivity to see whether to kill.
		  Intent killIntent = new Intent(this, DisplayActivity.class);
		  killIntent.setAction(getString(R.string.kill_intent_action));
		  killIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		  PendingIntent pendingKillIntent = PendingIntent.getActivity(this, 0, killIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		  
		  NotificationCompat.Builder nb = new NotificationCompat.Builder(this);
		  nb.setContentTitle(getString(R.string.app_name))
		    .setContentText(getString(R.string.app_desc))
		    .setSmallIcon(R.drawable.ic_launcher)
		    .setTicker(getString(R.string.foreground_service_started_ticker))
		    .setContentIntent(pendingIntent)
		    .addAction(R.drawable.ic_cancel, getString(R.string.title_activity_pause), pendingKillIntent);
		  
	      Notification notification = nb.build();

		  int  ONGOING_NOTIFICATION_ID = 100;  // has to be unique within this application
		  startForeground(ONGOING_NOTIFICATION_ID, notification);

		  // ready to receive kill broadcast.
		  stopReceiver = new UserStopServiceReceiver();
		  registerReceiver(stopReceiver,  new IntentFilter(USER_STOP_SERVICE_REQUEST));
		  
	      // If we get killed, after returning from here, restart
	      return START_STICKY;
	  }

	  @Override
	  public IBinder onBind(Intent intent) {
	      // We don't provide binding, so return null
	      return null;
	  }
	  
	private void closeOut() {
		Log.d("onService",
				"service being closed out; pid=" + android.os.Process.myPid());
		// ready to receive kill broadcast.
		if (stopReceiver != null) {
			unregisterReceiver(stopReceiver);
			stopReceiver = null;
		}
		  
		stopLocationListeners();
		threadWait(200);
		tts.stop(); // stop the current speaking before say goodbye
		fwReady = false;
		speakOut(TextToSpeech.QUEUE_FLUSH, "Good bye!");
		// make sure last sentence is done and event is fired to abandon audio
		threadWait(1600);

		ttsReady = false;
		if (tts != null) {
			tts.stop();
			tts.shutdown();
			tts = null;
		}

		SharedPreferences.Editor editor = sharedPref.edit();
		nextLang = sharedPref.getString(NEXT_LANG, "American");
		if (nextLang.equals("British")) {
			nextLang = "American";
		} else {
			nextLang = "British";
		}
		editor.putString(NEXT_LANG, nextLang);
		editor.commit();
	}
	  @Override
	  public void onDestroy() {
		  closeOut();
	      dm.close(false);
	  }
	  
	  private void setupLocationCallbacks() {
	    	// Acquire a reference to the system Location Manager
	    	LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	    	if (locProviders == null || locProviders.size() < 1) {
	    		locProviders = new ArrayList<String>();
	    		locProviders.addAll(locationManager.getProviders(true));
	    	}

	    	// Define a listener that responds to location updates
	    	myLocListener = new MyLocationListener();
	    	
	    	// Register the listener with the Location Manager to receive location updates
	    	for (String providerStr: locProviders) {
	    		if (!providerStr.equalsIgnoreCase(LocationManager.PASSIVE_PROVIDER)) {
		    		locationManager.requestLocationUpdates(providerStr, pollTimeMillis, 0.0f, myLocListener);
		    		Log.d("onService", "for provider "+ providerStr);
	    		}
	    	}
	  }
	    
	  private void stopLocationListeners() {
	    	// Acquire a reference to the system Location Manager
	    	LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	    	
	    	locationManager.removeUpdates(myLocListener);

	    	Log.d("Listener", "down");
	  }
	    
	  /**
	   * This method will compose and speak/announce new locations
	   * @param newLocJS
	   */
	  private void speakLoc(JSONObject newLocJS) throws JSONException {
 	    	NumberFormat nf =  NumberFormat.getInstance();
 	    	NumberFormat nf2 =  NumberFormat.getInstance();
 	    	nf.setMaximumFractionDigits(1);
 	    	nf2.setMaximumFractionDigits(2);

 	    	String provider = newLocJS.getString(DataManager.PROVIDER);
 	    	double altitude = newLocJS.getDouble(DataManager.ALTITUDE);

 	    	long  triptime = newLocJS.getLong(DataManager.TRIPTIME);
 	    	long  tripdistance = newLocJS.getLong(DataManager.TRIPDISTANCE);
 	    	float mileDist = (float) (((double)tripdistance) * 0.000621371); 
 	    	double altFeet = altitude * 3.28084;
 	    	String dispTxt = "Location from " + provider + ",\n...  Current elevation  " + nf.format(altFeet) + " feet.\n";

 	    	String speakTxt = "<?xml version=\"1.0\"?> " +
                              "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" " +
                              "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                              "xsi:schemaLocation=\"http://www.w3.org/2001/10/synthesis http://www.w3.org/TR/speech-synthesis/synthesis.xsd\" " +
                              "xml:lang=\"en-US\"><prosody  >" + dispTxt + "</prosody></speak>";
 	    	speakOut(TextToSpeech.QUEUE_ADD, speakTxt);
 	    	
 	    	if (tripdistance > -1) {
 	    		
 	    		String timeTxt = DataManager.convertTimeString(triptime);
 	    		
 	    		String speakTxt2 = "Trip time: " + timeTxt + ",... distance: " + nf2.format(mileDist) + " miles";
 	    		
    			tts.setSpeechRate(1.8f);
    			tts.setPitch(0.9f);
    			speakOut(TextToSpeech.QUEUE_ADD, speakTxt2);
 	    	}
	  }
	  private void speakOut(int queueMode, String speakTxt) {
	    	if (tts != null && ttsReady == true) {
	    		// acquire audio focus 
	    		// NOTE: this is a sanity acquire, there are cases that this method is called 
	    		//       before previous sentence is finished, so the app still has the focus,
	    		//       this method would pass through, and QUEUED the speech, and when
	    		//       the previous speech is done, it abandons focus, the music will be on.
	    		//  SO, it is important to acquire focus again in onStart(), so that this 
	            //  sentence will have the focus.
		        int res = audioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, 
	                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
		        if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
		        	// set utterance ID, in order to make utterance progress listener work
		    		HashMap<String, String> hm = new HashMap<String, String>();
		    		s++;
		    		hm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "Utterance " + s);
		    		tts.speak(speakTxt, queueMode, hm);
		        } else {
		        	Log.e("onUtter", "Failed to get audio!");
		        }
	    	}
	  }
	  
	  private void threadWait(int millis) {
	    	try {
				// allow time to abandon audio
				Thread.sleep(millis);
			}catch(InterruptedException x) {}
	  }
}
