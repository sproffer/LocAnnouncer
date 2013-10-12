package net.garyzhu.locannouncer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONObject;

import android.content.Context;
import android.location.Location;
import android.os.Environment;
import android.util.Log;

public class DataManager {
	// a list of JSON keys
	public final static String TRIPNAME = "tripname";
	public final static String TRIPDETAIL = "tripdetail";
	/**
	 * string of gps or network
	 */
	public final static String PROVIDER = "provider";
	/**
	 * type of double
	 */
	public final static String LATITUDE = "latitude";
	/**
	 * type of double
	 */
	public final static String LONGITUDE = "longitude";
	/**
	 * type of double
	 */
	public final static String ALTITUDE = "altitude";
	/**
	 * type of long
	 */
	public final static String FIXTIME = "fixtime";
	/**
	 * type of float
	 */
	public final static String ACCURACY = "accuracy";
	/**
	 * type of long
	 */
	public final static String TRIPTIME = "triptime";
	/**
	 * type of long
	 */
	public final static String TRIPDISTANCE = "tripdistance";
	/**
	 * trip handle intent.putExtra
	 */
	public final static String TH = "tripHandle";
	
	private final static String FILEPREFIX = "trip.";
	
	private Context  ctx =  null;
	private FileWriter fw = null;
	private boolean  fwReady = false;
	private File  dir = null;
	
	private JSONObject firstLocationJSObj = null;
	private JSONObject lastLocationJSObj = null;
	
	private Locale myLocale = Locale.getDefault();
	
	private class MyFilenameFilter implements FilenameFilter {

		MyFilenameFilter() {
			// do nothing constructor
		}
		@Override
		public boolean accept(File arg0, String arg1) {
			if (arg1 != null && arg1.startsWith(FILEPREFIX)) {
				return true;
			}
			return false;
		}		
	}
	
	/**
	 * get a list of trip handles, 
	 * @return
	 */
	public List<TripHandle> getTripHandles(boolean includeComplete) {
		List<TripHandle> l = new ArrayList<TripHandle>();
		File[] files = dir.listFiles(new MyFilenameFilter());
		if (files != null && files.length > 0) {
			for (int i = 0; i < files.length; i++) {
				File afile = files[i];
				try {
					StringBuffer tripNameBuf = new StringBuffer();
					List<JSONObject> jsonL = readFirstLast(afile, tripNameBuf);
					TripHandle t = new TripHandle();
					t.fileName = afile.getName();
					t.tripName = tripNameBuf.toString();
					if (jsonL.size() > 1) {
						// assign values if not empty trip
						t.startTime = new java.sql.Timestamp(jsonL.get(0)
								.getLong(FIXTIME));
						t.stopTime = new java.sql.Timestamp(jsonL.get(1)
								.getLong(FIXTIME));
						t.completed = (jsonL.size() == 3);
					}
					if (includeComplete || t.completed==false) {
						l.add(t);
						Log.d("DataManager", "got a trip handle " + t.toString());
					}
				}
				catch(Exception x) {
					Log.e("DataManager", "Failed to get trip handle " + afile.getAbsolutePath(), x);
					afile.delete();
				}
			}
		}
		return l;
	}
	
	/**
	 * Open a file based on TripHandle, and read and assign tripName, firstLocationJSObj, lastLocationJSObj
	 * and then, open as file writer, assign fw and fwReady=true, for subsequent append
	 * if the TripHandle only contains tripName, create a new file, and write the tripname and make it ready 
	 * for write.
	 * 
	 * @return  true if successful
	 */
	public boolean openTripHandle(TripHandle th) {
		if (th.tripName == null || th.tripName.length() == 0) {
			Log.d("DataManager", "not a valid TripHandle");
			return false;
		}
		
		if (th.fileName == null && th.startTime == null) {
			// a brand new TripHandle, create a new file name
			long t = System.currentTimeMillis();
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd.HH-mm-ss", myLocale);
			th.fileName = FILEPREFIX + df.format(new java.sql.Timestamp(t));
		}

		if (fwReady && fw != null) {
			try {
				fwReady = false;
				fw.flush();
				fw.close();
				fw = null;
			} catch (IOException ie) {
				Log.e("DataManager", "Failed to close previous file writer", ie);
			}
		}
		
		try {
			  File tripFile = new File(dir, th.fileName);
			  if (tripFile.exists() == false) {
				  // create a  file, put in initial section
				  fw = new FileWriter(tripFile);
				  fw.append("{\"" + TRIPNAME + "\": \"" + th.tripName + "\", \"" + TRIPDETAIL + "\":[");
				  fw.flush();
				  // keep file open and we are ready
				  fwReady = true;
			  } else {
				  // read existing data
				  try {
					  StringBuffer tNameBuf = new StringBuffer();
						List<JSONObject> l = readFirstLast(tripFile, tNameBuf);
						if (l.size() == 3) {
							Log.e("DataManager", "the file " + tripFile.getName() + " is completed trip");
							throw new IOException("Cannot append to a completed trip.");
						} else if (l.size() == 2) {
							firstLocationJSObj = l.get(0);
							lastLocationJSObj = l.get(1);
						}
						
						// re-open file for write
						fw = new FileWriter(tripFile, true);
						fwReady = true;
				  } catch (Exception je) {
					  // mal-formatted file, recreate a file

					  Log.e("DataManager", "Mal-Formed file, recreate the file. ", je);
					  fw = new FileWriter(tripFile);
					  fw.append("{\"" + TRIPNAME + "\": \"" + th.tripName +"\", \"" + TRIPDETAIL + "\":[");
					  fw.flush();
					  fwReady = true;
				  }
			  }
		} catch (Exception e) {
			Log.e("DataManager", "Failed to read from file", e);
		}
		
		return fwReady;
	}
	/**
	 * Read the file, and load the first and last location data, the method returns an ordered 
	 * list of JSONObject, the first being first location data, and second being last location data;
	 * if the trip has only one location, then, the first and second object returned would be the same. 
	 * If the trip is completed, the returned List size would be 3, with the third element is the same
	 * as last location data.
	 * If the trip is empty, return 0 List.
	 *  
	 * @param tripFile
	 * @param tripNameBuf a buffer used to append trip name, 
	 * the invoker should allocate the StringBuffer with empty contents
	 * @return      returns an ordered list of JSONObject, it would be 2 or 3 elements in the list.
	 * @throws Exception
	 */
	private List<JSONObject> readFirstLast(File tripFile, StringBuffer tripNameBuf) throws Exception {
		List<JSONObject> retL = new ArrayList<JSONObject> (4);
		boolean completed = false;
		String firstLine = "";
		String lastLine = "";
		String tLine = null;
		BufferedReader bin = new BufferedReader(new FileReader(tripFile));
		// get the trip name
		tLine = bin.readLine();  // skip the trip name part
		tLine = tLine + "]}";
		JSONObject headJSObj = new JSONObject(tLine);
		tripNameBuf.append(headJSObj.getString(TRIPNAME));
		
		// read the second line and last line
		while ((tLine = bin.readLine()) != null) {
			if (tLine.trim().length() < 5)  continue;
			if (firstLine.length() < 10) {
				// process and assign first line
				firstLine = tLine;
				if (firstLine.endsWith("]}")) {
					int objPos = firstLine.lastIndexOf("]}");
					firstLine = firstLine.substring(0, objPos);
				}
				retL.add(new JSONObject(firstLine));
			}
			lastLine = tLine;
		}
		//  process last line
		if (lastLine.trim().startsWith(",")) {
			lastLine = lastLine.trim().substring(1);
		}
		if (lastLine.endsWith("]}")) {
			int pos = lastLine.lastIndexOf("]}");
			lastLine = lastLine.substring(0, pos);
			completed = true;
		}
		if (lastLine.length() > 10) {
			retL.add(new JSONObject(lastLine));
			if (completed) {
				retL.add(new JSONObject(lastLine));
			}
		}
		bin.close();
		return retL;
	}
	
	public DataManager(Context x) {
		ctx = x;
		fw = null;
		fwReady = false;
		dir = ctx.getFilesDir();
		if (isExternalStorageWritable()) {
			dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
		}
		firstLocationJSObj = null;
		lastLocationJSObj = null;
	}
	
	/**
	 * save new location data into storage after calculated total trip time and distance
	 * @param newLoc
	 * @return   JSONObject for last location with total trip time and distance calculated; 
	 * or null if something is wrong
	 */
	public JSONObject save(Location newLoc) {
		if (fwReady) {
			try {
				JSONObject newLocJSObj = getJSONObject(newLoc);
				if (firstLocationJSObj == null) {
					firstLocationJSObj = newLocJSObj;
				} else {
					// calculate trip time and distance
					if (lastLocationJSObj != null) {
						long totalTime = lastLocationJSObj.getLong(TRIPTIME) + 
								newLocJSObj.getLong(FIXTIME) - lastLocationJSObj.getLong(FIXTIME);
						long totalDistance = ((long) getLastLocationInstance().distanceTo(newLoc)) + lastLocationJSObj.getLong(TRIPDISTANCE);
						
						newLocJSObj.put(TRIPTIME, totalTime);
						newLocJSObj.put(TRIPDISTANCE, totalDistance);
					}
					fw.append(",");
				}
				lastLocationJSObj = newLocJSObj;
				fw.append("\n" + newLocJSObj.toString() );
				fw.flush();
			} catch (Exception x) {
				Log.e("DataManager", "Failed to append location data", x);
				return null;
			}
		}
		return lastLocationJSObj;
	}

	/**
	 *  Checks if external storage is available for read and write 
	 */
	private boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		return false;
	}

	/**
	 * convert a JSONObject to Location object
	 * 
	 * @param locJSObj
	 * @return
	 */
	public Location getLocationInstance(JSONObject locJSObj) {
		Location retLoc = null;
		try {
			retLoc = new Location(locJSObj.getString(PROVIDER));
			retLoc.setLatitude(locJSObj.getDouble(LATITUDE));
			retLoc.setLongitude(locJSObj.getDouble(LONGITUDE));
			retLoc.setAltitude(locJSObj.getDouble(ALTITUDE));
			retLoc.setAccuracy((float) locJSObj.getDouble(ACCURACY));
			retLoc.setTime(locJSObj.getLong(FIXTIME));
		} catch (Exception x) {
			Log.e("DataManager",
					"failed to convert JSONObj location to Location object", x);
		}
		return retLoc;
	}

	/**
	 * Convert a Location object to a JSONObject
	 * 
	 * @param loc
	 * @return
	 */
	public JSONObject getJSONObject(Location loc) {
		JSONObject j = null;
		try {
			j = new JSONObject();
			j.put(PROVIDER, loc.getProvider());
			j.put(LATITUDE, loc.getLatitude());
			j.put(LONGITUDE, loc.getLongitude());
			j.put(ALTITUDE, loc.getAltitude());
			j.put(ACCURACY, (double) loc.getAccuracy());
			j.put(FIXTIME, loc.getTime());

			j.put(TRIPDISTANCE, 0);
			j.put(TRIPTIME, 0);
		} catch (Exception e) {
			Log.e("DataManager", "Failed to getJSONObject from Location", e);
		}
		return j;
	}
	  
	public Location getLastLocationInstance() {
		if (lastLocationJSObj == null)  return null;
		return getLocationInstance(lastLocationJSObj);
	}
	
	/**
	 * this method will flush and close the file - mainly invoked when the app is exiting.
	 * 
	 * @param isTripComplete  true if a user indicates the entire trip is complete, the closing JSON
	 *       brackets will be added, for a completed trip. 
	 */
	public void close(boolean isTripComplete) {
		if (fw == null) {
			return;
		}
		try {
			if (isTripComplete) {
				fw.append("]}");
			}
			fw.flush();
			fw.close();
			fw = null;
			fwReady = false;
		} catch (IOException ie) {
			Log.e("DataManager", "failed to close file ", ie);
		}
	}

	static public String convertTimeString(long t) {
 		long minutes = t / 60000;
 		long hours = minutes / 60;
 		if (hours > 0) {
 			minutes = minutes % 60;
 		}
 		long days = hours / 24;
 		if (days > 0) {
 			hours = hours % 24;
 		}
 		
 		String timeTxt = " " + minutes + " minutes";
 		if (hours == 1 || (hours == 0 && days > 0)) {
 			timeTxt = " " + hours + " hour, " + timeTxt;
 		} else if (hours > 1) {
 			timeTxt = " " + hours + " hours, " + timeTxt;
 		}
 		if (days > 1) {
 			timeTxt = " " + days + " days, " + timeTxt;
 		} else if (days == 1) {
 			timeTxt = "1 day, " + timeTxt;
 		}
 		return timeTxt;
	}
}