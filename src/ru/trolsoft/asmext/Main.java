package ru.trolsoft.asmext;

import java.io.File;
import java.io.IOException;
import ru.trolsoft.asmext.processor.Parser;
import ru.trolsoft.asmext.processor.SyntaxException;

public class Main {

    public static void main(String[] args) {
        long t0 = System.currentTimeMillis();
        if (args.length != 2) {
            System.out.println("Usage: avr-asm-ext: <source file> <output file>");
            System.exit(1);
        }
        String src = args[0];
        String out = args[1];
        Parser parser = new Parser(src.toLowerCase().endsWith(".s"));
        File srcFile = new File(src);
        if (!srcFile.exists()) {
            System.out.println("File not found: " + src);
            System.exit(2);
        }
        try {
            parser.parse(srcFile);
            parser.getOutput().writeToFile(out);
        } catch (IOException e1) {
            System.out.println(e1.getMessage());
            System.exit(1);
        } catch (SyntaxException e2) {
            System.out.println(src + ":" + e2.line + ": Error: " + e2.getMessage());
            System.exit(1);
        }
        //System.out.println("Elapsed time: " + (System.currentTimeMillis() - t0) + " ms");
    }
}
/*

.pin keyboard_clk = D[0]

keyboard_clk->DDR = 1
    DDR[keyboard_clk] = 1

keyboard_clk->PORT = 0
    PORT[keyboard_clk] = 0
r1 = keyboard_clk->PIN
r1 = PIN[keyboard_clk]




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


	if (!io[UCSRA].UDRE) goto @wait
	;sbis	UCSRA, UDRE ; wait for empty char
	;rjmp	@wait


.loop (r1 = r18 = 100)

.loop
.endloop

	rmp = BYTE1(100000000) ; check overflow
	cp rRes1, rmp
	rmp = BYTE2(100000000)
	cpc rRes2, rmp
	rmp = BYTE3(100000000)
	cpc rRes3, rmp
	rmp = BYTE4(100000000)
	cpc rRes4, rmp
	brcs @1




if (r2.r1 < ZH.ZL) goto @2 ; ended subtraction



	if (ZL == 1) goto Interval_enc_clockwise
	if (ZL == 7) goto Interval_enc_clockwise
	if (ZL == 8) goto Interval_enc_clockwise
	if (ZL == 14) goto Interval_enc_clockwise




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



 IF (reg < 32) goto @1
 IF (reg < 32):
    goto @1
 ENDIF

 IF (sreg.s)
 ENDIF

 IF (!sreg.z)
 ENDIF
                                                                unsigned            signed

.if
.else
.endif

 */