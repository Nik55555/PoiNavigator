package com.progmatic.snowball.navigator;

import java.sql.Timestamp;

public class TrackPoint {
    public final long id;
    public final Double latitude;
    public final Double longitude;
    public final Integer elevation;
    public final long time; // секунды

    public TrackPoint(long id, Double latitude, Double longitude, Integer elevation, Timestamp time) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevation = elevation;
        this.time = time.getTime() / 1000;
    }
}
