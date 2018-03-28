package ru.trolsoft.asmext.compiler;

import ru.trolsoft.asmext.processor.SyntaxException;

abstract class Errors {

    void error(String message) throws SyntaxException {
        throw new SyntaxException(message);
    }

    void error(String message, Throwable cause) throws SyntaxException {
        throw new SyntaxException(message, cause);
    }

    void invalidExpressionError() throws SyntaxException {
        error("invalid expression");
    }

    void invalidExpressionError(String msg) throws SyntaxException {
        error("invalid expression: " + msg);
    }

    void unsupportedOperationError() throws SyntaxException {
        error("unsupported operation");
    }

    void unsupportedOperationError(Object o) throws SyntaxException {
        error("unsupported operation: " + o);
    }

    void sizesMismatchError() throws  SyntaxException {
        error("sizes mismatch");
    }

    void wrongCallSyntaxError() throws SyntaxException {
        error("wrong call/jump syntax");
    }

    void undefinedProcedureError(String name) throws SyntaxException {
        error("undefined procedure: \"" + name + '"');
    }

    void wrongArgumentError(Object name) throws SyntaxException {
        error("wrong argument: " + name);
    }

    void wrongValueError(String value) throws SyntaxException {
        error("wrong value: '" + value + "'");
    }

    void wrongIndexError(String index) throws SyntaxException {
        error("wrong index: " + index);
    }

    void internalError(String message, Throwable cause) throws SyntaxException {
        error("internal error (" + message + ")", cause);
    }

    void constExpectedError(String val) throws SyntaxException {
        error("constant expected: " + val);
    }

    void unexpectedExpressionError() throws SyntaxException {
        error("unexpected expression");
    }

    void unexpectedExpressionError(Object o) throws SyntaxException {
        error("unexpected expression: " + o);
    }

    void emptyExpressionError() throws SyntaxException {
        error("empty expression");
    }

    void wrongArrayIndex(String arrayName) throws SyntaxException {
        unsupportedOperationError("wrong " + arrayName + "[] index");
    }

}
