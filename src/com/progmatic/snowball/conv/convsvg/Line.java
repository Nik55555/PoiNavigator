package com.progmatic.snowball.conv.convsvg;

class Point {
    public double x, y;
    
    Point() {
        this(0, 0);
    }
    Point(double x, double y) {
        this.x = x;
        this.y = y;
    }
 
    public double getX() {
        return x;
    }
 
    public void setX(double x) {
        this.x = x;
    }
 
    public double getY() {
        return y;
    }
 
    public void setY(double y) {
        this.y = y;
    }
 
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point)) return false;
 
        Point point = (Point) o;
 
        if (Double.compare(point.x, x) != 0)
            return false;
        return Double.compare(point.y, y) == 0;
    }
 
    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = x != +0.0d ? Double.doubleToLongBits(x) : 0L;
        result = (int) (temp ^ (temp >>> 32));
        temp = y != +0.0d ? Double.doubleToLongBits(y) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
    
    @Override
    public String toString() {
        return "Point{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
 
/**
 * Линия, выраженная уравнением вида y = k * x + b
 */
public class Line {
    private double k;
    private double b;
    
    public double x1;
    public double y1;
    public double x2;
    public double y2;
 
    public double ix;
    public double iy;
 
    Line(double x1, double y1,double x2, double y2) {
        this(new Point(x1, y1), new Point(x2, y2));
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }
    
    Line(Point a, Point b) {
        if (a.equals(b))
            throw new IllegalArgumentException("Points are equal. There are endless number of lines through one point.");
        double A = a.getY() - b.getY();
        double B = b.getX() - a.getX();
        if (B == 0)
            throw new IllegalArgumentException("Points lay on same vertical line.");
        double C = a.getX() * b.getY() - b.getX() * a.getY();
        this.k = - A / B;
        this.b = - C / B;
    }
 
    Line(Double k, Double b) {
        this.k = k;
        this.b = b;
    }
    
    public double distancePoints(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return (double) Math.sqrt(dx * dx + dy * dy);
    }

    public double distance(double x, double y) {
        Point p = this.getIntersectionPoint(getPerpendicularLine(new Point(x, y)));
        ix = p.x;
        iy = p.y;
        if ((x1 <= ix && x2 >= ix) || (x2 <= ix && x1 >= ix)) {
            return distancePoints(x, y, p.x, p.y);
        } else {
            double l1 = distancePoints(x, y, x1, y1);
            double l2 = distancePoints(x, y, x2, y2);
            if (l1 < l2) {
                ix = x1;
                iy = y1;
                return l1;
            } else {
                ix = x2;
                iy = y2;
                return l2;
            }
        }
    }
    
    /**
     * Возвращает угловой коэффициент линии из формулы вида y = k * x + b
     * @return угловой коэфициент
     */
    public double getK() {
        return k;
    }
 
    /**
     * Возвращает смещение линии из формулы вида y = k * x + b
     * @return смещение линии
     */
    public double getB() {
        return b;
    }
 
    Line getPerpendicularLine(Point point) {
        return new Line(-1 / k, point.getY() + point.getX() / k);
    }
 
    Line getPerpendicularLine(double x, double y) {
        return new Line(-1 / k, y + x / k);
    }
 
    Point getIntersectionPoint(Line other) {
        if (getK() == other.getK())
            throw new IllegalArgumentException("Lines are parallel and do not intersect.");
        Double x  = (other.getB() - getB()) / (getK() - other.getK());
        Double y = getK() * x + getB();
        return new Point(x, y);
    }
 
    @Override
    public String toString() {
        return "Line{" +
                "y = " + k +
                " * x + " + b +
                '}';
    }
}