package ru.trolsoft.asmext.compiler;

import ru.trolsoft.asmext.data.Block;
import ru.trolsoft.asmext.processor.Expression;
import ru.trolsoft.asmext.processor.Parser;
import ru.trolsoft.asmext.processor.SyntaxException;
import ru.trolsoft.asmext.processor.Token;
import ru.trolsoft.asmext.utils.TokenString;

import static ru.trolsoft.asmext.data.Block.BLOCK_LOOP;
import static ru.trolsoft.asmext.compiler.Cmd.*;

public class LoopsCompiler extends BaseCompiler {
    public LoopsCompiler(Parser parser) {
        super(parser);
    }

    public void compileLoopStart(TokenString src, Expression expr) throws SyntaxException {
        setup(src);
        if (!expr.getLast().isOperator("{")) {
            error("'{' expected");
        }
        Expression argExpr = null;
        if (expr.get(1).isOperator("(")) {
            if (!expr.getLast(1).isOperator(")")) {
                error("')' not found");
            }
            argExpr = expr.subExpression(2, expr.size() - 3);
            if (argExpr.isEmpty()) {
                argExpr = null;
            }
        } else if (expr.size() != 2) {
            error("wrong loop syntax");
        }
        String argName = argExpr != null ? argExpr.getFirst().asString() + "_" : "";
        Block block = parser.addNewBlock(BLOCK_LOOP, argExpr, "loop_" + argName);

        if (argExpr != null) {
            Token reg = argExpr.getFirst();
            if (!reg.isRegister()) {
                error("register expected: " + reg);
            }
            if (argExpr.size() > 1) {
                if (!argExpr.get(1).isOperator("=")) {
                    error("wrong loop argument expression");
                }
                new ExpressionsCompiler(parser).compile(src, argExpr.copy(), out);
            }
        }
        addLabel(block.getLabelStart());
        cleanup();
    }


    public void compileLoopEnd(TokenString src, Block block) throws SyntaxException {
        setup(src);
        if (block.expr != null) {
            Token reg = block.expr.getFirst();
            addCommand(DEC, reg);
            addCommand(BRNE, block.getLabelStart());
        } else {
            addCommand(RJMP, block.getLabelStart());
        }
        if (block.getLabelEnd() != null) {
            addLabel(block.getLabelEnd());
        }
        cleanup();
    }


    public void compileBreak(TokenString src, Block block) throws SyntaxException {
        setup(src);
        addCommand(RJMP, block.buildEndLabel());
        cleanup();
    }

    public void compileContinue(TokenString src, Block block) throws SyntaxException {
        setup(src);
        addCommand(RJMP, block.getLabelStart());
        cleanup();
    }
}
