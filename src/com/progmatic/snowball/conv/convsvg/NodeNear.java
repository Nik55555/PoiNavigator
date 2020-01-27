package com.progmatic.snowball.conv.convsvg;

import java.util.ArrayList;

public class NodeNear extends Vector2 {
    public int pointsNear = 0;
    public int pointsEqual = 0;
    public boolean endpoint = false;
    public ArrayList<NodeNear> nearest = new ArrayList<>();
    public LayerData layerData = null;
    
    NodeNear(NodeNear v) {
        set(v.x, v.y);
        this.pointsNear = v.pointsNear;
        this.pointsEqual = v.pointsEqual;
        this.endpoint = v.endpoint;
        this.layerData = v.layerData;
    }
    
    NodeNear(Vector2 v) {
        set(v.x, v.y);
    }
    
    NodeNear() {
    }
}
