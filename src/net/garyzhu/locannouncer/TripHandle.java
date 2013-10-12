package net.garyzhu.locannouncer;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class TripHandle implements Parcelable {

	public String fileName;
	public String tripName;
	public Timestamp startTime;
	public Timestamp stopTime;
	public boolean completed;
	
	public TripHandle() {
		fileName = null;
		tripName = null;
		startTime = null;
		stopTime = null;
		completed = false;
	}
	
	@SuppressLint("SimpleDateFormat")
	public String toString() {
		SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy  HH:mm:ss");
		String ret = "";
		if (fileName != null && tripName != null ) {
			if (startTime != null && stopTime != null) {
				ret = tripName +": started at " + df.format(startTime) + ",  "
					+ (completed? "completed at ":"paused at ") + df.format(stopTime);
			} else {
				ret = "New trip " + tripName + "(" + fileName +")";
			}
		}
		return ret;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// TODO Auto-generated method stub
		Bundle b = new Bundle();
		b.putString("tripName", tripName);
		b.putString("fileName", fileName);
		if (startTime != null) 	b.putLong("startT", startTime.getTime());
		if (stopTime != null)   b.putLong("stopT", stopTime.getTime());
		b.putBoolean("completed", completed);
		dest.writeBundle(b);
	}

	public static final Parcelable.Creator<TripHandle> CREATOR = new Parcelable.Creator<TripHandle>() {
		public TripHandle createFromParcel(Parcel in) {
			return new TripHandle(in);
		}

		public TripHandle[] newArray(int size) {
			return new TripHandle[size];
		}
	};

	private TripHandle(Parcel in) {
		Bundle b = in.readBundle();
		tripName = b.getString("tripName");
		fileName = b.getString("fileName");
		long t = b.getLong("startT");
		if (t > 10000)	startTime = new java.sql.Timestamp(t);
		
		t = b.getLong("stopT");
		if (t > 1000)   stopTime = new java.sql.Timestamp(t);
		completed = b.getBoolean("completed");
	}
}