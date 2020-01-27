// разбор SVG на основе SAX

package com.progmatic.snowball.conv.convsvg;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class SVGSaxParser extends DefaultHandler {
    
    public static boolean debug = false;
    public static long skiAreaId = 0;
    
    public static double MAXROUND = 1.5; // точность приближения (используется также для отрезания кусков трасс)
    
    public static double ACCURACY = 0.01; // точность интерполяции ломаной по длине, а также точность равенства координат
    public static double ACCURACY_HALF = 0.005; // увеличенная вдвое точность равенства координат
    public static double ACCURACY_LIFT_STATION = 10; // точность в пикселях для определения промежуточных станций подъёмника. < этого числа от начальной или конечной точки считается начальной или конечной станцией, соответственно
    
    public static final int MAX_LINES = 1024; // максимальное количество отрезков ломаной
    
    public static final String SLOPE_ = "slope";
    public static final String LIFT_ = "lift";
    public static final String ICONS = "icons";
//    public static final String LAYER_ = "Layer_";
    public static final String PATH = "path";
    public static final String LINE = "line";
    public static final String POLYLINE = "polyline";
    public static final String CIRCLE = "circle";
    public static final String RECT = "rect";
    public static final String ID = "id";
    public static final String DISPLAY = "display";
    public static final String NONE = "none";

    public static final String DOWNHILL = "downhill";
    public static final String AERIALWAY = "aerialway";
    
    public static final int NODES_PRINT_INTERVAL = 1000;
    
    public static String connect_jdbc = "jdbc:postgresql://localhost:5432/snowball";
    public static String connect_lgn = "postgres";
    public static String connect_pwd = "postgres";
    
    public static String jsonPrefix = "{\"coords\":[";
    public static String jsonPostfix = "]}";
    
    public static String layersFN = "layer_svg"; // название таблицы для внесения данных о найденных трассах и подъёмниках
    public static String segmentsFN = "layer_svg_segments"; // название таблицы для записи выделенных сегментов трасс и подъёмников
    
    Connection connection;
    Statement stmt = null;
    
    String currentElement = "";
    String subElement = "";
    Long id;
    int counter = 0;
    int savedWays = 0;
    int checkedWays = 0;
    int savedRelations = 0;
    int checkedRelations = 0;
    int countIntersections = 0;
    
    ArrayList<Vector2> stations; // станции подъёмника
    
    String name;
    static String filename;
    int part = 0;
    int lavel = 0;
    boolean skipWay = false;
    boolean skipRelation = false;
    
    // блок переменных для масштабирования и смещения SVG
    private static double coeff = 1; // коэффициент для определения относительных координат в соответствии с картинкой jpg или png
    public static double image_height = 0; // высота картинки jpg или png
    public static double svg_height = 0;
    public static double vbX = 0;
    public static double vbY = 0;
    
    // список предупреждений
    public static String errors = "";
    
    public static final double notExist = 1000000; // заведомо несуществующая координата
    
    public static double x;
    public static double y;
    
    public static int toCorrect = 0;
    public static boolean roundToInt = false;
    
    public static boolean pEquals(double x1, double y1, double x2, double y2) {
        return (Math.abs(x1-x2) < ACCURACY) && (Math.abs(y1-y2) < ACCURACY);
    }
    
    public static boolean pEqualsHalf(double x1, double y1, double x2, double y2) {
        return (Math.abs(x1-x2) < ACCURACY_HALF) && (Math.abs(y1-y2) < ACCURACY_HALF);
    }
    
    public static double lineLength(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return (double) Math.sqrt(dx * dx + dy * dy);
    }

    public static double lineLength(Vector2 p1, Vector2 p2) {
        double dx = p2.x - p1.x, dy = p2.y - p1.y;
        return (double) Math.sqrt(dx * dx + dy * dy);
    }
    
    // определяем вырожденность линии - один отрезок длиной меньше MAXROUND
    public static boolean lineDegenerate(LayerData ld) {
        
        double x1;
        double y1;
        
        String s = stripJson(ld.data);
        
        StringTokenizer stp = new StringTokenizer(s, ",");
        if (stp.countTokens() >= 2) {
            x1 = new Double(stp.nextToken());
            y1 = new Double(stp.nextToken());
        } else
            // отсутствует долгота первой точки
            return true;

        double x2 = x1;
        double y2 = y1;
        
        int pointsNum = 1;
        
        while(stp.hasMoreTokens()){
            x2 = new Double(stp.nextToken());
            if (stp.countTokens() > 0) {
                y2 = new Double(stp.nextToken());
            } else {
                // отсутствует долгота последней точки
                return true;
            }
            pointsNum++;
        }
        return (lineLength(x1, y1, x2, y2) < MAXROUND) || (pointsNum < 2);
    }
    
    public int removeDegenerateLines(ArrayList<LayerData> ml) {
        int deleted = 0;
        for (int i = 0; i < ml.size(); i++) {
            LayerData ld = ml.get(i);
            // проверка трассы на вырожденность - добавляем только длиннее MAXROUND
            if (lineDegenerate(ld)) {
                ml.remove(i);
                i--;
                deleted++;
            }
        }
        return deleted;
    }
    
    static boolean aboutTCross(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        boolean res = false;

        toCorrect = 0;
        
/*        if ((x1 <= x && x2 >= x) || (x2 <= x && x1 >= x)) {
            if ((x3 <= x && x4 >= x) || (x4 <= x && x3 >= x)) {
                res = true;
            } else {
                if (line2start && lineLength(x, y, x3, y3) < MAXROUND) {
                    // скорректировать точку 3
                    toCorrect = 3;
                    res = true;
                } else if (line2finish && lineLength(x, y, x4, y4) < MAXROUND) {
                    // скорректировать точку 4
                    toCorrect = 4;
                    res = true;
                } else  {
                    res = false;
                }
            }
        } else if ((x3 <= x && x4 >= x) || (x4 <= x && x3 >= x)) {
            if ((x1 <= x && x2 >= x) || (x2 <= x && x1 >= x)) {
                res = true;
            } else {
                if (line1start && lineLength(x, y, x1, y1) < MAXROUND) {
                    // скорректировать точку 1
                    toCorrect = 1;
                    res = true;
                } else if (line1finish && lineLength(x, y, x2, y2) < MAXROUND) {
                    // скорректировать точку 2
                    toCorrect = 2;
                    res = true;
                } else  {
                    res = false;
                }
            }
        }
*/
        return res;
    }
    
    public static boolean findIntersection(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
    // находим точку пересечения отрезков
    
        if (pEquals(x1, y1, x2, y2) || pEquals(x3, y3, x4, y4) || // если отрезки вырождены в точку
            pEquals(x1, y1, x3, y3) || pEquals(x1, y1, x4, y4) || // если одна точка отрезка 1 совпадает
            pEquals(x2, y2, x3, y3) || pEquals(x2, y2, x4, y4))   // с какой-либо точкой отрезка 2
            return false;
        
        double dx2 = x4 - x3;
        double dx1 = x2 - x1;
        
        if (Math.abs(dx2) < ACCURACY) {
            if (Math.abs(dx2) < ACCURACY)
                // оба отрезка параллельны оси Y
                return false;
            else
                // отрезок 1 параллелен оси Y, а отрезок 2 нет:
                // меняем отрезки местами для исключения деления на ноль
                return findIntersection(x3, y3, x4, y4, x1, y1, x2, y2);
        }
        
        double dy1 = y2 - y1;
        double dy2 = y4 - y3;
        
	x = dy1 * dx2 - dy2 * dx1;
	if((Math.abs(x) < ACCURACY*ACCURACY)) // прямые параллельны
            return false;
        // находим пересечение прямых
	y = x3 * y4 - y3 * x4;
	x = ((x1 * y2 - y1 * x2) * dx2 - y * dx1) / x;
	y = (dy2 * x - y) / dx2;
        
        // проверка на принадлежнось точки отрезкам
        // или на допустимую близость, если не пренадлежит отрезкам
        
	return 
            ((x1 <= x && x <= x2) || (x2 <= x && x <= x1))&&
            ((x3 <= x && x <= x4) || (x4 <= x && x <= x3));
//            ((x1 - ACCURACY <= x && x <= x2 + ACCURACY) || (x2 - ACCURACY <= x && x <= x1 + ACCURACY))&&
//            ((x3 - ACCURACY <= x && x <= x4 + ACCURACY) || (x4 - ACCURACY <= x && x <= x3 + ACCURACY));
    }
    
    void changePoint(LayerData ld, double oldX, double oldY, double newX, double newY) {
                        String s = newX + "," + newY;
                        ld.data = ld.data.replaceAll(oldX + "," + oldY, s);
                        // если в результате предыдущей операции появился вырожденный отрезок: убираем
                        ld.data = ld.data.replaceAll(s + "," + s, s);
    }
    

    // Определяем, совпадает ли второй конец отрезка конечной точки (n.x, n.y) с одним из концов отрезка (x1, y1), (x2, y2)
    boolean prevPointEqualEndpoint(NodeNear n, double x1, double y1, double x2, double y2) {
        String s = n.layerData.data;
        s = stripJson(s);
        StringTokenizer stp = new StringTokenizer(s, ",");
        
        double nx;
        double ny;
        double pre_nx = 0;
        double pre_ny = 0;
        
        if (stp.countTokens() >= 2) {
            nx = new Double(stp.nextToken());
            ny = new Double(stp.nextToken());
        } else
            // отсутствует долгота первой точки
            return false;
        
        if (pEquals(n.x, n.y, nx, ny)) {
            // n - начальная точка
            if (stp.countTokens() >= 2) {
                nx = new Double(stp.nextToken());
                ny = new Double(stp.nextToken());
            } else
                // отсутствует долгота первой точки
                return false;
            return pEquals(nx, ny, x1, y1) || pEquals(nx, ny, x2, y2);
        } else {
            while(stp.hasMoreTokens()){
                pre_nx = nx;
                pre_ny = ny;
                nx = new Double(stp.nextToken());
                if (stp.countTokens() > 0) {
                    ny = new Double(stp.nextToken());
                } else {
                    // отсутствует долгота последней точки
                    return false;
                }
            }
            return pEquals(nx, ny, n.x, n.y) && (pEquals(pre_nx, pre_ny, x1, y1) || pEquals(pre_nx, pre_ny, x2, y2));
        }

    }
    
    void removePoint(LayerData ld, double x1, double y1) {
        String s = ld.data;
        String points = x1 + "," + y1;
        s = s.replace(points, "");
        s = s.replace(",,", ",");
        s = s.replace(jsonPrefix + ",", jsonPrefix);
        s = s.replace("," + jsonPostfix, jsonPostfix);
        ld.data = s;
    }
    
    
    void printMessage() {
        System.out.println(
            "Parsed tags: " + counter + 
            " ways: " + savedWays + "/" + checkedWays +
            " relations: " + savedRelations + "/" + checkedRelations +
            " intersections: " + countIntersections
        );
    }
    
    
    public String randomColor() {
        Random random = new Random();
        String colors[] = 
            {"BLUE", "GREEN", "MAGENTA", "RED", "CYAN", "ORANGE", "YELLOW", "GRAY", "PINK",
             "DARKBLUE", "DARKGREEN", "DARKMAGENTA", "DARKRED", "DARKCYAN", "DARKORANGE", "DARKGRAY"};
        int pos = random.nextInt(colors.length);
        return (colors[pos]);
    }
    
    void connectDB() {
        try {
                Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
                System.out.println("PostgreSQL JDBC Driver not found!");
                return;
                //System.exit(0);
        }
        try {
            connection = DriverManager.getConnection(
                connect_jdbc, connect_lgn, connect_pwd);
            stmt = connection.createStatement();
            
        } catch (SQLException e) {
            throw new ParseException("Connection failed! Check login and password or host and port.", 0);
        }
    }
    
    @Override
    public void startDocument() throws SAXException {
        System.out.println("Connecting to Database...");
        connectDB();
        System.out.println("Succesfully!");
        System.out.println("Start parse SVG data...");
        
        // удаляем из файлов все трассы и подъёмники, связанные с этим SVG файлом
        PreparedStatement pstmt = null;
        try {
            // сперва удаляем все сегменты
            pstmt = connection.prepareStatement(
                    "DELETE FROM " + segmentsFN + " " +
                    "WHERE (layer_svg_id in " +
                    "(SELECT id FROM " + layersFN + " WHERE ski_area_id = ?))"
            );
            pstmt.setLong(1, skiAreaId);
            pstmt.executeUpdate();
            
            pstmt = connection.prepareStatement(
                    "DELETE FROM " + layersFN + " " +
                    "WHERE ski_area_id = ?"
            );
            pstmt.setLong(1, skiAreaId);
            pstmt.executeUpdate();
            
            pstmt.close();
        } catch (SQLException ex) {
            if (pstmt != null)
                try {
                    pstmt.close();
                } catch (SQLException e) {}

            log("Ошибка при попытке удалить записи из таблицы " + segmentsFN);
        }
        finally {
            if (pstmt != null)
                try {
                    pstmt.close();
                } catch (SQLException e) {}
        }
    }
    
    private boolean checkDisplay(Attributes atts) {
        return !NONE.equals(atts.getValue(DISPLAY));
    }
    
    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        
        // определяем размеры картинки и коэффициент
        if (qName.equals("svg")) {
            // определяем высоту
            String height_str = atts.getValue("height");
            if (height_str == null) {
                // критическая ошибка отсутствует высота картинки в SVG
                throw new ParseException("Критическая ошибка: отсутствует высота картинки в файле SVG!", 1);
            }
            
            // определяем viewBox и запоминаем смещение по первой координате
            String viewBox_str = atts.getValue("viewBox");
            if (viewBox_str != null) {
                // тэг найден, определяем координаты
                String[] vb_coords = viewBox_str.split(" ");
                vbX = new Double(vb_coords[0]);
                vbY = new Double(vb_coords[1]);
            } else {
                vbX = 0;
                vbY = 0;
            }
                
            if (svg_height == 0 && !height_str.equals("")) {
                if (height_str.contains("px")) {
                    // пиксели
                    svg_height = new Double(height_str.replace("px", ""));
                } else if (height_str.contains("mm")) {
                    // миллиметры
                    svg_height = (96 / 25.4) * new Double(height_str.replace("mm", ""));
                } else if (height_str.contains("cm")) {
                    // сантиметры
                    svg_height = (96 / 2.54) * new Double(height_str.replace("cm", ""));
                } else if (height_str.contains("in")) {
                    // дюймы
                    svg_height = 96 * new Double(height_str.replace("in", ""));
                } else {
                    // неизвестная единица
                    throw new ParseException("Ошибка в единице измерения высоты картинки (свойство height тега <svg>)", 3);
                }
                
                if (image_height != 0 && svg_height != 0)
                    // определяем коэффициент
                    coeff = image_height / svg_height;
            }
            return;
        }
        
        if (!checkDisplay(atts)) // если содержит аттрибут display="none" пропускаем
            return;
                 
        String idValue = atts.getValue(ID);
        if (idValue == null) 
            idValue = "";
        else
            idValue = idValue.toLowerCase();
        idValue = idValue.replace("scope", "slope").trim();
        
        if (qName.equals("g")) {
            if (idValue.contains(SLOPE_) || idValue.contains(LIFT_) || idValue.equals(ICONS) || lavel == 0) {
                currentElement = idValue.replaceAll("_", " ").trim();
                lavel = 1;
                part = 0;
                stations = new ArrayList<>();
            } else {
                lavel++;
            }
        }
        
        if (currentElement.contains(SLOPE_)) {
            if (qName.equals(PATH)) {
                // трасса
                String d = atts.getValue("d");
                BezierPath bp = new BezierPath();
                bp.parsePathString(d);
                
                part++;
                
                Vector2 lastV = null;
                double llen = 0;
                double plen = bp.pathLength(MAX_LINES) * coeff;
                
                String line = "";
                String data = "";
                int acc = 0; // отрезков
                
                if (plen > 0) {
                    // нормальный path
                
                    acc = 0; // начальное значение количества отрезков - 1

                    while (Math.abs(1 - llen / plen) > ACCURACY) {
                        line = "M";
                        data = "";

                        if (acc < MAX_LINES) {
                            acc++;
                            llen = 0;
                        } else
                            break;

                        for (int i = 0; i <= acc; i++) {
                            Vector2 v = bp.eval(((double)i)/acc);
                            v.x -= vbX;
                            v.x *= coeff;
                            v.y -= vbY;
                            v.y *= coeff;
                            line += v.x + "," + v.y + ((i != acc)? " " : "");
                            data += v.x + "," + v.y + ((i != acc)? "," : "");
                            if (i > 0)
                                llen += lineLength(lastV, v);
                            lastV = v;
                        }
                    }
                }

                // запись трассы в layer_svg
                LayerSVG svg = new LayerSVG();
                svg.name = makeMemberName(currentElement, SLOPE_);
                svg.part = part;
                svg.type = 1; // трасса
                svg.scheme_data = jsonPrefix + data + jsonPostfix;
                svg.svg_filename = filename;
                
                String info = "Трасса " + svg.name;
                if (plen > 0) {
                    info += "; точность " + String.format("%f", Math.abs(1 - llen / plen));
                    svg.status = 0;
                } else {
                    info += "; ошибка - линия не определена";
                    svg.status = -1;
                    log(info + "; вероятно, указана только одна точка: " + d);
                }
                info += "; отрезков " + acc;
                info += "; длина кривой " + String.format("%f", plen);
                info += "; длина ломаной " + String.format("%f", llen);
                info += "; path=\"" + d + "\"";

                if (svg.status == -1)
                    log(info, false);
                
                if (!debug) {
                    System.out.println(info);
                    System.out.println(line);
                }
                
                svg.info = info;

                // пишем трассы определённого места катания в layer_svg
                PreparedStatement pstmt = null;
                ResultSet rs = null;
                try {
                    // проверить наличие записи в базе
                    pstmt = connection.prepareStatement(
                        "SELECT * FROM " + layersFN + " WHERE name=? AND part=? AND type=? AND ski_area_id=?" 
                    );
                    pstmt.setString(1, svg.name);
                    pstmt.setInt(2, svg.part);
                    pstmt.setInt(3, 1);
                    pstmt.setLong(4, skiAreaId);
                    
                    rs = pstmt.executeQuery();
                    if (rs.next()) {
                        log("Логическая ошибка в " + filename + ": трасса " + svg.name + " сегмент " + svg.part + " уже присутствует в зоне катания " + skiAreaId);
                        return;
                    }
                    
                    // добавить запись
                    pstmt.close();
                    pstmt = connection.prepareStatement(
                        "INSERT INTO " + layersFN + " (name, part, scheme_data, svg_filename, info, status, type, ski_area_id) " +
                        "VALUES (?,?,?,?,?,?,?,?)");
                    pstmt.setString(1, svg.name);
                    pstmt.setInt(2, svg.part);
                    pstmt.setString(3, svg.scheme_data);
                    pstmt.setString(4, svg.svg_filename);
                    pstmt.setString(5, new SimpleDateFormat().format(new Date()) + " " + svg.info);
                    pstmt.setInt(6, svg.status);
                    pstmt.setInt(7, svg.type);
                    pstmt.setLong(8, skiAreaId);
                    pstmt.executeUpdate();
                } catch (SQLException ex) {
                    log("Ошибка при добавлении трассы " + svg.name + " сегмента " + svg.part + " в таблицу " + layersFN);
                } finally {
                    if (pstmt != null)
                        try {
                            pstmt.close();
                        } catch (SQLException e) {}
                	
                    if (rs != null)
                        try {
                            rs.close();
                        } catch (SQLException e) {}
                }
                if (!debug)
                    System.out.printf("Трасса %s сегмент %s добавлена в таблицу " + layersFN + "\n", svg.name, svg.part);
            }
        } else if (currentElement.contains(LIFT_)) {
            switch (qName) {
                case PATH:
                case LINE:
                case POLYLINE:
                    // подъёмник
                    part++;
                    double llen = 0; // длина полученной ломаной
                    double plen = 0; // длина линии из SVG файла
                    String line = "";
                    String data = "";
                    int acc = 0; // отрезков
                    String d = "";
                    if (qName.equals(PATH)) {
                        // линия, заданная кривыми Безье
                        d = atts.getValue("d");
                        BezierPath bp = new BezierPath();
                        bp.parsePathString(d);
                        
                        Vector2 lastV = null;
                        llen = 0;
                        plen = bp.pathLength(MAX_LINES) * coeff;
                        
                        if (plen > 0) {
                            // нормальный path
                            
                            acc = 0; // начальное значение количества отрезков - 1
                            
                            while (Math.abs(1 - llen / plen) > ACCURACY) {
                                line = "M";
                                data = "";
                                
                                if (acc < MAX_LINES) {
                                    acc++;
                                    llen = 0;
                                } else
                                    break;
                                
                                for (int i = 0; i <= acc; i++) {
                                    Vector2 v = bp.eval(((double)i)/acc);
                                    v.x -= vbX;
                                    v.x *= coeff;
                                    v.y -= vbY;
                                    v.y *= coeff;
                                    line += v.x + "," + v.y + ((i != acc)? " " : "");
                                    data += v.x + "," + v.y + ((i != acc)? "," : "");
                                    if (i > 0)
                                        llen += lineLength(lastV, v);
                                    lastV = v;
                                }
                            }
                        }
                    } else if (qName.equals(LINE)) {
                        // отрезок
                        double x1 = (new Double(atts.getValue("x1")) - vbX)  * coeff;
                        double y1 = (new Double(atts.getValue("y1")) - vbY)  * coeff;
                        double x2 = (new Double(atts.getValue("x2")) - vbX)  * coeff;
                        double y2 = (new Double(atts.getValue("y2")) - vbY)  * coeff;
                        d = x1 + "," + y1 + " " + x2 + "," + y2;
                        llen = lineLength(x1, y1, x2, y2);
                        plen = llen;
                        line = "M" + d;
                        data = x1 + "," + y1 + "," + x2 + "," + y2;
                        acc = 1;
                    } else if (qName.equals(POLYLINE)) {
                        // ломаная
                        d = atts.getValue("points").trim();
                        line = "M" + d;
                        String[] d_array = d.split("\\s+|,");
                        llen = 0;
                        acc = 0;
                        if (d_array.length > 1) {
                            // формируем строку из всех точек
                            double x1 = (new Double(d_array[0]) - vbX) * coeff;
                            double y1 = (new Double(d_array[1]) - vbY) * coeff;
                            
                            data = x1 + "," + y1;
                            acc = 0;
                            for (int i = 1, j = d_array.length / 2; i < j; i++) {
                                double x2 = (new Double(d_array[i * 2]) - vbX)  * coeff;
                                double y2 = (new Double(d_array[i * 2 + 1]) - vbY)  * coeff;
                                data += "," + x2 + "," + y2;
                                llen += lineLength(x1, y1, x2, y2);
                                x1 = x2;
                                y1 = y2;
                                acc++;
                            }
                        }
                        plen = llen;
                    }   // запись подъёмника в layer_svg
                    
                    LayerSVG svg = new LayerSVG();
                    svg.name = makeMemberName(currentElement, LIFT_);
                    svg.part = part;
                    svg.type = 2; // подъёмник
                    svg.scheme_data = jsonPrefix + data + jsonPostfix;
                    svg.svg_filename = filename;
                    name = svg.name;
                    String info = "Подъёмник " + svg.name;
                    if (plen > 0) {
                        info += "; точность " + String.format("%f", Math.abs(1 - llen / plen));
                        svg.status = 0;
                    } else {
                        info += "; ошибка - линия не определена";
                        svg.status = -1;
                        log(info + "; вероятно, указана только одна точка: " + d);
                    }
                    info += "; отрезков " + acc;
                    info += "; длина кривой " + String.format("%f", plen);
                    info += "; длина ломаной " + String.format("%f", llen);
                    info += "; " + qName + "=\"" + d + "\"";
                    if (!debug) {
                        System.out.println(info);
                        System.out.println(line);
                    }
                    svg.info = info;
                    // пишем подъёмники определённого места катания в layer_svg
                    PreparedStatement pstmt = null;
                    ResultSet rs = null;
                    PreparedStatement pstmtSelLift = null;
                    try {
                        // проверить наличие записи в базе
                        pstmtSelLift = connection.prepareStatement(
                                "SELECT * FROM " + layersFN + " WHERE name=? AND type=? AND ski_area_id=?"
                        );
                        pstmtSelLift.setString(1, svg.name);
                        pstmtSelLift.setInt(2, 2);
                        pstmtSelLift.setLong(3, skiAreaId);
                        
                        rs = pstmtSelLift.executeQuery();
                        if (rs.next()) {
                            log("Логическая ошибка в " + filename + ": подъёмник " + svg.name + " уже присутствует в зоне катания " + skiAreaId);
                        } else {
                            
                            // добавить запись в базовую таблицу
                            pstmt = connection.prepareStatement(
                                    "INSERT INTO " + layersFN + " (name, part, scheme_data, svg_filename, info, status, type, ski_area_id) " +
                                            "VALUES (?,?,?,?,?,?,?,?)");
                            pstmt.setString(1, svg.name);
                            pstmt.setInt(2, svg.part);
                            pstmt.setString(3, svg.scheme_data);
                            pstmt.setString(4, svg.svg_filename);
                            pstmt.setString(5,  new SimpleDateFormat().format(new Date()) + " " + svg.info);
                            pstmt.setInt(6, svg.status);
                            pstmt.setInt(7, svg.type);
                            pstmt.setLong(8, skiAreaId);
                            pstmt.executeUpdate();
                            
                            if (!debug)
                                System.out.printf("Подъёмник %s добавлен в таблицу " + layersFN + "\n", svg.name);
                        }
                    } catch (SQLException ex) {
                        log("Ошибка при добавлении подъёмника " + svg.name + " в таблицу " + layersFN);
                    }
                    finally  {
                        if (pstmtSelLift != null)
                            try {
                                pstmtSelLift.close();
                            } catch (SQLException e1) {}
                        
                        if (pstmt != null)
                            try {
                                pstmt.close();
                            } catch (SQLException e) {}
                	
                	if (rs != null)
                            try {
                                rs.close();
                            } catch (SQLException e) {}
                    }
                    break;
                case CIRCLE:
                    // станции подъёмника
                    double cx = (new Double(atts.getValue("cx")) - vbX)  * coeff;
                    double cy = (new Double(atts.getValue("cy")) - vbY)  * coeff;
                    stations.add(new Vector2(cx, cy));
                    break;
            }
        } else if (currentElement.equals(ICONS)) {
            // Иконки
            switch (qName) {
                case "g":
                    // определяем имя иконки
                    if (!idValue.equals(""))
                        subElement = idValue;
                    break;
                case RECT:
                    if (subElement.equals(""))
                        break;
                    String iname = subElement;
                    subElement = ""; // считаем этот прямоугольник единственным и конечным
                    
                    // определяем точку по центру прямоугольника
                    part++;
                    double cx = new Double(atts.getValue("x")) + (new Double(atts.getValue("width"))) / 2; // центр прямоугльника
                    double cy = new Double(atts.getValue("y")) + (new Double(atts.getValue("height"))) / 2; // центр прямоугльника
                    
                    cx = (cx - vbX)  * coeff;
                    cy = (cy - vbY)  * coeff;
                    
                    String data = cx + "," + cy;
                    
                    // запись иконки в layer_svg
                    LayerSVG svg = new LayerSVG();
                    svg.name = iname;
                    svg.part = part;
                    svg.type = 3; // иконка
                    svg.scheme_data = jsonPrefix + data + jsonPostfix;
                    svg.svg_filename = filename;
                    svg.info = "Иконка " + svg.name + "; " + atts.toString();
                    // пишем подъёмники определённого места катания в layer_svg
                    PreparedStatement pstmt = null;
                    ResultSet rs = null;
                    PreparedStatement pstmtSelLift = null;
                    try {
                        // проверить наличие записи в базе
                        pstmtSelLift = connection.prepareStatement(
                                "SELECT * FROM " + layersFN + " WHERE name=? AND type=? AND ski_area_id=?"
                        );
                        pstmtSelLift.setString(1, svg.name);
                        pstmtSelLift.setInt(2, svg.type);
                        pstmtSelLift.setLong(3, skiAreaId);
                        
                        rs = pstmtSelLift.executeQuery();
                        if (rs.next()) {
                            log("Логическая ошибка в " + filename + ": иконка " + svg.name + " уже присутствует в зоне катания " + skiAreaId);
                        } else {
                            
                            // добавить запись в базовую таблицу
                            pstmt = connection.prepareStatement(
                                    "INSERT INTO " + layersFN + " (name, part, scheme_data, svg_filename, info, status, type, ski_area_id) " +
                                            "VALUES (?,?,?,?,?,?,?,?)");
                            pstmt.setString(1, svg.name);
                            pstmt.setInt(2, svg.part);
                            pstmt.setString(3, svg.scheme_data);
                            pstmt.setString(4, svg.svg_filename);
                            pstmt.setString(5,  new SimpleDateFormat().format(new Date()) + " " + svg.info);
                            pstmt.setInt(6, 0);
                            pstmt.setInt(7, svg.type);
                            pstmt.setLong(8, skiAreaId);
                            pstmt.executeUpdate();
                            
                            if (!debug)
                                System.out.printf("Иконка %s добавлена в таблицу " + layersFN + "\n", svg.name);
                            
                            rs = pstmtSelLift.executeQuery();

                            if (rs.next()) {
                                // добавить запись в таблицу сегментов
                                pstmt = connection.prepareStatement(
                                    "INSERT INTO " + segmentsFN + " (layer_svg_id, segment, scheme_data) " +
                                    "VALUES (?,?,?)");

                                pstmt.setLong(1, rs.getLong(ID));
                                pstmt.setInt(2, 0);
                                pstmt.setString(3, rounding(svg.scheme_data));
                                pstmt.executeUpdate();

                                if (debug) {
                                    System.out.println("<rect x=\"" + (cx - 3.5) + "\" y=\"" + (cy - 3.5) + "\" fill=\"#212121\" width=\"7\" height=\"7\"/>");
                                } else {
                                    System.out.printf("Сегмент иконки %s добавлен в таблицу %s\n", name, segmentsFN);
                                }
                            } else {
                                log("Логическая ошибка: иконка " + svg.name + " уже присутствует в таблице " + segmentsFN);
                            }
                        }
                    } catch (SQLException ex) {
                        log("Ошибка при добавлении иконки " + svg.name + " в таблицу " + layersFN);
                    }
                    finally  {
                        if (pstmtSelLift != null)
                            try {
                                pstmtSelLift.close();
                            } catch (SQLException e1) {}
                        
                        if (pstmt != null)
                            try {
                                pstmt.close();
                            } catch (SQLException e) {}
                	
                	if (rs != null)
                            try {
                                rs.close();
                            } catch (SQLException e) {}
                    }
                    break;
            }
        }
    }
    
    @Override 
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        if (qName.equals("g")) {
            // считаем уровень вложенности
            lavel--;
        
            if (lavel == 0) {
                // для подъёмников проверяем станции и делим на сегменты
                if (currentElement.contains(LIFT_)) {
                    // определяем id только что записанного подъёмника
                    PreparedStatement pstmt = null;
                    ResultSet rs = null;
                    PreparedStatement pstmtSelLift = null;
                    try {
                        pstmtSelLift = connection.prepareStatement(
                            "SELECT * FROM " + layersFN + " WHERE name=? AND type=? AND part=? AND ski_area_id=?"
                        );
                        pstmtSelLift.setString(1, name);
                        pstmtSelLift.setInt(2, 2);
                        pstmtSelLift.setInt(3, 1);
                        pstmtSelLift.setLong(4, skiAreaId);
                        
                        rs = pstmtSelLift.executeQuery();
                        if (rs.next()) {
                            id = rs.getLong("id");

                            String scheme_data = rs.getString("scheme_data");
                            scheme_data = stripJson(scheme_data);
                            
                            String[] spoints = scheme_data.split(",");
                            int last = spoints.length;
                            
                            if (last >= 2) {
                                Vector2 start = new Vector2(new Double(spoints[0]), new Double(spoints[1]));
                                Vector2 finish = new Vector2(new Double(spoints[last-2]), new Double(spoints[last-1]));

                                ArrayList<String> sdata = new ArrayList<>();

                                sdata.add(scheme_data);

                                while (stations.size() > 0) {
                                    Vector2 st = stations.get(0);
                                    // проверка на близость к старту/финишу
                                    if (lineLength(st, start) < ACCURACY_LIFT_STATION || 
                                        lineLength(st, finish) < ACCURACY_LIFT_STATION) {
                                        stations.remove(0);
                                        continue;
                                    }
                                    // цикл по отрезкам и деление на сегменты
                                    for (int a = 0, b = sdata.size(); a < b; a++) {
                                        String sd = sdata.get(a);
                                        String[] d_array = sd.split(",");
                                        if (d_array.length > 3) {
                                            double x1 = new Double(d_array[0]);
                                            double y1 = new Double(d_array[1]);
                                            String data = x1 + "," + y1;
                                            for (int i = 1, j = d_array.length / 2; i < j; i++) {
                                                double x2 = new Double(d_array[i * 2]);
                                                double y2 = new Double(d_array[i * 2 + 1]);

                                                Line l = new Line(x1, y1, x2, y2);
                                                if (l.distance(st.x, st.y) < ACCURACY_LIFT_STATION) {
                                                    // расстояние до отрезка в пределах погрешности и
                                                    // станция не совпадает с началом и концом подъёмника

                                                    // добавляем новый сегмент
                                                    String points = x2 + "," + y2;
                                                    String new_point = st.x + "," + st.y;
                                                    String new_data;
                                                    if (st.isEqual(x1, y1)) {
                                                        new_data = sd.substring(sd.indexOf(points));
                                                        sd = data + "," + points;
                                                    } else if (st.isEqual(x2, y2)) {
                                                        new_data = sd.substring(sd.indexOf(points));
                                                        sd = data + "," + points;
                                                    } else {
                                                        new_data = new_point + "," + sd.substring(sd.indexOf(points));
                                                        //редактируем старый
                                                        sd = data + "," + points + "," + new_point;
                                                    }
                                                    sdata.set(a, sd);
                                                    sdata.add(new_data);

                                                    break;
                                                }

                                                data += "," + x2 + "," + y2;

                                                x1 = x2;
                                                y1 = y2;
                                            }
                                        }
                                    }
                                    stations.remove(0);
                                }

                                stmt.execute("DELETE FROM " + segmentsFN + " WHERE status < 1 AND layer_svg_id = " + id);

                                // добавить записи в таблицу сегментов с учётом станций в виде <circles>
                                pstmt = connection.prepareStatement(
                                    "INSERT INTO " + segmentsFN + " (layer_svg_id, segment, scheme_data) " +
                                    "VALUES (?,?,?)");

                                for (String sd : sdata) {
                                    pstmt.setLong(1, id);
                                    pstmt.setInt(2, 0);
                                    pstmt.setString(3, rounding(jsonPrefix + sd + jsonPostfix));
                                    pstmt.executeUpdate();

                                    if (debug) {
                                        System.out.println("<path fill-rule=\"evenodd\" clip-rule=\"evenodd\" fill=\"none\" stroke=\"BLACK\" stroke-width=\"3\" stroke-miterlimit=\"10\" d=\"M" + sd + "\"/>");
                                    } else {
                                        System.out.printf("Сегмент подъёмника %s добавлен в таблицу %s\n", name, segmentsFN);
                                    }
                                }
                                
                            }
                        }
                    } catch (SQLException ex) {
                        log("Ошибка при добавлении сегмента подъёмника " + name + " в таблицу " + segmentsFN);
                    }
                    finally {
                    	if (pstmtSelLift != null)
                            try {
                                pstmtSelLift.close();
                            } catch (SQLException e) {}
                        
                    	if (rs != null)
                            try {
                                rs.close();
                            } catch (SQLException e) {}
                    	
                    	if (pstmt != null)
                            try {
                                pstmt.close();
                            } catch (SQLException e) {}
                    }
                }

                // закрытие корневого элемента подъёмника или трассы
                currentElement = "";
            }
        }
    }
    
    private ArrayList<LayerData> createMemberList() {
        
        ArrayList<LayerData> ml = new ArrayList<>();
        
        ResultSet rs = null;
        PreparedStatement pstmt = null;
        try {
            pstmt = connection.prepareStatement(
                    "SELECT * FROM " + layersFN + " WHERE ski_area_id=? AND type=?"
            );
            pstmt.setLong(1, skiAreaId);
            pstmt.setInt(2, 1);

            rs = pstmt.executeQuery();
            while (rs.next()) {
                LayerData ld = new LayerData();
                ld.name = rs.getString("name");
                ld.data = rs.getString("scheme_data");
                ld.layer_type_id =  rs.getInt("id"); // используем это поле для хранения id базовой записи
                ld.osmType = DOWNHILL;
                
                // проверка трассы на вырожденность - добавляем только длиннее MAXROUND
                if (!lineDegenerate(ld))
                    ml.add(ld);
            }
        } catch (SQLException ex) {
            if (rs != null)
                try {
                        rs.close();
                } catch (SQLException e) {}

            if (pstmt != null)
                try {
                        pstmt.close();
                } catch (SQLException e) {}
            throw new ParseException("Ошибка при попытке прочитать данные из таблицы " + layersFN, 0);
        } finally {
            if (rs != null)
                try {
                        rs.close();
                } catch (SQLException e) {}

            if (pstmt != null)
                try {
                        pstmt.close();
                } catch (SQLException e) {}
        }
        
        return ml;
    }
    
    @Override
    public void endDocument() {
        if (!debug)
            System.out.println("Идёт расчёт пересечений / деление трасс для зоны катания " + skiAreaId);
        
        // документ разобран, подъёмники добавлены, фрмируем список трасс
        ArrayList<LayerData> ml = createMemberList();
        
        // делим трассы в случае попадания конечной точки одной трассы на узел другой
        // объединяем близлежащие (на расстоянии < MAXROUND) узлы
        // определяем по всем оставшимся одиночным концам трасс расстояние до ближайшего отрезка
        // и если находим меньше MAXROUND, соединяем с ближайшей точкой этого отрезка
        divideSlopeNodes(ml);

        // отсекаем вырожденные трассы, появившиеся в результате деления и объединения
        removeDegenerateLines(ml);
        
        //ищем пересечения
        divideSlopeSegments(ml);
        
        // отсекаем вырожденные трассы, появившиеся в результате пересечений отрезков
        removeDegenerateLines(ml);
        
        // сортируем направление всех трасс с севера на юг
        organizePoints(ml);
        
        //!!! котрольный вывод - отладочный вариант - УБРАТЬ!
        if (debug)
            makeNodeList(ml, true);
        
        // пишем трассы в layer_svg_segments
        for (LayerData ld : ml) {
            
            saveLayerData(ld);
            
            if (debug) {
                String s = stripJson(ld.data);
                System.out.println("<path fill-rule=\"evenodd\" clip-rule=\"evenodd\" fill=\"none\" stroke=\"" + randomColor() + "\" stroke-width=\"2\" stroke-miterlimit=\"10\" d=\"M" + s + "\"/>");
            }
        }

    	// закрываем соединения
        try {
            if (stmt != null)
                stmt.close();
            if (connection != null)
                connection.close();
        } catch (SQLException ex) {
            log("Ошибка при попытке закрыть соединение с БД");
        }
        if (counter % NODES_PRINT_INTERVAL != 0)
            printMessage();
        System.out.println(filename + " parsed succesfully."); 
    }
 
    // деление трасс на сегменты
    public void divideSlopeSegments(ArrayList<LayerData> memberList) {
        if (!memberList.isEmpty()) {
            // проверяем пересечения трасс и делим отрезки
            boolean foundIntersection = true;

            int lastI = 0;

            while (foundIntersection) { // крутим цикл пока не закончатся пересечения
                foundIntersection = false;

                for (int i = lastI; i < memberList.size(); i++) {
                    LayerData ld = memberList.get(i);

                    // работаем только по трассам
                    if (!ld.osmType.contains(DOWNHILL))
                        continue;

                    double x1, y1, x2, y2; // первый отрезок
                    String points = jsonPrefix;

                    // убираем префикс и постфикс JSON
                    String s = stripJson(ld.data);
                    
                    StringTokenizer stp = new StringTokenizer(s, ",");
                    if (stp.countTokens() >= 2) {
                        x1 = new Double(stp.nextToken());
                        y1 = new Double(stp.nextToken());
                        points += x1 + "," + y1;
                    } else
                        // отсутствует долгота первой точки
                        break;

                    while(stp.hasMoreTokens()){
                        x2 = new Double(stp.nextToken());
                        if (stp.countTokens() > 0) {
                            y2 = new Double(stp.nextToken());
                        } else {
                            // отсутствует долгота последней точки
                            break;
                        }

                        // проходим по оставшимся трассам
                        for (int m = i + 1; m < memberList.size(); m++) {
                            LayerData ld1 = memberList.get(m);

                            // работаем только по трассам
                            if (!ld1.osmType.contains(DOWNHILL))
                                continue;

                            String points1 = jsonPrefix;
                            double x3, y3, x4, y4; // второй отрезок

                            // убираем префикс и постфикс JSON
                            String s1 = stripJson(ld1.data);
                            StringTokenizer stp1 = new StringTokenizer(s1, ",");
                            if (stp1.countTokens() >= 2) {
                                x3 = new Double(stp1.nextToken());
                                y3 = new Double(stp1.nextToken());
                                points1 += x3 + "," + y3;
                            } else
                                // отсутствует долгота первой точки
                                break;

                            while(stp1.hasMoreTokens()){
                                x4 = new Double(stp1.nextToken());
                                if (stp1.countTokens() > 0) {
                                    y4 = new Double(stp1.nextToken());
                                } else {
                                    // отсутствует долгота последней точки
                                    break;
                                }

                                // проверка на пересечение отрезков,
                                // проверяем на персечение только если ранее не встретилось другого
                                if (!foundIntersection && findIntersection(x1, y1, x2, y2, x3, y3, x4, y4)) {
                                    countIntersections++;
        
                                    if (debug)
                                        System.out.println("<circle fill-rule=\"evenodd\" clip-rule=\"evenodd\" fill=\"none\" stroke=\"red\" stroke-width=\"1\" stroke-miterlimit=\"10\" cx=\"" + x + "\" cy=\"" + y + "\" r=\"3\"/>");
                                    else
                                        System.out.println(
                                            "Found intersection in ski area " + skiAreaId + 
                                            " between downhill " + ld.name + " and " + ld1.name +
                                            " at the point lat=" + x + " lon=" + y
                                        );
                                    
                                    if (!pEqualsHalf(x, y, x1, y1) && !pEqualsHalf(x, y, x2, y2)) {
                                        // первый отрезок пересекается вторым - работаем с первым
                                        
                                        if (stp.hasMoreTokens()) {
                                            // добавляем сегмент от точки пересечения данного отрезка до конца текущего сегмента
                                            LayerData ld_new = new LayerData(ld);
                                            ld_new.setX1Y1(x, y);
                                            ld_new.data = jsonPrefix + x + "," + y;
                                            // выбираем остаток точек из токенайзера, таким образом формируя data и
                                            // прекращая цикл по следующим отрезкам первого сегмента
                                            while(stp.hasMoreTokens()){
                                                double nx = new Double(stp.nextToken());
                                                if (stp.countTokens() > 0) {
                                                    double ny = new Double(stp.nextToken());
                                                    ld_new.data += "," + nx + "," + ny;
                                                } else {
                                                    // отсутствует долгота последней точки
                                                    break;
                                                }
                                            }
                                            ld_new.data += jsonPostfix;
                                            memberList.add(ld_new);
                                        }
                                        
                                        // редактируем текущий сегмент - меняем конечную точку сегмента на точку пересечения
                                        ld.setX2Y2(x, y);
                                        // и меняем текущий отрезок
                                        x2 = x;
                                        y2 = y;
                                        
                                        // выход из цикла и перезапуск с новыми отрезками с позиции lastI
                                        foundIntersection = true;
                                        lastI = i;
                                    }

                                    if (!pEqualsHalf(x, y, x3, y3) || !pEqualsHalf(x, y, x4, y4)) {
                                        // второй отрезок пересекается первым - работаем со вторым
                                        
                                        if (stp1.hasMoreTokens()) {
                                            // добавляем сегмент от точки пересечения данного отрезка до конца текущего сегмента
                                            LayerData ld_new = new LayerData(ld1);
                                            ld_new.setX1Y1(x, y);
                                            ld_new.data = jsonPrefix + x + "," + y;
                                            // выбираем остаток точек из токенайзера, таким образом формируя data и 
                                            // прекращая цикл по следующим отрезкам второго сегмента
                                            while(stp1.hasMoreTokens()){
                                                double nx = new Double(stp1.nextToken());
                                                if (stp1.countTokens() > 0) {
                                                    double ny = new Double(stp1.nextToken());
                                                    ld_new.data += "," + nx + "," + ny;
                                                } else {
                                                    // отсутствует долгота последней точки
                                                    break;
                                                }
                                            }
                                            ld_new.data += jsonPostfix;
                                            memberList.add(ld_new);
                                        }
                                        
                                        // редактируем текущий сегмент - меняем конечную точку сегмента на точку пересечения
                                        ld1.setX2Y2(x, y);
                                        // и меняем текущий отрезок
                                        x4 = x;
                                        y4 = y;

                                        // выход из цикла и перезапуск с новыми отрезками с позиции lastI
                                        foundIntersection = true;
                                        lastI = i;
                                    }
                                }

                                x3 = x4;
                                y3 = y4;
                                points1 += "," + x3 + "," + y3;
                            }

                            points1 += jsonPostfix;
                            ld1.data = points1;
                        }

                        x1 = x2;
                        y1 = y2;
                        points += "," + x1 + "," + y1;
                    }

                    points += jsonPostfix;
                    ld.data = points;

                    if (foundIntersection)
                        break;
                }
            }
        }

    }

    public ArrayList<NodeNear> makeNodeList(ArrayList<LayerData> memberList) {
        return makeNodeList(memberList, false);
    }
    
    public ArrayList<NodeNear> makeNodeList(ArrayList<LayerData> memberList, boolean printNodes) {
        ArrayList<NodeNear> nodes = new ArrayList<>();

        for (LayerData ld : memberList) {
            // убираем префикс и постфикс JSON
            String s = stripJson(ld.data);

            StringTokenizer stp = new StringTokenizer(s, ",");
            boolean first = true;
            while(stp.hasMoreTokens()){
                NodeNear v = new NodeNear();
                v.x = new Double(stp.nextToken());
                if (stp.countTokens() > 0) {
                    v.y = new Double(stp.nextToken());
                } else {
                    // отсутствует долгота последней точки
                    break;
                }
                if (printNodes)
                    System.out.println("<circle fill=\"none\" stroke=\"green\" stroke-width=\"0.3\" cx=\"" + v.x + "\" cy=\"" + v.y + "\" r=\"2\"/>");

                if ((first) || !stp.hasMoreTokens()) {
                    v.endpoint = true;
                    if (printNodes) {
                        if (first) {
                            System.out.println("<circle fill=\"none\" stroke=\"green\" stroke-width=\"1\" cx=\"" + v.x + "\" cy=\"" + v.y + "\" r=\"7\"/>");
                        } else {
                            System.out.println("<circle fill=\"none\" stroke=\"blue\" stroke-width=\"1\" cx=\"" + v.x + "\" cy=\"" + v.y + "\" r=\"8\"/>");
                        }
                    }
                }
                v.layerData = ld;

                first = false;
                nodes.add(v);
            }
        }
        
        return nodes;
    }
    
    // приведение лежащих рядом узлов к одному
    // деление трасс в повторяющихся узлах на сегменты
    // корректируем одиночные конечные точки отрезков в случае близости к другим трассам менее MAXROUND
    public void divideSlopeNodes(ArrayList<LayerData> memberList) {
        if (!memberList.isEmpty()) {
            // формируем список всех узлов
            ArrayList<NodeNear> nodes = makeNodeList(memberList);
            
            // приводим все узлы, лежащие на расстоянии < MAXROUND друг от друга, к одному
            // строим список узлов с количеством близлежащих узлов
            ArrayList<NodeNear> nn = new ArrayList<>();
            int maxNear = 0;
            for (int i = 0, j=nodes.size(); i < j; i++) {
                NodeNear v = nodes.get(i);
                NodeNear n = new NodeNear(v);
                for (int a = 0, b=nodes.size(); a < b; a++) {
                    NodeNear v1 = nodes.get(a);
                    if (a != i) {
                        if (n.isEqual(v1)) {
                            n.pointsEqual++;
                            n.endpoint = n.endpoint || v1.endpoint;
                        } else if (lineLength(n, v1) < MAXROUND) {
                            n.nearest.add(v1);
                            n.pointsNear++;
                        }
                    }
                }
                maxNear = Math.max(n.pointsNear, maxNear);
                
                // добавляем только дублирующиеся или близлежащие узлы
                if (n.pointsNear + n.pointsEqual > 0)
                    nn.add(n);
            }
            
//            System.out.println(bestV2);
//            System.out.println(lineLength(175.52896445572762, 144.9845523695636, 176.4770050048828, 144.58099365234375));

            // удаляем все повторяющиеся узлы из списка nn с pointsEqual > 0
            int i = 0;
            while (i < nn.size()) {
                NodeNear vi = nn.get(i);
                int j = i + 1;
                while (j < nn.size()) {
                    NodeNear vj = nn.get(j);
                    if (vi.isEqual(vj)) {
                        nn.remove(j);
                    } else
                        j++;
                }
                i++;
            }

            // повторяем цикл, пока не переберём все повторяющиеся точки
            while (true) {
                // ищем узел с максимальным чмслом ближайших точек
                i = 0;
                int maxI = -1;
                int maxNearest = 0;
                for (NodeNear n : nn) {
                    if (n.pointsNear > 0) {
                        if ((maxNearest < n.pointsNear) || ((maxNearest == n.pointsNear) && (n.endpoint))) {
                            // при наличии максимального числа близлежащих точек узла выбираем старт/финиш трассы
                            maxNearest = n.pointsNear;
                            maxI = i;
                        }
                    }
                    i++;
                }

                if (maxI == -1) 
                    break;

                // меняем точки на выбранную
                NodeNear nnear = nn.get(maxI);
                for (NodeNear v : nnear.nearest) {
                    for (LayerData ld : memberList) {
                        // меняем "ближайшую" точку на отобранную
                        changePoint(ld, v.x, v.y, nnear.x, nnear.y);
                    }
                }
                nn.remove(maxI);
            }

            // формируем список всех узлов повторно
            nodes = makeNodeList(memberList, false);
            
            // удаляем все одиночные узлы
            i = 0;
            while (i < nodes.size()) {
                NodeNear vi = nodes.get(i);
                int j = i + 1;
                boolean foundV2 = false;
                while (j < nodes.size()) {
                    NodeNear vj = nodes.get(j);
                    if (vi.isEqual(vj)) {
                        nodes.remove(j);
                        foundV2 = true;
                    } else
                        j++;
                }
                if (foundV2)
                    i++;
                else
                    nodes.remove(i);
            }
            
            // делим трассы на сегменты по дублирующимся узлам, попадающим на середину трассы
            for (NodeNear v : nodes) {
                for (int k=0, j = memberList.size(); k < j; k++) {
                    LayerData ld = memberList.get(k);
                    String points = v.x + "," + v.y;
                    int pointsPos = ld.data.indexOf(points);
                    if ((pointsPos > jsonPrefix.length()) && (pointsPos + points.length() < ld.data.indexOf(jsonPostfix))) {
                        // делим трассу
//                        System.out.println(ld.data);
                        
                        // добавляем новый сегмент
                        LayerData ld1 = new LayerData(ld);
                        ld1.data = jsonPrefix + ld.data.substring(ld.data.indexOf(points));
                        memberList.add(ld1);
                        //редактируем старый
                        ld.data = ld.data.substring(0, ld.data.indexOf(points) + points.length()) + jsonPostfix;
                        if (debug)
                            System.out.println("<circle fill-rule=\"evenodd\" clip-rule=\"evenodd\" fill=\"none\" stroke=\"blue\" stroke-width=\"1\" stroke-miterlimit=\"10\" cx=\"" + v.x + "\" cy=\"" + v.y + "\" r=\"4\"/>");
                        
 //                       System.out.println(ld.data);
//                        System.out.println(ld1.data);
//                        System.out.println();
                    }
                }
            }
            
            // корректируем конечные точки отрезков для случаев близости к другим трассам менее 2*MAXROUND
            // формируем список всех узлов
            nodes = makeNodeList(memberList, false);
            
            // строим список узлов с количеством повторений
            nn = new ArrayList<>();
            maxNear = 0;
            for (int k = 0, j = nodes.size(); k < j; k++) {
                NodeNear v = nodes.get(k);
                NodeNear n = new NodeNear(v);
                for (int a = 0, b = nodes.size(); a < b; a++) {
                    NodeNear v1 = nodes.get(a);
                    if (a != k) {
                        if (n.isEqual(v1)) {
                            n.pointsEqual++;
                            n.endpoint = n.endpoint || v1.endpoint;
                        }
                    }
                }
                maxNear = Math.max(n.pointsNear, maxNear);
                
                // добавляем только конечные точки отрезков
                if (n.endpoint)
                    nn.add(n);
            }
            
            
            for (NodeNear n : nn) {
                // ищем кратчайшее расстояние до всех отрезков
                // проходим по всем трассам
                if (n.endpoint && n.pointsEqual == 0) {
                    // идём только по одиночным конечным точкам трасс
                    for (int k=0, j = memberList.size(); k < j; k++) {
                        LayerData ld = memberList.get(k);
                        
                        if (ld.equals(n.layerData))
                            continue;
                        
                        double x1, y1, x2, y2; // отрезок
                        // убираем префикс и постфикс JSON
                        String s = stripJson(ld.data);
                        StringTokenizer stp = new StringTokenizer(s, ",");
                        if (stp.countTokens() >= 2) {
                            x1 = new Double(stp.nextToken());
                            y1 = new Double(stp.nextToken());
                        } else
                            // отсутствует долгота первой точки
                            break;
                        while(stp.hasMoreTokens()){
                            x2 = new Double(stp.nextToken());
                            if (stp.countTokens() > 0) {
                                y2 = new Double(stp.nextToken());
                            } else {
                                // отсутствует долгота последней точки
                                break;
                            }

                            if (pEquals(x1, y1, n.x, n.y) || pEquals(x2, y2, n.x, n.y)) {
                                // точка совпала: выходим из цикла и переходим к следующей трассе
                                break;
                            }
                            // проверка на расстояние до отрезка от данной точки
                            if (!pEquals(x1, y1, x2, y2)) {
                                try {
                                    Line line = new Line(x1, y1, x2, y2);
                                    if (line.distance(n.x, n.y) < 2*MAXROUND) {
                                        
                                        // если другой конец отрезка содержащего данную точку совпадает с концом
                                        // исследуемого отрезка, то отсекаем лишнее...
                                        if (prevPointEqualEndpoint(n, x1, y1, x2, y2)) {
                                            removePoint(n.layerData, n.x, n.y);
                                            break;
                                        }
                                        
                                        // меняем конечную не повторяющуюся точку на рассчитанную
                                        changePoint(n.layerData, n.x, n.y, line.ix, line.iy);
                                        
                                        // делим трассу по найденной точке
                                        // добавляем новый сегмент
                                        String point1 = x1 + "," + y1;
                                        String point2 = x2 + "," + y2;
                                        String newPoint = line.ix + "," + line.iy;
                                        
                                        LayerData ld1 = new LayerData(ld);
                                        ld1.data = jsonPrefix + newPoint + "," + ld.data.substring(ld.data.indexOf(point2));
                                        memberList.add(ld1);
                                        //редактируем старый
                                        ld.data = ld.data.substring(0, ld.data.indexOf(point1) + point1.length()) + "," + newPoint + jsonPostfix;
                                        if (debug)
                                            System.out.println("<circle fill=\"none\" stroke=\"yellow\" stroke-width=\"1\" cx=\"" + line.ix + "\" cy=\"" + line.iy + "\" r=\"2.5\"/>");
                                        
                                        // переходим к следующей одиночной точке
                                        break;
                                    }
                                } catch (IllegalArgumentException ex) {
                                    // точки равны  с точностью погрешности вычисления
                                    // встретился вырожденный отрезок - убираем
                                    String s1 = x1 + "," + y1;
                                    ld.data = ld.data.replaceAll(s1 + "," + x2 + "," + y2, s1);
                                }
                            } else {
                                // встретился вырожденный отрезок (хотя на этом этапе такого быт не должно) - убираем
                                String s1 = x1 + "," + y1;
                                ld.data = ld.data.replaceAll(s1 + "," + s1, s1);
                            }
                            
                            x1 = x2;
                            y1 = y2;
                        }
                    }
                }
            }
        }
    }

    private String makeMemberName(String id, String prefix) {
        String res = id.replaceFirst(prefix, "").trim();
        if (res.indexOf("_")==0) {
            res = res.replaceFirst("_", "").trim();
        }
        return res;
    }
    
    // запрещаем проверку схемы xml через интернет
    @Override
    public org.xml.sax.InputSource resolveEntity(String publicId, String systemId)
            throws org.xml.sax.SAXException, java.io.IOException {
//        System.out.println("Ignoring: " + publicId + ", " + systemId);
        return new org.xml.sax.InputSource(new java.io.StringReader(""));
    }
    
    protected void log(String msg) {
        log(msg, true);
    }
    
    protected void log(String msg, boolean toConsole) {
        if (toConsole)
            System.out.println(msg);
        errors += msg + "\n";
    }

    public static String stripJson(String s) {
        String s1;
        s1 = s.substring((jsonPrefix).length());
        s1 = s1.substring(0, s1.indexOf(jsonPostfix));
        
        return s1;
    }
    
    // виртуальная функция для округления - перекрыть или установить roundToInt в False
    private String rounding(String jsonPoints) {
        // округление координат до целого, если установлен флаг roundToInt
        
        if (!roundToInt) {
            return jsonPoints;
        }
        
        // убираем префикс и постфикс JSON
        String s = stripJson(jsonPoints);
        
        String[] points = s.split(",");
        s = "";
        
        for (String point : points) {
            double d = new Double(point);
            long i = Math.round(d);
            s += "," + i;
        }
        
        if (s.length() > 0)
            s = s.substring(1);
        
        return jsonPrefix + s + jsonPostfix;
    }

    // виртуальная функция для географических координат - перекрыть elevation...
    protected boolean below(double xb, double yb, double xe, double ye) {
        return (yb > ye);
    }

     public void organizePoints(ArrayList<LayerData> ml) {
        for (LayerData ld : ml) {
            String s = stripJson(ld.data);
            StringTokenizer stp = new StringTokenizer(s, ",");
            if (stp.countTokens() >= 2) {
                double xb = new Double(stp.nextToken());
                double yb = new Double(stp.nextToken());
                double xe = notExist;
                double ye = notExist;
                // на всякий случай по ходу делаем реверс точек
                String s1 = xb + "," + yb; 
                while(stp.hasMoreTokens()){
                    xe = new Double(stp.nextToken());
                    if (stp.countTokens() > 0) {
                        ye = new Double(stp.nextToken());
                    } else {
                        // отсутствует долгота последней точки
                        ye = notExist;
                        break;
                    }
                    s1 = xe + "," +ye + "," + s1;
                }
                if (ye == notExist)
                    break;
                if (below(xb, yb, xe, ye)) {
                    // конец севернее начала: делаем реверс точек
                    ld.data = jsonPrefix + s1 + jsonPostfix;
                }
            } else
                // отсутствует долгота первой точки
                break;
        }
    }

   private void saveLayerData(LayerData ld) {
        PreparedStatement pstmt = null;
        try {
            // добавить запись
            pstmt = connection.prepareStatement(
                "INSERT INTO " + segmentsFN + " (layer_svg_id, segment, scheme_data) " +
                "VALUES (?,?,?)");
            pstmt.setLong(1, ld.layer_type_id);
            pstmt.setInt(2, 0);
            pstmt.setString(3, rounding(ld.data));
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException ex) {
            log("Ошибка при добавлении сегмента трассы " + ld.name + " в таблицу " + segmentsFN);
        } finally {
            if (!debug)
                System.out.println("Для трассы " + ld.name + " ID=" + ld.layer_type_id + " добавлен сегмент в таблицу " + segmentsFN);
            if (pstmt != null)
                try {
                    pstmt.close();
                } catch (SQLException e) {}
        }
    }
}