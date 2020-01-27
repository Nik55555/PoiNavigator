package com.progmatic.snowball.navigator;

import com.progmatic.snowball.navigator.SkiArea.NodalPoint;
import com.progmatic.snowball.navigator.SkiArea.Transition;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Dejkstra {
    public static final double INFINITY = Double.MAX_VALUE;
    
    public TreeMap<NodalPoint, ArriveInfo> arrives = new TreeMap<>();

    private TreeMap<GeoPoint, NodalPoint> nodalPoints;
    private boolean from; // параметр определяет способ применения алгоритма: время от старта или время до финиша
    private TreeMap<String, GeoPoint> currentNodes = new TreeMap<>(); // список отсортированных по расстоянию точек по ходу выбора вершин алгоритмом
    private List<GeoPoint> usedNodes = new ArrayList<>(); // список пройденных точек
    private long time;
    private RiderPreferences riderPreferences;

    public Dejkstra(TreeMap<GeoPoint, SkiArea.NodalPoint> npoints, RiderPreferences riderPreferences) {
        nodalPoints = npoints;
        this.riderPreferences = riderPreferences;
    }

    public interface CheckTransition {
        boolean checkTransition(SkiArea.Transition transition, double time, RiderPreferences riderPreferences);
    }

    public double getDuration(NodalPoint np) {
        ArriveInfo arriveInfo = arrives.get(np);
        if (arriveInfo == null)
            return INFINITY;
        if (from) {
            return arriveInfo.durationFromStart;
        } else {
            return arriveInfo.durationToFinish;
        }
    };
    
    public void setDuration(GeoPoint p, double d, Transition transition) throws Exception {
//        System.out.println("point= " + p);
        if (nodalPoints.containsKey(p)) {
            NodalPoint np = nodalPoints.get(p);
//            System.out.println("find in nodalPoints= " + np.point);
            ArriveInfo arriveInfo;
            if (arrives.containsKey(np)) {
                arriveInfo = arrives.get(np);
            } else {
                // создаём информацию о возможности доехать для этой точки
//                System.out.println("Added arriveInfo");
                arriveInfo = new ArriveInfo();
                arrives.put(np, arriveInfo);
            }
            if (from) {
                if (arriveInfo.durationFromStart > d) {
                    arriveInfo.durationFromStart = d;
                    arriveInfo.transitionFromStart = transition;
                }
            } else {
                if (arriveInfo.durationToFinish > d) {
                    arriveInfo.durationToFinish = d;
                    arriveInfo.transitionToFinish = transition;
                }
            }
        } else {
            throw new Exception("Deikstra: point " + p + " not found in nodalPoints!");
        }
    };

    private static String makeKey(double d, GeoPoint p) {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        formatter.setMinimumIntegerDigits(5);
        formatter.setMaximumIntegerDigits(5);
        formatter.setMinimumFractionDigits(3);
        formatter.setMaximumFractionDigits(3);
        String s = formatter.format(d);

        return s + "," + p.getLongitude() + "," + p.getLatitude();
    }

    // search for start point in the graph - nearest point
    public static GeoPoint findNodalPoint(TreeMap<GeoPoint, NodalPoint> nPoints, GeoPoint point) {
        GeoPoint res = null;
        double mindist = INFINITY;

        if (nPoints.values() == null)
            return res;

        if (nPoints.containsKey(point))
            return point;

        for (GeoPoint gp : nPoints.keySet()) {
            double d = GeoPoint.distance(gp, point);
            if (d < mindist) {
                mindist = d;
                res = gp;
            }
        }

        return res;
    }

    // search for start point in the graph - nearest point
    private GeoPoint findNodalPoint(GeoPoint point) {
        return findNodalPoint(nodalPoints, point);
    }

    public void CalculateDurations(GeoPoint point, boolean fromPoint, long time) throws Exception {
        CalculateDurations(point, fromPoint, 0, new CheckTransition() {
            @Override
            public boolean checkTransition(SkiArea.Transition transition, double time, RiderPreferences riderPreferences) {
                return true;
            }
        });
    }
    
    public void CalculateDurations(GeoPoint point, boolean fromPoint) throws Exception {
        CalculateDurations(point, fromPoint, 0);
    }

    public void CalculateDurations(GeoPoint point, boolean fromPoint, long time, CheckTransition check) throws Exception {
        // параметр fromPoint определяет способ применения алгоритма: время от старта или время до финиша

        from = fromPoint;
        this.time = time;

        // выставляем duration в бесконечность
        if (arrives != null) {
            if (from)
                for (ArriveInfo ai : arrives.values()) ai.durationFromStart = INFINITY;
            else
                for (ArriveInfo ai : arrives.values()) ai.durationToFinish = INFINITY;
        }

        // ищем ближайшую вершину графа и работаем от неё
        point = findNodalPoint(point);

        currentNodes.clear();
        currentNodes.put(makeKey((double) 0, point), point);
        setDuration(point, 0, null);

        usedNodes.clear();

        while (currentNodes.size() > 0) {
            Iteration(check);
        }

        // тестовый цикл формирования всех полученных маршрутов
    }

    private void Iteration(CheckTransition check) throws Exception {
        // берём минимальную по времени вершину, определяем исходящие (или входящие) из него вершины, а эту вершину удаляем из списка как пройденную
        String fk = currentNodes.firstKey();
        GeoPoint point = currentNodes.get(fk);
        currentNodes.remove(fk);
        usedNodes.add(point);

        NodalPoint np = nodalPoints.get(point);
        double npDuration = getDuration(np);

        // выбор списка транзишенов в зависимости от направления
        List<Transition> pistes = from ? np.transitionsFrom : np.transitionsTo;

        for (Transition t : pistes) {
            double duration = t.getDuration();
            if (duration <= 0 || t.gStart.equals(t.gEnd))
                continue;
            
            double sumDuration = npDuration + duration;
            if (!check.checkTransition(t, time + sumDuration, riderPreferences))
                continue;

            // вычисляем следующую вершину графа (начало или конец коннекшена)
            GeoPoint newPoint = from ? t.gEnd : t.gStart;

            if (!nodalPoints.containsKey(newPoint))
                // при нормальном раскладе такого быть не должно
                continue;

            if (usedNodes.contains(newPoint))
                continue;

            String key = makeKey(sumDuration, newPoint);
            if (!currentNodes.containsKey(key)) {
                // добавляем точку в список вершин
                currentNodes.put(key, newPoint);
            }
            // исправляем duration до начальной/конечной точки в nodalPoints если требуется
            // пишем направляющий коннекшн от старта либо до финиша
            setDuration(newPoint, sumDuration, t);
        }
    }
}