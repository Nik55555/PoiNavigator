package com.progmatic.snowball.navigator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.progmatic.snowball.navigator.Navigator.RiderLevel;
import com.progmatic.snowball.navigator.Navigator.SlopeColor;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.progmatic.snowball.utils.gpxparser.Point;
import org.apache.log4j.Logger;
import org.json.JSONObject;

public class SkiArea {

    public static void logInfo(String msg) {
        System.out.println(new Date() + ": "  + msg);
        _log.info(msg);
    }

    private boolean isFarOneOfNode(ArrayList<PairGeoPoint> nodesGraph2Graph, NodalPoint np1, NodalPoint np2, double graphDistance) {
        // проверка пар точек: одна из точек лежит достаточно далеко от существующих (в своём миниграфе)
        for (PairGeoPoint nodeG2G : nodesGraph2Graph) {
            if (GeoPoint.distance(nodeG2G.gp1, np1.point) < graphDistance &&
                GeoPoint.distance(nodeG2G.gp2, np2.point) < graphDistance)
                return false;
        }
        return true;
    }
    
    private void mergeGraphs() {
        while (merge2Graphs(Navigator.summerMaxDistanceBetweenMiniGraphs1));
        while (merge2Graphs(Navigator.summerMaxDistanceBetweenMiniGraphs2));
        while (merge2Graphs(Navigator.summerMaxDistanceBetweenMiniGraphs3));
    }

    class NearestGraphPoint2Point {
        // класс, содержащий соответствующие точки графов, подлежащие слиянию; и техническая информация
        int gpaphIndex = 0;
        double distance = Dejkstra.INFINITY;
        NodalPoint npFromGraph1 = null;
        NodalPoint npFromGraph2 = null;
    }
    
    private NearestGraphPoint2Point findNearestPointsFrom2Graphs(ArrayList<NodalPoint> graph1, ArrayList<NodalPoint> graph2, ArrayList<PairGeoPoint> nodesGraph2Graph, double graphDistance) {
        NearestGraphPoint2Point result = new NearestGraphPoint2Point();
        // ищём ближайшие точки
        // проходим по всем сочетаниям 0-го графа и остальных, и ищем ближайший граф
        for(NodalPoint np1 : graph1) {
            // проходим по всем точкам i-го графа
            for(NodalPoint np2 : graph2) {
                double dist = GeoPoint.distance(np1.point, np2.point);
                if (dist < result.distance && dist < graphDistance && isFarOneOfNode(nodesGraph2Graph, np1, np2, graphDistance)) {
                    // точки ближайшие и расстояние хотя бы одной из них 
                    // до уже существующих в её миниграфе достаточно большое
                    result.distance = dist;
                    result.npFromGraph1 = np1;
                    result.npFromGraph2 = np2;
                }
            }
        }
        
        return result;
    }
    
    void mergeGraphsByNodalPoints(ArrayList<PairGeoPoint> nodesGraph2Graph) {
        // привязка
        for (PairGeoPoint pairGP : nodesGraph2Graph) {
            snapPointTransitionsToNode(pairGP.gp2, pairGP.gp1);
        }
        
        // удаление
        for (PairGeoPoint pairGP : nodesGraph2Graph) {
            if (nodalPoints.containsKey(pairGP.gp2))
                nodalPoints.remove(pairGP.gp2);
        }
    }
    
    class PairGeoPoint {
        GeoPoint gp1;
        GeoPoint gp2;

        public PairGeoPoint(GeoPoint gp1, GeoPoint gp2) {
            this.gp1 = gp1;
            this.gp2 = gp2;
        }
    }
    
    private boolean merge2Graphs(double graphDistance) {
        ArrayList<GeoPoint> allGeoPoints = new ArrayList<>(nodalPoints.keySet());
        
        // формируем миниграфы
        ArrayList<ArrayList<NodalPoint>> miniGraphs = new ArrayList<>();
        ArrayList<PairGeoPoint> nodesGraph2Graph = new ArrayList<>(); // список точек слияния из первого миниграфа
        while (allGeoPoints.size() > 0) {
            // строим Дейкстру от и до первой точки
            Dejkstra dejkstra = new Dejkstra(nodalPoints, new RiderPreferences());
            GeoPoint mainPoint = allGeoPoints.get(0);
            try {
                dejkstra.CalculateDurations(mainPoint, true);
                dejkstra.CalculateDurations(mainPoint, false);
            } catch (Exception ex) {
            }
            
            // выявление всех точек, лежащих в миниграфе, содержащем mainNP
            ArrayList<NodalPoint> miniGraph = new ArrayList<>();
            for(NodalPoint np : nodalPoints.values()) {
                ArriveInfo arriveInfo = dejkstra.arrives.get(np);
                if (arriveInfo != null && (arriveInfo.durationFromStart < Dejkstra.INFINITY && arriveInfo.durationToFinish < Dejkstra.INFINITY)) {
                    // точка пренадлежит миниграфу
                    if (allGeoPoints.contains(np.point))
                        allGeoPoints.remove(np.point);
                    miniGraph.add(np);
                }
            }
            
            if (!miniGraph.isEmpty())
                miniGraphs.add(miniGraph);
        }
        
        // объединяем 2 миниграфа
        if (miniGraphs.size() < 2)
            return false;
        
        // ищём ближайшие точки
        // проходим по всем сочетаниям первого графа (который можно объеденить с каким-либо другим)
        // и остальных, и ищем ближайший к нему граф
        for (int m = 0, n = miniGraphs.size(); m < n; m++ ) {
            TreeMap<Double, NearestGraphPoint2Point> ngpps = new TreeMap<>();
            for(int i = m + 1, j = miniGraphs.size(); i < j; i++ ) {
                // проходим по всем точкам i-го графа
                NearestGraphPoint2Point ngpp = findNearestPointsFrom2Graphs(miniGraphs.get(m), miniGraphs.get(i), nodesGraph2Graph, graphDistance);
                if (ngpp.npFromGraph1 != null) {
                    ngpp.gpaphIndex = i;
                    ngpps.put(ngpp.distance, ngpp);
                }
            }

            if (ngpps.isEmpty())
                // нет графов, которые можно обеденить с выбранным - переходим к следующему
                continue;

            nodesGraph2Graph.add(
                    new PairGeoPoint(
                            ngpps.firstEntry().getValue().npFromGraph1.point, 
                            ngpps.firstEntry().getValue().npFromGraph2.point
                    )
            );
            int nearestGraphIndex = ngpps.firstEntry().getValue().gpaphIndex;

            // работаем по ближайшему графу и ищем все подходящие точки
            NearestGraphPoint2Point ngpp;
            do {
                ngpp = findNearestPointsFrom2Graphs(miniGraphs.get(m), miniGraphs.get(nearestGraphIndex), nodesGraph2Graph, graphDistance);
                if (ngpp.npFromGraph1 != null)
                    nodesGraph2Graph.add(new PairGeoPoint(ngpp.npFromGraph1.point, ngpp.npFromGraph2.point));
            } while (ngpp.npFromGraph1 != null);

            // слияние точек (и 2-х графов!) и завершение итерации
            mergeGraphsByNodalPoints(nodesGraph2Graph);
            
            return true;
        }
        
        // цикл завершён без слияний
        return false;
    }

    public enum Type {
        Summer, Winter
    }
    
    public SkiArea() {
    }

    public SkiArea(String layerDataJsonPathName, String poiJsonPathName) {
        try {
            List<LayerData> ldList = new Gson().fromJson(
                    Files.readString(Paths.get(layerDataJsonPathName)),
                    new TypeToken<List<LayerData>>() {}.getType()
            );
            updateByLayerData(Type.Winter, ldList);
            if (!poiJsonPathName.isEmpty()) {
                List<POI> pois = POI.getPOIFromJson(poiJsonPathName);
                POI.linkAllPOIs2NodalPoints(pois, nodalPoints);
            }
        } catch (IOException e) {
            System.out.println(e.fillInStackTrace());
        }
    }

    static Logger _log = Logger.getLogger("Graph");

    // Ski area graph item, may be lift, slope, or other transport (for the future)
    public static class Connection {

        public enum Type {
            PisteGreen, PisteBlue, PisteRed, PisteBlack, SkiRoute,
            SkiTow, ChairLift, Gondola,
            Transport
        }

        // ratings is used for greedy algorithm
        public static class Usage {

            private Connection connection; // need for future public or private rating
            private int count; // count of this connection passages

            public Usage(Connection connection) {
                this.connection = connection;
                this.count = 1;
            }

            public double getUsage() {
                return count;
            }

            public static void incUse(TreeMap<Long, Usage> usages, Connection connection, int count) {
                if (usages == null || connection == null) {
                    return;
                }

                Usage rating;
                if (usages.containsKey(connection.data.id)) {
                    rating = usages.get(connection.data.id);
                } else {
                    rating = new Usage(connection);
                    usages.put(connection.data.id, rating);
                }
                rating.count += count;
            }

            public static double getUsage(TreeMap<Long, Usage> usages, Connection connection) {
                if (usages != null && usages.containsKey(connection.data.id)) {
                    Usage rating = usages.get(connection.data.id);
                    return rating.getUsage();
                }
                return 1;
            }
        }

        public LayerData data;
        public GeoPoint start;
        public GeoPoint end;
        public Double duration; // in sec
        public double distance; // in m
        public double speed;
        public Navigator.SlopeColor slopeColor;
        public Type type;
        public boolean twoWay;
        public int schemePart = 3; // 1 of 5 sheme parts
        public double liftingCoeff;
        public long openTime; // time in ses
        public long closeTime; // time in ses

        // all available transitions from this connection to  others
//!!!    public TreeMap<Long, Transition> transitions = new TreeMap<>();
        public Connection(LayerData data, Type type) {
            List<GeoPoint> track = data.getPoints(); // TODO:
            this.data = data;
            this.type = type;
            start = track.get(0);
            end = track.get(track.size() - 1);
            distance = Navigator.calcDistance(track);
            slopeColor = type2color(type);
            int e1 = Elevation.getElevation(start);
            int e2 = Elevation.getElevation(end);

//      System.out.println(data.name +  " (" + data.id + ") e1=" + e1 + " e2=" + e2 + ", isLift=" + isLift());
            start.setAltitude(e1);
            end.setAltitude(e2);

            if (isLift()) {
                this.duration = this.distance / Navigator.avgLiftSpeed;
                if (data.description != null && data.description.startsWith("{")) {
                    try {
                        JSONObject json = new JSONObject(data.description);
                        DateFormat formatter = new SimpleDateFormat("HH:mm");
                        String str = (String) json.get("openFrom");
                        Date date = formatter.parse(str);
                        openTime = date.getTime() / 1000;
                        str = (String) json.get("openTo");
                        date = formatter.parse(str);
                        closeTime = date.getTime() / 1000;
                    } catch (Exception e) {
                    }
                }
                //check points order for piste & lift!!!

                if (e1 > e2) {
                    data.isInverted = true;
                    data.invertPoints();
                    track = data.getPoints();
                    start = track.get(0);
                    end = track.get(track.size() - 1);
                }

            }

            if (duration != null && duration > 0) {
                this.speed = distance / duration;
            }
            if (isGroundTransport() || is2WayLift()) {
                twoWay = true;
            } else if (isPiste()) {
                if (isWalk() || canTwoWay()) {
                    twoWay = true;
                }

                if (e2 > e1 && !twoWay) {
                    data.isInverted = true;
                    data.invertPoints();
                    track = data.getPoints();
                    start = track.get(0);
                    end = track.get(track.size() - 1);
                }

            }
//      if (data.id == 16878 || data.id == 16834 || data.id == 16879 || data.id == 16880) {
//        System.out.println(data.id + " start" + start + " alt=" + start.getAltitude() + ", end " + end + " alt=" + end.getAltitude());
//      }
        }

        public boolean is2WayLift() {
            if (type == Connection.Type.ChairLift || type == Connection.Type.Gondola) {
                return true; // TODO условия двустронности под вопросом
            } else {
                return false;
            }
        }

        public boolean isWalk() {
            return (data.getType().isWalk());
        }

        public boolean canTwoWay() {
            double altDiff = Math.abs(start.getAltitude() - end.getAltitude());
            boolean res = (altDiff <= Navigator.flatSurfaceAltitudeDifference);
            /*
      double catCoeff = distance > 0 ? altDiff/distance : altDiff;
      boolean res = altDiff <= Navigator.criticalOneStepAltDiff || 
        (altDiff <= Navigator.flatSurfaceAltitudeDifference && catCoeff <= Navigator.flatSurfaceCathetusDiv);
      logInfo("name=" + this.data.name + " (" + this.data.id + ") res=" + res + " catCoeff=" + catCoeff + " distance=" + distance + " altDiff=" + altDiff);
             */
            return res;
        }

        public boolean isGroundTransport() {
            if (type == Connection.Type.Transport) {
                return true;
            } else {
                return false;
            }
        }

        public boolean isLift() {
            return (data.getType().isLift());
        }

        public boolean isTransport() {
            if (type == Connection.Type.ChairLift || type == Connection.Type.Gondola
                    || type == Connection.Type.SkiTow || type == Connection.Type.Transport) {
                return true;
            } else {
                return false;
            }
        }

        public boolean isPiste() {
            return (data.getType().isPiste());
        }

        public boolean isOpen(long time) {
            if (time != 0 && openTime != 0 && closeTime != 0) {
                return time > openTime && time < closeTime;
            } else {
                return true; //TODO: check online open-close
            }
        }

        public static Transition createTransition(Connection con, GeoPoint gStart, GeoPoint gEnd) {
            return new Transition(con, con.start, con.end, gStart, gEnd, Navigator.getSlopeColor(con.start, con.end));
        }

        public static Transition createTransitionBack(Connection con, GeoPoint gStart, GeoPoint gEnd) {
            return new Transition(con, con.end, con.start, gStart, gEnd, Navigator.getSlopeColor(con.end, con.start));
        }

        public double getDuration() {
            double d;
            // this duration in future we will get from database
            if (duration != null) {
                d = duration;
            } else // but now we calc it approximately
            {
                if (isPiste()) {
                    d = Navigator.calcDuration(RiderLevel.Middle, slopeColor, distance);
                } else {
                    d = Navigator.calcDuration(type, distance);
                }
            }
            return d;
        }

        public static Navigator.SlopeColor type2color(Type type) {
            if (type == Type.PisteGreen) {
                return Navigator.SlopeColor.Green;
            }
            if (type == Type.PisteBlue) {
                return Navigator.SlopeColor.Blue;
            }
            if (type == Type.PisteRed) {
                return Navigator.SlopeColor.Red;
            }
            if (type == Type.PisteBlack) {
                return Navigator.SlopeColor.Black;
            }
            return SlopeColor.Yellow;
        }

        public double distanceLeft(GeoPoint current, boolean isReverse) {
            if (current == null) {
                return Dejkstra.INFINITY;
            }

            double res;

            if (isLift()) {
                double distToEnd = GeoPoint.distance(current, end);
                double distToStart = GeoPoint.distance(current, start);
                if (distance + Navigator.deviation2 < distToEnd + distToStart) // выехали за пределы коннекшена
                {
                    res = 0;
                } else {
                    res = (!isReverse) ? distToEnd : distToStart;
                }
            } else {
                GeoPoint p = OnPisteNavigator.pointOnConnection(current, this);
                if (p != null) {
                    res = calcDistanceLeft(p, isReverse);
                } else {
                    res = (!isReverse) ? GeoPoint.distance(current, end) : GeoPoint.distance(current, start);
                }
            }
            return res;
        }

        public double calcDistanceLeft(GeoPoint current, boolean isReverse) {
            if (this == null || !isPiste()) {
                return 0;
            }
            double res = 0;
            GeoPoint p1 = null;
            for (GeoPoint p2 : data.getPoints()) {
                if (GeoPoint.same(current, p2)) {
                    break;
                }
                if (p1 != null) {
                    res += GeoPoint.distance(p1, p2);
                }
                p1 = p2;
            }

            if (!isReverse) // нормальное направление
            {
                return distance - res;
            } else // обратное направление
            {
                return res;
            }
        }

        /*
    public double calcDistanceLeft(GeoPoint current)
    {
      if (this == null || !isPiste())
        return 0;
      double res = 0;
      GeoPoint p1 = null;
      for (GeoPoint p2 : data.getPoints())
      {
        if (p1 != null)
        {
          res +=   GeoPoint.distance(p1, p2);
          if (GeoPoint.same(current, p2))
            break;
        }
        p1 = p2;
      }
      return distance - res;
    }
         */
        public boolean isOpenOnTime(long time) {
            return true;
        }

        public boolean isOpenOnLine() {
            return true;
        }

    }

    private boolean checkTransition(SkiArea.Type skiAreaType, GeoPoint gp, GeoPoint gp1) {
        double d = Navigator.fullDistance(gp, gp1);
        double dist = skiAreaType == SkiArea.Type.Summer ? Navigator.summerGraphDistance : Navigator.inPisteOffPisteDistance;
        boolean res = Navigator.canGo(gp, gp1, d) && (d < dist || connectionPointExists(gp, gp1));
//    if (gp.getLatitude() == 46.4632942414554 || gp1.getLatitude() == 46.4632942414554 || gp.getLatitude() == 46.4632947033446 || gp1.getLatitude() == 46.4632947033446)
//        logInfo("Point1=" + gp + " Point2=" + gp1 + " res=" + res + " fullDistance=" + d + " altDiff=" + Math.abs(gp.getAltitude() - gp1.getAltitude()));
        return res;
    }

    private boolean checkLocalMergeTransition(GeoPoint gp, GeoPoint gp1) {
        double d = Navigator.fullDistance(gp, gp1);
        boolean res = Navigator.canGo(gp, gp1, d) && (d < Navigator.localMergeDistance);
        return res;
    }

    // this class links connection toeach other, because between them sometimes we found holes about 50 meters
    public static class Transition {

        public double distance;
        public double duration;
        public GeoPoint start;
        public GeoPoint end;

        public Connection connection;
        public Navigator.SlopeColor slopeColor;
        public Event event = null;
        boolean liftDown;

        public GeoPoint gStart; // вершина графа, соответствующая start
        public GeoPoint gEnd; // вершина графа, соответствующая end

        public Transition(Connection con, GeoPoint start, GeoPoint end, GeoPoint gStart, GeoPoint gEnd, Navigator.SlopeColor slopeColor) {
            this.start = start;
            this.end = end;

            // this code should be in Connection constructor
//      if (con.getDuration() == 0)
//        con.duration = Navigator.calcDuration(riderPreferences.getRiderLevel(), slopeColor, distance);
            this.connection = con;

            setGraphPoints(gStart, gEnd);

            this.slopeColor = slopeColor;

            this.liftDown = this.connection.is2WayLift() && this.connection.start == this.end;
        }

        public Transition(Connection con, GeoPoint start, GeoPoint end, Navigator.SlopeColor slopeColor) {
            this(con, start, end, null, null, slopeColor);
        }

        public Transition(Connection con, GeoPoint start, GeoPoint end) {
            this(con, start, end, Navigator.getSlopeColor(con.start, con.end));
        }

        public Transition(Event event) {
            this.start = event.getLocation();
            this.end = event.getLocation();
            this.gStart = null;
            this.gEnd = null;
            this.connection = null;
            this.distance = 0;
            this.duration = event.getDuration();
            this.event = event;
        }

        public boolean isReverse() {
            return connection != null && this.start.equals(this.connection.end);
        }

        public double getDuration() {
            return getTransitionDuration() + (connection == null ? 0 : connection.getDuration());
        }

        public double getTransitionDuration() {
            return duration;
        }

        public double getDistance() {
            return distance + (connection == null ? 0 : connection.distance);
        }

        public void calcDistance() {
            // вычисляем расстояние от вершины вершин графа до концов коннекшена (start и end)
            if (this.gStart != null && this.gEnd != null) {
                double distance = 0;

                if ((start != null) && (!start.equals(gStart))) {
                    distance += Navigator.fullDistance(start, gStart);
                }
                if ((end != null) && (!end.equals(gEnd))) {
                    distance += Navigator.fullDistance(end, gEnd);
                }

                this.duration = Navigator.calcDuration(RiderLevel.Middle, slopeColor, distance);
            }
        }

        public void setGraphPoints(GeoPoint gStart, GeoPoint gEnd) {
            // точки соответствующих вершин графа
            this.gStart = gStart;
            this.gEnd = gEnd;

            calcDistance();
        }

        public void setGraphStart(GeoPoint gStart) {
            // точки соответствующих вершин графа
            this.gStart = gStart;

            calcDistance();
        }

        public void setGraphEnd(GeoPoint gEnd) {
            // точки соответствующих вершин графа
            this.gEnd = gEnd;

            calcDistance();
        }

        long getTransitionId() {
            return connection != null ? (isReverse() ? -connection.data.id : connection.data.id) : 0;
        }

        @Override
        public String toString() {
            return Long.toString(getTransitionId());
        }
    }

    // вершины графа
    public static class NodalPoint implements Comparable<NodalPoint> {
        public GeoPoint point;
        public int greenPistes;
        public int bluePistes;
        public int redPistes;
        public int blackPistes;
        public int skiRoutes;

        public List<Transition> transitionsFrom = new ArrayList<>();
        public List<Transition> transitionsTo = new ArrayList<>();
        
        public String stringTransitionsFrom = "";
        public String stringTransitionsTo = "";

        public List<POI> poiList = new ArrayList<>(); // точки интереса, привязанные к NodalPoint

        public NodalPoint(GeoPoint point) {
            this.point = point;
            greenPistes = 0;
            bluePistes = 0;
            redPistes = 0;
            blackPistes = 0;
            skiRoutes = 0;
        }
        
        public void calculateTransitionStrings() {
            stringTransitionsFrom = "";
            for (Transition transition : transitionsFrom) {
                stringTransitionsFrom += stringTransitionsFrom.isEmpty() ? transition.getTransitionId() : "," + transition.getTransitionId();
            }
            stringTransitionsFrom = "{\"ids\":[" + stringTransitionsFrom + "]}";
            
            stringTransitionsTo = "";
            for (Transition transition : transitionsTo) {
                stringTransitionsTo += stringTransitionsTo.isEmpty() ? transition.getTransitionId() : "," + transition.getTransitionId();
            }
            stringTransitionsTo = "{\"ids\":[" + stringTransitionsTo + "]}";
        }

        public void addTransitionsFrom(Transition t) {
            if (t == null) {
                return;
            }
            if ((!transitionsFrom.contains(t))) {
                Connection.Type type = t.connection.type;
                if (type == Connection.Type.PisteGreen) {
                    greenPistes++;
                }
                if (type == Connection.Type.PisteBlue) {
                    bluePistes++;
                }
                if (type == Connection.Type.PisteRed) {
                    redPistes++;
                }
                if (type == Connection.Type.PisteBlack) {
                    blackPistes++;
                }
                if (type == Connection.Type.SkiRoute) {
                    skiRoutes++;
                }

                transitionsFrom.add(t);
            }
        }

        public void addTransitionsTo(Transition t) {
            if (t == null) {
                return;
            }
            if ((!transitionsTo.contains(t))) {
                transitionsTo.add(t);
            }
        }

        public double getKeyByRiderPreference(RiderPreferences riderPreferences) {
            double res = 0;
            if (riderPreferences.getRiderLevel() == RiderLevel.Beginner) {
                res += greenPistes + bluePistes;
            } else if (riderPreferences.getRiderLevel() == RiderLevel.Middle) {
                res += redPistes;
                if (!riderPreferences.isNoGreenPiste()) {
                    res += (double) greenPistes / 3;
                }
                if (!riderPreferences.isNoBluePiste()) {
                    res += (double) bluePistes / 2;
                }
            } else if (riderPreferences.getRiderLevel() == RiderLevel.Expert) {
                res += blackPistes;
                if (!riderPreferences.isNoGreenPiste()) {
                    res += (double) greenPistes / 4;
                }
                if (!riderPreferences.isNoBluePiste()) {
                    res += (double) bluePistes / 3;
                }
                if (!riderPreferences.isNoRedPiste()) {
                    res += (double) redPistes / 1.5;
                }
            } else if (riderPreferences.getRiderLevel() == RiderLevel.FreeRider) {
                res += skiRoutes;
                if (!riderPreferences.isNoGreenPiste()) {
                    res += (double) greenPistes / 5;
                }
                if (!riderPreferences.isNoBluePiste()) {
                    res += (double) bluePistes / 4;
                }
                if (!riderPreferences.isNoRedPiste()) {
                    res += (double) redPistes / 2;
                }
                if (!riderPreferences.isNoBlackPiste()) {
                    res += (double) blackPistes / 1.5;
                }
            }
            return res;
        }

        @Override
        public int compareTo(NodalPoint arg0) {
            return this.point.compareTo(arg0.point);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            if (obj.getClass() != getClass()) {
                return false;
            }
            final NodalPoint rhs = (NodalPoint) obj;
            return (this.point.equals(rhs.point));
        }

        @Override
        public int hashCode() {
            return this.point.hashCode();
        }

        @Override
        public String toString() {
            return "NodalPoint(" + point + ")";
        }
    }

    // класс для получения NodalPoint из json
    public static class NodalPointJson {
        public double lat;
        public double lon;
        public long altitude;
        public String transitionsFrom;
        public String transitionsTo;

    }

    public Transition transitionToFinish(GeoPoint point, TreeMap<NodalPoint, ArriveInfo> arriveInfo) {
        if (nodalPoints == null || nodalPoints.isEmpty()) {
            return null;
        }
        NodalPoint np = nodalPoints.get(point);
        ArriveInfo ai;
        if (np != null) {
            ai = arriveInfo.get(np);
        } else {
            return null;
        }
        if (ai != null) {
            return ai.transitionToFinish;
        } else {
            return null;
        }
    }

    public Transition transitionFromStart(GeoPoint point, TreeMap<NodalPoint, ArriveInfo> arriveInfo) {
        if (nodalPoints == null || nodalPoints.isEmpty()) {
            return null;
        }
        NodalPoint np = nodalPoints.get(point);
        ArriveInfo ai;
        if (np != null) {
            ai = arriveInfo.get(np);
        } else {
            return null;
        }
        if (ai != null) {
            return ai.transitionFromStart;
        } else {
            return null;
        }
    }

    // Ski area members
    public double x1, y1, x2, y2;
    public TreeMap<Long, Transition> transitions = new TreeMap<>();
    public List<LayerData> layerRecords;
    private List<GeoPoint> points;
    public List<GeoPoint> connectionPoints50 = null;
    public List<GeoPoint> connectionPoints100 = null;
    public List<GeoPoint> connectionPoints150 = null;
    public List<GeoPoint> noDivisionPoints = null;
    public List<GeoPoint> localMergePoints = null;
    private boolean prepared = false;
    private volatile boolean useless;
    protected TreeMap<GeoPoint, NodalPoint> nodalPoints = new TreeMap<GeoPoint, NodalPoint>();
    public double sumDistance = 0;
    public double sumLiftDistance = 0;
    public double sumPisteDistance = 0;
    
    public TreeMap<GeoPoint, NodalPoint> getNodalPoints() {
        return nodalPoints;
    }

    public void setNodalPoints(TreeMap<GeoPoint, NodalPoint> nodalPoints) {
        this.nodalPoints = nodalPoints;
    }

    private void snapPointTransitionsToNode(GeoPoint point, GeoPoint nodePoint) {
        NodalPoint basePoint = nodalPoints.get(nodePoint);

        for (Transition transition : nodalPoints.get(point).transitionsFrom) {
            // проходим по всем трассам и меняем "ближайшую" точку на отобранную (ссылка на узел графа в gStart)
            basePoint.addTransitionsFrom(transition);
            transition.setGraphStart(nodePoint);
//      System.out.println("      t_from    " + transition.connection.data.name + " (" + transition.connection.data.id + ") gStart " + transition.gStart + ", gEnd " + transition.gEnd);
        }
        for (Transition transition : nodalPoints.get(point).transitionsTo) {
            // проходим по всем трассам и меняем "ближайшую" точку на отобранную (ссылка на узел графа в gEnd)
            basePoint.addTransitionsTo(transition);
            transition.setGraphEnd(nodePoint);
//      System.out.println("      t_to      " + transition.connection.data.name + " (" + transition.connection.data.id + ") gStart " + transition.gStart + ", gEnd " + transition.gEnd);
        }
    }
    
    enum CheckTransitionType {
        Normal,
        LocalMerge
    }
    
    private void mergeNodalPoints(SkiArea.Type skiAreaType, TreeMap<GeoPoint,SkiArea.NodalPoint> nodalPoints, CheckTransitionType type) {
        // объединяем близлежащие узлы в один
        // формируем список с указанием близлежащих точек
        // строим список узлов с количеством близлежащих узлов
        logInfo("START mergeNodalPoints() for " + type);
        TreeMap<GeoPoint, NodeNear> nn = new TreeMap<>();
        int maxNear = 0;
        logInfo("   START make NodeNear List");
        for (GeoPoint gp : nodalPoints.keySet()) {
            NodeNear n = new NodeNear(nodalPoints.get(gp).point);
            for (GeoPoint gp1 : nodalPoints.keySet()) {
                if (!gp.equals(gp1)) {
//          System.out.println("gp=" + gp + " gp1=" + gp1);
                    if ((type == CheckTransitionType.Normal && checkTransition(skiAreaType, gp, gp1)) ||
                        (type == CheckTransitionType.LocalMerge && checkLocalMergeTransition(gp, gp1))) {
//            System.out.println("checked");
                        n.nearest.add(gp1);
                        n.pointsNear++;
                    }
                }
            }
            maxNear = Math.max(n.pointsNear, maxNear);

            // добавляем только точки с близлежащими узлами
            if (n.pointsNear > 0) {
                if (!nn.containsKey(n.point)) {
                    nn.put(n.point, n);
                }
            }
        }
        logInfo("   END make NodeNear List");

        List<GeoPoint> toDelete = new ArrayList<>();

        // повторяем цикл, пока не переберём все повторяющиеся точки
        logInfo("   START build Nodes");
        while (true) {
            // ищем узел с максимальным числом ближайших точек
            GeoPoint maxP = null;
            int maxNearest = 0;
            for (NodeNear n : nn.values()) {
                if (n.pointsNear > 0) {
                    if (maxNearest < n.pointsNear) {
                        maxNearest = n.pointsNear;
                        maxP = n.point;
                    }
                }
            }

            if (maxP == null) {
                break;
            }

            List<GeoPoint> setOfPoints = new ArrayList<>(); // "суммарное множество"  - множество ближайших точек вокруг узловой
            // с вложениями ближайших от ближайших (рекурсивно)
            List<GeoPoint> setNodePoints = new ArrayList<>(); // список узловых точек из "суммарного множества"
            List<Transition> setTransitionsBack = new ArrayList<>(); // список узловых точек из "суммарного множества"

            // меняем точки на выбранную (алгоритм усложнён для разрешения ситуации с коннекшенами, попадающими в зону узла графа двумя концами)
            NodeNear nnear = nn.get(maxP);
            NodalPoint basePoint;

            // формируем "суммарное множество"
            nnear.appendSetOfPoint(setOfPoints, nn);
            if (!setOfPoints.contains(nnear.point)) {
                setOfPoints.add(nnear.point);
            }

            // выбираем из суммарного множества точки, являющиеся концами одного транзишена
            // каждая такая точка будет узлом графа
            // проходим по всем точкам множества и прикрепляем каждую к ближайшему узлу графа
            for (GeoPoint point : setOfPoints) {
                for (Transition tr : nodalPoints.get(point).transitionsFrom) {
                    if (!tr.gEnd.equals(point) && setOfPoints.contains(tr.gEnd)) {
                        // точки начала и окончания транзишена (пока ещё совпадает с коннекшеном) попадают в "суммарное множество"
                        if (!setNodePoints.contains(point)) {
                            setNodePoints.add(point);
                        }
                        if (!setNodePoints.contains(tr.gEnd)) {
                            setNodePoints.add(tr.gEnd);
                        }
                        // формируем список "суммарных" транзишенов
                        // и отмечаем такой транзишен двусторонним (если перепад высот на нём не более Navigator.flatSurfaceAltitudeDifference)
                        if (!setTransitionsBack.contains(tr) && !tr.isReverse()) {
                            setTransitionsBack.add(tr);
                        }
                    }
                }
            }

            //logInfo("Make twoway transitions: setTransitionsBack.size=" + setTransitionsBack.size());
            // создаём и сохраняем транзишены, отмеченные как двусторонние (и если возможна двунаправленность)
            for (Transition tr : setTransitionsBack) {
                if (transitions.containsKey(-tr.connection.data.id) /*|| !tr.connection.canTwoWay()*/) {
                    continue;
                }
                Transition t = addReverseTransition(tr);

                nodalPoints.get(tr.gStart).addTransitionsTo(t);
                nodalPoints.get(tr.gEnd).addTransitionsFrom(t);
            }

            if (setNodePoints.isEmpty()) {
                //logInfo("Make node = " + maxP + ", setOfPoints.size=" + setOfPoints.size());
                // таких точек нет, работаем только по maxP
                for (GeoPoint v : setOfPoints) {
                    if (v.equals(nnear.point)) {
                        continue;
                    }

                    // привязываем точку "суммарного множества" к ближайшему найденному узлу
                    for (Transition transition : nodalPoints.get(v).transitionsFrom) {
                        // проходим по всем трассам и меняем "ближайшую" точку на отобранную (ссылка на узел графа в gStart)
                        transition.setGraphStart(maxP);
                    }
                    for (Transition transition : nodalPoints.get(v).transitionsTo) {
                        // проходим по всем трассам и меняем "ближайшую" точку на отобранную (ссылка на узел графа в gEnd)
                        transition.setGraphEnd(maxP);
                    }

                    // удаляем близлежащие точки из списка проверки
                    if (nn.containsKey(v)) {
                        nn.remove(v);
                    }

                    toDelete.add(v);

                    basePoint = nodalPoints.get(maxP);
                    // удаляем близлежащие точки из списка узлов, предварительно перенеся исходящие трассы в узловую точку
                    for (Transition transition : nodalPoints.get(v).transitionsFrom) {
                        basePoint.addTransitionsFrom(transition);
                    }
                    for (Transition transition : nodalPoints.get(v).transitionsTo) {
                        basePoint.addTransitionsTo(transition);
                    }
                }
                if (nn.containsKey(maxP)) {
                    nn.remove(maxP);
                }
            } else {
                //logInfo("Make nodes by setNodePoints, size=" + setNodePoints.size() + ", setOfPoints.size=" + setOfPoints.size());
                // такие точки есть, ищем минимальное расстояние от каждой точки "суммарного множества" до новой узловой
                for (GeoPoint point : setOfPoints) {
                    // удаляем близлежащие точки из списка проверки
                    if (nn.containsKey(point)) {
                        nn.remove(point);
                    }

                    if (setNodePoints.contains(point)) // это одна из узловых точек!
                    {
                        continue;
                    }

                    // и из списка узлов (если не узел)
                    toDelete.add(point);

                    GeoPoint minDistanceNode = null;
                    double minDistance = Dejkstra.INFINITY;
                    for (GeoPoint node : setNodePoints) {
                        double d = GeoPoint.distance(point, node);
                        if (d < minDistance) {
                            minDistance = d;
                            minDistanceNode = node;
                        }
                    }

//          System.out.println("point=" + point);
                    // привязываем точку "суммарного множества" к ближайшему найденному узлу
                    //logInfo("Snap point " + point + " to node " + minDistanceNode);
                    snapPointTransitionsToNode(point, minDistanceNode);
                }

                /*        
        for (GeoPoint p : setNodePoints) {
            System.out.println("NodalPoint=" + p);
            for (Transition tr : nodalPoints.get(p).transitionsFrom) {
                System.out.println("      T_FROM    " + tr.connection.data.name + " (" + tr.connection.data.id + ") gStart " + tr.gStart + ", gEnd " + tr.gEnd);
            }
            for (Transition tr : nodalPoints.get(p).transitionsTo) {
                System.out.println("      T_TO    " + tr.connection.data.name + " (" + tr.connection.data.id + ") gStart " + tr.gStart + ", gEnd " + tr.gEnd);
            }
        }
                 */
                // объединяем все ближайшие точки "суммарных" транзишенов в оптимальную (если больше одного)
                //logInfo("setTransitionsBack.size=" + setTransitionsBack.size());
                if (setTransitionsBack.size() > 1) {
                    double minDist = Dejkstra.INFINITY / 3;
                    List<GeoPoint> connectedPoints = new ArrayList<>();

                    // находим центральную точку
                    GeoPoint centerPoint = setTransitionsBack.get(0).gStart;
                    for (Transition tr : setTransitionsBack) {
                        double start_d = 0;
                        double end_d = 0;
                        for (Transition tr1 : setTransitionsBack) {
                            if (tr1 == tr) {
                                continue;
                            }
                            start_d += Math.min(GeoPoint.distance(tr.gStart, tr1.gStart), GeoPoint.distance(tr.gStart, tr1.gEnd));
                            end_d += Math.min(GeoPoint.distance(tr.gEnd, tr1.gStart), GeoPoint.distance(tr.gEnd, tr1.gEnd));
                        }
                        if (end_d > start_d) {
                            if (minDist > start_d) {
                                minDist = start_d;
                                centerPoint = tr.gStart;
                            }
                        } else if (minDist > end_d) {
                            minDist = end_d;
                            centerPoint = tr.gEnd;
                        }
                    }

//            System.out.println("CENTER_POINT=" + centerPoint);
                    //logInfo("CENTER_POINT=" + centerPoint);

                    // формируем список ближайших к центральной точке концов остальных отрезков
                    for (Transition tr : setTransitionsBack) {
                        if (tr.gEnd.equals(centerPoint) || tr.gStart.equals(centerPoint)) {
                            continue;
                        }

                        double start_d = GeoPoint.distance(centerPoint, tr.gStart);
                        double end_d = GeoPoint.distance(centerPoint, tr.gEnd);
                        if (start_d < end_d) {
                            if (!connectedPoints.contains(tr.gStart)) {
                                connectedPoints.add(tr.gStart);
                            }
                        } else if (!connectedPoints.contains(tr.gEnd)) {
                            connectedPoints.add(tr.gEnd);
                        }
                    }

                    for (GeoPoint cp : connectedPoints) {
                        if (centerPoint == null) {
                            centerPoint = cp;
                        } else {
                            snapPointTransitionsToNode(cp, centerPoint);

                            // удаляем из списка узлов, nodalPoints и списка проверки
                            setNodePoints.remove(cp);
                            toDelete.add(cp);
                            if (nn.containsKey(cp)) {
                                nn.remove(cp);
                            }
                        }
                    }
                    
                    TreeMap<GeoPoint, GeoPoint> npMerge = new TreeMap<>();
                    // слияние узлов графа, являющихся концами отрезков,
                    // вложенных в зону узла centerPoint двумя концами
                    for (int i = 0, s = setTransitionsBack.size(); i < s; i++) {
                        Transition tr = setTransitionsBack.get(i);
                        GeoPoint other = tr.gStart.equals(centerPoint) ? tr.gEnd : tr.gStart;
                        
                        if (centerPoint.equals(other))
                            continue;
                        
                        for (int j = i + 1; j < s; j++) {
                            Transition tr1 = setTransitionsBack.get(j);
                            if (tr1.gStart.equals(centerPoint)) {
                                double dist = GeoPoint.distance(tr1.gEnd, other);
                                if (dist < 7) {
                                    // что на что менять
                                    npMerge.put(tr1.gEnd, other);
                                    // слияние узлов
                                }
                            } else {
                                double dist = GeoPoint.distance(tr1.gStart, other);
                                if (dist < 7) {
                                    // что на что менять
                                    npMerge.put(tr1.gStart, other);
                                    // слияние узлов
                                }
                            }
                        }
                    }
                    
                    for (Map.Entry<GeoPoint, GeoPoint> entry : npMerge.entrySet()) {
                        entry.getKey();
                        GeoPoint cp = entry.getKey();
                        snapPointTransitionsToNode(cp, entry.getValue());

                        // удаляем из списка узлов, nodalPoints и списка проверки
                        setNodePoints.remove(cp);
                        toDelete.add(cp);
                        if (nn.containsKey(cp)) {
                            nn.remove(cp);
                        }
                    }
                    
                    // удаляем обратные транзишены, если объединение одним узлом, позволяет доехать
                    // от центрального узла до объединённого и обратно по "прямым" тразишенам
                    NodalPoint npCenter = nodalPoints.get(centerPoint);
                    
                    for (GeoPoint mergedGeoPoint : npMerge.values()) {
                        boolean canGoToMerged = false;
                        for (Transition tr : npCenter.transitionsFrom) {
                            if (tr.isReverse())
                                continue;
                            if (tr.gEnd.equals(mergedGeoPoint)) {
                                canGoToMerged = true;
                                break;
                            }
                        }
                        
                        boolean canGoToCenter = false;
                        NodalPoint npMerged = nodalPoints.get(mergedGeoPoint);
                        for (Transition tr : npMerged.transitionsFrom) {
                            if (tr.isReverse())
                                continue;
                            if (tr.gEnd.equals(centerPoint)) {
                                canGoToCenter = true;
                                break;
                            }
                        }
                        
                        if (canGoToCenter && canGoToMerged) {
                            // доехать по "прямым" транзишенам можно - удаляем обратные
                            List<Transition> reverseTransitions = new ArrayList<>();
                            for (Transition tr : npCenter.transitionsFrom) {
                                if (!tr.isReverse())
                                    continue;
                                if (!tr.connection.twoWay && tr.gEnd.equals(mergedGeoPoint)) {
                                    reverseTransitions.add(tr);
                                    break;
                                }
                            }
                            for (Transition revTra : reverseTransitions) {
                                // удаляем из узлов графа
                                npCenter.transitionsFrom.remove(revTra);
                                npMerged.transitionsTo.remove(revTra);
                                // удаляем из зоны катания
                                transitions.remove(-revTra.connection.data.id);
                            }
                            
                            reverseTransitions.clear();
                            for (Transition tr : npMerged.transitionsFrom) {
                                if (!tr.isReverse())
                                    continue;
                                if (!tr.connection.twoWay && tr.gEnd.equals(centerPoint)) {
                                    reverseTransitions.add(tr);
                                    break;
                                }
                            }
                            for (Transition revTra : reverseTransitions) {
                                // удаляем из узлов графа
                                npMerged.transitionsFrom.remove(revTra);
                                npCenter.transitionsTo.remove(revTra);
                                // удаляем из зоны катания
                                transitions.remove(-revTra.connection.data.id);
                            }
                        }
                    }
                }

                /*        
        for (GeoPoint p : setNodePoints) {
            System.out.println("NodalPoint=" + p);
            for (Transition tr : nodalPoints.get(p).transitionsFrom) {
                System.out.println("    after  T_FROM    " + tr.connection.data.name + " (" + tr.connection.data.id + ") gStart " + tr.gStart + ", gEnd " + tr.gEnd);
            }
            for (Transition tr : nodalPoints.get(p).transitionsTo) {
                System.out.println("    after  T_TO    " + tr.connection.data.name + " (" + tr.connection.data.id + ") gStart " + tr.gStart + ", gEnd " + tr.gEnd);
            }
        }
                 */
            }
        }
        logInfo("   END build Nodes");

        logInfo("Remove nodal points");

        for (GeoPoint p : toDelete) {
            if (nodalPoints.containsKey(p)) {
                nodalPoints.remove(p);
            }
        }
        logInfo("END mergeNodalPoints() for " + type);
    }

    private void prepare(SkiArea.Type skiAreaType, TreeMap<Long, Connection> connections) {
        logInfo("Start prepare");

        double minX = 1000, minY = 1000, maxX = -1000, maxY = -1000;
        sumLiftDistance = 0;
        sumPisteDistance = 0;
        for (Connection con : connections.values()) {
            if (con.distance <= GeoPoint.std_prec) {
                continue;
            }
            
            if (con.isLift())
                sumLiftDistance += con.distance;
            else
                sumPisteDistance += con.distance;
            
            // формируем транзишн совпадающий с коннекшеном
            addTransition(con);
            if (con.twoWay) {
                addTransition(con, true);
            }
//      if (con.data.name.contains("Horberg"))
//        System.out.println("Коннекшн=" + con.data.name + ", Id=" + con.data.id + ", start=" + con.start + ", end=" + con.end + ", twoWay=" + con.twoWay);
//            logInfo("Con=" + con.data.name + ", Id=" + con.data.id + ", start=" + con.start + ", end=" + con.end + ", twoWay=" + con.twoWay);

            minX = Math.min(minX, con.data.getX1());
            minY = Math.min(minY, con.data.getY1());
            maxX = Math.max(maxX, con.data.getX2());
            maxY = Math.max(maxY, con.data.getY2());
        }
        x1 = minX;
        y1 = minY;
        x2 = maxX;
        y2 = maxY;
        
        sumDistance = sumLiftDistance + sumPisteDistance;

        if (transitions.isEmpty()) // зона катания пустая
            return;

        for (Transition transition : transitions.values()) {
            // формируем узлы графа
            NodalPoint np;

            if (!nodalPoints.containsKey(transition.start)) {
                np = new NodalPoint(transition.start);
                nodalPoints.put(transition.start, np);
            } else {
                np = nodalPoints.get(transition.start);
            }

            np.addTransitionsFrom(transition);

            if (!nodalPoints.containsKey(transition.end)) {
                np = new NodalPoint(transition.end);
                nodalPoints.put(transition.end, np);
            } else {
                np = nodalPoints.get(transition.end);
            }

            np.addTransitionsTo(transition);
        }
        
        // формируем точки локального слияния
        TreeMap<GeoPoint,SkiArea.NodalPoint> nodalPointsLM = new TreeMap<>();
        for (GeoPoint lmGeoPoint : localMergePoints) {
            for (GeoPoint np : nodalPoints.keySet()) {
                if (Navigator.fullDistance(lmGeoPoint, np) < Navigator.localMergeDistance) {
                    nodalPointsLM.put(np, nodalPoints.get(np));
                }
            }
        }
        
        // удаляем точки локального слияния из списка nodalPoints
        for (GeoPoint np : nodalPointsLM.keySet()) {
            nodalPoints.remove(np);
        }
        
        /*    NodalPoint np = nodalPoints.get(new GeoPoint(47175018,11827721,1782));
    for (Transition t : np.transitionsFrom) {
      Log.d("Triangulation31", "точка=" + np.point + ", tFrom=" + t.connection.data.id + ", t.gStart=" + t.gStart + ", t.gEnd=" + t.gEnd);
    }
    for (Transition t : np.transitionsTo) {
      Log.d("Triangulation31", "точка=" + np.point + ", tTo=" + t.connection.data.id + ", t.gStart=" + t.gStart + ", t.gEnd=" + t.gEnd);
    }
         */

        mergeNodalPoints(skiAreaType, nodalPointsLM, CheckTransitionType.LocalMerge);
        mergeNodalPoints(skiAreaType, nodalPoints, CheckTransitionType.Normal);
        
        // объединение точек локального слияния с основным списком
        nodalPoints.putAll(nodalPointsLM);
        
        if (skiAreaType == SkiArea.Type.Summer) {
            // слияние связанных графов
            mergeGraphs();
        }
        
        // установить liftingCoeff
        for (Transition transition : transitions.values()) {
            Double d = getLiftingCoef(transition, null);
            transition.connection.liftingCoeff = d == null ? Navigator.liftSpeedCoeff / 2 : d;
        }
        logInfo("End prepare");
    }

    public void markUseless() {
        useless = true;
    }

    // check if conFrom and conTo are really connected
    private Transition addTransition(Connection con, boolean back) {
        if (con.start.equals(con.end)) {
            return null;
        }
        GeoPoint pFrom = con.start;
        GeoPoint pTo = con.end;
        pFrom.setAltitude(Elevation.getElevation(pFrom));
        pTo.setAltitude(Elevation.getElevation(pTo));
        Transition res;
        if (back) {
            res = Connection.createTransitionBack(con, pTo, pFrom);
            this.transitions.put(-con.data.id, res);
        } else {
            res = Connection.createTransition(con, pFrom, pTo);
            this.transitions.put(con.data.id, res);
        }
//    System.out.println("Коннекшн=" + res.connection.data.name + ", Id=" + res.connection.data.id + ", start=" + res.gStart + ", end=" + res.gEnd + ", twoWay=" + res.connection.twoWay);
        return res;

    }

    private Transition addTransition(Connection con) {
        if (!con.start.equals(con.end)) {
            return addTransition(con, false);
        } else {
            return null;
        }
    }

    private Transition addReverseTransition(Transition transition) {
        if (transitions.containsKey(-transition.connection.data.id)) {
            return transitions.get(-transition.connection.data.id);
        } else {
//      logInfo("addReverseTransition.Parent transition: id=" + transition.connection.data.id + ", name=" + transition.connection.data.name);
            Transition tr = addTransition(transition.connection, true);
//    if (tr != null)
//          tr.setGraphPoints(transition.gEnd, transition.gStart);
            return tr;
        }
    }

    private Double getLiftingCoef(Transition trans, TreeMap<Long, Long> stack) {
        if (trans.connection.isLift()) {
            return Navigator.liftSpeedCoeff / trans.connection.speed;
        }

        Double min = null;
        /*
    if (stack == null)
      stack = new TreeMap<Long, Long>();
    stack.put(trans.connection.data.id, trans.connection.data.id);
    for (Transition transition : nodalPoints.get(trans.gEnd).transitionsFrom)
    {
      if (stack.containsKey(transition.connection.data.id))
        continue;

      Double coeff = getLiftingCoef(transition, stack);
      if (coeff != null && (min == null || min < coeff))
        min = coeff;
    }
         */
        return min;
    }

    private boolean connectionPointExists(GeoPoint pFrom, GeoPoint pTo) {
        if (Navigator.fullDistance(pFrom, pTo) > 2 * Navigator.connectionPointDistance150)
            return false;

        for (GeoPoint connectionPoint : connectionPoints50) {
            if (Navigator.fullDistance(connectionPoint, pFrom) < Navigator.connectionPointDistance50 && Navigator.fullDistance(connectionPoint, pTo) < Navigator.connectionPointDistance50) {
                return true;
            }
        }
        for (GeoPoint connectionPoint : connectionPoints100) {
            if (Navigator.fullDistance(connectionPoint, pFrom) < Navigator.connectionPointDistance100 && Navigator.fullDistance(connectionPoint, pTo) < Navigator.connectionPointDistance100) {
                return true;
            }
        }
        for (GeoPoint connectionPoint : connectionPoints150) {
            if (Navigator.fullDistance(connectionPoint, pFrom) < Navigator.connectionPointDistance150 && Navigator.fullDistance(connectionPoint, pTo) < Navigator.connectionPointDistance150) {
                return true;
            }
        }
        return false;
    }

    public TreeMap<Long, Transition> getTransitions() {
        return transitions;
    }

    private List<GeoPoint> getPoints() {
        return points;
    }

    private void setPoints(List<GeoPoint> points) {
        this.points = points;
    }

    public TreeMap<Double, NodalPoint> getConnectionPointsByRiderPreference(RiderPreferences riderPreferences) {
        TreeMap<Double, NodalPoint> res = new TreeMap<>();
        for (NodalPoint p : nodalPoints.values()) {
            res.put(new Double(p.getKeyByRiderPreference(riderPreferences)), p);
        }
        return res;
    }

    public TreeMap<Long, Connection> createConnections(List<LayerData> layerRecords) {
        connectionPoints50 = new ArrayList<>();
        connectionPoints100 = new ArrayList<>();
        connectionPoints150 = new ArrayList<>();
        noDivisionPoints = new ArrayList<>();
        localMergePoints = new ArrayList<>();
        TreeMap<Long, Connection> connections = new TreeMap<>();

        for (LayerData layerItem : layerRecords) {
            if (layerItem.getType().groupType == LayerType.LayerTypeGroup.groupPolyline) {
                Connection.Type connType = ConnectionTypeFromLayerType(layerItem.getType());
                if (connType != null) {
                    connections.put(layerItem.id, new Connection(layerItem, connType));
                }
            } else if (layerItem.getType().id == LayerType.LAYERTYPE_ID_CONNECTION_POINT_50) {
                GeoPoint p = new GeoPoint(layerItem.getY1(), layerItem.getX1());
                p.setAltitude(Elevation.getElevation(p));
                connectionPoints50.add(p);
            } else if (layerItem.getType().id == LayerType.LAYERTYPE_ID_CONNECTION_POINT_100) {
                GeoPoint p = new GeoPoint(layerItem.getY1(), layerItem.getX1());
                p.setAltitude(Elevation.getElevation(p));
                connectionPoints100.add(p);
            } else if (layerItem.getType().id == LayerType.LAYERTYPE_ID_CONNECTION_POINT_150) {
                GeoPoint p = new GeoPoint(layerItem.getY1(), layerItem.getX1());
                p.setAltitude(Elevation.getElevation(p));
                connectionPoints150.add(p);
            } else if (layerItem.getType().id == LayerType.LAYERTYPE_ID_NO_DIVISION_POINT) {
                GeoPoint p = new GeoPoint(layerItem.getY1(), layerItem.getX1());
                p.setAltitude(Elevation.getElevation(p));
                noDivisionPoints.add(p);
            } else if (layerItem.getType().id == LayerType.LAYERTYPE_ID_LOCAL_MERGE_POINT) {
                GeoPoint p = new GeoPoint(layerItem.getY1(), layerItem.getX1());
                p.setAltitude(Elevation.getElevation(p));
                localMergePoints.add(p);
            }
        }

        return connections;
    }

    public void updateByLayerData(List<LayerData> layerRecords) {
        updateByLayerData(Type.Summer, layerRecords);
    }
    
    public void updateByLayerData(SkiArea.Type skiAreaType, List<LayerData> layerRecords) {
        logInfo("Calling createConnections");
        this.layerRecords = layerRecords;
        TreeMap<Long, Connection> connections = createConnections(layerRecords);

        logInfo("Calling prepare");
        prepare(skiAreaType, connections);
        SkiArea.this.prepared = true;
//    SkiArea.this.notify();
    }
    
    
    private Connection.Type ConnectionTypeFromLayerType(LayerType layerType) {
        if (layerType == null || layerType.realServiceGroup == null) {
            return null;
        }
        switch (layerType.realServiceGroup) {
            case Walk:
            case slopeGreen:
                return Connection.Type.PisteGreen;
            case slopeBlue:
                return Connection.Type.PisteBlue;
            case slopeRed:
                return Connection.Type.PisteRed;
            case slopeBlack:
                return Connection.Type.PisteBlack;
            case skiRoute:
                return Connection.Type.SkiRoute;
            //case otherRoute:
            //  return null;
            case skiTow:
                return Connection.Type.SkiTow;
            case chairlift:
                return Connection.Type.ChairLift;
            case cableCar:
                return Connection.Type.Gondola;
            //case otherTransport:
            //  return Connection.Type.Transport;
            default:
                return null;
        }
    }
    
    public static List<GeoPoint> convexPolygon(List<GeoPoint> points) {
        // обходим по часовой стрелке все точки, начиная с самой восточной
        return convexPolygon(new TreeSet<>(points));
    }
    
    public List<GeoPoint> convexPolygon() {
        // обходим по часовой стрелке все точки, начиная с самой восточной
        Set<GeoPoint> listNP = new TreeSet<>();
        // формируем список всех геоточек всех трасс и подъёмников
        for (Long traId : transitions.keySet()) {
            if (traId < 0)
                continue;
            Transition tra = transitions.get(traId);
            for (GeoPoint gp : tra.connection.data.getPoints()) {
                listNP.add(gp);
            }
            
        }
        return convexPolygon(listNP);
    }
    
//    public static List<GeoPoint> convexPolygon(Set<GeoPoint> listNP) {
//        // обходим по часовой стрелке все точки, начиная с самой восточной
//        List<GeoPoint> result = new ArrayList<>();
//        
//        if (listNP.isEmpty())
//            return result;
//        
//        // поиск самой восточной точки
//        GeoPoint eastGP = null;
//        for (GeoPoint geoPoint : listNP) {
//            if (eastGP == null || geoPoint.getLongitude() > eastGP.getLongitude()) {
//                eastGP = geoPoint;
//            }
//        }
//        result.add(eastGP);
//        
//        // идём от найденной точки по часовой стрелке и ищем точку,
//        // составляющую с предыдущей точкой отрезок, для которого все остальные точки справа
//        GeoPoint lastGP = eastGP;
//        do {
//            for (GeoPoint geoPoint : listNP) {
//                if (geoPoint.equals(lastGP))
//                    continue;
//                boolean allRight = true;
//                for (GeoPoint rightPoint : listNP) {
//                    if (geoPoint.equals(rightPoint))
//                        continue;
//                    int leftRight = Navigator.leftOrRight(lastGP, geoPoint, rightPoint);
//                    if (leftRight > 0) {
//                        // -1 - значит справа
//                        allRight = false;
//                        break;
//                    }
//                }
//                if (allRight) {
//                    if (geoPoint.equals(eastGP)) {
//                        // завершили обход и пришли опять к самой восточной точке
//                        return result;
//                    }
//                    if (!geoPoint.equals(lastGP))
//                        result.add(geoPoint);
//                    lastGP = geoPoint;
//                    break;
//                }
//            }
//            listNP.remove(lastGP);
//        } while (!listNP.isEmpty());
//        return result;
//    }
    
    public static List<GeoPoint> convexPolygon(Set<GeoPoint> listNP) {
        // формирует выпуклый многоуольник методом Джарвиса (работает очень быстро на большом количестве точек)

        List<GeoPoint> points = new ArrayList<>();

        // обходим по часовой стрелке все точки, начиная с самой восточной
        if (listNP.isEmpty())
            return points;

        // поиск самой восточной точки
        GeoPoint eastGP = null;
        for (final GeoPoint geoPoint : listNP) {
            if (eastGP == null || geoPoint.getLongitude() > eastGP.getLongitude()) {
                eastGP = geoPoint;
            }
        }

        // множество точек для идентификации завершения цикла (включая случаи попадания точек на одну прямую)
        TreeSet<GeoPoint> pointsSet = new TreeSet<>(); 
        pointsSet.add(eastGP);

        // идём от найденной точки по часовой стрелке и ищем точку,
        // угол с которой от отрезка [prevGP, lastGP] является максимальным
        GeoPoint lastGP = eastGP;
        // вводим точку, параллельную оси OY, для определения первого угла
        GeoPoint prevGP = new GeoPoint(eastGP.getLatitude() + 0.01, eastGP.getLongitude());
        GeoPoint maxAnglePoint = null;
        do {
            points.add(lastGP);
            double minCos = 999; // минимальный косинус соответствует максимальному углу
            double x1 = prevGP.getLongitude() - lastGP.getLongitude();
            double y1 = prevGP.getLatitude() - lastGP.getLatitude();
            for (final GeoPoint anglePoint : listNP) {
                if (lastGP.equals(anglePoint))
                    continue;
                double x2 = anglePoint.getLongitude() - lastGP.getLongitude();
                double y2 = anglePoint.getLatitude() - lastGP.getLatitude();
                double sqrt = Math.sqrt((x1*x1 + y1*y1) * (x2*x2 + y2*y2));
                double curCos = sqrt > 0 ? (x1*x2 + y1*y2) / sqrt : 1;
                if (curCos < minCos) {
                    minCos = curCos;
                    maxAnglePoint = anglePoint;
                }
            }

            if (maxAnglePoint == null)
                break;

            prevGP = lastGP;
            lastGP = maxAnglePoint;
        } while (pointsSet.add(lastGP));

        if (maxAnglePoint != null) {
            while (points.size() > 0 && !points.get(0).equals(maxAnglePoint)) {
                // удаляем стартовые точки для вырожденного случая (точки на одной прямой)
                points.remove(0);
            }
        }
        return points;
    }

    public final TreeMap<NodalPoint, ArriveInfo> recalcTimeToFinish(GeoPoint finish) throws Exception {
        Dejkstra dejkstra = new Dejkstra(nodalPoints, new RiderPreferences());
        dejkstra.CalculateDurations(finish, false, 0,
                new Dejkstra.CheckTransition() {
                    @Override
                    public boolean checkTransition(Transition transition, double time, RiderPreferences riderPreferences) {
                        return true;
                    }
                }
        );
        return dejkstra.arrives;
    }



    //        1. GeoPoint - географическая координата
    //        2. LayerData - информация о дороге
    //        3. Connection - дорога без направления
    //        4. Transition - направленная дорога со ссылками на узлы графа (NodalPoint)
    //        5. NodalPoint - узел графа дорог
    //        6. ArriveInfo - информация о времени движения от/до какой-то NodalPoint
    //        7. POI - достопримечательность (point of interest)
    //        8. OnPisteRoute - маршрут

    public static void main(String[] args) {
        // список всех LD из json
        SkiArea skiArea = new SkiArea("./areas/132/data.json", "./areas/132/pois.json");
        try {
            //TreeMap<NodalPoint, ArriveInfo> arrives = skiArea.recalcTimeToFinish(new GeoPoint(47.1999652855942,11.9190948665161));
            TreeMap<NodalPoint, ArriveInfo> arrives = skiArea.recalcTimeToFinish(new GeoPoint(47.1982868525548, 11.9113846977339));
            for (var entry : arrives.entrySet()) {
                NodalPoint np = entry.getKey();
                ArriveInfo ai = entry.getValue();
                if (!entry.getKey().poiList.isEmpty())
                    System.out.println(np + "; time to finish: " + ai.durationToFinish + "; " + np.poiList);
            }
            System.out.println(arrives.size());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // пример построения маршрута (кратчайший маршрут)
        GeoPoint start = new GeoPoint(47.1982868525548, 11.9113846977339);
        GeoPoint finish = new GeoPoint(47.206030235868, 11.873995906905);
        OnPisteRoute fastestRoute = OnPisteNavigator.fastestRouteDejkstra(skiArea, start, finish);
        System.out.println(fastestRoute);
    }
}
