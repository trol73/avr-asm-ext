package ru.trolsoft.asmext.compiler;

import ru.trolsoft.asmext.data.Variable;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.processor.*;
import ru.trolsoft.asmext.utils.AsmUtils;
import ru.trolsoft.asmext.utils.TokenString;

abstract class BaseCompiler extends Errors {

    private static final String[] STRING_OF_ZERO = {"", "0", "00", "000", "0000", "00000", "000000", "0000000", "00000000", "000000000", "0000000000"};



    TokenString src;
    OutputFile out;
    public final Parser parser;    // TODO move to interface

    BaseCompiler(Parser parser) {
        this.parser = parser;
    }

    void cleanup() {
        this.src = null;
        this.out = null;
    }

    void setup(TokenString src, OutputFile out) {
        this.src = src;
        this.out = out;
    }

    void addCommand(Cmd cmd, String arg1, String arg2) throws SyntaxException {
        try {
            cmd.check(arg1, arg2);
            out.appendCommand(src, cmd.name().toLowerCase(), arg1, arg2);
        } catch (SyntaxException e) {
            cleanup();
            throw e;
        }
    }

    void addCommand(Cmd cmd, String arg) throws SyntaxException {
        addCommand(cmd, arg, (String) null);
    }

    void addCommand(Cmd cmd) throws SyntaxException {
        addCommand(cmd, (String) null, (String) null);
    }

    void addCommand(Cmd cmd, Token arg1, Token arg2) throws SyntaxException {
        String s1 = arg1 != null ? arg1.toString() : null;
        String s2 = arg2 != null ? arg2.toString() : null;
        addCommand(cmd, s1, s2);
    }

    void addCommand(Cmd cmd, Token arg1, String arg2) throws SyntaxException {
        String s1 = arg1 != null ? arg1.toString() : null;
        addCommand(cmd, s1, arg2);
    }

    void addCommand(Cmd cmd, String arg1, Token arg2) throws SyntaxException {
        if (arg2 != null) {
            addCommand(cmd, arg1, arg2.toString());
        } else {
            addCommand(cmd, arg1, (String) null);
        }
    }

    void addCommand(Cmd cmd, Token arg) throws SyntaxException {
        if (arg != null) {
            addCommand(cmd, arg.toString());
        } else {
            addCommand(cmd);
        }
    }

    void addCommand(AsmUtils.Instruction instruction) throws SyntaxException {
        addCommand(instruction.getCommand(), instruction.getArg1Str(), instruction.getArg2Str());
    }

    String arrayIndexToPort(Token tokenIndex, String suffix) throws SyntaxException {
        Token.ArrayIndex index = tokenIndex.getIndex();
        if (index.hasModifier() || index.isPair()) {
            wrongArrayIndex("io");
        }
        String name = index.getName() + suffix;
        return parser.gcc ? "_SFR_IO_ADDR(" +  name  + ")" : name;
    }

    String arrayIndexToPort(Token tokenIndex) throws SyntaxException {
        return arrayIndexToPort(tokenIndex, "");
    }

    void checkBitIndex(Token t) throws SyntaxException {
        if (t.isNumber()) {
            int val = t.getNumberValue();
            if (val < 0 || val > 7) {
                wrongIndexError("wrong index: " + val);
            }
        } else if (!t.isAnyConst() && !t.isSomeString()) {
            error("bit offset constant expected: " + t);
        }
    }


    void checkRegister(Token t) throws SyntaxException {
        if (!t.isRegister()) {
            error("register expected: " + t);
        }
    }

    void checkExpressionMinLength(Expression expr, int minLength) throws SyntaxException {
        if (expr.size() < minLength) {
            invalidExpressionError();
        }
    }

    String loVarPtr(Token name, Variable.Type type, boolean negative) {
        if (parser.gcc) {
            return negative ? "lo8(-(" + name + "))" : "lo8(" + name + ")";
        } else {
            if (type == Variable.Type.POINTER) {
                return negative ? "LOW(-" + name + ")" : "LOW(" + name + ")";
            } else if (type == Variable.Type.PRGPTR) {
                return negative ? "LOW(-2*" + name + ")" : "LOW(2*" + name + ")";
            } else {
                throw new RuntimeException("unexpected pointer type");
            }
        }
    }

    String hiVarPtr(Token name, Variable.Type type, boolean negative) {
        if (parser.gcc) {
            return negative ? "hi8(-(" + name + "))" : "hi8(" + name + ")";
        } else {
            if (type == Variable.Type.POINTER) {
                return negative ? "HIGH(-" + name + ")" : "HIGH(" + name + ")";
            } else if (type == Variable.Type.PRGPTR) {
                return negative ? "HIGH(-2*" + name + ")" : "HIGH(2*" + name + ")";
            } else {
                throw new RuntimeException("unexpected pointer type");
            }
        }
    }

    Variable getVar(Token name) {
        return parser.getVariable(name.asString());
    }

    boolean isVar(Token name) {
        return getVar(name) != null;
    }

    int getVarSize(Token name) {
        Variable var = getVar(name);
        return var != null ? var.getSize() : -1;
    }

    Variable.Type getVarType(Token name) {
        Variable var = getVar(name);
        return var != null ? var.type : null;
    }

    static String arrayPairIndexValue(Token.ArrayIndex index) {
        String name = index.getName();
        if (index.isPreDec()) {
            return "-" + name;
        } else if (index.isPreInc()) {
            return "+" + name;
        } else if (index.isPostDec()) {
            return name + "-";
        } else if (index.isPostInc()) {
            return name + "+";
        } else {
            return name;
        }
    }


    void checkBitNumber(Token token) throws SyntaxException {
        if (token.isNumber()) {
            int bit = token.getNumberValue();
            if (bit < 0 || bit > 7) {
                unsupportedOperationError("wrong bit number");
            }
        } else if (token.isRegisterBit()) {
            Token index = token.getBitIndex();
            int bit;
            if (index.isAnyConst() && parser.getConstant(index.asString()) != null) {
                String val = parser.getConstant(index.asString()).value;
                if (!ParserUtils.isNumber(val)) {
                    unsupportedOperationError("unexpected value for bit index: " + index);
                }
                bit = ParserUtils.parseValue(val);
            } else if (index.isNumber()) {
                bit = index.getNumberValue();
            } else {
                unsupportedOperationError("unexpected value for bit index: " + index);
                return;
            }
            if (bit < 0 || bit > 7) {
                unsupportedOperationError("wrong bit number");
            }
        } else if (!(token.isAnyConst() || token.isSomeString())) {
            unsupportedOperationError("bit number expected after dot");
        }
    }


    static String hexByteStr(int val) {
        String s = Integer.toHexString(val);
        return s.length() == 1 ? "0x0" + s : "0x" + s;
    }

    static String binByteStr(int val) {
        String s = Integer.toBinaryString(val);
        s = STRING_OF_ZERO[8 - s.length()] + s;
        return "0b" + s;
    }


    String hiByte(Token val) {
        if (val.isNumber()) {
            int d = val.getNumberValue();
            return hexByteStr(d >> 8);
        }
        if (parser.gcc) {
            return "(" +val + " >> 8)";
        } else {
            return "HIGH(" + val + ")";
        }
    }

    String loByte(Token val) {
        if (val.isNumber()) {
            int d = val.getNumberValue();
            return hexByteStr(d & 0xff);
        }
        if (parser.gcc) {
            return "(" + val + " & 0xFF)";
        } else {
            return "LOW(" + val + ")";
        }
    }

    String numByte(Token val, int byteNum) {
        if (val.isNumber()) {
            int d = val.getNumberValue();
            d = d >> (8*byteNum);
            return hexByteStr(d & 0xff);
        }
        if (parser.gcc) {
            return "((" + val + ">>" + (byteNum*8) + ") & 0xFF)";
        } else {
            String valStr = val.toString();
            if (!ParserUtils.isInBrackets(valStr)) {
                valStr = "(" + valStr + ")";
            }
            return ("BYTE" + (byteNum+1)) + valStr;
        }
    }



    @Override
    void error(String message) throws SyntaxException {
        cleanup();
        super.error(message);
    }

    void error(String message, Throwable cause) throws SyntaxException {
        cleanup();
        super.error(message, cause);
    }
}
