package com.progmatic.snowball.navigator;

public class Alarm
{

  public enum Sound
  {
    None, Info, Warning, Critical, Panic, Navigation, Danger;

    public static String getFileName(Sound sound)
    {
      switch (sound)
      {
        case Info:
          return "alarm_info";
        case Warning:
          return "alarm_warning";
        case Critical:
          return "alarm_info1";
        case Panic:
          return "alarm_panic";
        case Navigation:
          return "alarm_buzz";
        case Danger:
          return "alarm_danger";
        case None:
        default:
          return "";
      }
    }
  }

  public enum Type
  {
    Noting, Info, Warning, Critical, Panic, TakeLeft1, TakeLeft2, TakeLeft3, TakeRight1, TakeRight2, TakeRight3,
    HugeDeviation, LowAltitude, CriticalAltitude, ClimbAltitude,
    WrongRouteType, TestAlarm,
    TakePiste, TakeLift, TakeOther, FollowPiste, FollowLift, DestinationIn, ReachDestination, RecalcRoute,
    /*TimeLeft,*/ TimeCritical, TimeMissing, /*TimeOk,*/ LiftClosed, /*LiftClosedByTime,*/ LiftWillBeClosedIn,
    BeCareful, SlowDown, IntersectionClose, DifficultArea, DangerAhead, ProblemsAhead, LongStop,
    TakePisteRight, TakePisteLeft, TakePisteAhead, TakeLiftLeft, TakeLiftRight, LunchTime, MeetingTime, ChildTime, HomeTime, DifficultPiste, PisteClosed
  }

  public enum Distance
  {
    None,
    _0, _50, _100, _150, _200, _250, _300, _400, _500, _750, _1000, // meters
    _5, _10, _15, _20, _25, _30, _45, _60; // minutes

    public static int toNumber(Distance item)
    {
      switch (item)
      {
        case _0:
          return 0;
        case _50:
          return 50;
        case _100:
          return 100;
        case _150:
          return 150;
        case _200:
          return 200;
        case _250:
          return 250;
        case _300:
          return 300;
        case _400:
          return 400;
        case _500:
          return 500;
        case _750:
          return 750;
        case _1000:
          return 1000;
        case _5:
          return 5;
        case _10:
          return 10;
        case _15:
          return 15;
        case _20:
          return 20;
        case _25:
          return 25;
        case _30:
          return 30;
        case _45:
          return 45;
        case _60:
          return 60;
      }

      return 0;
    }

  }

  public Sound sound;
  public Type type;
  public String info;
  public Distance distance;

  public Alarm(Type type)
  {
    this.type = type;
    this.info = null;
    this.distance = Distance.None;
    this.sound = Sound.None;
  }

  public Alarm(Sound sound, Type type)
  {
    this.type = type;
    this.info = null;
    this.distance = Distance.None;
    this.sound = sound;
  }

  public Alarm(Type type, String info)
  {
    this.type = type;
    this.info = info;
    this.distance = Distance.None;
    this.sound = Sound.None;
  }

  public Alarm(Type type, Distance distance, String info, Sound sound)
  {
    this.type = type;
    this.info = info;
    this.distance = distance;
    this.sound = sound;
  }

  public Alarm(Type type, Distance distance, Sound sound)
  {
    this.type = type;
    this.info = null;
    this.distance = distance;
    this.sound = sound;
  }
}
