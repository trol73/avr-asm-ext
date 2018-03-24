package ru.trolsoft.asmext;

import java.io.File;
import java.io.IOException;
import java.util.*;

import ru.trolsoft.asmext.processor.Parser;
import ru.trolsoft.asmext.processor.SyntaxException;

public class Main {

    private static void processFile(String srcPath, String outPath) throws IOException, SyntaxException {
        long t0 = System.currentTimeMillis();
        boolean gcc = srcPath.toLowerCase().endsWith(".s");
        Parser parser = new Parser(gcc);
        File srcFile = new File(srcPath);
        if (!srcFile.exists()) {
            fatalError("File not found: " + srcPath);
        }
        System.out.print("Process file: " + srcFile.getName());
        parser.parse(srcFile);
        parser.getOutput().writeToFile(outPath);
        t0 = System.currentTimeMillis() - t0;
        System.out.println(" .. " + t0 + "ms");
    }

    private static List<AbstractMap.SimpleEntry<String, String>> buildProcessList(String[] args) {
        List<AbstractMap.SimpleEntry<String, String>> result = new ArrayList<>();
        if (args.length == 2) {
            String src = args[0];
            String out = args[1];
            result.add(new AbstractMap.SimpleEntry<>(src, out));
        } else {
            String srcPath = args[0];
            String outPath = args[1];
            if (!new File(outPath).exists()) {
                fatalError("Output directory doesn't exists: " + outPath);
            }
            if (!srcPath.endsWith(File.separator)) {
                srcPath += File.separator;
            }
            if (!outPath.endsWith(File.separator)) {
                outPath += File.separator;
            }
            for (int i = 2; i < args.length; i++) {
                String arg = args[i];
                result.add(new AbstractMap.SimpleEntry<>(srcPath + arg, outPath + arg));
            }
        }
        return result;
    }

    public static void main(String[] args) {
        checkUsage(args);
        List<AbstractMap.SimpleEntry<String, String>> processList = buildProcessList(args);
        String src = "", out;
        try {
            for (AbstractMap.SimpleEntry<String, String> pair : processList) {
                src = pair.getKey();
                out = pair.getValue();
                processFile(src, out);
            }
        } catch (IOException e1) {
            System.out.println();
            System.out.println(e1.getMessage());
            System.exit(1);
        } catch (SyntaxException e2) {
            System.out.println();
            System.out.println(src + ":" + e2.line + ": Error: " + e2.getMessage());
            System.exit(1);
        }
    }

    private static void checkUsage(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: avr-asm-ext <source file> <output file>");
            System.out.println("   or: avr-asm-ext <source path> <output path> <filename-1> .. <filename-n>");
            System.exit(1);
        }
    }

    private static void fatalError(String msg) {
        System.out.println(msg);
        System.exit(2);
    }
}
/*

TODO avr asm ext - subi, cbi only high

; TODO !!!! компилируется неверно без ошибки if (j < slider_y || j > slider_y + slider_height) {


cpse  r4, r0   ; Сравнить r4 с r0 и пропустить следующую команду, если они равны


.pin keyboard_clk = D[0]

keyboard_clk->DDR = 1
    DDR[keyboard_clk] = 1

keyboard_clk->PORT = 0
    PORT[keyboard_clk] = 0
r1 = keyboard_clk->PIN
r1 = PIN[keyboard_clk]


r16_mask[1] = 1


wait_clk0_loop_%:
	sbic		PINC, CLK_PIN
	rjmp		wait_clk0_loop_%
while (PIN[keyboard_clk]);
while (!PIN[keyboard_clk]);


sts	PCMSK2, r3		; PCMSK2 = 0
iom[PCMSK2] = r3


	if (r21 != KEY_CTRL_R) goto check_code_1
	r21 = KEY_CTRL_L
check_code_1:



r26 = r0 << 1

rmp = (1<<URSEL)|(1<<UCSZ1)|(1<<UCSZ0) ; set 8 bit characters
rmp = bitmask(URSEL, UCSZ1, UCSZ0)


	rmp = LOW(1001)
	cp rRes1, rmp
	rmp = HIGH(1001)
	cpc rRes2, rmp
	brcc @E


	rmp = BYTE1(100000000) ; check overflow
	cp rRes1, rmp
	rmp = BYTE2(100000000)
	cpc rRes2, rmp
	rmp = BYTE3(100000000)
	cpc rRes3, rmp
	rmp = BYTE4(100000000)
	cpc rRes4, rmp
	brcs @1

adc r0, rmp
    r0 += F_CARRY + rmp


    rmp |= 1 << bLcdRs 	; set Rs to one
        rmp[bLcdRs] = 1

	if (rmp == cCR) goto @NoPar
	if (rmp == cLF) goto @NoPar
        if (rmp == cCR || rmp == cLF) goto @NoPar




if (r2.r1 < ZH.ZL) goto @2 ; ended subtraction



	if (ZL == 1) goto Interval_enc_clockwise
	if (ZL == 7) goto Interval_enc_clockwise
	if (ZL == 8) goto Interval_enc_clockwise
	if (ZL == 14) goto Interval_enc_clockwise



if (cond) {
    e1
} else {
    e2
}

if (!cond) goto @le     | if
    e1                  | if
    rjmp @lf            | else
@le:                    | else
    e2                  | else
@lf:                    | else




.MACRO swap_regs
    push    $1
    $1 = $0
    pop     $0
.ENDMACRO

.MACRO SET_BAT
.IF  $0 > 0x3F
.MESSAGE "Адрес больше, чем 0x3f"
lds $2, @0
sbr $2, (1<<$1)
sts $0, $2
.ELSE
.MESSAGE " Адрес меньше или равен 0x3f"
.ENDIF
.ENDMACRO




 */