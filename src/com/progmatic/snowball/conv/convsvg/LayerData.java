package com.progmatic.snowball.conv.convsvg;

public class LayerData {
    public long area_id;
    public String name;
    public String description;
    public double x1;
    public double y1;
    public double x2;
    public double y2;
    public long layer_type_id;
    public String data;
    public Long osmId;
    public String osmType;

    LayerData(LayerData ld) {
        this.area_id = ld.area_id;
        this.name = ld.name;
        this.description = ld.description;
        this.x1 = ld.x1;
        this.y1 = ld.y1;
        this.x2 = ld.x2;
        this.y2 = ld.y2;
        this.layer_type_id = ld.layer_type_id;
        this.data = ld.data;
        this.osmId = ld.osmId;
        this.osmType = ld.osmType;
    }

    LayerData() {
    }
    
    public void setX2Y2(double x, double y) {
        this.x2 = x;
        this.y2 = y;
    }
    
    public void setX1Y1(double x, double y) {
        this.x1 = x;
        this.y1 = y;
    }
}
