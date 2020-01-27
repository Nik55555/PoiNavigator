package com.progmatic.snowball.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeToString
{
  public static String convert(Date time)
  {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(time);
  }

  public static String convertToFileName(long time)
  {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'", Locale.getDefault()).format(new Date(time));
  }

}
