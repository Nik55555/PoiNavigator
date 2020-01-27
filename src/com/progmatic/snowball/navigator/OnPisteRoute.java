package com.progmatic.snowball.navigator;

import com.progmatic.snowball.navigator.SkiArea.Transition;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class OnPisteRoute extends Route {
    @Override
    public String toString() {
        return "OnPisteRoute:" +
                "\n     start(" + start +
                "),\n     end(" + end +
                "),\n     duration=" + duration +
                ",\n     distance=" + distance +
                ",\n     transitions=" + transitions;
    }

    public enum Error {
        ok(0),
        canceledByUser(1),
        cantFindEnter(2),
        cantFindExit(3),
        cantFindRoute(4),
        eventsNotDefined(5),
        eventOutOfTime(6),
        internalError(7);

        private final int value;

        Error(int intValue) {
            this.value = intValue;
        }

        public static Error fromInteger(int intValue) {
            switch (intValue) {
                case 0: {
                    return ok;
                }
                case 1: {
                    return canceledByUser;
                }
                case 2: {
                    return cantFindEnter;
                }
                case 3: {
                    return cantFindExit;
                }
                case 4: {
                    return cantFindRoute;
                }
                case 5: {
                    return eventsNotDefined;
                }
                case 6: {
                    return eventOutOfTime;
                }
                case 7: {
                    return internalError;
                }
            }
            return null;
        }

        public int toInteger() {
            return value;
        }

    }

    ;

    private List<Transition> transitions;
    private GeoPoint start;
    private GeoPoint end;
    private double startPercent;
    private double endPercent;
    private SkiArea skiArea;
    private List<Event> events = null;
    private Error error;
    private Event errorEvent;

    private double duration = 0;
    private double distance = 0;

    private double totalUp = 0;
    private double totalDown = 0;
    private double totalGreen = 0;
    private double totalBlue = 0;
    private double totalRed = 0;
    private double totalBlack = 0;
    private double totalTow = 0;
    private double totalChair = 0;
    private double totalGondola = 0;

    private int currentRouteItem = 0;

    public OnPisteRoute(SkiArea skiArea, GeoPoint start, GeoPoint end) {
        this.skiArea = skiArea;
        this.start = start;
        this.end = end;
        transitions = new ArrayList<Transition>();
        startPercent = 1;
        endPercent = 1;
        error = Error.ok;
    }

    public OnPisteRoute(SkiArea skiArea, GeoPoint start, GeoPoint end, List<Transition> path) {
        this(skiArea, start, end);
        setTransitions(path);
        calculateTotals();
    }

    public OnPisteRoute(Error error) {
        this.error = error;
    }

    public OnPisteRoute(Error error, Event errorEvent) {
        this.error = error;
        this.errorEvent = errorEvent;
    }

    public void merge(OnPisteRoute route) {
        end = route.end;
        transitions.addAll(route.getTransitions());
        duration += route.getDuration();
        distance += route.getDistance();
    }

    public void add(Transition transition, TreeMap<Long, SkiArea.Connection.Usage> usages) {
        end = transition.end;
        transitions.add(transition);
        setDuration(duration + transition.getDuration());
        setDistance(distance + transition.getDistance());
        SkiArea.Connection.Usage.incUse(usages, transition.connection, 1);
    }

    public void removeLastTransition(TreeMap<Long, SkiArea.Connection.Usage> usages) {
        Transition trans = transitions.get(transitions.size() - 1);
        transitions.remove(transitions.size() - 1);
        setDuration(duration - trans.getDuration());
        setDistance(distance - trans.getDistance());
        SkiArea.Connection.Usage.incUse(usages, trans.connection, -1);
    }

    protected void calculateTotals() {
        SkiArea.Transition startTran = null, endTran = null;
        if (transitions.size() > 0)
            startTran = transitions.get(0);
        if (transitions.size() > 1)
            endTran = transitions.get(transitions.size() - 1);
        this.distance = 0;
        for (Transition transition : transitions) {
            if (transition.connection != null) {
                double duration = transition.connection.getDuration();
                if (transition == startTran)
                    duration *= startPercent;
                if (transition == endTran)
                    duration *= endPercent;

                totalDown += transition.getTransitionDuration();
                double dist = transition.getDistance();
                distance += dist;
                if (transition.connection.isPiste()) {
                    totalDown += duration;
                    if (transition.connection.type == SkiArea.Connection.Type.PisteGreen)
                        totalGreen += dist;
                    else if (transition.connection.type == SkiArea.Connection.Type.PisteBlue)
                        totalBlue += dist;
                    else if (transition.connection.type == SkiArea.Connection.Type.PisteRed)
                        totalRed += dist;
                    else if (transition.connection.type == SkiArea.Connection.Type.PisteBlack)
                        totalBlack += dist;
                } else {
                    totalUp += duration;
                    switch (transition.connection.data.getType().realServiceGroup) {
                        case skiTow:
                            totalTow++;
                            break;
                        case chairlift:
                            totalChair++;
                            break;
                        case cableCar:
                            totalGondola++;
                            break;
                    }
                }
            }
        }
        this.duration = totalDown + totalUp;
    }


    public boolean passable() {
        return transitions.size() > 0;
    }

    public GeoPoint getStart() {
        return start;
    }

    public void setStart(GeoPoint start) {
        this.start = start;
    }

    public GeoPoint getStop() {
        return end;
    }

    public void setStop(GeoPoint stop) {
        this.end = stop;
    }

    public GeoPoint getEnd() {
        return end;
    }

    public void setEnd(GeoPoint end) {
        this.end = end;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public List<Transition> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<Transition> transitions) {
        this.transitions = transitions;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public List<GeoPoint> getPoints() {
        List<GeoPoint> points = new ArrayList<GeoPoint>();
        for (Transition transition : transitions) {
            points.add(transition.start);
            if (transition.connection != null)
                points.addAll(transition.connection.data.getPoints());
        }
        return points;
    }

    public SkiArea getSkiArea() {
        return skiArea;
    }

    public void setSkiArea(SkiArea skiArea) {
        this.skiArea = skiArea;
    }

    public double getTotalUp() {
        return totalUp;
    }

    public void setTotalUp(double totalUp) {
        this.totalUp = totalUp;
    }

    public double getTotalDown() {
        return totalDown;
    }

    public void setTotalDown(double totalDown) {
        this.totalDown = totalDown;
    }

    public double getTotalGreen() {
        return totalGreen;
    }

    public void setTotalGreen(double totalGreen) {
        this.totalGreen = totalGreen;
    }

    public double getTotalBlue() {
        return totalBlue;
    }

    public void setTotalBlue(double totalBlue) {
        this.totalBlue = totalBlue;
    }

    public double getTotalRed() {
        return totalRed;
    }

    public void setTotalRed(double totalRed) {
        this.totalRed = totalRed;
    }

    public double getTotalBlack() {
        return totalBlack;
    }

    public void setTotalBlack(double totalBlack) {
        this.totalBlack = totalBlack;
    }

    public double getTotalTow() {
        return totalTow;
    }

    public void setTotalTow(double totalTow) {
        this.totalTow = totalTow;
    }

    public double getTotalChair() {
        return totalChair;
    }

    public void setTotalChair(double totalChair) {
        this.totalChair = totalChair;
    }

    public double getTotalGondola() {
        return totalGondola;
    }

    public void setTotalGondola(double totalGondola) {
        this.totalGondola = totalGondola;
    }

    public int getCurrentRouteItem() {
        return currentRouteItem;
    }

    public void setCurrentRouteItem(int currentRouteItem) {
        this.currentRouteItem = currentRouteItem;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public double getStartPercent() {
        return startPercent;
    }

    public void setStartPercent(double startPercent) {
        this.startPercent = startPercent;
    }

    public double getEndPercent() {
        return endPercent;
    }

    public void setEndPercent(double endPercent) {
        this.endPercent = endPercent;
    }

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public Event getErrorEvent() {

        return errorEvent;
    }

    public void setErrorEvent(Event errorEvent) {
        this.errorEvent = errorEvent;
    }

}
