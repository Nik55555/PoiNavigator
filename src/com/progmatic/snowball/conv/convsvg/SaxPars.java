// разбор и конвертация OSM

package com.progmatic.snowball.conv.convsvg;

import com.progmatic.snowball.navigator.Elevation;
import com.progmatic.snowball.navigator.GeoPoint;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;


class SaxPars extends SVGSaxParser {
    
    public static final int CONV_DEF_AERIALWAY = 55; // тип по умолчанию для подъёмника
    public static final int CONV_DEF_DOWNHILL = 56; // тип по умолчанию для трассы
    
    public static final String WAY = "way";
    public static final String RELATION = "relation";
    public static final String PISTETYPE = "piste:type";
    public static final String PISTEDIFF = "piste:difficulty";
    
    int savedNodes = 0;
    int checkedNodes = 0;
    
    public static boolean checkNodes = true;

    String nodes;
    String osmType;
    String osmTypeDiff;
    String members;
    
    @Override
    void printMessage() {
        System.out.println(
            "Parsed tags: " + counter + 
            ", saved/checked nodes: " + savedNodes + "/" + checkedNodes +
            " ways: " + savedWays + "/" + checkedWays +
            " relations: " + savedRelations + "/" + checkedRelations +
            " intersections: " + countIntersections
        );
    }
    
    // конвертация типа OSM в snowball layer_type_id
    Long convert_osm_type(String osmType) {
        Long snowballType = (long) -1;
        
        if (osmType.contains(AERIALWAY))
            snowballType = (long) CONV_DEF_AERIALWAY;
        else if (osmType.contains(DOWNHILL))
            snowballType = (long) CONV_DEF_DOWNHILL;
        
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = connection.prepareStatement(
           "SELECT layer_type_id FROM osm_match WHERE osm_type=?" 
            );
            pstmt.setString(1, osmType);
            rs = pstmt.executeQuery();
            if (rs.next())
                snowballType = rs.getLong("layer_type_id");

        } catch (SQLException ex) {
            if (pstmt != null)
                try {
                    pstmt.close();
                } catch (Exception e) {}
                    
            if (rs != null)
                try {
                    rs.close();
                } catch (Exception e) {}
                    
        } finally {
            if (pstmt != null)
                try {
                    pstmt.close();
                } catch (Exception e) {}
                    
            if (rs != null)
                try {
                    rs.close();
                } catch (Exception e) {}
        }
        
        return snowballType;
    }
    
    @Override
    public void startDocument() throws SAXException {
        System.out.println("Connecting to Database...");
        connectDB();
        System.out.println("Succesfully!");
        System.out.println("Start parse OSM data...");
        
        // удаляем из файлов все трассы и подъёмники, связанные с этим SVG файлом
        PreparedStatement pstmt = null;
        try {
            // сперва удаляем все сегменты
            stmt.executeUpdate("DELETE FROM ski_area_import");
            stmt.executeUpdate("DELETE FROM layer_data_import");
        } catch (SQLException ex) {
            log("Ошибка при попытке удалить записи из таблиц ski_area_import, layer_data_import");
        }
    }
    
    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        switch (currentElement) {
            case WAY:
                if (qName.equals(WAY)) {
                    subElement = "";
                } else {
                    subElement = qName;
                }   break;
            case RELATION:
                if (qName.equals(RELATION)) {
                    subElement = "";
                } else {
                    subElement = qName;
            }   break;
            default:
                currentElement = qName;
                break;
        }
        
        switch (currentElement) {
            case "node":
                if (!checkNodes)
                    return;
                id = new Long(atts.getValue("id"));
                String lat = atts.getValue("lat");
                String lon = atts.getValue("lon");
                // поиск точки
                ResultSet rs = null;
                try {
                    rs = stmt.executeQuery( "SELECT * FROM osm_nodes WHERE id=" + id + ";" );
                    checkedNodes++;
                    if (!rs.next()) {
                        // добавление точки
                        stmt.executeUpdate(
                                "INSERT INTO osm_nodes (id, lat, lon) " +
                                        "VALUES (" + id + ", " + lat + ", " + lon + ");"
                        );
                        savedNodes++;
                    }
                } catch (SQLException ex) {
                    if (rs != null)
                        try {
                            rs.close();
                        } catch (Exception e) {}
                    
                    log("Error insert node... Id=" + id + ", lat=" + lat + ", lon=" + lon);
                } finally {
                    if (rs != null)
                        try {
                            rs.close();
                        } catch (Exception e) {}
                }   break;
            case WAY:
                // подъёмники и трассы
                switch (subElement) {
                    case "":
                        // корневой элемент
                        id = new Long(atts.getValue("id"));
                        name = "";
                        nodes = "";
                        osmType = "";
                        osmTypeDiff = "";
                        break;
                    case "nd":
                        // точка в порядке следования
                        nodes += atts.getValue("ref") + ",";
                        break;
                    case "tag":
                        // определение типа пути: название, тип(aerialway/piste), сколько мест, сложность, тип лыжни (downhill)
                        String k = atts.getValue("k");
                        switch (k) {
                            case "name":
                                name = atts.getValue("v");
                                break;
                            case AERIALWAY:
                                // подъёмник
                                osmType = AERIALWAY + " " + atts.getValue("v") + osmType;
                                break;
                            case AERIALWAY + ":occupancy":
                                break;
                            case PISTETYPE:
                                // трасса тип
                                String t = atts.getValue("v");
                                if (t.equals(DOWNHILL)) {
                                    osmType = t + osmType;
                                } else {
                                    skipWay = true;
                                }
                                break;
                            case PISTEDIFF:
                                // трасса сложность
                                osmTypeDiff = " " + atts.getValue("v");
                                break;
                        }
                        break;
                }
                break;
            case RELATION:
                // связанные данные (места катания)
                switch (subElement) {
                    case "":
                        // корневой элемент
                        id = new Long(atts.getValue("id"));
                        name = "";
                        members = "";
                        osmType = "";
                        break;
                    case "member":
                        // трассы и подъёмники
                        if ((atts.getValue("type")).equals("way"))
                            members += atts.getValue("ref") + ",";
                        break;
                    case "tag":
                        // название, тип
                        String k = atts.getValue("k");
                        switch (k) {
                            case "name":
                                name = atts.getValue("v");
                                break;
                            case PISTETYPE:
                                // трасса тип
                                String t = atts.getValue("v");
                                if (t.equals(DOWNHILL)) {
                                    osmType = t + osmType;
                                } else {
                                    skipRelation = true;
                                }
                                break;
                            case PISTEDIFF:
                                // трасса сложность
                                osmType += " " + atts.getValue("v");
                                break;
                        }
                        break;
                }
                break;
        }
    }
    
    @Override 
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        switch (currentElement) {
            case WAY:
                if (qName.equals(WAY)) {
                    currentElement = "";
                    
                    // запись в базу трасс и подъёмников
                    if (osmType.equals(""))
                        skipWay = true;
                    
                    if ((!osmType.contains(DOWNHILL)) && (!osmType.contains(AERIALWAY)))
                        skipWay = true;
                    
                    if (skipWay) {
                        skipWay = false;
                    } else {
                        ResultSet rs = null;
                        PreparedStatement pstmt = null;
                        try {
                            // поиск
                            rs = stmt.executeQuery( "SELECT * FROM osm_way WHERE id=" + id + ";" );
                            checkedWays++;
                            if (!rs.next()) {
                                // добавление
                                if (nodes.length() > 0)
                                    // удаляем последнюю запятую
                                    nodes = nodes.substring(0, nodes.length() - 1);
                                
                                if (!osmType.contains(AERIALWAY))
                                    osmType += osmTypeDiff;
                                
                                // убираем вероятные пробелы + переводим в нижний регистр
                                osmType = osmType.trim().toLowerCase();
                                
                                // для записей, содержащих указание, что это трасса и подъёмник одновременно,
                                // считаем, что это подъёмник
                                if ((osmType.contains(AERIALWAY)) && (osmType.contains(DOWNHILL)))
                                    osmType = osmType.replace(DOWNHILL, "");
                                
                                if (name.length() > 500)
                                    name = name.substring(0, 500);
                                
                                pstmt = connection.prepareStatement(
                                        "INSERT INTO osm_way (id, nodes, osm_type, name) " +
                                                "VALUES (?,?,?,?);"
                                );
                                pstmt.setLong(1, id);
                                pstmt.setString(2, nodes);
                                pstmt.setString(3, osmType);
                                pstmt.setString(4, name);
                                pstmt.executeUpdate();
                                
                                savedWays++;
                            }
                        } catch (SQLException ex) {
                            if (rs != null)
                                try {
                                    rs.close();
                                } catch (Exception e) {}
                            if (pstmt != null)
                                try {
                                    pstmt.close();
                                } catch (Exception e) {}
                            log("Error insert way... Id=" + id + ", osmType=" + osmType + ", nodes=" + nodes);
                        } finally {
                            if (rs != null)
                                try {
                                    rs.close();
                                } catch (Exception e) {}
                            if (pstmt != null)
                                try {
                                    pstmt.close();
                                } catch (Exception e) {}
                        }
                    }
                } else {
                    subElement = "";
                }   break;
            case RELATION:
                // связанные данные - места катания
                if (qName.equals(RELATION)) {
                    currentElement = "";
                    
                    if (members.length() > 0)
                        // удаляем последнюю запятую
                        members = members.substring(0, members.length() - 1);
                    // убираем вероятные пробелы + переводим в нижний регистр
                    osmType = osmType.trim().toLowerCase();
                    
                    ArrayList<LayerData> memberList = new ArrayList<>();
                    
                    double minX = 1000;
                    double minY = 1000;
                    double maxX = -1000;
                    double maxY = -1000;
                    
                    ResultSet rs = null;
                    PreparedStatement pstmt_node = null;
                    PreparedStatement pstmt = null;
                    try {
                        // проверяем на наличие среди members хотя бы одной горнолыжной трассы
                        // трассы и подъёмники
                        StringTokenizer stm = new StringTokenizer(members, ",");
                        while(stm.hasMoreTokens()){
                            Long member = new Long(stm.nextToken());
                            
                            // ищем ways
                            rs = stmt.executeQuery( "SELECT * FROM osm_way WHERE id=" + member + ";" );
                            if (rs.next()) {
                                LayerData ld = new LayerData();
                                ld.osmType = rs.getString("osm_type");
                                ld.layer_type_id = convert_osm_type(ld.osmType);
                                ld.name = rs.getString("name");
                                ld.data = jsonPrefix;
                                ld.osmId = member;
                                String _nodes = rs.getString("nodes");
                                ld.x1 = notExist;
                                ld.y1 = notExist;
                                // точки трасс и подъёмников
                                StringTokenizer stp = new StringTokenizer(_nodes, ",");
                                pstmt_node =
                                        connection.prepareStatement("SELECT * FROM osm_nodes WHERE id=?");
                                while(stp.hasMoreTokens()){
                                    Long node = new Long(stp.nextToken());
                                    ResultSet rsp;
                                    pstmt_node.setLong(1, node);
                                    rsp = pstmt_node.executeQuery();
                                    if (rsp.next()) {
                                        if ((ld.x1 == notExist) && (ld.y1 == notExist)) {
                                            ld.x1 = rsp.getDouble("lat");
                                            ld.y1 = rsp.getDouble("lon");
                                        }
                                        ld.x2 = rsp.getDouble("lat");
                                        ld.y2 = rsp.getDouble("lon");
                                        ld.data += ld.x2 + "," + ld.y2 + ",";
                                        
                                        // определяем координаты места катания
                                        minX = Math.min(minX, ld.x2);
                                        minY = Math.min(minY, ld.y2);
                                        maxX = Math.max(maxX, ld.x2);
                                        maxY = Math.max(maxY, ld.y2);
                                    }
                                }
                                
                                ld.data += "]}";
                                
                                if ((ld.x1 != notExist) && (ld.y1 != notExist))
                                    memberList.add(ld);
                            }
                        }
                        
                        if (!memberList.isEmpty()) {
                            savedRelations++;
                            
                            //проводим операции над списком LayerData
                            // делим трассы в случае попадания конечной точки одной трассы на узел другой
                            // объединяем близлежащие (на расстоянии < MAXROUND) узлы
                            // определяем по всем оставшимся одиночным концам трасс расстояние до ближайшего отрезка
                            // и если находим меньше MAXROUND, соединяем с ближайшей точкой этого отрезка
                            divideSlopeNodes(memberList);
                            
                            // отсекаем вырожденные трассы, появившиеся в результате деления и объединения
                            removeDegenerateLines(memberList);

                            //ищем пересечения
                            divideSlopeSegments(memberList);

                            // отсекаем вырожденные трассы, появившиеся в результате пересечений отрезков
                            removeDegenerateLines(memberList);
                            
                            // сортируем направление всех трасс по высоте
                            organizePoints(memberList);
                            
                            // пишем relation в места катания
                            name += " (OSM " + id + ")";
                            
                            pstmt = connection.prepareStatement(
                                    "INSERT INTO ski_area_import (name, x1, y1, x2, y2) " +
                                            "VALUES (?,?,?,?,?)"
                            );
                            pstmt.setString(1, name);
                            pstmt.setDouble(2, minX);
                            pstmt.setDouble(3, minY);
                            pstmt.setDouble(4, maxX);
                            pstmt.setDouble(5, maxY);
                            pstmt.executeUpdate();
                            
                            // получаем id только что записанной области катания
                            pstmt = connection.prepareStatement( "SELECT id FROM ski_area_import WHERE name=? AND x1=? AND y1=? AND x2=? AND y2=?");
                            pstmt.setString(1, name);
                            pstmt.setDouble(2, minX);
                            pstmt.setDouble(3, minY);
                            pstmt.setDouble(4, maxX);
                            pstmt.setDouble(5, maxY);
                            
                            rs = pstmt.executeQuery();
                            if (rs.next()) {
                                skiAreaId = rs.getLong("id");
                            }
                            
                            // пишем трассы и подъёмники записанного места катания в layer_data
                            pstmt = connection.prepareStatement(
                                    "INSERT INTO layer_data_import (ski_area_id, name, x1, y1, x2, y2, data, layer_type_id) " +
                                            "VALUES (?,?,?,?,?,?,?,?)");
                            
                            for (int i = 0, j = memberList.size(); i < j; i++) {
                                LayerData ld = memberList.get(i);
                                
                                setRectangle(ld);
                                
                                pstmt.setLong(1, skiAreaId);
                                pstmt.setString(2, ld.name + " (OSM " + ld.osmId + ")");
                                pstmt.setDouble(3, ld.x1);
                                pstmt.setDouble(4, ld.y1);
                                pstmt.setDouble(5, ld.x2);
                                pstmt.setDouble(6, ld.y2);
                                pstmt.setString(7, ld.data);
                                pstmt.setLong(8, ld.layer_type_id);
                                pstmt.executeUpdate();
                            }
                        }
                        
                    } catch (SQLException ex) {
                        if (rs != null)
                            try {
                                rs.close();
                            } catch (Exception e) {}
                        if (pstmt != null)
                            try {
                                pstmt.close();
                            } catch (Exception e) {}
                        if (pstmt_node != null)
                            try {
                                pstmt_node.close();
                            } catch (Exception e) {}
                        log(ex.getMessage());
                    } finally {
                        if (rs != null)
                            try {
                                rs.close();
                            } catch (Exception e) {}
                        if (pstmt != null)
                            try {
                                pstmt.close();
                        } catch (Exception e) {}
                    if (pstmt_node != null)
                        try {
                            pstmt_node.close();
                        } catch (Exception e) {}
                    }
                } else {
                    subElement = "";
                }
                break;
            default:
                currentElement = "";
                break;
        }
        counter++;
        if (counter % NODES_PRINT_INTERVAL == 0)
            printMessage();
    }
    
    @Override
    public void endDocument() {
    	// закрываем соединения
        try {
            if (stmt != null)
                stmt.close();
            if (connection != null)
                connection.close();
        } catch (SQLException ex) {
            Logger.getLogger(SVGSaxParser.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (counter % NODES_PRINT_INTERVAL != 0)
            printMessage();
        System.out.println(filename + " parsed succesfully."); 
    }
    
    private void setRectangle(LayerData ld) {
        String s = stripJson(ld.data);
        ld.x1 = notExist;
        ld.y1 = notExist;
        ld.x2 = notExist;
        ld.y2 = notExist;
        
        double minX = 1000;
        double minY = 1000;
        double maxX = -1000;
        double maxY = -1000;
        
        StringTokenizer stp = new StringTokenizer(s, ",");
        if (stp.countTokens() >= 2) {
            while (stp.countTokens() >= 2) {
                double cx = new Double(stp.nextToken());
                double cy = new Double(stp.nextToken());

                // определяем координаты места катания
                minX = Math.min(minX, cx);
                minY = Math.min(minY, cy);
                maxX = Math.max(maxX, cx);
                maxY = Math.max(maxY, cy);
            }
            ld.x1 = minX;
            ld.y1 = minY;
            ld.x2 = maxX;
            ld.y2 = maxY;
        }
    }

    @Override
    protected boolean below(double xb, double yb, double xe, double ye) {
        boolean res = false;
        try {
            GeoPoint gp1 = new GeoPoint(xb, yb);
            GeoPoint gp2 = new GeoPoint(xe, ye);
            res = (Elevation.getElevation(gp1) < Elevation.getElevation(gp2));
        } catch(Exception e) {
            log(e.getMessage());
        }
        return res;
//        return (yb > ye);
    }
}