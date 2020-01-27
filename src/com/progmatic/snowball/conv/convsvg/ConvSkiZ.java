package com.progmatic.snowball.conv.convsvg;

import java.io.File;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class ConvSkiZ {
    
    public static String parseOSM(String connect, String login, String password, String filename, boolean checkNodes) throws Exception {
    	// connect - строка для соединения с БД PostgreSQL
        // login - логин для доступа к PostgreSQL
        // password - пароль
        // filename - имя конвертируемого OSM файла
        // checkNodes - установить в true для нормальной конвертации (используется для ускорения конвертации при наличии точек в таблице)

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        SaxPars handler = new SaxPars();
        
        SaxPars.connect_jdbc = connect;
        SaxPars.connect_lgn = login;
        SaxPars.connect_pwd = password;
        SaxPars.checkNodes = checkNodes;
        SaxPars.filename = filename;
        
        // устанавливаем значения точности и приближения для географических координат (метры / 111111)
        SaxPars.MAXROUND = 15 / 111111; // точность приближения (используется также для отрезания кусков трасс)
        SaxPars.ACCURACY = 0.1 / 111111; // точность равенства координат
        SaxPars.ACCURACY_HALF = 0.05 / 111111; // увеличенная вдвое точность равенства координат
                
        parser.parse(new File(filename), handler);
        return SaxPars.errors;
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length >= 4) {
            if (args.length >= 5)
                SaxPars.checkNodes = !(args[4].toLowerCase().equals("false"));
            parseOSM(args[0], args[1], args[2], args[3], (args.length < 5 || args[4].toLowerCase().equals("true")));
            return;
        } else {
            if (args.length > 0) {
                System.out.println("Wrong number of parameters!");
                return;
            }
        }
        
        parseOSM("jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!\\planet_pistes.osm\\planet_pistes.osm", true);
                
    }

}
