package com.progmatic.snowball.navigator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

// точка интереса
public class POI {
    public String name; // название
    public GeoPoint point;
    double rating = 0; // рейтинг
    int  duration = 0; // продолжительность осмотра в секундах

    POI(String name, GeoPoint point) {
        this.name = name;
        this.point = point;
    }

    POI(String name, GeoPoint point, double rating, int  duration) {
        this(name, point);
        this.rating = rating;
        this.duration = duration;
    }

    // привязка POI к ближайшей NodalPoint
    public void linkPOI2NodalPoint(TreeMap<GeoPoint, SkiArea.NodalPoint> nodalPoints) {
        SkiArea.NodalPoint nearestNP = null;
        double minDistance = Double.MAX_VALUE;
        for (SkiArea.NodalPoint np : nodalPoints.values()) {
            double distance = GeoPoint.distance(np.point, point);
            if (distance < minDistance) {
                minDistance = distance;
                nearestNP = np;
            }
        }

        if (nearestNP != null && !nearestNP.poiList.contains(this))
            nearestNP.poiList.add(this);
    }

    // время ожидания в очереди
    public double getQueueWaitTime() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        POI poi = (POI) o;
        return name.equals(poi.name) &&
                point.equals(poi.point);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, point);
    }

    public static List<POI> getPOIFromJson(String poiJsonPathName) throws IOException {
        List<POI> poiList = new Gson().fromJson(
                Files.readString(Paths.get(poiJsonPathName)),
                new TypeToken<List<POI>>() {
                }.getType()
        );
        return poiList;
    }

    public static void linkAllPOIs2NodalPoints(List<POI> poiList, TreeMap<GeoPoint, SkiArea.NodalPoint> nodalPoints) {
        for (POI poi : poiList)
            poi.linkPOI2NodalPoint(nodalPoints);
    }

    @Override
    public String toString() {
        return name + "(rating=" + rating + ", duration=" + duration + ')';
    }
}
