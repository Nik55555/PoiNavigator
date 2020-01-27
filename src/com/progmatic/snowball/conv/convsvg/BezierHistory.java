package com.progmatic.snowball.conv.convsvg;

public class BezierHistory
{

    public Vector2 startPoint = new Vector2();
    public Vector2 lastPoint = new Vector2();
    public Vector2 lastKnot = new Vector2();

    public BezierHistory()
    {
    }
    
    public void setStartPoint(double x, double y)
    {
        startPoint.set(x, y);
    }
    
    public void setLastPoint(double x, double y)
    {
        lastPoint.set(x, y);
    }
    
    public void setLastKnot(double x, double y)
    {
        lastKnot.set(x, y);
    }
}
