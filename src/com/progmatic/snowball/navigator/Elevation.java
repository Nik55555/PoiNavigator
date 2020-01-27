package com.progmatic.snowball.navigator;

import com.progmatic.snowball.elevationcache.ElevationDataCache;

/**
 * Placeholder for altitude calculation functions.
 *
 * <p>Static methods of this class only perform calculation (interpolation) of the altitude and
 * relies on data provided by {@link ElevationDataCache}.</p>
 */
public class Elevation {

	/**
	 * Get altitude in meters for specified geographic point.
	 *
	 * <p>This function may block on network or local IO and should not be called from UI thread.
	 * Use AsyncTask or other methods of asynchronous execution.</p>
	 *
	 * @param point
	 *             the point where to calculate the altitude
	 * @return the altitude in meters
	 */
	public static int getElevation(GeoPoint point) {
		return 0;
		// exceptions for geopoints
//		if (point != null && GeoPoint.distance(point, new GeoPoint(47.2429748901964, 11.9593557004085)) < 15)
//			return 2180;
//
//		final ElevationDataCache cache = ElevationDataCache.getInstance();
//		final String hgtFilename = ElevationDataCache.getHgtFilename(point);
//
//		// get the altitude data array from cache
//		final short[] data = cache.getElevationData(hgtFilename);
//		if (data == null) {
//			return -1;
//		}
//
//		// calculate the coordinates at the tile
//		double idx_lat = get_idx_lat(point.getLatitude());
//		double idx_lon = get_idx_lon(point.getLongitude());
//		// get integer part which is less or equal
//		int south_lat = (int)Math.floor(idx_lat);
//		int west_lon = (int)Math.floor(idx_lon);
//		// get real offset
//		double dlat = idx_lat - Math.floor(idx_lat);
//		double dlon = idx_lon - Math.floor(idx_lon);
//
//		// perform Biliniar interpolation (see http://en.wikipedia.org/wiki/Bilinear_interpolation#Unit_Square)
//		double alt = get_land_alt(data, south_lat, west_lon) * (1.0 - dlat) * (1.0 - dlon) +
//				get_land_alt(data, south_lat + 1, west_lon) * dlat * (1.0 - dlon) +
//				get_land_alt(data, south_lat, west_lon + 1) * (1.0 - dlat) * dlon +
//				get_land_alt(data, south_lat + 1, west_lon + 1) * dlat * dlon;
//		/* debug print for those who doubts
//		getElevationFromGoogleMaps(point);
//		Log.d("getElevation", "(" + point + ") = " + alt);
//		Log.d("getElevation", "interpolation data " +
//				get_land_alt(data, south_lat, west_lon) + "," +
//				get_land_alt(data, south_lat + 1, west_lon) + "," +
//				get_land_alt(data, south_lat + 1, west_lon + 1) + "," +
//				get_land_alt(data, south_lat, west_lon + 1) + ": " +
//				dlat + "," + dlon);
//				*/
//		// get nearest integer value
//		return (int)Math.round(alt);
	}

	// Get elevation from Google Api, return value in meters.
	// More details on https://developers.google.com/maps/documentation/elevation/
	private static double get_idx_lat(double d) {
		return (d - Math.floor(d)) * Parameters.hgtRows;
	}

	private static double get_idx_lon(double d) {
		return (d - Math.floor(d)) * Parameters.hgtColumns;
	}

	private static short get_land_alt(short[] data, int lat, int lon) {
		// Altitude values placed in data array from North to South, while coordinates grows
		// from South to North.
		int idx = (Parameters.hgtRows - lat) * Parameters.hgtColumns + lon;
		if (data == null || idx >= data.length) {
			return 0;
		} else {
			return data[idx];
		}
	}
}
