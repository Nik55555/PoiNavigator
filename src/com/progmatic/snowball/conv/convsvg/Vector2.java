package com.progmatic.snowball.conv.convsvg;

public class Vector2 {
    public double x;
    public double y;

    Vector2(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    Vector2() {
    }
    
    public void set(double setx, double sety) {
        x = setx;
        y = sety;
    }
    
    public boolean isEqual(double setx, double sety) {
        return (x == setx) && (y == sety);
    }
    
    public boolean isEqual(Vector2 v) {
        return (x == v.x) && (y == v.y);
    }
}

