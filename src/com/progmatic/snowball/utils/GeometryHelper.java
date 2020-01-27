package com.progmatic.snowball.utils;

import com.progmatic.snowball.utils.gpxparser.Point;

public class GeometryHelper
{

  /**
   * Returns distance to segment
   * <p/>
   * www.java2s.com/Code/Java/2D-Graphics-GUI/Returnsdistancetosegment.htm
   *
   * @param sx1 segment x coord 1
   * @param sy1 segment y coord 1
   * @param sx2 segment x coord 2
   * @param sy2 segment y coord 2
   * @param px  point x coord
   * @param py  point y coord
   * @return distance to segment
   */

  public final static double ACCURACY = 0.01; // точность равенства координат

  private static double getDistanceToSegment(int sx1, int sy1, int sx2, int sy2, int px, int py)
  {
    Point closestPoint = getClosestPointOnSegment(sx1, sy1, sx2, sy2, px, py);
    return getDistance(closestPoint.x, closestPoint.y, px, py);
  }

  /**
   * Returns distance to segment
   *
   * @param sx1 segment x coord 1
   * @param sy1 segment y coord 1
   * @param sx2 segment x coord 2
   * @param sy2 segment y coord 2
   * @param px  point x coord
   * @param py  point y coord
   * @return distance to segment
   */
  public static double getDistanceToSegment(double sx1, double sy1, double sx2, double sy2, double px, double py)
  {
    Double sx1i = sx1;
    Double sy1i = sy1;
    Double sx2i = sx2;
    Double sy2i = sy2;
    Double pxi = px;
    Double pyi = py;

    double result = getDistanceToSegment(
            sx1i.intValue(),
            sy1i.intValue(),
            sx2i.intValue(),
            sy2i.intValue(),
            pxi.intValue(),
            pyi.intValue()
    );
    return result;
  }

  /**
   * Returns distance between two sets of coords
   *
   * @param x1 first x coord
   * @param y1 first y coord
   * @param x2 second x coord
   * @param y2 second y coord
   * @return distance between sets of coords
   */
  public static double getDistance(double x1, double y1, double x2, double y2)
  {
    // using long to avoid possible overflows when multiplying
    double dx = x2 - x1;
    double dy = y2 - y1;

    // return Math.hypot(x2 - x1, y2 - y1); // Extremely slow
    // return Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2)); // 20 times faster than hypot
    return Math.sqrt(dx * dx + dy * dy); // 10 times faster then previous line
  }

  /**
   * Returns closest point on segment to point
   *
   * @param sx1 segment x coord 1
   * @param sy1 segment y coord 1
   * @param sx2 segment x coord 2
   * @param sy2 segment y coord 2
   * @param px  point x coord
   * @param py  point y coord
   * @return closets point on segment to point
   */
  public static Point getClosestPointOnSegment(double sx1, double sy1, double sx2, double sy2, double px, double py) {
    return getClosestPointOnSegment((int) sx1, (int) sy1, (int) sx2, (int) sy2, (int) px, (int) py);
  }

  private static Point getClosestPointOnSegment(int sx1, int sy1, int sx2, int sy2, int px, int py)
  {
    double xDelta = sx2 - sx1;
    double yDelta = sy2 - sy1;

    if ((xDelta == 0) && (yDelta == 0))
    {
      return new Point(sx1, sy1);
//      throw new IllegalArgumentException("Segment start equals segment end");
    }

    double u = ((px - sx1) * xDelta + (py - sy1) * yDelta) / (xDelta * xDelta + yDelta * yDelta);

    final Point closestPoint;
    if (u < 0)
    {
      closestPoint = new Point(sx1, sy1);
    }
    else if (u > 1)
    {
      closestPoint = new Point(sx2, sy2);
    }
    else
    {
      closestPoint = new Point((int) Math.round(sx1 + u * xDelta), (int) Math.round(sy1 + u * yDelta));
    }

    return closestPoint;
  }

  private static Point getClosestPointOnLine(int sx1, int sy1, int sx2, int sy2, int px, int py)
  {
    double xDelta = sx2 - sx1;
    double yDelta = sy2 - sy1;

    if ((xDelta == 0) && (yDelta == 0)) {
      return new Point(sx1, sy1);
    }

    double u = ((px - sx1) * xDelta + (py - sy1) * yDelta) / (xDelta * xDelta + yDelta * yDelta);

    final Point closestPoint = new Point((int) Math.round(sx1 + u * xDelta), (int) Math.round(sy1 + u * yDelta));

    return closestPoint;
  }

}
