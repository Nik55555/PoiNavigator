package com.progmatic.snowball.navigator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.annotation.XmlType;

public class LiftQueueStat {
    final static long MIN_POINTS_IN_TRACK = 300;
    final static long MAX_DATA_COUNT_DSP = 1000;
    final static long MIN_DATA_COUNT = 10;
    final static double MIN_LIFT_SPEED = 1.2; // m/s
    final static double MAX_LIFT_SPEED = 7; // m/s
    
    final static String STD_DATE_FORMAT = "yyyy-MM-dd";
    
    static Date lastLiftQueueStatDate = null;
    static String lastLiftQueueStatDateString = "";
    static Date checkLiftQueueStatDate = null;
    static String checkLiftQueueStatDateString = "";
    
    public static String connect_jdbc = "jdbc:postgresql://localhost:5432/snowball";
    public static String connect_lgn = "postgres";
    public static String connect_pwd = "postgres";

    // список предупреждений
    public static String info = "";
    public static long normalTrackCount = 0;
    public static long smallTrackCount = 0;
    public static long speedTrackCount = 0;
    public static long queueTrackCount = 0;
    
    private static Connection connection = null;
    private static Statement stmt = null;
    private static Statement stmt1 = null;
    
    boolean writeDataFiles = false;
    
    @XmlType(name="LiftQueueStat.LiftQueue")
    public static class LiftQueue implements Comparable<LiftQueue>{
        public long transitionId;
        public long time;
        public long waitTime;
        
        @Override
	public String toString() {
		return "transitionId=" + transitionId + " time=" + time + " waitTime=" + waitTime;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != getClass())
			return false;
		final LiftQueue t = (LiftQueue) obj;
		return (transitionId == t.transitionId && time == t.time);
	}

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 59 * hash + (int) (this.transitionId ^ (this.transitionId >>> 32));
            hash = 59 * hash + (int) (this.time ^ (this.time >>> 32));
            return hash;
        }

        @Override
        public int compareTo(LiftQueue t) {
            if (transitionId == t.transitionId) {
                if (time == t.time)
                    return 0;
                return time < t.time ? -1 : 1;
            }
            return transitionId < t.transitionId ? -1 : 1;
        }
        
        public LiftQueue() {}
        LiftQueue(long transitionId, long time, long waitTime) {
            this.transitionId = transitionId;
            this.time = time;
            this.waitTime = waitTime;
        }
    }    
   
    public static class ConnectionSpeed {
        long layerDataId;
        double avgSpeed;
        long speedSetCounter;
    }
    
    public static class LiftQueueMathStat {
        public short half_hour_num;
        public short day_of_week;
        public double avg_wait;
        public long cnt_wait = 0;
        public double dsp_wait;
        public long holiday_id;
        public long transition_id;
        
        double sigma() {
            return dsp_wait <= 0 ? 0 : Math.sqrt(dsp_wait);
        }
        
        boolean check3sigma(double waitTime) {
            return Math.abs(avg_wait - waitTime) < 3 * sigma();
        }
    }
    
    public static class LiftSpeedMathStat {
        public short rider_level = 0;
        public double avg_speed;
        public long cnt_speed = 0;
        public double dsp_speed;
        public long layer_data_id;
    }
    
    public static final class GlobalLiftQueueMatStat {
        public long transition_id;
        public double avg_wait;
        double dsp_wait;
        double sigma;
        
        GlobalLiftQueueMatStat(long transition_id) {
            this.transition_id = transition_id;
        }
        
        public GlobalLiftQueueMatStat(long transition_id, double avg_wait, double dsp_wait) {
            this.transition_id = transition_id;
            this.avg_wait = avg_wait > 0 ? avg_wait : 0;
            setDsp_wait(dsp_wait);
        }

        public double getDsp_wait() {
            return dsp_wait;
        }

        public double getSigma() {
            return sigma;
        }

        public void setDsp_wait(double dsp_wait) {
            this.dsp_wait = dsp_wait > 0 ? dsp_wait : 0;
            this.sigma = Math.sqrt(this.dsp_wait);
        }
        
    }
    
    protected static void log(String msg) {
        log(msg, true);
    }
    
    protected static void log(String msg, boolean toConsole) {
        if (toConsole)
            System.out.println(msg);
        info += msg + "\n";
    }
    
    public static short halfHourNum(long time) {
        // time в секундах
        // 0..47
        return (short) ((time % 86400) / 1800);
    }
    
    public static short dayOfWeekNum(long time) {
        // time в секундах
        // 0..6
        return  (short)((time / 86400 + 3 /* 01.01.1970 - четверг*/) % 7);
    }
    
    public static LiftQueueMathStat calculateAvgLiftQueue(LiftQueueMathStat lqms, short holidayId, ArrayList<LiftQueue> liftQueues) {
        // если запись новая (по ключу), то lqms.transition_id должен быть равен 0
        liftQueues.stream().forEach((liftQueue) -> {
            calculateAvgLiftQueue(lqms, holidayId, liftQueue);
        });
        
        return lqms;
    }
    
    public static LiftQueueMathStat calculateAvgLiftQueue(LiftQueueMathStat lqms, short holidayId, LiftQueue liftQueue) {
        // если запись новая (по ключу), то lqms.cnt_wait должен быть равен 0
        
        // проверка на соответствие транзишенов
        short halfHourNum = halfHourNum(liftQueue.time);
        short dayOfWeekNum = dayOfWeekNum(liftQueue.time);
        if (lqms.cnt_wait != 0) {
            if (
                    lqms.transition_id != liftQueue.transitionId || 
                    lqms.day_of_week != dayOfWeekNum || 
                    lqms.half_hour_num != halfHourNum ||
                    lqms.holiday_id != holidayId
                )
                return lqms;
        }
        
        if (lqms.cnt_wait <= 0) {
            // первая запись
            lqms.half_hour_num = halfHourNum;
            lqms.day_of_week = dayOfWeekNum;
            lqms.cnt_wait = 1;
            lqms.avg_wait = liftQueue.waitTime;
            lqms.dsp_wait = 0;
            lqms.holiday_id = 0;
            lqms.transition_id = liftQueue.transitionId;
        } else {
            lqms.cnt_wait++; // после этого будет всегда >= 2
            
            long iNumPoints = lqms.cnt_wait > MAX_DATA_COUNT_DSP ? MAX_DATA_COUNT_DSP : lqms.cnt_wait;

            // среднее
            double iAverage = lqms.avg_wait * (iNumPoints - 1) / iNumPoints +
                    (double)liftQueue.waitTime / iNumPoints;

            // дисперсия
            double diff = iAverage - liftQueue.waitTime;
            double iDispersion = lqms.dsp_wait * (iNumPoints - 2) / (iNumPoints - 1) +
                    (diff * diff) / (iNumPoints - 1);

            // сигма
            double iSigma = Math.sqrt(iDispersion);
            
            // дисперсия меняется всегда
            lqms.dsp_wait = iDispersion;
            
            // среднее меняется только если выполнено правило 3 сигма (первые MIN_DATA_COUNT значений пишутся всегда)
            if (Math.abs(diff) < 3 * iSigma || lqms.cnt_wait <= MIN_DATA_COUNT)
                lqms.avg_wait = iAverage;
        }
        
        return lqms;
    }
    
    public static LiftSpeedMathStat calculateAvgLiftSpeed(LiftSpeedMathStat lsms, ConnectionSpeed liftSpeed) {
        // если запись новая (по ключу), то lqms.cnt_speed должен быть равен 0
        
        // проверка на соответствие транзишенов
        if (lsms.cnt_speed != 0) {
            if (lsms.layer_data_id != liftSpeed.layerDataId)
                return lsms;
            if (liftSpeed.speedSetCounter <= 0)
                return lsms;
        }
        
        if (lsms.cnt_speed <= 0) {
            // первая запись
            lsms.cnt_speed = 1;
            lsms.avg_speed = liftSpeed.avgSpeed;
            lsms.dsp_speed = 0;
            lsms.layer_data_id = liftSpeed.layerDataId;
        } else {
            lsms.cnt_speed += liftSpeed.speedSetCounter; // после этого будет всегда >= 2
            
            long iNumPoints = lsms.cnt_speed > MAX_DATA_COUNT_DSP ? MAX_DATA_COUNT_DSP : lsms.cnt_speed;

            // среднее
            long cnt = Math.min(liftSpeed.speedSetCounter, iNumPoints - 1);
            double iAverage = (lsms.avg_speed * (iNumPoints - cnt) + liftSpeed.avgSpeed * cnt) / iNumPoints;

            // дисперсия
            double diff = iAverage - liftSpeed.avgSpeed;
            
            double iDispersion = lsms.dsp_speed;
            for (int i = 0; i < cnt; i++) {
                iDispersion = iDispersion * (iNumPoints - 1 - cnt + i) / (iNumPoints - cnt + i) +
                    (diff * diff) / (iNumPoints - cnt + i);
            }

            // сигма
            double iSigma = Math.sqrt(iDispersion);
            
            // дисперсия меняется всегда
            lsms.dsp_speed = iDispersion;
            
            // среднее меняется только если выполнено правило 3 сигма (первые MIN_DATA_COUNT значений пишутся всегда)
            if (Math.abs(diff) < 3 * iSigma || lsms.cnt_speed <= MIN_DATA_COUNT)
                lsms.avg_speed = iAverage;
        }
        
        return lsms;
    }

    private static void readTracksAndCalcStats(long skiAreaId, SkiArea skiArea) {
        if (checkLiftQueueStatDate.compareTo(lastLiftQueueStatDate) <= 0) {
            log("Calculation of queues for the specified period has already been made");
            return;
        }
        try {
            if (connection == null)
                connectDB();
            
            // цикл чтения данных из таблицы
            ResultSet rs = stmt.executeQuery(
                    "SELECT b.* FROM track a, track_point b WHERE a.id=b.track_id AND " +
                    "((" + skiArea.x1 + "<=a.x1 AND a.x1<=" + skiArea.x2 + ") OR (a.x1<=" + skiArea.x1 + " AND " + skiArea.x1 + "<=a.x2)) AND " +
                    "((" + skiArea.y1 + "<=a.y1 AND a.y1<=" + skiArea.y2 + ") OR (a.y1<=" + skiArea.y1 + " AND " + skiArea.y1 + "<=a.y2)) AND " +
                    "(a.name>'" + lastLiftQueueStatDateString + "' AND a.name<='" + checkLiftQueueStatDateString + "') " +
                    //"a.x1>=" + skiArea.x1 + " AND a.y1>=" + skiArea.y1 + " AND a.x2<=" + skiArea.x2 + " AND a.y2<=" + skiArea.y2 + " " +
                    "ORDER BY a.id, b.time"
            );
            long trackId = -1;
            TrackPoint lastTrackPoint = null;
            List<TrackPoint> trackPoints = new ArrayList<>();
            while (rs.next()) {
                TrackPoint tp = new TrackPoint(rs.getLong("id"), rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getInt("elevation"), rs.getTimestamp("time"));
                long lastTrackId = rs.getLong("track_id");
                if (trackId < 0)
                    trackId = lastTrackId;
                else if (trackId != lastTrackId) {
                    // запись трека
                    //if (trackPoints.size() >= MIN_POINTS_IN_TRACK)
                    //    GpxWriter.writeGpxTrack("c:/!/gpx/" + skiAreaId + "/", trackId, trackPoints);
                    
                    // начало нового трека
                    calculateAndSaveRowDataStats(skiAreaId, skiArea, trackPoints, trackId);
                    trackId = lastTrackId;
                    trackPoints.clear();
                    lastTrackPoint = null;
                }
                
                // фильтр точек по прямоугольнику области
                if (skiArea.y1 <= tp.latitude && tp.latitude <= skiArea.y2 &&
                    skiArea.x1 <= tp.longitude && tp.longitude <= skiArea.x2) {
                    // ... и по повторам координат
                    if (lastTrackPoint == null || !Objects.equals(lastTrackPoint.latitude, tp.latitude) || !Objects.equals(lastTrackPoint.longitude, tp.longitude)) {
                        trackPoints.add(tp);
                        lastTrackPoint = tp;
                    }
                }
            }
            if (!trackPoints.isEmpty())
                calculateAndSaveRowDataStats(skiAreaId, skiArea, trackPoints, trackId);
            
            // запись даты окончания выборки (в случае успешной обработки хотя бы одной зоны)
            writeLastDate();
            
        } catch (Exception e) {
            // закрываем соединения
            try {
                if (stmt != null)
                    stmt.close();
                if (stmt1 != null)
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
            log("Ошибка при попытке чтении данных из track и track_point");
        }
    }

    private static void readNodalPoints2SkiArea(long skiAreaId, SkiArea skiArea) {
        try {
            // цикл чтения данных из таблицы
            ResultSet rs = stmt.executeQuery("SELECT * FROM nodal_point b WHERE ski_area_id=" + skiAreaId);

            skiArea.nodalPoints.clear();
            
            while (rs.next()) {
                GeoPoint gp = new GeoPoint(rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getInt("altitude"));
                SkiArea.NodalPoint np = new SkiArea.NodalPoint(gp);
                np.stringTransitionsFrom = rs.getString("transitions_from");
                np.stringTransitionsTo = rs.getString("transitions_to");
                skiArea.nodalPoints.put(gp, np);
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
            log("Ошибка при попытке чтении данных из track и track_point");
        }
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
            //ResultSet rs = stmt.executeQuery("SELECT * FROM layer_data WHERE ski_area_id=" + skiAreaId);
            //ResultSet rs = stmt.executeQuery("SELECT b.* FROM ski_area a join layer_data b on (b.ski_area_id=a.id) WHERE a.ski_area_id=" + skiAreaId);
            ResultSet rs = stmt.executeQuery(
                    "SELECT * FROM layer_data WHERE ski_area_id=" + skiAreaId + " UNION " +
                    "SELECT b.* FROM ski_area a join layer_data b on (b.ski_area_id=a.id) WHERE a.ski_area_id=" + skiAreaId
            );
            while (rs.next()) {
                com.progmatic.snowball.entity.LayerData lData = new com.progmatic.snowball.entity.LayerData(
                    rs.getLong("id"), skiAreaId, rs.getLong("layer_type_id"), null, null, 0, rs.getString("name"),
                    rs.getDouble("x1"), rs.getDouble("y1"), rs.getDouble("x2"), rs.getDouble("y2"),
                    rs.getString("data")
                );
                lData.setSchemeData(rs.getString("scheme_data"));
                lData.setDemData(rs.getString("dem_data"));
                
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
    }

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
            stmt1 = connection.createStatement();
            
        } catch (SQLException e) {
            throw new Exception("Connection failed! Check login and password or host and port.");
        }
    }
    
    private static List<LayerData> convertEntityToNavigator(List<com.progmatic.snowball.entity.LayerData> lDatas) {
        List<LayerData> layerRecords = new ArrayList<>();
        for (com.progmatic.snowball.entity.LayerData lData : lDatas) {
            LayerData ld = new LayerData(
                lData.getId(), lData.getLayerTypeId(), lData.getSkiAreaId(), lData.getName(), lData.getDescription(),
                lData.getX1(), lData.getY1(), lData.getX2(), lData.getY2(), lData.getData(),
                lData.getSchemeData(), lData.getDemData()
            );
            layerRecords.add(ld);
        }
        return layerRecords;
    }
    
    public static SkiArea makeGraph(SkiArea.Type skiAreaType, List<com.progmatic.snowball.entity.LayerData> lDatas) {
        // lDatas - список из LayerData из entity сервера
        // возвращает SkiArea, в котором граф составляют:
        // nodalPoints - список узлов с входящими и исходящими транзишенами
        // transitions - список транзишенов
        
        List<LayerData> layerRecords = convertEntityToNavigator(lDatas);
        
        SkiArea skiArea = new SkiArea();
        skiArea.updateByLayerData(skiAreaType, layerRecords);
        
        return skiArea;
    }
    
    public static SkiArea makeGraph(SkiArea.Type skiAreaType, long skiAreaId, String connect, String login, String password) {
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
        
        return makeGraph(skiAreaType, lDatas);
    }
    
    static void saveLiftQueues(long skiAreaId, List<LiftQueue> liftQueues) {
        try {
            // запись
            for(LiftQueue liftQueue : liftQueues) {
                stmt1.executeUpdate(
                        "INSERT INTO current_queue (transition_id, time, wait_time) " +
                                "VALUES (" + liftQueue.transitionId + ", " + liftQueue.time + ", " + liftQueue.waitTime + ");"
                );
                
                // статистика
                if (liftQueue.time > 1480550400 /*01.12.2016*/) {
                    LiftQueueMathStat lqms = new LiftQueueMathStat();
                    lqms.half_hour_num = halfHourNum(liftQueue.time);
                    lqms.day_of_week = dayOfWeekNum(liftQueue.time);
                    lqms.holiday_id = 0; // TODO
                    lqms.transition_id = liftQueue.transitionId;
                    ResultSet rs = stmt1.executeQuery(
                            "SELECT * FROM lift_queue " + 
                            "WHERE " + 
                                "half_hour_num=" + lqms.half_hour_num + " " +
                                "AND day_of_week=" + lqms.day_of_week + " " +
                                "AND holiday_id=" + lqms.holiday_id + " " + // TODO
                                "AND transition_id=" + lqms.transition_id
                    );

                    if (rs.next()) {
                        lqms.avg_wait = rs.getDouble("avg_wait");
                        lqms.dsp_wait = rs.getDouble("dsp_wait");
                        lqms.cnt_wait = rs.getLong("cnt_wait");
                    }

                    // расчёт статистики (все формулы - рекурретные)
                    calculateAvgLiftQueue(lqms, (short)0, liftQueue);

                    if (lqms.cnt_wait <= 1) {
                        // новая запись
                        stmt1.executeUpdate(
                                "INSERT INTO lift_queue (half_hour_num, day_of_week, holiday_id, transition_id, avg_wait, dsp_wait, cnt_wait, ski_area_id) " +
                                        "VALUES (" + lqms.half_hour_num + ", " + lqms.day_of_week + ", " + lqms.holiday_id + ", " + lqms.transition_id + ", " + lqms.avg_wait + ", " + lqms.dsp_wait + ", " + lqms.cnt_wait + ", " + skiAreaId + ");"
                        );
                    } else {
                        // изменение существующей
                        stmt1.executeUpdate(
                                "UPDATE lift_queue " + 
                                "SET avg_wait=" + lqms.avg_wait + ", dsp_wait=" + lqms.dsp_wait + ", cnt_wait=" + lqms.cnt_wait + " " +
                                "WHERE half_hour_num=" + lqms.half_hour_num + " AND day_of_week=" + lqms.day_of_week + " AND holiday_id=" + lqms.holiday_id + " AND transition_id=" + lqms.transition_id
                        );
                    }
                }
                // окончание статистики
            }
            if (!liftQueues.isEmpty())
                log("       ...queues detected: " + liftQueues.size());
        } catch (SQLException ex) {
            log(ex.getMessage());
        }
    }

    static void saveLiftSpeeds(long skiAreaId, List<ConnectionSpeed> liftSpeeds) {
        try {
            for(ConnectionSpeed connectionSpeed : liftSpeeds) {
                // проверка на допустимую скорость лифта
                if (connectionSpeed.avgSpeed < MIN_LIFT_SPEED || connectionSpeed.avgSpeed > MAX_LIFT_SPEED)
                    continue;
                
                ResultSet rs = stmt1.executeQuery(
                        "SELECT * FROM layer_data_speed " + 
                        "WHERE " + 
                            "layer_data_id=" + connectionSpeed.layerDataId
                );

                LiftSpeedMathStat lsms = new LiftSpeedMathStat();
                boolean isNew = true;
                if (rs.next()) {
                    isNew = false;
                    lsms.avg_speed = rs.getDouble("avg_speed");
                    lsms.dsp_speed = rs.getDouble("dsp_speed");
                    lsms.cnt_speed = rs.getLong("cnt_speed");
                    lsms.layer_data_id = rs.getLong("layer_data_id");
                }
                    
                calculateAvgLiftSpeed(lsms, connectionSpeed);
                    
                if (!isNew) {
                    // изменение существующей записи
                    stmt1.executeUpdate(
                            "UPDATE layer_data_speed " + 
                            "SET rider_level=" + lsms.rider_level + ", avg_speed=" + lsms.avg_speed + ", dsp_speed=" + lsms.dsp_speed + ", cnt_speed=" + lsms.cnt_speed + " " +
                            "WHERE layer_data_id=" + lsms.layer_data_id
                    );
                } else {
                    // новая запись
                    stmt1.executeUpdate(
                            "INSERT INTO layer_data_speed (rider_level, avg_speed, dsp_speed, cnt_speed, layer_data_id) " +
                                    "VALUES (" + lsms.rider_level + ", " + lsms.avg_speed + ", " + lsms.dsp_speed + ", " + lsms.cnt_speed + ", " + lsms.layer_data_id + ");"
                    );
                }
            }
            if (!liftSpeeds.isEmpty())
                log("       ...speeds calculated: " + liftSpeeds.size());
        } catch (SQLException ex) {
            log(ex.getMessage());
        }
    }

    static Type LiftQueueType = new TypeToken<ArrayList<LiftQueue>>(){}.getType();
    static Type ConnectionSpeedType = new TypeToken<ArrayList<ConnectionSpeed>>(){}.getType();
    
    private static void calculateAndSaveRowDataStats(long skiAreaId, SkiArea skiArea, List<TrackPoint> trackPoints, long trackId) {
        if (trackPoints.size() < MIN_POINTS_IN_TRACK) {
            smallTrackCount++;
            return;
        }
        
        normalTrackCount++;
        
        List<SkiArea.NodalPoint> nps = new ArrayList<>(skiArea.nodalPoints.values());
        //skiArea.updateNavlibSkiArea(0, skiArea.layerRecords, nps);
        
        String jsonDataForUI = null;
        // прогон трека через navlib
        for (TrackPoint tp : trackPoints) {
            GeoPoint gp = new GeoPoint(tp.latitude, tp.longitude, tp.elevation);
            //jsonDataForUI = com.mobangels.navlib.Navigator.navlibCheckAlarm(gp, tp.time, 0, 0, false);
        }
        if (jsonDataForUI == null)
            return;
        
        log("   Track " + trackId);
        
        // проверяем флаги очередей и скоростей
        //DataForUI dataForUI = DataForUI.fromJson(jsonDataForUI);
        // очереди
        if (true/*dataForUI.queueWasCalculated*/) {
            queueTrackCount++;
            String jsonQueues = "";//com.mobangels.navlib.Navigator.getLiftQueues(skiAreaId);
            List<LiftQueue> liftQueues = new Gson().fromJson(jsonQueues, LiftQueueType);
            // удаление
            saveLiftQueues(skiAreaId, liftQueues);
            //GpxWriter.writeUsingFiles("/!/lift_queues/" + skiAreaId + "/" + trackId + ".json", jsonQueues);
        }
        
        // скорости
        if (true/*dataForUI.speedWasCalculated*/) {
            speedTrackCount++;
            String jsonSpeeds = "";//com.mobangels.navlib.Navigator.getConnectionSpeeds(skiAreaId);
            List<ConnectionSpeed> conSpeeds = new Gson().fromJson(jsonSpeeds, ConnectionSpeedType);
            List<ConnectionSpeed> liftSpeeds = new ArrayList<>();
            for (ConnectionSpeed conSpd : conSpeeds) {
                SkiArea.Transition tra = skiArea.transitions.get((Long)conSpd.layerDataId);
                if (tra != null && tra.connection != null && tra.connection.isLift())
                    liftSpeeds.add(conSpd);
            }
            saveLiftSpeeds(skiAreaId, liftSpeeds);
        }
    }

    static {
      System.loadLibrary("navlib");
    }
    
    public static void readLastDate() {
        if (lastLiftQueueStatDate != null)
            return;
        
        try {
            // читаем время окончания предыдущего прогона из БД
            SimpleDateFormat sdf = new SimpleDateFormat(STD_DATE_FORMAT);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            ResultSet rs0 = stmt.executeQuery("SELECT * FROM parameter WHERE name='lastLiftQueueStatDate'");
            if (rs0.next()) {
                lastLiftQueueStatDateString = rs0.getString("value");
                try {
                    lastLiftQueueStatDate = sdf.parse(lastLiftQueueStatDateString);
                } catch (ParseException ex) {
                    log("Date convert error for lastLiftQueueStatDate = " + lastLiftQueueStatDateString, true);
                }
            } else {
                // создаём параметр времени
                lastLiftQueueStatDate = new Date(0);
                lastLiftQueueStatDateString = sdf.format(lastLiftQueueStatDate);
                stmt1.executeUpdate(
                    "INSERT INTO parameter (name, value) " +
                    "VALUES ('lastLiftQueueStatDate', '" + sdf.format(lastLiftQueueStatDate) + "')"
                );
            }
            if (checkLiftQueueStatDate == null) {
                checkLiftQueueStatDate = new Date((long)(System.currentTimeMillis() / 86400000 - 1) * 86400000);
                checkLiftQueueStatDateString = sdf.format(checkLiftQueueStatDate);
            }
        } catch (SQLException ex) {
            log(ex.getMessage());
        }
    }
    
    public static void writeLastDate() {
        if (checkLiftQueueStatDate == null)
            return;
        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(STD_DATE_FORMAT);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            stmt1.executeUpdate("UPDATE parameter " + 
                    "SET value='" + sdf.format(checkLiftQueueStatDate) + "' " +
                    "WHERE name='lastLiftQueueStatDate'");
        } catch (SQLException ex) {
            log(ex.getMessage());
        }
    }
    
    public static void calcStatsOnArea(SkiArea.Type skiAreaType, long skiAreaId, String server, String login, String password) throws Exception {
        SkiArea area1 = makeGraph(SkiArea.Type.Winter, skiAreaId, server, login, password);
        readNodalPoints2SkiArea(skiAreaId, area1);

        readLastDate();
        
        System.out.println("SkiArea " + skiAreaId + " in the process...");
        long lastTime = lastLiftQueueStatDate.getTime() / 1000;
        long checkTime = checkLiftQueueStatDate.getTime() / 1000 + 86399;
        stmt1.executeUpdate(
            "DELETE FROM current_queue WHERE \"time\">" + lastTime + " AND \"time\"<=" + checkTime + " AND abs(transition_id) IN " +
                "(SELECT id FROM layer_data WHERE ski_area_id=" + skiAreaId + " UNION " +
                " SELECT b.id FROM ski_area a join layer_data b on (b.ski_area_id=a.id) WHERE a.ski_area_id=" + skiAreaId + ")"
        );

        readTracksAndCalcStats(skiAreaId, area1);
    }
    
    public static void calcStatsOnAreas(SkiArea.Type skiAreaType, ArrayList<Long> areas, String server, String login, String password) throws Exception {
        readLastDate();
        if (checkLiftQueueStatDate.compareTo(lastLiftQueueStatDate) <= 0) {
            log("Calculation of queues for the specified period has already been made");
            return;
        }
        for(long skiAreaId : areas) {
            calcStatsOnArea(SkiArea.Type.Winter, skiAreaId, server, login, password);
        }
    }
    
    public static ArrayList<Long> getActualSkiAreas(String connect_jdbc, String connect_lgn, String connect_pwd) {
        ArrayList<Long> result = new ArrayList<>();
        try {
            if (connection == null)
                connectDB();
            ResultSet rs = stmt.executeQuery(
                "SELECT id FROM ski_area where ski_area_id is null and scheme_status = 1 ORDER BY id"
            );
            
            while (rs.next()) {
                result.add(rs.getLong(1));
            }
        } catch (Exception ex) {
            Logger.getLogger(LiftQueueStat.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }
    
    // глобальная дисперсия по LayerData
    static void calcAndSaveGlobalLiftStat() throws Exception {
        try {
            if (connection == null)
                connectDB();
            
            // очистка таблицы глобальных дисперсий
            stmt.executeUpdate(
                "DELETE FROM lift_dsp"
            );
            
            // чтение отсортированных по транзишену и времени очередей
            ResultSet rs = stmt.executeQuery(
                "SELECT * FROM current_queue ORDER BY transition_id, \"time\""
            );
            
            double avg_wait = 0;
            double dsp_wait = 0;
            long cnt_wait = 0;

            long curTraId = 0;
            double lastWaitTime = 0;
            
            while (rs.next()) {
                long transition_id = rs.getLong("transition_id");
                long time = rs.getLong("time");
                double waitTime = rs.getLong("wait_time");
                
                if (curTraId != transition_id) {
                    if (transition_id != 0) {
                        // окончательное вычисление и запись дисперсии
                        if (cnt_wait > 1) {
                            dsp_wait /= cnt_wait - 1;
                            avg_wait /= cnt_wait;
                            // новая запись
                            stmt1.executeUpdate(
                                "INSERT INTO lift_dsp (transition_id, avg_wait, dsp_wait, cnt_wait) " +
                                "VALUES (" + transition_id + ", " + avg_wait + ", " + dsp_wait + ", " + cnt_wait + ");"
                            );
                        }
                    }
                    
                    curTraId = transition_id;
                    cnt_wait = 0;
                    
                    avg_wait = 0;
                    dsp_wait = 0;
                    cnt_wait = 0;
                }
                
                // обычная итерация
                avg_wait += waitTime;
                if (cnt_wait > 0) {
                    double diff = waitTime - lastWaitTime;
                    dsp_wait += diff * diff;
                }
                
                lastWaitTime = waitTime;
                
                cnt_wait++;
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(LiftQueueStat.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    public static void main(String[] args) throws Exception {
        // запуск с параметрами: skiAreaId, connection_string, login, password
        // для запуска необходима библиотека navlib, собранная для сервера ()

        // тесты
        //int ofs = LiftQueues.getLocalTimeOffset("Europe/Moscow");
        //calcAndsaveGlobalLiftStat();
        
        /*
        String timeZone = "Europe/Moscow";
        long locTime = LiftQueues.getLocalNow(timeZone);
        
        ArrayList<GlobalLiftQueueMatStat> globalLQD = new ArrayList<>();
        globalLQD.add(new GlobalLiftQueueMatStat(1, 25, 1000));
        globalLQD.add(new GlobalLiftQueueMatStat(-1, 0, 0));
        globalLQD.add(new GlobalLiftQueueMatStat(3, 88, 10000));
        
        LiftQueues liftQueues = new LiftQueues(globalLQD);
        liftQueues.addQueue(new LiftQueue(1, locTime - 300, 30));
        liftQueues.addQueue(new LiftQueue(1, locTime - 200, 300));
        liftQueues.addQueue(new LiftQueue(1, locTime - 100, 0));
        
        liftQueues.addQueue(new LiftQueue(2, locTime - 899, 30));
        liftQueues.addQueue(new LiftQueue(2, locTime - 200, 300));
        liftQueues.addQueue(new LiftQueue(2, locTime - 100, 0));
        
        ArrayList<Long> conIds = new ArrayList<>();
        conIds.add((long)1);
        conIds.add((long)2);
        conIds.add((long)3);
        ArrayList<LiftQueueStat.LiftQueue> currentLiftQueues = liftQueues.getCurrentQueues(conIds);
        
        if (true) return;
        // окончание тестов
        */

        if (args.length >= 3) {
            connect_jdbc = args[1];
            connect_lgn = args[2];
            connect_pwd = args[3];
            if (args.length >= 4) {
                long skiAreaId =  Long.parseLong(args[4]);
                calcStatsOnArea(SkiArea.Type.Winter, skiAreaId, connect_jdbc, connect_lgn, connect_pwd);
            } else {
                ArrayList<Long> areas = getActualSkiAreas(connect_jdbc, connect_lgn, connect_pwd);
                calcStatsOnAreas(SkiArea.Type.Winter, areas, connect_jdbc, connect_lgn, connect_pwd);
            }
        } else {
            if (args.length > 0) {
                System.out.println("Wrong number of parameters!");
                return;
            }
            
            connect_jdbc = "jdbc:postgresql://localhost:5432/snowball";
            connect_lgn = "postgres";
            connect_pwd = "postgres";
            
            ArrayList<Long> areas = getActualSkiAreas(connect_jdbc, connect_lgn, connect_pwd);
            calcStatsOnAreas(SkiArea.Type.Winter, areas, connect_jdbc, connect_lgn, connect_pwd);
        }
        log("----------------");
        log("Processed normal tracks: " + normalTrackCount + ", queues detected: " + queueTrackCount + ", speeds calculated: " + speedTrackCount);
        log("Ignored small tracks: " + smallTrackCount);
        log("----------------");
        calcAndSaveGlobalLiftStat();
        log("Global statistics (lift queues) have been restated");
    }
}
