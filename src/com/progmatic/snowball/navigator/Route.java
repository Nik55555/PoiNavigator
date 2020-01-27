package com.progmatic.snowball.navigator;

import java.util.ArrayList;
import java.util.List;

public class Route {
    protected List<GeoPoint> track = new ArrayList<>();

    public List<GeoPoint> getTrack() {
        return track;
    }

    public void setTrack(List<GeoPoint> track) {
        this.track = track;
    }
}
