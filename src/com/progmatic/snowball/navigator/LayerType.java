package com.progmatic.snowball.navigator;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/* Information regarding geo layer type (type's group, name, rendering options e t.c.)
 * Also provides global access to all possible types of layer objects statically **/
public class LayerType
{
  /* Predefined layer type IDs */
  public static final long LAYERTYPE_ID_CONNECTION_POINT_50 = 132;
  public static final long LAYERTYPE_ID_CONNECTION_POINT_100 = 130;
  public static final long LAYERTYPE_ID_CONNECTION_POINT_150 = 133;
  public static final long LAYERTYPE_ID_NO_DIVISION_POINT = 134;
  public static final long LAYERTYPE_ID_LOCAL_MERGE_POINT = 135;
  public static final long LAYERTYPE_ID_GONDOLA = 30;
  public static final long LAYERTYPE_ID_BUS = 44;

  /* Helper enums */
  public static enum LayerTypeLineStyle
  {
    lineStyleSolid,
    lineStyleDotted,
    lineStyleDashed
  }

  public static enum LayerTypeGroup
  {
    groupPoint,
    groupPolyline,
    groupPolygon;
  }


  public final long id;
  public final LayerTypeGroup groupType;
  public final String name;
  public final int minimalZoom;
  // Line rendering options, accessed via setLinePaintStyle(). For polylines and polygons.
  private final Color lineColor;
  private final LayerTypeLineStyle lineStyle;
  private final float lineWidth;
  // Points uses this as marker drawable. Polygons uses this as drawable for filling area
  public final int drawableResId;

  public final LayerTypeRealServiceGroup realServiceGroup;

  private static final long ID_DEFAULT_OBJECT = -100l;

  private static final int MINIMAL_ZOOM_UNUSED = -1;
  private static final int MINIMAL_ZOOM_1 = 1;
  private static final int MINIMAL_ZOOM_13 = 13;

  private static LayerType defaultDataType;
  private static ConcurrentHashMap<Long, LayerType> builtins =
          new ConcurrentHashMap<Long, LayerType>();

  private static List<LayerType> builtinsList;

  static
  {
    builtinsList = getBuiltinDataTypes();
    for (LayerType item : builtinsList)
    {
      builtins.put(item.id, item);
    }
    defaultDataType = new LayerType(
            ID_DEFAULT_OBJECT, LayerTypeGroup.groupPoint,
            "Default point style for unknown objects", MINIMAL_ZOOM_13,
            Color.WHITE, LayerTypeLineStyle.lineStyleSolid, 0.0f);
  }

  public LayerType(long id, LayerTypeGroup groupType, String name, int minimalZoom, 
                   Color lineColor, LayerTypeLineStyle lineStyle, float lineWidth)
  {
    this.id = id;
    this.groupType = groupType;
    this.name = name;
    this.minimalZoom = minimalZoom;
    this.drawableResId = 0;
    this.lineColor = lineColor;
    this.lineStyle = lineStyle;
    this.lineWidth = lineWidth;
    this.realServiceGroup = null;
  }

  public LayerType(long id, LayerTypeGroup groupType, String name, int minimalZoom,
                   Color lineColor, LayerTypeLineStyle lineStyle, float lineWidth, LayerTypeRealServiceGroup realServiceGroup)
  {
    this.id = id;
    this.groupType = groupType;
    this.name = name;
    this.minimalZoom = minimalZoom;
    this.drawableResId = 0;
    this.lineColor = lineColor;
    this.lineStyle = lineStyle;
    this.lineWidth = lineWidth;
    this.realServiceGroup = realServiceGroup;
  }

  private static List<LayerType> getBuiltinDataTypes()
  {
    List<LayerType> retVal = new ArrayList<>();

    // TODO: Should we load these builtins from the XML asset?
    retVal.add(new LayerType(
            1, LayerTypeGroup.groupPoint,
            "Точка", MINIMAL_ZOOM_1,
            Color.WHITE, LayerTypeLineStyle.lineStyleSolid, 0.0f));
    
    retVal.add(new LayerType(
            2, LayerTypeGroup.groupPolyline,
            "Ломаная линия", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f));
    
    retVal.add(new LayerType(
            3, LayerTypeGroup.groupPolygon,
            "Многоугольник", MINIMAL_ZOOM_UNUSED,
            Color.MAGENTA, LayerTypeLineStyle.lineStyleSolid, 7.0f));

    retVal.add(new LayerType(
            106, LayerTypeGroup.groupPoint,
            "Restaurant", MINIMAL_ZOOM_1, // MINIMAL_ZOOM_13
            Color.WHITE, LayerTypeLineStyle.lineStyleSolid, 0.0f,
            LayerTypeRealServiceGroup.restaurant));
    retVal.add(new LayerType(
            107, LayerTypeGroup.groupPoint,
            "Café", MINIMAL_ZOOM_13,
            Color.WHITE, LayerTypeLineStyle.lineStyleSolid, 0.0f,
            LayerTypeRealServiceGroup.restaurant));
    retVal.add(new LayerType(
            LAYERTYPE_ID_CONNECTION_POINT_50, LayerTypeGroup.groupPoint,
            "Connection Point 30", MINIMAL_ZOOM_13,
            Color.WHITE, LayerTypeLineStyle.lineStyleSolid, 0.0f,
            null));
    retVal.add(new LayerType(
            LAYERTYPE_ID_CONNECTION_POINT_100, LayerTypeGroup.groupPoint,
            "Connection Point 50", MINIMAL_ZOOM_13,
            Color.WHITE, LayerTypeLineStyle.lineStyleSolid, 0.0f,
            null));
    retVal.add(new LayerType(
            LAYERTYPE_ID_CONNECTION_POINT_150, LayerTypeGroup.groupPoint,
            "Connection Point 70", MINIMAL_ZOOM_13,
            Color.WHITE, LayerTypeLineStyle.lineStyleSolid, 0.0f,
            null));
    retVal.add(new LayerType(
            LAYERTYPE_ID_NO_DIVISION_POINT, LayerTypeGroup.groupPoint,
            "Connection Point", MINIMAL_ZOOM_13,
            Color.WHITE, LayerTypeLineStyle.lineStyleSolid, 0.0f,
            null));
    retVal.add(new LayerType(
            LAYERTYPE_ID_LOCAL_MERGE_POINT, LayerTypeGroup.groupPoint,
            "Connection Point", MINIMAL_ZOOM_13,
            Color.WHITE, LayerTypeLineStyle.lineStyleSolid, 0.0f,
            null));

    retVal.add(new LayerType(
            LAYERTYPE_ID_GONDOLA, LayerTypeGroup.groupPolyline,
            "Gondola", MINIMAL_ZOOM_UNUSED,
            Color.DARK_GRAY, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.cableCar));
    retVal.add(new LayerType(
            32, LayerTypeGroup.groupPolyline,
            "Cable Car", MINIMAL_ZOOM_UNUSED,
            Color.RED, LayerTypeLineStyle.lineStyleSolid, 5.0f,
            LayerTypeRealServiceGroup.cableCar));
    retVal.add(new LayerType(
            19, LayerTypeGroup.groupPolyline,
            "Ski rope tow", MINIMAL_ZOOM_UNUSED,
            Color.CYAN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.skiTow));
    retVal.add(new LayerType(
            20, LayerTypeGroup.groupPolyline,
            "Moving Carpet", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.skiTow));
    retVal.add(new LayerType(
            37, LayerTypeGroup.groupPolyline,
            "Funicular", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.cableCar));

    retVal.add(new LayerType(
            51, LayerTypeGroup.groupPolyline,
            "Chairlift", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            21, LayerTypeGroup.groupPolyline,
            "Chairlift 1x", MINIMAL_ZOOM_UNUSED,
            Color.MAGENTA, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            22, LayerTypeGroup.groupPolyline,
            "Chairlift 2x", MINIMAL_ZOOM_UNUSED,
            Color.BLUE, LayerTypeLineStyle.lineStyleDashed, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            50, LayerTypeGroup.groupPolyline,
            "Chairlift 3x", MINIMAL_ZOOM_UNUSED,
            Color.MAGENTA, LayerTypeLineStyle.lineStyleSolid, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            23, LayerTypeGroup.groupPolyline,
            "Chairlift 4x", MINIMAL_ZOOM_UNUSED,
            Color.RED, LayerTypeLineStyle.lineStyleDashed, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            52, LayerTypeGroup.groupPolyline,
            "Chairlift high speed", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            53, LayerTypeGroup.groupPolyline,
            "Chairlift high speed 2x", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            54, LayerTypeGroup.groupPolyline,
            "Chairlift high speed 3x", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            24, LayerTypeGroup.groupPolyline,
            "Chairlift high speed 4x", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            25, LayerTypeGroup.groupPolyline,
            "Chairlift high speed 6x", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.chairlift));
    retVal.add(new LayerType(
            26, LayerTypeGroup.groupPolyline,
            "Chairlift high speed 8x", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.chairlift));


    retVal.add(new LayerType(
            7, LayerTypeGroup.groupPolyline,
            "Slope Black", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleDashed, 5.0f,
            LayerTypeRealServiceGroup.slopeBlack));
    retVal.add(new LayerType(
            5, LayerTypeGroup.groupPolyline,
            "Slope Blue", MINIMAL_ZOOM_UNUSED,
            Color.BLUE, LayerTypeLineStyle.lineStyleDotted, 5.0f,
            LayerTypeRealServiceGroup.slopeBlue));
    retVal.add(new LayerType(
            4, LayerTypeGroup.groupPolyline,
            "Slope Green", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleSolid, 5.0f,
            LayerTypeRealServiceGroup.slopeGreen));
    retVal.add(new LayerType(
            6, LayerTypeGroup.groupPolyline,
            "Slope Red", MINIMAL_ZOOM_UNUSED,
            Color.RED, LayerTypeLineStyle.lineStyleSolid, 8.0f,
            LayerTypeRealServiceGroup.slopeRed));

    retVal.add(new LayerType(
            8, LayerTypeGroup.groupPolyline,
            "Ski route", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.skiRoute));


    retVal.add(new LayerType(
            9, LayerTypeGroup.groupPolyline,
            "Marked ski-tour ascent route", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            10, LayerTypeGroup.groupPolyline,
            "Sledge route", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            11, LayerTypeGroup.groupPolyline,
            "Snowshoe route", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            12, LayerTypeGroup.groupPolyline,
            "Cross country ski slope", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            13, LayerTypeGroup.groupPolyline,
            "Snow tubing", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            14, LayerTypeGroup.groupPolyline,
            "Hiking trail", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            15, LayerTypeGroup.groupPolyline,
            "Snow cat routes", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            41, LayerTypeGroup.groupPolyline,
            "Escalator", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            42, LayerTypeGroup.groupPolyline,
            "Walk", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleSolid, 5.0f,
            LayerTypeRealServiceGroup.Walk));

    retVal.add(new LayerType(
            43, LayerTypeGroup.groupPolyline,
            "Ski bridge", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            47, LayerTypeGroup.groupPolyline,
            "Footpath, trail", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            48, LayerTypeGroup.groupPolyline,
            "Bridge", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            49, LayerTypeGroup.groupPolyline,
            "Slope School", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherRoute));

    retVal.add(new LayerType(
            16, LayerTypeGroup.groupPolyline,
            "Ski tow - button", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.skiTow));

    retVal.add(new LayerType(
            17, LayerTypeGroup.groupPolyline,
            "Ski tow - J-bar", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.skiTow));

    retVal.add(new LayerType(
            18, LayerTypeGroup.groupPolyline,
            "Ski tow - T-bar", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.skiTow));

    retVal.add(new LayerType(
            19, LayerTypeGroup.groupPolyline,
            "Ski rope tow", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.skiTow));

    retVal.add(new LayerType(
            20, LayerTypeGroup.groupPolyline,
            "Moving carpet", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.skiTow));

    retVal.add(new LayerType(
            27, LayerTypeGroup.groupPolyline,
            "Chairlift high speed 10x", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.chairlift));

    retVal.add(new LayerType(
            28, LayerTypeGroup.groupPolyline,
            "Chairlift high speed 12x", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.chairlift));

    retVal.add(new LayerType(
            29, LayerTypeGroup.groupPolyline,
            "Hibrid lift", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.cableCar));

    retVal.add(new LayerType(
            31, LayerTypeGroup.groupPolyline,
            "Gondola funitel", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.cableCar));

    retVal.add(new LayerType(
            33, LayerTypeGroup.groupPolyline,
            "Cable car dobledecker", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.cableCar));

    retVal.add(new LayerType(
            34, LayerTypeGroup.groupPolyline,
            "Cable car funifor", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.cableCar));

    retVal.add(new LayerType(
            35, LayerTypeGroup.groupPolyline,
            "Pulse lift", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.cableCar));

    retVal.add(new LayerType(
            36, LayerTypeGroup.groupPolyline,
            "Cable railway", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.cableCar));

    retVal.add(new LayerType(
            38, LayerTypeGroup.groupPolyline,
            "Funicular tube", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.cableCar));

    retVal.add(new LayerType(
            39, LayerTypeGroup.groupPolyline,
            "Ski bus", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherTransport));

    retVal.add(new LayerType(
            40, LayerTypeGroup.groupPolyline,
            "Rack railway", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherTransport));

    retVal.add(new LayerType(
            LAYERTYPE_ID_BUS, LayerTypeGroup.groupPolyline,
            "Bus", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherTransport));

    retVal.add(new LayerType(
            45, LayerTypeGroup.groupPolyline,
            "Railway", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherTransport));

    retVal.add(new LayerType(
            46, LayerTypeGroup.groupPolyline,
            "Road", MINIMAL_ZOOM_UNUSED,
            Color.BLACK, LayerTypeLineStyle.lineStyleSolid, 3.0f,
            LayerTypeRealServiceGroup.otherTransport));

    retVal.add(new LayerType(
            218, LayerTypeGroup.groupPolyline,
            "Bike", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleSolid, 5.0f,
            LayerTypeRealServiceGroup.Walk));

    retVal.add(new LayerType(
            219, LayerTypeGroup.groupPolyline,
            "Country road", MINIMAL_ZOOM_UNUSED,
            Color.GREEN, LayerTypeLineStyle.lineStyleSolid, 5.0f,
            LayerTypeRealServiceGroup.Walk));

    return retVal;
  }

  public static LayerType getLayerTypeById(long typeId)
  {

    LayerType layerType = builtins.get(typeId);
    if (layerType != null)
    {
      return layerType;
    }
    else
    {
      return defaultDataType;
    }
  }

  public static LayerTypeGroup getGroupTypeById(long typeId)
  {
    LayerType layerType = getLayerTypeById(typeId);
    return layerType.groupType;
  }

  public static float getLineWidthById(long typeId)
  {
    LayerType layerType = getLayerTypeById(typeId);
    return layerType.lineWidth;
  }

  public static LayerTypeLineStyle getLineStyleById(long typeId)
  {
    LayerType layerType = getLayerTypeById(typeId);
    return layerType.lineStyle;
  }

  public static Color getLineColorById(long typeId)
  {
    LayerType layerType = getLayerTypeById(typeId);
    return layerType.lineColor;
  }

  public boolean isLift()
  {
    boolean result = false;
    switch (realServiceGroup)
    {
      case skiTow:
      case chairlift:
      case cableCar:
        result = true;
        break;
      default:
        result = false;
    }
    return result;
  }

  public boolean isPiste()
  {
    boolean result;
    switch (realServiceGroup)
    {
      case Walk:
      case slopeGreen:
      case slopeBlue:
      case slopeRed:
      case slopeBlack:
      case skiRoute:
        result = true;
        break;
      default:
        result = false;
    }
    return result;
  }

  public boolean isWalk()
  {
    return (realServiceGroup == LayerTypeRealServiceGroup.Walk);
  }
  
  public enum LayerTypeRealServiceGroup
  {
    Walk,
    slopeGreen,
    slopeBlue,
    slopeRed,
    slopeBlack,
    skiRoute,
    otherRoute,
    skiTow,
    chairlift,
    cableCar,
    otherTransport,
    restaurant,
    other;
  }

}
