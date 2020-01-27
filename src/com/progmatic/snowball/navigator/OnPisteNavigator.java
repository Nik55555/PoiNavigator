package com.progmatic.snowball.navigator;

import com.progmatic.snowball.navigator.Event.Category;
import com.progmatic.snowball.navigator.SkiArea.Connection;
import com.progmatic.snowball.navigator.SkiArea.NodalPoint;
import com.progmatic.snowball.navigator.SkiArea.Transition;
import com.progmatic.snowball.utils.gpxparser.GpxAnalyser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

public class OnPisteNavigator extends Navigator {
    public static final int REURSION_DEPTH = 3;

    static class OnPisteContext {
        public double key;
        public double duration;
        OnPisteContext parent;
        Transition transition;

        public OnPisteContext(Transition transition, OnPisteContext parent, double duration, double key) {
            this.transition = transition;
            this.parent = parent;
            this.key = key;
            this.duration = duration;
        }
    }

    // members
    private TreeMap<Double, OnPisteContext> sorted;
    private TreeMap<Long, OnPisteContext> all;
    private Transition conStart = null;
    private Transition conEnd = null;
    private GeoPoint start, pstart;
    private GeoPoint end, pend;
    private SkiArea area;
    private TreeMap<Long, Connection.Usage> usages;

    /**
     * @param area     ski area - graph of connections
     * @param conStart if start connection is known, otherwise - null
     * @param conEnd   if end connection is known, otherwise - null
     * @param start    start point - may be not on connection, but in some proximity
     * @param end      end point - may be not on connection, but in some proximity
     * @param usages   used for greedy algorithm
     */
    public OnPisteNavigator(SkiArea area, Transition conStart, Transition conEnd, GeoPoint start, GeoPoint end, TreeMap<Long, Connection.Usage> usages) {
        this.start = start;
        this.end = end;
        // find start connection if it null
        if (conStart == null)
            this.conStart = findEnter(area, start);
        else
            this.conStart = conStart;
        // find end connection if it null
        if (conEnd == null)
            this.conEnd = findExit(area, end);
        else
            this.conEnd = conEnd;
        //TODO: check canGo for start && end
        // check if start point on connection or not
        if (this.conStart != null) {
            pstart = pointOnConnection(start, this.conStart.connection);
            if (pstart == null)
                pstart = this.conStart.start;
        }
        // check if end point on connection or not
        if (this.conEnd != null) {
            pend = pointOnConnection(end, this.conEnd.connection);
            if (pend == null)
                pend = this.conEnd.end;
        }
        // initialization
        sorted = new TreeMap<Double, OnPisteContext>();
        all = new TreeMap<Long, OnPisteContext>();
        this.usages = usages;
        // add first step
        if (this.conStart != null) {
            double key = GeoPoint.distance(this.start, this.end) / avgPisteSpeed * connectionDistCoeff; //what does mean connectionDistCoeff ??
            OnPisteContext path = new OnPisteContext(this.conStart, null, 0, key);
            sorted.put(key, path);
            all.put(this.conStart.connection.data.id, path);
        }
/*
    if (this.conStart == null)
      FLog.d("aaa", "start is null");
    else
      FLog.d("aaa", "start is "+this.conStart.connection.data.id+" "+this.conStart.connection.data.name);
    if (this.conEnd == null)
      FLog.d("aaa", "end is null");
    else
    FLog.d("aaa", "end is "+this.conEnd.connection.data.id+" "+this.conEnd.connection.data.name);
*/
    }

    // search for enter to the graph - nearest connection for the start point
    private static Transition findEnter(SkiArea area, GeoPoint start) {
        List<GpxAnalyser.PointWithDistance> points = GpxAnalyser.listNearestLines(
                area.getTransitions(),
                start,
                Navigator.deviation3 * 3);

        if (points.isEmpty()) {
            points = GpxAnalyser.listNearestLines(
                    area.getTransitions(),
                    start,
                    Double.MAX_VALUE / 2);
        }

        if (points.isEmpty())
            return null;

        long layerDataId = points.get(0).layerData.id;
        Transition res = area.getTransitions().get(layerDataId);

        return res;
    }

    // search for exit from the graph - nearest connection for the end point
    private static Transition findExit(SkiArea area, GeoPoint end) {
        return findEnter(area, end);
    }

    private static Dejkstra.CheckTransition makeCheck(boolean checkPrefs) {
        Dejkstra.CheckTransition checkTransition;
        if (checkPrefs) {
            checkTransition = new Dejkstra.CheckTransition() {
                @Override
                public boolean checkTransition(Transition transition, double time, RiderPreferences riderPreferences) {
                    return canGo(transition, (long) time, riderPreferences);
                }
            };
        } else {
            checkTransition = new Dejkstra.CheckTransition() {
                @Override
                public boolean checkTransition(Transition transition, double time, RiderPreferences riderPreferences) {
                    return true;
                }
            };
        }
        return checkTransition;
    }


    protected static OnPisteRoute fastestRouteDejkstra(SkiArea area, GeoPoint start, GeoPoint end) {
        RiderPreferences preferences = new RiderPreferences();
        return calculateOnPisteRouteDejkstra(area, start, end, 0, new Dejkstra(area.nodalPoints, preferences),
                true, false, false, preferences);
    }


    protected static OnPisteRoute calculateOnPisteRouteDejkstra(
            SkiArea area, GeoPoint start, GeoPoint end, long time, Dejkstra dejkstra,
            boolean recalcFromStart, boolean recalcToFinish, boolean checkPrefs,
            RiderPreferences riderPreferences) {

        if (area.nodalPoints.isEmpty())
            return new OnPisteRoute(OnPisteRoute.Error.cantFindRoute);

        GeoPoint startPoint = Dejkstra.findNodalPoint(area.nodalPoints, start);
        GeoPoint endPoint = Dejkstra.findNodalPoint(area.nodalPoints, end);

        if (startPoint == null)
            return new OnPisteRoute(OnPisteRoute.Error.cantFindEnter);
        if (endPoint == null)
            return new OnPisteRoute(OnPisteRoute.Error.cantFindExit);

        if (dejkstra == null) {
            dejkstra = new Dejkstra(area.nodalPoints, riderPreferences);
        }

        try {
            if (recalcFromStart)
                dejkstra.CalculateDurations(startPoint, true, time, makeCheck(checkPrefs)); // считаем время от старта до всех точек
            if (recalcToFinish)
                dejkstra.CalculateDurations(endPoint, false, time, makeCheck(checkPrefs)); // считаем время до финиша от всех точек
        } catch (Exception e) {
            return new OnPisteRoute(OnPisteRoute.Error.internalError);
        }


        if (recalcToFinish) {
            NodalPoint np = area.nodalPoints.get(startPoint);
            if (np == null)
                return new OnPisteRoute(OnPisteRoute.Error.internalError);

            ArriveInfo ai = dejkstra.arrives.get(np);
            if (ai == null || ai.durationToFinish == Dejkstra.INFINITY)
                return new OnPisteRoute(OnPisteRoute.Error.cantFindRoute);
        } else if (recalcFromStart) {
            NodalPoint np = area.nodalPoints.get(endPoint);
            if (np == null)
                return new OnPisteRoute(OnPisteRoute.Error.internalError);

            ArriveInfo ai = dejkstra.arrives.get(np);
            if (ai == null || ai.durationFromStart == Dejkstra.INFINITY)
                return new OnPisteRoute(OnPisteRoute.Error.cantFindRoute);
        }

        List<Transition> path;
        if (recalcFromStart) {
            // выбор маршрута от старта
            path = new ArrayList<>();
            GeoPoint curPoint = endPoint;
            while (!curPoint.equals(startPoint)) {
                Transition curTransition = area.transitionFromStart(curPoint, dejkstra.arrives);
                if (curTransition == null)
                    return new OnPisteRoute(OnPisteRoute.Error.internalError);

                path.add(curTransition);

                curPoint = curTransition.gStart;
            }
        } else {
            // выбор маршрута от финиша
            path = new ArrayList<>();
            GeoPoint curPoint = startPoint;
            while (!curPoint.equals(endPoint)) {
                Transition curTransition = area.transitionToFinish(curPoint, dejkstra.arrives);
                if (curTransition == null)
                    return new OnPisteRoute(OnPisteRoute.Error.internalError);

                path.add(curTransition);

                curPoint = curTransition.gEnd;
            }
        }

        OnPisteRoute route = new OnPisteRoute(area, startPoint, endPoint, path);

        return route;
    }

    protected static OnPisteRoute calculateOnPisteRoute1(SkiArea area, long startTime, Transition traStart, Transition traEnd, GeoPoint start, GeoPoint end, TreeMap<Long, Connection.Usage> usages, RiderPreferences riderPreferences) {
        List<Event> events = new ArrayList<Event>();
        events.add(new Event("s", Event.Category.entrySchool, new GeoPoint(45.567506, 6.810441), 1449219954823L, 0));
        events.add(new Event("r", Event.Category.entryRestaurant, new GeoPoint(45.542897, 6.840464), 1449231726834L, 0));
//    events.add(new Event("r",Event.Category.entryRestaurant,new GeoPoint(45.5629, 6.7949), 1449244523725L, 0));
//    events.add(new Event("r",Event.Category.entryRestaurant,new GeoPoint(45.544761, 6.833467), 1449231726834L, 0));
//    events.add(new Event("e",Event.Category.entryMeeting,new GeoPoint(45.549228, 6.739139), 1449244523725L, 0));
        OnPisteRoute route = OnPisteNavigator.calculateBestCoveredForTimeRoute(area, events, new TreeMap<Long, SkiArea.Connection.Usage>(), riderPreferences);
        return route;
    }

    /**
     * @param area             ski area - graph of connections
     * @param startTime
     * @param traStart         if start transition is known, otherwise - null
     * @param traEnd           if end transition is known, otherwise - null
     * @param start            start point - may be not on connection, but in some proximity
     * @param end              end point - may be not on connection, but in some proximity
     * @param usages           used for greedy algorithm
     * @param riderPreferences
     * @return
     */

    // search fastest way from start to end in graph
    protected static OnPisteRoute calculateOnPisteRoute(
            SkiArea area, long startTime, Transition traStart, Transition traEnd, GeoPoint start, GeoPoint end,
            TreeMap<Long, Connection.Usage> usages, RiderPreferences riderPreferences) {

        OnPisteNavigator navigator = new OnPisteNavigator(area, traStart, traEnd, start, end, usages);

        if (navigator.conStart == null)
            return new OnPisteRoute(OnPisteRoute.Error.cantFindEnter);
        if (navigator.conEnd == null)
            return new OnPisteRoute(OnPisteRoute.Error.cantFindExit);

        if (navigator.conStart != null && navigator.conEnd != null) {
            // событие "доехать до точки" для хранения dejkstra.arrives
            Event event = new Event("Simple go to point", Category.entryMeeting, navigator.conEnd.gEnd, 0, 0);
            // рачёт расстояний до финиша
            Dejkstra dejkstra = new Dejkstra(area.nodalPoints, riderPreferences);
            calculateOnPisteRouteDejkstra(area, navigator.conStart.gEnd, navigator.conEnd.gEnd, startTime, dejkstra, false, true, !(startTime == 0), riderPreferences);

            // main calculation loop
            Entry<Double, OnPisteContext> entry;
            do {
                // take best path for this step of algorithm
                entry = navigator.sorted.firstEntry();
                // stop check now is here - if true we are rich destination
                if (entry.getValue().transition.connection == navigator.conEnd.connection)
                    break;

//        FLog.d("aaa", "->" + entry.getValue().transition.connection.data.name+"("+entry.getValue().transition.connection.data.id+")");
                // remove best path from sorted ...
                navigator.sorted.remove(entry.getKey());
                // .. to add next step paths
                navigator.nextStep(area, entry.getValue(), startTime, dejkstra.arrives, riderPreferences);
            } while (navigator.sorted.size() > 0);

            if (navigator.sorted.size() == 0)
                return new OnPisteRoute(OnPisteRoute.Error.cantFindRoute);

            //FLog.d("aaa", "--->*"+entry.getValue().transition.connection.data.name);
            // prepare res
            OnPisteRoute route = new OnPisteRoute(area, start, end);
            // save end of the route for recalculation that may happens in future
            route.setEvents(new ArrayList<Event>());
            route.getEvents().add(new Event("", null, end, 0, 0));
            // prepare result route from data structures used for search
            navigator.convert2track(entry, route);
            return route;
        }
        if (navigator.conStart == null)
            return new OnPisteRoute(OnPisteRoute.Error.cantFindEnter);
        else
            return new OnPisteRoute(OnPisteRoute.Error.cantFindExit);
    }

    /**
     * @param entry head of list, contained steps of the route
     * @param route result route
     * @return
     */
    // prepare result route from data structures used for search
    private void convert2track(Entry<Double, OnPisteContext> entry, OnPisteRoute route) {
        // main content of a route - a list of transitions
        List<Transition> transitions = new ArrayList<Transition>();
        if (entry != null) {
            OnPisteContext path = entry.getValue();

            // this is last transition if end point out of piste
            if (pend == conEnd.end) {
//          Transition transition = Connection.createTransition(null, conEnd.end, end);
                transitions.add(conEnd);
                route.setDuration(conEnd.getDuration());
                route.setDistance(conEnd.getDistance());
            }
            do {
                //if (path.transition.connection!=null)
                //FLog.d("aaa","->"+path.transition.connection.data.name+"("+path.transition.connection.data.id+")");
                // add transitions to result route to the head, because in OnPisteContext its in back direction
                transitions.add(0, path.transition);
                // this block calculate total info about route to display it to user
                if (path.transition.connection != null) {
                    // if finish not at the end of connection
                    if (path.transition == conEnd && pend != conEnd.end) {
                        double percent = 1 - percentLeft(pend, conEnd.connection);
                        route.setEndPercent(percent);
                        route.setDuration(route.getDuration() + path.transition.getTransitionDuration() + path.transition.connection.getDuration() * percent);
                        route.setDistance(route.getDistance() + path.transition.distance + path.transition.connection.distance * percent);
                    } else // if start not at the beginning of connection
                        if (path.transition == conStart && pstart != conStart.gStart) {
                            double percent = percentLeft(pstart, conStart.connection);
                            route.setStartPercent(percent);
                            route.setDuration(route.getDuration() + path.transition.getTransitionDuration() + path.transition.connection.getDuration() * percent);
                            route.setDistance(route.getDistance() + path.transition.distance + path.transition.connection.distance * percent);
                        } else // simple add connection to route
                        {
                            route.setDistance(route.getDistance() + path.transition.getDistance());
                            route.setDuration(route.getDuration() + path.transition.getDuration());
                        }
                }
                // here take next, while dont reach tail
                path = path.parent;
            } while (path != null);
        }
        route.setTransitions(transitions);
    }

    public static GeoPoint pointOnConnection(GeoPoint current, SkiArea.Connection connection) {
        if (connection == null)
            return null;

        GeoPoint p1 = null;
        double mindist = 0;
        GeoPoint minp = null;
        for (GeoPoint p2 : connection.data.getPoints()) {
            if (p1 != null) {
                double d;
                if (GeoPoint.same(p1, p2))
                    d = fullDistance(p1, current);
                else
                    d = Navigator.getDistanceToSegment(current, p1, p2);
                if (minp == null || d < mindist) {
                    minp = p1;
                    mindist = d;
                }
            }
            p1 = p2;
        }
        if (mindist < point2lineDist)
            return minp;
        else
            return null;
    }

    public static double percentLeft(GeoPoint current, SkiArea.Connection connection) {
        if (connection.distance > 0.1)
            return connection.distanceLeft(current, true) / connection.distance; //!!!
        else
            return 1;
    }

    /////////////////////////////

    private static long addTime(long startTime, double duration) {
        return startTime == 0 ? 0 : startTime + (long) duration;
    }

    private static boolean canGoToFinish(SkiArea area, Transition transition, TreeMap<NodalPoint, ArriveInfo> arrives) {
        SkiArea.NodalPoint npnext = area.getNodalPoints().get(transition.gEnd);
        ArriveInfo ai = arrives.get(npnext);
        return (ai != null && ai.durationToFinish != Dejkstra.INFINITY);
    }

    // add all variants from this connection to neighbors
    private void nextStep(SkiArea area, OnPisteContext path, long startTime, TreeMap<NodalPoint, ArriveInfo> arrives, RiderPreferences riderPreferences) {
        SkiArea.NodalPoint np = area.getNodalPoints().get(path.transition.gEnd);
        for (SkiArea.Transition transition : np.transitionsFrom) {
            if (canGoToFinish(area, transition, arrives))
                addNext(area, path, transition, addTime(startTime, (long) path.duration), riderPreferences);
        }
    }

    // really add next step
    private boolean addNext(SkiArea area, OnPisteContext path, SkiArea.Transition transition, long time, RiderPreferences riderPreferences) {
//    FLog.d("aaa", path.transition.connection.data.name+"->"+transition.connection.data.name+"("+transition.connection.data.id+")");
        // check - if we can go this connection - some of them may be prohibited by settings
        if (canGo(transition, time, riderPreferences)) //TODO: add current time
        {
            // check if we already be here to avoid loops.
            // check transition.connection != conEnd is obsolete, but to remove it I should test algorithm again
            if (all.containsKey(transition.connection.data.id) && transition != conEnd) {
                OnPisteContext prev = all.get(transition.connection.data.id);
                double duration = path.duration + transition.getDuration();
                // check witch way to same point is better
                if (prev.duration > duration) {
                    // if better one - remove old and save current
                    sorted.remove(prev.key);
                    all.remove(transition.connection.data.id);
                    add2Sorted(area, transition, duration, path, riderPreferences);
                } else
                    // if new is worse - we dont save it to sorted, this way we dont save impasses
                    return false;
            } else {
                // here we save step, that is totally new
                double duration = path.duration + transition.getDuration();
                add2Sorted(area, transition, duration, path, riderPreferences);
            }
            return true;
        }
//    FLog.d("ggg","cantGo "+path.transition.connection.data.id+"==>"+transition.connection.data.id);
        return false;
    }

    private static String conDEM(Connection con) {
        return "" + con.data.id + " " + con.start.getAltitude() + " " + con.end.getAltitude();
    }

    private void add2Sorted(SkiArea area, Transition transition, double duration, OnPisteContext path, RiderPreferences riderPreferences) {
        double key = getComplexKey(area, transition, usages, duration, end, riderPreferences);
        while (sorted.containsKey(key))
            key += keyAdd;
        OnPisteContext p = new OnPisteContext(transition, path, duration, key);
        sorted.put(key, p);
        all.put(transition.connection.data.id, p);
    }

    // check if this connection may be used according settings
    private static boolean canGo(SkiArea.Transition transition, long time, RiderPreferences riderPreferences) {
        if (transition.connection.isGroundTransport())
            return true;

        if (transition.connection.isLift()) {
            if (!transition.connection.isOpen(time))
                return false;
            else if (transition.connection.type == Connection.Type.SkiTow && riderPreferences.isAvoidDragLifts())
                return false;
            else
                return true;
        }

        if (riderPreferences.getRiderLevel() == RiderLevel.Beginner)
            if (transition.connection.type == Connection.Type.PisteBlack || transition.connection.type == Connection.Type.SkiRoute)
                return false;
        if (riderPreferences.getRiderLevel() == RiderLevel.Middle)
            if (transition.connection.type == Connection.Type.SkiRoute)
                return false;

        return connectionInSettings(transition.connection.type);
    }

    private static boolean connectionInSettings(SkiArea.Connection.Type type) {
        return true;
    }

    ////////////////////////////////////////////////////////////////
    //DayPlanner
    ////////////////////////////////////////////////////////////////

    // time in sec
    private static OnPisteRoute gotoBestNodalPointForTime(SkiArea area, GeoPoint start, GeoPoint end, long time, RiderPreferences riderPreferences) {
        TreeMap<Double, SkiArea.NodalPoint> nodalPoints = area.getConnectionPointsByRiderPreference(riderPreferences);
        //Double[] keys = (Double [])points.keySet().toArray();
        List<GeoPoint> points = new ArrayList<>();
        for (SkiArea.NodalPoint p : nodalPoints.values()) {
            points.add(0, p.point);
        }
        return gotoPointsForTime(area, start, end, time, points, new ArrayList<GeoPoint>(), new TreeMap<Long, Connection.Usage>(), riderPreferences);
    }

    // time in sec
    private static OnPisteRoute gotoPointsForTime(SkiArea area, GeoPoint start, GeoPoint end, long time, List<GeoPoint> points, List<GeoPoint> donePoints, TreeMap<Long, Connection.Usage> usages, RiderPreferences riderPreferences) {
        OnPisteRoute res = new OnPisteRoute(area, start, end);
        Transition conCurrent = findEnter(area, start);
        if (conCurrent == null || res.getTransitions().size() <= 0)
            return res;

        GeoPoint current = start;
        for (GeoPoint p : points) {
            double home = GeoPoint.distance(p, end) * connectionDistCoeff / avgPisteSpeed;
            // next step
            OnPisteRoute route = calculateOnPisteRoute(area, 0, conCurrent, null, current, p, usages, riderPreferences);
            res.merge(route);
            Transition trans = res.getTransitions().get(res.getTransitions().size() - 1);
            current = trans.gEnd;
            conCurrent = trans;

            // time to go home
            if (res.getDuration() + home > time || Math.abs(time - home + res.getDuration()) <= arrivalPrec) {
                route = calculateOnPisteRoute(area, 0, conCurrent, null, current, end, usages, riderPreferences);
                // remove some parts
                while (time < res.getDuration() + route.getDuration()) {
                    if (res.getTransitions().size() > 1) {
                        res.removeLastTransition(usages);

                        trans = res.getTransitions().get(res.getTransitions().size() - 1);
                        route = calculateOnPisteRoute(area, 0, trans, null, trans.gEnd, end, usages, riderPreferences);
                    } else return null;  // if duration of home route > than time
                }
                res.merge(route);
                if (time - res.getDuration() < arrivalPrec)
                    return res;
            }

            donePoints.add(p); //it need to delete alredy visited points from list
        }

        //if all points from list have been visited - than we get route from greedy algorithm

    /*TODO: finsh this
     * OnPisteRoute coveredRoute = gotoBestCoveredForTime(area, current
            , Collections.singletonList(new Event(end, (long) (time - res.getDuration()), 0))
            , usages);
    mergeRoutes(res, coveredRoute, usages);*/
        return res;
    }

    //-------------- GREEDY FOR SEVERAL POINTS ------------

    private static long min(long ms) {
        return ms / 60;
    }

    /**
     * @param area    is SkiArea for routing
     * @param ievents list of pairs (GeoPoint, Time) points to be must visited in certain time
     * @return
     */

    protected static OnPisteRoute calculateBestCoveredForTimeRoute(SkiArea area, List<Event> ievents, TreeMap<Long, Connection.Usage> usages, RiderPreferences riderPreferences) {
        if (ievents == null || ievents.size() < 2)
            return new OnPisteRoute(OnPisteRoute.Error.eventsNotDefined);

        List<Event> events = new ArrayList<Event>();
        // convert to sec
        for (Event event : ievents) {
            events.add(new Event(event, 1000));
//      FLog.d("aaa", "event time"+event.getTime());
        }

        ArrayList<Transition> impass = new ArrayList<>();
        Event first = events.get(0);
        Event last = events.get(events.size() - 1);

        Transition curTransition = findEnter(area, first.getLocation());
        if (curTransition == null)
            return new OnPisteRoute(OnPisteRoute.Error.cantFindEnter);

        OnPisteRoute res = new OnPisteRoute(area, first.getLocation(), last.getLocation());
        res.setEvents(events);

        Dejkstra dejkstra = new Dejkstra(area.nodalPoints, riderPreferences);

        for (Event event : events) {
            if (event == first)
                continue;

            Transition traEnd = findExit(area, event.getLocation());
            if (traEnd == null)
                return new OnPisteRoute(OnPisteRoute.Error.cantFindExit);
            OnPisteRoute route = calculateOnPisteRouteDejkstra(area, curTransition.gStart, event.getLocation(), event.getTime(), dejkstra, false, true, true, riderPreferences);
//      OnPisteRoute route = calculateOnPisteRoute(area, first.getTime(), curTransition, event.getTransition(), curTransition.gEnd, event.getLocation(), usages);
            if (route.getError() != OnPisteRoute.Error.ok)
                return route;

            int addedCount = 0;
            if (event.getTime() > 0) {
                while (first.getTime() + res.getDuration() + route.getDuration() + arrivalPrec < event.getTime()) {
                    long time = (long) (first.getTime() + route.getDuration() + arrivalPrec);
                    Transition trans = getTransitionByRating(area, curTransition, time, usages, impass, dejkstra.arrives, riderPreferences);
                    if (trans != null) {
                        addedCount++;
                        res.add(trans, usages);
                    } else {
                        impass.add(curTransition);
                        res.removeLastTransition(usages);
                        trans = res.getTransitions().get(res.getTransitions().size() - 1);
                        addedCount--;
                    }
                    curTransition = trans;
                    time = (long) (first.getTime() + route.getDuration() + arrivalPrec);
                    route = calculateOnPisteRouteDejkstra(area, curTransition.gEnd, event.getLocation(), time, dejkstra, false, false, true, riderPreferences);
//          route = calculateOnPisteRoute(area, time, curTransition, event.getTransition(), curTransition.gEnd, event.getLocation(), usages);
                    if (route.getError() != OnPisteRoute.Error.ok) {
                        impass.add(curTransition);
                        res.removeLastTransition(usages);
                        if (res.getTransitions().size() == 0)
                            return route;
                        curTransition = res.getTransitions().get(res.getTransitions().size() - 1);
                        addedCount--;
                    }
                }

                //remove some added parts if too long journey
                while (first.getTime() + res.getDuration() + route.getDuration() - event.getTime() > latePrec) {
                    // can delete only last leg but last event is obligatory
                    if (addedCount > 0 || event == last) {
                        if (res.getTransitions().size() > 1) {
                            res.removeLastTransition(usages);

                            Transition trans = res.getTransitions().get(res.getTransitions().size() - 1);
                            long time = (long) (first.getTime() + route.getDuration() + arrivalPrec);
                            // !!! trans.conEnd
                            route = calculateOnPisteRoute(area, time, trans, traEnd, trans.gEnd, event.getLocation(), usages, riderPreferences);
                            if (route.getError() != OnPisteRoute.Error.ok)
                                return route;
                            addedCount--;
                        } else
                            return new OnPisteRoute(OnPisteRoute.Error.eventOutOfTime, last);
                    } else
                        return new OnPisteRoute(OnPisteRoute.Error.eventOutOfTime, event);
                }
            }

            if (route.getError() == OnPisteRoute.Error.ok)
                res.merge(route);
            else
                return route;

            if (event != last) {
                if (event.getDuration() == null)
                    event.setDuration(defaultEventDuration);
                curTransition = res.getTransitions().get(res.getTransitions().size() - 1);
                res.add(new Transition(event), null);
            }
            res.setDuration(res.getDuration() + event.getDuration());
            if (res.getTransitions().size() == 0)
                return new OnPisteRoute(OnPisteRoute.Error.eventOutOfTime, event);
        }

        return res;
    }

    /*------------
        Keys function of Greedy Algorithm, that return best transition
        Min key means better value
     ------------*/
    private static Transition getTransitionByRating(SkiArea area, Transition trans, long time, TreeMap<Long, Connection.Usage> usages, ArrayList<Transition> impass, TreeMap<NodalPoint, ArriveInfo> arrives, RiderPreferences riderPreferences) {
        double best = 0;
        List<Transition> results = new ArrayList<Transition>();

        SkiArea.NodalPoint np = area.getNodalPoints().get(trans.gEnd);
        for (SkiArea.Transition transition : np.transitionsFrom) {
            if (canGoToFinish(area, transition, arrives) && canGo(transition, time, riderPreferences) && !impass.contains(transition.connection)) {
                double key = 1 / getCoverKey(area, transition, usages, 0, riderPreferences);
                if (results.size() == 0 || Math.abs(best - key) < keyPrec)
                    results.add(transition);
                else if (key < best) {
                    best = key;
                    results.clear();
                    results.add(transition);
                }
            }
        }

        if (results.size() == 0)
            return null;
        else {
            // randomize close variants
            int n = (int) (results.size() * Math.random());
            //FLog.d("aaa","best="+best+" size="+results.size()+" n="+n+" get(0)"+results.get(n).connection.data.name+"("+results.get(n).connection.data.id+")");
            if (n < results.size())
                return results.get(n);
            else
                return results.get(0);
        }
    }

/*
  private static Transition getTransitionByRating(Connection connection, TreeMap<Long, Connection.Usage> usages, ArrayList<Connection> impass) {

    return null;
  }
*/

    private static double getCoverKey(SkiArea area, Transition transition, TreeMap<Long, Connection.Usage> usages, int recursionDepth, RiderPreferences riderPreferences) {
        double duration = transition.getDuration();
        double key = (duration == 0 ? 1 : 1 / duration)
                * Connection.Usage.getUsage(usages, transition.connection)
                * (transition.liftDown ? 1 * liftDownCoeff : 1)
                * getSchemePartCoeff(transition.connection.schemePart, riderPreferences)
                * getSlopeColorCoeff(transition, riderPreferences);
        if (riderPreferences.isMinimizeLifting())
            key = key * transition.connection.liftingCoeff;
        key = (key != 0) ? 1 / key : Double.MAX_VALUE / 2; // меняем на чем больше, тем лучше (для сложения нескольких трасс)

        double bestKey = 0;
        if (recursionDepth <= REURSION_DEPTH) {
            SkiArea.NodalPoint np = area.getNodalPoints().get(transition.gEnd);
            for (SkiArea.Transition trans : np.transitionsFrom) {
                double nextKey = getCoverKey(area, trans, usages, recursionDepth + 1, riderPreferences); // TODO: добавить проверку на пройденные при подсчёте ключа трассы
                if (nextKey > bestKey)
                    bestKey = nextKey;
            }
            return key + bestKey;
        }
        if (recursionDepth == 0)
            key = key / REURSION_DEPTH;
        return key;
    }


    // old key version
  /*private static double getCoverKey(Transition transition, TreeMap<Long, ConnectionRating> usages)
  {
    double duration = transition.getDuration();
    double key = ConnectionRating.getRating(usages, transition.connection)
            * (transition.liftDown ? duration * liftDownCoeff : duration)
            * getSchemePartCoeff(transition.connection.schemePart)
            * (RiderPreferences.isMinimizeLifting() ? transition.connection.liftingCoeff : 1);
    return key;
  }*/

    private static double getFastKey(Transition transition, double prevDuration, GeoPoint end) {
        double duration = transition.getDuration();
        double key = (prevDuration + (transition.liftDown ? duration * liftDownCoeff : duration) +
                GeoPoint.distance(transition.gEnd, end) / avgPisteSpeed * onPisteDistCoeff);
//      double key = (prevDuration + duration);
        return key;
    }

    //TODO: we may optimise it using matrix of coeff, or TreeMap
    private static double getSlopeColorCoeff(Transition transition, RiderPreferences riderPreferences) {
        if (riderPreferences.getRiderLevel() == RiderLevel.Beginner)
            if (transition.connection.type == Connection.Type.PisteGreen)
                return beginnerGreenCoeff;
            else if (transition.connection.type == Connection.Type.PisteBlue)
                return beginnerBlueCoeff;
            else
                return beginnerRedCoeff;

        if (riderPreferences.getRiderLevel() == RiderLevel.Middle)
            if (transition.connection.type == Connection.Type.PisteGreen)
                return middleGreenCoeff;
            else if (transition.connection.type == Connection.Type.PisteBlue)
                return middleBlueCoeff;
            else if (transition.connection.type == Connection.Type.PisteRed)
                return middleRedCoeff;
            else
                return middleBlackCoeff;

        if (riderPreferences.getRiderLevel() == RiderLevel.Expert)
            if (transition.connection.type == Connection.Type.PisteGreen)
                return expertGreenCoeff;
            else if (transition.connection.type == Connection.Type.PisteBlue)
                return expertBlueCoeff;
            else if (transition.connection.type == Connection.Type.PisteRed)
                return expertRedCoeff;
            else if (transition.connection.type == Connection.Type.PisteRed)
                return expertBlackCoeff;
            else
                return expertSkiRouteCoeff;

        return 1;
    }

    private static double getComplexKey(SkiArea area, Transition transition, TreeMap<Long, Connection.Usage> usages, double prevDuration, GeoPoint end, RiderPreferences riderPreferences) {
        if (usages == null)
            return getFastKey(transition, prevDuration, end);
        else
            return 1 / getCoverKey(area, transition, usages, 0, riderPreferences);
    }

    private static double getSchemePartCoeff(int part, RiderPreferences riderPreferences) {
        return Math.abs(part - riderPreferences.getSchemePart()) * schemePartCoeff + 1;
    }

    //////////////////////////////////////////////////////////

    private static double calcDistanceLeft(OnPisteRoute route, GeoPoint current, Transition trans) {
        double distance = 0;
        for (int i = route.getCurrentRouteItem() + 1; i < route.getTransitions().size(); i++) {
            Transition transition = route.getTransitions().get(i);
            if (transition.connection.data.name.equals(trans.connection.data.name))
                distance += transition.getDistance();
            else
                break;
        }
        if (trans.connection.isLift())
            distance += GeoPoint.distance(current, trans.gEnd);
        else {
            GeoPoint p = pointOnConnection(current, trans.connection);
            distance += trans.connection.distanceLeft(p, true); //!!!
        }
        return distance;
    }

    public Connection getNextConnection() {
        if (currentRoute == null)
            return null;

        OnPisteRoute route = (OnPisteRoute) currentRoute;
        if (route.getCurrentRouteItem() >= route.getTransitions().size() - 1)
            return null;
        return route.getTransitions().get(route.getCurrentRouteItem()).connection;
    }

}
