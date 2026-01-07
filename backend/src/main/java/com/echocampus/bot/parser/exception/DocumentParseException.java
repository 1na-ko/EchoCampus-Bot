package com.echocampus.bot.parser.exception;

/**
 * 文档解析异常
 */
public class DocumentParseException extends Exception {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
