package com.progmatic.snowball.navigator;

import com.progmatic.snowball.utils.gpxparser.GpxAnalyser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

import org.apache.log4j.Logger;

public class Navigator {
    protected final static double connectionPointDistance50 = 50; // in meters
    protected final static double connectionPointDistance100 = 100; // in meters
    protected final static double connectionPointDistance150 = 150; // in meters
    protected final static double inPisteOffPisteDistance = 50; // in meters
    protected final static double summerGraphDistance = 9; // in meters
    protected final static double summerMaxDistanceBetweenMiniGraphs1 = 15; // in meters
    protected final static double summerMaxDistanceBetweenMiniGraphs2 = 30; // in meters
    protected final static double summerMaxDistanceBetweenMiniGraphs3 = 50; // in meters
    protected final static double localMergeDistance = 5; // in meters
    protected final static double noDivisionDistance = 5; // in meters
    protected final static int flatSurfaceAltitudeDifference = 3; // in meters
    protected final static double flatSurfaceCathetusDiv = 0.035; // СЃРѕРѕС‚РЅРѕС€РµРЅРёРµ РєР°С‚РµС‚РѕРІ
    protected final static int criticalOneStepAltDiff = 1; // in meters
    protected final static double avgPisteSpeed = 1.5; // m/s
    protected final static double avgLiftSpeed = 1.5; // m/s
    protected final static double liftConnectionTime = 60; //sec
    protected final static double arrivalPrec = 20 * 60; // sec
    protected final static double latePrec = 5 * 60; // sec
    protected final static double criticalTime1 = arrivalPrec; // sec
    protected final static long defaultEventDuration = 20 * 60; // sec
    protected final static double liftDownCoeff = 2;
    protected final static double schemePartCoeff = 0.5;
    protected final static double preferNewRunsCoeff = 2.0;
    protected final static double liftSpeedCoeff = 5;
    protected final static double keyPrec = 0.5;
    protected final static double keyAdd = 0.00000001;
    public static final double M_IN_DEGREE = 111111.111111;

    private final static double slopeGreen = 15.2; // in %
    private final static double slopeBlue = 25.8; // in %
    private final static double slopeRed = 43.7; // in %
    public final static double slopeCriticalMotion = 100; // in % - depends on rider experience
    private final static double slopeCriticalSurface = 120; // in % 120% = 50 grad

    protected final static double beginnerGreenCoeff = 2;
    protected final static double beginnerBlueCoeff = 1;
    protected final static double beginnerRedCoeff = 20;
    protected final static double middleGreenCoeff = 30;
    protected final static double middleBlueCoeff = 10;
    protected final static double middleRedCoeff = 1;
    protected final static double middleBlackCoeff = 20;
    protected final static double expertGreenCoeff = 50;
    protected final static double expertBlueCoeff = 30;
    protected final static double expertRedCoeff = 10;
    protected final static double expertBlackCoeff = 1;
    protected final static double expertSkiRouteCoeff = 1;

    protected final static double baseStep = 50;  //in meters
    protected final static double point2lineDist = 30;  //in meters
    protected final static double lenCoeff = 1.5; // map length to destination coeff
    protected final static double downCoeff = 1.0; // elevation difference coeff
    protected final static double upCoeff = 5.0;//1.2; // elevation difference coeff to go up
    protected final static double predictionTime = 5; // sec
    protected final static double offPisteDistCoeff = 5; //
    protected final static double onPisteDistCoeff = 5; //
    protected final static double connectionDistCoeff = 2; //
    protected final static double routeDeviationCoeff = 1.3;
    protected final static double minCheckDistance = 150;
    protected final static double minChangeDistance = 50;
    protected final static int recalcCoeff = 3;
    protected final static long checkAlarmPeriod = 10000; // ms

    public static final double deviation1 = 50; // meters
    protected final static double deviation2 = 100; // meters
    protected final static double deviation3 = 150; // meters
    protected final static double bearingSector = 15; // grad +/-

    //
    protected int trackDeepness = 25;
    protected long lastRouteRecalc = 0;
    protected long timeRouteRecalc = 0;
    protected long lastCriticalAlarmCheck = 0;
    protected long lastNavigationAlarmCheck = 0;
    protected GeoPoint lastPosition = null;
    protected GeoPoint prevPosition = null;
    protected Alarm.Type lastCriticalAlarm = Alarm.Type.Noting;
    protected Alarm.Type lastNavigationAlarm = Alarm.Type.Noting;

    public SkiArea.Connection currentConnection = null;
    public double currentConnnectionDistanceLeft = 0;
    public Route currentRoute = null;
    public boolean recalcRoute = true;

    protected static Logger _log = Logger.getLogger(Navigator.class);

    //

    public enum SlopeColor {
        Green, Blue, Red, Black, Yellow
    }

    public enum RiderLevel {
        Beginner, Middle, Expert, FreeRider
    }

    public enum MovementMode {
        StandBy, FlatSurface, Ascent, Descent
    }

    public static OnPisteRoute calculateOnPisteRoute(SkiArea area, boolean checkLiftIsOpen, SkiArea.Connection conStart, SkiArea.Connection conEnd, GeoPoint start, GeoPoint end, RiderPreferences riderPreferences) {
        try {
            long time;
            if (checkLiftIsOpen) {
                time = new Date().getTime() / 1000; // in sec
            } else
                time = 0;

            //GeoPoint startGeoPoint = new GeoPoint(45.567506, 6.810441);
            //GeoPoint endGeoPoint = new GeoPoint(45.542897, 6.840464);

            //OnPisteRoute route = OnPisteNavigator.calculateOnPisteRoute(area, 0, null, null, startGeoPoint, endGeoPoint, new TreeMap<Long, SkiArea.Connection.Usage>());
            SkiArea.Transition traStart = (conStart == null) ? null : area.getTransitions().get(conStart.data.id);
            SkiArea.Transition traEnd = (conEnd == null) ? null : area.getTransitions().get(conEnd.data.id);
            OnPisteRoute route = OnPisteNavigator.calculateOnPisteRoute(area, time, traStart, traEnd, start, end, new TreeMap<Long, SkiArea.Connection.Usage>(), riderPreferences);
            if (route != null && route.getError() == OnPisteRoute.Error.ok) {
                route.calculateTotals();
            } else {
                if (route != null)
                    _log.error("error=" + route.getError());
            }
            return route;
        } catch (Exception e) {
            _log.error(e.toString(), e);
        }
        return new OnPisteRoute(OnPisteRoute.Error.internalError);
    }

    public static OnPisteRoute calculateBestCoveredForTimeRoute(SkiArea area, List<Event> events, RiderPreferences riderPreferences) {
        try {
            OnPisteRoute route = OnPisteNavigator.calculateBestCoveredForTimeRoute(area, events, new TreeMap<Long, SkiArea.Connection.Usage>(), riderPreferences);
            if (route != null && route.getError() == OnPisteRoute.Error.ok)
                route.calculateTotals();
            else if (route != null)
                _log.error("error=" + route.getError());
            else
                _log.error("internel error in calculateBestCoveredForTimeRoute");

            return route;
        } catch (Exception e) {
            _log.error("calculateBestCoveredForTimeRoute", e);
        }
        return new OnPisteRoute(OnPisteRoute.Error.internalError);
    }

    public static double calcDistance(List<GeoPoint> points) {
        double distance = 0;
        GeoPoint p1 = null;
        for (GeoPoint p2 : points) {
            if (p2.getAltitude() == 0)
                p2.setAltitude(Elevation.getElevation(p2));
            if (p1 != null)
                distance += fullDistance(p1, p2);
            p1 = p2;
        }
        return distance;
    }

    /////////////////////////////////////////////////

    public static double calcDuration(RiderLevel level, SlopeColor color, double distance) {
        return distance / avgPisteSpeed; //TODO: make more relevant
    }

    public static double calcDuration(SkiArea.Connection.Type type, double distance) {
        return distance / avgLiftSpeed + liftConnectionTime; //TODO: make more relevant
    }

    /////////////////////////////////////////////////
    //TODO: You can store the extreme found and read from it, in order to optimize
    // this is work properly only if points on same distance between each other
    public static List<GeoPoint> findNearest(List<GeoPoint> route, GeoPoint current) {
        List<GeoPoint> res = new ArrayList<>();
        int found = -1;
        double distance = 0;
        for (int i = 0; i < route.size(); i++) {
            GeoPoint p = route.get(i);
            double d = GeoPoint.distance(current, p);
            if (found < 0 || d < distance) {
                found = i;
                distance = d;
            }
        }
        res.add(route.get(found));
        if (route.size() > 1) {
            if (found == 0)
                res.add(route.get(1));
            else if (found == route.size() - 1)
                res.add(route.get(route.size() - 2));
            else {
                GeoPoint prev = route.get(found - 1);
                GeoPoint next = route.get(found + 1);
                double dnext = GeoPoint.distance(current, next);
                double dprev = GeoPoint.distance(current, prev);
                if (dprev < dnext)
                    res.add(prev);
                else
                    res.add(next);
            }
        } else
            res.add(route.get(found));
        return res;
    }

    public static double calcDeviation(List<GeoPoint> segment, GeoPoint current) {
        if (segment.size() == 1)
            return GeoPoint.distance(current, segment.get(0));
        GeoPoint p0 = segment.get(0);
        GeoPoint p1 = segment.get(1);
        double a = GeoPoint.distance(p0, p1);
        double b = GeoPoint.distance(current, p0);
        double c = GeoPoint.distance(current, p1);
        double ac = angleCos(a, b, c);
        double ab = angleCos(a, c, b);
        double p2 = Math.PI / 2;
        if (ac <= p2 || ab <= p2)
            return Math.min(b, c);
        return b * Math.sin(ab);
    }

    public static double normalizeBearing(double bearing) {
        return (bearing + 360) % 360;
    }

    public static boolean wrongBearing(double current, double toPoint) {
        double b1 = normalizeBearing(toPoint + bearingSector);
        double b2 = normalizeBearing(toPoint - bearingSector);
        if (current > b2 && current < b1)
            return false;
        if (b1 < bearingSector * 2) {
            b1 += 360;
            if (current < bearingSector * 2)
                current += 360;
            if (current > b2 && current < b1)
                return false;
        }
        return true;
    }

    public static boolean canGo(GeoPoint current, GeoPoint next, double distance) {
        return slope(current.getAltitude(), next.getAltitude(), distance) < slopeCriticalMotion;
    }

    public static boolean canGo(GeoPoint current, GeoPoint next, int ecur, int enext, double distance, double step, double dstep) {
        return slope(ecur, enext, distance) < slopeCriticalMotion && !noGoZone(next, enext, step, dstep);
    }

	/*private static boolean reachable(GeoPoint current, GeoPoint next, double step) {
    if (step <= minStep)
			return true;
		OffroadRoute path = Navigator.calculateOffroadRoute(current, next, distance(current, next)*distCoeff, step/10);
		return path.passable();
	}*/


    public static double maxSlope(GeoPoint point, int elev, double step, double dstep) {
        double slope, result = 0;
        slope = slopeTo(point, elev, step, 0);
        result = Math.max(result, slope);
        slope = slopeTo(point, elev, dstep, 45);
        result = Math.max(result, slope);
        slope = slopeTo(point, elev, step, 90);
        result = Math.max(result, slope);
        slope = slopeTo(point, elev, dstep, 135);
        result = Math.max(result, slope);
        slope = slopeTo(point, elev, step, 180);
        result = Math.max(result, slope);
        slope = slopeTo(point, elev, dstep, 225);
        result = Math.max(result, slope);
        slope = slopeTo(point, elev, step, 270);
        result = Math.max(result, slope);
        slope = slopeTo(point, elev, dstep, 315);
        result = Math.max(result, slope);
        return result;
    }

    public static boolean noGoZone(GeoPoint point, int elev, double step, double dstep) {
        return maxSlope(point, elev, step, dstep) > slopeCriticalSurface;
    }

    private static double slopeTo(GeoPoint point, int elev, double distance, double bearing) {
        GeoPoint next = nextPoint(point, distance, bearing);
        return slope(elev, Elevation.getElevation(next), distance);
    }

    public static double distance(List<GeoPoint> route, List<GeoPoint> segment) {
        double dist = 0;
        GeoPoint prev = null;
        for (int i = route.indexOf(segment.get(0)); i < route.size(); i++) {
            GeoPoint cur = route.get(i);
            if (prev != null)
                dist += GeoPoint.distance(prev, cur);
            prev = cur;
        }
        return dist;
    }

    /////////////////////////////////////////////////

    public static SlopeColor getSlopeColor(GeoPoint p1, GeoPoint p2) {
        double slope = slopeBetweenTwoPoints(p1, p2);
        if (p1.getAltitude() < p2.getAltitude())
            return SlopeColor.Yellow;
        if (slope <= slopeGreen)
            return SlopeColor.Green;
        if (slope <= slopeBlue)
            return SlopeColor.Blue;
        if (slope <= slopeRed)
            return SlopeColor.Red;
        return SlopeColor.Black;
    }

    public static double slopeBetweenTwoPoints(GeoPoint p1, GeoPoint p2) {
        return slope(p1.getAltitude(), p2.getAltitude(), GeoPoint.distance(p1, p2));
    }

    /////////////////////////////////////////////////

    public static double angleCos(double a, double b, double c) {
        return Math.acos(Math.round((a * a + b * b - c * c) / (2 * a * b) * 1000) / 1000);
    }

    public static double slope(int e1, int e2, double distance) {
        if (distance < 1)
            return 0;
        double ediff = Math.abs(e1 - e2);
        return 100 * (ediff / distance);
    }

    private static double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private static double rad2deg(double rad) {
        return (rad * 180 / Math.PI);
    }

    public static double edistance(double distance, int elevation1, int elevation2) {
        int elev_diff = elevation1 - elevation2;
        return Math.sqrt(distance * distance + elev_diff * elev_diff);
    }

    public static double fullDistance(GeoPoint point1, GeoPoint point2) {
        return GeoPoint.distance(point1, point2);
        //return edistance(distance(point1, point2), point1.getAltitude(), point2.getAltitude());
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

    public static GeoPoint nextPoint(GeoPoint p, double distance, double bearing) {
        double R = 6371200;
        double rlat1 = deg2rad(p.getLatitude());
        double rlon1 = deg2rad(p.getLongitude());
        double rbearing = deg2rad(bearing);
        double rdistance = distance / R; // normalize linear distance to radian angle

        double lat2 = Math.asin(Math.sin(rlat1) * Math.cos(rdistance) +
                Math.cos(rlat1) * Math.sin(rdistance) * Math.cos(rbearing));
        double lon2 = rlon1 + Math.atan2(Math.sin(rbearing) * Math.sin(rdistance) * Math.cos(rlat1),
                Math.cos(rdistance) - Math.sin(rlat1) * Math.sin(lat2));
        return new GeoPoint(rad2deg(lat2), rad2deg(lon2));
    }

    public static double diagonal(double distance) {
        return Math.sqrt(distance * distance + distance * distance);
    }

    public static GpxAnalyser.UpDownFlag MM2UDF(MovementMode mode) {
        if (mode == MovementMode.Ascent)
            return GpxAnalyser.UpDownFlag.UP;
        if (mode == MovementMode.Descent)
            return GpxAnalyser.UpDownFlag.DOWN;
        if (mode == MovementMode.StandBy)
            return GpxAnalyser.UpDownFlag.STOP;
        if (mode == MovementMode.FlatSurface)
            return GpxAnalyser.UpDownFlag.WALK;
        return GpxAnalyser.UpDownFlag.NONE;
    }

    public static double getDistanceToSegment(GeoPoint myGeoPoint, GeoPoint p1, GeoPoint p2) {
        //how many meters in 1 grad about.
        double dist1grad_lat = Navigator.M_IN_DEGREE;
        double dist1grad_lon = Navigator.pdistance(
                myGeoPoint.getLatitude(),
                myGeoPoint.getLongitude(),
                myGeoPoint.getLatitude(),
                myGeoPoint.getLongitude() + 1
        );

        double dist = com.progmatic.snowball.utils.GeometryHelper.getDistanceToSegment(
                p1.getLatitude() * dist1grad_lat,
                p1.getLongitude() * dist1grad_lon,
                p2.getLatitude() * dist1grad_lat,
                p2.getLongitude() * dist1grad_lon,
                myGeoPoint.getLatitude() * dist1grad_lat,
                myGeoPoint.getLongitude() * dist1grad_lon
        );

        return dist;
    }

}