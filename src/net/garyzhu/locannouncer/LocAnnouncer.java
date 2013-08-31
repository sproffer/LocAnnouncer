package net.garyzhu.locannouncer;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
	// key values to save last position and time
	private final static String LAST_PROV = "last_provider";
	private final static String LAST_LAT = "last_lat";
	private final static String LAST_LONG = "last_long";
	private final static String LAST_ALT = "last_lalt";
	private final static String LAST_TIME = "last_time";
	private final static String LAST_ACCURACY = "last_accuracy";
	private final long pollTimeMillis = 122000;   // make 2 minutes intervals

	private final static String NEXT_LANG = "next_lang";
	
	private SharedPreferences sharedPref = null;
	boolean noLocation = true;
	private double latitude = 0.00;
	private double longitude = 0.00; 
	private double altitude = 0.00;
	private float  accuracy = 0.0f;
	private String provider = "";
	private long   sampleTime = 0;
	
	private int timesNoGps = 0;
	private int s = 0;
	private LocationListener myLocListener = null;

	private List<String> locProviders = null;
	private TextToSpeech  tts = null;
	private String nextLang = "";
	private boolean ttsReady = false;
	private Location lastLoc = null;
	private AudioManager audioManager = null;
	private OnAudioFocusChangeListener  afChangeListener = null;
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
	private class utteranceListener extends UtteranceProgressListener {
		@Override
        public void onDone(String utteranceId)
        {			
			tts.setSpeechRate(1.0f);
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
	        Log.d("onLocChange", location.getProvider() + ":   "+ location.getLatitude() + ", " + location.getLongitude() + ";   altitude=" + location.getAltitude() +
	        		";   precision=" + location.getAccuracy());
	        
	        if (location.getProvider().equalsIgnoreCase(LocationManager.GPS_PROVIDER))
	        {
	        	// have GPS signal, reset the counter
	        	timesNoGps = 0;
	        } else {
	        	timesNoGps++;
	        }
	        /**
	         * check to see whether to update the location:
	         * 1.  if same provider, update
	         * 2.  if different provider, but better accuracy, update
	         * 3.  if different provider, worse accuracy, at least 3 cycles without GPS, then start using less accurate provider
	         */
	        boolean doUpdate = false;
	        if (provider.equalsIgnoreCase(location.getProvider())) {
	        	doUpdate = true;
	        } else if (accuracy > location.getAccuracy()) {
	        	doUpdate = true;
	        } else if (timesNoGps >=2 ) {
        		Log.d("Main", "3 cycles without GPS, use less accurate location");
        		doUpdate = true;
        		if (timesNoGps == 2) {
        			// announce no gps
        			speakOut(TextToSpeech.QUEUE_ADD, "2 cycles without GPS, use less accurate " + location.getProvider() +".");
        		}
	        } else {
	        	Log.d("onLocChange", "Ignore " + location.getProvider() +", waiting for GPS");
	        }
	        
	        if (doUpdate) {
	        	lastLoc = getLocationInstance(provider, latitude, longitude, altitude, accuracy, sampleTime);
	        	
	        	latitude = location.getLatitude();
	        	longitude = location.getLongitude();
	        	if (location.getAltitude() > 1.0) {
	        		altitude = location.getAltitude();
	        	}
	        	accuracy = location.getAccuracy();
	        	provider = location.getProvider();
	        	sampleTime = location.getTime();
	        
	        	updateLocDisplay(lastLoc, doUpdate);
	        }
	    }
	    
	    public void onStatusChanged(String provider, int status, Bundle extras) {}

	    public void onProviderEnabled(String provider) {}

	    public void onProviderDisabled(String provider) {
	    	latitude = 0.00;
	    	longitude = 0.00;
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
		provider = sharedPref.getString(LAST_PROV, "");
		nextLang = sharedPref.getString(NEXT_LANG, "");
		latitude = Double.valueOf(sharedPref.getString(LAST_LAT, "0.0"))
				.doubleValue();
		longitude = Double.valueOf(sharedPref.getString(LAST_LONG, "0.0"))
				.doubleValue();
		altitude = Double.valueOf(sharedPref.getString(LAST_ALT, "0.0"))
				.doubleValue();
		sampleTime = sharedPref.getLong(LAST_TIME, 0);
		accuracy = sharedPref.getFloat(LAST_ACCURACY, 0.0f);
		accuracy += 50; // assume less accuracy with saved data

		tts = new TextToSpeech(this, this);
		
		setupLocationCallbacks();
		Log.d("onService", "done onCreate, pid=" + android.os.Process.myPid());
	}

	  @Override
	  public int onStartCommand(Intent intent, int flags, int startId) {
		  Log.d("onService", "service onStartCommand");	
      
		  // intent to re-launch Activity UI
		  Intent notificationIntent = new Intent(this, MainActivity.class);
		  notificationIntent.setAction(getString(R.string.relaunch_intent_action));
		  notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		  PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
			
		  // intent to launch KillActivity to kill this service,
		  // the comparison of intent does not look for extras, so I have to set 
		  // Action differently and allow MainActivity to see whether to kill.
		  Intent killIntent = new Intent(this, MainActivity.class);
		  killIntent.setAction(getString(R.string.kill_intent_action));
		  killIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		  PendingIntent pendingKillIntent = PendingIntent.getActivity(this, 0, killIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		  
		  NotificationCompat.Builder nb = new NotificationCompat.Builder(this);
		  nb.setContentTitle(getString(R.string.app_name))
		    .setContentText(getString(R.string.app_desc))
		    .setSmallIcon(R.drawable.ic_launcher)
		    .setTicker(getString(R.string.foreground_service_started_ticker))
		    .setContentIntent(pendingIntent)
		    .addAction(R.drawable.ic_cancel, getString(R.string.title_activity_kill), pendingKillIntent);
		  
	      Notification notification = nb.build();

		  int  ONGOING_NOTIFICATION_ID = 100;  // has to be unique within this application
		  startForeground(ONGOING_NOTIFICATION_ID, notification);

	      // If we get killed, after returning from here, restart
	      return START_STICKY;
	  }

	  @Override
	  public IBinder onBind(Intent intent) {
	      // We don't provide binding, so return null
	      return null;
	  }
	  
	  @Override
	  public void onDestroy() {
		  Log.d("onService", "service being distroyed; pid=" + android.os.Process.myPid());
		  threadWait(200);
	    	stopLocationListeners();
	    	tts.stop(); // stop the current speaking before say goodbye

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
	    	editor.putString(LAST_LAT, Double.toString(latitude));
	    	editor.putString(LAST_LONG, Double.toString(longitude));
	    	editor.putString(LAST_ALT, Double.toString(altitude));
	    	editor.putLong(LAST_TIME, sampleTime);
	    	editor.putString(LAST_PROV, provider);
	    	editor.putFloat(LAST_ACCURACY, accuracy);
	    	editor.commit();

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
	    
	    private void updateLocDisplay(Location l, boolean alwaysUpdate) {

	 	    if (alwaysUpdate) {
	 	    	NumberFormat nf =  NumberFormat.getInstance();
	 	    	nf.setMaximumFractionDigits(4);

	 	    	String dispTxt = (alwaysUpdate?"  New ":"  Last ") + provider + ",\n  latitude       " + nf.format(latitude) 
	 	    			+ ",\n  longitude " + nf.format(longitude) +",\n  altitude   " + (long)(altitude)
	 	    			+" meters,\n  accuracy   " + (int)(accuracy) +" meters.\n\n";

	 	    	String speakTxt = "<?xml version=\"1.0\"?> " +
	                              "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" " +
	                              "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
	                              "xsi:schemaLocation=\"http://www.w3.org/2001/10/synthesis http://www.w3.org/TR/speech-synthesis/synthesis.xsd\" " +
	                              "xml:lang=\"en-US\"><prosody rate=\"1.2\">" + dispTxt + "</prosody></speak>";
	 	    	speakOut(TextToSpeech.QUEUE_ADD, speakTxt);
	 	    	
	 	    	// check distance the ascend/descend
	 	    	if (l != null && Math.abs(l.getAltitude()) > 0.01 && Math.abs(l.getLongitude()) > 0.01) {
	 	    		Location newL = getLocationInstance(provider, latitude, longitude, altitude, accuracy, sampleTime);
	 	    		float dist = newL.distanceTo(l);
	 	    		double h = altitude - l.getAltitude();
	 	    		if ((dist > 20) || (h > 20) || (h < -20)) {
	 	    			String distMsg = "Traveled distance of " + (int)(dist) + " meters, ";
	 	    			if (h > 0.5) {
	 	    				distMsg = distMsg + " ascended up " + (int)(h) + " meters, ";
	 	    			} else if ( h < -0.5) {
	 	    				distMsg = distMsg + " descended down " + (int)((-1)*h) + " meters, ";
	 	    			}
	 	    			long pastMin = ((sampleTime - l.getTime())/1000)/60;
	 	    			distMsg = distMsg + " in the past " + pastMin + " minutes. \n";
	 	    			tts.setSpeechRate(1.4f);
	 	    			tts.setPitch(1.5f);
	 	    			speakOut(TextToSpeech.QUEUE_ADD, distMsg);
	 	    		}
	 	    	}
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
	    
	  private Location getLocationInstance(String prov, double latitude, double longitude, double altitude, float accuracy, long lastTime) {
	    	Location retLoc = new Location(prov);
	    	retLoc.setLatitude(latitude);
	    	retLoc.setLongitude(longitude);
	    	retLoc.setAltitude(altitude);
	    	retLoc.setAccuracy(accuracy);
	    	retLoc.setTime(lastTime);
	    	
	    	return retLoc;
	  }	  
	  private void threadWait(int millis) {
	    	try {
				// allow time to abandon audio
				Thread.sleep(millis);
			}catch(InterruptedException x) {}
	  }
}
