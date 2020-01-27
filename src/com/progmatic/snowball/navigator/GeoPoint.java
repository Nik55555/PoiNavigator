package com.progmatic.snowball.navigator;

@SuppressWarnings("EqualsAndHashcode")
public class GeoPoint implements Comparable<GeoPoint> {
	private final static double step = 50;  //in meters
	public final static double std_prec = step/1000;
	
	private double lat;
	private double lon;
	
	private int alt;
	
	private Double prec = null;
	
	public GeoPoint(){}

	public void setPrec(Double prec) {
		this.prec = prec;
	}
	
	public int getAltitude() {
		return alt;
	}

	public void setAltitude(int alt) {
		this.alt = alt;
	}

	public GeoPoint(double lat, double lon) {
		this.lat = lat;
		this.lon = lon;
	}
	
	public GeoPoint(double lat, double lon, int alt) {
		this.lat = lat;
		this.lon = lon;
		this.alt = alt;
	}
	
	public double getLatitude() {
		return lat;
	}

	public void setLatitude(double lat) {
		this.lat = lat;
	}

	public double getLongitude() {
		return lon;
	}

	public void setLongitude(double lon) {
		this.lon = lon;
	}

	public static boolean same(GeoPoint p1, GeoPoint p2) {
		double p = p1.prec != null ? p1.prec : p2.prec != null ? p2.prec : std_prec;
		return distance(p1, p2) < p;
	}
	
	public static double distance(GeoPoint p1, GeoPoint p2) {
		return pdistance(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude());
	}

	public static double pdistance(double lat1, double lon1, double lat2, double lon2) {
            double theta = lon1 - lon2;
            double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
            dist = Math.acos(dist);
            dist = rad2deg(dist);
            dist = dist * 60 * 1.1515;
            dist = dist * 1609.344;
            return (dist);
        }
	
	public static double midpoint(double a, double b)
	{
		double result;
		
		if (a > b) result = (a + b) / 2;
		else
		{
			result = (a + b) / 2;
			if (result <= 0) result = 90 + result;
			else result = -90 + result;
		}
		
		return result;
	}
	
	private static double deg2rad(double deg) {
		  return (deg * Math.PI / 180.0);
		}
	
	private static double rad2deg(double rad) {
		  return (rad * 180 / Math.PI);
		}
	
	public double bearingTo(GeoPoint other) {
		final double lat1 = Math.toRadians(lat);
		final double long1 = Math.toRadians(lon);
		final double lat2 = Math.toRadians(other.lat);
		final double long2 = Math.toRadians(other.lon);
		final double delta_long = long2 - long1;
		final double a = Math.sin(delta_long) * Math.cos(lat2);
		final double b = Math.cos(lat1) * Math.sin(lat2) -
						 Math.sin(lat1) * Math.cos(lat2) * Math.cos(delta_long);
		final double bearing = Math.toDegrees(Math.atan2(a, b));
		final double bearing_normalized = (bearing + 360) % 360;
		return bearing_normalized;
	}
	
	@Override
	public int compareTo(GeoPoint arg0) {
		if ((lat == arg0.lat && lon == arg0.lon) /*|| same(this, arg0)*/)
			return 0;
		else if (lat == arg0.lat)
			return lon < arg0.lon ? -1 : 1;
		else return lat < arg0.lat ? -1 : 1;
	}
	
        @Override
	public String toString() {
		return "lat="+lat+" lon="+lon;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		if (obj.getClass() != getClass()) {
			return false;
		}
		final GeoPoint rhs = (GeoPoint) obj;
		return (rhs.lat == this.lat && rhs.lon == this.lon) /* || same(this, rhs)*/;
	}
}
