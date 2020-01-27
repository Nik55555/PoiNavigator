package com.progmatic.snowball.conv.convsvg;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BezierPath {
    
    public static double ACCURACY = 0.000000000001;
    
    static final Matcher matchPoint = Pattern.compile("\\s*(\\d+)[^\\d]+(\\d+)\\s*").matcher("");

    BezierListProducer path;
    boolean onlyOnePoint;
    
    /** Creates a new instance of Animate */
    public BezierPath()  {
    }

    public void parsePathString(String d) {

        this.path = new BezierListProducer();

        parsePathList(d);
    }
    
    protected void parsePathList(String list)
    {
        final Matcher matchPathCmd = Pattern.compile("([MmLlHhVvAaQqTtCcSsZz])|([-+]?((\\d*\\.\\d+)|(\\d+))([eE][-+]?\\d+)?)").matcher(list);

        //Tokenize
        LinkedList<String> tokens = new LinkedList<>();
        while (matchPathCmd.find()) {
            tokens.addLast(matchPathCmd.group());
        }

        char curCmd = 'Z';
        
        onlyOnePoint = (tokens.size() < 4);
        
        while (tokens.size() != 0) {
            String curToken = tokens.removeFirst();
            char initChar = curToken.charAt(0);
            if ((initChar >= 'A' && initChar <= 'Z') || (initChar >= 'a' && initChar <= 'z')) {
                curCmd = initChar;
            } else {
                tokens.addFirst(curToken);
            }

            switch (curCmd) {
                case 'M':
                    path.movetoAbs(nextFloat(tokens), nextFloat(tokens));
                    curCmd = 'L';
                    break;
                case 'm':
                	path.movetoRel(nextFloat(tokens), nextFloat(tokens));
                    curCmd = 'l';
                    break;
                case 'L':
                    path.linetoAbs(nextFloat(tokens), nextFloat(tokens));
                    break;
                case 'l':
                	path.linetoRel(nextFloat(tokens), nextFloat(tokens));
                    break;
                case 'H':
                    path.linetoHorizontalAbs(nextFloat(tokens));
                    break;
                case 'h':
                	path.linetoHorizontalRel(nextFloat(tokens));
                    break;
                case 'V':
                    path.linetoVerticalAbs(nextFloat(tokens));
                    break;
                case 'v':
                	path.linetoVerticalAbs(nextFloat(tokens));
                    break;
                case 'A':
                case 'a':
                    break;
                case 'Q':
                    path.curvetoQuadraticAbs(nextFloat(tokens), nextFloat(tokens),
                        nextFloat(tokens), nextFloat(tokens));
                    break;
                case 'q':
                	path.curvetoQuadraticAbs(nextFloat(tokens), nextFloat(tokens),
                        nextFloat(tokens), nextFloat(tokens));
                    break;
                case 'T':
                    path.curvetoQuadraticSmoothAbs(nextFloat(tokens), nextFloat(tokens));
                    break;
                case 't':
                	path.curvetoQuadraticSmoothRel(nextFloat(tokens), nextFloat(tokens));
                    break;
                case 'C':
                    path.curvetoCubicAbs(nextFloat(tokens), nextFloat(tokens),
                        nextFloat(tokens), nextFloat(tokens),
                        nextFloat(tokens), nextFloat(tokens));
                    break;
                case 'c':
                	path.curvetoCubicRel(nextFloat(tokens), nextFloat(tokens),
                        nextFloat(tokens), nextFloat(tokens),
                        nextFloat(tokens), nextFloat(tokens));
                    break;
                case 'S':
                    path.curvetoCubicSmoothAbs(nextFloat(tokens), nextFloat(tokens),
                        nextFloat(tokens), nextFloat(tokens));
                    break;
                case 's':
                	path.curvetoCubicSmoothRel(nextFloat(tokens), nextFloat(tokens),
                        nextFloat(tokens), nextFloat(tokens));
                    break;
                case 'Z':
                case 'z':
                    path.closePath();
                    break;
                default:
                    throw new RuntimeException("Invalid path element");
            }
        }
    }
    
    static protected float nextFloat(LinkedList<String> l) {
        String s = l.removeFirst();
        return Float.parseFloat(s);
    }
    

    // Найти точку на path в зависимости от interp - параметр уравнений, допустимые значения от 0 до 1
    public Vector2 eval(double interp) {
        Vector2 point = new Vector2();
  
        if (interp > 1) {
            interp = 1;
        }
        
        double curLength = path.curveLength * interp;
        for (Bezier bez : path.bezierSegs) {
            double bezLength = bez.getLength();
            if (curLength <= bezLength + ACCURACY)
            {
                double param = curLength / bezLength;
                bez.eval(param, point);
                break;
            }
            
            curLength -= bezLength;
        }
        
        return point;
    }

    // длина отрезка
    public double lineLength(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return (double) Math.sqrt(dx * dx + dy * dy);
    }
    
    public static double lineLength(Vector2 p1, Vector2 p2) {
        double dx = p2.x - p1.x, dy = p2.y - p1.y;
        return (double) Math.sqrt(dx * dx + dy * dy);
    }

    // длина - аппроксимация отрезками
    public double pathLength(int lineCount) {
        if (onlyOnePoint)
            return 0;
        Vector2 lastV = null;
        double llen = 0;

        for (int i = 0; i <= lineCount; i++) {
            Vector2 v = eval(((double)i)/lineCount);
            if (lastV != null)
                llen += lineLength(lastV, v);
            lastV = v;
        }

        return llen;
    }
}
