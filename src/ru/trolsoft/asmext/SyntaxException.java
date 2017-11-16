package ru.trolsoft.asmext;

class SyntaxException extends Exception {

    int line;

    SyntaxException() {
        super();
    }

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
