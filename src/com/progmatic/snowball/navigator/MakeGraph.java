package com.progmatic.snowball.navigator;

import static com.progmatic.snowball.navigator.Navigator.calculateOnPisteRoute;
import static com.progmatic.snowball.navigator.OnPisteNavigator.calculateOnPisteRouteDejkstra;
import static com.progmatic.snowball.navigator.OnPisteRoute.Error.ok;
import com.progmatic.snowball.navigator.SkiArea.Transition;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MakeGraph {
    public static String connect_jdbc = "jdbc:postgresql://localhost:5432/snowball";
    public static String connect_lgn = "postgres";
    public static String connect_pwd = "postgres";

    // список предупреждений
    public static String errors = "";
    
    protected static void log(String msg) {
        log(msg, true);
    }
    
    protected static void log(String msg, boolean toConsole) {
        if (toConsole)
            System.out.println(msg);
        errors += msg + "\n";
    }

    private static void readLayerDatas(long skiAreaId, List<com.progmatic.snowball.entity.LayerData> lDatas) {
        try {
            if (connection == null)
                connectDB();
            
            if (lDatas == null)
                lDatas = new ArrayList<>();
            
            if (lDatas.size() > 0)
                lDatas.clear();
            
            // цикл чтения данных из таблицы
            ResultSet rs = stmt.executeQuery("SELECT * FROM layer_data WHERE ski_area_id=" + skiAreaId);
            while (rs.next()) {
                com.progmatic.snowball.entity.LayerData lData = new com.progmatic.snowball.entity.LayerData(
                    rs.getLong("id"), skiAreaId, rs.getLong("layer_type_id"), null, null, 0, rs.getString("name"),
                    rs.getDouble("x1"), rs.getDouble("y1"), rs.getDouble("x2"), rs.getDouble("y2"),
                    rs.getString("data")
                );
                lData.setDescription(rs.getString("description"));
                
                lDatas.add(lData);
            }
            
        } catch (Exception e) {
            // закрываем соединения
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException ex) {
                log("Ошибка при попытке закрыть сеанс работы с БД");
            }
            try {
                if (connection != null)
                    connection.close();
            } catch (SQLException ex) {
                log("Ошибка при попытке закрыть соединение с БД");
            }

            log("Ошибка при попытке чтении данных из layer_data");
        }
        finally {
            // закрываем соединения
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException ex) {
                log("Ошибка при попытке закрыть сеанс работы с БД");
            }
            try {
                if (connection != null)
                    connection.close();
            } catch (SQLException ex) {
                log("Ошибка при попытке закрыть соединение с БД");
            }
        }
    }

    private static Connection connection = null;
    private static Statement stmt = null;
    
    static void connectDB() throws Exception {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC Driver not found!");
            return;
        }
        try {
            connection = DriverManager.getConnection(
                connect_jdbc, connect_lgn, connect_pwd);
            stmt = connection.createStatement();
            
        } catch (SQLException e) {
            throw new Exception("Connection failed! Check login and password or host and port.");
        }
    }
    
    private static List<LayerData> convertEntityToNavigator(List<com.progmatic.snowball.entity.LayerData> lDatas) {
        List<LayerData> layerRecords = new ArrayList<>();
        for (com.progmatic.snowball.entity.LayerData lData : lDatas) {
            LayerData ld = new LayerData(
                lData.getId(), lData.getLayerTypeId(), lData.getSkiAreaId(), lData.getName(), lData.getDescription(),
                lData.getX1(), lData.getY1(), lData.getX2(), lData.getY2(), lData.getData(), ""
            );
            layerRecords.add(ld);
        }
        return layerRecords;
    }
    
    public static SkiArea makeGraph(List<com.progmatic.snowball.entity.LayerData> lDatas) {
        // lDatas - список из LayerData из entity сервера
        // возвращает SkiArea, в котором граф составляют:
        // nodalPoints - список узлов с входящими и исходящими транзишенами
        // transitions - список транзишенов
        
        List<LayerData> layerRecords = convertEntityToNavigator(lDatas);
        
        SkiArea skiArea = new SkiArea();
        skiArea.updateByLayerData(layerRecords);
        
        return skiArea;
    }
    
    public static SkiArea makeGraph(long skiAreaId, String connect, String login, String password) {
    // connect - строка для соединения с БД PostgreSQL
    // login - логин для доступа к PostgreSQL
    // password - пароль
        
        connect_jdbc = connect;
        connect_lgn = login;
        connect_pwd = password;
        
        connection = null;
        stmt = null;
        
        List<com.progmatic.snowball.entity.LayerData> lDatas = new ArrayList<>();
        readLayerDatas(skiAreaId, lDatas);
        
        return makeGraph(lDatas);
    }
  
    public static String normalizeDB(List<com.progmatic.snowball.entity.LayerData> lDatas) {
    // lDatas - список из LayerData из entity сервера
    // возвращает журнал исполнения
        
        List<LayerData> layerRecords = convertEntityToNavigator(lDatas);
        
        SkiArea skiArea = new SkiArea();
        skiArea.updateByLayerData(layerRecords);
        
        //NormalizeDB normalizeDB = new NormalizeDB();
        //normalizeDB.normalizeDB(skiArea, layerRecords, true);
        
        return "";//normalizeDB.log;
    }
    
    public static String normalizeDB(long skiAreaId, String connect, String login, String password) {
    // connect - строка для соединения с БД PostgreSQL
    // login - логин для доступа к PostgreSQL
    // password - пароль
        
        connect_jdbc = connect;
        connect_lgn = login;
        connect_pwd = password;
        
        connection = null;
        stmt = null;
        
        List<com.progmatic.snowball.entity.LayerData> lDatas = new ArrayList<>();
        readLayerDatas(skiAreaId, lDatas);
        
        return normalizeDB(lDatas);
    }
  
    public static void main(String[] args) throws Exception {

//        String log = normalizeDB(50, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres");
//        String log = normalizeDB(125, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres");
//        String log = normalizeDB(126, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres");
//        String log = normalizeDB(127, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres");
//        String log = normalizeDB(132, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres");

//        System.out.println(log);
//        System.out.println();
        // строим граф
//        SkiArea area1 = makeGraph(132, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres");
//        GeoPoint start = new GeoPoint(47.2479334391276, 11.9498214712176);
//        GeoPoint end = new GeoPoint(47.2622707574333, 11.9948944849699);
        SkiArea area1 = makeGraph(87, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres");
        
        for (SkiArea.NodalPoint np : area1.nodalPoints.values()) {
            System.out.println(np.point);
            for (Transition t : np.transitionsFrom) {
                System.out.println("          From: " + t.connection.data.name + " " + t.connection.data.id + " " + t.connection.isLift());
            }
            for (Transition t : np.transitionsTo) {
                System.out.println("          To: " + t.connection.data.name + " " + t.connection.data.id + " " + t.connection.isLift());
            }
        }
        
        GeoPoint start = new GeoPoint(46.9238662785771, 10.9451049311771);
        GeoPoint end = new GeoPoint(46.9253337722796, 10.9267239198463);
//        SkiArea area1 = makeGraph(50, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres");
        
        // проверяем алгоритмы
//        SkiArea area1 = makeGraph(87, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres");
//        GeoPoint start = new GeoPoint(47.1357337968425, 11.8588769422557);
//        GeoPoint end = new GeoPoint(47.1398944288248, 11.8542722599293);
        RiderPreferences rp = new RiderPreferences(7, false, 3);
        
        System.out.println(start);
        OnPisteRoute route1 = calculateOnPisteRouteDejkstra(area1, start, end, 0, null, true, true, true, rp);
        System.out.println("calculateOnPisteRouteDejkstra");
        if (route1.getError() == ok) {
            for (Transition t : route1.getTransitions())
                System.out.println(t.connection.data.name + " " + t.connection.data.id + " " + t.connection.isLift());
        } else
            System.out.println(route1.getError());

        System.out.println("calculateOnPisteRoute");
        OnPisteRoute route = calculateOnPisteRoute(area1, false, null, null, start, end, rp);
        for (Transition t : route.getTransitions())
            System.out.println(t.connection.data.name + " " + t.connection.data.id + " " + t.connection.isLift());
        
    }

}
