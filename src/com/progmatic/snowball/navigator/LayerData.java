package com.progmatic.snowball.navigator;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.progmatic.snowball.utils.gpxparser.Point;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LayerData implements Comparable<Object> {
    public final long id;
    @SerializedName("layerTypeId")
    private final long typeId;
    @SerializedName("skiAreaId")
    public final long areaId;
    public final String name;
    public final String description;
    public final String data;
    public final String schemeData;
    public String demData = "";

    /* x1, y1  always represents left-bottom (west-south) corner, x2, y2 -  right-top (east-north) corner */
    private double x1;
    private double y1;
    private double x2;
    private double y2;

    protected boolean isAvailable = true;

    protected boolean isInverted = false;

    private List<GeoPoint> points;
    private List<Point> schemaPoints;

    public LayerData(long id, long typeId, long areaId, String name, String description,
            double x1, double y1, double x2, double y2, String data, String schemeData) {
        this.id = id;
        this.typeId = typeId;
        this.areaId = areaId;
        this.name = name;
        this.description = description;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.data = data;
        this.schemeData = schemeData;
        /*
        // Additional coordinates check
        if (LayerType.getGroupTypeById(typeId) != LayerType.LayerTypeGroup.groupPoint) {
            points = getGeoPointsFromJson(data);
            correctCoordsByPoints();
        }
         */
    }

    public LayerData(long id, long typeId, long areaId, String name, String description,
            double x1, double y1, double x2, double y2, String data,
            String schemeData, String demData) {
        this(id, typeId, areaId, name, description, x1, y1, x2, y2, data, schemeData);
        this.demData = demData;
    }

    public LayerType getType() {
        return LayerType.getLayerTypeById(typeId);
    }

    public double getX1() {
        return x1;
    }

    public double getY1() {
        return y1;
    }

    public double getX2() {
        return x2;
    }

    public double getY2() {
        return y2;
    }

    public void correctCoordsByPoints() {
        x1 = 180;
        x2 = -180;
        y1 = 180;
        y2 = -180;

        for (GeoPoint pt : points) {

            if (pt.getLongitude() > x2) {
                x2 = pt.getLongitude();
            }
            if (pt.getLongitude() < x1) {
                x1 = pt.getLongitude();
            }
            if (pt.getLatitude() > y2) {
                y2 = pt.getLatitude();
            }
            if (pt.getLatitude() < y1) {
                y1 = pt.getLatitude();
            }
        }
    }

    private static List<GeoPoint> getGeoPointsFromJson(String in) {
        List<GeoPoint> retVal = new ArrayList<GeoPoint>();
        try {
            if (in != null) {
                JSONObject json = new JSONObject(in);
                JSONArray coords = json.getJSONArray("coords");
                if (coords != null) {
                    int pointsCount = coords.length() / 2;
                    for (int i = 0; i < pointsCount; i++) {
                        final double lat = coords.getDouble(2 * i);
                        final double lon = coords.getDouble(2 * i + 1);
                        retVal.add(new GeoPoint(lat, lon, 0));
                    }
                }
            }
        } catch (JSONException je) {
            // Should we stop on JSON parse incident? I think no, just log it
//      CrashHelper.reportCrash(je);
        }
        return retVal;
    }

    public List<GeoPoint> getPoints() {
        if ((points == null) /*&& (LayerType.getGroupTypeById(typeId) != LayerType.LayerTypeGroup.groupPoint)*/) {
            points = getGeoPointsFromJson(data);
            //data = null;
        }
        return points;
    }

    // workaround to avoid wrong order of data.
    //TODO: this check should be done on server
    public void invertPoints() {
        List<GeoPoint> newPoints = new ArrayList<GeoPoint>();
        for (GeoPoint p : points) {
            newPoints.add(0, p);
        }
        points = newPoints;
    }

    private List<Point> getSchemaPointsFromJson(String in) {

        List<Point> retVal = new ArrayList<Point>();
        try {
            if (in != null) {
                JSONObject json = new JSONObject(in);
                JSONArray coords = json.getJSONArray("coords");
                if (coords != null) {
                    int pointsCount = coords.length() / 2;
                    for (int i = 0; i < pointsCount; i++) {
                        final int x = coords.getInt(2 * i);
                        final int y = coords.getInt(2 * i + 1);
                        retVal.add(new Point(x, y));
                    }
                }
            }
        } catch (JSONException je) {
//      CrashHelper.reportCrashWithString("LayerData.id", String.valueOf(this.id));
//      CrashHelper.reportCrash(je); 
            // TODO обработать ошибку
        }
        return retVal;
    }

    public List<Point> getSchemaPoints() {
        if (schemaPoints == null) {
            schemaPoints = getSchemaPointsFromJson(schemeData);
            //schemeData = null;
        }
        return schemaPoints;
    }

    public double getFullSchemaDistance() {
        double result = 0;
        if (getSchemaPoints() != null && getSchemaPoints().size() > 0) {
            Point prevPoint = null;
            for (Point p : schemaPoints) {
                if (prevPoint != null) {
                    result += GeometryHelper.getDistance(prevPoint.x, prevPoint.y, p.x, p.y);
                }
                prevPoint = p;
            }
        }
        return result;
    }

    public double getDistance() {
        double result = Navigator.calcDistance(getPoints());
        return result;
    }

    @Override
    public int compareTo(Object obj) {
        return Long.compare(id, ((LayerData)obj).id);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (obj.getClass() != getClass())
            return false;
        final LayerData ld = (LayerData) obj;
        return (id == ld.id);
    }

    @Override
    public int hashCode() {
        return (int)this.id;
    }

    public void printLDInfo() {
        System.out.println("id: " + id);
        System.out.println("typeId: " + typeId);
        System.out.println("areaId: " + areaId);
        System.out.println("name: " + name);
        System.out.println("description: " + description);
        System.out.println("data: " + data);
        System.out.println("schemeData: " + schemeData);
        System.out.println("demData: " + demData);
        System.out.println("x1: " + x1);
        System.out.println("y1: " + y1);
        System.out.println("x2: " + x2);
        System.out.println("y2: " + y2);
    }
}
