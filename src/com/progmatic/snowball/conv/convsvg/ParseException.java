package com.progmatic.snowball.conv.convsvg;

public class ParseException extends RuntimeException {

    /**
     * @serial The embedded exception if tunnelling, or null.
     */    
    protected Exception exception;
    
    /**
     * @serial The line number.
     */
    protected int lineNumber;

    /**
     * @serial The column number.
     */
    protected int columnNumber;
    
    protected int errorCode = 0;

    /**
     * Creates a new ParseException.
     * @param message The error or warning message.
     * @param line The line of the last parsed character.
     * @param column The column of the last parsed character.
     */
    public ParseException (String message, int line, int column) {
        super(message);
        exception = null;
        lineNumber = line;
        columnNumber = column;
    }
    
    public ParseException (String message, int errCode) {
        super(message);
        exception = null;
        errorCode = errCode;
    }
    
    /**
     * Creates a new ParseException wrapping an existing exception.
     *
     * <p>The existing exception will be embedded in the new
     * one, and its message will become the default message for
     * the ParseException.
     * @param e The exception to be wrapped in a ParseException.
     */
    public ParseException (Exception e) {
        exception = e;
        lineNumber = -1;
        columnNumber = -1;
    }
    
    /**
     * Creates a new ParseException from an existing exception.
     *
     * <p>The existing exception will be embedded in the new
     * one, but the new exception will have its own message.
     * @param message The detail message.
     * @param e The exception to be wrapped in a SAXException.
     */
    public ParseException (String message, Exception e) {
        super(message);
        this.exception = e;
    }
    
    /**
     * Return a detail message for this exception.
     *
     * <p>If there is a embedded exception, and if the ParseException
     * has no detail message of its own, this method will return
     * the detail message from the embedded exception.
     * @return The error or warning message.
     */
    @Override
    public String getMessage () {
        String message = super.getMessage();
        
        if (message == null && exception != null) {
            return exception.getMessage();
        } else {
            return message;
        }
    }
    
    /**
     * Return the embedded exception, if any.
     * @return The embedded exception, or null if there is none.
     */
    public Exception getException () {
        return exception;
    }

    /**
     * Returns the line of the last parsed character.
     * @return 
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Returns the column of the last parsed character.
     * @return 
     */
    public int getColumnNumber() {
        return columnNumber;
    }

    /* возвращает код ошибки конвертации для ConvSVG
        0 - Не удалось установить соединение с базой данной
        1 - в файле SVG не указана высота
        2 - невозможно определить размер image (файл JPEG или PNG)
        3 - ошибка в единице измерения высоты картинки (свойство height тега <svg>)
    */
    public int getErrorCode() {
        return errorCode;
    }

}
