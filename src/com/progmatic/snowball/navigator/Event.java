package com.progmatic.snowball.navigator;

import org.json.JSONException;
import org.json.JSONObject;

public class Event {
	private GeoPoint location;
	private long time;
	private long duration;
	private String name;
	private Category type;
        
	private static final String PARAM_LATITUDE = "lat";
	private static final String PARAM_LONGITUDE = "lon";
	private static final String PARAM_ALTITUDE = "alt";
	private static final String PARAM_NAME = "name";
	private static final String PARAM_TYPE = "type";
	private static final String PARAM_TIME = "time";
	private static final String PARAM_DURATION = "dur";

	public enum Category
	{
		entrySchool(0),
		entryRestaurant(1),
		entryMeeting(2);

		private final int value;

		Category(int intValue)
		{
			this.value = intValue;
		}

		public static Category fromInteger(int intValue)
		{
			switch (intValue)
			{
				case 0:
				{
					return entrySchool;
				}
				case 1:
				{
					return entryRestaurant;
				}
				case 2:
				{
					return entryMeeting;
				}
			}
			return null;
		}

		public int toInteger()
		{
			return value;
		}

	}
	
	public Event()
	{
		
	}
	
	public Event(String name, Category type, GeoPoint location, long time, long duration)
	{
		this.location = location;
		this.time = time;
		this.duration = duration;
		this.name = name;
		this.type = type;
	}

	public Event(Event event, int div)
	{
		this.location = event.location;
		this.time = event.time / div;
		this.duration = event.duration;
		this.name = event.name;
		this.type = event.type;
	}

	public Event(JSONObject obj) throws JSONException
	{
		long lat = obj.getLong(PARAM_LATITUDE);
		long lon = obj.getLong(PARAM_LONGITUDE);
		long alt = obj.getInt(PARAM_ALTITUDE);
		this.location = new GeoPoint(lat, lon, (int)alt);

		this.name = "";
		if (obj.has(PARAM_NAME))
			this.name = obj.getString(PARAM_NAME);

		this.type = null;
		if (obj.has(PARAM_TYPE))
			this.type = Event.Category.fromInteger(obj.getInt(PARAM_TYPE));

		if (obj.has(PARAM_TIME))
			this.time = obj.getLong(PARAM_TIME);
		if (obj.has(PARAM_DURATION))
			this.duration = obj.getLong(PARAM_DURATION);
	}

	public JSONObject getEventAsJSONObject() throws JSONException
	{
		JSONObject obj = new JSONObject();

		if (this.getType() != null)
			obj.put(PARAM_TYPE, this.getType().toInteger());
		if (this.getName() != null)
			obj.put(PARAM_NAME, this.getName());

		obj.put(PARAM_LATITUDE, this.getLocation().getLatitude());
		obj.put(PARAM_LONGITUDE, this.getLocation().getLongitude());
		obj.put(PARAM_ALTITUDE, this.getLocation().getAltitude());

		if (this.getTime() != null)
			obj.put(PARAM_TIME, this.getTime());
		if (this.getDuration() != null)
			obj.put(PARAM_DURATION, this.getDuration());

		return obj;
	}

	public GeoPoint getLocation() {
		return location;
	}
	public void setLocation(GeoPoint location) {
		this.location = location;
	}
	public Long getTime() {
		return time;
	}
	public void setTime(Long time) {
		this.time = time;
	}
	public Long getDuration() {
		return duration;
	}
	public void setDuration(Long duration) {
		this.duration = duration;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public Category getType()
	{
		return type;
	}

	public void setType(Category type)
	{
		this.type = type;
	}
}
