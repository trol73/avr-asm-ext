package ru.trolsoft.asmext;

public class SyntaxException extends Exception {

    int line;

    SyntaxException(String message) {
        super(message);
    }

    SyntaxException(String message, Throwable cause) {
        super(message, cause);
    }

    SyntaxException(Throwable cause) {
        super(cause);
    }

    SyntaxException line(int line) {
        this.line = line;
        return this;
    }
}
