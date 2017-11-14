package ru.trolsoft.asmext;

import java.util.ArrayList;
import java.util.List;

class Compiler {
    void compile(String[] tokens, StringBuilder out) {
        if (tokens.length == 0) {
            return;
        }
        String firstToken = null;
        for (String token : tokens) {
            if (!token.trim().isEmpty()) {
                firstToken = token.toLowerCase();
                break;
            }
        }
        if ("push".equals(firstToken)) {
            compilePushPop(tokens, out);
        } else if ("pop".equals(firstToken)) {
            compilePushPop(tokens, out);
        } else {
            compileDefault(tokens, out);
        }
    }


    private void compilePushPop(String[] tokens, StringBuilder out) {
        List<String> regs = new ArrayList<>();
        for (String token : tokens) {
            if (ParserUtils.isRegister(token)) {
                regs.add(token);
            }
        }
        if (regs.size() <= 1) {
            for (String token : tokens) {
                out.append(token);
            }
        } else {
            int regNum = 0;
            for (String reg : regs) {
                regNum++;
                for (int i = 0; i < tokens.length; i++) {
                    String token = tokens[i];
                    if (token == null) {
                        continue;
                    }
                    boolean isReg = ParserUtils.isRegister(token);
                    if (!isReg || token.equalsIgnoreCase(reg)) {
                        out.append(token);
                        if (isReg) {
                            tokens[i] = null;
                            if (regNum < regs.size() && i < tokens.length-1 && tokens[i+1].trim().isEmpty()) {
                                tokens[i+1] = null;
                            }
                        }
                    }
                }
                out.append('\n');
            }
        }
    }


    private void compileDefault(String[] tokens, StringBuilder out) {
        for (String token : tokens) {
            out.append(token);
        }
    }


    private boolean isComment(String token) {
        return token.startsWith(";") || token.startsWith("//");
    }
}
