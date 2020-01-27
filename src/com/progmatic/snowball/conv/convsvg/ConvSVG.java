package com.progmatic.snowball.conv.convsvg;

import java.awt.image.BufferedImage;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class ConvSVG {
    
    public static String parseSVG(long skiAreaId, String connect, String login, String password, String filename, String image) throws Exception {
    	// connect - строка для соединения с БД PostgreSQL
        // login - логин для доступа к PostgreSQL
        // password - пароль
        // filename - имя конвертируемого SVG файла
        // image - имя jpg или png картинки для масштабирования
        
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        SVGSaxParser handler = new SVGSaxParser();
        
        SVGSaxParser.skiAreaId = skiAreaId;
        SVGSaxParser.filename = filename;
        
        SVGSaxParser.connect_jdbc = connect;
        SVGSaxParser.connect_lgn = login;
        SVGSaxParser.connect_pwd = password;
        SVGSaxParser.filename = filename;
        
        File f = new File(SVGSaxParser.filename);
        
        SVGSaxParser.filename = f.getName();
        
        Vector2 v;
        try {
            v = checkPicture(image);
        } catch(Exception e) {
            v = new Vector2(0, 0);
        }
        
        if (v.y != 0)
            SVGSaxParser.image_height = v.y;
        else
            throw new ParseException("Критическая ошибка: невозможно определить размер image (файл JPEG или PNG)!", 2);
        
        SVGSaxParser.roundToInt = true;
        
//        SVGSaxParser.debug = true;
        
        parser.parse(f, handler);
        
        return 
            "Критических ошибок нет. Список предупреждений: \n" + SVGSaxParser.errors + 
            "\n--------------\n" +
            "Параметры: skiAreaId=" + skiAreaId + ", image=" + image + ", h-size=" + v.x + ", v-size=" + v.y;
    }
    
    public static Vector2 checkPicture(String fn) throws IOException {
        BufferedImage bi = ImageIO.read(new File(fn));
        int w = bi.getWidth();
        int h = bi.getHeight();
        
        return new Vector2(w, h);
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length >= 5) {
            parseSVG(0, args[0], args[1], args[2], args[3], args[4]);
        } else {
            if (args.length > 0) {
                System.out.println("Wrong number of parameters!");
                return;
            }
//            String log = parseSVG(93, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\map007-mayrhofen.svg", "c:\\!!\\map007-mayrhofen-comp.jpg");
            String log = parseSVG(91, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\map031-obertauern.svg", "c:\\!!\\1.jpg");
//            String log = parseSVG(92, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\map001-serre-chevalier.svg", "c:\\!!\\1.jpg");
//            String log = parseSVG(90, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\map002-les-deux-alpes.svg", "c:\\!!\\1.jpg");
//            String log = parseSVG(99, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\map002-les-deux-alpes.svg", "c:\\!!\\1.jpg");
//            parseSVG(94, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\Plan_Hiver_BVT-FLG_15-16DU.svg", "c:\\!!\\1.jpg");
//            parseSVG(95, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\Serre Chevalier copy.svg", "c:\\!!\\1.jpg");
//            parseSVG(96, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\Alexander_ZermattCerviniaValtournenche.svg", "c:\\!!\\1.jpg");
//            parseSVG(97, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\99.svg", "c:\\!!\\1.jpg");
//            String log = parseSVG(8, "jdbc:postgresql://localhost:5432/snowball", "postgres", "postgres", "c:\\!!\\22.svg", "c:\\!!\\1.jpg");
            System.out.println(log);
        }
    }
}