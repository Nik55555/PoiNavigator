package com.progmatic.snowball.utils.gpxparser;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Created by Val on 24.05.2015.
 */
public class TrkPt
{
  private final static String START_TAG = "<trkpt ";
  private final static String START_ANY_TAG = "<";
  private final static String END_TAG_EMPTY = "/>";
  private final static String END_TAG_FULL = "</trkpt>";

  private final static String ATTR_LAT = "lat=\"";
  private final static String ATTR_LON = "lon=\"";
  private final static String ELEM_ELE = "<ele>";
  private final static String ELEM_TIME = "<time>";

  private double mLat = 0.0;
  private double mLon = 0.0;
  private double mEle = Double.MIN_VALUE;
  private long mTime = 0;

  public TrkPt()
  {
  }

  private boolean parse(String s)
  {
    int lat = s.indexOf(ATTR_LAT);
    int lon = s.indexOf(ATTR_LON);
    int ele = s.indexOf(ELEM_ELE);
    int time = s.indexOf(ELEM_TIME);

    if (lat < 0 || lon < 0)
    {
      //throw new InvalidParameterException("trkpt without lat or lon attribute");
      return false;
    }

    int endLat = s.indexOf("\"", lat + ATTR_LAT.length());
    int endLon = s.indexOf("\"", lon + ATTR_LON.length());

    mLat = Double.parseDouble(s.substring(lat + ATTR_LAT.length(), endLat));
    mLon = Double.parseDouble(s.substring(lon + ATTR_LON.length(), endLon));

    if (ele > 0)
    {
      mEle = Double.parseDouble(s.substring(ele + ELEM_ELE.length(), s.indexOf("<", ele + ELEM_ELE.length())));
    }

    if (time > 0)
    {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
      String ss = s.substring(time + ELEM_TIME.length(), s.indexOf("<", time + ELEM_TIME.length()));
      try
      {
        mTime = sdf.parse(ss).getTime();
      }
      catch (ParseException e)
      {
        e.printStackTrace();
      }
    }

    //Log.d("Parse gpx", String.format("lat: %f lon: %f time %d", mLat, mLon, mTime));
    //System.out.println("lat " + (lat+ATTR_LAT.length()) + " endLat " + endLat);

    return true;
  }

  public boolean parse(StringBuilder s)
  {
    int startTag = s.indexOf(START_TAG);
    if (startTag < 0) return false;

    int nextTag = s.indexOf(START_ANY_TAG, startTag);
    int endTagEmpty = s.indexOf(END_TAG_EMPTY, startTag);
    int endTagFull = s.indexOf(END_TAG_FULL, startTag);

    if (endTagEmpty + END_TAG_EMPTY.length() == s.length())
    {
      // It's like <trkpt .../>EOF
      //System.out.println("matches 0 " + s.substring(startTag, endTagEmpty + END_TAG_EMPTY.length()));
      return parse(s.substring(startTag, endTagEmpty + END_TAG_EMPTY.length()));
    }
    else if (nextTag > 0 && endTagEmpty > 0 && nextTag < endTagEmpty)
    {
      // It's like <trkpt ...><...
      if (nextTag == endTagFull)
      {
        // It's like <trkpt ...></trkpt>
        return parse(s.substring(startTag, endTagFull + END_TAG_FULL.length()));
      }
      else if (endTagFull > 0)
      {
        // It's like <trkpt ...>...</trkpt>
        //System.out.println("matches 2 " + s.substring(startTag, endTagFull + END_TAG_FULL.length()));
        return parse(s.substring(startTag, endTagFull + END_TAG_FULL.length()));
      }
      else
      {
        return false;
      }
    }
    else if (nextTag > 0 && endTagFull > 0)
    {
      //System.out.println("matches 3 " + s.substring(startTag, endTagFull + END_TAG_FULL.length()));
      return parse(s.substring(startTag, endTagFull + END_TAG_FULL.length()));
    }
    else
    {
      //System.out.println("no match " + s.toString());
      return false;
    }

  }

  public double getLat()
  {
    return mLat;
  }

  public double getLon()
  {
    return mLon;
  }

  public double getEle()
  {
    return mEle;
  }

  public long getTime()
  {
    return mTime;
  }
}