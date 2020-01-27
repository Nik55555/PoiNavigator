package com.progmatic.snowball.utils;

import java.awt.geom.Point2D;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.progmatic.snowball.entity.LayerData;
import com.progmatic.snowball.entity.LayerType;
import com.progmatic.snowball.entity.SkiArea;
import com.progmatic.snowball.navigator.GeoPoint;
import com.progmatic.snowball.service.ServerProperties;

import de.micromata.opengis.kml.v_2_2_0.Boundary;
import de.micromata.opengis.kml.v_2_2_0.Folder;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.LineString;
import de.micromata.opengis.kml.v_2_2_0.LinearRing;
import de.micromata.opengis.kml.v_2_2_0.Placemark;


public class XmlParser {

    public static List<LayerData> getLayerDataListFromFile(String fileName, List<LayerType> defLayerTypes) throws ParserConfigurationException, SAXException, IOException
    {
    	fileName = ServerProperties.GetProperty("UploadsPath") + fileName;

    	List<LayerData> result = new ArrayList<LayerData>();
    	
    	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	DocumentBuilder builder = factory.newDocumentBuilder();
    	Document document = builder.parse(new File(fileName));
    	NodeList nl = document.getElementsByTagName("Placemark");
		
    	for (int n = 0; n < nl.getLength(); n++)
		{
			Node node = nl.item(n);
			LayerData ld = new LayerData();
			
			for (int m = 0; m < node.getChildNodes().getLength(); m++)
			{
				Node cn = node.getChildNodes().item(m);
				
				if (cn.getNodeName().equals("name")) ld.setName(cn.getFirstChild().getNodeValue());
				
				try{
					if (cn.getNodeName().equals("description")) ld.setDescription(cn.getFirstChild().getNodeValue());
				}catch (Exception e) {}
				
				if (cn.getNodeName().equals("Point")) ld = getPointLayerData(cn, ld, defLayerTypes.get(0));
				if (cn.getNodeName().equals("LineString")) ld = getLineLayerData(cn, ld, defLayerTypes.get(1));
				if (cn.getNodeName().equals("Polygon")) ld = getPolygonLayerData(cn, ld, defLayerTypes.get(2));
				
			}

			result.add(ld);
		}
		
		return result;
    }

    private static LayerData getPointLayerData(Node node, LayerData ld, LayerType defLayerType)
    {
    	
    	Node n = getChildNode(node, "coordinates");
    	//System.out.println("Point" + n.getFirstChild().getNodeValue());
    	
    	List<GeoPoint> gps = getGeoPointList(n.getFirstChild().getNodeValue());
    	
    	ld.setX1(gps.get(0).getLongitude());
    	ld.setY1(gps.get(0).getLatitude());
    	ld.setX2(gps.get(0).getLongitude());
    	ld.setY2(gps.get(0).getLatitude());
    	
    	ld.setLayerType(defLayerType);
    	ld.setData(serializeObject(gps));
    	
    	return ld;
    }

    private static LayerData getLineLayerData(Node node, LayerData ld, LayerType defLayerType)
    {
    	Node n = getChildNode(node, "coordinates");
    	//System.out.println("Point" + n.getFirstChild().getNodeValue());
    	
    	List<GeoPoint> gps = getGeoPointList(n.getFirstChild().getNodeValue());
    	GeoPoint mingp = getMinGeoPoint(gps);
    	GeoPoint maxgp = getMaxGeoPoint(gps);
    	
    	ld.setX1(mingp.getLongitude());
    	ld.setY1(mingp.getLatitude());
    	ld.setX2(maxgp.getLongitude());
    	ld.setY2(maxgp.getLatitude());
    	
    	ld.setLayerType(defLayerType);
    	ld.setData(serializeObject(gps));

    	return ld;
    }

    private static LayerData getPolygonLayerData(Node node, LayerData ld, LayerType defLayerType)
    {
    	Node n = getChildNode(node, "outerBoundaryIs");
    	n = getChildNode(n, "LinearRing");
    	n = getChildNode(n, "coordinates");
    	//System.out.println("Polygon" + n.getFirstChild().getNodeValue());

    	List<GeoPoint> gps = getGeoPointList(n.getFirstChild().getNodeValue());
    	GeoPoint mingp = getMinGeoPoint(gps);
    	GeoPoint maxgp = getMaxGeoPoint(gps);
    	
    	ld.setX1(mingp.getLongitude());
    	ld.setY1(mingp.getLatitude());
    	ld.setX2(maxgp.getLongitude());
    	ld.setY2(maxgp.getLatitude());
    	
    	ld.setLayerType(defLayerType);
    	ld.setData(serializeObject(gps));

    	return ld;
    }
    
    
    private static List<GeoPoint> getGeoPointList(String coordinates)
    {
    	List<GeoPoint> gpl = new ArrayList<GeoPoint>();
    	
    	String[] pts = coordinates.split(" ");
    	
    	for (int n = 0; n < pts.length; n++)
    	{
    		GeoPoint gp = new GeoPoint();
    		String[] cords = pts[n].split(",");
    		
    		gp.setLatitude(Double.parseDouble(cords[1]));
    		gp.setLongitude(Double.parseDouble(cords[0]));
    		gp.setAltitude(Integer.parseInt(cords[2]));
    		
    		gpl.add(gp);
    	}
    	
    	return gpl;
    }
    
    private static Node getChildNode(Node parentNode, String nodeName)
    {
    	Node result = null;
    	NodeList childNodes = parentNode.getChildNodes();
    	
    	for (int n = 0; n < childNodes.getLength(); n++)
    	{
    		if (childNodes.item(n).getNodeName().equals(nodeName)) return childNodes.item(n);
    	}
    	
    	return result;
    }
    
    public static GeoPoint getMinGeoPoint(List<GeoPoint> gpl)
    {
    	double lat = gpl.get(0).getLatitude(); 
    	double lon = gpl.get(0).getLongitude();
    	
    	for (GeoPoint gp : gpl)
    	{
    		if (gp.getLatitude() < lat) lat = gp.getLatitude();
    		if (gp.getLongitude() < lon) lon = gp.getLongitude();
    	}
    	
    	return new GeoPoint(lat, lon);
    }

    public static GeoPoint getMaxGeoPoint(List<GeoPoint> gpl)
    {
    	double lat = gpl.get(0).getLatitude(); 
    	double lon = gpl.get(0).getLongitude();
    	
    	for (GeoPoint gp : gpl)
    	{
    		if (gp.getLatitude() > lat) lat = gp.getLatitude();
    		if (gp.getLongitude() > lon) lon = gp.getLongitude();
    	}
    	
    	return new GeoPoint(lat, lon);
    }
    
    public static String serializeObject(Object o)
    {
    	return serializeObject(o, Boolean.parseBoolean(ServerProperties.GetProperty("SerializeToJSON")));
    }

    public static String serializeObject(Object o, boolean serializeToJSON)
    {
    	if (!serializeToJSON)
    	{
	    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
			XMLEncoder xmlEncoder = new XMLEncoder(baos);
			xmlEncoder.writeObject(o);
			xmlEncoder.close();
	
			return baos.toString();
    	}
		else
		{
			JSONObject obj = new JSONObject();
			JSONArray ja = new JSONArray();
			
			try
			{
				@SuppressWarnings("unchecked")
				List<GeoPoint> gpl = (List<GeoPoint>) o;
				for (GeoPoint gp : gpl)
				{
					ja.put(gp.getLatitude());
					ja.put(gp.getLongitude());
				}
			}
			catch(Exception e)
			{
				try
				{
					ja = new JSONArray();
					@SuppressWarnings("unchecked")
					List<Point2D> pl = (List<Point2D>) o;
					for (Point2D p : pl)
					{
						ja.put(p.getX());
						ja.put(p.getY());
					}
				}
				catch (Exception ex){}
			}
			
			try 
			{
				obj.put("coords", ja);
			}
			catch (JSONException e)
			{
				e.printStackTrace();
			}
			
			return obj.toString();
		}
    }

    @SuppressWarnings("unchecked")
	public static List<GeoPoint> deSerializeGeoPointList(String serialized)
    {
    	List<GeoPoint> result = new ArrayList<GeoPoint>();
    	
    	if (!Boolean.parseBoolean(ServerProperties.GetProperty("SerializeToJSON")))
    	{
	    	try
	    	{
	    		InputStream is = new ByteArrayInputStream(serialized.getBytes());
		    	XMLDecoder xmlDecoder = new XMLDecoder(is);
		    	result = (List<GeoPoint>) xmlDecoder.readObject();
		    	xmlDecoder.close();
	    	}
	    	catch (Exception e)
	    	{
	    		e.printStackTrace();
	    	}
    	}
    	else
    	{
    		JSONObject obj = null;

    		try 
			{
				obj = new JSONObject(serialized);
			} 
    		catch (JSONException e) 
    		{
    			e.printStackTrace();
    		}
    		
    		if (obj != null)
    		{
    			JSONArray ja = null;
    			try 
    			{
					ja = obj.getJSONArray("coords");
				} catch (JSONException e) {
					e.printStackTrace();
				}
    			
    			if (ja != null)
    			{
    				for (int n = 0; n < ja.length(); n = n + 2)
    				{
    					
    					try {
							GeoPoint gp = new GeoPoint(ja.getDouble(n), ja.getDouble(n + 1));
							result.add(gp);
    					} catch (JSONException e) {
							e.printStackTrace();
						}
    				}
    			}
    		}
    	}
    	
    	return result;
    }
    
    @SuppressWarnings("unchecked")
	public static List<String> deSerializeDescription(String serialized)
    {
    	List<String> result = new ArrayList<String>();
    	
    	if (!Boolean.parseBoolean(ServerProperties.GetProperty("SerializeToJSON")))
    	{
	    	try
	    	{
	    		InputStream is = new ByteArrayInputStream(serialized.getBytes());
		    	XMLDecoder xmlDecoder = new XMLDecoder(is);
		    	result = (List<String>) xmlDecoder.readObject();
		    	xmlDecoder.close();
	    	}
	    	catch (Exception e)
	    	{
	    		e.printStackTrace();
	    	}
    	}
    	else
    	{
    		JSONObject obj = null;

    		try 
			{
				obj = new JSONObject(serialized);
    		
	    		if (obj != null)
	    		{
	    			String[] keys = {"description", "openFrom", "openTo", "img", "date"};
	    			
	    			for (String key : keys)
	    			{
		    			result.add(obj.getString(key));
	    			}
	    		}
    		}
    		catch (Exception e) { e.printStackTrace();}
    	}
    	
    	return result;
    }

    public static List<Point2D> parseHtmlToPoint2DList(String htmlData)
    {
    	List<Point2D> result = new ArrayList<Point2D>();

    	try
    	{
	    	int start = htmlData.indexOf("ctx.moveTo");
	    	int end = htmlData.indexOf("ctx.stroke();");
	
	    	String coordsData = htmlData.substring(start, end);
	    	
	    	coordsData = coordsData.replace(" ", "");
	    	coordsData = coordsData.replace("\t", "");
	    	coordsData = coordsData.replace("ctx.moveTo(", "");
	    	coordsData = coordsData.replace("ctx.lineTo(", "");
	    	coordsData = coordsData.replace(");", "");
	
	    	String[] sa = coordsData.split("\n");
	    	
	    	for (int n = 0; n < sa.length; n++)
	    	{
	    		String[] st = sa[n].split(",");
	    		double x = Double.parseDouble(st[0]);
	    		double y = Double.parseDouble(st[1]);
	    		result.add(new Point2D.Double(x, y));
	    	}
	    	}
    	catch (Exception e) {}
    	
    	return result;
    }

    @SuppressWarnings("unchecked")
	public static List<Point2D> deSerializePoint2DList(String serialized)
    {
    	List<Point2D> result = new ArrayList<Point2D>();
    	if (serialized == null) return result;
    	
    	if (!Boolean.parseBoolean(ServerProperties.GetProperty("SerializeToJSON")))
    	{
	    	try
	    	{
	    		InputStream is = new ByteArrayInputStream(serialized.getBytes());
		    	XMLDecoder xmlDecoder = new XMLDecoder(is);
		    	result = (List<Point2D>) xmlDecoder.readObject();
		    	xmlDecoder.close();
	    	}
	    	catch (Exception e)
	    	{
	    		e.printStackTrace();
	    	}
    	}
    	else
    	{
    		JSONObject obj = null;

    		try 
			{
				obj = new JSONObject(serialized);
			} 
    		catch (JSONException e) 
    		{
    			e.printStackTrace();
    		}
    		
    		if (obj != null)
    		{
    			JSONArray ja = null;
    			try 
    			{
					ja = obj.getJSONArray("coords");
				} catch (JSONException e) {
					e.printStackTrace();
				}
    			
    			if (ja != null)
    			{
    				for (int n = 0; n < ja.length(); n = n + 2)
    				{
    					
    					try {
							Point2D p = new Point2D.Double(ja.getDouble(n), ja.getDouble(n + 1));
							result.add(p);
    					} catch (JSONException e) {
							e.printStackTrace();
						}
    				}
    			}
    		}
    	}
    	
    	return result;
    }
    
    public static long[] deSerializeIds(String serialized)
    {
		if (serialized == null) return new long[0];
		
    	try
    	{
			serialized = serialized.substring(8, serialized.length() - 2);
			String[] sids = serialized.split(",");
			long[] lids = new long[sids.length];
			for (int n = 0; n < sids.length; n++ )
			{
				lids[n] = Long.parseLong(sids[n]);
			}
	    	
			return lids;
    	}
    	catch (Exception e){}
    	
    	return new long[0];
    }
    
    public static void exportSkiAreaToKml(SkiArea sa)
    {
    	Kml kml = new Kml();
    	
    	Folder fd = kml.createAndSetFolder().withName(sa.getName()).withOpen(true);

    	if (!sa.getSkiAreas().isEmpty())
    	{
    		for (SkiArea saa : sa.getSkiAreas())
    		{
        		Folder f = fd.createAndAddFolder().withName(saa.getName()).withOpen(true);
    			addSkiAreaToKml(saa, f);
    		}
    		
    	}
    	else
    	{
			addSkiAreaToKml(sa, fd);
    	}
    	
    	try 
    	{
    		File dir = new File(ServerProperties.GetProperty("RootDownloadsPath") + "kml");
    		try
    		{
    			dir.mkdirs();
    		}
    		catch (Exception e){}

    		File file = new File(ServerProperties.GetProperty("RootDownloadsPath") + "kml/" + sa.getId() + ".kml");
        	if (file.exists()) file.delete();

        	FileOutputStream os = new FileOutputStream(file);

        	try
        	{
        		kml.marshal(os);
        	}
        	catch (Exception e){}
        	finally
        	{
        		try
        		{
					os.close();
				} catch (IOException e) {}
        	}
		} catch (FileNotFoundException e) {}
    }
    
    private static void addSkiAreaToKml(SkiArea sa, Folder f)
    {
    	for (LayerData ld : sa.getLayerDatas())
    	{
    		try
    		{
    			List<GeoPoint> pl = deSerializeGeoPointList(ld.getData());
    		
    	    	Placemark pm = f.createAndAddPlacemark().withName(ld.getName());

    	    	if (ld.getLayerType().getGroupType() == 1)
	    		{
		    		pm.createAndSetPoint().addToCoordinates(pl.get(0).getLongitude(), pl.get(0).getLatitude()).withExtrude(true);
	    		}
    	    	else if (ld.getLayerType().getGroupType() == 2)
	    		{
		    		LineString ls = pm.createAndSetLineString().withExtrude(true);
		    		for (GeoPoint gp : pl)
		    		{
		    			ls.addToCoordinates(gp.getLongitude(), gp.getLatitude());
		    		}
	    		}
    	    	else if (ld.getLayerType().getGroupType() == 3)
	    		{
		    		LinearRing lr = new LinearRing().withExtrude(true);
		    		for (GeoPoint gp : pl)
		    		{
		    			lr.addToCoordinates(gp.getLongitude(), gp.getLatitude());
		    		}
		    		Boundary b = new Boundary().withLinearRing(lr);
    	    		pm.createAndSetPolygon().withOuterBoundaryIs(b);
	    		}
    		}
    		catch (Exception e){}
    	}
    }
}
