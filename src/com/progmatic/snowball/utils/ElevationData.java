package com.progmatic.snowball.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Elevation Data descriptor and holder
 *
 * <p>Utility class to store information about area, holds Elevation Data and provides
 * basic manipulation methods.</p>
 *
 * <p>The class intended to be used in both Android client and Snowball application server.</p>
 */
public class ElevationData {
	// all coordinates stored multiplied to 1M as integers
	private static final int E6 = 1000000;

	/**
	 * Area coordinates
	 */
	public int southE6;
	public int northE6;
	public int westE6;
	public int eastE6;

	/**
	 * Grid parameters.
	 *
	 * Currently only square areas used, but it is possible to work with rectangular ones.
	 *
	 * First row and last row adjusted at the area edge. So typical values for
	 * number of rows (columns) is 1200 + 1, 3600 + 1 etc.
	 */
	public int rows;
	public int columns;

	/**
	 * Elevation data stored as a byte array,
	 * 2 bytes per value, most significant byte goes first (aka big-endian or network byte order)
	 */
	public byte[] data;

	/**
	 * Default constructor. Does nothing.
	 */
	public ElevationData()
	{
	}

	/**
	 * Initialize zero-sized are started at specified point.
	 *
	 * @param latE6
	 *          latitude of the southern edge
	 * @param lonE6
	 *          longitude of the western edge
	 */
	public ElevationData(int latE6, int lonE6)
	{
		southE6 = northE6 = latE6;
		westE6 = eastE6 = lonE6;
	}

	/**
	 * Initialize copy of the {@param hgt}. Data is not copied.
	 *
	 * @param hgt
	 *          Elevation Data object to copy data from
	 */
	public ElevationData(ElevationData hgt)
	{
		copy(hgt);
	}

	/**
	 * Change the grid size
	 *
	 * @param r
	 *          number of rows
	 * @param c
	 *          number of columns
	 */
	public void setSize(int r, int c)
	{
		if (rows != r || columns != c || data == null || data.length != r * c * 2) {
			rows = r;
			columns = c;
			data = new byte[ rows * columns * 2 ];
		}
	}

	/**
	 * Set the grid size from the number of bytes to be allocated.
	 * Works only for 'proper' values - the doubles squares.
	 *
	 * @param bytes
	 *          number of bytes for data
	 */
	public void setSize(int bytes)
	{
		int n = (int)Math.sqrt((double)(bytes / 2));
		if (n * n * 2 == bytes) {
			setSize(n, n);
		}
	}

	/**
	 * Inflate the area to meet bounds of latitude and longitude tile.
	 *
	 * @param latSizeE6
	 *          tile height
	 * @param lonSizeE6
	 *          tile width
	 */
	public void inflate(int latSizeE6, int lonSizeE6)
	{
		southE6 = ((int)Math.floor((double)southE6 / latSizeE6)) * latSizeE6;
		northE6 = southE6 + latSizeE6;
		westE6 = ((int)Math.floor((double)westE6 / lonSizeE6)) * lonSizeE6;
		eastE6 = westE6 + lonSizeE6;
	}

	/**
	 * Copy {@param hgt} to current ElevationData
	 *
	 * @param hgt
	 *          the source
	 * @return
	 *          current ElevationData
	 */
	public ElevationData copy(ElevationData hgt)
	{
		southE6 = hgt.southE6;
		northE6 = hgt.northE6;
		westE6 = hgt.westE6;
		eastE6 = hgt.eastE6;

		rows = hgt.rows;
		columns = hgt.columns;
		data = null;

		return this;
	}

	/**
	 * Calculates the elevation in points between grid lines using biliniar interpolation
	 *
	 * @param latE6
	 *          point latitude
	 * @param lonE6
	 *          point longitude
	 * @return
	 *          elevation
	 */
	public int interpolate(int latE6, int lonE6)
	{
		// calculate the coordinates at the tile
		int latSizeE6 = northE6 - southE6;
		double latIdx = (double)((latE6 - southE6) % latSizeE6) * (rows - 1) / latSizeE6;
		int lonSizeE6 = eastE6 - westE6;
		double lonIdx = (double)((lonE6 - westE6) % lonSizeE6) * (columns - 1) / lonSizeE6;
		// get integer part which is less or equal
		int row = (int) Math.floor(latIdx);
		int col = (int) Math.floor(lonIdx);
		// get real offset
		double rowDelta = latIdx - row;
		double colDelta = lonIdx - col;

		// perform Biliniar interpolation (see http://en.wikipedia.org/wiki/Bilinear_interpolation#Unit_Square)
		double alt = get(row,col) * (1.0 - rowDelta) * (1.0 - colDelta) +
				get(row + 1, col) * rowDelta * (1.0 - colDelta) +
				get(row, col + 1) * (1.0 - rowDelta) * colDelta +
				get(row + 1, col + 1) * rowDelta * colDelta;
		return (int) Math.round(alt);
	}

	/**
	 * Get offset in data array for specified row & column
	 * @param row
	 *          grid row
	 * @param column
	 *          grid column
	 * @return
	 *          offset
	 */
	public int getOffset(int row, int column)
	{
		return ((rows - row - 1) * columns + column) * 2;
	}

	/**
	 * Get grid value at specified row & column
	 *
	 * @param row
	 *          grid row
	 * @param column
	 *          grid column
	 * @return
	 *          elevation in meters
	 * @throws ArrayIndexOutOfBoundsException
	 */
	public int get(int row, int column) throws ArrayIndexOutOfBoundsException
	{
		int o = getOffset(row, column);
		return ((data[o] & 0xFF) << 8) + (data[o + 1] & 0xFF);
	}

	/**
	 * Set grid value at specified row & column
	 *
	 * @param row
	 *          grid row
	 * @param column
	 *          grid column
	 * @param h
	 *          elevation
	 * @throws ArrayIndexOutOfBoundsException
	 */
	public void set(int row, int column, int h) throws ArrayIndexOutOfBoundsException
	{
		set(getOffset(row, column), h);
	}

	/**
	 * Set grid value at specified offset
	 *
	 * @param offset
	 *          offset in data array
	 * @param h
	 *          elevation
	 * @throws ArrayIndexOutOfBoundsException
	 */
	public void set(int offset, int h) throws ArrayIndexOutOfBoundsException
	{
		data[offset] = (byte)((h >> 8) & 0xFF);
		data[offset + 1] = (byte)(h & 0xFF);
	}

	/**
	 * Read data content from input stream. data array should be initialized
	 *
	 * @param is
	 *          input stream
	 * @throws IOException
	 */
	public void readFrom(InputStream is) throws IOException
	{
		int res;
		int pos = 0;
		while( (res = is.read(data, pos, data.length - pos)) > 0 ) {
			pos += res;
		}
	}

	/**
	 * Write data array to output stream
	 *
	 * @param os
	 *          output stream
	 * @throws IOException
	 */
	public void writeTo(OutputStream os) throws IOException
	{
		os.write(data);
	}

	/**
	 * Based on area coordinates, copy portion of {@param hgt} to current object.
	 *
	 * @param hgt
	 *          elevation data to copy from
	 */
	public void copyFrom(ElevationData hgt)
	{
	  rows = (int)((long)(northE6 - southE6) * (hgt.rows - 1) / (hgt.northE6 - hgt.southE6) + 1);
	  columns = (int)((long)(eastE6 - westE6) * (hgt.columns - 1) / (hgt.eastE6 - hgt.westE6) + 1);

	  setSize(rows, columns);

	  int rowOffset = (int)((long)(southE6 - hgt.southE6) * (hgt.rows - 1) / (hgt.northE6 - hgt.southE6));
	  int columnOffset = (int)((long)(westE6 - hgt.westE6) * (hgt.columns - 1) / (hgt.eastE6 - hgt.westE6));

	  for(int r = 0; r < rows; ++r) {
		System.arraycopy(
		  hgt.data, hgt.getOffset(r + rowOffset, columnOffset),
		  data, getOffset(r, 0),
		  columns * 2);
	  }
	}

	/**
	 * Based on area coordinates, copy portion of {@param hgt} to ouput stream {@param os}.
	 *
	 * @param hgt
	 *          elevation data to copy from
	 * @param os
	 *          output stream
	 * @throws IOException
	 */
	public void copyFrom(ElevationData hgt, OutputStream os) throws IOException
	{
	  rows = (int)((long)(northE6 - southE6) * (hgt.rows - 1) / (hgt.northE6 - hgt.southE6) + 1);
	  columns = (int)((long)(eastE6 - westE6) * (hgt.columns - 1) / (hgt.eastE6 - hgt.westE6) + 1);

	  int rowOffset = (int)((long)(southE6 - hgt.southE6) * (hgt.rows - 1) / (hgt.northE6 - hgt.southE6));
	  int columnOffset = (int)((long)(westE6 - hgt.westE6) * (hgt.columns - 1) / (hgt.eastE6 - hgt.westE6));

	  for(int r = rows - 1; r >= 0; --r) {
		os.write(hgt.data, hgt.getOffset(r + rowOffset, columnOffset), columns * 2);
	  }
	}

	/**
	 * Get string representation
	 *
	 * @return area coordinates and grid size
	 */
	public String toString()
	{
	  return "(" + southE6 + "," + westE6 + "," + northE6 + "," + eastE6 + ") " + rows + "x" + columns;
	}

	/**
	 * Create file name part (latitude or longitude) for given {@param valueE6}.
	 *
	 * @param positivePrefix
	 *          prefix for positive {@param valueE6}
	 * @param negativePrefix
	 *          prefix for negative {@param valueE6}
	 * @param valueE6
	 *          value
	 * @param sizeE6
	 *          tile size used to adjust the value for name
	 * @param intDigits
	 *          number of integer digits
	 * @param fractionDigits
	 *          number of fractionDigits
	 * @return string representation of coordinate {@param valueE6} aligned for {@param sizeE6}
	 */
	public static String getNamePart(
			char positivePrefix,
			char negativePrefix,
			int valueE6,
			int sizeE6,
			int intDigits,
			int fractionDigits)
	{
		int i = (int)Math.floor((double)valueE6 / sizeE6);
		valueE6 = i * sizeE6;
		char prefix = positivePrefix;
		int vint = valueE6 / E6;
		int vfrac = valueE6 % E6;
		if (vint < 0) {
			prefix = negativePrefix;
			vint = -vint;
			vfrac = -vfrac;
		}
		String format = "%c%0" + intDigits + "d";
		if (fractionDigits > 0) {
			format += ".%0" + fractionDigits + "d";
			vfrac = vfrac * (int) Math.pow(10, fractionDigits) / E6;
		}
		return String.format(format, prefix, vint, vfrac);
	}

	/**
	 * Create latitude file name part for given {@param latE6}.
	 * The short-cut of getNamePart()
	 *
	 * @param latE6
	 *          value
	 * @param sizeE6
	 *          tile size used to adjust the value for name
	 * @param fractionDigits
	 *          number of fractionDigits
	 * @return string representation of latitude {@param latE6} aligned for {@param sizeE6}
	 */
	public static String getLatNamePart(
			int latE6,
			int sizeE6,
			int fractionDigits)
	{
		return getNamePart('N', 'S', latE6, sizeE6, 2, fractionDigits);
	}

	/**
	 * Create longitude file name part for given {@param lonE6}.
	 * The short-cut of getNamePart()
	 *
	 * @param lonE6
	 *          value
	 * @param sizeE6
	 *          tile size used to adjust the value for name
	 * @param fractionDigits
	 *          number of fractionDigits
	 * @return string representation of longitude {@param lonE6} aligned for {@param sizeE6}
	 */
	public static String getLonNamePart(
			int lonE6,
			int sizeE6,
			int fractionDigits)
	{
		return getNamePart('E','W',lonE6,sizeE6,3,fractionDigits);
	}


}
