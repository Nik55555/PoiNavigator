package com.progmatic.snowball.navigator;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class NodeNear extends SkiArea.NodalPoint {
    public int pointsNear = 0;
    public ArrayList<GeoPoint> nearest = new ArrayList<>();

    public NodeNear(NodeNear v) {
        super(v.point);
        this.pointsNear = v.pointsNear;
    }

    protected void appendSetOfPoint(List<GeoPoint> setOfPoints, TreeMap<GeoPoint, NodeNear> nn) {
        // рекурсивная функция для формирования всего множества ближайших точек вместе с ближаишими к ближайшим и т.д.
        // возвращает множество точек setOfPoints
        for (GeoPoint near : this.nearest) {
            if (!setOfPoints.contains(near)) {
                setOfPoints.add(near);
                if (nn.containsKey(near))
                    nn.get(near).appendSetOfPoint(setOfPoints, nn);
            }
        }
    };

    public NodeNear(GeoPoint gp) {
        super(gp);
    }

}
