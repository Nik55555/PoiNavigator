package com.progmatic.snowball.navigator;

public class RiderPreferences {
  private boolean noGreenPiste;
  private boolean noBluePiste;
  private boolean noRedPiste;
  private boolean noBlackPiste;
  private boolean noSkiRoute;
  private boolean avoidDragLifts;
  private boolean LiftDown;
  private boolean preferNewRuns;
  private boolean minimizeLifting;
  private int schemePart;
  private Integer riderLevel;

  public RiderPreferences()
  {
	  
  }
  
  public RiderPreferences(int riderLevel, boolean minimizeLifting, int schemePart)
  {
      this.riderLevel = riderLevel;
  }

  public Navigator.RiderLevel getRiderLevel()
  {
    if (riderLevel == null)
      // Value was not set yet; designed to be used only with startup wizard
      return null;

    switch (riderLevel)
    {
      case 2:
      case 3:
        return Navigator.RiderLevel.Middle;
      case 4:
      case 5:
        return Navigator.RiderLevel.Expert;
      case 6:
      case 7:
        return Navigator.RiderLevel.FreeRider;
      default:
        return Navigator.RiderLevel.Beginner;
    }

  }

  public Integer getIntRiderLevel()
  {
    return riderLevel;
  }

  public void setIntRiderLevel(int level)
  {
    riderLevel = level;
  }

  //public static void setRiderLevel(Navigator.RiderLevel riderLevel)
  //{
  //  SnowballAppPreferences.riderLevel = riderLevel;
  //  Preferences.setString("riderLevel", riderLevel.toString(), context);
  //}

  public boolean isNoGreenPiste()
  {
    return noGreenPiste;
  }

  public void setNoGreenPiste(boolean noGreenPiste)
  {
    this.noGreenPiste = noGreenPiste;
  }

  public boolean isNoBluePiste()
  {
    return noBluePiste;
  }

  public void setNoBluePiste(boolean noBluePiste)
  {
    this.noBluePiste = noBluePiste;
  }

  public boolean isNoRedPiste()
  {
    return noRedPiste;
  }

  public void setNoRedPiste(boolean noRedPiste)
  {
    this.noRedPiste = noRedPiste;
  }

  public boolean isNoBlackPiste()
  {
    return noBlackPiste;
  }

  public void setNoBlackPiste(boolean noBlackPiste)
  {
    this.noBlackPiste = noBlackPiste;
  }

  public boolean isNoSkiRoute()
  {
    return noSkiRoute;
  }

  public void setNoSkiRoute(boolean noSkiRoute)
  {
    this.noSkiRoute = noSkiRoute;
  }

  public boolean isAvoidDragLifts()
  {
    return avoidDragLifts;
  }

  public void setAvoidDragLifts(boolean value)
  {
    avoidDragLifts = value;
  }

  public boolean isLiftDown()
  {
    return LiftDown;
  }

  public void setLiftDown(boolean liftDown)
  {
    LiftDown = liftDown;
  }

  public boolean isPreferNewRuns()
  {
    return preferNewRuns;
  }

  public void setPreferNewRuns(boolean preferNewRuns)
  {
    this.preferNewRuns = preferNewRuns;
  }

  public boolean isMinimizeLifting()
  {
    return minimizeLifting;
  }

  public void setMinimizeLifting(boolean minimizeLifting)
  {
    this.minimizeLifting = minimizeLifting;
  }

  public int getSchemePart()
  {
    return schemePart;
  }

  public void setSchemePart(int schemePart)
  {
    this.schemePart = schemePart;
  }

    
}
