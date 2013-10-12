package net.garyzhu.locannouncer;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
		
	DataManager dm = null;
	List<TripHandle> thList = null;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    	
        setContentView(R.layout.activity_main);
        Log.d("mainActivity", "done onCreate pid=" + android.os.Process.myPid());
    }
    
    @Override
    public void onStart() {
    	super.onStart();
    	// allow hardware volume button to work here
    	this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
    	dm = new DataManager(this);
    	thList = dm.getTripHandles(false);
    	Log.d("mainActivity", "done onStart; list size=" + thList.size());
    }

    @Override
    public void onResume() {    	
    	super.onResume();   // for activity, always call super class method first
    	
    	List<Map<String, String>> listTrips = getListData(thList, "name");
    	
        // We get the ListView component from the layout
    	ListView lv = (ListView) findViewById(R.id.listView);
	    // This is a simple adapter that accepts as parameter
	    // Context
	    // Data list
	    // The row layout that is used during the row creation
	    // The keys used to retrieve the data
	    // The View id used to show the data. The key number and the view id must match
	    SimpleAdapter simpleAdpt = new SimpleAdapter(this, listTrips, android.R.layout.simple_list_item_1, new String[] {"name"}, new int[] {android.R.id.text1});
	 
	    lv.setAdapter(simpleAdpt);
	    
	    // React to user clicks on item
	    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
	         public void onItemClick(AdapterView<?> parentAdapter, View view, int position,  long id) {

	             // We know the View is a TextView so we can cast it
	             //TextView clickedView = (TextView) view;
	             TripHandle  th = thList.get(position);
	             if (th.completed) {
	            	 Toast.makeText(MainActivity.this, "The trip " + id + " ["+th.tripName+"] has completed!", Toast.LENGTH_LONG).show();
	             } else {
	            	 Toast.makeText(MainActivity.this, "Continue trip "+id+" ["+th.tripName+"]", Toast.LENGTH_SHORT).show();
	            	 continueTrip(th);
	             }
	         }
	    });
    }
    
    private void continueTrip(TripHandle th) {
    	Intent intent = new Intent(this, DisplayActivity.class);
    	intent.putExtra(DataManager.TH, th);
    	startActivity(intent);
    }
    
    @SuppressLint("SimpleDateFormat")
	private List<Map<String, String>> getListData(List<TripHandle> l, String title) {
    	SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy  HH:mm:ss");
    	SimpleDateFormat tf = new SimpleDateFormat("HH:mm:ss");
    	
    	List<Map<String, String>> retA = new ArrayList< Map<String, String> >();
    	// specify which file contains trip data, it could be an empty (or non-existent) file.
    	// later on, the Main panel would open all files, read the title and then, allow users
    	// to choose.
    	//
    	for (TripHandle th: l) {
    		Log.d("mainActivity", "trip handle "+ th.toString());
    			String line =  th.tripName + "\n" + (th.startTime==null? "" : (df.format(th.startTime) + " -- " + tf.format(th.stopTime)));
    		    line += "\n duration: " + DataManager.convertTimeString(th.stopTime.getTime() - th.startTime.getTime());
    		    if (th.completed) {
    		    	line += "\n  -- completed --";
    		    }
    			HashMap<String, String> oneEntry = new HashMap<String, String>();
    			oneEntry.put(title, line);
    			retA.add(oneEntry);
    	}
    	return retA;
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
    
    public void startNewRoute(View v) {
    	Log.d("mainActivity", "in onResumt .. starting service,  pid=" +android.os.Process.myPid());
   	
    	EditText et = (EditText)findViewById(R.id.new_trip_name);
    	String tripName = et.getText().toString();
    	TripHandle th = new TripHandle();
    	th.tripName = tripName;

    	Intent intent = new Intent(this, DisplayActivity.class);
    	intent.putExtra(DataManager.TH, th);
    	startActivity(intent);
    }
}
