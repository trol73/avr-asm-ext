package ru.trolsoft.asmext.processor;

public class SyntaxException extends Exception {

    public int line;

    public SyntaxException() {
        super();
    }

    public SyntaxException(String message) {
        super(message);
    }

    public SyntaxException(String message, Throwable cause) {
        super(message, cause);
    }

    public SyntaxException(Throwable cause) {
        super(cause);
    }

    SyntaxException line(int line) {
        this.line = line;
        return this;
    }
}
