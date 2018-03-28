package ru.trolsoft.asmext.compiler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static ru.trolsoft.asmext.compiler.Cmd.*;

class AsmInstr {
    static final Map<String, Cmd> BRANCH_IF_FLAG_SET_MAP;
    static final Map<String, Cmd> BRANCH_IF_FLAG_CLEAR_MAP;

    static final Map<String, Cmd> SET_FLAG_MAP;
    static final Map<String, Cmd> CLEAR_FLAG_MAP;


    static {
        Map<String, Cmd> branchIfFlag = new HashMap<>(8);
        branchIfFlag.put("F_GLOBAL_INT", BRIE);
        branchIfFlag.put("F_BIT_COPY", BRTS);
        branchIfFlag.put("F_HALF_CARRY", BRHS);
        branchIfFlag.put("F_SIGN", BRLT);
        branchIfFlag.put("F_TCO", BRVS);
        branchIfFlag.put("F_NEG", BRMI);
        branchIfFlag.put("F_ZERO", BREQ);
        branchIfFlag.put("F_CARRY", BRCS);
        BRANCH_IF_FLAG_SET_MAP = Collections.unmodifiableMap(branchIfFlag);

        Map<String, Cmd> branchIfNotFlag = new HashMap<>(8);
        branchIfNotFlag.put("F_GLOBAL_INT", BRID);
        branchIfNotFlag.put("F_BIT_COPY", BRTC);
        branchIfNotFlag.put("F_HALF_CARRY", BRHC);
        branchIfNotFlag.put("F_SIGN", BRGE);
        branchIfNotFlag.put("F_TCO", BRVC);
        branchIfNotFlag.put("F_NEG", BRPL);
        branchIfNotFlag.put("F_ZERO", BRNE);
        branchIfNotFlag.put("F_CARRY", BRCC);
        BRANCH_IF_FLAG_CLEAR_MAP = Collections.unmodifiableMap(branchIfNotFlag);

        Map<String, Cmd> setFlag = new HashMap<>(8);
        setFlag.put("F_GLOBAL_INT", SEI);
        setFlag.put("F_BIT_COPY", SET);
        setFlag.put("F_HALF_CARRY", SEH);
        setFlag.put("F_SIGN", SES);
        setFlag.put("F_TCO", SEV);
        setFlag.put("F_NEG", SEN);
        setFlag.put("F_ZERO", SEZ);
        setFlag.put("F_CARRY", SEC);
        SET_FLAG_MAP = Collections.unmodifiableMap(setFlag);

        Map<String, Cmd> clearFlag = new HashMap<>(8);
        clearFlag.put("F_GLOBAL_INT", CLI);
        clearFlag.put("F_BIT_COPY", CLT);
        clearFlag.put("F_HALF_CARRY", CLH);
        clearFlag.put("F_SIGN", CLS);
        clearFlag.put("F_TCO", CLV);
        clearFlag.put("F_NEG", CLN);
        clearFlag.put("F_ZERO", CLZ);
        clearFlag.put("F_CARRY", CLC);
        CLEAR_FLAG_MAP = Collections.unmodifiableMap(clearFlag);
    }

}
