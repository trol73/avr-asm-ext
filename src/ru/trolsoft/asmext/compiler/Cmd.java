package ru.trolsoft.asmext.compiler;

import ru.trolsoft.asmext.processor.SyntaxException;
import ru.trolsoft.asmext.processor.Token;

import static ru.trolsoft.avr.Registers.isHighRegister;
import static ru.trolsoft.avr.Registers.isPairLow;
import static ru.trolsoft.avr.Registers.isEven;


public enum Cmd {
    ADD,
    ADC,
    ADIW {
        @Override
        void check(String arg1, String arg2) throws SyntaxException {
            checkPairLow(arg1);
        }
    },
    AND,
    ANDI {
        @Override
        void check(String arg1, String arg2) throws SyntaxException {
            checkHighRegister(arg1);
        }
    },
    BLD,
    BST,
    BRIE,
    BRTS,
    BRHS,
    BRLT,
    BRVS,
    BRMI,
    BREQ,
    BRCS,
    BRID,
    BRTC,
    BRHC,
    BRGE,
    BRVC,
    BRPL,
    BRNE,
    BRCC,
    BRLO,
    BRSH,

    CBI,
    CBR,
    CLI,
    CLT,
    CLH,
    CLS,
    CLV,
    CLN,
    CLZ,
    CLC,

    CLR,
    CP,
    CPC,
    CPI {
        @Override
        void check(String arg1, String arg2) throws SyntaxException {
            checkHighRegister(arg1);
        }
    },
    CPSE,
    INC,
    DEC,
    IN,
    OUT,

    MOV,
    MOVW {
        @Override
        void check(String arg1, String arg2) throws SyntaxException {
            checkEvenReg(arg1);
            checkEvenReg(arg2);
        }
    },

    LD,
    LDI {
        @Override
        void check(String arg1, String arg2) throws SyntaxException {
            checkHighRegister(arg1);
        }
    },
    LDS,
    LPM,
    LSL,
    LSR,
    NEG,
    OR,
    ORI {
        @Override
        void check(String arg1, String arg2) throws SyntaxException {
            checkHighRegister(arg1);
        }
    },

    PUSH,
    POP,

    ROL,
    ROR,

    RJMP,
    RCALL,
    RET,
    RETI,

    SEI,
    SET,
    SEH,
    SES,
    SEV,
    SEN,
    SEZ,
    SEC,

    SUB,
    SBC,
    SBI,
    SBR,
    SBIC,
    SBIS,
    SBRS,
    SBRC,
    SBIW {
        @Override
        void check(String arg1, String arg2) throws SyntaxException {
            checkPairLow(arg1);
        }
    },
    SUBI {
        @Override
        void check(String arg1, String arg2) throws SyntaxException {
            checkHighRegister(arg1);
        }
    },
    SBCI {
        @Override
        void check(String arg1, String arg2) throws SyntaxException {
            checkHighRegister(arg1);
        }
    },
    ST,
    STS,
    TST,


    FMULSU,
    NOP,
    MUL,
    SPM,
    JMP,
    COM,
    ASR,
    MULS,
    BSET,
    SWAP,
    EOR,
    FMUL,
    BRBC,
    CALL,
    WDR,
    STD,
    MULSU,
    FMULS,
    ICALL,
    SER,
    EIJMP,
    SLEEP,
    BRBS,
    LDD,
    IJMP,
    BCLR,
    EICALL,
    ELPM,

    //LAC,
    //LAS,
    //LAT,
    //XCH,
    ;

    void check(String arg1, String arg2) throws SyntaxException {
    }

    private static void checkHighRegister(String reg) throws SyntaxException {
        if (!isHighRegister(reg)) {
            throw new SyntaxException("r16..r31 expected but " + reg + " found");
        }
    }

    private static void checkPairLow(String reg) throws SyntaxException {
        if (!isPairLow(reg)) {
            throw new SyntaxException("r14, r26, r28, r30 expected but " + reg + " found");
        }
    }

    private static void checkEvenReg(String reg) throws SyntaxException {
        if (!isEven(reg)) {
            throw new SyntaxException("r0, r2, ..., r30 expected but " + reg + " found");
        }
    }

    public static Cmd fromStr(String name) throws SyntaxException {
        name = name.toUpperCase();
        for (Cmd cmd : values()) {
            if (cmd.name().equals(name)) {
                return cmd;
            }
        }
        throw new SyntaxException("wrong command: " + name);
    }


    public static Cmd fromToken(Token token) throws SyntaxException {
        return fromStr(token.asString());
    }
}
