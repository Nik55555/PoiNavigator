package com.progmatic.snowball.utils.gpxparser;


import com.progmatic.snowball.entity.TrackPoint;
import com.progmatic.snowball.navigator.GeoPoint;
import com.progmatic.snowball.navigator.LayerData;
import com.progmatic.snowball.navigator.LayerType;
import com.progmatic.snowball.navigator.Navigator;
import com.progmatic.snowball.navigator.SkiArea.Transition;
import com.progmatic.snowball.utils.GeometryHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;


public class GpxAnalyser
{
  private int splitArrSpeedPrcnt = 10;

  private List<GpxArray> result = new ArrayList<>();
  private GpxItemData oldGpx = null;
  private GpxItemData nextGpx = null;
  private GpxArray currArr = new GpxArray(0);

  public GpxAnalyser()
  {
  }

  public GpxAnalyser(int splitArrSpeedPrcnt)
  {
    this.splitArrSpeedPrcnt = splitArrSpeedPrcnt;
  }

  /**
   * Returns arrays of point for gpx.
   *
   * @param in - gpx-file
   * @return different arrays of point
   */
  public List<GpxArray> analyse(InputStream in) throws IOException
  {
    GpxParser gp = new GpxParser(in);

    TrkPt tp = gp.nextTrkPt();
    for (int i = 0; tp != null; i++)
    {
      nextGpx = new GpxItemData(
              tp.getLat(),
              tp.getLon(),
              tp.getEle(),
              tp.getTime()
      );

      if (oldGpx != null)
      {
        AnalysePairData apd = new AnalysePairData(oldGpx, nextGpx);
        currArr = doNext(currArr, apd, i, result, nextGpx);
      }

      tp = gp.nextTrkPt();
      oldGpx = nextGpx;
    }
    return result;
  }


  private GpxArray doNext(GpxArray currArr, AnalysePairData apd, int i, List<GpxArray> result, GpxItemData nextGpx)
  {
    if (currArr.flagUpDown == apd.currUpDown)
    {
      currArr.finishIndex = i;

      double speedDifPrcnt = 0;
      if (currArr.speed > 0)
      {
        speedDifPrcnt = Math.abs(currArr.speed - apd.speed3d) / currArr.speed * 100;
      }

      if (speedDifPrcnt < splitArrSpeedPrcnt)
      { //check speed diff < splitArrSpeedPrcnt %.
        currArr.speed = (currArr.speed + apd.speed3d) / 2; //count speed for the set.
        currArr.data.add(nextGpx);
      }
      else
      {
        currArr = finishGpxArray(currArr, result, i, apd, nextGpx);
        result.get(result.size() - 1).finishIndex = i - 1; //change 1 step back;
      }
    }
    else
    {
      //wrong direction for the next item.
      currArr = finishGpxArray(currArr, result, i, apd, nextGpx);
    }
    return currArr;
  }

  private GpxArray finishGpxArray(GpxArray currArr, List<GpxArray> result, int i, AnalysePairData apd, GpxItemData nextGpx)
  {
    if (result.size() < 2 && currArr.finishIndex != currArr.startIndex)
    {
      //start here.
      result.add(currArr);
    }
    else if (result.size() > 1)
    {
      boolean isSmallSet = (currArr.finishIndex - currArr.startIndex <= 1);

      GpxArray lastArr = result.get(result.size() - 1);
      boolean lastDirectSame = (lastArr.flagUpDown == currArr.flagUpDown
              && currArr.startIndex - lastArr.finishIndex <= 2);

      if (lastDirectSame)
      {
        lastArr.finishIndex = i - 1; //change finish index.
      }
      else if (!isSmallSet)
      {
        result.add(currArr);
      }
    }

    currArr = new GpxArray(i - 1); //start a next set.
    currArr.flagUpDown = apd.currUpDown; //set direction for the array.
    currArr.finishIndex = i;
    currArr.speed = apd.speed3d;
    currArr.data.add(nextGpx);
    return currArr;
  }

  /**
   * Returns arrays of point for gpx and connect it to LayerData.
   *
   * @param in           - gpx-file
   * @param geoData      - geoData
   * @param nearDistance - check no more than nearDistance m.
   * @return different arrays of point
   */
  public List<GpxArray> analyse(InputStream in, List<LayerData> geoData, int nearDistance) throws IOException
  {
    GpxParser gp = new GpxParser(in);

    TrkPt tp = gp.nextTrkPt();
    int i;
    for (i = 0; tp != null; i++)
    {
      nextGpx = new GpxItemData(
              tp.getLat(),
              tp.getLon(),
              tp.getEle(),
              tp.getTime()
      );

      if (oldGpx != null)
      {
        AnalysePairData apd = new AnalysePairData(oldGpx, nextGpx);
        GpxArray savedArr = currArr; //arr can be finished and created a new one.
        currArr = doNext(currArr, apd, i, result, nextGpx);
        if (currArr != savedArr)
        {
          //find some nearest geo-point.
          LayerData layerData = searchLayerData(savedArr, geoData, nearDistance);
          if (layerData != null)
            savedArr.layerData = layerData;
        }
      }

      tp = gp.nextTrkPt();
      oldGpx = nextGpx;
    }
    return result;
  }

  /**
   * Returns list of GpxArray with no all data inside.
   *
   * @param in         - track points collection.
   * @param schemaData - list of LayerData for schema.
   * @return list of GpxArray with no all data inside.
   */
  public List<GpxArray> analyseWithLayerData(List<TrackPoint> in, List<LayerData> schemaData)
  {
    List<GpxArray> result = new ArrayList<>();
    for (int i = 0; i < in.size(); i++)
    {
      TrackPoint tp = in.get(i);
      List<PointWithDistance> l3nl = list3NearestLines(
              schemaData,
              UpDownFlag.NONE,
              true,
              new GeoPoint(tp.getLatitude(), tp.getLongitude()));
      if (l3nl != null && l3nl.size() > 0)
      {
        if (result.size() == 0)
        {
          GpxArray ga = new GpxArray(0);
          ga.layerData = l3nl.get(0).layerData;
          result.add(ga);
        }
        else if (result.get(result.size() - 1).layerData != l3nl.get(0).layerData)
        {
          result.get(result.size() - 1).finishIndex = i - 1;
          GpxArray ga = new GpxArray(i);
          ga.layerData = l3nl.get(0).layerData;
          result.add(ga);
        }
      }
    }
    return result;
  }


  /**
   * Returns info about nearest geo-point.
   *
   * @param myGeoPoint   - Geo-Point need to check
   * @param geoData      - geoData
   * @param nearDistance - check no more than nearDistance m.
   * @return nearest geo-point
   */
  public List<PointWithDistance> analyse(GeoPoint myGeoPoint, List<LayerData> geoData, int nearDistance) throws IOException
  {
    //find points and lines.
    List<PointWithDistance> points = list3NearesPoints(geoData, myGeoPoint);
    points.addAll(list3NearestLines(geoData, UpDownFlag.NONE, true, myGeoPoint));

    List<PointWithDistance> result = new ArrayList<PointWithDistance>();
    for (PointWithDistance pwd : points)
    {
      if (pwd.distance <= nearDistance)
      {
        if (result.isEmpty())
        {
          result.add(pwd);
        }
        else if (result.get(0).distance > pwd.distance)
        {
          result.clear();
          result.add(pwd);
        }
        else if (result.get(0).distance == pwd.distance)
        {
          result.add(pwd);
        }
      }
    }
    ;

    //if result has just 1 point
    if (result.size() == 1)
      return result;


    //sort all points by distance.
    Collections.sort(points, new Comparator<PointWithDistance>()
    {
      public int compare(PointWithDistance gpd1, PointWithDistance gpd2)
      {
        if (gpd1.distance == gpd2.distance)
          return 0;
        else if (gpd1.distance < gpd2.distance)
          return -1;
        else
          return 1;
      }
    });

    //get 2-3 nearest elements.
    List<PointWithDistance> knownResult = new ArrayList<>(3);
    for (int i = 0; i < 3 && i < points.size(); i++)
    {
      knownResult.add(points.get(i));
    }
    return knownResult;

  }

  /**
   * Returns nearest geo-point.
   *
   * @param gpxArray     - gpx-array.
   * @param geoData      - geoData
   * @param nearDistance - check no more than nearDistance m.
   * @return nearest geo-point
   */
  public LayerData searchLayerData(GpxArray gpxArray, List<LayerData> geoData, int nearDistance)
  {
    if (gpxArray.data.size() < 1)
      return null;

    //collect nearest geo-objects for start.
    GeoPoint startPoint = new GeoPoint(
            gpxArray.data.get(0).x,
            gpxArray.data.get(0).y);
    List<PointWithDistance> startPointList = null;
    if (gpxArray.flagUpDown == UpDownFlag.STOP || gpxArray.flagUpDown == UpDownFlag.WALK)
    {
      startPointList = list3NearesPoints(geoData, startPoint);
    }
    else
    {
      startPointList = list3NearestLines(geoData, UpDownFlag.NONE, true, startPoint);
    }

    //collect nearest geo-objects for finish.
    GeoPoint finishPoint = new GeoPoint(
            gpxArray.data.get(gpxArray.data.size() - 1).x,
            gpxArray.data.get(gpxArray.data.size() - 1).y);
    List<PointWithDistance> finishPointList = null;
    if (gpxArray.flagUpDown == UpDownFlag.STOP || gpxArray.flagUpDown == UpDownFlag.WALK)
    {
      finishPointList = list3NearesPoints(geoData, finishPoint);
    }
    else
    {
      finishPointList = list3NearestLines(geoData, UpDownFlag.NONE, true, finishPoint);
    }

    //the same geo-point < nearDistance
    if (finishPointList != null && startPointList != null
            && finishPointList.get(0).geoPoint.equals(startPointList.get(0).geoPoint)
            && finishPointList.get(0).distance < nearDistance
            && startPointList.get(0).distance < nearDistance
            )
    {

      LayerData result = finishPointList.get(0).layerData;
      return result;
    }
    return null;
  }

  /**
   * Returns 3 nearest layers with additional data.
   *
   * @param schemaData - schemaData
   * @param myGeoPoint - my geo position.
   * @return 3 nearest geo-points.
   */
  public List<PointWithDistance> list3NearesPoints(List<LayerData> schemaData, GeoPoint myGeoPoint)
  {

    List<PointWithDistance> pointCollector = new ArrayList<>();
    //no result if no schemaData.
    if (schemaData == null)
      return pointCollector;

    for (LayerData item : schemaData)
    {
      if (LayerType.getGroupTypeById(item.getType().id) == LayerType.LayerTypeGroup.groupPoint && item.getSchemaPoints().size() > 0)
      {
        pointCollector.add(new PointWithDistance(
                item,
                Navigator.pdistance(
                        myGeoPoint.getLatitude(),
                        myGeoPoint.getLongitude(),
                        item.getX1(),
                        item.getY1()
                )
        ));
      }
    }

    //sort all points by distance.
    Collections.sort(pointCollector, new Comparator<PointWithDistance>()
    {
      public int compare(PointWithDistance gpd1, PointWithDistance gpd2)
      {
        if (gpd1.distance == gpd2.distance)
          return 0;
        else if (gpd1.distance < gpd2.distance)
          return -1;
        else
          return 1;
      }
    });

    //get 3 nearest elements.
    List<PointWithDistance> result = new ArrayList<>(3);
    for (int i = 0; i < 3 && i < pointCollector.size(); i++)
    {
      result.add(pointCollector.get(i));
    }
    return result;
  }

  //Returns 3 nearest layers with additional data.
  public static List<PointWithDistance> list3NearestLines(List<LayerData> data, UpDownFlag flag, boolean SchemePoints, GeoPoint myGeoPoint)
  {
    List<PointWithDistance> pointCollector = new ArrayList<>();

    if (myGeoPoint == null)
    {
      return null;
    }

    //how many meters in 1 grad about.
    double dist1grad_lat = Navigator.M_IN_DEGREE;
    double dist1grad_lon = Navigator.pdistance(
            myGeoPoint.getLatitude(),
            myGeoPoint.getLongitude(),
            myGeoPoint.getLatitude(),
            myGeoPoint.getLongitude() + 1
    );

    // переменные для оценки интересного нам расстояния в градусах для долготы и широты
    double degree_deviation1_lon = Navigator.deviation1 / dist1grad_lon;
    double degree_deviation1_lat = Navigator.deviation1 / dist1grad_lat;

    for (LayerData item : data)
    {
      //check if data OK.
      if (LayerType.getGroupTypeById(item.getType().id) == LayerType.LayerTypeGroup.groupPolyline
              && (!SchemePoints || item.getSchemaPoints().size() > 0) //no idea.
              && ((flag == UpDownFlag.NONE) ||
              (flag == UpDownFlag.UP && item.getType().isLift()) || (flag == UpDownFlag.DOWN && item.getType().isPiste())))
      {
        // предварительная оценка попадания данной точки в прямоугольник описывающий LayerData
        // со сторонами (deviation1 + a + deviation1) x  (deviation1 + b + deviation1)
        // (отсеиваем большинство LayerData простой проверкой)
        if (item.getX1() - myGeoPoint.getLongitude() > degree_deviation1_lon)
          continue;
        if (myGeoPoint.getLongitude() - item.getX2() > degree_deviation1_lon)
          continue;
        if (item.getY1() - myGeoPoint.getLatitude() > degree_deviation1_lat)
          continue;
        if (myGeoPoint.getLatitude() - item.getY2() > degree_deviation1_lat)
          continue;


        double minDist = 10000000;
        //each point: check distance to point and to line.
        List<GeoPoint> listGeoPoints = item.getPoints();
        for (int i = 0; i < listGeoPoints.size(); i++)
        {
          double dist = Navigator.pdistance(
                  listGeoPoints.get(i).getLatitude(),
                  listGeoPoints.get(i).getLongitude(),
                  myGeoPoint.getLatitude(),
                  myGeoPoint.getLongitude()
          );
          if (dist < minDist)
          {
            minDist = dist;
          }
          else if (i > 0)
          { //check distance to line.
            double getEndsDiff = Navigator.pdistance(
                    listGeoPoints.get(i - 1).getLatitude(),
                    listGeoPoints.get(i - 1).getLongitude(),
                    listGeoPoints.get(i).getLatitude(),
                    listGeoPoints.get(i).getLongitude()
            );
            if (getEndsDiff > 3)
            { //check with a near line.
              double nextP2sDist = GeometryHelper.getDistanceToSegment(
                      listGeoPoints.get(i - 1).getLatitude() * dist1grad_lat,
                      listGeoPoints.get(i - 1).getLongitude() * dist1grad_lon,
                      listGeoPoints.get(i).getLatitude() * dist1grad_lat,
                      listGeoPoints.get(i).getLongitude() * dist1grad_lon,
                      myGeoPoint.getLatitude() * dist1grad_lat,
                      myGeoPoint.getLongitude() * dist1grad_lon

              );
              if (minDist > nextP2sDist)
              {
                minDist = nextP2sDist;
              }
            }
          }
        }
        PointWithDistance nextRec = new PointWithDistance(item, minDist);

        //get the nearest point between start and finish.
        double startDist = Navigator.pdistance(
                listGeoPoints.get(0).getLatitude(),
                listGeoPoints.get(0).getLongitude(),
                myGeoPoint.getLatitude(),
                myGeoPoint.getLongitude()
        );
        double endDist = Navigator.pdistance(
                listGeoPoints.get(listGeoPoints.size() - 1).getLatitude(),
                listGeoPoints.get(listGeoPoints.size() - 1).getLongitude(),
                myGeoPoint.getLatitude(),
                myGeoPoint.getLongitude()
        );
        if (startDist < endDist) {
          nextRec.geoPoint = listGeoPoints.get(0);
        }
        else {
          nextRec.geoPoint = listGeoPoints.get(listGeoPoints.size() - 1);
        }
        pointCollector.add(nextRec);
      }
    }

    //sort all points by distance.
    Collections.sort(pointCollector, new Comparator<PointWithDistance>()
    {
      public int compare(PointWithDistance gpd1, PointWithDistance gpd2)
      {
        if (gpd1.distance == gpd2.distance)
          return 0;
        else if (gpd1.distance < gpd2.distance)
          return -1;
        else
          return 1;
      }
    });

    //get 3 nearest elements.
    List<PointWithDistance> result = new ArrayList<>(3);
    for (int i = 0; i < 3 && i < pointCollector.size(); i++)
    {
      result.add(pointCollector.get(i));
    }
    return result;
  }

  //Returns nearest layers with additional data.
  public static List<PointWithDistance> listNearestLines(TreeMap<Long, Transition> data, GeoPoint myGeoPoint, double deviation)
  {
    List<PointWithDistance> pointCollector = new ArrayList<>();

    if (myGeoPoint == null)
    {
      return null;
    }

    //how many meters in 1 grad about
    double dist1grad_lat = Navigator.M_IN_DEGREE;
    double dist1grad_lon = Navigator.pdistance(
            myGeoPoint.getLatitude(),
            myGeoPoint.getLongitude(),
            myGeoPoint.getLatitude(),
            myGeoPoint.getLongitude() + 1
    );

    // переменные для оценки интересного нам расстояния в градусах для долготы и широты
    double degree_deviation_lon = deviation / dist1grad_lon;
    double degree_deviation_lat = deviation / dist1grad_lat;

    for (Long key : data.keySet())
    {
      if (key < 0)
        continue;
      Transition item = data.get(key);
      //check if data OK.
        // предварительная оценка попадания данной точки в прямоугольник описывающий LayerData
        // со сторонами (deviation + a + deviation) x  (deviation + b + deviation)
        // (отсеиваем большинство LayerData простой проверкой)
        if (item.connection.data.getX1() - myGeoPoint.getLongitude() > degree_deviation_lon)
          continue;
        if (myGeoPoint.getLongitude() - item.connection.data.getX2() > degree_deviation_lon)
          continue;
        if (item.connection.data.getY1() - myGeoPoint.getLatitude() > degree_deviation_lat)
          continue;
        if (myGeoPoint.getLatitude() - item.connection.data.getY2() > degree_deviation_lat)
          continue;


        double minDist = Double.MAX_VALUE;
        GeoPoint minLinePoint1;
        GeoPoint minLinePoint2;
        GeoPoint minPointOnLine;
        GeoPoint linePoint;

        //each point: check distance to point and to line.
        List<GeoPoint> listGeoPoints = item.connection.data.getPoints();
        int size = listGeoPoints.size();
        if (size == 1) {
          // только одна точка
          minLinePoint1 = listGeoPoints.get(0);
          minLinePoint2 = minLinePoint1;
          minPointOnLine = minLinePoint1;
          minDist = GeoPoint.distance(myGeoPoint, minPointOnLine);
        } else {
          if (size == 2) {
            // две точки
            minLinePoint1 = listGeoPoints.get(0);
            minLinePoint2 = listGeoPoints.get(1);
          } else {
            // больше двух
            int minI = 0;
            for (int i = 0; i < size; i++) {
              linePoint = listGeoPoints.get(i);
              double dist = GeoPoint.distance(linePoint, myGeoPoint);
              if (dist < minDist) {
                minDist = dist;
                minI = i;
              }
            }

            if (minI == 0) {
              // ближайшая первая
              minLinePoint1 = listGeoPoints.get(0);
              minLinePoint2 = listGeoPoints.get(1);
            } else if (minI == size - 1) {
              // ближайшая последняя
              minLinePoint1 = listGeoPoints.get(size - 2);
              minLinePoint2 = listGeoPoints.get(size - 1);
            } else {
              // ближайшая внутри ломаной
              GeoPoint gp1 =  listGeoPoints.get(minI-1);
              GeoPoint gp2 =  listGeoPoints.get(minI);
              if (GeoPoint.distance(gp1, gp2) >= GeoPoint.distance(gp1, myGeoPoint)) {
                // находится на отрезке до ближайшей
                minLinePoint1 = gp1;
                minLinePoint2 = gp2;
              } else {
                // находится на отрезке после ближайшей
                minLinePoint1 = gp2;
                minLinePoint2 = listGeoPoints.get(minI + 1);
              }
            }
          }

          // расстояние до ближайшего сегмента ломаной
          double px = myGeoPoint.getLongitude() * dist1grad_lon;
          double py = myGeoPoint.getLatitude() * dist1grad_lat;
          Point closestPoint = GeometryHelper.getClosestPointOnSegment(
                  minLinePoint1.getLongitude() * dist1grad_lon,
                  minLinePoint1.getLatitude() * dist1grad_lat,
                  minLinePoint2.getLongitude() * dist1grad_lon,
                  minLinePoint2.getLatitude() * dist1grad_lat,
                  px, py);

          minDist = GeometryHelper.getDistance(closestPoint.x, closestPoint.y, px, py);
          minPointOnLine = new GeoPoint(closestPoint.y / dist1grad_lat, closestPoint.x / dist1grad_lon);
        }

        PointWithDistance nextRec = new PointWithDistance(item.connection.data, minDist, minLinePoint1, minLinePoint2, minPointOnLine, false);

        //get the nearest point between start and finish.
        double startDist = Navigator.pdistance(
                listGeoPoints.get(0).getLatitude(),
                listGeoPoints.get(0).getLongitude(),
                myGeoPoint.getLatitude(),
                myGeoPoint.getLongitude()
        );
        double endDist = Navigator.pdistance(
                listGeoPoints.get(listGeoPoints.size() - 1).getLatitude(),
                listGeoPoints.get(listGeoPoints.size() - 1).getLongitude(),
                myGeoPoint.getLatitude(),
                myGeoPoint.getLongitude()
        );
        if (startDist < endDist) {
          nextRec.geoPoint = listGeoPoints.get(0);
        }
        else {
          nextRec.geoPoint = listGeoPoints.get(listGeoPoints.size() - 1);
        }
        pointCollector.add(nextRec);
    }

    //sort all points by distance.
    Collections.sort(pointCollector, new Comparator<PointWithDistance>()
    {
      public int compare(PointWithDistance gpd1, PointWithDistance gpd2)
      {
        if (gpd1.distance == gpd2.distance)
          return 0;
        else if (gpd1.distance < gpd2.distance)
          return -1;
        else
          return 1;
      }
    });

    //get 3 nearest elements.
    List<PointWithDistance> result = new ArrayList<>();
      for (PointWithDistance pointCollector1 : pointCollector) {
          result.add(pointCollector1);
      }
    return result;
  }
  
  public enum UpDownFlag
  {

    NONE, UP, DOWN, STOP, WALK, RIDE
  }

  public static class GpxArray
  {

    public final int startIndex;
    public int finishIndex;
    public UpDownFlag flagUpDown;
    public Double speed;
    public List<GpxItemData> data;
    public LayerData layerData;

    public GpxArray(int startIndex)
    {
      this.startIndex = startIndex;
      this.finishIndex = startIndex;
      if (startIndex == 0)
      {
        this.speed = 0.0;
      }
      data = new ArrayList<>();
    }
  }

  private static class GpxItemData
  {

    public double x;
    public double y;
    public double z;
    public long t;

    public GpxItemData(Double x, Double y, Double z, long t)
    {
      this.x = x;
      this.y = y;
      this.z = z;
      this.t = t;
    }
  }

  private class AnalysePairData
  {

    double dist3d;
    double dist2d;
    Double speed2d;
    Double speed3d;
    long timeDiff;
    UpDownFlag currUpDown;

    public AnalysePairData(GpxItemData oldGpx, GpxItemData nextGpx)
    {
      this.timeDiff = (nextGpx.t - oldGpx.t) / 1000; //1sec
      this.dist2d = Navigator.pdistance(oldGpx.x, oldGpx.y, nextGpx.x, nextGpx.y);
      this.speed2d = (dist2d / timeDiff) * 3.6; //horisontal speed - km/h

      this.dist3d = Math.sqrt(Math.pow(dist2d, 2) + Math.pow(nextGpx.z - oldGpx.z, 2));
      this.speed3d = (dist3d / timeDiff) * 3.6; //3d speed - km/h

      //direction.
      if (nextGpx.z < oldGpx.z)
      {
        currUpDown = UpDownFlag.DOWN;
      }
      else if (nextGpx.z > oldGpx.z)
      {
        currUpDown = UpDownFlag.UP;
      }
      else if (nextGpx.z == oldGpx.z && speed3d.intValue() > 1)
      {
        if (speed3d.intValue() < 7)
          currUpDown = UpDownFlag.WALK;
        else
          currUpDown = UpDownFlag.RIDE;
      }
      else
      {
        currUpDown = UpDownFlag.STOP;
      }
    }
  }

  public static class PointWithDistance
  {
    public double distance;
    public GeoPoint geoPoint;
    public LayerData layerData;
    public GeoPoint linePoint1;
    public GeoPoint linePoint2;
    public GeoPoint pointOnLine;

    public PointWithDistance(LayerData layerData, double distance)
    {
      this.layerData = layerData;
      this.distance = distance;
    }
    public PointWithDistance(LayerData layerData, double distance, GeoPoint linePoint1, GeoPoint linePoint2, GeoPoint pointOnLine, boolean schemePoints)
    {
      this.layerData = layerData;
      this.distance = distance;
      this.linePoint1 = linePoint1;
      this.linePoint2 = linePoint2;
      this.pointOnLine = pointOnLine;
    }

  }

  public static class RouteAggregateResult
  {
    public double duration = 0;
    public double distance = 0;

    public double totalUp = 0; //duration
    public double totalDown = 0; //duration
    public double totalGreen = 0; //distance
    public double totalBlue = 0; //distance
    public double totalRed = 0; //distance
    public double totalBlack = 0; //distance
    public double totalTow = 0; //just count
    public double totalChair = 0; //just count
    public double totalGondola = 0; //just count

    public double maxSpeed = 0;
    public double avgSpeed = 0;
    public double maxAltitude = 0;
    public double minAltitude = 100000000;
  }


}

