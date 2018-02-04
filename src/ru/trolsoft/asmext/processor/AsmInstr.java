package ru.trolsoft.asmext.processor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class AsmInstr {
    static final Map<String, String> BRANCH_IF_FLAG_SET_MAP;
    static final Map<String, String> BRANCH_IF_FLAG_CLEAR_MAP;

    static final Map<String, String> SET_FLAG_MAP;
    static final Map<String, String> CLEAR_FLAG_MAP;


    static {
        Map<String, String> branchIfFlag = new HashMap<>(8);
        branchIfFlag.put("F_GLOBAL_INT", "brie");
        branchIfFlag.put("F_BIT_COPY", "brts");
        branchIfFlag.put("F_HALF_CARRY", "brhs");
        branchIfFlag.put("F_SIGN", "brlt");
        branchIfFlag.put("F_TCO", "brvs");
        branchIfFlag.put("F_NEG", "brmi");
        branchIfFlag.put("F_ZERO", "breq");
        branchIfFlag.put("F_CARRY", "brcs");
        BRANCH_IF_FLAG_SET_MAP = Collections.unmodifiableMap(branchIfFlag);

        Map<String, String> branchIfNotFlag = new HashMap<>(8);
        branchIfNotFlag.put("F_GLOBAL_INT", "brid");
        branchIfNotFlag.put("F_BIT_COPY", "brtc");
        branchIfNotFlag.put("F_HALF_CARRY", "brhc");
        branchIfNotFlag.put("F_SIGN", "brge");
        branchIfNotFlag.put("F_TCO", "brvc");
        branchIfNotFlag.put("F_NEG", "brpl");
        branchIfNotFlag.put("F_ZERO", "brne");
        branchIfNotFlag.put("F_CARRY", "brcc");
        BRANCH_IF_FLAG_CLEAR_MAP = Collections.unmodifiableMap(branchIfNotFlag);

        Map<String, String> setFlag = new HashMap<>(8);
        setFlag.put("F_GLOBAL_INT", "sei");
        setFlag.put("F_BIT_COPY", "set");
        setFlag.put("F_HALF_CARRY", "seh");
        setFlag.put("F_SIGN", "ses");
        setFlag.put("F_TCO", "sev");
        setFlag.put("F_NEG", "sen");
        setFlag.put("F_ZERO", "sez");
        setFlag.put("F_CARRY", "sec");
        SET_FLAG_MAP = Collections.unmodifiableMap(setFlag);

        Map<String, String> clearFlag = new HashMap<>(8);
        clearFlag.put("F_GLOBAL_INT", "cli");
        clearFlag.put("F_BIT_COPY", "clt");
        clearFlag.put("F_HALF_CARRY", "clh");
        clearFlag.put("F_SIGN", "cls");
        clearFlag.put("F_TCO", "clv");
        clearFlag.put("F_NEG", "cln");
        clearFlag.put("F_ZERO", "clz");
        clearFlag.put("F_CARRY", "clc");
        CLEAR_FLAG_MAP = Collections.unmodifiableMap(clearFlag);
    }

}
