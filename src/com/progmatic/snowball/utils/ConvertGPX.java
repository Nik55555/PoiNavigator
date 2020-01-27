package com.progmatic.snowball.utils;

//import android.support.annotation.NonNull;
//import android.util.Log;

import com.progmatic.snowball.entity.TrackPoint;
import com.progmatic.snowball.entity.UserPoint;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class ConvertGPX
{

  private static final int indent = 4;

  public static boolean Export(List<TrackPoint> track, File file)
  {
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder;
    try
    {
      docBuilder = docFactory.newDocumentBuilder();
      // root elements
      Document doc = docBuilder.newDocument();
      Element rootElement = doc.createElement("gpx");
      doc.appendChild(rootElement);

      Element trk = doc.createElement("trk");
      rootElement.appendChild(trk);

      addTextNode(doc, trk, "name", "SNOWBALLTRK");
      addTextNode(doc, trk, "desc", "");

      Element trkseg = doc.createElement("trkseg");
      trk.appendChild(trkseg);

      for (TrackPoint point : track)
      {
        Element pt = doc.createElement("trkpt");
        trkseg.appendChild(pt);

        Attr attr = doc.createAttribute("lat");
        attr.setValue(String.valueOf(point.getLatitude()));
        pt.setAttributeNode(attr);

        attr = doc.createAttribute("lon");
        attr.setValue(String.valueOf(point.getLongitude()));
        pt.setAttributeNode(attr);

        addTextNode(doc, pt, "ele", String.valueOf(point.getElevation()));
        addTextNode(doc, pt, "time", TimeToString.convert(point.getTime()));

      }

      // write the content into xml file
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      DOMSource source = new DOMSource(doc);
      StreamResult result = new StreamResult(file);

      //transformerFactory.setAttribute("indent-number", indent);
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.transform(source, result);
      return true;
    }
    catch (ParserConfigurationException | TransformerException e)
    {
      e.printStackTrace();
    }

    return false;
  }

  private static void addTextNode(Document doc, Element ele, String element, String text)
  {
    Element e = doc.createElement(element);
    e.appendChild(doc.createTextNode(text));
    ele.appendChild(e);
  }

/* Usage:
          String path = Environment.getExternalStorageDirectory()+ File.separator+"20140131_085431.gpx";
          FileInputStream is = null;
          try {
            is = new FileInputStream(path);
            List<TrackPoint> list = ConvertGPX.Import(is);
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          } catch (IOException e) {
            e.printStackTrace();
          }
 */


  public static List<TrackPoint> Import(InputStream inputStream)
  {
//    Log.("Parse gpx", "Start");
    List<TrackPoint> result = new ArrayList<TrackPoint>();

    try {
      Document xmldoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
      NodeList nodes = xmldoc.getElementsByTagName("trkpt");
      for (int i = 0; i < nodes.getLength(); i++) {
        Node node = nodes.item(i);
        TrackPoint tp = new TrackPoint();
        tp.setLatitude(Double.parseDouble(node.getAttributes().getNamedItem("lat").getTextContent()));
        tp.setLongitude(Double.parseDouble(node.getAttributes().getNamedItem("lon").getTextContent()));
        
        if(node.hasChildNodes())
        {
          NodeList childs =  node.getChildNodes();
          for (int j = 0; j <childs.getLength(); j++) {
            Node child = childs.item(j);
            switch (child.getNodeName())
            {
              case "ele":
                tp.setElevation((int)Double.parseDouble(child.getTextContent()));
                break;
              case "time":
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                try {
                  tp.setTime(sdf.parse(child.getTextContent()));
                } catch (ParseException e) {
                  e.printStackTrace();
                }
                break;
              default:break;
            }
          }
        }

        result.add(tp);
      }
    } catch (SAXException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }

//    Log.d("Parse gpx", String.format("End: (%d points)",  result.size()));
    return result;
  }

  public static List<UserPoint> ImportUserPoints(InputStream inputStream)
  {
//    Log.("Parse gpx", "Start");
    List<UserPoint> result = new ArrayList<UserPoint>();

    try {
      Document xmldoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
      NodeList nodes = xmldoc.getElementsByTagName("gpx");
      for (int i = 0; i < nodes.getLength(); i++) {
        Node node = nodes.item(i);
        UserPoint up = new UserPoint();
        up.setLatitude(Double.parseDouble(node.getAttributes().getNamedItem("lat").getTextContent()));
        up.setLongitude(Double.parseDouble(node.getAttributes().getNamedItem("lon").getTextContent()));
        
        if(node.hasChildNodes())
        {
          NodeList childs =  node.getChildNodes();
          for (int j = 0; j <childs.getLength(); j++) {
            Node child = childs.item(j);
            switch (child.getNodeName())
            {
              case "ele":
                up.setElevation((int)Double.parseDouble(child.getTextContent()));
                break;
              case "time":
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                try {
                  up.setDate(sdf.parse(child.getTextContent()));
                } catch (ParseException e) {
                  e.printStackTrace();
                }
                break;
              case "name":
                up.setName(child.getTextContent());
            	break;
              case "cmt":
            	up.setPointType(Integer.parseInt(child.getTextContent()));
            	break;
              default:break;
            }
          }
        }

        result.add(up);
      }
    } catch (SAXException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }

//    Log.d("Parse gpx", String.format("End: (%d points)",  result.size()));
    return result;
  }

}
